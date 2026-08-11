package com.disciplineos.app.ui.mission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.TargetDirection
import java.time.Instant
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
     * @param targetDirection Batch G6 — the parent [GoalMission]'s [TargetDirection], passed
     *   through so the milestone-creation form can be hidden entirely for a
     *   [TargetDirection.MAINTAIN] mission or one with no target set at all (Addendum §B.3:
     *   ordinal-only milestones remain creatable regardless, but a numeric target milestone has
     *   no well-defined "crossing" without a real direction — see
     *   [com.disciplineos.data.metrics.milestoneAchievementSatisfied]'s own kdoc).
     * @param milestones Batch G6, Integration Plan §7 — every [MilestoneUiItem] for this mission,
     *   in creation order. Empty list (not null) when none exist yet — a real, displayable state
     *   ("no milestones set" card), not the same as [Loading]/[NotFound].
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
        val targetDirection: TargetDirection? = null,
        val milestones: List<MilestoneUiItem> = emptyList(),
    ) : MissionDetailUiState
}

/**
 * Batch G6 — one row in [MissionDetailUiState.Loaded.milestones]. Presentation-shaped subset of
 * [com.disciplineos.data.entity.Milestone]: [id] kept (needed for the update call when the
 * Fragment marks one achieved), [achieved] flattened from `achievedAt != null` since the screen
 * only ever needs the boolean, never the timestamp itself — same "presentation state carries
 * only what rendering needs" split [MissionDetailUiState.Loaded] already draws elsewhere in this
 * file (e.g. [BehaviorReadClassification] over a raw hit-rate double).
 */
data class MilestoneUiItem(
    val id: UUID,
    val label: String,
    val targetValue: Double?,
    val targetDate: Instant?,
    val achieved: Boolean,
)

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
    onAddMilestone: () -> Unit = {},
    onMarkMilestoneAchieved: (UUID) -> Unit = {},
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
                        onAddMilestone = onAddMilestone,
                        onMarkMilestoneAchieved = onMarkMilestoneAchieved,
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
    onAddMilestone: () -> Unit,
    onMarkMilestoneAchieved: (UUID) -> Unit,
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

    // Batch G6, Integration Plan §7, Addendum §B.2 — descriptive-only progress checkpoints,
    // shown regardless of archetype (unlike the relationship view above, which is
    // outcome-driven only) since a Milestone's targetValue is meaningful against any GoalMission
    // shape a person chooses to define one for, not only OUTCOME_DRIVEN missions. Placed after
    // the Adherence/relationship cards, same "a person's own progress data is the primary
    // content" ordering the trigger-prompt card below states for itself.
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.mission_detail_milestones_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (state.milestones.isEmpty()) {
                Text(
                    text = stringResource(R.string.mission_detail_milestones_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else {
                state.milestones.forEach { milestone ->
                    MilestoneRow(
                        milestone = milestone,
                        onMarkAchieved = { onMarkMilestoneAchieved(milestone.id) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = onAddMilestone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.mission_detail_milestones_add_button))
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

/**
 * Batch G6 — strips a trailing ".0" for whole-number target values (e.g. "70" not "70.0"),
 * keeps real decimals as typed otherwise (e.g. "12.5"). Plain [String] formatting, not
 * `@Composable`, so it stays testable without a Compose host — same posture this file's own
 * kdoc already states for [BehaviorReadClassification] staying free of `stringResource`.
 */
internal fun formatMilestoneTargetValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * Batch G6 — one row in the milestones card. Descriptive only, same boundary
 * [MilestoneUiItem]'s own kdoc states for the entity it's shaped from: checking [achieved] here
 * never touches Reputation, Discipline Debt, or any [com.disciplineos.data.ledger.LedgerEntry].
 *
 * [Checkbox] rather than a plain achieved/unachieved [Text] label, matching the closest existing
 * toggleable-row precedent in this project
 * ([com.disciplineos.app.ui.onboarding.AppPickerScreen]'s `AppRow`) — a person can mark an
 * ordinal-only milestone (no [MilestoneUiItem.targetValue]) achieved by hand, since
 * [com.disciplineos.data.metrics.milestoneAchievementSatisfied] can never auto-compute a
 * crossing for one (see that function's own kdoc). [onMarkAchieved] is only ever wired to fire a
 * one-directional "mark achieved" write, never an "un-achieve" — matching that same pure
 * function's "once hit, always hit" contract; the checkbox is disabled once [milestone.achieved]
 * is already true so tapping it can't imply a reversible toggle that doesn't actually exist.
 */
@Composable
private fun MilestoneRow(
    milestone: MilestoneUiItem,
    onMarkAchieved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!milestone.achieved) {
                    Modifier.clickable(onClick = onMarkAchieved)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = milestone.achieved,
            onCheckedChange = { if (!milestone.achieved) onMarkAchieved() },
            enabled = !milestone.achieved,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (milestone.targetValue != null) {
                // %1$s, not %1$f — a Double formatted via stringResource's own %f defaults to
                // the device locale's decimal separator and a fixed 6 decimal places (Java's
                // String.format behavior), neither of which is what a person entered as free
                // text on MilestoneCreationScreen. Pre-formatting to a plain string here keeps
                // the displayed value matching what was typed (e.g. "70" not "70.000000").
                Text(
                    text = stringResource(
                        R.string.mission_detail_milestones_target_value,
                        formatMilestoneTargetValue(milestone.targetValue),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
