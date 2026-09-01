package com.disciplineos.domain.policy

/**
 * Base design doc §4.2 ("Adherence — resolved as a real, lightweight consequence path") /
 * Integration Plan §4.3. Mirrors [ReputationDecayPolicy]'s reasoning exactly:
 * `ApplyAdherenceDecayUseCase` depends on this interface, not hardcoded numbers, so building the
 * use-case doesn't require inventing plausible-sounding constants to make it compile.
 *
 * **`[HYPOTHESIS]` per base doc §4.2's own closing paragraph:** "exact decay formula and window
 * length are `[HYPOTHESIS]`... This needs pilot data, not a guess dressed as a default." Kept as
 * a *separate* interface from [ReputationDecayPolicy] for the same reason that one is kept
 * separate from [ConsequencePolicy] — Adherence and Reputation are two different consequence
 * tracks, "separate, never merged" per base doc §4.2's own heading, and folding their policies
 * into one interface would blur exactly the separation the schema-level split
 * ([com.disciplineos.data.adherence.AdherenceLedgerEntry] being a physically distinct table from
 * [com.disciplineos.data.ledger.LedgerEntry]) exists to keep visible.
 */
interface AdherenceDecayPolicy {
    /**
     * Default rolling-window length in days, used when a [com.disciplineos.data.entity.GoalMission]
     * doesn't set [com.disciplineos.data.entity.GoalMission.adherenceWindow] explicitly (it's
     * nullable per Integration Plan §2.1). Base doc §4.2 names the window itself as `[HYPOTHESIS]`
     * — this is that placeholder default, not a derived value.
     */
    fun defaultAdherenceWindowDays(): Int

    /**
     * Hit-rate (0.0–1.0) at/above which a window counts as "met," per base doc §4.2's
     * "straightforward hit-rate" framing. `[HYPOTHESIS]` — no formula in either spec doc states
     * this threshold as a fraction; this is this batch's own placeholder pending pilot data,
     * flagged the same way `plannedDurationMin`'s 25-minute default and §5.9's tier bands both
     * were before real usage data existed.
     */
    fun hitRateThreshold(): Double

    /**
     * Consecutive windows below [hitRateThreshold] required before a decay-threshold crossing
     * fires — base doc §4.2: "decays on sustained miss patterns, not single misses." Mirrors
     * [ReputationDecayPolicy.consecutiveDaysBelowFloorForDemotion]'s exact role for the
     * Reputation equivalent, applied here to windows instead of days.
     */
    fun consecutiveWindowsBelowThresholdForDecay(): Int

    /** Adherence-score points lost when a decay-threshold crossing fires. Always >= 0. */
    fun decayPerThresholdCrossing(): Double
}

/**
 * The ONLY implementation that exists pre-pilot — see [ReputationDecayPolicy]'s
 * [HypothesisReputationDecayPolicy] for the identical reasoning, restated here rather than
 * cross-referenced so this file is self-contained for whoever swaps it out later.
 *
 * None of these four numbers are derived from anything — not the PRD, not the Data Model doc,
 * not the base design doc, not any research citation the specs elsewhere lean on. They exist
 * only so `ApplyAdherenceDecayUseCase` and its tests can run before real pilot data exists.
 * Swap the implementation, don't tune these numbers in place, once real data justifies a value.
 */
class HypothesisAdherenceDecayPolicy : AdherenceDecayPolicy {
    // [HYPOTHESIS] — 7 days chosen only because it's the shortest window that can express a
    // WEEKLY cadenceType's full cycle at least once; no rationale beyond that, not validated.
    override fun defaultAdherenceWindowDays(): Int = 7

    // [HYPOTHESIS] — 0.7 (70%) chosen as "more hits than misses, with real headroom for an
    // off day," a design intuition mirroring HypothesisReputationDecayPolicy's own stated
    // non-derivation, not a validated number.
    override fun hitRateThreshold(): Double = 0.7

    // [HYPOTHESIS] — 2 consecutive windows, chosen for the same "don't demote/decay on one
    // bad stretch" reasoning HypothesisReputationDecayPolicy's N=3 days gives, scaled down
    // because a "window" here is already multi-day (unlike Reputation's single-day unit), so
    // 2 windows already spans at least 2x defaultAdherenceWindowDays() worth of sustained miss.
    override fun consecutiveWindowsBelowThresholdForDecay(): Int = 2

    // [HYPOTHESIS] — mirrors HypothesisReputationDecayPolicy.decayPerMissedDay()'s 1.0 value
    // and its identical "no rationale beyond an intuition" caveat.
    override fun decayPerThresholdCrossing(): Double = 10.0
}
