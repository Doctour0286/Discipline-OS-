package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.adherence.AdherenceLedgerDao
import com.disciplineos.data.adherence.AdherenceLedgerEntry
import com.disciplineos.data.dao.EnforcementSessionDao
import com.disciplineos.data.dao.GoalMissionDao
import com.disciplineos.data.dao.MissionLogEntryDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.CadenceType
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.MissionLogEntry
import com.disciplineos.data.entity.TargetDirection
import com.disciplineos.domain.policy.AdherenceDecayPolicy
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil

/**
 * Batch G3 (BUILD_PLAN.md), ROADMAP.md §5.36. Base design doc §4.2 ("Adherence — resolved as a
 * real, lightweight consequence path") / Integration Plan §4.1. Sibling to
 * [ApplyReputationDecayUseCase], following that use-case's pattern directly rather than
 * inventing a new shape — a `*Policy` object gating the pure math, a use-case applying it and
 * writing the result via an event-sourced ledger table.
 *
 * **Scope — base doc §4.2's exact wording:** "Applies to Outcome-driven and Constraint missions,
 * and to Behavior-driven missions that have no attached EnforcementSession." [execute] checks
 * this first and short-circuits (empty [Result], no ledger writes) for a [GoalMission] outside
 * scope — a Behavior-driven mission whose `MissionPeriod`(s) already produced a real
 * `EnforcementSession` gets Reputation/Debt treatment via that session instead (base doc §4.2:
 * "shown as a secondary number, never substituted for Reputation on that mission" — this
 * use-case simply doesn't compute one in that case, rather than computing and discarding it).
 * The "no attached EnforcementSession" check is [EnforcementSessionDao.hasAnySessionFor], not
 * [com.disciplineos.data.entity.MissionPeriod.enforcementProfileId] nullability — see that
 * field's own kdoc for why (a real, separate, unflagged base-doc divergence this batch found but
 * did not fix, since this existence check sidesteps it cleanly).
 *
 * **Hit-rate computation — base doc §4.2: "Computed from MissionLogEntry presence/value against
 * cadenceType and targetDirection over adherenceWindow... a straightforward hit-rate."** Per
 * entry in the window ([MissionLogEntryDao.forMissionSince]), a "hit" is:
 * - [MissionLogEntry.didOccur] `== true`, if non-null (habit/constraint missions, base doc
 *   §3.3's own framing for that field) — regardless of [MissionLogEntry.numericValue].
 * - Else, if [MissionLogEntry.numericValue] is non-null and [GoalMission.targetValue] is
 *   non-null: compared against [GoalMission.targetDirection] ([TargetDirection.INCREASE]/
 *   [TargetDirection.DECREASE] need the value at/past the target in that direction;
 *   [TargetDirection.MAINTAIN] needs the value within [MAINTAIN_TOLERANCE_FRACTION] of it —
 *   `[HYPOTHESIS]`, no tolerance is stated in either spec doc, flagged the same way every other
 *   placeholder constant in this class is).
 * - A note-only entry (both null) does not count as a hit *or* a miss — it's excluded from both
 *   the hit count and the expected-entry count below, matching [MissionLogEntry]'s own kdoc
 *   ("doesn't count toward the hit-rate").
 * - If [GoalMission.targetValue] is null (no numeric target set) but [MissionLogEntry
 *   .numericValue] is present, that entry is treated as a hit by presence alone — there is
 *   nothing to compare it against, and base doc §4.2 opens with "presence/value," presence being
 *   the first-named criterion, not merely a fallback.
 *
 * **Expected-entry count over the window** — how many log entries *should* exist, the
 * denominator for the hit-rate — is derived from [CadenceType]: [CadenceType.DAILY] expects one
 * per day in the window; [CadenceType.WEEKLY] expects `ceil(windowDays / 7.0)`;
 * [CadenceType.CUSTOM_DAYS] has no per-day/per-week schedule stored anywhere on [GoalMission]
 * (Integration Plan doesn't add one) — `[HYPOTHESIS]`, treated as [CadenceType.DAILY]'s
 * expectation pending a real schedule field, flagged rather than silently guessed differently
 * per call; [CadenceType.NONE] expects only that at least one entry exists in the window (any
 * log at all counts as adherence for a cadence-free mission) — denominator is 1, not `windowDays`.
 * A window with fewer actual hits than the expected denominator is a miss for that window; a
 * window meeting or exceeding [AdherenceDecayPolicy.hitRateThreshold] as a fraction of expected
 * is a "met" window.
 *
 * **Decay is on sustained miss *windows*, not single misses** (base doc §4.2), mirroring
 * [ApplyReputationDecayUseCase]'s `consecutiveDaysBelowFloor`-style tracking exactly, applied to
 * [GoalMission.consecutiveWindowsBelowThreshold] instead of
 * [com.disciplineos.data.entity.User.consecutiveDaysBelowFloor]. Each call to [execute] evaluates
 * exactly one window ending at [now] — same "caller invokes once per elapsed period, this
 * use-case does not schedule itself" contract [ApplyReputationDecayUseCase.execute]'s own kdoc
 * states, extended here from "once per day per user" to "once per elapsed
 * [GoalMission.adherenceWindow] per GoalMission" (no scheduler exists yet for either — this is
 * still a real, external-caller-invoked function, not a background job).
 *
 * **Never feeds Tier — base doc §4.2's own heading, restated as a hard boundary:** this use-case
 * never calls [TierTransitionUseCase] and never writes to
 * [com.disciplineos.data.ledger.LedgerDao]. It writes only to
 * [AdherenceLedgerDao]/[GoalMissionDao] — see [AdherenceLedgerEntry]'s kdoc for why that table
 * is structurally, not just conventionally, separate from Reputation/Debt.
 *
 * **Weekly Report callout hook (Integration Plan §4.2) — not built, matching that section's own
 * explicit statement that Weekly Reports themselves are Batch F scope, not G3.** [Result
 * .thresholdCrossing] is the hook: true exactly when this call's decay fired, so whenever Batch F
 * builds Weekly Reports, surfacing this is a read of [AdherenceLedgerEntry.thresholdCrossing]
 * rows, not new computation — same pattern [ApplyReputationDecayUseCase]'s own demotion-event
 * return value already uses for its analogous "something notable happened this call" signal.
 */
