package com.disciplineos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R
import com.disciplineos.app.ui.theme.DisciplineOsTheme

/**
 * Compose screen for Onboarding, Consent & Interaction Spec §2.9 (First Mission Scheduling).
 * Design-system pass proof-of-concept — the first screen migrated from XML+Fragment to Compose
 * per app/build.gradle.kts's buildFeatures.compose comment and Google's own recommended
 * incremental strategy (Fragment + Jetpack Navigation stay exactly as they are; only this
 * screen's *content* moves into a ComposeView — see FirstMissionSchedulingFragment.kt for the
 * host).
 *
 * All business logic (Mission creation, time parsing, navigation) stays in the Fragment
 * unchanged — this file is presentation only, taking simple callbacks. That split keeps this
 * migration pass honest: a UI framework swap, not a rewrite of what FirstMissionSchedulingFragment's
 * substantial kdoc already carefully reasoned through (scheduledStart semantics, the
 * [HYPOTHESIS] duration default, the missing-profile fallback, etc.) — none of that logic or
 * its reasoning changes here, only how it's rendered.
 *
 * §2.9 requirement carried over unchanged from the XML version: "schedule vs. start-now choice
 * here is itself the first data point for Self-Initiation Trend... doesn't affect this screen's
 * design" — Start Now and Schedule Mission are both [Button] (equal visual weight), not one
 * [Button] and one lower-emphasis [OutlinedButton]/[androidx.compose.material3.TextButton], so
 * neither reads as the "recommended" path.
 */
@Composable
fun FirstMissionSchedulingScreen(
    onStartNow: () -> Unit,
    onSchedule: (rawTimeInput: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var timeInput by remember { mutableStateOf("") }

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
                text = stringResource(R.string.first_mission_scheduling_step_progress),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.first_mission_scheduling_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            )

            Text(
                text = stringResource(R.string.first_mission_scheduling_intro_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            Button(
                onClick = onStartNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) {
                Text(stringResource(R.string.first_mission_scheduling_start_now_button))
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Text(
                text = stringResource(R.string.first_mission_scheduling_schedule_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
                value = timeInput,
                onValueChange = { timeInput = it },
                placeholder = { Text(stringResource(R.string.first_mission_scheduling_schedule_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.first_mission_scheduling_schedule_format_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Button(
                onClick = { onSchedule(timeInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.first_mission_scheduling_schedule_button))
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_placeholder_back))
            }
        }
    }
}
