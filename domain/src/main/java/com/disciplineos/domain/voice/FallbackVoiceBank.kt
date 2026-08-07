package com.disciplineos.domain.voice

import com.disciplineos.data.entity.Tier

/**
 * Architecture doc §2.1: the Warden-Voice interception path is "(c) cloud call with a hard
 * local fallback bank if the call fails/times out ... the fallback bank is non-negotiable
 * given what a failed/blank response at that moment would mean for trust." This is that bank.
 *
 * Content requirements this file exists to satisfy:
 * - Architecture §2.1: "Fallback bank content must independently pass the same §22.1
 *   behavior-vs-identity review as generated content — it can't be an afterthought written
 *   once and never audited." Every line below is checked against [VoiceLineGate] by
 *   `FallbackVoiceBankTest` (fails the build if any line is rejected) — that's the "review,"
 *   made durable rather than a one-time manual pass.
 * - PRD §22.1: Warden Voice tone is "Direct, Aggressive, Unapologetic, Precise, Evidence-based.
 *   Never abusive. Never degrading," behavior-focused not identity-focused. Used at Warden and
 *   Iron; per §22.1, "and for standard violation feedback at Operator" — so Operator draws from
 *   this bank too, not just Warden/Iron, though Architecture §2.1 frames the *latency-critical*
 *   path (needing a fallback at all) as specifically the in-the-moment interception moment,
 *   which applies at Operator/Warden/Iron alike per §14's countdown mechanics existing at all
 *   three of those tiers.
 * - PRD §14: Recruit tier's interception content is "informational, lower-pressure," not Warden
 *   Voice at all — Recruit is deliberately excluded from this bank; its interception content is
 *   plain informational copy, not an AI Voice line (see the `app` module's interception screen).
 *
 * Recalibration Voice (§22.2) is a separate, cloud-tolerant surface per Architecture §2.1
 * ("Tribunal is a reflective, scheduled interaction, not a real-time interception moment") and
 * does not need a latency fallback bank the way Warden Voice does — out of scope for this file,
 * Phase 3 (Tribunal UI) territory.
 *
 * [HYPOTHESIS]: the specific wording of every line below beyond the four PRD §22.1 worked
 * examples is this codebase's own composition, following the stated tone/behavior-focus rules
 * as closely as it can — none of it is quoted or lightly-reworded from the PRD (the four PRD
 * example lines are reproduced verbatim below since they're the spec's own canonical examples,
 * not third-party content), but it has not had the human copy-review pass Onboarding doc §4
 * calls for ("a literal checklist pass over every string in this doc before first build").
 * [VoiceLineGate] passing is necessary, not sufficient, for that review — flag this bank for
 * that pass before treating it as ship-ready, not just CI-green.
 */
object FallbackVoiceBank {

    /**
     * A single contextual reason a blocklisted-app access was intercepted — used to select a
     * more specific line where one exists, falling back to [generic] otherwise. Kept small and
     * closed (not open-ended free text) since every line keyed off it needs to have passed
     * [VoiceLineGate] ahead of time; an arbitrary reason string could not offer that guarantee.
     */
    enum class InterceptionContext {
        BLOCKLIST_ACCESS,
        EARLY_EXIT_ATTEMPT,
        REPEATED_ATTEMPT, // same blocklisted app, same Mission, not the first attempt
    }

    /**
     * @param tier caller must not pass [Tier.RECRUIT] — Recruit's interception screen doesn't
     *   use Warden Voice at all per PRD §14/§22.1; enforced with `require` rather than silently
     *   returning a line, since a caller reaching this method for Recruit is a wiring bug, not
     *   a case this bank should paper over.
     * @param context selects the most specific available line; falls back to a generic line
     *   for that tier if no context-specific one exists.
     * @param attemptNumber 1-indexed count of blocklist-access attempts against this exact
     *   Mission so far, used only to route to [InterceptionContext.REPEATED_ATTEMPT] content
     *   when it's genuinely a repeat — callers may also pass this context directly.
     */
    fun line(
        tier: Tier,
        context: InterceptionContext = InterceptionContext.BLOCKLIST_ACCESS,
        attemptNumber: Int = 1,
    ): String {
        require(tier != Tier.RECRUIT) {
            "FallbackVoiceBank.line() called for RECRUIT — Recruit tier does not use Warden " +
                "Voice at all (PRD §14, §22.1); this is a call-site bug, not a missing line."
        }
        val effectiveContext = if (attemptNumber > 1 && context == InterceptionContext.BLOCKLIST_ACCESS) {
            InterceptionContext.REPEATED_ATTEMPT
        } else {
            context
        }
        val byTier = bank.getValue(tier)
        return byTier[effectiveContext]?.random() ?: byTier.getValue(InterceptionContext.BLOCKLIST_ACCESS).first()
    }

    // Every list below is non-empty and every line is covered by FallbackVoiceBankTest's
    // exhaustive gate-check (it iterates `bank` directly, not a hand-picked subset).
    private val bank: Map<Tier, Map<InterceptionContext, List<String>>> = mapOf(
        Tier.OPERATOR to mapOf(
            InterceptionContext.BLOCKLIST_ACCESS to listOf(
                "This app is outside the Mission's allowlist right now.",
                "You opened a blocked app during an active Mission.",
                "This is an avoidable distraction event.",
            ),
            InterceptionContext.EARLY_EXIT_ATTEMPT to listOf(
                "You're attempting to end this Mission before its scheduled time.",
                "The Mission isn't finished. This would end it early.",
            ),
            InterceptionContext.REPEATED_ATTEMPT to listOf(
                "This is the same blocked app you tried to open earlier in this Mission.",
                "You've come back to this app more than once during this Mission.",
            ),
        ),
        Tier.WARDEN to mapOf(
            InterceptionContext.BLOCKLIST_ACCESS to listOf(
                // PRD §22.1 worked examples, reproduced verbatim — the spec's own canonical
                // behavior-focused lines, not third-party or generated text.
                "You abandoned the Mission after 23 minutes.",
                "This was an avoidable distraction event.",
                "Your behavior reduced your Reliability Index.",
                "Present-you committed to this. Present-you didn't follow through.",
                "You opened a blocked app while this Mission was active.",
            ),
            InterceptionContext.EARLY_EXIT_ATTEMPT to listOf(
                "You're trying to break this commitment before the scheduled end time.",
                "This Mission has time remaining. Breaking it now counts as a violation.",
            ),
            InterceptionContext.REPEATED_ATTEMPT to listOf(
                "You've opened this same blocked app more than once this Mission.",
                "This is a repeated attempt at the same blocked app.",
            ),
        ),
        Tier.IRON to mapOf(
            InterceptionContext.BLOCKLIST_ACCESS to listOf(
                "You abandoned the Mission after 23 minutes.",
                "This was an avoidable distraction event.",
                "Your behavior reduced your Reliability Index.",
                "Present-you committed to this. Present-you didn't follow through.",
                "You opened a blocked app while this Mission was active, at Iron tier.",
            ),
            InterceptionContext.EARLY_EXIT_ATTEMPT to listOf(
                "Breaking this commitment now, at Iron, requires a reason before it's recorded.",
                "You're ending this Mission early. Iron requires you to state why.",
            ),
            InterceptionContext.REPEATED_ATTEMPT to listOf(
                "You've returned to this blocked app more than once during this Mission.",
                "This is a repeated attempt, at Iron tier, at the same blocked app.",
            ),
        ),
    )
}
