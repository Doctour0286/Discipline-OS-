package com.disciplineos.data

import com.disciplineos.data.entity.Tier
import com.disciplineos.data.metrics.clampToDebtCeiling
import com.disciplineos.data.metrics.debtCeiling
import com.disciplineos.data.metrics.debtQuartileMarkers
import com.disciplineos.data.metrics.ironCalibrationSatisfied
import com.disciplineos.data.metrics.reliabilityIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforcer-stripped MetricsTest. Tests for hypothesizingStageSatisfied and
 * milestoneAchievementSatisfied moved to web-app-reference/ with the functions themselves.
 */
class MetricsTest {

    @Test
    fun `reliability index with no data returns neutral 1_0, not zero`() {
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
                nowEpochMilli = 0L,
            ),
        )
    }
}
