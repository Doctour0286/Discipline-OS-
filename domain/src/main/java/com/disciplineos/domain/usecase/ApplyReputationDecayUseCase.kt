package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.EnforcementSessionDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.TierEvent
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
 * **Review note (2026-08-09):** an earlier draft of this class wrote the demotion `TierEvent`
 * directly via `TierDao` rather than calling [TierTransitionUseCase.standardDowngrade],
 * duplicating that method's logic instead of reusing it. Fixed before merge — see [execute]'s
 * kdoc for why the actual `standardDowngrade` call has to happen outside this method's
 * `withTransaction` block rather than inside it.
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
    private val missionDao: EnforcementSessionDao,
    private val ledgerDao: LedgerDao,
    private val tierTransitionUseCase: TierTransitionUseCase,
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
        // §5.9 demotion firing (below) calls TierTransitionUseCase.standardDowngrade, which
        // opens its own database.withTransaction — Room composes nested transactions onto
        // the same connection/thread fine, but standardDowngrade re-fetches the User row
        // itself (see requireUser() in that class) rather than accepting one, so any
        // consecutiveDaysBelowFloor bookkeeping this method needs persisted *before* that
        // call must actually be written first, not just held in a local `var user`. That's
        // why the counter-reset branches below call userDao.update immediately rather than
        // batching every User mutation into one write at the end the way the original
        // (pre-review) version of this method did.
        val entriesAndDemotion = database.withTransaction {
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

            // §5.9 demotion_triggered — see class kdoc for the full walkthrough. Determine
            // the outcome here (inside this transaction, so it sees a consistent Reputation
            // value alongside the decay/recovery entries just written above), but the actual
            // tier change — if any — is applied via TierTransitionUseCase.standardDowngrade
            // just below, outside this transaction block, once the counter bookkeeping for
            // the non-demoting branches is safely persisted.
            val currentValue = ledgerDao.currentValue(userId, LedgerMetric.REPUTATION)
            val currentBand = policy.bandFor(currentValue)
            // currentTier is nullable on User (Batch B: it doesn't exist until Tier
            // Confirmation, screen 4a, during onboarding) but this use case only ever runs
            // post-onboarding, on an already-tiered user — see User.kt kdoc's "every
            // non-test call site reading these four fields was checked" note, which
            // predates this file and should be extended to cover it too.
            val currentTier = requireNotNull(user.currentTier) {
                "ApplyReputationDecayUseCase ran for user $userId with no tier set — " +
                    "decay/demotion should never run before onboarding completes"
            }
            val floorBand = tierFloorBand(currentTier)

            var pendingDemotion: PendingDemotion? = null

            if (floorBand != null && currentBand < floorBand) {
                val daysBelow = user.consecutiveDaysBelowFloor + 1
                if (daysBelow >= policy.consecutiveDaysBelowFloorForDemotion()) {
                    val toTier = oneTierDown(currentTier)
                    if (toTier != null) {
                        // Reset the counter now and persist it, since standardDowngrade
                        // below will re-fetch this User row and must see the reset counter,
                        // not the stale pre-demotion value.
                        user = user.copy(consecutiveDaysBelowFloor = 0)
                        userDao.update(user)
                        pendingDemotion = PendingDemotion(
                            toTier = toTier,
                            reasonNote = "Reputation sustained below $floorBand floor for " +
                                "$daysBelow consecutive day(s) (§5.9 demotion_triggered, N=" +
                                "${policy.consecutiveDaysBelowFloorForDemotion()})",
                        )
                    } else {
                        // Already at the floor tier (Recruit) — nowhere lower to demote to.
                        // Keep counting is pointless once there's no further tier to fall
                        // into, so hold the counter rather than let it grow unbounded.
                        user = user.copy(consecutiveDaysBelowFloor = daysBelow)
                        userDao.update(user)
                    }
                } else {
                    user = user.copy(consecutiveDaysBelowFloor = daysBelow)
                    userDao.update(user)
                }
            } else if (user.consecutiveDaysBelowFloor != 0) {
                user = user.copy(consecutiveDaysBelowFloor = 0)
                userDao.update(user)
            }

            EntriesAndPendingDemotion(entries, pendingDemotion)
        }

        // Outside the withTransaction block above (standardDowngrade opens its own) — see
        // the note at the top of this method for why the counter reset had to be persisted
        // first rather than passed in-memory.
        val demotionEvent = entriesAndDemotion.pendingDemotion?.let { pending ->
            tierTransitionUseCase.standardDowngrade(
                userId = userId,
                toTier = pending.toTier,
                reasonNote = pending.reasonNote,
                now = now,
            )
        }

        return Result(entries = entriesAndDemotion.entries, demotionEvent = demotionEvent)
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

    /**
     * Everything decided about a possible §5.9 demotion *inside* the withTransaction block,
     * before the actual tier change is applied via [TierTransitionUseCase.standardDowngrade]
     * outside it — see the note at the top of [execute] for why the tier change itself can't
     * happen inside that same block.
     */
    private data class PendingDemotion(val toTier: Tier, val reasonNote: String)

    /** Return type of the withTransaction block in [execute] — see that method's kdoc. */
    private data class EntriesAndPendingDemotion(
        val entries: List<LedgerEntry>,
        val pendingDemotion: PendingDemotion?,
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
