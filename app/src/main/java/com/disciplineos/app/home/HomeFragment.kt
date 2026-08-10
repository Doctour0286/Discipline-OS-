package com.disciplineos.app.home

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
import com.disciplineos.app.ui.home.HomeScreen
import com.disciplineos.app.ui.theme.themedComposeView
import com.disciplineos.data.entity.PredictiveFailureAlertDismissal
import com.disciplineos.data.entity.PredictiveFailureAlertOutcome
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import com.disciplineos.data.metrics.ironCalibrationSatisfied
import com.disciplineos.domain.usecase.FollowUpAction
import com.disciplineos.domain.usecase.PredictiveFailureAlert
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Home-screen state derived from the local [User] row — pulled out as a plain data class plus
 * a pure function ([computeHomeState]) specifically so both are unit-testable without a
 * Fragment/Compose host (see `HomeFragmentTest`), matching this project's existing preference
 * (every other screen-level test file in `:app`) for testing at the DAO/pure-function level
 * rather than through `fragment-testing`, which this module doesn't depend on.
 */
data class HomeState(
    val currentTier: Tier?,
    val showIronCard: Boolean,
    val ironEligibleNow: Boolean,
    val daysRemaining: Long,
)

/**
 * Same [ironCalibrationSatisfied] pure function
 * [com.disciplineos.domain.usecase.TierTransitionUseCase.activateIron] itself gates on, reused
 * here rather than re-deriving the calibration threshold logic a second time — see that
 * use-case's own kdoc for why reusing the one pure function matters (a single source of truth
 * for "is the window satisfied," rather than two independently-maintained copies that could
 * drift). The Iron card is hidden entirely (not just disabled) once [user] is already IRON, or
 * before any tier has been selected at all ([User.tierSelectedAt] null) — both cases mean
 * there's nothing left to calibrate toward or nothing yet to calibrate from.
 */
fun computeHomeState(user: User?, now: Instant): HomeState {
    val tier = user?.currentTier
    val tierSelectedAt = user?.tierSelectedAt
    if (tier == null || tier == Tier.IRON || tierSelectedAt == null) {
        return HomeState(
            currentTier = tier,
            showIronCard = false,
            ironEligibleNow = false,
            daysRemaining = 0L,
        )
    }

    val satisfied = ironCalibrationSatisfied(
        tier = Tier.IRON,
        tierSelectedAtEpochMilli = tierSelectedAt.toEpochMilli(),
        calibrationWindowDays = user.calibrationWindowDays,
        nowEpochMilli = now.toEpochMilli(),
    )
    val remaining = if (satisfied) {
        0L
    } else {
        val elapsedDays = ChronoUnit.DAYS.between(tierSelectedAt, now)
        (user.calibrationWindowDays - elapsedDays).coerceAtLeast(0L)
    }

    return HomeState(
        currentTier = tier,
        showIronCard = true,
        ironEligibleNow = satisfied,
        daysRemaining = remaining,
    )
}

/**
 * The post-onboarding home shell — did not exist anywhere in this app before this pass.
 * See [com.disciplineos.app.ui.home.HomeScreen]'s kdoc for the full "why this exists at all"
 * reasoning: `MainActivity` previously only ever ran the onboarding nav graph, and onboarding's
 * last screen (`firstMissionSchedulingFragment`) had no outgoing `<action>`, so there was
 * nowhere for a user who finished onboarding to land.
 *
 * **What this Fragment does, concretely:** reads the single local [User] row via
 * [loadHomeState], computes [HomeState] via [computeHomeState], and pushes it into
 * [androidx.compose.runtime.mutableStateOf] fields — same async-load-then-render pattern
 * [com.disciplineos.app.onboarding.MissionProfileSetupFragment.loadSuggestedBlocklist] already
 * established for this project's other screens that need a value from the database before
 * their first real render. No database writes happen here — this screen only ever reads.
 *
 * **No user row yet:** shouldn't be reachable (onboarding always creates one — see
 * [com.disciplineos.app.onboarding.TierConfirmationFragment] / `selectInitialTier`), but handled
 * the same defensive way every other screen in this project treats its own "should be
 * impossible" case: [computeHomeState] returns `showIronCard = false` for a null user rather
 * than throwing.
 *
 * **Predictive Failure Alert card (Phase 4, ROADMAP.md) — this pass.** Onboarding/Interaction
 * Spec §3.5: "checked on app open and after each Mission completion." This Fragment only
 * covers "app open" (there is no Mission-completion event hook yet for this screen to
 * subscribe to — Mission completion happens inside `MissionAccessibilityService`/the
 * interception flow, which doesn't currently notify Home; flagged here as a real gap, not
 * silently worked around). Loads [com.disciplineos.domain.usecase.BehavioralFingerprintResult]
 * via [AppContainer.computeBehavioralFingerprintUseCase] and shows **at most one** active
 * alert, per §3.5's "one rule, one card ... not a combined summary" — if multiple rules
 * triggered simultaneously, the first by [com.disciplineos.domain.usecase.FingerprintRule]
 * declaration order (F1 before F2 before F3 before F5) wins this session; the rest remain
 * available on the next check (app open or, once wired, Mission completion) since dismissal is
 * per-rule, not global.
 */
