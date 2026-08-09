package com.disciplineos.domain.policy

/**
 * Data Model & Schema doc §3.5: `decay_per_missed_day` and `recovery_per_completed_mission`
 * are both [HYPOTHESIS] — explicitly flagged in PRD §42 ("What decay rate for Reputation
 * demotion produces the right felt stakes — too slow and rank stops meaning anything; too
 * fast and it re-approximates immediate demotion?") as needing pilot data, not a value
 * anyone has picked yet.
 *
 * Mirrors [ConsequencePolicy]'s reasoning exactly: `ApplyReputationDecayUseCase` depends on
 * this interface, not a hardcoded number, so building the use-case doesn't require inventing
 * a plausible-sounding constant to make it compile. Kept as a *separate* interface from
 * [ConsequencePolicy] rather than folded in, because these two policies answer different
 * questions on different triggers — [ConsequencePolicy] answers "how much does this specific
 * Violation cost," evaluated once per Violation; this answers "how much does sustained
 * non-compliance or sustained compliance move rank," evaluated on a schedule (Data Model
 * §3.5's formula is stated per-day/per-completed-Mission, not per-Violation). Collapsing them
 * would make a single interface answer two different "per what" questions, which is exactly
 * the kind of conflation the shared-cause guard (§27.2) exists to keep visible rather than
 * hide behind one calculator.
 */
interface ReputationDecayPolicy {
    /** Reputation-rank points lost per missed day (Data Model §3.5's `decay_per_missed_day`). Always >= 0. */
    fun decayPerMissedDay(): Double

    /** Reputation-rank points gained per completed Mission (Data Model §3.5's `recovery_per_completed_mission`). Always >= 0. */
    fun recoveryPerCompletedMission(): Double

    /** Reputation-rank band [value] falls in — see [ReputationBand]. */
    fun bandFor(value: Double): ReputationBand

    /** Consecutive days below a rank's floor required before `demotion_triggered` fires. */
    fun consecutiveDaysBelowFloorForDemotion(): Int
}

/**
 * ROADMAP.md §5.9, resolved 2026-08-09, `[HYPOTHESIS]` pending Phase 5 pilot data. Seven
 * bands spanning the full 0–100 Reputation range, each with a floor (the value at/above
 * which a user is considered "in" that band or higher).
 */
enum class ReputationBand(val floor: Double) {
    UNDISCIPLINED(0.0),
    INCONSISTENT(21.0),
    RELIABLE(41.0),
    DISCIPLINED(55.0),
    RELENTLESS(70.0),
    ELITE(85.0),
    IRON_WILL(95.0),
    ;

    companion object {
        /** Bands ordered highest floor first, so [bandFor]-style lookups can short-circuit on the first match. */
        val DESCENDING = entries.sortedByDescending { it.floor }
    }
}

/**
 * The ONLY implementation that exists pre-pilot — see [ConsequencePolicy]'s
 * [HypothesisConsequencePolicy] for the identical reasoning, restated here rather than
 * cross-referenced so this file is self-contained for whoever swaps it out later.
 *
 * These two numbers are NOT derived from anything — not from the PRD, not from the Data
 * Model doc, not from any research citation the specs elsewhere lean on. They exist only so
 * `ApplyReputationDecayUseCase` and its tests can run before Phase 5 pilot data exists.
 * Swap the implementation, don't tune these numbers in place, once real data justifies a
 * value — same instruction Data Model §3.1 gives for the (cut) Discipline Score weights.
 */
class HypothesisReputationDecayPolicy : ReputationDecayPolicy {
    // [HYPOTHESIS] — no rationale beyond "recovery should modestly outweigh a single day's
    // decay so a user who returns to Missions can visibly climb back, not just stop
    // sliding" — a design intuition, not a validated number. Flagged exactly as such.
    override fun decayPerMissedDay(): Double = 1.0
    override fun recoveryPerCompletedMission(): Double = 1.5

    /**
     * §5.9 sign-off: bands are 0–20 / 21–40 / 41–54 / 55–69 / 70–84 / 85–94 / 95–100.
     * [ReputationBand.DESCENDING] walk finds the highest band whose floor [value] clears —
     * e.g. value=54 matches RELIABLE (floor 41), not DISCIPLINED (floor 55), which is exactly
     * the 41–54 band boundary the sign-off specified. Values below 0 (shouldn't happen given
     * the Ledger formula, but not contractually impossible) fall back to the lowest band
     * rather than throwing, since a band lookup being asked to classify an out-of-spec value
     * should degrade gracefully, not crash a decay run.
     */
    override fun bandFor(value: Double): ReputationBand =
        ReputationBand.DESCENDING.firstOrNull { value >= it.floor } ?: ReputationBand.UNDISCIPLINED

    // §5.9 sign-off: N = 3 consecutive days below a rank's floor before demotion_triggered.
    override fun consecutiveDaysBelowFloorForDemotion(): Int = 3
}
