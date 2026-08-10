package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.onboarding.MissionProfileSetupScreen
import com.disciplineos.app.ui.theme.themedComposeView
import com.disciplineos.data.entity.MissionProfile
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.8 (Mission Profile Setup) — real content,
 * replacing [OnboardingPlaceholderFragment] at this one destination.
 *
 * **What this screen does:** collects a name plus an allowlist/blocklist (one package id per
 * line, free text — see the layout's own kdoc for why this is plain `EditText` rather than an
 * installed-app picker) and writes the first [MissionProfile] row for the local user, via
 * [MissionProfileDao][com.disciplineos.data.dao.MissionProfileDao].insert directly — no
 * `:domain` use-case wraps this (unlike [TierSelectionFragment]'s
 * `TierTransitionUseCase.selectInitialTier`), because there is no transactional or
 * multi-step logic here to coordinate: one row, one insert, no other table needs to change in
 * the same transaction. Introducing a use-case for a single unconditional insert would be
 * exactly the kind of premature structure the Data Model doc's own §3.1 reasoning (quoted in
 * [com.disciplineos.data.db.DisciplineOsDatabase]'s migration-policy kdoc) argues against —
 * add one if/when a second call site or a real invariant to enforce actually shows up.
 *
 * **Closes a real, previously-undocumented spec gap:** [MissionProfile] itself did not exist
 * anywhere in this codebase before this pass — see that entity's own kdoc for the full
 * account of `Mission.missionProfileId` having referenced a table that was never defined, in
 * either the code or the Data Model doc. This screen is what finally gives that id something
 * real to point at.
 *
 * **What this screen does NOT do, and why:** §2.8 asks for suggested defaults "drawn from
 * §2.2's flagged categories" — that data doesn't exist (Goal Definition, §2.2, is still
 * [OnboardingPlaceholderFragment] content two steps earlier in this same flow), so the
 * allowlist/blocklist fields start empty rather than pre-filled with an invented guess.
 * Flagged in ROADMAP.md §5, not silently worked around.
 *
 * **Design-system pass (ROADMAP.md §5.26/onboarding-wide follow-up):** UI now lives in
 * [MissionProfileSetupScreen], hosted via [themedComposeView] (ROADMAP.md §5.29 — replaces the
 * inline `ComposeView(requireContext()).apply { ... }` boilerplate every onboarding Fragment
 * previously repeated). `parseLines`, the insert, and its re-entry guard are unchanged.
 */
class MissionProfileSetupFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = themedComposeView {
        MissionProfileSetupScreen(
            onContinue = { rawName, allowlistRaw, blocklistRaw ->
                val name = rawName.trim().let {
                    if (it.isEmpty()) DEFAULT_PROFILE_NAME else it
                }
                submitProfile(name, parseLines(allowlistRaw), parseLines(blocklistRaw))
            },
            onBack = { findNavController().popBackStack() },
        )
    }

    /**
     * One package id per line; blank lines and surrounding whitespace are dropped rather than
     * stored as empty-string entries — a blank line is almost certainly incidental (trailing
     * newline, accidental double-Enter), not a package id the user meant to add, and storing
     * it would silently corrupt every downstream consumer of this list (e.g.
     * `InterceptionController`'s blocklist-membership check, which has no reason to expect or
     * handle an empty-string entry).
     */
    private fun parseLines(raw: String?): List<String> =
        raw.orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun submitProfile(name: String, allowlist: List<String>, blocklist: List<String>) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)

            // Same re-entry guard as TierSelectionFragment/TierConfirmationFragment (see
            // either's kdoc for the full reasoning) applied to this table instead of `users`:
            // Back-then-resubmit or a slow double-tap on Continue must not create a second
            // MissionProfile row for a user who already has one. MissionProfileDao has no
            // @Update yet (see that DAO's kdoc — real profile editing is future work), so
            // "already has one" here means "skip the insert and just move on," matching
            // exactly how the tier screens treat "a User row already exists."
            val userId = database.userDao().getSingleLocalUser()?.id
            if (userId != null && database.missionProfileDao().mostRecentFor(userId) == null) {
                database.missionProfileDao().insert(
                    MissionProfile(
                        id = UUID.randomUUID(),
                        userId = userId,
                        name = name,
                        allowlist = allowlist,
                        blocklist = blocklist,
                        createdAt = Instant.now(),
                    )
                )
            }
            // userId == null (no User row yet — onboarding was somehow reached out of order)
            // is not otherwise handled here: this screen is only reachable via
            // onboarding_nav_graph.xml's Tier Selection / Tier Confirmation actions, both of
            // which already guarantee a User row exists before navigating here. Silently
            // skipping the insert rather than crashing is still the safer of the two options
            // if that invariant is ever violated by a future change to the graph.
            findNavController().navigate(R.id.action_missionProfileSetup_to_coreDataConsent)
        }
    }

    companion object {
        private const val DEFAULT_PROFILE_NAME = "Default"
    }
}
