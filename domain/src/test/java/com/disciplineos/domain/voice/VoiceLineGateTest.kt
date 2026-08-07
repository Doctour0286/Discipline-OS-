package com.disciplineos.domain.voice

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture §2.2's own worked example is the load-bearing case this test suite is built
 * around: the PRD §22.1 line rejected in v3.6 ("Future you cannot trust commitments made by
 * present you") must fail the gate, and its v3.6 replacement ("Present-you committed to this.
 * Present-you didn't follow through.") must pass — if this file only tested synthetic examples
 * and missed the actual documented case, it would be testing the wrong thing.
 */
class VoiceLineGateTest {

    @Test
    fun `rejects the v3_6-removed identity-focused example line`() {
        val result = VoiceLineGate.check("Future you cannot trust commitments made by present you.")
        assertTrue(result is VoiceLineGate.GateResult.Rejected)
    }

    @Test
    fun `passes the v3_6 replacement behavior-focused line`() {
        val result = VoiceLineGate.check("Present-you committed to this. Present-you didn't follow through.")
        assertTrue(result is VoiceLineGate.GateResult.Passed)
    }

    @Test
    fun `passes all four PRD section 22_1 worked examples`() {
        val examples = listOf(
            "You abandoned the Mission after 23 minutes.",
            "This was an avoidable distraction event.",
            "Your behavior reduced your Reliability Index.",
            "Present-you committed to this. Present-you didn't follow through.",
        )
        examples.forEach { line ->
            val result = VoiceLineGate.check(line)
            assertTrue("Expected pass for: $line", result is VoiceLineGate.GateResult.Passed)
        }
    }

    @Test
    fun `rejects a direct you-are identity claim`() {
        val result = VoiceLineGate.check("You are a person who can't follow through.")
        assertTrue(result is VoiceLineGate.GateResult.Rejected)
    }

    @Test
    fun `rejects a you're-a contraction identity claim`() {
        val result = VoiceLineGate.check("You're a failure at this.")
        assertTrue(result is VoiceLineGate.GateResult.Rejected)
    }

    @Test
    fun `passes a plain factual statement with no identity claim`() {
        val result = VoiceLineGate.check("This app is outside the Mission's allowlist right now.")
        assertTrue(result is VoiceLineGate.GateResult.Passed)
    }
}
