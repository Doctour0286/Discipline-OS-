package com.disciplineos.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R
import com.disciplineos.data.entity.PredictiveFailureAlertOutcome
import com.disciplineos.data.entity.Tier
import com.disciplineos.domain.usecase.FollowUpAction
import com.disciplineos.domain.usecase.PredictiveFailureAlert

/**
 * Post-onboarding home shell. Did not exist anywhere in this app before this pass — see this
 * commit's ROADMAP.md entry: `MainActivity` previously only ever ran the onboarding nav graph,
 * and `firstMissionSchedulingFragment` (onboarding's last screen) had no outgoing `<action>` at
 * all, so a user who finished onboarding had nowhere to go. This screen is the minimum real
 * destination that fixes that — not a full dashboard (Daily/Weekly Reports, Reliability Index
 * reporting UI, etc. are all still `⬜` per STATUS.md and out of scope here), just enough to
 * host the one concrete thing this pass was asked to build: a real entry point to the Iron
 * Calibration flow for a user who already completed onboarding at a lower tier.
 *
 * **Why Iron eligibility lives here, not on its own separate settings-style screen:** the only
 * spec anchor for "how does an existing user reach Iron later" is
 * [com.disciplineos.domain.usecase.TierTransitionUseCase.activateIron]'s own kdoc — the
 * calibration window's whole *point* (PRD §12.6 / Data Model §5) is that it lapses in the
 * background while the user just keeps using the app normally. Surfacing it as a persistent
 * card on the first screen the user lands on after onboarding matches that "ambient, not
 * hunted-for" framing better than burying it in a settings menu this app doesn't have yet.
 *
 * **Not shown at all if [currentTier] is already IRON** — nothing left to calibrate toward.
 *
 * **Predictive Failure Alert card (Phase 4, this pass) — see [activeAlert]/[PredictiveFailureAlertCard].**
 * Onboarding/Interaction Spec §3.5's shared card pattern for F1/F2/F3/F5, rendered here as a
 * second, independent optional section — presentation only, same split as everything else on
 * this screen: [com.disciplineos.app.home.HomeFragment] computes which rule (if any) is
 * currently active and passes it in; this composable makes no use-case or database calls of
 * its own for it either.
 *
 * Presentation only, same split every other screen in this project already follows: current
 * tier / calibration state is computed by the hosting Fragment
 * ([com.disciplineos.app.home.HomeFragment]) and passed in as plain parameters; this composable
 * makes no database or use-case calls of its own.
 */
@Composable
fun HomeScreen(
    currentTier: Tier?,
    showIronEligibilityCard: Boolean,
    ironEligibleNow: Boolean,
    daysRemainingUntilIronEligible: Long,
    onOpenIronCalibration: () -> Unit,
    modifier: Modifier = Modifier,
    activeAlert: PredictiveFailureAlert? = null,
    onAlertFollowUpAction: (FollowUpAction) -> Unit = {},
    onAlertDismissed: (PredictiveFailureAlertOutcome) -> Unit = {},
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
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = currentTierLabel(currentTier),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            // Onboarding/Interaction Spec §3.5 — Predictive Failure Alert card. Placed above
            // the Iron eligibility card: no spec section orders the two relative to each other
            // (§3.5 only says the alert lives "on the home/dashboard screen," not where on it),
            // so this is a judgment call, not a spec-derived ordering — reasoning: the alert is
            // a fresh, ambient observation about recent behavior, closer in spirit to
            // time-sensitive content than the Iron card's comparatively static "is the window
            // up yet" status, so it reads first.
            if (activeAlert != null) {
                PredictiveFailureAlertCard(
                    observationText = observationTextFor(activeAlert.followUpAction),
                    followUpActionLabel = followUpLabelFor(activeAlert.followUpAction),
                    onFollowUpAction = { onAlertFollowUpAction(activeAlert.followUpAction) },
                    onNotAccurate = { onAlertDismissed(PredictiveFailureAlertOutcome.NOT_ACCURATE) },
                    onGotIt = { onAlertDismissed(PredictiveFailureAlertOutcome.ACKNOWLEDGED) },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            if (showIronEligibilityCard) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.home_iron_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = if (ironEligibleNow) {
                                stringResource(R.string.home_iron_card_body_eligible)
                            } else {
                                stringResource(
                                    R.string.home_iron_card_body_waiting,
                                    daysRemainingUntilIronEligible,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        Button(
                            onClick = onOpenIronCalibration,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (ironEligibleNow) {
                                        R.string.home_iron_card_button_eligible
                                    } else {
                                        R.string.home_iron_card_button_waiting
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun currentTierLabel(tier: Tier?): String = when (tier) {
    Tier.RECRUIT -> "Recruit"
    Tier.OPERATOR -> "Operator"
    Tier.WARDEN -> "Warden"
    Tier.IRON -> "Iron"
    null -> "No tier set"
}
