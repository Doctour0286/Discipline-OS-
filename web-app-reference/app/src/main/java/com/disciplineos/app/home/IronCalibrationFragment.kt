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
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.home.IronCalibrationScreen
import com.disciplineos.app.ui.home.IronCalibrationUiState
import com.disciplineos.app.ui.theme.themedComposeView
import com.disciplineos.data.metrics.ironCalibrationSatisfied
import com.disciplineos.data.entity.Tier
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The real "existing user reaches Iron later" destination that onboarding_nav_graph.xml's own
 * comment on `ironCalibrationGateFragment` describes as not modeled anywhere yet — see this
 * Fragment's own file being new in this pass, and STATUS.md's "what's actually next" item 2
 * that named this exact gap.
 *
 * **Calls [com.disciplineos.domain.usecase.TierTransitionUseCase.activateIron] directly** —
 * the one call site that use-case's own kdoc says should exist and, until this pass, didn't.
 * Reached only from [HomeFragment]'s Iron eligibility card, never from the onboarding nav
 * graph — this is a deliberately separate destination/graph from `ironCalibrationGateFragment`
 * (still an unreachable-by-design placeholder there, see that destination's own comment),
 * not a repurposing of it, since the onboarding-time destination's whole reason for existing
 * was already documented as "for a flow this graph doesn't model," and this Fragment lives in
 * that different flow instead of retrofitting the onboarding graph to carry it.
 *
 * **`activateIron()` throwing `IllegalStateException`** (the gate not being satisfied yet) is
 * an expected, real outcome per that method's own kdoc — not a bug path. Caught here and
 * rendered as [IronCalibrationUiState.GateNotSatisfied] with a fresh days-remaining figure
 * (recomputed at the moment of the failed attempt, since time may have passed since
 * [HomeFragment] first computed its own estimate) rather than left as an unhandled crash or a
 * generic error toast — the whole point of a hard-fail-with-a-clear-reason design (per the
 * PRD §12.6 "no exception path" language `activateIron`'s kdoc quotes) is that the caller
 * shows the real reason, not a swallowed failure.
 */
class IronCalibrationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        var uiState by mutableStateOf<IronCalibrationUiState>(IronCalibrationUiState.Idle)

        return themedComposeView {
            IronCalibrationScreen(
                uiState = uiState,
                onActivate = {
                    uiState = IronCalibrationUiState.Activating
                    activateIron { result -> uiState = result }
                },
                onBack = { findNavController().popBackStack() },
            )
        }
    }

    private fun activateIron(onResult: (IronCalibrationUiState) -> Unit) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val useCase = AppContainer.tierTransitionUseCase(context)

            val userId = database.userDao().getSingleLocalUser()?.id
            if (userId == null) {
                // Should be unreachable — this screen is only ever reached from HomeFragment,
                // which already required a real User row to show the Iron card at all. Same
                // defensive posture every other screen in this project takes for its own
                // equivalent "should be impossible" case: shown as the same gate-not-satisfied
                // state with a zero remainder, rather than crashing.
                onResult(IronCalibrationUiState.GateNotSatisfied(daysRemaining = 0L))
                return@launch
            }

            try {
                useCase.activateIron(userId)
                onResult(IronCalibrationUiState.Success)
            } catch (e: IllegalStateException) {
                onResult(IronCalibrationUiState.GateNotSatisfied(daysRemaining = daysRemainingNow(userId)))
            }
        }
    }

    /**
     * Re-reads the [com.disciplineos.data.entity.User] row and recomputes the remaining days
     * at the moment `activateIron()` actually failed, rather than trusting whatever estimate
     * [HomeFragment] passed along earlier — time may have elapsed between that screen loading
     * and this button press, and this is the number shown to the user as the reason their
     * attempt failed, so it should reflect "now," not "whenever Home last loaded."
     */
    private suspend fun daysRemainingNow(userId: java.util.UUID): Long {
        val database = AppContainer.database(requireContext().applicationContext)
        val user = database.userDao().get(userId) ?: return 0L
        val tierSelectedAt = user.tierSelectedAt ?: return 0L
        val now = Instant.now()
        val satisfied = ironCalibrationSatisfied(
            tier = Tier.IRON,
            tierSelectedAtEpochMilli = tierSelectedAt.toEpochMilli(),
            calibrationWindowDays = user.calibrationWindowDays,
            nowEpochMilli = now.toEpochMilli(),
        )
        if (satisfied) return 0L
        val elapsedDays = ChronoUnit.DAYS.between(tierSelectedAt, now)
        return (user.calibrationWindowDays - elapsedDays).coerceAtLeast(0L)
    }
}
