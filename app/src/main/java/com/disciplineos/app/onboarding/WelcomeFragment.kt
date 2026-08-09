package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R

/**
 * Onboarding, Consent & Interaction Spec §2.1 (Welcome / Product Philosophy) — real content,
 * replacing [OnboardingPlaceholderFragment] at the `welcomeFragment` destination (the
 * onboarding graph's `app:startDestination`).
 *
 * **Purpose, per the spec's own framing:** "not a screen to sell the product, it's a screen
 * to filter for the right user." Unlike [GoalDefinitionFragment], this screen collects no
 * input and writes nothing — it's pure disclosure, so there's no `submitX`-shaped method
 * here, just a single Continue action.
 *
 * **Content requirements (§2.1), each covered by its own string in `strings.xml`, reviewed
 * before writing against the exact standard the spec names:**
 * - States plainly that the app restricts phone functionality during Missions
 *   (`welcome_restricts_body`).
 * - States that higher tiers include confrontational language by design, and that it's a
 *   choice, not a default (`welcome_tone_body`) — checked against
 *   [com.disciplineos.domain.voice.FallbackVoiceBank]'s own actual scoping (that bank's kdoc:
 *   used at Operator/Warden/Iron, never Recruit) before writing this copy, so "a choice, not
 *   a default" describes what the app actually does rather than being aspirational marketing
 *   language that could drift from the real tier behavior over time.
 * - **No urgency/scarcity dark patterns** — the spec's own example of what to avoid
 *   ("Only the disciplined make it past this screen") is exactly the shape
 *   `welcome_fit_body` was written to avoid: it tells the reader this may be more structure
 *   than they want, framed as useful information to have *before* setting anything up, not
 *   as a challenge or a test of whether they'll continue. This is also the Architecture doc
 *   §4.1 app-review risk this screen is explicitly called out against ("deceptive/manipulative
 *   design patterns" — guilt, shame, or manipulative pressure tactics) — onboarding is the
 *   first thing a reviewer sees, so per that section's own reasoning this should be "the
 *   cleanest part of the app," not the place tone gets tested.
 *
 * **No data written.** Nothing in §2.1 asks this screen to persist anything, and no `User`
 * field exists for "has seen Welcome" or similar — same reasoning
 * [GoalDefinitionFragment]'s kdoc gives for not persisting its own free-text field: don't add
 * a field speculatively for something the spec never asked to be stored.
 *
 * **No DAO round-trip test file for this screen, deliberately.** BUILD_PLAN.md's Batch B
 * verification checklist calls for a DAO round-trip test per screen, but that checklist item
 * exists to cover screens with real persistence logic to regress-test
 * ([GoalDefinitionFragmentTest], [MissionProfileSetupFragmentTest] both exist for exactly
 * that reason). This screen has no DAO call, no branch logic, and no field to round-trip —
 * a test file here would only be able to assert that string resources exist, which the
 * Android resource compiler already guarantees at build time. Revisit if that stops being
 * true (e.g. if this screen ever gains a "mark onboarding started" write).
 */
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val continueButton = view.findViewById<Button>(R.id.welcomeContinueButton)
        continueButton.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_goalDefinition)
        }
    }
}
