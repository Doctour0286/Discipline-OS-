package com.disciplineos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import com.disciplineos.data.entity.Tier

/**
 * Compose screen for Onboarding, Consent & Interaction Spec §2.4 (Tier Selection). Part of
 * the onboarding-wide Compose migration (ROADMAP.md §5.26 started this). Presentation only —
 * all business logic (which tier routes to confirmation vs. direct submission, the
 * User-row re-entry guard, ONBOARDING_CONSENT_VERSION) stays exactly as-is in
 * [com.disciplineos.app.onboarding.TierSelectionFragment], whose kdoc has the full account.
 * This screen was already CI + device confirmed in its XML form — this migration changes only
 * how the same four-option choice is rendered, not what tapping Continue does afterward.
 *
 * Iron is shown (per §12.6 "shown, not hidden") but not selectable — `enabled = false` on its
 * [RadioButton], mirroring the original layout's `android:enabled="false"`, so [onContinue]
 * can never be invoked with [Tier.IRON] from this screen; the Fragment's own
 * `require(tier != Tier.IRON)` defense-in-depth in `selectInitialTier` stays as the second
 * layer, unchanged by this migration.
 */
@Composable
fun TierSelectionScreen(
    onContinue: (selectedTier: Tier) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recruit selected by default, matching the original layout's android:checked="true" on
    // the Recruit RadioButton.
    var selected by remember { mutableStateOf(Tier.RECRUIT) }

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.tier_selection_step_progress),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.tier_selection_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.tier_selection_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Column(modifier = Modifier.selectableGroup()) {
                TierOption(
                    label = stringResource(R.string.tier_recruit_label),
                    description = stringResource(R.string.tier_recruit_description),
                    selected = selected == Tier.RECRUIT,
                    enabled = true,
                    onSelect = { selected = Tier.RECRUIT },
                )
                TierOption(
                    label = stringResource(R.string.tier_operator_label),
                    description = stringResource(R.string.tier_operator_description),
                    selected = selected == Tier.OPERATOR,
                    enabled = true,
                    onSelect = { selected = Tier.OPERATOR },
                )
                TierOption(
                    label = stringResource(R.string.tier_warden_label),
                    description = stringResource(R.string.tier_warden_description),
                    selected = selected == Tier.WARDEN,
                    enabled = true,
                    onSelect = { selected = Tier.WARDEN },
                )
                // Iron: shown, never selectable — see this file's kdoc.
                TierOption(
                    label = stringResource(R.string.tier_iron_label),
                    description = stringResource(R.string.tier_iron_description),
                    selected = false,
                    enabled = false,
                    onSelect = {},
                )
            }

            Button(
                onClick = { onContinue(selected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 12.dp),
            ) {
                Text(stringResource(R.string.tier_selection_continue))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_placeholder_back))
            }
        }
    }
}

@Composable
private fun TierOption(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onSelect,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 8.dp, top = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
