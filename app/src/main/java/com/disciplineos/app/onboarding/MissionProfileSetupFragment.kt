package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * **Default-suggestions wiring (ROADMAP.md §5.30 — closes the gap this class's own kdoc used
 * to flag):** §2.8 asks for the blocklist to "default to suggestions drawn from §2.2's flagged
 * categories rather than a blank list, to reduce first-session abandonment." That data exists
 * now (Goal Definition, §2.2, has real content as of an earlier pass) — [onCreateView] kicks
 * off an async read of [com.disciplineos.data.entity.User.flaggedCategories] and pushes the
 * result into [suggestedBlocklist], a Compose [androidx.compose.runtime.State] this Fragment
 * owns, once it resolves.
 *
 * **Blocklist, not allowlist — and not both.** §2.2's own text calls flagged categories
 * "high-value" *and* "high-risk" apps/categories in the same undifferentiated free-text field
 * ([GoalDefinitionScreen][com.disciplineos.app.ui.onboarding.GoalDefinitionScreen] never asked
 * the user to say which is which, and `User.flaggedCategories` is a single `List<String>` with
 * no field distinguishing the two) — so there is no real signal in this codebase's data for
 * which flagged category the user meant as "protect this" versus "restrict this." Blindly
 * splitting the same list into both allowlist and blocklist would misrepresent high-value
 * entries as things to block. The categories field's own hint text and this app's whole
 * premise (restricting distractions during a Mission) both point the same direction: what a
 * user flags here reads as "things I'm tempted by," not "things I want unrestricted access
 * to" — so blocklist is the honest target, allowlist stays untouched (empty by default, same
 * as before this pass), rather than inventing a heuristic split the data doesn't support.
 *
 * **Still just a starting point.** `MissionProfileSetupScreen`'s own kdoc covers this in more
 * detail: the suggestion pre-fills the blocklist field's initial text, fully editable, not a
 * locked default — a user can clear or change it exactly like any hand-typed content.
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
    ): View {
        var suggestedBlocklist by mutableStateOf("")

        loadSuggestedBlocklist { suggestion -> suggestedBlocklist = suggestion }

        return themedComposeView {
            MissionProfileSetupScreen(
                onContinue = { rawName, allowlistRaw, blocklistRaw ->
                    val name = rawName.trim().let {
                        if (it.isEmpty()) DEFAULT_PROFILE_NAME else it
                    }
                    submitProfile(name, parseLines(allowlistRaw), parseLines(blocklistRaw))
                },
                onBack = { findNavController().popBackStack() },
                suggestedBlocklist = suggestedBlocklist,
            )
        }
    }

    /**
     * One category per line, same convention [GoalDefinitionFragment.parseLines] already
     * established for the source data itself — blank entries dropped, not preserved as
     * empty-string lines, matching [parseLines]'s own reasoning for why that matters
     * downstream. Reads [com.disciplineos.data.entity.User.flaggedCategories] directly; an
     * empty or missing list (no categories flagged, or no `User` row yet — shouldn't be
     * reachable via this screen's own nav-graph position, but handled the same defensive way
     * every other screen in this package treats its own "should be impossible" case) resolves
     * to an empty string, which [MissionProfileSetupScreen] already treats as "no suggestion,"
     * matching its pre-this-pass behavior exactly.
     */
    private fun loadSuggestedBlocklist(onLoaded: (String) -> Unit) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val categories = database.userDao().getSingleLocalUser()?.flaggedCategories.orEmpty()
            onLoaded(categories.joinToString(separator = "\n"))
        }
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
