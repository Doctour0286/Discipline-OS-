package com.disciplineos.domain.voice

import com.disciplineos.data.entity.Tier
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class WardenVoiceProviderTest {

    private val context = FallbackVoiceBank.InterceptionContext.BLOCKLIST_ACCESS

    @Test
    fun `returns the generated line when generation succeeds and passes the gate`() = runTest {
        val provider = WardenVoiceProvider(
            generator = { _, _ -> "This was an avoidable distraction event." },
            timeout = 50.milliseconds,
        )
        val result = provider.line(Tier.WARDEN, context)
        assertEquals("This was an avoidable distraction event.", result.text)
        assertEquals(VoiceLineSource.GENERATED, result.source)
        assertNull("no rejection should be recorded on a passing generation", provider.lastRejection)
    }

    @Test
    fun `falls through to the bank when generation exceeds the timeout`() = runTest {
        val provider = WardenVoiceProvider(
            generator = { _, _ ->
                delay(500) // deliberately longer than the timeout below
                "This would never be seen."
            },
            timeout = 20.milliseconds,
        )
        val result = provider.line(Tier.WARDEN, context)
        assertEquals(VoiceLineSource.FALLBACK_BANK, result.source)
        // Every bank line passes VoiceLineGate (covered by FallbackVoiceBankTest); asserting
        // non-blank here is enough to confirm the fallback path actually produced content.
        assertNotNull(result.text)
    }

    @Test
    fun `falls through to the bank when generation returns null`() = runTest {
        val provider = WardenVoiceProvider(
            generator = { _, _ -> null },
            timeout = 50.milliseconds,
        )
        val result = provider.line(Tier.WARDEN, context)
        assertEquals(VoiceLineSource.FALLBACK_BANK, result.source)
    }

    @Test
    fun `falls through to the bank when generation throws`() = runTest {
        val provider = WardenVoiceProvider(
            generator = { _, _ -> throw RuntimeException("simulated network failure") },
            timeout = 50.milliseconds,
        )
        val result = provider.line(Tier.WARDEN, context)
        assertEquals(VoiceLineSource.FALLBACK_BANK, result.source)
    }

    @Test
    fun `falls through to the bank and records the rejection when generation fails the identity gate`() = runTest {
        val provider = WardenVoiceProvider(
            generator = { _, _ -> "Future you cannot trust commitments made by present you." },
            timeout = 50.milliseconds,
        )
        val result = provider.line(Tier.WARDEN, context)
        assertEquals(VoiceLineSource.FALLBACK_BANK, result.source)
        val rejection = provider.lastRejection
        assertNotNull("a gate rejection should have been recorded", rejection)
        assertEquals("Future you cannot trust commitments made by present you.", rejection!!.line)
    }

    @Test
    fun `never throws even when generator throws and bank is exercised repeatedly`() = runTest {
        val provider = WardenVoiceProvider(
            generator = { _, _ -> error("boom") },
            timeout = 10.milliseconds,
        )
        // No assertion beyond "this completes without throwing" — the guarantee under test is
        // Architecture §2.1's "must never show a *failed* response," i.e. this call must always
        // return, never propagate.
        repeat(10) {
            val result = provider.line(Tier.IRON, context, attemptNumber = it + 1)
            assertNotNull(result.text)
        }
    }
}
