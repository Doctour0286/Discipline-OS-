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
import androidx.compose.runtime.LaunchedEffect
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
 * [com.disciplineos.app.onboarding.MissionProfileSetupFragment], whose kdoc explains why no
 * `:domain` use-case wraps this single unconditional insert.
 *
 * **[suggestedBlocklist] (ROADMAP.md §5.30):** §2.8's own text — "should default to
 * suggestions drawn from §2.2's flagged categories rather than a blank list, to reduce
 * first-session abandonment" — pre-fills the blocklist field's initial value only; the
 * allowlist starts empty regardless. See [MissionProfileSetupFragment]'s kdoc for why
 * blocklist, not allowlist, is the correct target given what this codebase's flagged-category
 * data actually is. Still freely editable — this is a starting point, not a locked default;
 * the user can clear or change it like any other field content before continuing.
 *
 * **Arrives asynchronously, applied exactly once.** [MissionProfileSetupFragment] can't await
 * its DB read before `onCreateView` returns a `View` synchronously, so this composable starts
 * with `suggestedBlocklist = ""` and the Fragment supplies the real value once its query
 * resolves (almost always within one recomposition in practice, but not guaranteed). Applying
 * it via [LaunchedEffect] keyed on [suggestedBlocklist] — rather than seeding
 * `remember { mutableStateOf(suggestedBlocklist) }` directly — means a late-arriving value
 * still reaches the field; a `hasAppliedSuggestion` guard stops that same effect from
 * re-firing and clobbering the user's own edits if [suggestedBlocklist] happens to recompute
 * again later (e.g. Fragment recreation) after the user already changed the field by hand.
 */
@Composable
fun MissionProfileSetupScreen(
    onContinue: (name: String, allowlistRaw: String, blocklistRaw: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    suggestedBlocklist: String = "",
) {
    var name by remember { mutableStateOf("") }
    var allowlist by remember { mutableStateOf("") }
    var blocklist by remember { mutableStateOf("") }
    var hasAppliedSuggestion by remember { mutableStateOf(false) }

    LaunchedEffect(suggestedBlocklist) {
        if (!hasAppliedSuggestion && suggestedBlocklist.isNotEmpty()) {
            blocklist = suggestedBlocklist
            hasAppliedSuggestion = true
        }
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

            Text(
                text = stringResource(R.string.mission_profile_setup_allowlist_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.mission_profile_setup_allowlist_hint_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = allowlist,
                onValueChange = { allowlist = it },
                placeholder = { Text(stringResource(R.string.mission_profile_setup_allowlist_hint)) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )

            Text(
                text = stringResource(R.string.mission_profile_setup_blocklist_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.mission_profile_setup_blocklist_hint_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (hasAppliedSuggestion) {
                Text(
                    text = stringResource(R.string.mission_profile_setup_blocklist_suggested_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            OutlinedTextField(
                value = blocklist,
                onValueChange = { blocklist = it },
                placeholder = { Text(stringResource(R.string.mission_profile_setup_blocklist_hint)) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            Text(
                text = stringResource(R.string.mission_profile_setup_empty_ok_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            Button(
                onClick = { onContinue(name, allowlist, blocklist) },
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
