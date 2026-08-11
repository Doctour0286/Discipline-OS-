package com.disciplineos.app.ui.mission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.TriggerCueType

/**
 * Batch G5 (BUILD_PLAN.md), Integration Plan §6, base design doc §4.3. Presentation only, same
 * split every other screen in this project follows — [com.disciplineos.app.mission
 * .TriggerCreationFragment] does all reading/writing, this composable only renders state and
 * reports the person's choices back via [onCreate].
 *
 * **`APP_OPEN` is only offered as a real option when [missionArchetype] is
 * [MissionArchetype.CONSTRAINT]** — base doc §4.3: "All `cueType` values except `APP_OPEN` are
 * not independently phone-enforceable," and §6.2 resolves `APP_OPEN` specifically as sugar over
 * an `ALWAYS_ON` period for Constraint missions. Offering it for a non-Constraint mission would
 * imply a blocking mechanism this screen has no path to actually create — the radio option is
 * hidden entirely for other archetypes rather than shown-then-disabled, since there is no future
 * flow (unlike Iron's "shown, not hidden" precedent in Tier Selection) that would ever enable it
 * for those archetypes.
 *
 * **[packageId] field only shown when [TriggerCueType.APP_OPEN] is selected** — every other cue
 * type is descriptive-only (base doc §4.3) and has no package to name. [onCreate] validates
 * (blank cue/response text, or a blank package id when `APP_OPEN` is selected, disable the
 * Create button) rather than allowing an obviously-incomplete submission through to the
 * Fragment — same "catch it here, not one layer down" posture as other onboarding forms.
 */
@Composable
fun TriggerCreationScreen(
    missionArchetype: MissionArchetype,
    onCreate: (cueType: TriggerCueType, cueDescription: String, responseDescription: String, packageId: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCueType by remember { mutableStateOf(TriggerCueType.TIME_OF_DAY) }
    var cueDescription by remember { mutableStateOf("") }
    var responseDescription by remember { mutableStateOf("") }
    var packageId by remember { mutableStateOf("") }

    val isConstraintMission = missionArchetype == MissionArchetype.CONSTRAINT
    val isAppOpen = selectedCueType == TriggerCueType.APP_OPEN

    // APP_OPEN needs a package id; every other cue type needs a response description (what to
    // do instead) — APP_OPEN's "response" is the block itself, see
    // CreateConstraintTriggerUseCase's own kdoc for why that field is empty on that path.
    val canCreate = cueDescription.isNotBlank() &&
        (if (isAppOpen) packageId.isNotBlank() else responseDescription.isNotBlank())

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.trigger_creation_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.trigger_creation_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Text(
                text = stringResource(R.string.trigger_creation_cue_type_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Column(modifier = Modifier.selectableGroup()) {
                CueTypeOption(
                    label = stringResource(R.string.trigger_cue_type_time_of_day),
                    selected = selectedCueType == TriggerCueType.TIME_OF_DAY,
                    onSelect = { selectedCueType = TriggerCueType.TIME_OF_DAY },
                )
                CueTypeOption(
                    label = stringResource(R.string.trigger_cue_type_preceding_event),
                    selected = selectedCueType == TriggerCueType.PRECEDING_EVENT,
                    onSelect = { selectedCueType = TriggerCueType.PRECEDING_EVENT },
                )
                CueTypeOption(
                    label = stringResource(R.string.trigger_cue_type_location),
                    selected = selectedCueType == TriggerCueType.LOCATION,
                    onSelect = { selectedCueType = TriggerCueType.LOCATION },
                )
                // See class kdoc — only a real option for Constraint missions.
                if (isConstraintMission) {
                    CueTypeOption(
                        label = stringResource(R.string.trigger_cue_type_app_open),
                        selected = selectedCueType == TriggerCueType.APP_OPEN,
                        onSelect = { selectedCueType = TriggerCueType.APP_OPEN },
                    )
                }
                CueTypeOption(
                    label = stringResource(R.string.trigger_cue_type_manual),
                    selected = selectedCueType == TriggerCueType.MANUAL,
                    onSelect = { selectedCueType = TriggerCueType.MANUAL },
                )
            }

            OutlinedTextField(
                value = cueDescription,
                onValueChange = { cueDescription = it },
                label = { Text(stringResource(R.string.trigger_creation_cue_description_label)) },
                placeholder = { Text(stringResource(R.string.trigger_creation_cue_description_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
            )

            if (isAppOpen) {
                OutlinedTextField(
                    value = packageId,
                    onValueChange = { packageId = it },
                    label = { Text(stringResource(R.string.trigger_creation_package_label)) },
                    placeholder = { Text(stringResource(R.string.trigger_creation_package_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            } else {
                OutlinedTextField(
                    value = responseDescription,
                    onValueChange = { responseDescription = it },
                    label = { Text(stringResource(R.string.trigger_creation_response_description_label)) },
                    placeholder = { Text(stringResource(R.string.trigger_creation_response_description_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }

            Button(
                onClick = {
                    onCreate(
                        selectedCueType,
                        cueDescription,
                        if (isAppOpen) "" else responseDescription,
                        if (isAppOpen) packageId else null,
                    )
                },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.trigger_creation_create_button))
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.trigger_creation_back_button))
            }
        }
    }
}

@Composable
private fun CueTypeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
