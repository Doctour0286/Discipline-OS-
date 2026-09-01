package com.disciplineos.app.ui.onboarding

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Compose screen for Onboarding, Consent & Interaction Spec §2.9 (First Mission Scheduling).
 *
 * All business logic (Mission creation, navigation) stays in
 * [com.disciplineos.app.onboarding.FirstMissionSchedulingFragment] — this file is presentation
 * only, taking simple callbacks. That split is unchanged from this screen's original
 * design-system pass.
 *
 * **Time input (this pass):** replaces the original free-text OutlinedTextField + "yyyy-MM-dd
 * HH:mm" format-note hint with chained native [DatePickerDialog] + [TimePickerDialog]. The user
 * can no longer produce a malformed or ambiguous timestamp — every value this screen can hand
 * back via [onSchedule] is already a real, valid, unambiguous [Instant]. That's also why
 * [onSchedule]'s signature changed from `(rawTimeInput: String) -> Unit` to `(scheduledStart:
 * Instant) -> Unit`: there is no longer a parse step for the Fragment to own, and no
 * invalid-input case for it to handle (see that class's own kdoc, "Time input" section, for the
 * corresponding removal of `parseScheduledTime`).
 *
 * The date picker's minimum selectable date is today (`datePicker.minDate =
 * System.currentTimeMillis()`), since scheduling a Mission in the past isn't a meaningful choice
 * this screen offers — DatePickerDialog enforces that at the OS-widget level rather than this
 * screen validating it after the fact.
 *
 * §2.9 requirement carried over unchanged: "schedule vs. start-now choice here is itself the
 * first data point for Self-Initiation Trend... doesn't affect this screen's design" — Start Now
 * and Schedule Mission are both [Button] (equal visual weight), not one [Button] and one
 * lower-emphasis [OutlinedButton], so neither reads as the "recommended" path.
 */
@Composable
fun FirstMissionSchedulingScreen(
    onStartNow: () -> Unit,
    onSchedule: (scheduledStart: Instant) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pickedInstant by remember { mutableStateOf<Instant?>(null) }

    val displayFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")
    }

    fun launchPickers() {
        val now = Calendar.getInstance()

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timeInitial = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val combined = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hourOfDay, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        pickedInstant = Instant.ofEpochMilli(combined.timeInMillis)
                    },
                    timeInitial.get(Calendar.HOUR_OF_DAY),
                    timeInitial.get(Calendar.MINUTE),
                    false,
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

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

            Text(
                text = pickedInstant?.let {
                    displayFormatter.format(it.atZone(ZoneId.systemDefault()))
                } ?: stringResource(R.string.first_mission_scheduling_schedule_unset),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedButton(
                onClick = { launchPickers() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.first_mission_scheduling_pick_date_time))
            }

            Button(
                onClick = { pickedInstant?.let(onSchedule) },
                enabled = pickedInstant != null,
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
