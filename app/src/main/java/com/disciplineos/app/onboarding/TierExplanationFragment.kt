package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R

/**
 * Onboarding, Consent & Interaction Spec §2.3 (Tier Explanation) — real content, replacing
 * [OnboardingPlaceholderFragment] at the `tierExplanationFragment` destination. Spec's own
 * label for this section: "highest-stakes copy in onboarding."
 *
 * **Read-only comparison screen, no selection UI.** [TierSelectionFragment] (§2.4, one screen
 * later) is where the actual choice happens — this screen exists purely so the user can
 * compare all four tiers before that choice, per §2.3's explicit "side by side, not
 * sequentially revealed" requirement. Continue always advances to Tier Selection regardless
 * of what the user scrolled through here; there is nothing to persist and no re-entry guard
 * needed, since this screen reads and writes no [com.disciplineos.data.entity.User] state at
 * all (unlike [GoalDefinitionFragment] or [TierSelectionFragment] itself).
 *
 * **Three content requirements per tier (§2.3), each satisfied per-tier in `strings.xml`'s
 * `tier_explanation_*` block — see that block's own comment for the full sourcing account:**
 * - Enforcement mechanics, read directly from
 *   [com.disciplineos.domain.policy.InterceptionPolicy]'s real countdown/early-dismissal/
 *   reason-entry functions rather than invented for this copy.
 * - Voice tone, quoting an actual line from
 *   [com.disciplineos.domain.voice.FallbackVoiceBank] (this codebase's own authored content).
 * - **The no-casual-exit disclosure at Warden/Iron** — §2.3's own words: "this is the single
 *   most important disclosure in the whole flow." Given its own dedicated line per tier
 *   (`tier_explanation_*_exit`) rather than folded into the enforcement paragraph, so it can't
 *   be skimmed past inside a longer block of text.
 *
 * **Anti-pattern compliance (§2.3's explicit requirement) is structural, not just a copy
 * choice:** [R.layout.fragment_tier_explanation] gives every tier's card one identical
 * template — same section order, same text sizes, same spacing, no per-tier color or
 * emphasis — so no tier can end up visually coded as "the serious choice" independent of
 * what any individual string says. See that layout file's own comment for the full reasoning.
 *
 * **No DAO round-trip test file for this screen, deliberately** — no persistence, no DAO
 * call, no branch logic here to regress-test (same reasoning
 * [GoalDefinitionFragmentTest]/[MissionProfileSetupFragmentTest] exist for the screens that
 * do have real persistence logic — see their own kdocs).
 */
class TierExplanationFragment : Fragment(R.layout.fragment_tier_explanation) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val continueButton = view.findViewById<Button>(R.id.tierExplanationContinueButton)
        val backButton = view.findViewById<Button>(R.id.tierExplanationBackButton)

        backButton.setOnClickListener { findNavController().popBackStack() }
        continueButton.setOnClickListener {
            findNavController().navigate(R.id.action_tierExplanation_to_tierSelection)
        }
    }
}
