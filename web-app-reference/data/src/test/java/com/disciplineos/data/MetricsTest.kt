package com.disciplineos.data

import com.disciplineos.data.entity.LifecycleStage
import com.disciplineos.data.entity.Milestone
import com.disciplineos.data.entity.MissionLogEntry
import com.disciplineos.data.entity.TargetDirection
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.metrics.clampToDebtCeiling
import com.disciplineos.data.metrics.debtCeiling
import com.disciplineos.data.metrics.debtQuartileMarkers
import com.disciplineos.data.metrics.hypothesizingStageSatisfied
import com.disciplineos.data.metrics.ironCalibrationSatisfied
import com.disciplineos.data.metrics.milestoneAchievementSatisfied
import com.disciplineos.data.metrics.reliabilityIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MetricsTest {

    @Test
    fun `reliability index with no data returns neutral 1_0, not zero`() {
        // A brand new user shouldn't read as 0% reliable before they've done anything.
        assertEquals(1.0, reliabilityIndex(0, 0), 0.0001)
    }

    @Test
    fun `reliability index is completed over total`() {
        assertEquals(0.8, reliabilityIndex(completedMissions = 8, violatedMissions = 2), 0.0001)
    }

    @Test
    fun `debt ceiling scales with average mission duration and window`() {
        assertEquals(14.0 * 45, debtCeiling(avgMissionDurationMin = 45.0), 0.0001)
        assertEquals(7.0 * 45, debtCeiling(avgMissionDurationMin = 45.0, windowDays = 7), 0.0001)
    }

    @Test
    fun `debt clamps to zero floor and ceiling`() {
        assertEquals(0.0, clampToDebtCeiling(rawDebt = -50.0, ceiling = 630.0), 0.0001)
        assertEquals(630.0, clampToDebtCeiling(rawDebt = 900.0, ceiling = 630.0), 0.0001)
        assertEquals(300.0, clampToDebtCeiling(rawDebt = 300.0, ceiling = 630.0), 0.0001)
    }

    @Test
    fun `quartile markers are 25 50 75 percent of ceiling`() {
        assertEquals(listOf(100.0, 200.0, 300.0), debtQuartileMarkers(ceiling = 400.0))
    }

    @Test
    fun `iron calibration gate blocks activation before window elapses`() {
        val selectedAt = 0L
        val tenDaysMillis = 10 * 24 * 60 * 60 * 1000L

        assertFalse(
            ironCalibrationSatisfied(
                tier = Tier.IRON,
                tierSelectedAtEpochMilli = selectedAt,
                calibrationWindowDays = 10,
                nowEpochMilli = selectedAt + tenDaysMillis - 1,
            ),
        )
        assertTrue(
            ironCalibrationSatisfied(
                tier = Tier.IRON,
                tierSelectedAtEpochMilli = selectedAt,
                calibrationWindowDays = 10,
                nowEpochMilli = selectedAt + tenDaysMillis,
            ),
        )
    }

    @Test
    fun `iron calibration gate is always satisfied for non-Iron tiers`() {
        assertTrue(
            ironCalibrationSatisfied(
                tier = Tier.RECRUIT,
                tierSelectedAtEpochMilli = 0L,
                calibrationWindowDays = 10,
                nowEpochMilli = 0L, // no time elapsed at all
            ),
        )
    }

    @Test
    fun `hypothesizing stage not satisfied below the threshold`() {
        assertFalse(
            hypothesizingStageSatisfied(
                currentStage = LifecycleStage.OBSERVING,
                hasAnyBehaviorAttached = false,
                outcomeLogCount = 2,
                threshold = 3,
            ),
        )
    }

    @Test
    fun `hypothesizing stage satisfied once outcome log count reaches the threshold`() {
        assertTrue(
            hypothesizingStageSatisfied(
                currentStage = LifecycleStage.OBSERVING,
                hasAnyBehaviorAttached = false,
                outcomeLogCount = 3,
                threshold = 3,
            ),
        )
        // Past the threshold, not just at it, should also satisfy.
        assertTrue(
            hypothesizingStageSatisfied(
                currentStage = LifecycleStage.OBSERVING,
                hasAnyBehaviorAttached = false,
                outcomeLogCount = 5,
                threshold = 3,
            ),
        )
    }

    @Test
    fun `hypothesizing stage never satisfied once a behavior is already attached`() {
        // Even with plenty of outcome logs, a mission that already has a behavior attached
        // has nothing left to transition into Hypothesizing for.
        assertFalse(
            hypothesizingStageSatisfied(
                currentStage = LifecycleStage.OBSERVING,
                hasAnyBehaviorAttached = true,
                outcomeLogCount = 10,
                threshold = 3,
            ),
        )
    }

    @Test
    fun `hypothesizing stage never satisfied outside Observing`() {
        // Already Hypothesizing, Enforcing, or Reviewing — nothing to transition from.
        assertFalse(
            hypothesizingStageSatisfied(
                currentStage = LifecycleStage.HYPOTHESIZING,
                hasAnyBehaviorAttached = false,
                outcomeLogCount = 10,
                threshold = 3,
            ),
        )
        assertFalse(
            hypothesizingStageSatisfied(
                currentStage = LifecycleStage.ENFORCING,
                hasAnyBehaviorAttached = false,
                outcomeLogCount = 10,
                threshold = 3,
            ),
        )
        assertFalse(
            hypothesizingStageSatisfied(
                currentStage = LifecycleStage.REVIEWING,
                hasAnyBehaviorAttached = false,
                outcomeLogCount = 10,
                threshold = 3,
            ),
        )
    }

    // --- Milestone achievement (Batch G6) ---

    private fun milestone(
        targetValue: Double? = 70.0,
        targetDate: Instant? = null,
        achievedAt: Instant? = null,
    ): Milestone = Milestone(
        id = UUID.randomUUID(),
        missionId = UUID.randomUUID(),
        label = "Test checkpoint",
        targetValue = targetValue,
        targetDate = targetDate,
        achievedAt = achievedAt,
    )

    private fun logEntry(numericValue: Double?): MissionLogEntry = MissionLogEntry(
        id = UUID.randomUUID(),
        missionId = UUID.randomUUID(),
        createdAt = Instant.EPOCH,
        note = null,
        numericValue = numericValue,
    )

    @Test
    fun `milestone achievement satisfied when a DECREASE log entry crosses at or below target`() {
        assertTrue(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 70.0),
                targetDirection = TargetDirection.DECREASE,
                logEntries = listOf(logEntry(85.0), logEntry(69.0)),
            ),
        )
        // Exactly at target also counts as crossed, not just strictly past it.
        assertTrue(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 70.0),
                targetDirection = TargetDirection.DECREASE,
                logEntries = listOf(logEntry(70.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement not satisfied when no DECREASE log entry has crossed target yet`() {
        assertFalse(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 70.0),
                targetDirection = TargetDirection.DECREASE,
                logEntries = listOf(logEntry(85.0), logEntry(78.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement satisfied when an INCREASE log entry crosses at or above target`() {
        assertTrue(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 10000.0),
                targetDirection = TargetDirection.INCREASE,
                logEntries = listOf(logEntry(8000.0), logEntry(10500.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement not satisfied when no INCREASE log entry has crossed target yet`() {
        assertFalse(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 10000.0),
                targetDirection = TargetDirection.INCREASE,
                logEntries = listOf(logEntry(8000.0), logEntry(9500.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement never satisfied for MAINTAIN direction`() {
        // A maintain-type goal has no directional "crossing" for an intermediate checkpoint.
        assertFalse(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 70.0),
                targetDirection = TargetDirection.MAINTAIN,
                logEntries = listOf(logEntry(70.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement never re-evaluated once already achieved`() {
        assertFalse(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 70.0, achievedAt = Instant.EPOCH),
                targetDirection = TargetDirection.DECREASE,
                logEntries = listOf(logEntry(60.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement not satisfied with no numeric target`() {
        // Ordinal-only milestone (targetValue null) — no threshold to cross, always null-read.
        assertFalse(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = null),
                targetDirection = TargetDirection.DECREASE,
                logEntries = listOf(logEntry(1.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement not satisfied with no target direction`() {
        assertFalse(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 70.0),
                targetDirection = null,
                logEntries = listOf(logEntry(60.0)),
            ),
        )
    }

    @Test
    fun `milestone achievement not satisfied with no numeric log entries`() {
        assertFalse(
            milestoneAchievementSatisfied(
                milestone = milestone(targetValue = 70.0),
                targetDirection = TargetDirection.DECREASE,
                logEntries = listOf(logEntry(null), logEntry(null)),
            ),
        )
    }
}
