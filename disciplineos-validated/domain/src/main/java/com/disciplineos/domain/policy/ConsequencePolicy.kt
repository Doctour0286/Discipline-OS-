package com.disciplineos.domain.policy

import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.ViolationType

/**
 * Data Model & Schema doc §3.4/§3.5: `violation_penalty` (Debt) and `decay_per_missed_day`
 * (Reputation) are both explicitly [HYPOTHESIS] — tier-dependent constants with no value
 * given anywhere in the spec, flagged in PRD §42 as needing pilot data (Phase 5 of
 * ROADMAP.md) before they can be validated.
 *
 * This interface exists so [com.disciplineos.domain.usecase.RecordViolationUseCase] depends
 * on a *policy*, not a hardcoded number. Nobody writing the use-case should have to invent a
 * plausible-sounding constant just to make the code compile — that's exactly the "invented
 * precision" failure mode the Data Model doc's own §3.1 (cut Discipline Score composite)
 * was written to avoid. A real implementation gets swapped in once Phase 5 produces actual
 * validated numbers; until then, [HypothesisConsequencePolicy] below is the only
 * implementation, and it's loud about being a placeholder every place it's used.
 */
interface ConsequencePolicy {
    /** Debt delta to apply for a single Violation at the given tier. Always >= 0. */
    fun debtPenalty(tier: Tier, violationType: ViolationType): Double

    /** Reputation delta to apply for a single Violation at the given tier. Always <= 0. */
    fun reputationPenalty(tier: Tier, violationType: ViolationType): Double
}

/**
 * The ONLY implementation that exists pre-pilot. Every value here is a placeholder chosen
 * to be directionally plausible (higher tiers cost more) but is explicitly NOT validated —
 * see Data Model doc §8's open-items table and ROADMAP.md Phase 5.
 *
 * Do not read these numbers as "the design." They exist so the rest of Phase 1's logic
 * (transactions, shared-cause guard, dispute reversal) can be built and tested without
 * waiting on Phase 5 data that doesn't exist yet. Swap this implementation out — don't tune
 * these numbers in place — once real pilot data justifies specific values, per the same
 * "fit, don't guess" instruction Data Model §3.1 gives for the (cut) Discipline Score
 * weights.
 */
class HypothesisConsequencePolicy : ConsequencePolicy {
    // [HYPOTHESIS] — flat multiplier per tier, not derived from anything. Placeholder only.
    private val tierSeverityMultiplier = mapOf(
        Tier.RECRUIT to 1.0,
        Tier.OPERATOR to 1.5,
        Tier.WARDEN to 2.5,
        Tier.IRON to 4.0,
    )

    // [HYPOTHESIS] — base units are arbitrary; only relative ordering (early_exit costs less
    // than blocklist_access) reflects any real reasoning, and even that is a guess.
    private val baseDebtByType = mapOf(
        ViolationType.BLOCKLIST_ACCESS to 30.0,
        ViolationType.EARLY_EXIT to 20.0,
        ViolationType.NON_START to 15.0,
    )

    private val baseReputationByType = mapOf(
        ViolationType.BLOCKLIST_ACCESS to -3.0,
        ViolationType.EARLY_EXIT to -2.0,
        ViolationType.NON_START to -1.0,
    )

    override fun debtPenalty(tier: Tier, violationType: ViolationType): Double {
        val base = baseDebtByType.getValue(violationType)
        val multiplier = tierSeverityMultiplier.getValue(tier)
        return base * multiplier
    }

    override fun reputationPenalty(tier: Tier, violationType: ViolationType): Double {
        val base = baseReputationByType.getValue(violationType)
        val multiplier = tierSeverityMultiplier.getValue(tier)
        return base * multiplier
    }
}
