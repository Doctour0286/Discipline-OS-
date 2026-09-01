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
import com.disciplineos.app.ui.onboarding.TierSelectionScreen
import com.disciplineos.app.ui.theme.themedComposeView
import com.disciplineos.data.entity.Tier
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.4 (Tier Selection) — real content, replacing
 * [OnboardingPlaceholderFragment] at this one destination. This is the first onboarding
 * screen with real behavior, not navigation-skeleton scaffolding — see ROADMAP.md's
 * decision log for why this slice exists (the placeholder proved the graph shape; Tier
 * Selection choosing nothing was a genuine functional gap the placeholder correctly never
 * hid, per its own kdoc's "not a shortcut to avoid writing real screens" disclaimer).
 *
 * **What this screen does:**
 * - Recruit / Operator selected → calls [com.disciplineos.domain.usecase
 *   .TierTransitionUseCase.selectInitialTier] directly, then navigates to Mission Profile
 *   Setup — no extra friction, per §2.4 ("secondary confirmation step for Warden/Iron
 *   specifically — not for Recruit/Operator").
 * - Warden selected → navigates to [TierConfirmationFragment] first (§2.4's "distinct
 *   confirmation screen" form of the requirement); [selectInitialTier] is only called after
 *   that screen's own confirm button.
 * - Iron → cannot be selected at all. The RadioButton is `android:enabled="false"` in the
 *   layout (§12.6 shown-not-hidden, per §2.3's "all four tiers side by side" requirement),
 *   so this Fragment never even attempts to submit Iron — [selectInitialTier]'s own
 *   `require(tier != Tier.IRON)` is defense in depth, not the primary gate here, since a
 *   disabled RadioButton cannot be checked by a tap in the first place.
 *
 * **User id:** this project has no login/multi-profile concept (see [com.disciplineos.data
 * .dao.UserDao.currentUser]'s kdoc for the same "single local user" assumption made
 * elsewhere) and, until this screen runs, no [com.disciplineos.data.entity.User] row exists
 * at all — there is nothing to read an id *from*. A fresh [UUID] is generated here and is,
 * by construction, this device's one and only user id from this point on.
 *
 * **Consent version:** [ONBOARDING_CONSENT_VERSION] is a plain placeholder string, not read
 * from any consent-copy screen — [selectInitialTier][com.disciplineos.domain.usecase
 * .TierTransitionUseCase.selectInitialTier] requires a non-null value at this point in the
 * flow (screen 4, before Core Data Consent's screen 6 has run), so this was never going to be
 * able to hold a real version. **Now resolved:** Core Data Consent (§2.6) has real content as
 * of this pass ([CoreDataConsentFragment]) and overwrites whatever value is written here with
 * a real version the moment the user actually reaches and agrees to that screen's copy — see
 * that Fragment's own kdoc for the full account. The value this constant holds is
 * intentionally never read back or compared against anything; it only needs to be non-null
 * long enough to satisfy the row's constructor until Core Data Consent overwrites it.
 *
 * **Design-system pass (ROADMAP.md §5.26/onboarding-wide follow-up):** UI now lives in
 * [TierSelectionScreen], hosted via [themedComposeView] (ROADMAP.md §5.29 — replaces the
 * inline `ComposeView(requireContext()).apply { ... }` boilerplate every onboarding Fragment
 * previously repeated). This screen was CI + device confirmed in its XML form before this
 * migration — [TierSelectionScreen]'s kdoc documents that Iron stays non-selectable
 * (`enabled = false`) in the Compose version too, so [submitInitialTier] is still structurally
 * never reachable with [Tier.IRON] from this screen, matching the pre-migration guarantee this
 * class's own kdoc describes.
 *
 * **One real behavioral simplification from the XML version, noted rather than silent:** the
 * original `RadioGroup`-based listener had an `else -> null` "no selection somehow made it
 * through" branch, defensive against `checkedRadioButtonId` returning `View.NO_ID`.
 * [TierSelectionScreen]'s Compose state always holds a concrete [Tier] (defaulting to
 * [Tier.RECRUIT], mirroring the original layout's `android:checked="true"` on Recruit) — there
 * is no Compose equivalent of an "unset" RadioGroup state to defend against, so `onContinue`
 * below is always invoked with a real, selectable tier. This isn't a behavior change a user
 * could ever observe (the `else` branch was unreachable in practice before this migration too,
 * per the original kdoc's own note), just a defensive branch that no longer has anything to
 * defend against.
 */
class TierSelectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = themedComposeView {
        TierSelectionScreen(
            onContinue = { selectedTier ->
                if (selectedTier == Tier.WARDEN) {
                    // §2.4: Warden needs its own confirmation screen before the
                    // tier is actually recorded.
                    findNavController().navigate(R.id.action_tierSelection_to_tierConfirmation)
                } else {
                    submitInitialTier(selectedTier)
                }
            },
            onBack = { findNavController().popBackStack() },
        )
    }

    private fun submitInitialTier(tier: Tier) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val useCase = AppContainer.tierTransitionUseCase(context)

            // Guard against re-entry: this screen's Back button (or a slow double-tap on
            // Continue before navigation completes) can bring the user here a second time
            // after tier selection already happened once.
            //
            // CORRECTED (Batch B, BUILD_PLAN.md) — this guard used to be "does a User row
            // exist at all," which was correct back when the only way a User row could exist
            // was via this exact call. That stopped being true once GoalDefinitionFragment
            // (screen 2, earlier in the same flow) started creating a *draft* row with
            // currentTier == null, purely to have somewhere durable to write flagged
            // categories. The real re-entry condition now is "has a tier already been
            // selected" (currentTier != null), not "does any row exist."
            //
            // Also: this must reuse the draft row's existing id, not generate a fresh
            // UUID.randomUUID() unconditionally — TierTransitionUseCase.selectInitialTier()
            // now updates-in-place if a row for the given userId already exists (see that
            // method's kdoc), but only if called with the SAME id the draft row was created
            // under. Generating a new random id here would miss the draft entirely and create
            // a second, disconnected row — silently losing whatever Goal Definition wrote.
            val existingUser = database.userDao().getSingleLocalUser()
            if (existingUser == null || existingUser.currentTier == null) {
                useCase.selectInitialTier(
                    userId = existingUser?.id ?: UUID.randomUUID(),
                    tier = tier,
                    onboardingConsentVersion = ONBOARDING_CONSENT_VERSION,
                )
            }
            // Iron never reaches this call site (see class kdoc), so the Iron Calibration
            // Gate branch is deliberately not taken from here — every accepted tier routes
            // straight to Mission Profile Setup, matching onboarding_nav_graph.xml's existing
            // action for this destination.
            findNavController().navigate(R.id.action_tierSelection_to_missionProfileSetup)
        }
    }

    companion object {
        /**
         * See class kdoc's "Consent version" note — placeholder value, not a real consent
         * copy version, until Core Data Consent (§2.6) has real content of its own to
         * version against.
         */
        private const val ONBOARDING_CONSENT_VERSION = "unversioned-pre-consent-copy"
    }
}
