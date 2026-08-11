package com.disciplineos.app.mission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.mission.BehaviorReadClassification
import com.disciplineos.app.ui.mission.MissionDetailScreen
import com.disciplineos.app.ui.mission.MissionDetailUiState
import com.disciplineos.app.ui.mission.RelationshipQuadrant
import com.disciplineos.app.ui.mission.RelationshipView
import com.disciplineos.app.ui.theme.themedComposeView
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.MissionLogEntry
import com.disciplineos.data.entity.TargetDirection
import com.disciplineos.domain.usecase.ApplyAdherenceDecayUseCase
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Batch G4 (`BUILD_PLAN.md`), Integration Plan §5. Base design doc §4.1 ("The outcome/behavior
 * relationship view") / §4.2 ("Adherence").
 *
 * **Behavior axis — reads [ApplyAdherenceDecayUseCase.Result.hitRate], not
 * [GoalMission.adherenceScore]'s sign, and not [GoalMission.consecutiveWindowsBelowThreshold].**
 * `[HYPOTHESIS]`, not spelled out verbatim in base doc §4.1 (which names `adherenceScore` as the
 * source) — this departs from the literal field name for reasons worth stating precisely, since
 * it's a real judgment call, not a spec-derived one:
 * - `adherenceScore` is a lagging, decay-only accumulator (see [ApplyAdherenceDecayUseCase]'s own
 *   kdoc): it starts at `0.0`, sits there through any number of missed windows until
 *   `consecutiveWindowsBelowThresholdForDecay()` consecutive bad windows accumulate, and only
 *   *then* moves. A mission actively being missed right now would still read "followed" under an
 *   `adherenceScore >= 0.0` reading for up to that many windows — the wrong answer for a screen
 *   whose whole job is telling someone what's actually happening.
 * - `consecutiveWindowsBelowThreshold == 0` (the "sustained pattern" field base doc §4.5 uses for
 *   the *decay/penalty* trigger) was also considered and rejected: §4.5's "no single miss reads
 *   as failure" framing is stated in a penalty context (don't dock a score over one bad window),
 *   not a diagnostic-display context. Habit-tracking UX research converges on the opposite lesson
 *   for a *display*, not a penalty: a rolling compliance-rate metric over a window is the honest
 *   read; collapsing it into any single streak-style boolean (single-day OR single-window) is
 *   exactly the fragility that research warns against, because it makes a display cliff-edge
 *   around one cutoff regardless of which one.
 * - [ApplyAdherenceDecayUseCase.Result.hitRate] already *is* that rolling-window compliance rate,
 *   computed fresh every call over the same `adherenceWindow` the rest of Adherence uses, checked
 *   against the same [com.disciplineos.domain.policy.AdherenceDecayPolicy.hitRateThreshold] the
 *   domain layer already treats as its "met" line — reusing an existing threshold, not inventing
 *   a second one.
 *
 * **Near-miss handling — the plain-language degree-awareness this screen adds beyond a flat
 * binary.** A `hitRate` within [NEAR_MISS_MARGIN] below the threshold reads as "followed, but
 * close" rather than a flat "skipped" — still classified as followed for quadrant purposes (a
 * rate near the line is meaningfully different from a genuinely low one), with its own copy
 * variant so the plain-language read doesn't flatten "just under" and "far under" into the same
 * sentence. `[HYPOTHESIS]` margin, same placeholder-pending-pilot-data category as every other
 * unstated threshold in this codebase (`AdherenceDecayPolicy`'s own four constants, etc.).
 *
 * **Outcome axis — [MissionArchetype.OUTCOME_DRIVEN] only, matching base doc §4.1's own section
 * title ("...outcome-driven missions").** For other archetypes, [MissionDetailUiState] surfaces
 * Adherence but omits the four-quadrant relationship read entirely (`relationshipView == null`)
 * rather than fabricating an outcome axis with no numeric target to compare against — see
 * [computeMissionDetailState]'s own doc for the exact trend computation.
 *
 * Screen renders the four-quadrant read as plain-language text, not a chart (base doc §4.1's own
 * v1 requirement) — [MissionDetailScreen] is presentation-only; this Fragment does all reading.
 */
class MissionDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        var uiState by mutableStateOf<MissionDetailUiState>(MissionDetailUiState.Loading)

        // No Safe Args plugin in this project (checked — see TierConfirmationFragment's own
        // arguments?.getString(ARG_TIER) precedent) — plain Bundle read, same as every other
        // argument-taking Fragment here, not a one-off.
        val missionIdArg = arguments?.getString(ARG_MISSION_ID)
        val missionId = missionIdArg?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (missionId == null) {
            uiState = MissionDetailUiState.NotFound
        } else {
            loadMissionDetailState(missionId) { state -> uiState = state }
        }

        return themedComposeView {
            MissionDetailScreen(
                uiState = uiState,
                onBack = { findNavController().popBackStack() },
            )
        }
    }

    private fun loadMissionDetailState(missionId: UUID, onLoaded: (MissionDetailUiState) -> Unit) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val goalMission = database.goalMissionDao().get(missionId)
            if (goalMission == null) {
                onLoaded(MissionDetailUiState.NotFound)
                return@launch
            }

            val useCase = AppContainer.applyAdherenceDecayUseCase(context)
            val adherenceResult = useCase.execute(missionId, Instant.now())

            val logEntries = database.missionLogEntryDao().forMission(missionId)
            val hitRateThreshold = AppContainer.adherenceDecayPolicy().hitRateThreshold()

            onLoaded(
                computeMissionDetailState(
                    goalMission = goalMission,
                    adherenceResult = adherenceResult,
                    logEntries = logEntries,
                    hitRateThreshold = hitRateThreshold,
                ),
            )
        }
    }

    companion object {
        /** Matches `onboarding_nav_graph.xml`'s `missionDetailFragment` `missionId` argument name. */
        const val ARG_MISSION_ID = "missionId"
    }
}

