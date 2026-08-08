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
 * **Next-action resolution, and why it isn't a passed-in argument:** an earlier version of
 * this class took the next action's resource ID as a `Bundle` argument (`android:defaultValue
 * ="@id/action_xxx"` in the nav-graph XML). Dropped without being run through real CI — there
 * is no existing precedent anywhere in this codebase for resolving a resource ID via an
 * `<argument>` default value (`MissionInterceptionActivity`'s Intent-extras pattern, the only
 * precedent that does exist, passes primitive values it constructs itself, never a resource
 * ID), and per ROADMAP.md §4 item 2's standing caution about reasoning past what's actually
 * been compiler-verified, an untested resource-resolution trick was the wrong thing to build a
 * skeleton on. Instead, this class asks [findNavController] for its *current destination's*
 * own outgoing actions directly at click-time — an API shape with real, ordinary precedent in
 * Navigation Component's documented usage, not a resource-default trick unique to this file.
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

        // Look up whether this destination has an outgoing action rather than trusting a
        // passed-in ID — see class kdoc above for why. firstMissionSchedulingFragment (the
        // last screen, §2.9) correctly declares no <action> at all, so this list is empty
        // there and the button stays hidden, same end result as the original design without
        // relying on any untested resource-default resolution to get there.
        val nextAction = findNavController().currentDestination?.actions?.get(0)
        if (nextAction != null) {
            val actionId = findNavController().currentDestination!!.actions.keyAt(0)
            nextButton.setOnClickListener { findNavController().navigate(actionId) }
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
