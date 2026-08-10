package com.disciplineos.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R

/**
 * Compose screen for Onboarding, Consent & Interaction Spec §2.3 (Tier Explanation). Part of
 * the onboarding-wide Compose migration (ROADMAP.md §5.26 started this). Presentation only —
 * see [com.disciplineos.app.onboarding.TierExplanationFragment]'s kdoc for the full account of
 * why this screen has no selection UI and no persistence.
 *
 * §2.3's "side by side, not sequentially revealed" requirement, carried over from the original
 * `HorizontalScrollView` layout: a horizontally-scrolling [LazyRow] of four equal-width cards,
 * each built from the exact same [TierCard] composable with identical structure (label,
 * enforcement, voice, exit — same order, same styling, no per-tier color/weight/icon) — the
 * anti-pattern requirement (§2.3: don't visually code any tier as "the serious choice") is
 * enforced structurally by sharing one template, same as the original XML's own reasoning.
 */
private data class TierExplanationData(
    val label: String,
    val enforcement: String,
    val voice: String,
    val exit: String,
)

@Composable
fun TierExplanationScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiers = listOf(
        TierExplanationData(
            label = stringResource(R.string.tier_explanation_recruit_label),
            enforcement = stringResource(R.string.tier_explanation_recruit_enforcement),
            voice = stringResource(R.string.tier_explanation_recruit_voice),
            exit = stringResource(R.string.tier_explanation_recruit_exit),
        ),
        TierExplanationData(
            label = stringResource(R.string.tier_explanation_operator_label),
            enforcement = stringResource(R.string.tier_explanation_operator_enforcement),
            voice = stringResource(R.string.tier_explanation_operator_voice),
            exit = stringResource(R.string.tier_explanation_operator_exit),
        ),
        TierExplanationData(
            label = stringResource(R.string.tier_explanation_warden_label),
            enforcement = stringResource(R.string.tier_explanation_warden_enforcement),
            voice = stringResource(R.string.tier_explanation_warden_voice),
            exit = stringResource(R.string.tier_explanation_warden_exit),
        ),
        TierExplanationData(
            label = stringResource(R.string.tier_explanation_iron_label),
            enforcement = stringResource(R.string.tier_explanation_iron_enforcement),
            voice = stringResource(R.string.tier_explanation_iron_voice),
            exit = stringResource(R.string.tier_explanation_iron_exit),
        ),
    )

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Column(modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 12.dp)) {
                Text(
                    text = stringResource(R.string.tier_explanation_step_progress),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.tier_explanation_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.tier_explanation_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tiers) { tier -> TierCard(tier) }
            }

            Column(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Text(stringResource(R.string.tier_explanation_continue))
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_placeholder_back))
                }
            }
        }
    }
}

@Composable
private fun TierCard(tier: TierExplanationData) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(
            text = tier.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = stringResource(R.string.tier_explanation_section_enforcement),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tier.enforcement,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        Text(
            text = stringResource(R.string.tier_explanation_section_voice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tier.voice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        // §2.3: "the single most important disclosure in the whole flow" — its own section,
        // not folded into the enforcement paragraph above, so it can't be skimmed past.
        Text(
            text = stringResource(R.string.tier_explanation_section_exit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tier.exit,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
