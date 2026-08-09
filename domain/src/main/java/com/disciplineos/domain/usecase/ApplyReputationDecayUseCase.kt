package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.MissionDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerEntry
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.ReputationDecayPolicy
import java.time.Instant
import java.util.UUID

/**
 * ROADMAP.md Phase 1, final exit-criterion item: "Reputation decay rate constant is still
 * [HYPOTHESIS]-tagged and easily swappable — do NOT pick a 'reasonable-sounding' number
 * here; use the spec's placeholder posture — ApplyReputationDecayUseCase not yet written."
 *
 * Implements Data Model & Schema doc §3.5's decay/recovery formula:
 * ```
 * Reputation -= decay_per_missed_day
 * Reputation += recovery_per_completed_mission
 * demotion_triggered when Reputation < tier_floor for tier N consecutive days
 * ```
 *
 * **Distinct from `RecordViolationUseCase`'s existing per-violation Reputation write.** That
 * use-case already writes a `LedgerMetric.REPUTATION` entry once per Violation, guarded by
 * the §27.2 shared-cause cluster check — that is the *immediate* per-event penalty. This
 * use-case is the *separate*, schedule-driven component the same formula names: a
 * `decay_per_missed_day` term (evaluated per elapsed day, not per Violation) and a
 * `recovery_per_completed_mission` term (a positive credit `RecordViolationUseCase` never
 * writes, since it only fires on Violations). Both write to the same `LedgerMetric.REPUTATION`
 * running total via the same event-sourced Ledger — they are two different *inputs* to one
 * value, not two competing implementations of it. A caller (e.g. a daily WorkManager job,
 * Phase 2) is expected to invoke this once per elapsed day per user; this use-case does not
 * schedule itself.
 *
 * **`demotion_triggered` values are now decided (2026-08-09, ROADMAP.md §5.9) but NOT YET
 * IMPLEMENTED in this class.** Data Model §3.5 defines the trigger as "Reputation < tier_floor
 * for tier N consecutive days." Product-owner sign-off set, `[HYPOTHESIS]` pending Phase 5
 * pilot data:
 * ```
 * Undisciplined  0–20    Relentless  70–84
 * Inconsistent  21–40    Elite       85–94
 * Reliable      41–54    Iron Will   95–100
 * Disciplined   55–69
 * N = 3 consecutive days below a rank's floor before demotion_triggered fires
 * ```
 * This use-case still only computes and writes the running Reputation *value* — rank-band
 * mapping and demotion-trigger firing logic (walking the table above, tracking consecutive
 * days below floor, emitting a demotion event) are a scoped follow-up task, not yet written.
 * Do not treat this class as "done" for §5.9 purposes until that logic exists and is
 * CI-verified. See ROADMAP.md §5.9 for full rationale.
 *
 * **Crisis stabilization pause — a judgment call, not directly specified.** PRD §12.4.3
 * states Crisis Downgrade "pauses debt accrual" and is "a stabilization event, not a
 * punishment event"; §12.4.4 adds the Iron Crisis Exit "carries no score penalty." Neither
 * section says explicitly whether Reputation *decay* (as opposed to a Violation's immediate
 * Reputation penalty, which naturally doesn't apply since a crisis-exit Mission never reaches
 * `RecordViolationUseCase`) should also pause. Decay is itself a Reputation penalty
 * mechanism, and grinding it forward silently during a window the PRD frames as protective
 * would sit uneasily against "stabilization event, not a punishment event" — so this
 * implementation treats [com.disciplineos.data.entity.User.debtAccrualPausedUntil] as
 * gating decay too, reusing the existing field rather than adding a second one, since both
 * fields are set to the same instant by the same [TierTransitionUseCase] paths (crisis
 * downgrade, Iron crisis exit) and the PRD gives no indication they're meant to diverge.
 * Recovery credit is NOT paused by this same window — crediting a completed Mission during
 * stabilization is never adverse to the user, so there's no version of the "punishment
 * event" concern that would argue against it.
 */
class ApplyReputationDecayUseCase(
    private val database: DisciplineOsDatabase,
    private val userDao: UserDao,
    private val missionDao: MissionDao,
    private val ledgerDao: LedgerDao,
    private val policy: ReputationDecayPolicy,
) {

    /**
     * Applies one day's worth of decay/recovery for [userId], evaluated over the window
     * [since, now). Idempotent only in the sense that calling it twice for overlapping
     * windows double-counts — callers are responsible for tracking the last-applied instant
     * (not this use-case's concern; no such field exists on [com.disciplineos.data.entity.User]
     * yet, flagged here rather than added speculatively before a real scheduler needs it).
     *
     * @return the ledger entries written, or an empty list if decay is currently paused
     *   (crisis stabilization window active) — see class kdoc.
     */
    suspend fun execute(userId: UUID, since: Instant, now: Instant = Instant.now()): List<LedgerEntry> {
        return database.withTransaction {
            val user = requireNotNull(userDao.get(userId)) { "No User found for id $userId" }

            val entries = mutableListOf<LedgerEntry>()

            val stabilizationPauseActive = user.debtAccrualPausedUntil?.let { now.isBefore(it) } ?: false

            if (!stabilizationPauseActive) {
                val missedDays = missionDao.missedDaysSince(userId, since)
                if (missedDays > 0) {
                    val delta = -policy.decayPerMissedDay() * missedDays
                    entries += writeEntry(userId, delta, now)
                }
            }

            val completedMissions = missionDao.completedMissionsSince(userId, since)
            if (completedMissions > 0) {
                val delta = policy.recoveryPerCompletedMission() * completedMissions
                entries += writeEntry(userId, delta, now)
            }

            entries
        }
    }

    private suspend fun writeEntry(userId: UUID, delta: Double, appliedAt: Instant): LedgerEntry {
        val entry = LedgerEntry(
            id = UUID.randomUUID(),
            userId = userId,
            violationId = null, // Data Model §6: null because this isn't tied to one Violation — a scheduled system event
            metric = LedgerMetric.REPUTATION,
            delta = delta,
            appliedAt = appliedAt,
        )
        ledgerDao.insert(entry)
        return entry
    }
}
