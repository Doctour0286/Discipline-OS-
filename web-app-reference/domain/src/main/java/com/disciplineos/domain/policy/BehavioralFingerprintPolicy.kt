package com.disciplineos.domain.policy

/**
 * Behavioral Fingerprint & Predictive Failure Rules Spec (`04_BEHAVIORAL_FINGERPRINT_RULES_SPEC.md`)
 * §3–4: every rule's trigger condition and minimum sample-size gate. Mirrors
 * [ReputationDecayPolicy]/[ConsequencePolicy]'s exact reasoning — these numbers are stated in
 * the spec itself (unlike Reputation decay's constants, which the PRD explicitly flags
 * [HYPOTHESIS] with no numbers given at all), so this is less "invent a placeholder" and more
 * "isolate spec-given constants behind an interface anyway," for the same testability/single-
 * source-of-truth reason [InterceptionPolicy] gives for PRD §14's countdown durations. Kept as
 * an interface rather than inlined directly into `ComputeBehavioralFingerprintUseCase` so a
 * future product-owner sign-off pass (same shape as §5.5/§5.9/§5.10/§5.15's) can swap these
 * without touching the use-case itself — F5's threshold in particular is explicitly *not*
 * spec-given yet (§3, F5: "left as an explicit placeholder, not a guessed number"), so this
 * interface has to exist regardless of how settled the other four rules' numbers are.
 */
interface BehavioralFingerprintPolicy {
    // --- F1: Time-of-Day Violation Clustering ---
    /** How many of the most recent Missions-with-any-violation F1 scopes its clustering check to. Spec §3, F1: 10. */
    fun f1MissionWindow(): Int
    /** Width of the clustering window in hours. Spec §3, F1: 2. */
    fun f1ClusterWindowHours(): Int
    /** Minimum violations inside one clustering window before it's flagged internally. Spec §3, F1: 3. */
    fun f1MinClusterCount(): Int
    /** Minimum distinct calendar weeks the clustering must hold across before a user-facing alert. Spec §3, F1: 2. */
    fun f1MinDistinctWeeks(): Int
    /** Minimum violation sample size before a user-facing alert. Spec §4: F1 = 10. */
    fun f1MinSampleSize(): Int

    // --- F2: Pre-Mission Cancellation Pattern ---
    /** How many minutes into a Mission still counts as "early" cancellation. Spec §3, F2: 5. */
    fun f2EarlyCancelMinutes(): Int
    /** Lookback window in days. Spec §3, F2: 14. */
    fun f2WindowDays(): Int
    /** Proportion of Missions ending early that trips the trigger. Spec §3, F2: 0.25 (25%). */
    fun f2TriggerProportion(): Double
    /** Minimum Mission sample size before a user-facing alert. Spec §4: F2 = 8. */
    fun f2MinSampleSize(): Int

    // --- F3: Debt Trajectory Slope ---
    /** Lookback window in days for the consecutive-rise check. Spec §3, F3: 7. */
    fun f3WindowDays(): Int
    /** Debt Ceiling proportion Debt must exceed alongside the rising slope. Spec §3, F3: 0.5 (50%). */
    fun f3CeilingProportionThreshold(): Double
    /** Minimum days of Debt-sample history before a user-facing alert. Spec §4: F3 = 7 days. */
    fun f3MinSampleDays(): Int

    // --- F4: Reputation Decline Rate (internal-only for MVP, Spec §3, F4) ---
    /** Projected days-to-tier-floor threshold that flags the internal signal. No spec number given — engineering default, internal-only, never user-facing (see class kdoc). */
    fun f4ProjectedDaysToFloorThreshold(): Int

    // --- F5: Mission Profile Drift ---
    /**
     * Disputes/overrides count against one Mission Profile's allow/blocklist, within
     * [f5WindowDays], that trips the trigger. Spec §3, F5: explicitly left unset by the PRD
     * itself ("this threshold can only be set from real post-launch data") — defaulted
     * conservatively high per the spec's own instruction ("fewer false triggers") rather than
     * guessed at a specific plausible-sounding number. [HYPOTHESIS] in the strongest sense
     * this codebase uses that word: not even a design-intuition placeholder like
     * [HypothesisReputationDecayPolicy]'s numbers, closer to "the maximum value that still lets
     * the mechanism be exercised in tests without ever legitimately firing pre-pilot."
     */
    fun f5DisputeClusterThreshold(): Int
    /** Lookback window in days for F5's dispute/override clustering. No spec number given — engineering default alongside the threshold above. */
    fun f5WindowDays(): Int
}

/**
 * The ONLY implementation that exists pre-pilot, same posture as [HypothesisReputationDecayPolicy]
 * and [HypothesisConsequencePolicy] — restated here rather than cross-referenced so this file is
 * self-contained. F1/F2/F3's numbers below are taken directly from the spec's own stated values
 * (not invented), so this class's [HYPOTHESIS] framing applies most strongly to F4 and F5, whose
 * numbers the spec explicitly does not give.
 */
class HypothesisBehavioralFingerprintPolicy : BehavioralFingerprintPolicy {
    override fun f1MissionWindow(): Int = 10
    override fun f1ClusterWindowHours(): Int = 2
    override fun f1MinClusterCount(): Int = 3
    override fun f1MinDistinctWeeks(): Int = 2
    override fun f1MinSampleSize(): Int = 10

    override fun f2EarlyCancelMinutes(): Int = 5
    override fun f2WindowDays(): Int = 14
    override fun f2TriggerProportion(): Double = 0.25
    override fun f2MinSampleSize(): Int = 8

    override fun f3WindowDays(): Int = 7
    override fun f3CeilingProportionThreshold(): Double = 0.5
    override fun f3MinSampleDays(): Int = 7

    // [HYPOTHESIS], no spec anchor — see interface kdoc. 21 days chosen only as "long enough
    // that a fast, obviously-wrong projection doesn't spam the internal signal," not derived
    // from anything.
    override fun f4ProjectedDaysToFloorThreshold(): Int = 21

    // [HYPOTHESIS], deliberately high per Spec §3 F5's own instruction — see interface kdoc.
    // 999 is not a real product value; it exists so the mechanism can be unit-tested without
    // ever plausibly firing against real usage before Phase 5 sets a real number.
    override fun f5DisputeClusterThreshold(): Int = 999
    override fun f5WindowDays(): Int = 30
}
