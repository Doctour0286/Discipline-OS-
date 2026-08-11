package com.disciplineos.app.ui.mission

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R
import com.disciplineos.data.entity.MissionArchetype
import java.util.UUID

/**
 * Batch G4 — see [com.disciplineos.app.mission.MissionDetailFragment]'s class kdoc for the full
 * behavior/outcome-axis reasoning this state is built from. Presentation only, same split every
 * other screen in this project follows: the Fragment computes state, this composable renders it.
 */
sealed interface MissionDetailUiState {
    data object Loading : MissionDetailUiState
    data object NotFound : MissionDetailUiState

    /**
     * @param hitRate current window's compliance rate (0.0–1.0), null if never evaluated.
     * @param isSecondary true when Adherence should be shown as secondary rather than primary
     *   (base doc §4.2) — a Behavior-driven mission with an attached EnforcementSession already
     *   getting Reputation/Debt treatment via that session.
     * @param behaviorReadLabel plain-language behavior-axis read (e.g. "On track", "Close — just
     *   under this window", "Behavior not being followed"), null if [hitRate] is null.
     * @param relationshipView the four-quadrant read (base doc §4.1), null for any non-
     *   [MissionArchetype.OUTCOME_DRIVEN] mission or when there isn't yet enough log data to call
     *   an outcome trend — see [com.disciplineos.app.mission.computeMissionDetailState]'s own doc.
     * @param showTriggerPrompt Batch G5 — true while the mission is in
     *   [com.disciplineos.data.entity.LifecycleStage.HYPOTHESIZING] and no Trigger has been
     *   attached or dismissed yet. See [com.disciplineos.app.mission.MissionDetailFragment]'s
     *   class kdoc, "Batch G5 addition" section, for the full placement/dismissal reasoning.
     */
    data class Loaded(
        val missionId: UUID,
        val title: String,
        val archetype: MissionArchetype,
        val adherenceScore: Double?,
        val hitRate: Double?,
        val isSecondary: Boolean,
        val behaviorRead: BehaviorReadClassification?,
        val relationshipView: RelationshipView?,
        val showTriggerPrompt: Boolean = false,
    ) : MissionDetailUiState
}

/**
 * Behavior-axis classification only — see [com.disciplineos.app.mission.MissionDetailFragment]'s
 * class kdoc ("Behavior axis" / "Near-miss handling" sections) for the full reasoning behind
 * these three buckets. Mapped to plain-language copy by [behaviorReadLabelFor], a `@Composable`
 * `stringResource` mapping — same split [com.disciplineos.app.ui.home.observationTextFor] already
 * uses for [com.disciplineos.domain.usecase.FollowUpAction] in this project, kept here rather
 * than inlined as a raw `String` so the pure function stays free of Compose/`stringResource`
 * dependencies (untestable without a Compose host otherwise).
 */
enum class BehaviorReadClassification { ON_TRACK, NEAR_MISS, NOT_FOLLOWED }

/** Base doc §4.1's four quadrants — classification only, see [BehaviorReadClassification]'s kdoc for why. */
enum class RelationshipQuadrant { FOLLOWED_AND_MOVING, FOLLOWED_BUT_FLAT, SKIPPED_AND_FLAT, SKIPPED_BUT_MOVING }

/** Base doc §4.1's four-quadrant relationship read — [quadrant] mapped to copy by [quadrantMessageFor]. */
data class RelationshipView(
    val behaviorFollowed: Boolean,
    val outcomeMoving: Boolean,
    val quadrant: RelationshipQuadrant,
)

/** See [BehaviorReadClassification]'s kdoc for why this mapping lives here, not in the pure function. */
@Composable
private fun behaviorReadLabelFor(classification: BehaviorReadClassification): String = when (classification) {
    BehaviorReadClassification.ON_TRACK -> stringResource(R.string.mission_detail_behavior_on_track)
    BehaviorReadClassification.NEAR_MISS -> stringResource(R.string.mission_detail_behavior_near_miss)
    BehaviorReadClassification.NOT_FOLLOWED -> stringResource(R.string.mission_detail_behavior_not_followed)
}

