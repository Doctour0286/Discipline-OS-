package com.disciplineos.app.mission

import com.disciplineos.app.ui.mission.BehaviorReadClassification
import com.disciplineos.app.ui.mission.RelationshipQuadrant
import com.disciplineos.data.entity.CadenceType
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.LifecycleStage
import com.disciplineos.data.entity.MeasurementSource
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.MissionLogEntry
import com.disciplineos.data.entity.ResetMode
import com.disciplineos.data.entity.TargetDirection
import com.disciplineos.domain.usecase.ApplyAdherenceDecayUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Covers [computeMissionDetailState] directly — a plain pure function, deliberately pulled out
 * of [MissionDetailFragment] so it's testable without a Robolectric/Fragment host, same
 * reasoning [com.disciplineos.app.home.HomeFragmentTest] already gives for
 * [com.disciplineos.app.home.computeHomeState].
 *
 * [HIT_RATE_THRESHOLD] here mirrors [com.disciplineos.domain.policy
 * .HypothesisAdherenceDecayPolicy.hitRateThreshold]'s 0.7 default — passed explicitly into
 * [computeMissionDetailState] the same way the real call site
 * ([MissionDetailFragment.loadMissionDetailState]) reads it from
 * `AppContainer.adherenceDecayPolicy().hitRateThreshold()`, not duplicated as a hardcoded
 * literal inside the function under test.
 */
class MissionDetailFragmentTest {

    private val HIT_RATE_THRESHOLD = 0.7

    private fun goalMission(
        archetype: MissionArchetype = MissionArchetype.BEHAVIOR_DRIVEN,
        targetDirection: TargetDirection? = null,
        targetValue: Double? = null,
        adherenceScore: Double? = null,
        lifecycleStage: LifecycleStage = LifecycleStage.ENFORCING,
        triggerPromptDismissedAt: Instant? = null,
    ): GoalMission = GoalMission(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        title = "Test mission",
        archetype = archetype,
        targetDirection = targetDirection,
        targetValue = targetValue,
        unit = null,
        cadenceType = CadenceType.DAILY,
        resetMode = ResetMode.ROLLING_WINDOW,
        measurementSource = MeasurementSource.MANUAL_LOG,
        lifecycleStage = lifecycleStage,
        adherenceScore = adherenceScore,
        adherenceWindow = 7,
        createdAt = Instant.EPOCH,
        archivedAt = null,
        triggerPromptDismissedAt = triggerPromptDismissedAt,
    )

    private fun result(hitRate: Double?, isSecondary: Boolean? = false): ApplyAdherenceDecayUseCase.Result =
        ApplyAdherenceDecayUseCase.Result(
            inScope = true,
            hitRate = hitRate,
            isSecondary = isSecondary,
        )

    private fun logEntry(numericValue: Double?): MissionLogEntry = MissionLogEntry(
        id = UUID.randomUUID(),
        missionId = UUID.randomUUID(),
        createdAt = Instant.EPOCH,
        note = null,
        numericValue = numericValue,
    )

    // --- Behavior axis ---

