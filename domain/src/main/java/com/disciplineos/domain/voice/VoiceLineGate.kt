package com.disciplineos.domain.voice

/**
 * Architecture doc §2.2 (hard requirement, not a suggestion): "The behavior-vs-identity test
 * from §22.1 ... should be encoded as an actual automated check on generated output before it
 * ever reaches interception screen or Tribunal — not just prompt guidance hoping the model
 * complies. Recommend a lightweight secondary classifier or rule-based filter (e.g., flagging
 * second-person identity statements like 'you are/you're a ___ person') as a pre-display gate,
 * logged for review."
 *
 * PRD §22.1's own worked example of the distinction (post-v3.6 correction):
 * - Identity-focused (rejected): "Future you cannot trust commitments made by present you."
 * - Behavior-focused (kept): "Present-you committed to this. Present-you didn't follow through."
 *
 * This is a rule-based filter, not a classifier — matching Architecture §2.2's "lightweight...
 * or rule-based" framing and this project's stated preference (ROADMAP.md's own conventions,
 * `ArchitectureBoundaryTest`'s kdoc) for logic simple enough to audit by reading it over
 * anything cleverer. It is deliberately conservative: it flags the *shape* most identity claims
 * take ("you are/you're a[n] ___") rather than trying to be a general sentiment model. A
 * rule-based filter at this scope will have false negatives (identity-focused lines that don't
 * match the pattern) and occasional false positives (a behavior-focused line that happens to
 * contain the pattern incidentally) — both are logged (`GateResult.matchedPattern`) so a human
 * reviewer can see exactly what tripped it, per Architecture §2.2's "logged for review."
 *
 * This gate applies to BOTH generated Warden Voice output and the static
 * [FallbackVoiceBank] content — Architecture §2.1 states the fallback bank "must independently
 * pass the same §22.1 behavior-vs-identity review as generated content — it can't be an
 * afterthought written once and never audited." [FallbackVoiceGateSelfTest]-equivalent coverage
 * for the fallback bank lives in the test file for that bank, not here — this file only
 * defines the check itself.
 */
object VoiceLineGate {

    /**
     * Case-insensitive patterns for the "you are / you're a(n) ___" identity-claim shape
     * Architecture §2.2 names explicitly, plus its close variants. Kept as a short, readable
     * list rather than a single dense regex, per this module's stated preference for auditable
     * simplicity over cleverness — a reviewer should be able to read this list top to bottom
     * and understand exactly what it catches without parsing regex alternation.
     *
     * Deliberately does NOT try to catch every possible identity-focused phrasing (e.g.
     * "someone who can't be trusted" has no "you are" and would slip through) — Architecture
     * §2.2 calls this "a lightweight secondary classifier," not a complete one, and a filter
     * that reaches for completeness here risks false-positiving on ordinary behavior-focused
     * sentences instead. The gate is one layer; §22.1's human copy-review pass (Onboarding doc
     * §4, "systematic, not spot-checked once") is the other, and this gate does not replace it.
     */
    private val identityPatterns: List<Regex> = listOf(
        Regex("""\byou'?re\s+(a|an|the)\s+\w+""", RegexOption.IGNORE_CASE),
        Regex("""\byou\s+are\s+(a|an|the)\s+\w+""", RegexOption.IGNORE_CASE),
        // "future you" / "present you" framed as an identity noun-phrase subject rather than
        // an actor of a described action — the exact shape of the rejected v3.6 example line.
        Regex("""\b(future|present)\s+you\s+(is|are|can'?t|cannot|will never)\b""", RegexOption.IGNORE_CASE),
        Regex("""\byou'?ve\s+always\s+been\b""", RegexOption.IGNORE_CASE),
        Regex("""\byou'?re\s+just\s+not\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * @return a [GateResult] describing whether [line] passed. Never throws — a gate that can
     *   itself fail/crash at the exact moment §14's interception screen needs a line to display
     *   would recreate the "blank/error state" risk Architecture §2.1 already treats as
     *   unacceptable at this moment, just moved one layer over. Callers that get a
     *   [GateResult.Rejected] must fall through to a different, already-vetted line (see
     *   [FallbackVoiceBank]) rather than display the rejected line or show nothing.
     */
    fun check(line: String): GateResult {
        val match = identityPatterns.firstOrNull { it.containsMatchIn(line) }
        return if (match == null) {
            GateResult.Passed
        } else {
            GateResult.Rejected(line = line, matchedPattern = match.pattern)
        }
    }

    sealed class GateResult {
        data object Passed : GateResult()
        data class Rejected(val line: String, val matchedPattern: String) : GateResult()
    }
}