/** Base doc §4.1's four plain-language quadrant messages. See [RelationshipQuadrant]'s kdoc. */
@Composable
private fun quadrantMessageFor(quadrant: RelationshipQuadrant): String = when (quadrant) {
    RelationshipQuadrant.FOLLOWED_AND_MOVING -> stringResource(R.string.mission_detail_quadrant_followed_and_moving)
    RelationshipQuadrant.FOLLOWED_BUT_FLAT -> stringResource(R.string.mission_detail_quadrant_followed_but_flat)
    RelationshipQuadrant.SKIPPED_AND_FLAT -> stringResource(R.string.mission_detail_quadrant_skipped_and_flat)
    RelationshipQuadrant.SKIPPED_BUT_MOVING -> stringResource(R.string.mission_detail_quadrant_skipped_but_moving)
}

@Composable
fun MissionDetailScreen(
    uiState: MissionDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAttachTrigger: () -> Unit = {},
    onDismissTriggerPrompt: () -> Unit = {},
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
            when (uiState) {
                is MissionDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                }

                is MissionDetailUiState.NotFound -> {
                    Text(
                        text = stringResource(R.string.mission_detail_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }

                is MissionDetailUiState.Loaded -> {
                    MissionDetailContent(
                        state = uiState,
                        onAttachTrigger = onAttachTrigger,
                        onDismissTriggerPrompt = onDismissTriggerPrompt,
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.mission_detail_back_button))
            }
        }
    }
}

@Composable
private fun MissionDetailContent(
    state: MissionDetailUiState.Loaded,
    onAttachTrigger: () -> Unit,
    onDismissTriggerPrompt: () -> Unit,
) {
    Text(
        text = state.title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 24.dp),
    )

    // Base doc §4.2: shown "on the Mission detail screen (always)" — this card renders
    // regardless of archetype, unlike the relationship view below which is outcome-driven only.
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(
                    if (state.isSecondary) {
                        R.string.mission_detail_adherence_title_secondary
                    } else {
                        R.string.mission_detail_adherence_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = state.behaviorRead?.let { behaviorReadLabelFor(it) }
                    ?: stringResource(R.string.mission_detail_adherence_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Base doc §4.1 — outcome-driven missions only, plain-language quadrant read, not a chart.
    val relationshipView = state.relationshipView
    if (relationshipView != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.mission_detail_relationship_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = quadrantMessageFor(relationshipView.quadrant),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (state.archetype == MissionArchetype.OUTCOME_DRIVEN) {
        // Outcome-driven, but not enough log data yet to call a trend — real, distinct state
        // from "not applicable to this archetype," shown as its own message rather than
        // silently omitting the card (see computeMissionDetailState's MIN_ENTRIES_FOR_TREND).
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.mission_detail_relationship_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.mission_detail_relationship_not_enough_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Batch G5, Integration Plan §6, base doc §4.3 — "shown once per Mission during
    // Hypothesizing... offered more assertively than a bare mention but never mandatory."
    // Placed after the Adherence/relationship cards (a person's own progress data is the
    // primary content this screen exists to surface), same "judgment call, no spec section
    // orders these relative to each other" posture com.disciplineos.app.ui.home.HomeScreen's
    // own card-ordering comments already use for its own optional cards.
    if (state.showTriggerPrompt) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.mission_detail_trigger_prompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.mission_detail_trigger_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Button(
                    onClick = onAttachTrigger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(stringResource(R.string.mission_detail_trigger_prompt_attach_button))
                }
                TextButton(
                    onClick = onDismissTriggerPrompt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.mission_detail_trigger_prompt_dismiss_button))
                }
            }
        }
    }
}
