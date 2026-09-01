package com.disciplineos.app.home

import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Covers [computeHomeState] directly — a plain pure function, deliberately pulled out of
 * [HomeFragment] so it's testable without a Robolectric/Fragment host, same reasoning this
 * package's other pure-function tests already give (e.g. `MetricsTest` in `:data` for
 * [com.disciplineos.data.metrics.ironCalibrationSatisfied] itself, which this function reuses
 * rather than re-deriving).
 */
class HomeFragmentTest {

    private fun userAt(tier: Tier?, tierSelectedAt: Instant?, calibrationWindowDays: Int = 10): User =
        User(
            id = UUID.randomUUID(),
            createdAt = Instant.EPOCH,
            currentTier = tier,
            tierSelectedAt = tierSelectedAt,
            tierActivationAt = tierSelectedAt,
            onboardingConsentVersion = "v1",
            calibrationWindowDays = calibrationWindowDays,
        )

    @Test
    fun `null user hides the Iron card entirely`() {
        val state = computeHomeState(user = null, now = Instant.now())
        assertFalse(state.showIronCard)
        assertEquals(null, state.currentTier)
    }

    @Test
    fun `user already at Iron hides the Iron card`() {
        val now = Instant.now()
        val user = userAt(Tier.IRON, tierSelectedAt = now.minus(30, ChronoUnit.DAYS))
        val state = computeHomeState(user, now)
        assertFalse(state.showIronCard)
        assertEquals(Tier.IRON, state.currentTier)
    }

    @Test
    fun `user with no tierSelectedAt hides the Iron card`() {
        val user = userAt(Tier.RECRUIT, tierSelectedAt = null)
        val state = computeHomeState(user, Instant.now())
        assertFalse(state.showIronCard)
    }

    @Test
    fun `within the calibration window shows the card as not yet eligible with days remaining`() {
        val now = Instant.now()
        val user = userAt(Tier.WARDEN, tierSelectedAt = now.minus(3, ChronoUnit.DAYS), calibrationWindowDays = 10)
        val state = computeHomeState(user, now)
        assertTrue(state.showIronCard)
        assertFalse(state.ironEligibleNow)
        assertEquals(7L, state.daysRemaining)
    }

    @Test
    fun `once the calibration window has elapsed the card shows eligible now`() {
        val now = Instant.now()
        val user = userAt(Tier.WARDEN, tierSelectedAt = now.minus(11, ChronoUnit.DAYS), calibrationWindowDays = 10)
        val state = computeHomeState(user, now)
        assertTrue(state.showIronCard)
        assertTrue(state.ironEligibleNow)
        assertEquals(0L, state.daysRemaining)
    }

    @Test
    fun `exactly at the window boundary is eligible, matching ironCalibrationSatisfied's own inclusive check`() {
        val now = Instant.now()
        val user = userAt(Tier.OPERATOR, tierSelectedAt = now.minus(10, ChronoUnit.DAYS), calibrationWindowDays = 10)
        val state = computeHomeState(user, now)
        assertTrue(state.ironEligibleNow)
    }
}
