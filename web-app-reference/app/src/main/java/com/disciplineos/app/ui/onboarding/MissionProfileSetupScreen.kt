package com.disciplineos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R

/**
 * Compose screen for Onboarding, Consent & Interaction Spec §2.8 (Mission Profile Setup).
 * Part of the onboarding-wide Compose migration (ROADMAP.md §5.26 started this). Presentation
 * only — the [com.disciplineos.data.entity.MissionProfile] insert, its re-entry guard, and the
 * default-name fallback all stay in
 * [com.disciplineos.app.onboarding.MissionProfileSetupFragment].
 *
 * **Allow/blocklist are now app-picker driven, not free text (this pass).** Previously both
 * fields were plain `OutlinedTextField`s where the user hand-typed one package id per line —
 * flagged as a real correctness risk, not just a UX rough edge: a typo'd package id silently
 * does nothing in `InterceptionController`'s membership check, meaning the single mechanism
 * this whole product is built around (Mission enforcement) could silently fail with no
 * feedback to the user. [onAllowlistPickerRequested]/[onBlocklistPickerRequested] hand off to
 * [AppPickerScreen] (a separate destination, not inlined here — the installed-app list can be
 * 100+ items, which belongs in its own scrollable/searchable screen, not embedded in this
 * already-long form). This screen now just displays the current selection as a label summary
 * and a button that opens the picker; [allowlistSelection]/[blocklistSelection] (by package
 * name) are owned by the hosting Fragment so the selection survives navigating to the picker
 * and back.
 *
 * **[suggestedBlocklistNote] (ROADMAP.md §5.30):** §2.8's own text — "should default to
 * suggestions drawn from §2.2's flagged categories rather than a blank list, to reduce
 * first-session abandonment" — still applies; the hosting Fragment resolves flagged
 * *categories* into matching installed *packages* and pre-populates [blocklistSelection]
 * before this composable first renders. [suggestedBlocklistNote] controls only whether the
 * "pre-filled from your flagged categories" caption shows, so the caption still disappears
 * once the user's own edits diverge from the suggestion — same intent the old
 * `hasAppliedSuggestion` free-text flag tracked, sourced from the Fragment instead of computed
 * here. Still freely editable — this is a starting point, not a locked default.
 */
@Composable
fun MissionProfileSetupScreen(
    onContinue: (name: String) -> Unit,
    onBack: () -> Unit,
    onAllowlistPickerRequested: () -> Unit,
    onBlocklistPickerRequested: () -> Unit,
    allowlistSelection: List<AppSelectionEntry>,
    blocklistSelection: List<AppSelectionEntry>,
    modifier: Modifier = Modifier,
    suggestedBlocklistNote: Boolean = false,
) {
    var name by remember { mutableStateOf("") }

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
                text = stringResource(R.string.mission_profile_setup_step_progress),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.mission_profile_setup_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.mission_profile_setup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Text(
                text = stringResource(R.string.mission_profile_setup_name_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.mission_profile_setup_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )

            AppSelectionField(
                label = stringResource(R.string.mission_profile_setup_allowlist_label),
                bodyHint = stringResource(R.string.mission_profile_setup_allowlist_hint_body),
                selection = allowlistSelection,
                onPickerRequested = onAllowlistPickerRequested,
            )

            AppSelectionField(
                label = stringResource(R.string.mission_profile_setup_blocklist_label),
                bodyHint = stringResource(R.string.mission_profile_setup_blocklist_hint_body),
                selection = blocklistSelection,
                onPickerRequested = onBlocklistPickerRequested,
                suggestedNote = if (suggestedBlocklistNote) {
                    stringResource(R.string.mission_profile_setup_blocklist_suggested_note)
                } else {
                    null
                },
            )

            Text(
                text = stringResource(R.string.mission_profile_setup_empty_ok_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            Button(
                onClick = { onContinue(name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.mission_profile_setup_continue))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_placeholder_back))
            }
        }
    }
}

/**
 * One entry in an allow/blocklist selection, as displayed on this screen and passed back to
 * [com.disciplineos.app.onboarding.MissionProfileSetupFragment] for the eventual
 * [com.disciplineos.data.entity.MissionProfile] insert (which only needs [packageName] — see
 * that Fragment's `submitProfile`).
 */
data class AppSelectionEntry(
    val packageName: String,
    val label: String,
)

@Composable
private fun AppSelectionField(
    label: String,
    bodyHint: String,
    selection: List<AppSelectionEntry>,
    onPickerRequested: () -> Unit,
    suggestedNote: String? = null,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = bodyHint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    if (suggestedNote != null) {
        Text(
            text = suggestedNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    Text(
        text = if (selection.isEmpty()) {
            stringResource(R.string.mission_profile_setup_selection_empty)
        } else {
            selection.joinToString(separator = ", ") { it.label }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    OutlinedButton(
        onClick = onPickerRequested,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
    ) {
        Text(stringResource(R.string.mission_profile_setup_choose_apps))
    }
}
