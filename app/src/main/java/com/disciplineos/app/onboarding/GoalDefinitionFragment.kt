package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.onboarding.GoalDefinitionScreen
import com.disciplineos.app.ui.theme.DisciplineOsTheme
import com.disciplineos.data.entity.User
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.2 (Goal Definition) — real content, replacing
 * [OnboardingPlaceholderFragment] at this destination.
 *
 * **What this screen does:** collects free-text goal description (not persisted anywhere —
 * §2.2 doesn't ask for it to be stored, only surfaced back to the user in-flow; no `User`
 * field exists for it and none is added speculatively) and a flagged-categories list, written
 * to [com.disciplineos.data.entity.User.flaggedCategories] — same one-per-line plain-text
 * convention as [MissionProfileSetupFragment]'s allowlist/blocklist, same reasoning (no
 * installed-app/category picker UI exists in this project yet).
 *
 * **This screen creates the User row, in the common case — a real, load-bearing fact, not an
 * implementation detail.** Found while building this screen, not assumed going in: onboarding
 * runs Goal Definition (screen 2) before Tier Selection/Confirmation (screens 4/4a), but
 * *only* [TierTransitionUseCase.selectInitialTier] — called from those later screens — ever
 * created a `User` row before this pass. That meant this screen's data had nowhere durable to
 * be written on a first pass through onboarding. Buffering it in memory until screen 4a (nav
 * -graph arguments, carried across 3 intermediate screens) was considered and rejected: a
 * process death anywhere in that span — plausible during onboarding — would silently lose
 * everything typed here, with no error surfaced. Fixed by having this screen create a
 * *draft* `User` row itself (currentTier/tierSelectedAt/tierActivationAt/
 * onboardingConsentVersion all null — see [com.disciplineos.data.entity.User]'s kdoc for the
 * full account of why those four fields became nullable) if none exists yet, and update it in
 * place on any later resubmission. [TierTransitionUseCase.selectInitialTier] was changed to
 * match: it now fills in the tier fields on this draft row rather than assuming it always
 * gets to create a fresh row itself — see that method's kdoc.
 *
 * **Unblocks a previously-logged gap:** [MissionProfileSetupFragment]'s kdoc documents that
 * §2.8's "default suggestions drawn from §2.2's flagged categories" requirement couldn't be
 * met because this screen didn't exist. This screen is what finally gives that requirement
 * real data to draw from — but wiring `MissionProfileSetupFragment` to actually *use* it is
 * separate follow-up work, not done in this pass (see BUILD_PLAN.md Batch B's suggested
 * ordering: this screen first, then revisit Mission Profile Setup once this exists).
 *
 * **Both fields optional**, same precedent as [MissionProfileSetupFragment]'s "no invented
 * validation gate" reasoning — §2.2 doesn't require either field to be non-empty, and no
 * other real onboarding screen in this project blocks progress on empty text input.
 *
 * **Design-system pass (ROADMAP.md §5.26/onboarding-wide follow-up):** UI now lives in
 * [GoalDefinitionScreen], hosted via a single [ComposeView]. Free-text is read but not
 * persisted (unchanged) — Compose still collects it via [GoalDefinitionScreen]'s own state,
 * just never sent anywhere by this Fragment's callback, matching the pre-migration behavior.
 * `parseLines`/`submitCategories` are otherwise unchanged.
 */
class GoalDefinitionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DisciplineOsTheme {
                    GoalDefinitionScreen(
                        onContinue = { categoriesRaw ->
                            submitCategories(parseLines(categoriesRaw))
                        },
                        onBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }

    /**
     * Same parsing convention as [MissionProfileSetupFragment.parseLines] — one category per
     * line, blank lines/whitespace dropped rather than stored as empty-string entries. Not
     * extracted into a shared utility in this pass (two call sites, identical four-line
     * bodies) — worth revisiting once a third screen needs the same parsing, per this
     * project's stated preference for adding shared structure when a real second/third call
     * site shows up, not preemptively.
     */
    private fun parseLines(raw: String?): List<String> =
        raw.orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun submitCategories(categories: List<String>) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)

            // CORRECTED (found and fixed in the same review pass that added
            // User.flaggedCategories — see User.kt's kdoc for the full account): this screen
            // is not just writing to an already-existing User row, it's usually the row's
            // FIRST creation. Goal Definition (screen 2) runs before Tier
            // Selection/Confirmation (screens 4/4a) — the only place a User row was ever
            // created before this session — so on a first pass through onboarding, no row
            // exists yet when this runs. Insert a draft row (currentTier and friends left
            // null — see User.kt) if none exists; update in place if one already does
            // (covers both "back-then-resubmit on this same screen" and "returned here after
            // tier selection already happened," e.g. via Back navigation from a later screen).
            val existingUser = database.userDao().getSingleLocalUser()
            if (existingUser != null) {
                database.userDao().update(existingUser.copy(flaggedCategories = categories))
            } else {
                database.userDao().insert(
                    User(
                        id = UUID.randomUUID(),
                        createdAt = Instant.now(),
                        currentTier = null,
                        tierSelectedAt = null,
                        tierActivationAt = null,
                        onboardingConsentVersion = null,
                        flaggedCategories = categories,
                    )
                )
            }
            findNavController().navigate(R.id.action_goalDefinition_to_tierExplanation)
        }
    }
}
