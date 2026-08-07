package com.disciplineos.domain.policy

import com.disciplineos.data.entity.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class InterceptionPolicyTest {

    @Test
    fun `countdown durations match PRD section 14 exactly`() {
        assertEquals(5.seconds, InterceptionPolicy.countdownDuration(Tier.RECRUIT))
        assertEquals(10.seconds, InterceptionPolicy.countdownDuration(Tier.OPERATOR))
        assertEquals(10.seconds, InterceptionPolicy.countdownDuration(Tier.WARDEN))
        assertEquals(15.seconds, InterceptionPolicy.countdownDuration(Tier.IRON))
    }

    @Test
    fun `early dismissal allowed only at recruit and operator`() {
        assertTrue(InterceptionPolicy.allowsEarlyDismissal(Tier.RECRUIT))
        assertTrue(InterceptionPolicy.allowsEarlyDismissal(Tier.OPERATOR))
        assertFalse(InterceptionPolicy.allowsEarlyDismissal(Tier.WARDEN))
        assertFalse(InterceptionPolicy.allowsEarlyDismissal(Tier.IRON))
    }

    @Test
    fun `mandatory break-commitment reason entry applies only at iron`() {
        assertFalse(InterceptionPolicy.requiresBreakCommitmentReason(Tier.RECRUIT))
        assertFalse(InterceptionPolicy.requiresBreakCommitmentReason(Tier.OPERATOR))
        assertFalse(InterceptionPolicy.requiresBreakCommitmentReason(Tier.WARDEN))
        assertTrue(InterceptionPolicy.requiresBreakCommitmentReason(Tier.IRON))
    }

    @Test
    fun `warden voice used at operator warden and iron but not recruit`() {
        assertFalse(InterceptionPolicy.usesWardenVoice(Tier.RECRUIT))
        assertTrue(InterceptionPolicy.usesWardenVoice(Tier.OPERATOR))
        assertTrue(InterceptionPolicy.usesWardenVoice(Tier.WARDEN))
        assertTrue(InterceptionPolicy.usesWardenVoice(Tier.IRON))
    }

    @Test
    fun `iron interception screen surfaces the iron crisis exit, not the general stability control`() {
        assertEquals(
            InterceptionPolicy.StabilityControl.IRON_CRISIS_EXIT,
            InterceptionPolicy.interceptionScreenStabilityControl(Tier.IRON),
        )
        assertEquals(
            InterceptionPolicy.StabilityControl.EXPLICIT_DOWNGRADE,
            InterceptionPolicy.interceptionScreenStabilityControl(Tier.WARDEN),
        )
        assertEquals(
            InterceptionPolicy.StabilityControl.EXPLICIT_DOWNGRADE,
            InterceptionPolicy.interceptionScreenStabilityControl(Tier.RECRUIT),
        )
        assertEquals(
            InterceptionPolicy.StabilityControl.EXPLICIT_DOWNGRADE,
            InterceptionPolicy.interceptionScreenStabilityControl(Tier.OPERATOR),
        )
    }
}
