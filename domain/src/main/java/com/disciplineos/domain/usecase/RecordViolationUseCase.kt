package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.EnforcementSessionDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerEntry
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.ConsequencePolicy
import java.time.Duration
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
    private val missionDao: EnforcementSessionDao,
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
        // A Mission requires a MissionProfile (Mission.missionProfileId), which requires a
        // fully-onboarded User past Tier Confirmation (MissionProfileSetupFragment's
        // reachability) — same invariant as MissionInterceptionActivity's identical check.
        // user.currentTier being null here means that invariant was violated upstream; a
        // loud crash surfaces that immediately rather than silently misbehaving (Batch B,
        // BUILD_PLAN.md — see User.kt kdoc for why this field became nullable at all).
        val currentTier = requireNotNull(user.currentTier) {
            "RecordViolationUseCase reached for user ${user.id} with no currentTier — " +
                "should be structurally impossible once a Mission/MissionProfile exist " +
                "(User.kt kdoc, Batch B)"
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
            clusterAlreadyHasActiveEntry(clusterId, violation.id, violation.detectedAt, LedgerMetric.DEBT)
        val reputationBlocked = clusterId != null &&
            clusterAlreadyHasActiveEntry(clusterId, violation.id, violation.detectedAt, LedgerMetric.REPUTATION)

        val debtEntry = if (debtBlocked) null else {
            val delta = consequencePolicy.debtPenalty(currentTier, violation.type)
            writeEntry(user.id, violation.id, LedgerMetric.DEBT, delta)
        }
        val reputationEntry = if (reputationBlocked) null else {
            val delta = consequencePolicy.reputationPenalty(currentTier, violation.type)
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
     * §3.5 shared-cause guard: true if some *other* Violation sharing [clusterId], detected
     * within [WINDOW] of [newViolationDetectedAt], already has an active (non-reversed,
     * non-paused) [metric] entry.
     *
     * ROADMAP.md §5.5, resolved 2026-08-09: 3-day rolling window, `[HYPOTHESIS]` pending
     * Phase 5 pilot data. A same-cluster entry only counts as "already active" for guard
     * purposes if the *sibling Violation it belongs to* was detected within [WINDOW] of this
     * new Violation; outside that window, treat it as a new, independently-real pattern
     * rather than a duplicate. The window is measured against `Violation.detectedAt` (the
     * fact being deduplicated), not `LedgerEntry.appliedAt` (when the penalty was written) —
     * those are normally close together but are conceptually different timestamps, and the
     * guard is about "is this the same real-world incident," which is a property of the
     * Violations, not of when their ledger writes happened to land.
     */
    private suspend fun clusterAlreadyHasActiveEntry(
        clusterId: UUID,
        excludingViolationId: UUID,
        newViolationDetectedAt: Instant,
        metric: LedgerMetric,
    ): Boolean {
        val siblings = violationDao.forRootCauseCluster(clusterId)
            .filter { it.id != excludingViolationId }
            .filter { sibling ->
                val gap = Duration.between(sibling.detectedAt, newViolationDetectedAt).abs()
                gap <= WINDOW
            }
        if (siblings.isEmpty()) return false
        val siblingIds = siblings.map { it.id }
        return ledgerDao.activeEntriesForViolations(siblingIds, metric).isNotEmpty()
    }

    private companion object {
        /** §5.5: 3-day rolling window, `[HYPOTHESIS]`. */
        val WINDOW: Duration = Duration.ofDays(3)
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