class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        var currentTier by mutableStateOf<Tier?>(null)
        var showIronCard by mutableStateOf(false)
        var ironEligibleNow by mutableStateOf(false)
        var daysRemaining by mutableStateOf(0L)
        var activeAlert by mutableStateOf<PredictiveFailureAlert?>(null)

        loadHomeState { state ->
            currentTier = state.currentTier
            showIronCard = state.showIronCard
            ironEligibleNow = state.ironEligibleNow
            daysRemaining = state.daysRemaining
        }
        loadPredictiveFailureAlert { alert -> activeAlert = alert }

        return themedComposeView {
            HomeScreen(
                currentTier = currentTier,
                showIronEligibilityCard = showIronCard,
                ironEligibleNow = ironEligibleNow,
                daysRemainingUntilIronEligible = daysRemaining,
                onOpenIronCalibration = {
                    findNavController().navigate(R.id.action_home_to_ironCalibration)
                },
                activeAlert = activeAlert,
                onAlertFollowUpAction = { action -> navigateForFollowUp(action) },
                onAlertDismissed = { outcome ->
                    val dismissedRule = activeAlert?.rule
                    activeAlert = null
                    if (dismissedRule != null) {
                        recordDismissal(dismissedRule.name, outcome)
                    }
                },
            )
        }
    }

    private fun loadHomeState(onLoaded: (HomeState) -> Unit) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val user = database.userDao().getSingleLocalUser()
            onLoaded(computeHomeState(user, Instant.now()))
        }
    }

    private fun loadPredictiveFailureAlert(onLoaded: (PredictiveFailureAlert?) -> Unit) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val user = database.userDao().getSingleLocalUser() ?: return@launch onLoaded(null)
            val useCase = AppContainer.computeBehavioralFingerprintUseCase(context)
            val result = useCase.execute(user.id, Instant.now())
            onLoaded(result.activeAlerts.firstOrNull())
        }
    }

    /**
     * Fingerprint doc §5: "logged, not just discarded." Writes a
     * [com.disciplineos.data.entity.PredictiveFailureAlertDismissal] row directly via the DAO
     * rather than through a `:domain` use-case — this is a single-table insert with no
     * cross-entity invariant to protect (unlike e.g. [RecordViolationUseCase]'s ledger-write
     * transaction), so a bare DAO call matches this project's existing bias against wrapping
     * trivial writes in use-case ceremony they don't need (see [OnboardingEventDao.insert]'s
     * own direct-DAO call sites for the same reasoning applied to onboarding instrumentation).
     */
    private fun recordDismissal(ruleId: String, outcome: PredictiveFailureAlertOutcome) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            database.predictiveFailureAlertDismissalDao().insert(
                PredictiveFailureAlertDismissal(
                    id = UUID.randomUUID(),
                    ruleId = ruleId,
                    outcome = outcome,
                    dismissedAt = Instant.now(),
                ),
            )
        }
    }

    /**
     * §3.5's four follow-up destinations. Only [FollowUpAction.OPEN_RECOVERY_MODE] (F3) has a
     * real destination in this codebase today — Recovery Mode is `⬜` per STATUS.md ("Referenced
     * by domain logic; no dedicated flow/UI"), so this currently no-ops for every action rather
     * than crashing on a missing nav destination. Flagged here rather than silently building
     * fake navigation: the alert card and its dismissal/accuracy-tracking are real and usable
     * today even though none of its four follow-up links have a screen to land on yet — that's
     * a real, separate gap from this pass's own scope (F1–F5 rule implementations + the shared
     * card pattern), not something this pass can close on its own since none of Mission Profile
     * editing, Recovery Mode, or Mission Profile Drift review exist as screens yet either.
     */
    private fun navigateForFollowUp(action: FollowUpAction) {
        // No-op for now — see kdoc above. Left as an explicit empty branch (not a TODO
        // comment alone) so a future screen's nav action has an obvious, named place to plug
        // into per FollowUpAction value, matching this project's stated preference for
        // structure that names a gap rather than one that silently swallows it.
        when (action) {
            FollowUpAction.REVIEW_EVENING_MISSION_PROFILE -> Unit
            FollowUpAction.REVIEW_MISSION_PROFILE_SCOPE -> Unit
            FollowUpAction.OPEN_RECOVERY_MODE -> Unit
            FollowUpAction.REVIEW_MISSION_PROFILE_DRIFT -> Unit
        }
    }
}
