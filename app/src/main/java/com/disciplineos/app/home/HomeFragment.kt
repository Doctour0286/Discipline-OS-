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
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import com.disciplineos.data.metrics.ironCalibrationSatisfied
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

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

        loadHomeState { state ->
            currentTier = state.currentTier
            showIronCard = state.showIronCard
            ironEligibleNow = state.ironEligibleNow
            daysRemaining = state.daysRemaining
        }

        return themedComposeView {
            HomeScreen(
                currentTier = currentTier,
                showIronEligibilityCard = showIronCard,
                ironEligibleNow = ironEligibleNow,
                daysRemainingUntilIronEligible = daysRemaining,
                onOpenIronCalibration = {
                    findNavController().navigate(R.id.action_home_to_ironCalibration)
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
}