/**
 * Pure function — see this file's class kdoc for the full behavior-axis/outcome-axis reasoning.
 * Pulled out of the Fragment specifically so it's unit-testable without Robolectric, matching
 * [com.disciplineos.app.home.computeHomeState]'s exact precedent for this project.
 *
 * @param logEntries ALL log entries for [goalMission], not windowed — the outcome trend
 *   (see [outcomeTrendFor]) intentionally looks at its own recent-vs-earlier split independent of
 *   Adherence's own `adherenceWindow`, since the two axes answer different questions (behavior
 *   compliance rate vs. outcome direction) and nothing in base doc §4.1 ties their windows
 *   together.
 */
fun computeMissionDetailState(
    goalMission: GoalMission,
    adherenceResult: ApplyAdherenceDecayUseCase.Result,
    logEntries: List<MissionLogEntry>,
    hitRateThreshold: Double,
): MissionDetailUiState.Loaded {
    val behaviorRead = behaviorReadFor(result = adherenceResult, hitRateThreshold = hitRateThreshold)

    val relationshipView = if (goalMission.archetype == MissionArchetype.OUTCOME_DRIVEN) {
        val outcomeRead = outcomeTrendFor(goalMission, logEntries)
        if (outcomeRead != null && behaviorRead != null) {
            RelationshipView(
                behaviorFollowed = behaviorRead.followed,
                outcomeMoving = outcomeRead.movingAsExpected,
                quadrant = quadrantFor(behaviorRead.followed, outcomeRead.movingAsExpected),
            )
        } else {
            null
        }
    } else {
        null
    }

    return MissionDetailUiState.Loaded(
        missionId = goalMission.id,
        title = goalMission.title,
        archetype = goalMission.archetype,
        adherenceScore = goalMission.adherenceScore,
        hitRate = adherenceResult.hitRate,
        isSecondary = adherenceResult.isSecondary == true,
        behaviorRead = behaviorRead?.classification,
        relationshipView = relationshipView,
    )
}

private data class BehaviorRead(val followed: Boolean, val classification: BehaviorReadClassification)

/**
 * See class kdoc's "Behavior axis" section for the full reasoning behind this classification.
 *
 * [hitRateThreshold] is read from [com.disciplineos.domain.policy.AdherenceDecayPolicy
 * .hitRateThreshold] at the call site (see [MissionDetailFragment.loadMissionDetailState]) and
 * passed in here rather than duplicated as a second hardcoded literal — this is a display-layer
 * classification of a domain-owned threshold, not a second, independently-maintained copy of it,
 * so reading the real value avoids the exact silent-drift risk a duplicated literal would create
 * if [com.disciplineos.domain.policy.HypothesisAdherenceDecayPolicy]'s own threshold ever changes.
 */
