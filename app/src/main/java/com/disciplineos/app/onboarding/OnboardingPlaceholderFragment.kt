package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R

/**
 * ROADMAP.md Phase 3 — navigation skeleton, not real screen content. Onboarding, Consent &
 * Interaction Spec §1 defines nine sequential screens (Welcome → Goal Definition → Tier
 * Explanation → Tier Selection → [Iron Calibration Gate, conditional] → Mission Profile Setup
 * → Core Data Consent → Unsupervised Reliability Opt-In → First Mission Scheduling); §2 gives
 * each one detailed, non-trivial content requirements (§2.3's Tier Explanation alone specifies
 * side-by-side tier cards, exact disclosure language, and an explicit anti-pattern to avoid).
 *
 * Building real content for all nine in one pass — before any of it has been through a real
 * compiler, per ROADMAP.md §4 item 2's standing caution — repeats the exact failure mode this
 * project has already hit twice (§5.16, §5.17: code that looked right, broke on first real
 * CI run). This class exists so the navigation graph, argument-passing, and screen-to-screen
 * flow can be built, pushed, and CI-verified as one small, correct piece *before* any single
 * screen's real content is written on top of it — matching this project's established
 * incremental-slice discipline (see DebugSeeder's kdoc for the same reasoning applied
 * elsewhere: infrastructure verified in isolation before being relied upon).
 *
 * **This is explicitly not a shortcut to avoid writing nine Fragments** — each real onboarding
 * screen, when built, should almost certainly be its own dedicated Fragment (or Composable, if
 * that decision is ever revisited) with its own layout and content-specific logic, not a
 * variant of this class. This placeholder's only job is to prove the skeleton works: it shows
 * which step it represents, its position in the sequence, and a Next/Back pair wired to the
 * real nav graph — not to preview any real screen's design or copy.
 *
 * **Next-action resolution — second revision, after a real compile failure.** An earlier
 * version of this class took the next action's resource ID as a `Bundle` argument
 * (`android:defaultValue="@id/action_xxx"` in the nav-graph XML). That was dropped before
 * ever reaching CI, in favor of asking `findNavController().currentDestination?.actions` for
 * the current destination's own outgoing action at click-time — reasoned at the time to be
 * safer because it used real Navigation Component API surface rather than an untested
 * XML-resource-as-argument-default trick.
 *
 * **That reasoning was wrong, and CI caught it (ROADMAP.md, `build-and-test` run #12):**
 * `NavDestination.actions` is `private` in the Navigation Component version this project
 * pins (2.7.7) — `Cannot access 'actions': it is private in 'NavDestination'`, a real
 * `:app:compileDebugKotlin` failure, not a lint warning. The mistake was treating "this uses
 * real Navigation Component API" as equivalent to "this is public API" without actually
 * checking — the same category of error the resource-default rewrite was trying to avoid in
 * the first place, just relocated rather than eliminated.
 *
 * **Fix, third attempt — deliberately the most conservative option, not another guess:**
 * `NavDestination` does expose one small, stably-public lookup: `getAction(actionId: Int):
 * NavAction?`, a single-ID accessor rather than the private backing map. That means "ask the
 * current destination what its outgoing action is" isn't answerable generically through
 * public API at all — something has to supply *which* action ID to ask about. Rather than
 * reopening the resource-default-argument question a second time with no way to compiler-
 * verify it in this authoring environment, the next action for each destination is hardcoded
 * here as a plain Kotlin `when` on `currentDestination?.id`, matched against the graph's own
 * `R.id.*` action constants (generated, stable, already relied on correctly elsewhere in this
 * codebase — e.g. `R.layout.*`/`R.id.*` usage two lines below in this same class). This is
 * less elegant than a generic lookup, but every piece of it — `View.findViewById`-style
 * generated `R` references, a `when` expression, `NavController.navigate(Int)` — is API this
 * codebase has already used successfully and had compiler-verified (`MissionInterceptionActivity`,
 * this same class's own `R.id.stepTitleText` etc. above). Nothing here is being trusted on
 * the strength of "should work" a third time.
 */
class OnboardingPlaceholderFragment : Fragment(R.layout.fragment_onboarding_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val stepTitleText = view.findViewById<TextView>(R.id.stepTitleText)
        val stepProgressText = view.findViewById<TextView>(R.id.stepProgressText)
        val backButton = view.findViewById<Button>(R.id.backButton)
        val nextButton = view.findViewById<Button>(R.id.nextButton)

        val title = arguments?.getString(ARG_TITLE) ?: "Untitled step"
        val stepNumber = arguments?.getInt(ARG_STEP_NUMBER) ?: 0
        val totalSteps = arguments?.getInt(ARG_TOTAL_STEPS) ?: 0

        stepTitleText.text = title
        stepProgressText.text =
            getString(R.string.onboarding_placeholder_step_progress, stepNumber, totalSteps)

        // Back is the nav graph's default back-stack behavior — no custom handling needed for
        // a skeleton; a real screen may need to intercept back (e.g. to confirm discarding
        // input), but that's real-screen behavior, not something this placeholder should
        // pretend to demonstrate.
        backButton.setOnClickListener { findNavController().popBackStack() }

        // Explicit per-destination mapping — see class kdoc above for why this replaced the
        // generic (but privately-inaccessible) NavDestination.actions lookup. Each branch's
        // action ID is the exact <action android:id="@+id/action_..."> already declared for
        // that destination in onboarding_nav_graph.xml — kept in sync manually since Safe Args
        // isn't in use in this project (no libs.versions.toml / Safe Args plugin anywhere in
        // settings.gradle.kts or app/build.gradle.kts as of this session).
        //
        // tierSelectionFragment, tierConfirmationFragment, missionProfileSetupFragment,
        // goalDefinitionFragment, welcomeFragment, tierExplanationFragment, and
        // coreDataConsentFragment are deliberately absent from this map — all seven
        // destinations now run their own real Fragment classes (TierSelectionFragment /
        // TierConfirmationFragment / MissionProfileSetupFragment / GoalDefinitionFragment /
        // WelcomeFragment / TierExplanationFragment / CoreDataConsentFragment), not this
        // placeholder class, so their branches here would be dead code:
        // findNavController().currentDestination?.id can never actually equal any of those
        // seven IDs while this placeholder's own onViewCreated is running, since a different
        // Fragment class is what's hosted at those destinations now. Removed rather than left
        // in as unreachable scaffolding — leaving a mapping for a destination this class no
        // longer serves is the same category of drift ROADMAP.md's own conventions ask to
        // flag, just one step removed from §5.20's stale-argument case (a mapping nothing can
        // ever hit, instead of an argument nothing reads).
        val nextActionId = when (findNavController().currentDestination?.id) {
            R.id.ironCalibrationGateFragment -> R.id.action_ironCalibrationGate_to_missionProfileSetup
            R.id.unsupervisedReliabilityOptInFragment -> R.id.action_unsupervisedReliabilityOptIn_to_firstMissionScheduling
            // firstMissionSchedulingFragment (§2.9, last screen) falls through to null —
            // it declares no outgoing <action> in the graph, matching the hidden-button
            // behavior below.
            else -> null
        }

        if (nextActionId != null) {
            nextButton.setOnClickListener { findNavController().navigate(nextActionId) }
        } else {
            nextButton.visibility = View.GONE
        }
    }

    companion object {
        const val ARG_TITLE = "title"
        const val ARG_STEP_NUMBER = "stepNumber"
        const val ARG_TOTAL_STEPS = "totalSteps"
    }
}
