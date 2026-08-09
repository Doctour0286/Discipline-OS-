package com.disciplineos.app.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.R
import com.disciplineos.app.di.AppContainer
import com.disciplineos.data.entity.OnboardingScreenEvent
import com.disciplineos.data.entity.OnboardingScreenEventOutcome
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Onboarding, Consent & Interaction Spec §2.7 (Unsupervised Reliability Opt-In) — real
 * content, replacing [OnboardingPlaceholderFragment] at the
 * `unsupervisedReliabilityOptInFragment` destination.
 *
 * **Genuinely optional, structurally — the load-bearing difference from
 * [CoreDataConsentFragment] one screen earlier.** That screen has one forward action because
 * there's nothing to decline into (Mission enforcement cannot run without local storage). This
 * screen has two — Enable and Skip — both navigating to the identical next destination
 * ([R.id.action_unsupervisedReliabilityOptIn_to_firstMissionScheduling]), matching PRD §13.4's
 * explicit text: "Declining does not gate access to any other feature." Neither button is
 * disabled or gated behind the other; Skip is not a smaller/secondary-styled afterthought in
 * the layout (see that file's own kdoc) — both are real, equally reachable buttons.
 *
 * **Writes [com.disciplineos.data.entity.User.unsupervisedReliabilityOptIn] and
 * [com.disciplineos.data.entity.User.unsupervisedReliabilityOptInAt]** — both fields already
 * existed on `User` (added in an earlier phase alongside the rest of that entity's schema) but
 * were unused until this pass: no screen previously wrote to them. `optInAt` is set only when
 * `optIn` is `true` — declining leaves it `null`, matching the same "null means never
 * meaningfully set" convention [User]'s kdoc already uses for `tierSelectedAt`/
 * `tierActivationAt` in the pre-tier-selection window, rather than writing a `now()` timestamp
 * for a decline that has nothing to timestamp.
 *
 * **Does not gate on flagged categories being non-empty.** §2.2 (Goal Definition) made both
 * its fields optional — a user could reach this screen having flagged nothing. §2.7's scope
 * language ("only covers categories flagged in §2.2") is a statement about what data gets
 * looked at if enabled, not a precondition for the screen itself; a user with zero flagged
 * categories can still meaningfully enable this (it would simply have nothing to measure yet,
 * same as any category-scoped feature with an empty category list) or decline it. No extra
 * validation gate is invented here, same "no invented validation gate" precedent
 * [GoalDefinitionFragment]'s own kdoc already cites for MissionProfileSetupFragment.
 *
 * **Instrumentation: this screen is the actual subject of BUILD_PLAN.md Batch B's "instrument
 * completion/drop-off from day one" note**, itself lifted from §2.7's own text citing PRD
 * §13.2.1's Open Question about unknown opt-in completion rates. See
 * [OnboardingScreenEvent]'s kdoc for why this is a narrow, screen-scoped log rather than a
 * general analytics system. A [OnboardingScreenEventOutcome.VIEWED] event is logged once, the
 * first time [onViewCreated] runs for a given Fragment instance (not on every recomposition —
 * Fragment view lifecycle only calls this once per view creation, matching how a real "screen
 * shown" event should behave); [OnboardingScreenEventOutcome.ACCEPTED] or
 * [OnboardingScreenEventOutcome.DECLINED] is logged on whichever button is actually pressed.
 * Back-button navigation away from this screen logs neither ACCEPTED nor DECLINED — going Back
 * is not the same event as an affirmative decline, and collapsing the two would make "declined"
 * over-count relative to what a user actually chose. A user who presses Back and never returns
 * shows up correctly as a VIEWED row with no matching outcome row — the real "drop-off" case
 * this instrumentation exists to measure, per [OnboardingScreenEvent]'s kdoc.
 *
 * **No re-entry guard against writing this twice.** Unlike [GoalDefinitionFragment]'s genuine
 * insert-vs-update branch (that screen may run before any `User` row exists at all), by the
 * time this destination is reachable a `User` row is guaranteed to already exist (created no
 * later than Goal Definition, tier fields populated no later than Tier Confirmation — see
 * either Fragment's kdoc), so this screen only ever updates. Pressing Enable or Skip more than
 * once (e.g. Back then resubmit) simply overwrites `unsupervisedReliabilityOptIn`/`optInAt`
 * again with whatever was most recently chosen — the same "last choice wins, no invented
 * uniqueness constraint" posture [CoreDataConsentFragment] already takes for its own
 * single-field overwrite, and correct here for the same reason: this is a preference a user
 * should be able to change their mind about by simply choosing again, not a one-time event
 * that needs protecting from a second write.
 */
class UnsupervisedReliabilityOptInFragment : Fragment(R.layout.fragment_unsupervised_reliability_opt_in) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton = view.findViewById<Button>(R.id.unsupervisedReliabilityBackButton)
        val enableButton = view.findViewById<Button>(R.id.unsupervisedReliabilityEnableButton)
        val skipButton = view.findViewById<Button>(R.id.unsupervisedReliabilitySkipButton)

        // Switch itself is a visual preview only — see the layout's own kdoc. Neither button
        // handler below reads its state; Enable always means true, Skip always means false,
        // regardless of whatever position the Switch happens to be in when pressed.

        logViewedEvent()

        backButton.setOnClickListener { findNavController().popBackStack() }

        enableButton.setOnClickListener { recordChoiceAndContinue(optedIn = true) }

        skipButton.setOnClickListener { recordChoiceAndContinue(optedIn = false) }
    }

    private fun logViewedEvent() {
        lifecycleScope.launch {
            logEvent(OnboardingScreenEventOutcome.VIEWED)
        }
    }

    private fun recordChoiceAndContinue(optedIn: Boolean) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)

            // See class kdoc's "No re-entry guard" section: a User row is guaranteed to exist
            // by the time this destination is reachable. existingUser == null here would mean
            // that invariant was violated by a future change to the graph, not a real
            // user-facing case — same defensive "do nothing rather than crash" posture
            // CoreDataConsentFragment already takes for the identical scenario.
            val existingUser = database.userDao().getSingleLocalUser()
            if (existingUser != null) {
                database.userDao().update(
                    existingUser.copy(
                        unsupervisedReliabilityOptIn = optedIn,
                        unsupervisedReliabilityOptInAt = if (optedIn) Instant.now() else null,
                    )
                )
            }

            logEvent(if (optedIn) OnboardingScreenEventOutcome.ACCEPTED else OnboardingScreenEventOutcome.DECLINED)

            findNavController().navigate(R.id.action_unsupervisedReliabilityOptIn_to_firstMissionScheduling)
        }
    }

    private suspend fun logEvent(outcome: OnboardingScreenEventOutcome) {
        val context = requireContext().applicationContext
        val database = AppContainer.database(context)
        val userId = database.userDao().getSingleLocalUser()?.id ?: return

        database.onboardingEventDao().insert(
            OnboardingScreenEvent(
                id = UUID.randomUUID(),
                userId = userId,
                screenId = OnboardingScreenEvent.SCREEN_ID_UNSUPERVISED_RELIABILITY_OPT_IN,
                outcome = outcome,
                occurredAt = Instant.now(),
            )
        )
    }
}
