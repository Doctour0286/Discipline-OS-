package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import com.disciplineos.data.entity.Tier
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.4's "distinct confirmation screen" form of the
 * Warden/Iron secondary-confirmation requirement. Iron never reaches this screen — rejected
 * earlier, at [TierSelectionFragment] itself, since its RadioButton is disabled (§12.6, no
 * exception path) — so this Fragment only ever confirms Warden in this pass. Written to be
 * extendable to Iron's own confirmation later without restructuring (see [ARG_TIER]), even
 * though nothing routes Iron here yet.
 *
 * `selectInitialTier()` is called here, not in [TierSelectionFragment] — this is the actual
 * point of the confirmation step existing: the tier isn't recorded until the user confirms
 * on *this* screen, not the moment they tapped a RadioButton on the previous one.
 */
class TierConfirmationFragment : Fragment(R.layout.fragment_tier_confirmation) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val confirmButton = view.findViewById<Button>(R.id.tierConfirmationConfirmButton)
        val backButton = view.findViewById<Button>(R.id.tierConfirmationBackButton)

        // Back returns to Tier Selection without recording anything — the whole point of a
        // confirmation screen is that arriving here commits nothing by itself.
        backButton.setOnClickListener { findNavController().popBackStack() }

        confirmButton.setOnClickListener {
            val tierName = arguments?.getString(ARG_TIER) ?: Tier.WARDEN.name
            val tier = Tier.valueOf(tierName)

            lifecycleScope.launch {
                val context = requireContext().applicationContext
                val database = AppContainer.database(context)
                val useCase = AppContainer.tierTransitionUseCase(context)

                // Same re-entry guard as TierSelectionFragment.submitInitialTier() — see that
                // method's comment for the full, corrected reasoning (Batch B, BUILD_PLAN.md):
                // the real condition is "has a tier already been selected"
                // (existingUser.currentTier != null), not "does any row exist," since
                // GoalDefinitionFragment now creates a draft row earlier in the flow. Also
                // reuses the draft row's own id rather than generating a fresh one, so
                // selectInitialTier() updates that row in place instead of creating a second,
                // disconnected one.
                val existingUser = database.userDao().getSingleLocalUser()
                if (existingUser == null || existingUser.currentTier == null) {
                    useCase.selectInitialTier(
                        userId = existingUser?.id ?: UUID.randomUUID(),
                        tier = tier,
                        onboardingConsentVersion = ONBOARDING_CONSENT_VERSION,
                    )
                }
                findNavController().navigate(R.id.action_tierConfirmation_to_missionProfileSetup)
            }
        }
    }

    companion object {
        /** Nav-graph argument name — see [fragment_tier_confirmation]'s `<argument>`. */
        const val ARG_TIER = "tier"

        /**
         * See [TierSelectionFragment.ONBOARDING_CONSENT_VERSION]'s kdoc — same placeholder,
         * now overwritten with a real version by [CoreDataConsentFragment] once the user
         * reaches that screen later in the same flow.
         */
        private const val ONBOARDING_CONSENT_VERSION = "unversioned-pre-consent-copy"
    }
}
