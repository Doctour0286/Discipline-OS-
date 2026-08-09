package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.MissionDao
import com.disciplineos.data.dao.TierDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.TierEventKind
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerEntry
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.ReputationBand
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
 * **`demotion_triggered` (2026-08-09, ROADMAP.md §5.9) — now implemented.** Data Model §3.5
 * defines the trigger as "Reputation < tier_floor for tier N consecutive days." Bands and
 * N live in [ReputationDecayPolicy.bandFor] / [ReputationDecayPolicy.consecutiveDaysBelowFloorForDemotion]
 * (`[HYPOTHESIS]` pending Phase 5 pilot data — see that file). Each call to [execute]:
 * 1. Applies decay/recovery as before, producing a post-update Reputation value.
 * 2. Maps that value to a [com.disciplineos.domain.policy.ReputationBand] and compares it
 *    against the band implied by the user's *current tier* (see [tierFloorBand] below for
 *    the tier↔band correspondence this assumes).
 * 3. If below that tier's floor band, increments [com.disciplineos.data.entity.User
 *    .consecutiveDaysBelowFloor]; if at/above it, resets that counter to 0.
 * 4. If the counter reaches [ReputationDecayPolicy.consecutiveDaysBelowFloorForDemotion],
 *    fires a real tier demotion via [TierTransitionUseCase.standardDowngrade] (§12.4.1 —
 *    "sustained depletion signal across a rolling window," which is exactly what N
 *    consecutive days below floor is) and resets the counter to 0 so a fresh window starts
 *    for any further decline.
 *
 * **Tier↔band correspondence is a judgment call the specs don't make explicitly, flagged
 * here per ROADMAP.md §5 convention rather than silently assumed.** §5.9's bands describe
 * Reputation *value* ranges, not tiers directly, and neither the PRD nor Data Model doc
 * states which band a given [Tier] is expected to stay above. This implementation assumes
 * each tier's "floor" is the band matching its rank position — Recruit↔UNDISCIPLINED (no
 * floor to fall below), Operator↔INCONSISTENT, Warden↔RELIABLE, Iron↔DISCIPLINED — i.e. a
 * user demotes out of a tier once their Reputation sustains below the band one step under
 * that tier's own nominal band. This is a reasonable reading, not a spec-stated one — worth
 * product-owner confirmation before Phase 5 pilot, same as the band values themselves.
 *
 * This use-case still only fires **one** tier's worth of demotion per call, matching every
 * other downgrade path in this codebase (§12.4 downgrades all move exactly one tier or to a
 * fixed target, never multiple tiers in one transition) — a Reputation value low enough to
 * imply skipping a tier still only demotes one level per triggering day.
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
    private val tierDao: TierDao,
    private val policy: ReputationDecayPolicy,
) {

    /**
     * Applies one day's worth of decay/recovery for [userId], evaluated over the window
     * [since, now). Idempotent only in the sense that calling it twice for overlapping
     * windows double-counts — callers are responsible for tracking the last-applied instant
     * (not this use-case's concern; no such field exists on [com.disciplineos.data.entity.User]
     * yet, flagged here rather than added speculatively before a real scheduler needs it).
     *
     * @return a [Result]: the ledger entries written (empty if decay is currently paused —
     *   crisis stabilization window active, see class kdoc — and no recovery credit applied
     *   either), plus [Result.demotionEvent] if this call's §5.9 check fired a demotion.
     */
    suspend fun execute(userId: UUID, since: Instant, now: Instant = Instant.now()): Result {
        return database.withTransaction {
            var user = requireNotNull(userDao.get(userId)) { "No User found for id $userId" }

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

            // §5.9 demotion_triggered — see class kdoc for the full walkthrough.
            val currentValue = ledgerDao.currentValue(userId, LedgerMetric.REPUTATION)
            val currentBand = policy.bandFor(currentValue)
            val floorBand = tierFloorBand(user.currentTier)

            var demotionEvent: TierEvent? = null

            if (floorBand != null && currentBand < floorBand) {
                val daysBelow = user.consecutiveDaysBelowFloor + 1
                if (daysBelow >= policy.consecutiveDaysBelowFloorForDemotion()) {
                    val toTier = oneTierDown(user.currentTier)
                    if (toTier != null) {
                        val event = TierEvent(
                            id = UUID.randomUUID(),
                            userId = userId,
                            kind = TierEventKind.STANDARD_DOWNGRADE,
                            fromTier = user.currentTier,
                            toTier = toTier,
                            occurredAt = now,
                            reasonNote = "Reputation sustained below $floorBand floor for " +
                                "$daysBelow consecutive day(s) (§5.9 demotion_triggered, N=" +
                                "${policy.consecutiveDaysBelowFloorForDemotion()})",
                        )
                        tierDao.insertEvent(event)
                        user = user.copy(currentTier = toTier, consecutiveDaysBelowFloor = 0)
                        demotionEvent = event
                    } else {
                        // Already at the floor tier (Recruit) — nowhere lower to demote to.
                        // Keep counting is pointless once there's no further tier to fall
                        // into, so hold the counter rather than let it grow unbounded.
                        user = user.copy(consecutiveDaysBelowFloor = daysBelow)
                    }
                } else {
                    user = user.copy(consecutiveDaysBelowFloor = daysBelow)
                }
            } else if (user.consecutiveDaysBelowFloor != 0) {
                user = user.copy(consecutiveDaysBelowFloor = 0)
            }

            userDao.update(user)

            Result(entries = entries, demotionEvent = demotionEvent)
        }
    }

    /**
     * Tier↔band correspondence — see class kdoc's "judgment call" note. Returns null for
     * [Tier.RECRUIT] since there's no lower tier to demote out of, so no floor applies.
     */
    private fun tierFloorBand(tier: Tier): ReputationBand? = when (tier) {
        Tier.RECRUIT -> null
        Tier.OPERATOR -> ReputationBand.INCONSISTENT
        Tier.WARDEN -> ReputationBand.RELIABLE
        Tier.IRON -> ReputationBand.DISCIPLINED
    }

    /** Same one-tier-step mapping as §12.4.2's Explicit Downgrade — see that use-case. */
    private fun oneTierDown(tier: Tier): Tier? = when (tier) {
        Tier.IRON -> Tier.WARDEN
        Tier.WARDEN -> Tier.OPERATOR
        Tier.OPERATOR -> Tier.RECRUIT
        Tier.RECRUIT -> null
    }

    data class Result(
        val entries: List<LedgerEntry>,
        /** Non-null if this call's demotion check fired a tier demotion. */
        val demotionEvent: TierEvent?,
    )

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
