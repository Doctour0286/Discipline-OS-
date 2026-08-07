package com.disciplineos.domain.policy

import com.disciplineos.data.entity.Tier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * PRD §14 (Distraction Interception System): "Countdown length scales with Tier: 5 seconds at
 * Recruit (informational), 10 seconds at Operator, 10 seconds with no early dismissal at
 * Warden, 15 seconds with mandatory reason entry at Iron. The mandatory reason entry applies to
 * confirming Break Commitment, not to using the Crisis Exit."
 *
 * These durations are stated explicitly in the PRD text (not flagged [HYPOTHESIS] in §42
 * anywhere), so unlike [ConsequencePolicy]'s constants, this is a real spec value being
 * encoded directly, not a placeholder awaiting pilot data. Kept as its own pure,
 * `:domain`-layer object (no Android dependency) so the interception screen's countdown timing
 * logic is unit-testable the same way `Metrics.kt`'s formulas are, and so `:app`'s
 * `MissionAccessibilityService`/overlay code has one canonical source for these numbers rather
 * than each screen re-deriving them.
 */
object InterceptionPolicy {

    /** PRD §14 countdown length by tier. */
    fun countdownDuration(tier: Tier): Duration = when (tier) {
        Tier.RECRUIT -> 5.seconds
        Tier.OPERATOR -> 10.seconds
        Tier.WARDEN -> 10.seconds
        Tier.IRON -> 15.seconds
    }

    /**
     * PRD §14: "10 seconds with no early dismissal at Warden" — Warden's Return-to-Mission /
     * Break-Commitment choice cannot be confirmed before the countdown finishes. Recruit and
     * Operator's countdowns are "informational" (§14) / casual-exit tiers, so early dismissal
     * is allowed there; Iron's 15s-with-reason-entry framing implies the same no-early-dismissal
     * floor as Warden (a mandatory reason entry that could itself be submitted early would defeat
     * the purpose of pairing a countdown with a reflection requirement), so Iron is included here
     * too even though §14's sentence names Warden explicitly and doesn't repeat the phrase for
     * Iron — recorded as an inference, not a literal quote, since a future spec revision could
     * make Iron's rule explicit and this should be checked against that if it happens.
     */
    fun allowsEarlyDismissal(tier: Tier): Boolean = when (tier) {
        Tier.RECRUIT, Tier.OPERATOR -> true
        Tier.WARDEN, Tier.IRON -> false
    }

    /** PRD §14: mandatory reason entry to confirm Break Commitment applies only at Iron. */
    fun requiresBreakCommitmentReason(tier: Tier): Boolean = tier == Tier.IRON

    /**
     * PRD §14/§22.1: Recruit's interception content is informational, not Warden Voice at all.
     * Operator gets "standard violation feedback" from Warden Voice per §22.1's own text
     * ("Used at Warden and Iron tiers, and for standard violation feedback at Operator").
     */
    fun usesWardenVoice(tier: Tier): Boolean = tier != Tier.RECRUIT

    /**
     * PRD §12.4.2 / §12.4.4: the general "this is too much right now" control is available at
     * every tier (§12.2 lists it as carrying across every tier) but is superseded on the
     * interception screen specifically at Iron by the purpose-built Iron-Tier Crisis Exit
     * (§12.4.4: "At Iron, the interception screen instead surfaces the Iron-Tier Crisis Exit
     * ... for the full duration of the countdown — a control purpose-built for this exact
     * screen rather than the general-purpose §12.4.2 control"). This function answers which
     * affordance the interception screen itself should render — not whether §12.4.2 exists
     * elsewhere in the app (it does, at every tier, per §12.2).
     */
    fun interceptionScreenStabilityControl(tier: Tier): StabilityControl = when (tier) {
        Tier.IRON -> StabilityControl.IRON_CRISIS_EXIT
        else -> StabilityControl.EXPLICIT_DOWNGRADE
    }

    enum class StabilityControl { EXPLICIT_DOWNGRADE, IRON_CRISIS_EXIT }
}
