package com.disciplineos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R

/**
 * Compose screen for Onboarding, Consent & Interaction Spec §2.7 (Unsupervised Reliability
 * Opt-In). Part of the onboarding-wide Compose migration (ROADMAP.md §5.26 started this).
 * Presentation only — the `User.unsupervisedReliabilityOptIn`/`optInAt` write and the
 * VIEWED/ACCEPTED/DECLINED [com.disciplineos.data.entity.OnboardingScreenEvent] instrumentation
 * both stay exactly as-is in
 * [com.disciplineos.app.onboarding.UnsupervisedReliabilityOptInFragment], whose kdoc has the
 * full account of why Back logs neither ACCEPTED nor DECLINED.
 *
 * The [Switch] here is a visual preview only, same as the original layout's kdoc note — neither
 * [onEnable] nor [onSkip] reads its state; Enable always means opted-in true, Skip always means
 * false, regardless of the Switch's on-screen position when either button is pressed.
 */
@Composable
fun UnsupervisedReliabilityOptInScreen(
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var switchPreview by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.unsupervised_reliability_step_progress),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.unsupervised_reliability_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            )
            Text(
                text = stringResource(R.string.unsupervised_reliability_intro_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = stringResource(R.string.unsupervised_reliability_scope_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = stringResource(R.string.unsupervised_reliability_measurement_only_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = stringResource(R.string.unsupervised_reliability_self_report_preview_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = stringResource(R.string.unsupervised_reliability_deletable_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.unsupervised_reliability_switch_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = switchPreview, onCheckedChange = { switchPreview = it })
            }

            // Both real, equally reachable buttons — Skip is not a demoted/secondary style.
            // See this file's kdoc and the Fragment's kdoc: PRD §13.4 requires declining to
            // gate nothing.
            Button(
                onClick = onEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.unsupervised_reliability_enable_button))
            }
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.unsupervised_reliability_skip_button))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_placeholder_back))
            }
        }
    }
}
