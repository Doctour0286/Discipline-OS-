package com.disciplineos.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R
import com.disciplineos.domain.usecase.FollowUpAction

/**
 * Onboarding/Interaction Spec §3.5 — "one pattern used by all of F1, F2, F3, and (once its
 * threshold is set) F5." One rule, one card, one dismissal — §3.5's "does not stack multiple
 * alerts into one card" rule is enforced by the caller ([HomeScreen]) rendering at most one of
 * these per composition pass, not by anything in this file itself.
 *
 * **Deliberately uses the exact same neutral `surfaceContainer` card styling as
 * [com.disciplineos.app.ui.home.HomeScreen]'s Iron eligibility card** — §3.5: "Does not use
 * severity color coding (red/yellow) across rules. All four use the same neutral visual
 * treatment." No color parameter exists on this composable at all, which is intentional: a
 * hypothetical future rule cannot accidentally introduce severity coloring through a parameter
 * this component doesn't expose.
 *
 * **No "Warning"/"Alert" badge — just [observationText] as a plain sentence.** §3.5: "No
 * headline framing beyond this line ... which would smuggle interception-screen urgency into a
 * reflective surface it doesn't belong on."
 *
 * **Two dismissal buttons, never one.** §3.5: "Collapsing them into a single 'Dismiss' loses
 * the accuracy signal ... and a user who was actually wrong deserves a lower-friction way to
 * say so." [onNotAccurate] and [onGotIt] are separate callbacks specifically so the caller
 * (`HomeFragment`) can log which outcome fired to
 * [com.disciplineos.data.dao.PredictiveFailureAlertDismissalDao] distinctly, per Fingerprint
 * doc §5's accuracy-tracking requirement.
 */
@Composable
fun PredictiveFailureAlertCard(
    observationText: String,
    followUpActionLabel: String,
    onFollowUpAction: () -> Unit,
    onNotAccurate: () -> Unit,
    onGotIt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = observationText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedButton(
                onClick = onFollowUpAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(followUpActionLabel)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onNotAccurate) {
                    Text(stringResource(R.string.predictive_alert_dismiss_not_accurate))
                }
                TextButton(onClick = onGotIt) {
                    Text(stringResource(R.string.predictive_alert_dismiss_got_it))
                }
            }
        }
    }
}

/**
 * Resolves the plain-string [observationText] for a triggered rule's alert card, per §3.5's
 * "Fingerprint doc §3 gives the actual per-rule copy" instruction — one `stringResource` lookup
 * per rule, held here rather than inline at each call site so `HomeScreen`'s render loop (or any
 * future second surface that shows these cards) doesn't duplicate the rule→string mapping.
 * F4 deliberately has no branch — see [FollowUpAction]'s kdoc; a [FollowUpAction] value only
 * ever exists for a rule §3.5 lists as user-facing, so an exhaustive `when` here needs no F4 case.
 */
@Composable
fun observationTextFor(followUpAction: FollowUpAction): String = when (followUpAction) {
    FollowUpAction.REVIEW_EVENING_MISSION_PROFILE -> stringResource(R.string.predictive_alert_f1_observation)
    FollowUpAction.REVIEW_MISSION_PROFILE_SCOPE -> stringResource(R.string.predictive_alert_f2_observation)
    FollowUpAction.OPEN_RECOVERY_MODE -> stringResource(R.string.predictive_alert_f3_observation)
    FollowUpAction.REVIEW_MISSION_PROFILE_DRIFT -> stringResource(R.string.predictive_alert_f5_observation)
}

/** Follow-up button label per rule — see [observationTextFor]'s kdoc for the same reasoning. */
@Composable
fun followUpLabelFor(followUpAction: FollowUpAction): String = when (followUpAction) {
    FollowUpAction.REVIEW_EVENING_MISSION_PROFILE -> stringResource(R.string.predictive_alert_action_review_evening_profile)
    FollowUpAction.REVIEW_MISSION_PROFILE_SCOPE -> stringResource(R.string.predictive_alert_action_review_profile_scope)
    FollowUpAction.OPEN_RECOVERY_MODE -> stringResource(R.string.predictive_alert_action_open_recovery_mode)
    FollowUpAction.REVIEW_MISSION_PROFILE_DRIFT -> stringResource(R.string.predictive_alert_action_review_profile_drift)
}