    @Test
    fun `hitRate at or above threshold reads as on track`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(),
            adherenceResult = result(hitRate = 0.85),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(BehaviorReadClassification.ON_TRACK, state.behaviorRead)
    }

    @Test
    fun `hitRate just under threshold within the near-miss margin reads as near miss`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(),
            adherenceResult = result(hitRate = 0.6), // threshold 0.7, margin 0.15 -> [0.55, 0.7)
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(BehaviorReadClassification.NEAR_MISS, state.behaviorRead)
    }

    @Test
    fun `hitRate far below threshold reads as not followed`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(),
            adherenceResult = result(hitRate = 0.2),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(BehaviorReadClassification.NOT_FOLLOWED, state.behaviorRead)
    }

    @Test
    fun `null hitRate (never evaluated) reads as no behavior data rather than not followed`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(),
            adherenceResult = result(hitRate = null),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertNull(state.behaviorRead)
    }

    @Test
    fun `a different domain threshold changes the classification for the same hitRate`() {
        // Same 0.6 hitRate as the near-miss case above, but with a lower threshold (0.5) it's
        // now comfortably on track — proves the function actually uses the passed-in threshold
        // rather than a hardcoded literal (the bug this test suite exists to catch).
        val state = computeMissionDetailState(
            goalMission = goalMission(),
            adherenceResult = result(hitRate = 0.6),
            logEntries = emptyList(),
            hitRateThreshold = 0.5,
        )
        assertEquals(BehaviorReadClassification.ON_TRACK, state.behaviorRead)
    }

    @Test
    fun `isSecondary true on the result is carried through to isSecondary on the state`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(),
            adherenceResult = result(hitRate = 0.9, isSecondary = true),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertTrue(state.isSecondary)
    }

    // --- Outcome axis / relationship view ---

    @Test
    fun `non-outcome-driven missions never get a relationship view`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(archetype = MissionArchetype.BEHAVIOR_DRIVEN),
            adherenceResult = result(hitRate = 0.9),
            logEntries = List(10) { logEntry(numericValue = it.toDouble()) },
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertNull(state.relationshipView)
    }

    @Test
    fun `outcome-driven mission with too few numeric entries gets no relationship view`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = TargetDirection.INCREASE,
                targetValue = 100.0,
            ),
            adherenceResult = result(hitRate = 0.9),
            logEntries = listOf(logEntry(1.0), logEntry(2.0)), // below MIN_ENTRIES_FOR_TREND (4)
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertNull(state.relationshipView)
    }

    @Test
    fun `outcome-driven, behavior followed, outcome increasing as expected is FOLLOWED_AND_MOVING`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = TargetDirection.INCREASE,
                targetValue = 100.0,
            ),
            adherenceResult = result(hitRate = 0.9),
            logEntries = listOf(logEntry(1.0), logEntry(2.0), logEntry(10.0), logEntry(12.0)),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(RelationshipQuadrant.FOLLOWED_AND_MOVING, state.relationshipView?.quadrant)
    }

    @Test
    fun `outcome-driven, behavior followed, outcome flat is FOLLOWED_BUT_FLAT`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = TargetDirection.INCREASE,
                targetValue = 100.0,
            ),
            adherenceResult = result(hitRate = 0.9),
            logEntries = listOf(logEntry(10.0), logEntry(10.0), logEntry(9.0), logEntry(9.5)),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(RelationshipQuadrant.FOLLOWED_BUT_FLAT, state.relationshipView?.quadrant)
    }

    @Test
    fun `outcome-driven, behavior not followed, outcome flat is SKIPPED_AND_FLAT`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = TargetDirection.INCREASE,
                targetValue = 100.0,
            ),
            adherenceResult = result(hitRate = 0.1),
            logEntries = listOf(logEntry(10.0), logEntry(10.0), logEntry(9.0), logEntry(9.5)),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(RelationshipQuadrant.SKIPPED_AND_FLAT, state.relationshipView?.quadrant)
    }

    @Test
    fun `outcome-driven, behavior not followed, outcome moving is SKIPPED_BUT_MOVING`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = TargetDirection.INCREASE,
                targetValue = 100.0,
            ),
            adherenceResult = result(hitRate = 0.1),
            logEntries = listOf(logEntry(1.0), logEntry(2.0), logEntry(10.0), logEntry(12.0)),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(RelationshipQuadrant.SKIPPED_BUT_MOVING, state.relationshipView?.quadrant)
    }

    @Test
    fun `MAINTAIN direction within tolerance counts as moving as expected`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = TargetDirection.MAINTAIN,
                targetValue = 100.0,
            ),
            adherenceResult = result(hitRate = 0.9),
            // earlier mean 100, recent mean 102 -> within 10% tolerance of target (10.0)
            logEntries = listOf(logEntry(100.0), logEntry(100.0), logEntry(101.0), logEntry(103.0)),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(RelationshipQuadrant.FOLLOWED_AND_MOVING, state.relationshipView?.quadrant)
    }

    @Test
    fun `MAINTAIN direction outside tolerance does not count as moving as expected`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = TargetDirection.MAINTAIN,
                targetValue = 100.0,
            ),
            adherenceResult = result(hitRate = 0.9),
            // earlier mean 100, recent mean 150 -> well outside 10% tolerance of target
            logEntries = listOf(logEntry(100.0), logEntry(100.0), logEntry(140.0), logEntry(160.0)),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(RelationshipQuadrant.FOLLOWED_BUT_FLAT, state.relationshipView?.quadrant)
    }

    @Test
    fun `outcome-driven mission with no targetDirection gets no relationship view regardless of log volume`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                archetype = MissionArchetype.OUTCOME_DRIVEN,
                targetDirection = null,
                targetValue = null,
            ),
            adherenceResult = result(hitRate = 0.9),
            logEntries = List(10) { logEntry(it.toDouble()) },
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertNull(state.relationshipView)
    }

    @Test
    fun `basic fields pass through unchanged from the GoalMission and Result`() {
        val mission = goalMission(adherenceScore = 42.0)
        val state = computeMissionDetailState(
            goalMission = mission,
            adherenceResult = result(hitRate = 0.5),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertEquals(mission.id, state.missionId)
        assertEquals(mission.title, state.title)
        assertEquals(mission.archetype, state.archetype)
        assertEquals(42.0, state.adherenceScore)
        assertEquals(0.5, state.hitRate)
        assertFalse(state.isSecondary)
    }

    // --- Batch G5: trigger prompt visibility ---

    @Test
    fun `trigger prompt hidden by default when hasExistingTrigger is omitted (pre-G5 call sites unaffected)`() {
        // Every test above this line predates hasExistingTrigger and doesn't pass it — this
        // confirms the default keeps them meaningful rather than silently always-true/false in
        // a way that would make their omission of the parameter accidental rather than correct.
        val state = computeMissionDetailState(
            goalMission = goalMission(),
            adherenceResult = result(hitRate = 0.5),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
        )
        assertFalse(state.showTriggerPrompt)
    }

    @Test
    fun `trigger prompt shown while Hypothesizing with no existing trigger and not dismissed`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(lifecycleStage = LifecycleStage.HYPOTHESIZING),
            adherenceResult = result(hitRate = 0.5),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
            hasExistingTrigger = false,
        )
        assertTrue(state.showTriggerPrompt)
    }

    @Test
    fun `trigger prompt hidden outside Hypothesizing regardless of trigger state`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(lifecycleStage = LifecycleStage.OBSERVING),
            adherenceResult = result(hitRate = 0.5),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
            hasExistingTrigger = false,
        )
        assertFalse(state.showTriggerPrompt)
    }

    @Test
    fun `trigger prompt hidden once a Trigger already exists, even while Hypothesizing`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(lifecycleStage = LifecycleStage.HYPOTHESIZING),
            adherenceResult = result(hitRate = 0.5),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
            hasExistingTrigger = true,
        )
        assertFalse(state.showTriggerPrompt)
    }

    @Test
    fun `trigger prompt hidden once dismissed, even while Hypothesizing with no existing trigger`() {
        val state = computeMissionDetailState(
            goalMission = goalMission(
                lifecycleStage = LifecycleStage.HYPOTHESIZING,
                triggerPromptDismissedAt = Instant.EPOCH,
            ),
            adherenceResult = result(hitRate = 0.5),
            logEntries = emptyList(),
            hitRateThreshold = HIT_RATE_THRESHOLD,
            hasExistingTrigger = false,
        )
        assertFalse(state.showTriggerPrompt)
    }
}
