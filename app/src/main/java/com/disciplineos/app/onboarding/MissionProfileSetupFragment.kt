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
import com.disciplineos.app.applist.InstalledAppsProvider
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.onboarding.AppPickerScreen
import com.disciplineos.app.ui.onboarding.AppSelectionEntry
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
 * **What this screen does:** collects a name plus an allowlist/blocklist, each chosen via an
 * installed-app picker (see [AppPickerScreen]'s kdoc for why this replaced free-typed package
 * ids), and writes the first [MissionProfile] row for the local user, via
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
 * **Single Compose host, two "screens."** [MissionProfileSetupScreen] and [AppPickerScreen]
 * are both hosted from this one [onCreateView] via a local [pickerTarget] state, rather than
 * two separate nav-graph destinations — the picker is a modal-like sub-step of this screen,
 * not an independently navigable one (nothing else in the graph would ever link to it), so a
 * second `fragment`/`action` pair in `onboarding_nav_graph.xml` would be structure this screen
 * doesn't need. [installedApps] is loaded once per Fragment instance (a synchronous
 * [android.content.pm.PackageManager] query — see [InstalledAppsProvider]'s kdoc for why no
 * async/loading state is needed here) and shared by both allowlist and blocklist picker
 * invocations.
 *
 * **No more auto-suggested blocklist (this pass — was present, based on free text, before
 * it).** §2.2's flagged categories
 * ([com.disciplineos.data.entity.User.flaggedCategories]) are free-typed category names like
 * "social media" or "news," not package identifiers — there was never a real mapping from
 * that data to specific installed packages; the prior free-text version only *appeared* to
 * pre-fill correctly because it copied category names directly into a text field that never
 * actually validated they were package ids either. Now that the blocklist is a real picker
 * backed by actual installed packages, carrying that same category-name text over would just
 * be invalid pre-selections. Rather than inventing a fuzzy category→package matching heuristic
 * (a new and unvalidated piece of logic, out of scope for this pass), the blocklist starts
 * empty like the allowlist; [suggestedBlocklistNote] is passed as `false` accordingly. A real
 * category→installed-app suggestion feature, if wanted later, should be its own scoped and
 * tested piece of work, not a byproduct of this one.
 *
 * **Design-system pass (ROADMAP.md §5.26/onboarding-wide follow-up):** UI now lives in
 * [MissionProfileSetupScreen]/[AppPickerScreen], hosted via [themedComposeView] (ROADMAP.md
 * §5.29 — replaces the inline `ComposeView(requireContext()).apply { ... }` boilerplate every
 * onboarding Fragment previously repeated). The insert and its re-entry guard are unchanged.
 */
class MissionProfileSetupFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val installedApps = InstalledAppsProvider.loadLaunchableApps(requireContext())
        val labelsByPackage = installedApps.associate { it.packageName to it.label }

        var allowlistPackages by mutableStateOf<Set<String>>(emptySet())
        var blocklistPackages by mutableStateOf<Set<String>>(emptySet())
        var pickerTarget by mutableStateOf<PickerTarget?>(null)

        fun selectionFor(packages: Set<String>): List<AppSelectionEntry> =
            packages
                .map { packageName ->
                    AppSelectionEntry(
                        packageName = packageName,
                        label = labelsByPackage[packageName] ?: packageName,
                    )
                }
                .sortedBy { it.label.lowercase() }

        return themedComposeView {
            when (pickerTarget) {
                null -> MissionProfileSetupScreen(
                    onContinue = { rawName ->
                        val name = rawName.trim().let {
                            if (it.isEmpty()) DEFAULT_PROFILE_NAME else it
                        }
                        submitProfile(
                            name,
                            allowlistPackages.toList(),
                            blocklistPackages.toList(),
                        )
                    },
                    onBack = { findNavController().popBackStack() },
                    onAllowlistPickerRequested = { pickerTarget = PickerTarget.ALLOWLIST },
                    onBlocklistPickerRequested = { pickerTarget = PickerTarget.BLOCKLIST },
                    allowlistSelection = selectionFor(allowlistPackages),
                    blocklistSelection = selectionFor(blocklistPackages),
                )
                PickerTarget.ALLOWLIST -> AppPickerScreen(
                    title = getString(R.string.mission_profile_setup_allowlist_label),
                    apps = installedApps,
                    selectedPackages = allowlistPackages,
                    onToggle = { packageName ->
                        allowlistPackages = allowlistPackages.toggle(packageName)
                    },
                    onDone = { pickerTarget = null },
                    onBack = { pickerTarget = null },
                )
                PickerTarget.BLOCKLIST -> AppPickerScreen(
                    title = getString(R.string.mission_profile_setup_blocklist_label),
                    apps = installedApps,
                    selectedPackages = blocklistPackages,
                    onToggle = { packageName ->
                        blocklistPackages = blocklistPackages.toggle(packageName)
                    },
                    onDone = { pickerTarget = null },
                    onBack = { pickerTarget = null },
                )
            }
        }
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (contains(value)) this - value else this + value

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

    private enum class PickerTarget { ALLOWLIST, BLOCKLIST }

    companion object {
        private const val DEFAULT_PROFILE_NAME = "Default"
    }
}
