package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Onboarding, Consent & Interaction Spec §2.6 (Core Data Consent) — real content, replacing
 * [OnboardingPlaceholderFragment] at the `coreDataConsentFragment` destination.
 *
 * **Purpose, per the spec:** "standard local-storage/Mission-enforcement consent — required
 * to use the app at all, since this is core function, not an optional data use." Two content
 * requirements, both covered in `strings.xml` (see the layout's own kdoc for the exact
 * per-string breakdown): local-storage-is-required, and a plain-language local-first +
 * optional-cloud-sync explanation (Architecture doc §3.1).
 *
 * **What this screen does NOT do, and why:** it does not ask about Unsupervised Reliability.
 * PRD §13.4 is an explicit hard rule against bundling that consent into this one — "no
 * bundling Unsupervised Reliability consent into general permissions... no single 'I agree to
 * everything' screen" (§1's own framing, repeated in this destination's nav-graph comment).
 * That stays [unsupervisedReliabilityOptInFragment][com.disciplineos.app.onboarding], the very
 * next destination, still a placeholder as of this pass.
 *
 * **Required, not optional — so no decline path.** Unlike the opt-in screen that follows this
 * one, there is nothing to decline into: Mission enforcement (this app's core function) cannot
 * run without local storage, matching Architecture doc §3.1's framing of the on-device store
 * as the actual source of truth, not a permission gate in front of some lighter-weight mode.
 * One forward action only — [onboarding_placeholder_back][R.string] to go back, or agree and
 * continue.
 *
 * **This screen writes the real [onboardingConsentVersion][com.disciplineos.data.entity.User]
 * value — closing a gap both [TierSelectionFragment] and [TierConfirmationFragment] left open
 * on purpose.** Both of those screens run *before* this one in the flow (screens 4/4a vs. this
 * screen's 6) but [com.disciplineos.domain.usecase.TierTransitionUseCase.selectInitialTier]
 * requires a non-null `onboardingConsentVersion` argument at call time — there was no way to
 * defer that write until real consent copy existed without changing that use case's signature
 * for a one-pass problem. Both screens wrote
 * [ONBOARDING_CONSENT_VERSION][TierSelectionFragment.ONBOARDING_CONSENT_VERSION] instead — a
 * plain string constant, explicitly named and kdoc'd as a placeholder not meant to mean
 * anything, with an explicit instruction left in both kdocs: "bump this constant... when Core
 * Data Consent gets real content, not before." This screen is that moment. Rather than bump
 * the shared constant to a value this screen doesn't own or control, this screen instead
 * **overwrites** whatever placeholder value is on the row with [CONSENT_VERSION] the first
 * time the user actually reaches and agrees to real consent copy — the version now genuinely
 * describes "the consent copy this user agreed to," which the tier screens' placeholder value
 * never could, since it ran before any such copy existed to agree to.
 *
 * **Versioning scheme:** [CONSENT_VERSION] is a plain literal (`"v1"`), not derived from a
 * hash or resource id — matching the pragmatic, ungated stance the tier screens' own kdoc
 * already suggested wanting ("give it a real versioning scheme," not "give it an automated
 * one"). Bump it by hand if this screen's consent copy is ever materially rewritten; nothing
 * currently reads or compares this value programmatically (checked: no call site does
 * anything with `onboardingConsentVersion` beyond storing and displaying it), so there's no
 * migration or comparison logic this scheme needs to satisfy yet.
 *
 * **No DAO round-trip test file for this screen, deliberately** — same reasoning
 * [WelcomeFragment]'s kdoc gives for its own omission does *not* quite apply here (this screen
 * does write real data, via [update][com.disciplineos.data.dao.UserDao.update]), but the write
 * itself is a single unconditional field overwrite with no branch logic to regress — unlike
 * [GoalDefinitionFragment]'s genuinely two-branch insert-vs-update case, there's nothing here
 * a DAO-level test would catch that the Android resource compiler and Kotlin compiler don't
 * already guarantee. Revisit if this screen ever grows a second write path.
 */
class CoreDataConsentFragment : Fragment(R.layout.fragment_core_data_consent) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton = view.findViewById<Button>(R.id.coreDataConsentBackButton)
        val continueButton = view.findViewById<Button>(R.id.coreDataConsentContinueButton)

        backButton.setOnClickListener { findNavController().popBackStack() }

        continueButton.setOnClickListener {
            recordConsentAndContinue()
        }
    }

    private fun recordConsentAndContinue() {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)

            // By the time this destination is reachable, onboarding_nav_graph.xml guarantees
            // a User row exists — this screen is only reachable via Mission Profile Setup's
            // single outgoing action, and that screen is itself only reachable after Tier
            // Selection/Confirmation already created the row (see either Fragment's own
            // kdoc). existingUser == null here would mean that invariant was violated by a
            // future change to the graph, not a real user-facing case — silently doing
            // nothing rather than crashing is the same defensive posture
            // MissionProfileSetupFragment already takes for the identical scenario.
            val existingUser = database.userDao().getSingleLocalUser()
            if (existingUser != null) {
                database.userDao().update(
                    existingUser.copy(onboardingConsentVersion = CONSENT_VERSION)
                )
            }
            findNavController().navigate(R.id.action_coreDataConsent_to_unsupervisedReliabilityOptIn)
        }
    }

    companion object {
        /** See class kdoc's "This screen writes the real..." section for the full account. */
        const val CONSENT_VERSION = "v1"
    }
}