private fun behaviorReadFor(result: ApplyAdherenceDecayUseCase.Result, hitRateThreshold: Double): BehaviorRead? {
    val hitRate = result.hitRate ?: return null
    val threshold = hitRateThreshold
    return when {
        hitRate >= threshold -> BehaviorRead(followed = true, classification = BehaviorReadClassification.ON_TRACK)
        hitRate >= threshold - NEAR_MISS_MARGIN ->
            BehaviorRead(followed = true, classification = BehaviorReadClassification.NEAR_MISS)
        else -> BehaviorRead(followed = false, classification = BehaviorReadClassification.NOT_FOLLOWED)
    }
}

private data class OutcomeRead(val movingAsExpected: Boolean)

/**
 * Compares the mean of the most-recent half of numeric log entries against the earlier half,
 * direction-checked against [GoalMission.targetDirection] — the simplest trend read that doesn't
 * require a real time-series model, matching this project's stated bias (every other `[HYPOTHESIS]`
 * placeholder here) toward the plainest thing that answers the question rather than a more
 * sophisticated forecast v1 doesn't need. `[HYPOTHESIS]`: neither spec doc states a trend
 * algorithm — base doc §4.1 only says the outcome axis should read "moving as expected" or
 * "flat/wrong-direction," not how "moving" is computed from raw log rows.
 *
 * Returns null (no outcome read possible) if [GoalMission.targetValue]/[GoalMission
 * .targetDirection] aren't set, or if there are fewer than [MIN_ENTRIES_FOR_TREND] numeric
 * entries — too little data to call a direction rather than noise.
 */
private fun outcomeTrendFor(goalMission: GoalMission, logEntries: List<MissionLogEntry>): OutcomeRead? {
    val direction = goalMission.targetDirection ?: return null
    val numeric = logEntries.mapNotNull { it.numericValue }
    if (numeric.size < MIN_ENTRIES_FOR_TREND) return null

    val midpoint = numeric.size / 2
    val earlierMean = numeric.take(midpoint).average()
    val recentMean = numeric.drop(midpoint).average()

    val movingAsExpected = when (direction) {
        TargetDirection.INCREASE -> recentMean > earlierMean
        TargetDirection.DECREASE -> recentMean < earlierMean
        TargetDirection.MAINTAIN -> {
            val tolerance = (goalMission.targetValue ?: recentMean) * MAINTAIN_TREND_TOLERANCE_FRACTION
            kotlin.math.abs(recentMean - earlierMean) <= tolerance
        }
    }
    return OutcomeRead(movingAsExpected)
}

/** Base doc §4.1's four quadrants — classification only; plain-language copy lives in
 * [com.disciplineos.app.ui.mission.quadrantMessageFor] as a `stringResource` mapping, same split
 * [com.disciplineos.app.ui.home.observationTextFor] already uses for
 * [com.disciplineos.domain.usecase.FollowUpAction] in this project. */
private fun quadrantFor(behaviorFollowed: Boolean, outcomeMoving: Boolean): RelationshipQuadrant = when {
    behaviorFollowed && outcomeMoving -> RelationshipQuadrant.FOLLOWED_AND_MOVING
    behaviorFollowed && !outcomeMoving -> RelationshipQuadrant.FOLLOWED_BUT_FLAT
    !behaviorFollowed && !outcomeMoving -> RelationshipQuadrant.SKIPPED_AND_FLAT
    else -> RelationshipQuadrant.SKIPPED_BUT_MOVING
}

// [HYPOTHESIS] — how close to the threshold still reads as "close" rather than a flat "skipped."
// No rationale beyond "a round number with real but not excessive slack," same non-derivation
// caveat every other placeholder constant in this codebase states about itself.
private const val NEAR_MISS_MARGIN = 0.15

// [HYPOTHESIS] — minimum numeric log entries before calling a trend direction at all; below
// this, an "earlier half vs. recent half" split is mostly noise. No spec anchor for this number.
private const val MIN_ENTRIES_FOR_TREND = 4

// [HYPOTHESIS] — mirrors ApplyAdherenceDecayUseCase's own MAINTAIN_TOLERANCE_FRACTION exactly
// (10%), reused here for the same "round number with some real slack" reasoning, applied to a
// trend-stability check instead of a single-value target check.
private const val MAINTAIN_TREND_TOLERANCE_FRACTION = 0.10
