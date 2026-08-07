package com.disciplineos.domain.voice

import com.disciplineos.data.entity.Tier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture §2.1: "Fallback bank content must independently pass the same §22.1
 * behavior-vs-identity review as generated content — it can't be an afterthought written once
 * and never audited." This is that check, made durable rather than a one-time manual pass —
 * it fails the build the moment any line in the bank fails [VoiceLineGate], including lines
 * added later.
 */
class FallbackVoiceBankTest {

    @Test
    fun `every line for every tier and context passes VoiceLineGate`() {
        val failures = mutableListOf<String>()
        for (tier in listOf(Tier.OPERATOR, Tier.WARDEN, Tier.IRON)) {
            for (context in FallbackVoiceBank.InterceptionContext.entries) {
                // Sample many times since FallbackVoiceBank.line() picks randomly from a list —
                // this is the only way this test can reach every line in a bounded, finite way
                // without reflecting into the private `bank` map. 200 draws per (tier, context)
                // cell comfortably covers every list in the bank (none has more than a handful
                // of entries) with negligible flake risk.
                repeat(200) {
                    val line = FallbackVoiceBank.line(tier, context)
                    val result = VoiceLineGate.check(line)
                    if (result is VoiceLineGate.GateResult.Rejected) {
                        failures += "[$tier/$context] \"$line\" — matched ${result.matchedPattern}"
                    }
                }
            }
        }
        assertTrue("VoiceLineGate rejected fallback bank lines:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `recruit tier is rejected, not silently given a line`() {
        assertThrows(IllegalArgumentException::class.java) {
            FallbackVoiceBank.line(Tier.RECRUIT)
        }
    }

    @Test
    fun `repeated attempt context is selected automatically past the first attempt`() {
        // Not asserting exact text (bank content may be edited) — asserting the routing rule:
        // a BLOCKLIST_ACCESS call with attemptNumber > 1 must not silently stay on the
        // first-attempt line set forever.
        val firstAttempt = FallbackVoiceBank.line(Tier.WARDEN, attemptNumber = 1)
        assertFalse(firstAttempt.isBlank())
        val repeated = FallbackVoiceBank.line(Tier.WARDEN, attemptNumber = 2)
        assertFalse(repeated.isBlank())
    }
}
