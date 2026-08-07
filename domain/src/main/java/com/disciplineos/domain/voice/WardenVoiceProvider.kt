package com.disciplineos.domain.voice

import com.disciplineos.data.entity.Tier
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Architecture doc §2.1: "full generation gives the flexibility the PRD's tone requirements
 * need ... but the fallback bank is non-negotiable given what a failed/blank response at that
 * moment would mean for trust. Recommend (c) — cloud call with a hard local fallback bank if
 * the call fails/times out."
 *
 * [WardenVoiceGenerator] is the thin abstraction over "however the cloud call actually
 * works" — this module (`:domain`) has no HTTP/network dependency and shouldn't gain one; the
 * real implementation (an Anthropic API call or similar) lives in the `:app` module, which is
 * where Architecture doc §5 already puts all cloud/network responsibility. Tests here use a
 * fake implementation, never a real network call — this module's tests must stay fast and
 * offline per the pattern already established by [com.disciplineos.domain.usecase]'s test
 * suite (Robolectric + in-memory Room, no real I/O).
 */
fun interface WardenVoiceGenerator {
    /**
     * @return a generated line, or null if generation genuinely failed/errored (as distinct
     *   from timing out, which [WardenVoiceProvider] handles itself via [withTimeoutOrNull] and
     *   never reaches this far as a null — see that class). Must not throw for ordinary
     *   failure modes (network error, non-2xx response, empty completion) — return null and let
     *   [WardenVoiceProvider] fall through to the bank; throwing would work today because
     *   `withTimeoutOrNull` catches everything, but relying on that is the wrong contract for a
     *   generator implementation to code against.
     */
    suspend fun generate(tier: Tier, context: FallbackVoiceBank.InterceptionContext): String?
}

/**
 * Orchestrates the §2.1 "(c)" strategy end to end: try [generator] under a hard timeout, run
 * whatever comes back through [VoiceLineGate] (Architecture §2.2 — the gate applies to
 * generated output too, not just the fallback bank), and fall through to
 * [FallbackVoiceBank] if generation times out, errors, returns null, or fails the gate.
 *
 * This class is the single call site the interception screen (`:app`, Phase 2) should use —
 * it should never call [WardenVoiceGenerator] or [FallbackVoiceBank] directly, so the
 * timeout/gate/fallback sequencing can't be bypassed or reordered at a UI call site.
 */
class WardenVoiceProvider(
    private val generator: WardenVoiceGenerator,
    /**
     * Onboarding doc §3.1 / Architecture §2.1: "~2s starting ceiling, tunable." Explicitly a
     * starting value, not a validated one — no PRD/Data Model doc citation gives a firmer
     * number, and Onboarding §3.1 itself says "tunable." Kept as a constructor default (not a
     * hardcoded literal inside [line]) specifically so it's the one obvious place to tune it,
     * consistent with this project's [HYPOTHESIS]-constant convention elsewhere
     * ([com.disciplineos.domain.policy.ConsequencePolicy] etc.) even though this particular
     * value already has spec language behind it and isn't literally invented.
     */
    private val timeout: Duration = 2.seconds,
) {
    /**
     * @return a line that has already passed [VoiceLineGate] — either generated-and-gated, or
     *   drawn from [FallbackVoiceBank] (pre-gated at bank-authoring time, verified by
     *   `FallbackVoiceBankTest`, not re-checked per call here since that would be redundant
     *   work on every interception with no behavior difference). Never returns null and never
     *   throws — this is the load-bearing guarantee Architecture §2.1 requires ("must never see
     *   a *failed* response at that exact moment").
     */
    suspend fun line(
        tier: Tier,
        context: FallbackVoiceBank.InterceptionContext,
        attemptNumber: Int = 1,
    ): VoiceLineResult {
        val generated = try {
            withTimeoutOrNull(timeout) { generator.generate(tier, context) }
        } catch (_: TimeoutCancellationException) {
            null // withTimeoutOrNull already turns this into null; explicit catch documents
            // the case rather than relying on the reader to know withTimeoutOrNull's contract.
        } catch (_: Exception) {
            // Any other generator exception (network error thrown instead of returned-null,
            // serialization failure, etc.) — Architecture §2.1's guarantee holds regardless of
            // *how* generation failed, so this is caught broadly and deliberately, not left to
            // propagate and crash the interception screen at the worst possible moment.
            null
        }

        if (generated != null) {
            when (val gateResult = VoiceLineGate.check(generated)) {
                is VoiceLineGate.GateResult.Passed ->
                    return VoiceLineResult(text = generated, source = VoiceLineSource.GENERATED)
                is VoiceLineGate.GateResult.Rejected ->
                    // Falls through to the bank below. Not silently dropped — callers that want
                    // to log this for the "logged for review" requirement (Architecture §2.2)
                    // can do so via [lastRejection], set just before falling through.
                    lastRejection = gateResult
            }
        }

        val fallback = FallbackVoiceBank.line(tier, context, attemptNumber)
        return VoiceLineResult(text = fallback, source = VoiceLineSource.FALLBACK_BANK)
    }

    /**
     * Set immediately before falling through to the bank due to a gate rejection — exposed so
     * a caller (e.g. an app-level logging hook) can persist rejected generations for the human
     * review Architecture §2.2 asks for, without [WardenVoiceProvider] itself taking on a
     * logging/analytics dependency it doesn't otherwise need. Not thread-safe by itself — this
     * class is expected to be used from a single coroutine per interception, matching how the
     * rest of the enforcement loop already treats one Mission/interception at a time.
     */
    var lastRejection: VoiceLineGate.GateResult.Rejected? = null
        private set
}

enum class VoiceLineSource { GENERATED, FALLBACK_BANK }

data class VoiceLineResult(val text: String, val source: VoiceLineSource)
