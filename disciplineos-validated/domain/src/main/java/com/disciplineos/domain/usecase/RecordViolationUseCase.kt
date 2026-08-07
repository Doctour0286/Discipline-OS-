package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.MissionDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerEntry
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.ConsequencePolicy
import java.time.Instant
import java.util.UUID

/**
 * ROADMAP.md Phase 1, first exit-criterion item: "Recording a Violation and applying its
 * Debt/Reputation penalty happens in one transactional use-case, never as separate
 * uncoordinated DAO calls."
 *
 * Wires together, atomically:
 * - [ViolationDao.insert] — the fact of the Violation
 * - [ConsequencePolicy] — the (currently Hypothesis, per ConsequencePolicy's own kdoc)
 *   tier/type-dependent Debt and Reputation deltas
 * - the §27.2 / Data Model §3.5 shared-cause guard — before writing a penalty for either
 *   metric, checks whether this Violation's `rootCauseClusterId` already has an active
 *   (non-reversed) entry for that metric from a sibling Violation in the same cluster, and
 *   skips writing a second one if so
 * - the §6 dispute-freeze rule — a Violation created with `consequencePaused = true` (i.e.
 *   already flagged at creation time, an edge case but one the schema allows) gets recorded
 *   but does not write to the Ledger; consequences apply only once a dispute resolves as
 *   UPHELD, which is `ResolveDisputeUseCase`'s job (ROADMAP.md Phase 1, not yet built), not
 *   this class's
 *
 * Deliberately does NOT read from UnsupervisedSignal or import UnsupervisedSignalDao —
 * ArchitectureBoundaryTest's import-scan only checks files under `:data`'s src/main, but
 * the same boundary (Data Model §7 / §13.3) applies here by the same reasoning, and this
 * class is exactly the kind of enforcement-path code that rule exists to constrain.
 */
class RecordViolationUseCase(
    private val database: DisciplineOsDatabase,
    private val violationDao: ViolationDao,
    private val missionDao: MissionDao,
    private val userDao: UserDao,
    private val ledgerDao: LedgerDao,
    private val consequencePolicy: ConsequencePolicy,
) {

    /**
     * Records [violation] and, unless its consequences are paused or the shared-cause guard
     * blocks it, writes the corresponding Debt and Reputation ledger entries.
     *
     * @return the result describing what was actually written, so callers (e.g. a Daily
     *   Report refresh, or a test) don't have to re-derive it from ledger state.
     */
    suspend fun execute(violation: Violation): Result = database.withTransaction {
        val mission = requireNotNull(missionDao.get(violation.missionId)) {
            "Violation ${violation.id} references missing Mission ${violation.missionId}"
        }
        require(mission.status != MissionStatus.ABORTED_CRISIS_EXIT) {
            // Data Model §5, PRD §12.4.4: crisis exit must never reach this use-case at all —
            // it is handled as a distinct, non-consequence path. A caller wiring a crisis
            // exit into RecordViolationUseCase is a bug at the call site, not something this
            // use-case should silently accept and then have to special-case around.
            "Mission ${mission.id} is ABORTED_CRISIS_EXIT — must not go through " +
                "RecordViolationUseCase; crisis exit writes no Debt/Reputation (Data Model §5)"
        }
        val user = requireNotNull(userDao.get(mission.userId)) {
            "Mission ${mission.id} references missing User ${mission.userId}"
        }

        violationDao.insert(violation)

        if (violation.consequencePaused) {
            // §6: consequence_paused freezes both Debt and Reputation writes until the
            // dispute resolves. Nothing more to do here — see ResolveDisputeUseCase
            // (dispute-resolution use-case, not yet built) for the UPHELD path.
            return@withTransaction Result(
                violationId = violation.id,
                debtEntry = null,
                reputationEntry = null,
                skippedReason = SkipReason.CONSEQUENCE_PAUSED,
            )
        }

        val clusterId = violation.rootCauseClusterId
        val debtBlocked = clusterId != null &&
            clusterAlreadyHasActiveEntry(clusterId, violation.id, LedgerMetric.DEBT)
        val reputationBlocked = clusterId != null &&
            clusterAlreadyHasActiveEntry(clusterId, violation.id, LedgerMetric.REPUTATION)

        val debtEntry = if (debtBlocked) null else {
            val delta = consequencePolicy.debtPenalty(user.currentTier, violation.type)
            writeEntry(user.id, violation.id, LedgerMetric.DEBT, delta)
        }
        val reputationEntry = if (reputationBlocked) null else {
            val delta = consequencePolicy.reputationPenalty(user.currentTier, violation.type)
            writeEntry(user.id, violation.id, LedgerMetric.REPUTATION, delta)
        }

        val skipReason = when {
            debtBlocked && reputationBlocked -> SkipReason.SHARED_CAUSE_GUARD_BOTH
            debtBlocked -> SkipReason.SHARED_CAUSE_GUARD_DEBT_ONLY
            reputationBlocked -> SkipReason.SHARED_CAUSE_GUARD_REPUTATION_ONLY
            else -> null
        }

        Result(
            violationId = violation.id,
            debtEntry = debtEntry,
            reputationEntry = reputationEntry,
            skippedReason = skipReason,
        )
    }

    /**
     * §3.5 shared-cause guard: true if some *other* Violation sharing [clusterId] already
     * has an active (non-reversed) [metric] entry. Deliberately does not exclude entries by
     * age/window — the spec (§3.5) says "within a rolling window" but doesn't define the
     * window length anywhere, and picking one here would be exactly the kind of invented
     * constant ConsequencePolicy's kdoc and Data Model §3.1 both argue against. Until §42
     * resolves a real value, the guard is unconditional (any active same-cluster entry
     * blocks a second one), which is the strictly safer of the two unvalidated options —
     * it can only under-penalize relative to a windowed version, never double-penalize.
     */
    private suspend fun clusterAlreadyHasActiveEntry(
        clusterId: UUID,
        excludingViolationId: UUID,
        metric: LedgerMetric,
    ): Boolean {
        val siblingIds = violationDao.forRootCauseCluster(clusterId)
            .asSequence()
            .map { it.id }
            .filter { it != excludingViolationId }
            .toList()
        if (siblingIds.isEmpty()) return false
        return ledgerDao.activeEntriesForViolations(siblingIds, metric).isNotEmpty()
    }

    data class Result(
        val violationId: UUID,
        val debtEntry: LedgerEntry?,
        val reputationEntry: LedgerEntry?,
        val skippedReason: SkipReason?,
    )

    enum class SkipReason {
        CONSEQUENCE_PAUSED,
        SHARED_CAUSE_GUARD_DEBT_ONLY,
        SHARED_CAUSE_GUARD_REPUTATION_ONLY,
        SHARED_CAUSE_GUARD_BOTH,
    }

    private suspend fun writeEntry(
        userId: UUID,
        violationId: UUID,
        metric: LedgerMetric,
        delta: Double,
    ): LedgerEntry {
        val entry = LedgerEntry(
            id = UUID.randomUUID(),
            userId = userId,
            violationId = violationId,
            metric = metric,
            delta = delta,
            appliedAt = Instant.now(),
        )
        ledgerDao.insert(entry)
        return entry
    }
}
