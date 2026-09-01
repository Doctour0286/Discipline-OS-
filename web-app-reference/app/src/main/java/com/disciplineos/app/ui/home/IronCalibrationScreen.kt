package com.disciplineos.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.disciplineos.app.R

/**
 * The real destination `ironCalibrationGateFragment` in onboarding_nav_graph.xml was left
 * pointing at ([com.disciplineos.app.onboarding.OnboardingPlaceholderFragment]) — but for a
 * *different* flow than that one. That destination is onboarding-time only and stays
 * unreachable by design (Iron's RadioButton is disabled at Tier Selection itself, per §12.6 —
 * see that graph's own comment). This screen is the flow the graph comment says doesn't exist
 * yet: an *existing* user, already past onboarding at a lower tier, reaching Iron once their
 * [com.disciplineos.data.entity.User.calibrationWindowDays] window has elapsed, via
 * [com.disciplineos.domain.usecase.TierTransitionUseCase.activateIron].
 *
 * Presentation only — [com.disciplineos.app.home.IronCalibrationFragment] owns the actual
 * `activateIron()` call, its `IllegalStateException` handling (the gate not being satisfied
 * yet — a real, expected outcome per that method's own kdoc, not a bug path), and navigation.
 * This composable just renders whichever of the three states
 * ([IronCalibrationUiState]) the Fragment hands it.
 *
 * **No hidden retry-until-success loop.** Per PRD §12.6 "no exception path" — this screen does
 * not poll or auto-retry; a user who taps Activate before the window has elapsed sees exactly
 * why, once, and can leave and come back later. Matches [TierTransitionUseCase.activateIron]'s
 * own choice to hard-fail rather than silently wait.
 */
sealed interface IronCalibrationUiState {
    data object Idle : IronCalibrationUiState
    data object Activating : IronCalibrationUiState
    data object Success : IronCalibrationUiState
    data class GateNotSatisfied(val daysRemaining: Long) : IronCalibrationUiState
}

@Composable
fun IronCalibrationScreen(
    uiState: IronCalibrationUiState,
    onActivate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.iron_calibration_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = stringResource(R.string.iron_calibration_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 28.dp),
            )

            when (uiState) {
                is IronCalibrationUiState.Idle -> {
                    Button(
                        onClick = onActivate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Text(stringResource(R.string.iron_calibration_activate_button))
                    }
                }

                is IronCalibrationUiState.Activating -> {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                }

                is IronCalibrationUiState.Success -> {
                    Text(
                        text = stringResource(R.string.iron_calibration_success_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }

                is IronCalibrationUiState.GateNotSatisfied -> {
                    Text(
                        text = stringResource(
                            R.string.iron_calibration_gate_not_satisfied_body,
                            uiState.daysRemaining,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (uiState is IronCalibrationUiState.Success) {
                            R.string.iron_calibration_done_button
                        } else {
                            R.string.onboarding_placeholder_back
                        },
                    ),
                )
            }
        }
    }
}
