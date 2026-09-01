package com.disciplineos.app.ui.mission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R

/**
 * Batch G6 (BUILD_PLAN.md), Integration Plan §7, base design doc Addendum §B.2. Presentation
 * only, same split every other screen in this project follows —
 * [com.disciplineos.app.mission.MilestoneCreationFragment] does all reading/writing, this
 * composable only renders state and reports the person's choice back via [onCreate].
 *
 * **Person-authored only, not auto-generated — a deliberate v1 scope decision, not an
 * oversight.** Addendum §B.3 explicitly leaves "auto-generated vs. person-authored milestones"
 * as an open, un-signed-off question ("this should be a stated, sign-off'd default, not an
 * implementation-time guess"). This project's own stated convention is to not invent behavior a
 * spec leaves open (`MissionProfile`'s kdoc rejecting an unfounded allowlist/blocklist split,
 * §5.30's decision log) — so this screen only ever lets the person define their own milestones
 * by hand. Auto-generation (e.g. proposing N evenly-spaced milestones for a `FIXED_CALENDAR`
 * mission with a cadence) remains a real, named future option, not resolved here.
 *
 * **[targetValue]/[targetDate] both optional, matching Addendum §B.2's own field list exactly**
 * ("targetDate: Instant | null — null = milestone is ordinal only ('halfway'), not date-bound").
 * [label] is the only required field — a milestone with neither a numeric target nor a date is
 * still a real, valid checkpoint per the Addendum's own framing, just one that can never be
 * auto-achieved by [com.disciplineos.data.metrics.milestoneAchievementSatisfied] (which requires
 * a non-null [targetValue] and a real [com.disciplineos.data.entity.TargetDirection] to compute
 * a crossing) — the person can still mark it manually via the achieved toggle on the Mission
 * Detail milestone list.
 *
 * [targetValue] is entered as free text and parsed as a [Double] at the call site (this
 * composable never parses it itself, keeping numeric-string interpretation exactly where every
 * other form field's write path already lives — see [onCreate]'s own type) — an unparseable
 * non-blank value is treated as invalid (Create disabled) rather than silently dropped or
 * coerced to null, same "catch it here, not one layer down" posture
 * [TriggerCreationScreen]'s own kdoc states for its own validation.
 *
 * No date picker is wired for [targetDate] in this pass — a full [java.time.Instant]-precision
 * `DatePickerDialog` integration is real UI work with no existing precedent to reuse elsewhere
 * in this codebase (every other `Instant` field here is either `Instant.now()` at write time or
 * a duration/countdown, never a person-picked future date), so it's flagged here as scope this
 * pass deliberately doesn't cover rather than built ad hoc. [onCreate] always receives `null`
 * for `targetDate` as of this pass.
 */
@Composable
fun MilestoneCreationScreen(
    onCreate: (label: String, targetValue: Double?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by remember { mutableStateOf("") }
    var targetValueText by remember { mutableStateOf("") }

    val parsedTargetValue = targetValueText.toDoubleOrNull()
    val targetValueIsInvalid = targetValueText.isNotBlank() && parsedTargetValue == null
    val canCreate = label.isNotBlank() && !targetValueIsInvalid

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.milestone_creation_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.milestone_creation_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.milestone_creation_label_label)) },
                placeholder = { Text(stringResource(R.string.milestone_creation_label_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = targetValueText,
                onValueChange = { targetValueText = it },
                label = { Text(stringResource(R.string.milestone_creation_target_value_label)) },
                placeholder = { Text(stringResource(R.string.milestone_creation_target_value_placeholder)) },
                isError = targetValueIsInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.milestone_creation_target_value_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Button(
                onClick = { onCreate(label, parsedTargetValue) },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.milestone_creation_create_button))
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.milestone_creation_back_button))
            }
        }
    }
}