class ApplyAdherenceDecayUseCase(
    private val database: DisciplineOsDatabase,
    private val goalMissionDao: GoalMissionDao,
    private val missionLogEntryDao: MissionLogEntryDao,
    private val enforcementSessionDao: EnforcementSessionDao,
    private val adherenceLedgerDao: AdherenceLedgerDao,
    private val policy: AdherenceDecayPolicy,
) {

    /**
     * Evaluates one [GoalMission.adherenceWindow]-length window ending at [now] for
     * [goalMissionId]. See class kdoc for the full scope/hit-rate/decay walkthrough.
     *
     * @return [Result.inScope] false (with empty [Result.entries] and null
     *   [Result.thresholdCrossing]) if [goalMissionId] doesn't exist or is out of Adherence's
     *   base-doc-§4.2 scope — a real, distinct outcome from "in scope but nothing changed this
     *   call," not conflated with it, so a caller can tell "this Mission doesn't get an
     *   Adherence number" apart from "this Mission's Adherence number didn't move today."
     */
    suspend fun execute(goalMissionId: UUID, now: Instant = Instant.now()): Result {
        val goalMission = goalMissionDao.get(goalMissionId) ?: return Result.outOfScope()

        val inScope = when (goalMission.archetype) {
            MissionArchetype.OUTCOME_DRIVEN, MissionArchetype.CONSTRAINT -> true
            MissionArchetype.BEHAVIOR_DRIVEN ->
                !enforcementSessionDao.hasAnySessionFor(goalMissionId)
        }
        if (!inScope) return Result.outOfScope()

        val windowDays = goalMission.adherenceWindow ?: policy.defaultAdherenceWindowDays()
        val windowStart = now.minus(windowDays.toLong(), ChronoUnit.DAYS)

        return database.withTransaction {
            val entriesInWindow = missionLogEntryDao.forMissionSince(goalMissionId, windowStart)
            val hits = entriesInWindow.count { isHit(it, goalMission) }
            val expected = expectedEntryCount(goalMission.cadenceType, windowDays)
            val hitRate = if (expected <= 0) 1.0 else (hits.toDouble() / expected).coerceAtMost(1.0)
            val windowMet = hitRate >= policy.hitRateThreshold()

            val ledgerEntries = mutableListOf<AdherenceLedgerEntry>()
            var thresholdCrossing = false
            var updatedGoalMission = goalMission

            if (windowMet) {
                if (goalMission.consecutiveWindowsBelowThreshold != 0) {
                    updatedGoalMission = updatedGoalMission.copy(consecutiveWindowsBelowThreshold = 0)
                }
            } else {
                val windowsBelow = goalMission.consecutiveWindowsBelowThreshold + 1
                if (windowsBelow >= policy.consecutiveWindowsBelowThresholdForDecay()) {
                    val delta = -policy.decayPerThresholdCrossing()
                    val entry = AdherenceLedgerEntry(
                        id = UUID.randomUUID(),
                        goalMissionId = goalMissionId,
                        delta = delta,
                        appliedAt = now,
                        thresholdCrossing = true,
                    )
                    adherenceLedgerDao.insert(entry)
                    ledgerEntries += entry
                    thresholdCrossing = true
                    // Same "reset the counter once it's actually fired, so the next sustained
                    // pattern starts a fresh window" semantics ApplyReputationDecayUseCase uses
                    // for consecutiveDaysBelowFloor after a demotion fires.
                    updatedGoalMission = updatedGoalMission.copy(consecutiveWindowsBelowThreshold = 0)
                } else {
                    updatedGoalMission = updatedGoalMission.copy(consecutiveWindowsBelowThreshold = windowsBelow)
                }
            }

            // Always re-read, even when this window wrote no new entry — the displayed score
            // must reflect the ledger's actual current sum, not just "did this call write
            // something." A GoalMission's first-ever call establishes adherenceScore = 0.0
            // (COALESCE(SUM(delta), 0.0) on an empty ledger) rather than leaving it null —
            // deliberate: null means "never evaluated," 0.0 means "evaluated, currently neutral."
            val newScore = adherenceLedgerDao.currentValue(goalMissionId)
            if (updatedGoalMission.adherenceScore != newScore) {
                updatedGoalMission = updatedGoalMission.copy(adherenceScore = newScore)
            }
            if (updatedGoalMission != goalMission) {
                goalMissionDao.update(updatedGoalMission)
            }

            Result(
                inScope = true,
                entries = ledgerEntries,
                thresholdCrossing = thresholdCrossing,
                hitRate = hitRate,
            )
        }
    }

    /** See class kdoc's "Hit-rate computation" section for the exact per-entry rule this implements. */
    private fun isHit(entry: MissionLogEntry, goalMission: GoalMission): Boolean {
        entry.didOccur?.let { return it }

        val numericValue = entry.numericValue ?: return false // note-only entry — not a hit
        val targetValue = goalMission.targetValue ?: return true // presence-only, no target to compare against

        return when (goalMission.targetDirection) {
            TargetDirection.INCREASE -> numericValue >= targetValue
            TargetDirection.DECREASE -> numericValue <= targetValue
            TargetDirection.MAINTAIN -> {
                val tolerance = targetValue * MAINTAIN_TOLERANCE_FRACTION
                numericValue in (targetValue - tolerance)..(targetValue + tolerance)
            }
            null -> true // no direction to check against — presence/value alone is the signal
        }
    }

    /** See class kdoc's "Expected-entry count" section for the reasoning behind each branch. */
    private fun expectedEntryCount(cadenceType: CadenceType, windowDays: Int): Int = when (cadenceType) {
        CadenceType.DAILY -> windowDays
        CadenceType.WEEKLY -> ceil(windowDays / 7.0).toInt()
        // [HYPOTHESIS] — CUSTOM_DAYS has no stored per-week schedule on GoalMission to derive a
        // real expectation from; DAILY's expectation is used as a placeholder, not a derivation.
        CadenceType.CUSTOM_DAYS -> windowDays
        CadenceType.NONE -> 1
    }

    data class Result(
        /** False if [goalMissionId] doesn't exist or is out of Adherence's base-doc §4.2 scope. */
        val inScope: Boolean,
        /** Empty unless this call's decay check fired a threshold crossing. */
        val entries: List<AdherenceLedgerEntry> = emptyList(),
        /** True exactly when this call wrote a decay entry — the Weekly Report callout hook (Integration Plan §4.2). */
        val thresholdCrossing: Boolean = false,
        /** This window's computed hit-rate (0.0–1.0), null when [inScope] is false. */
        val hitRate: Double? = null,
    ) {
        companion object {
            fun outOfScope() = Result(inScope = false)
        }
    }

    companion object {
        // [HYPOTHESIS] — no tolerance for TargetDirection.MAINTAIN is stated in either spec
        // doc. 10% chosen only as "a round number with some real slack," not validated.
        private const val MAINTAIN_TOLERANCE_FRACTION = 0.10
    }
}
