package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
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
 * **Consent version:** [ONBOARDING_CONSENT_VERSION] is a plain string constant, not read
 * from any consent-copy screen, because Core Data Consent (§2.6) is still
 * [OnboardingPlaceholderFragment] content in this pass — there is no real consent copy yet
 * to version against. Flagged here rather than silently hardcoded as if it meant something:
 * bump this constant (and give it a real versioning scheme) when Core Data Consent gets real
 * content, not before.
 */
class TierSelectionFragment : Fragment(R.layout.fragment_tier_selection) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tierRadioGroup = view.findViewById<RadioGroup>(R.id.tierRadioGroup)
        val continueButton = view.findViewById<Button>(R.id.tierSelectionContinueButton)
        val backButton = view.findViewById<Button>(R.id.tierSelectionBackButton)

        backButton.setOnClickListener { findNavController().popBackStack() }

        continueButton.setOnClickListener {
            // Iron's RadioButton is disabled in the layout, so checkedRadioButtonId can only
            // ever land on Recruit/Operator/Warden here — see class kdoc's "defense in depth"
            // note on why selectInitialTier() also rejects Iron independently.
            //
            // Compares checkedRadioButtonId directly against each button's own R.id, rather
            // than calling findViewById(checkedRadioButtonId) and reading .id back off the
            // result — that pattern would depend on findViewById's behavior for an "unset"
            // RadioGroup (RadioGroup.checkedRadioButtonId returns View.NO_ID, i.e. -1, before
            // any button is checked), which was not independently confirmed here to behave
            // safely rather than throw. This project's own recent history (§5.21) is explicit
            // that "reads like it should be fine" is not a standard to build on without
            // checking — a plain equality comparison against known-good, already-declared
            // R.id constants sidesteps the question entirely rather than resting an answer on
            // it. fragment_tier_selection.xml also sets android:checked="true" on Recruit, so
            // in practice checkedRadioButtonId is never actually NO_ID when this listener
            // fires — but the `else -> null` branch below is kept regardless, so this doesn't
            // rely on that layout default holding either.
            val selectedTier = when (tierRadioGroup.checkedRadioButtonId) {
                R.id.tierRecruitRadio -> Tier.RECRUIT
                R.id.tierOperatorRadio -> Tier.OPERATOR
                R.id.tierWardenRadio -> Tier.WARDEN
                else -> null // no selection somehow made it through — treat as "do nothing"
            }

            if (selectedTier == Tier.WARDEN) {
                // §2.4: Warden needs its own confirmation screen before the tier is actually
                // recorded — selectInitialTier() is not called here, only after confirmation.
                findNavController().navigate(R.id.action_tierSelection_to_tierConfirmation)
            } else if (selectedTier != null) {
                submitInitialTier(selectedTier)
            }
        }
    }

    private fun submitInitialTier(tier: Tier) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val useCase = AppContainer.tierTransitionUseCase(context)

            // Guard against re-entry: this screen's Back button (or a slow double-tap on
            // Continue before navigation completes) can bring the user here a second time
            // after a User row already exists. UserDao.insert() has no onConflict strategy
            // (defaults to ABORT), so a second selectInitialTier() call for a fresh random
            // UUID would either crash outright or — if it somehow succeeded — leave two User
            // rows behind that getSingleLocalUser()'s `LIMIT 1` would pick between
            // unpredictably (see that method's kdoc: "single local user" is an assumption
            // this project relies on everywhere downstream, not something this screen can
            // quietly violate). If a local user already exists, onboarding's tier choice has
            // already been recorded once — treat re-arriving here as "already done" and just
            // continue forward, rather than attempting a second insert.
            if (database.userDao().getSingleLocalUser() == null) {
                useCase.selectInitialTier(
                    userId = UUID.randomUUID(),
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
