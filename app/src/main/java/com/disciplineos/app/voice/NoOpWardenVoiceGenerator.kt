package com.disciplineos.app.voice

import com.disciplineos.data.entity.Tier
import com.disciplineos.domain.voice.FallbackVoiceBank
import com.disciplineos.domain.voice.WardenVoiceGenerator

/**
 * Architecture doc §5: "AI Accountability Engine hosting: cloud API call ... needs its own
 * service boundary so a provider outage degrades to fallback bank, not app failure." Building
 * that real cloud call (an actual HTTP client, a system prompt matching §22.1's tone rules, an
 * API key / auth story, the "needs its own service boundary" backend Architecture §5 describes)
 * is real, separate scope — not something to invent silently as a side effect of wiring up the
 * Accessibility Service.
 *
 * This is an explicit, honest placeholder: it always returns `null`, which — by
 * [com.disciplineos.domain.voice.WardenVoiceProvider]'s own contract — means every interception
 * during Phase 2 draws from [FallbackVoiceBank] rather than generated text. This is a real,
 * working, gate-reviewed voice experience today (not a stub that breaks the interception
 * screen); it's just always the "provider outage" branch of §5's design, on purpose, until a
 * real generator is built.
 *
 * **What "wire up the real thing" means later:** implement [WardenVoiceGenerator] with a real
 * HTTP call (Architecture §5's cloud service boundary — a dedicated backend endpoint, not a
 * client-embedded API key, given §2.1/§2.2's system-prompt-separation and prompt-injection
 * concerns), then swap the [com.disciplineos.app.di.AppContainer.wardenVoiceProvider] call site
 * to pass that implementation instead of this one. No other code needs to change —
 * [com.disciplineos.domain.voice.WardenVoiceProvider]'s timeout/gate/fallback sequencing
 * already treats "generator returns null" and "generator times out" identically.
 */
object NoOpWardenVoiceGenerator : WardenVoiceGenerator {
    override suspend fun generate(tier: Tier, context: FallbackVoiceBank.InterceptionContext): String? = null
}
