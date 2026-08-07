package com.disciplineos.app.enforcement

import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.entity.ViolationType
import com.disciplineos.domain.policy.InterceptionPolicy
import com.disciplineos.domain.usecase.RecordViolationUseCase
import com.disciplineos.domain.usecase.TierTransitionUseCase
import com.disciplineos.domain.voice.FallbackVoiceBank
import com.disciplineos.domain.voice.VoiceLineResult
import com.disciplineos.domain.voice.WardenVoiceProvider
import java.time.Instant
import java.util.UUID

/**
 * ROADMAP.md Phase 2 exit criteria, orchestration layer: wires PRD §14 (countdown mechanics),
 * §12.4.4 (Iron crisis exit), and Architecture §2 (Voice generation+fallback) into the single
 * place `MissionAccessibilityService`'s overlay calls into. Deliberately has no Android
 * `Context`/View dependency of its own — the overlay (a `View`/Activity concern) calls this,
 * this calls into `:domain` use-cases, and nothing here needs to know how the screen is drawn.
 * That split is what makes this class unit-testable with plain JUnit (see
 * `InterceptionControllerTest`), consistent with the rest of this project's stated preference
 * for keeping business logic out of Android framework classes wherever the boundary allows it.
 *
 * One instance of this class corresponds to one interception *event* (one blocklist-access
 * attempt) — `MissionAccessibilityService` constructs a fresh one per interception rather than
 * reusing a single long-lived instance, so `attemptNumber` state doesn't leak across
 * unrelated interceptions of different apps.
 */
class InterceptionController(
    private val mission: Mission,
    private val tier: Tier,
    private val attemptNumber: Int,
    private val recordViolationUseCase: RecordViolationUseCase,
    private val tierTransitionUseCase: TierTransitionUseCase,
    private val wardenVoiceProvider: WardenVoiceProvider,
) {
    /**
     * Resolves the Voice line to display, per Architecture §2.1's timeout+fallback sequencing
     * and PRD §14's tier-dependent content rule (Recruit shows plain informational content,
     * never a Voice line at all — see [InterceptionPolicy.usesWardenVoice]).
     *
     * @return null for Recruit tier (informational screen, no Voice content — caller renders
     *   static copy instead) or a resolved [VoiceLineResult] for every other tier.
     */
    suspend fun resolveVoiceLine(): VoiceLineResult? {
        if (!InterceptionPolicy.usesWardenVoice(tier)) return null
        return wardenVoiceProvider.line(
            tier = tier,
            context = FallbackVoiceBank.InterceptionContext.BLOCKLIST_ACCESS,
            attemptNumber = attemptNumber,
        )
    }

    /**
     * PRD §14: countdown length and early-dismissal rule for this interception, sourced from
     * the shared [InterceptionPolicy] rather than re-derived here.
     */
    fun countdownSpec(): CountdownSpec = CountdownSpec(
        duration = InterceptionPolicy.countdownDuration(tier),
        allowsEarlyDismissal = InterceptionPolicy.allowsEarlyDismissal(tier),
    )

    /** Which stability-control affordance (§12.4.2 vs §12.4.4) this screen should render. */
    fun stabilityControl(): InterceptionPolicy.StabilityControl =
        InterceptionPolicy.interceptionScreenStabilityControl(tier)

    /**
     * "Return to Mission" — the user backs out of the blocklisted app and resumes the Mission.
     * No Violation is recorded; this is the non-consequence path. Nothing in PRD §14 or Data
     * Model doc §2.3 attaches any record to a successfully-resisted temptation at the
     * interception-screen level — §15's Temptation Tracking ("Instagram attempt → Cancelled →
     * Resisted") is a distinct, lighter-weight signal than a Violation and is out of Phase 2
     * scope (no `TemptationEvent` entity exists yet in `:data`) — flagged here rather than
     * silently folded into this method, since inventing that entity as a side effect of
     * building the interception screen would be exactly the kind of unscoped addition
     * ROADMAP.md's conventions ask to avoid.
     */
    fun returnToMission() {
        // Intentionally a no-op beyond what the overlay itself needs to do (dismiss, return
        // focus to the allowed app) — see kdoc above re: Temptation Tracking being out of scope.
    }

    /**
     * "Break Commitment" — the user confirms ending/violating the Mission. Records a Violation
     * via [RecordViolationUseCase], which applies Debt/Reputation per the existing shared-cause
     * guard and dispute-freeze rules (unchanged, Phase 1 logic — this method does not
     * reimplement any of that, only calls it with the right [Violation]).
     *
     * @param reason required and enforced non-blank when
     *   [InterceptionPolicy.requiresBreakCommitmentReason] is true for [tier] (Iron only, per
     *   PRD §14) — null/blank at any other tier, since §14 states the mandatory reason
     *   requirement "applies to confirming Break Commitment" specifically at Iron and nothing
     *   in the PRD asks for optional reason capture at lower tiers.
     */
    suspend fun breakCommitment(reason: String?, now: Instant = Instant.now()): RecordViolationUseCase.Result {
        if (InterceptionPolicy.requiresBreakCommitmentReason(tier)) {
            require(!reason.isNullOrBlank()) {
                "Break Commitment at Iron requires a non-blank reason (PRD §14 mandatory " +
                    "reason entry) — this should have been enforced by the overlay UI before " +
                    "this call, this is a defensive re-check, not the primary gate."
            }
        }
        val violation = Violation(
            id = UUID.randomUUID(),
            missionId = mission.id,
            detectedAt = now,
            type = ViolationType.BLOCKLIST_ACCESS,
        )
        return recordViolationUseCase.execute(violation)
    }

    /**
     * PRD §12.4.4 Iron-Tier Crisis Exit — only valid when [tier] is [Tier.IRON] (matching
     * `TierTransitionUseCase.ironCrisisExit`'s own hard `require`); the overlay must only ever
     * render this affordance when [stabilityControl] returned [InterceptionPolicy.StabilityControl.IRON_CRISIS_EXIT],
     * so reaching this method for a non-Iron tier is a UI wiring bug, not a case to handle
     * gracefully here — consistent with how `TierTransitionUseCase.ironCrisisExit` itself
     * treats a non-Iron caller as a hard failure, not a silent no-op.
     */
    suspend fun ironCrisisExit(userId: UUID, now: Instant = Instant.now()) =
        tierTransitionUseCase.ironCrisisExit(userId = userId, missionId = mission.id, now = now)

    data class CountdownSpec(
        val duration: kotlin.time.Duration,
        val allowsEarlyDismissal: Boolean,
    )
}
