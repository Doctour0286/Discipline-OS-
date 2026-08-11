package com.disciplineos.data.metrics

import com.disciplineos.data.entity.LifecycleStage
import com.disciplineos.data.entity.Tier
import kotlin.math.max
import kotlin.math.min

/**
 * Pure formula layer — Data Model & Schema doc §3. Deliberately has no DB/DAO dependency
 * so these can be unit tested against fixed inputs without a Room instance, and so the
 * "measurement never enforces" boundary (§7) has a single obvious place to check: none of
 * these functions accept an UnsupervisedSignal-derived value as an input that flows into a
 * Tier or LedgerEntry decision.
 */

/** Data Model §3.2. Thresholds (85%/10 days) are Hypothesis per PRD §42 — not encoded here. */
fun reliabilityIndex(completedMissions: Int, violatedMissions: Int): Double {
    val total = completedMissions + violatedMissions
    if (total == 0) return 1.0 // no data yet — treat as neutral, not a failure signal
    return completedMissions.toDouble() / total
}

/**
 * Data Model §3.4. Ceiling value itself is [HYPOTHESIS] — flagged §42 for possible
 * per-tier scaling, not yet implemented (flat 14-day window per current spec).
 */
fun debtCeiling(avgMissionDurationMin: Double, windowDays: Int = 14): Double =
    windowDays * avgMissionDurationMin

/**
 * Data Model §3.4: Debt = clamp(Debt, 0, DebtCeiling). This clamps a *proposed* new value —
 * callers are responsible for computing the raw running total from LedgerDao.currentValue()
 * and passing it through this before it's treated as "the" debt for display/Recovery Mode
 * triggers. The ledger itself is never clamped (it's an append-only log of deltas); only the
 * derived reading is.
 */
fun clampToDebtCeiling(rawDebt: Double, ceiling: Double): Double =
    max(0.0, min(rawDebt, ceiling))

/**
 * §27.1.1 (v3.6) — display-only quartile markers. No functional effect on enforcement;
 * explicitly not persisted state per Data Model §3.4.
 */
fun debtQuartileMarkers(ceiling: Double): List<Double> =
    listOf(0.25, 0.5, 0.75).map { it * ceiling }

/**
 * Iron calibration gate, Data Model §5: tierActivationAt for Iron cannot precede
 * (tierSelectedAt + calibrationWindowDays). This function only answers "is the gate
 * satisfied as of now" — it does not mutate anything; the caller decides what to do with
 * a false result (block activation, show countdown per Onboarding doc §2.5).
 */
fun ironCalibrationSatisfied(
    tier: Tier,
    tierSelectedAtEpochMilli: Long,
    calibrationWindowDays: Int,
    nowEpochMilli: Long,
): Boolean {
    if (tier != Tier.IRON) return true // gate only applies to Iron
    val windowMillis = calibrationWindowDays * 24L * 60 * 60 * 1000
    return nowEpochMilli >= tierSelectedAtEpochMilli + windowMillis
}

/**
 * Batch G5 (BUILD_PLAN.md), Integration Plan §6 / base design doc §5 ("Mission lifecycle:
 * observe -> hypothesize -> enforce -> review"). Answers "should this GoalMission move from
 * OBSERVING to HYPOTHESIZING as of now" — a pure read, same "does not mutate anything, caller
 * decides what to do with the result" contract [ironCalibrationSatisfied] already establishes
 * for this file, and the same reuse Integration Plan §6 explicitly asks for ("computed the same
 * way ironCalibrationSatisfied is today — a pure function, reused rather than re-derived at each
 * call site").
 *
 * Base doc §5, step 2: "after a minimum number of outcome logs with no behavior attached (a
 * small number, e.g. 2-3 -- exact threshold is `[HYPOTHESIS]`, not fixed here), the app may
 * surface a single, dismissible, non-scored prompt to attach a behavior." Two conditions from
 * that sentence, both required:
 * - the mission is still in [LifecycleStage.OBSERVING] (step 1's own state) — a mission already
 *   past Observing has nothing to transition *into* Hypothesizing from; re-firing this check on
 *   an already-Hypothesizing or already-Enforcing mission must be a no-op, not a downgrade.
 * - [outcomeLogCount] (count of [com.disciplineos.data.entity.MissionLogEntry] rows for this
 *   mission) has reached [threshold], with no behavior attached yet.
 *
 * "No behavior attached yet" is represented here as [hasAnyBehaviorAttached] rather than this
 * function inspecting [com.disciplineos.data.entity.MissionPeriod] rows itself — same
 * DB-free-pure-function boundary this file's own kdoc states; the caller (a use-case or Fragment
 * with real DAO access) resolves that existence check (`MissionPeriodDao.forMission(missionId)
 * .isNotEmpty()`) and passes in the boolean.
 *
 * [threshold] has no default here (unlike [ironCalibrationSatisfied]'s implicit reliance on a
 * caller-supplied [Int] with no built-in fallback either) — the placeholder value itself lives
 * at the call site as a flagged `[HYPOTHESIS]` constant, matching every other unstated-in-spec
 * number in this codebase (`NEAR_MISS_MARGIN`, `MIN_ENTRIES_FOR_TREND`, etc.) rather than being
 * silently baked into this shared function where a future real value would require an API
 * change to plug in.
 */
fun hypothesizingStageSatisfied(
    currentStage: LifecycleStage,
    hasAnyBehaviorAttached: Boolean,
    outcomeLogCount: Int,
    threshold: Int,
): Boolean {
    if (currentStage != LifecycleStage.OBSERVING) return false
    if (hasAnyBehaviorAttached) return false
    return outcomeLogCount >= threshold
}
