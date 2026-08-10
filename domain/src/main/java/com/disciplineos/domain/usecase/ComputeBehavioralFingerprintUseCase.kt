package com.disciplineos.domain.usecase

import com.disciplineos.data.dao.MissionDao
import com.disciplineos.data.dao.PredictiveFailureAlertDismissalDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.data.metrics.clampToDebtCeiling
import com.disciplineos.data.metrics.debtCeiling
import com.disciplineos.data.metrics.ironCalibrationSatisfied
import com.disciplineos.domain.policy.BehavioralFingerprintPolicy
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.UUID

/**
 * Behavioral Fingerprint & Predictive Failure Rules Spec (`04_BEHAVIORAL_FINGERPRINT_RULES
 * _SPEC.md`) — ROADMAP.md Phase 4 exit criterion. Computes all five [FingerprintRule]s
 * fresh on every call (Onboarding/Interaction Spec §3.5: "checked on app open and after each
 * Mission completion," not read from a stored snapshot — see [BehavioralFingerprintResult]'s
 * kdoc for why nothing here persists as a durable `BehavioralFingerprint`/`FingerprintSignal`
 * row; [DisciplineOsDatabase]'s v8 migration note restates the same reasoning). No Android
 * dependency, matching every other use-case in this module — [HomeFragment] is the only
 * current caller and does the async/lifecycle wiring on its side.
 *
 * **Internal trigger vs. user-facing alert are two different gates, not one.** Every rule
 * computes a [FingerprintSignal.triggered] value from its own condition alone (Spec §3). A
 * [PredictiveFailureAlert] is only added to [BehavioralFingerprintResult.activeAlerts] when,
 * additionally: the rule is one of the four user-facing ones (F4 never surfaces — Spec §3, F4:
 * "no UI surface at all until promoted"), the rule's own sample-size gate clears (Spec §4),
 * the user is past Iron calibration if applicable (see below), and no live dismissal already
 * suppresses it (see below). Test file
 * `calibration window gate suppresses every rule's user-facing alert` is explicit that F1's
 * internal signal still triggers during calibration — only the alert is gated.
 *
 * **Calibration-window gate — a judgment call, not directly spec-stated.** Neither the
 * Fingerprint doc nor the Onboarding/Interaction Spec says predictive alerts should be
 * suppressed during a user's Iron calibration window. This implementation treats it as a
 * reasonable reading anyway: calibration is explicitly a "just keep using the app normally,
 * we're just watching" window (Data Model §5 / PRD §12.6), and surfacing a "here's a pattern
 * in your behavior" alert during a window whose entire premise is *not* editorializing about
 * behavior yet would sit against that framing — same posture as [ApplyReputationDecayUseCase]'s
 * "Crisis stabilization pause" judgment call, flagged explicitly rather than silently assumed.
 * Reuses [ironCalibrationSatisfied] (the same pure function [HomeFragment.computeHomeState] and
 * [TierTransitionUseCase.activateIron] already gate on) rather than re-deriving the window
 * threshold a second time. If the user has no tier selected yet (shouldn't happen — this
 * use-case is only ever called post-onboarding, same assumption [ApplyReputationDecayUseCase]
 * makes), the gate defaults to "satisfied" so a null/pre-onboarding user doesn't spuriously
 * suppress everything either.
 *
 * **Dismissal suppression — Fingerprint doc §5 / Onboarding §3.5.** A rule whose most recent
 * dismissal (any outcome — "Not accurate" and "Got it" both suppress equally; Spec §3.5 doesn't
 * say a "Not accurate" dismissal should make the same pattern re-show sooner) falls within
 * a rule-specific window is treated as still-suppressed — see [dismissalWindowDays] for the
 * per-rule mapping (F2/F3/F5 reuse their own detection window; F1 has no day-width of its own
 * since it's Mission-scoped rather than day-scoped, so it falls back to a flagged default) as
 * the "same underlying pattern instance" window
 * [PredictiveFailureAlertDismissalDao.mostRecentSince]'s kdoc describes.
 */
class ComputeBehavioralFingerprintUseCase(
    private val missionDao: MissionDao,
    private val violationDao: ViolationDao,
    private val ledgerDao: LedgerDao,
    private val userDao: UserDao,
    private val dismissalDao: PredictiveFailureAlertDismissalDao,
    private val policy: BehavioralFingerprintPolicy,
) {

    /** Mirrors [debtCeiling]'s own default `windowDays` (Data Model §3.4) — see [computeF3]'s kdoc for why F3 needs this rather than its own narrower window. */
    private val debtCeilingWindowDays = 14

    suspend fun execute(userId: UUID, now: Instant): BehavioralFingerprintResult {
        val user = userDao.get(userId)

        // Calibration gate — see class kdoc. Defaults to "satisfied" (i.e. don't suppress)
        // for a null/untiered user, since that's an already-defensive "shouldn't be
        // reachable" case elsewhere in this codebase, not a real calibration window.
        val calibrationSatisfied = user?.let { u ->
            val tierSelectedAt = u.tierSelectedAt ?: return@let true
            val tier = u.currentTier ?: return@let true
            ironCalibrationSatisfied(
                tier = tier,
                tierSelectedAtEpochMilli = tierSelectedAt.toEpochMilli(),
                calibrationWindowDays = u.calibrationWindowDays,
                nowEpochMilli = now.toEpochMilli(),
            )
        } ?: true

        val f1 = computeF1(userId, now)
        val f2 = computeF2(userId, now)
        val f3 = computeF3(userId, now)
        val f4 = computeF4()
        val f5 = computeF5(userId, now)

        val signals = listOf(f1, f2, f3, f4, f5)

        // §3.5 declaration order F1 -> F2 -> F3 -> F5 (F4 has no follow-up action at all,
        // see FollowUpAction's kdoc) determines which alert wins if more than one rule
        // triggers in the same session — HomeFragment takes result.activeAlerts.firstOrNull().
        val activeAlerts = mutableListOf<PredictiveFailureAlert>()

        // user == null is the "shouldn't be reachable" defensive case (see HomeFragment kdoc's
        // identical reasoning) — no user-facing alerts at all without a real User row, since
        // there'd be nothing meaningful to have dismissed or calibrated against either.
        if (user != null) {
            val f1ClusterInfo = f1.value as? F1ClusterInfo
            if (f1.triggered && f1.sampleSize >= policy.f1MinSampleSize() &&
                (f1ClusterInfo?.distinctWeeks ?: 0) >= policy.f1MinDistinctWeeks() &&
                calibrationSatisfied && !isDismissed(FingerprintRule.F1, now)
            ) {
                activeAlerts += PredictiveFailureAlert(FingerprintRule.F1, FollowUpAction.REVIEW_EVENING_MISSION_PROFILE)
            }
            if (f2.triggered && f2.sampleSize >= policy.f2MinSampleSize() &&
                calibrationSatisfied && !isDismissed(FingerprintRule.F2, now)
            ) {
                activeAlerts += PredictiveFailureAlert(FingerprintRule.F2, FollowUpAction.REVIEW_MISSION_PROFILE_SCOPE)
            }
            if (f3.triggered && f3.sampleSize >= policy.f3MinSampleDays() &&
                calibrationSatisfied && !isDismissed(FingerprintRule.F3, now)
            ) {
                activeAlerts += PredictiveFailureAlert(FingerprintRule.F3, FollowUpAction.OPEN_RECOVERY_MODE)
            }
            // F4: never user-facing — Spec §3, F4. No branch here at all, matching
            // HomeFragment/PredictiveFailureAlertCard's identical omission.
            if (f5.triggered && calibrationSatisfied && !isDismissed(FingerprintRule.F5, now)) {
                activeAlerts += PredictiveFailureAlert(FingerprintRule.F5, FollowUpAction.REVIEW_MISSION_PROFILE_DRIFT)
            }
        }

        return BehavioralFingerprintResult(signals = signals, activeAlerts = activeAlerts)
    }

    // -----------------------------------------------------------------
    // F1 — Time-of-Day Violation Clustering
    // -----------------------------------------------------------------

    /**
     * Spec §3, F1: "if >= [BehavioralFingerprintPolicy.f1MinClusterCount] violations fall
     * within the same [BehavioralFingerprintPolicy.f1ClusterWindowHours]-hour window across
     * the last [BehavioralFingerprintPolicy.f1MissionWindow] Missions with any violation, flag
     * that window." Scoped by Mission count (not violation count) per
     * [MissionDao.missionIdsWithAnyViolation]'s kdoc — the literal spec reading, since a
     * single Mission can carry more than one Violation.
     *
     * Clustering is binned by hour-of-day (UTC — this codebase has no user-timezone concept
     * anywhere else either, so introducing one here would be new, unrequested scope) using a
     * sliding check across all 24 possible [f1ClusterWindowHours]-wide start hours, since the
     * spec's "same 2-hour window" doesn't pin the window to a fixed clock boundary (e.g.
     * 21:00-23:00 vs 21:30-23:30 should both count as the same cluster if 3+ violations fall
     * in either).
     */
    private suspend fun computeF1(userId: UUID, now: Instant): FingerprintSignal {
        val missionIds = missionDao.missionIdsWithAnyViolation(userId, policy.f1MissionWindow())
        if (missionIds.isEmpty()) {
            return FingerprintSignal(FingerprintRule.F1, triggered = false, confidence = FingerprintConfidence.NONE, sampleSize = 0, value = null)
        }

        val timestamps = violationDao.detectedAtTimestampsForMissions(missionIds)
        val sampleSize = timestamps.size

        val hours = timestamps.map { it.atZone(ZoneOffset.UTC).hour }
        val clusterWindow = policy.f1ClusterWindowHours()
        val minCount = policy.f1MinClusterCount()

        // Slide a clusterWindow-hour window across all 24 possible start hours (wrapping past
        // midnight), counting how many timestamps fall in each — the "same 2-hour window"
        // check the class kdoc describes.
        var bestStartHour = -1
        var bestCount = 0
        for (startHour in 0 until 24) {
            val count = hours.count { h -> hourInWindow(h, startHour, clusterWindow) }
            if (count > bestCount) {
                bestCount = count
                bestStartHour = startHour
            }
        }

        val clusterTriggered = bestCount >= minCount
        if (!clusterTriggered) {
            return FingerprintSignal(FingerprintRule.F1, triggered = false, confidence = confidenceFor(sampleSize, policy.f1MinSampleSize()), sampleSize = sampleSize, value = bestCount)
        }

        // Distinct-week gate (Spec §3: "must hold across at least
        // f1MinDistinctWeeks distinct calendar weeks") applies to the *clustered* timestamps
        // specifically — the ones that fell inside the winning window — not to every
        // timestamp in the sample.
        val distinctWeeks = timestamps
            .filter { hourInWindow(it.atZone(ZoneOffset.UTC).hour, bestStartHour, clusterWindow) }
            .map { it.atZone(ZoneOffset.UTC).get(WeekFields.ISO.weekOfWeekBasedYear()) to it.atZone(ZoneOffset.UTC).get(WeekFields.ISO.weekBasedYear()) }
            .distinct()
            .size

        // Spec §3's clustering condition itself (the internal trigger) doesn't require the
        // distinct-week gate — that gate belongs to the user-facing alert per Spec §4's
        // separate sample-size/week requirement. Folding it into `triggered` here would make
        // the "F1 triggers internally once 3 violations cluster" test (single-week input)
        // fail, so `triggered` reflects the cluster condition alone; the week count travels in
        // `value` (see [F1ClusterInfo]) so execute() can apply the week gate only to the
        // user-facing alert, not the internal signal.
        return FingerprintSignal(
            rule = FingerprintRule.F1,
            triggered = true,
            confidence = confidenceFor(sampleSize, policy.f1MinSampleSize()),
            sampleSize = sampleSize,
            value = F1ClusterInfo(bestStartHour, bestCount, distinctWeeks),
        )
    }

    private data class F1ClusterInfo(val startHour: Int, val count: Int, val distinctWeeks: Int)

    /** True if hour [h] falls in the [clusterWindow]-hour window starting at [startHour], wrapping past midnight. */
    private fun hourInWindow(h: Int, startHour: Int, clusterWindow: Int): Boolean {
        val offset = ((h - startHour) % 24 + 24) % 24
        return offset < clusterWindow
    }

    // -----------------------------------------------------------------
    // F2 — Pre-Mission Cancellation Pattern
    // -----------------------------------------------------------------

    /**
     * Spec §3, F2: proportion of Missions in the last [BehavioralFingerprintPolicy.f2WindowDays]
     * days that ended within [BehavioralFingerprintPolicy.f2EarlyCancelMinutes] minutes of
     * `actualStart`, trigger at >= [BehavioralFingerprintPolicy.f2TriggerProportion].
     * `sampleSize` here is the denominator ([MissionDao.resolvedMissionCountSince]) — Spec §4's
     * F2 gate ("minimum Mission sample size") reads naturally as "enough resolved Missions to
     * trust the proportion," not "enough early cancellations," so the total is what's compared
     * against [BehavioralFingerprintPolicy.f2MinSampleSize].
     */
    private suspend fun computeF2(userId: UUID, now: Instant): FingerprintSignal {
        val since = now.minus(policy.f2WindowDays().toLong(), ChronoUnit.DAYS)
        val earlyCount = missionDao.earlyCancelledMissionsSince(userId, since, policy.f2EarlyCancelMinutes())
        val total = missionDao.resolvedMissionCountSince(userId, since)

        if (total == 0) {
            return FingerprintSignal(FingerprintRule.F2, triggered = false, confidence = FingerprintConfidence.NONE, sampleSize = 0, value = 0.0)
        }

        val proportion = earlyCount.toDouble() / total
        val triggered = proportion >= policy.f2TriggerProportion()

        return FingerprintSignal(
            rule = FingerprintRule.F2,
            triggered = triggered,
            confidence = confidenceFor(total, policy.f2MinSampleSize()),
            sampleSize = total,
            value = proportion,
        )
    }

    // -----------------------------------------------------------------
    // F3 — Debt Trajectory Slope
    // -----------------------------------------------------------------

    /**
     * Spec §3, F3: Debt rising consecutively over
     * [BehavioralFingerprintPolicy.f3WindowDays] days AND current Debt exceeds
     * [BehavioralFingerprintPolicy.f3CeilingProportionThreshold] of Debt Ceiling. "Rising"
     * read here as the last entry's running value exceeding the first entry's running value
     * across the window (a monotonic-enough reading — the spec doesn't define "rising" more
     * precisely than "slope," and a single strict every-single-day monotonic requirement would
     * be brittle against same-day multiple entries or a single flat day inside an otherwise
     * rising week). `avgMissionDurationMin` for [debtCeiling] is deliberately sourced from
     * [debtCeilingWindowDays] (Data Model §3.4's own 14-day default for the Ceiling formula
     * itself), not [BehavioralFingerprintPolicy.f3WindowDays] (7 — F3's own slope-detection
     * window) — these answer different questions ("what's a representative avg Mission length
     * for sizing the Ceiling" vs. "did Debt rise over the last week") and conflating them would
     * make the Ceiling artificially small (or, as in a Mission history older than 7 days,
     * literally zero — see [debtCeilingWindowDays]'s kdoc) any time a user's recent Missions
     * happen to fall just outside F3's narrower slope window despite having plenty of Mission
     * history overall.
     */
    private suspend fun computeF3(userId: UUID, now: Instant): FingerprintSignal {
        val since = now.minus(policy.f3WindowDays().toLong(), ChronoUnit.DAYS)
        val entries = ledgerDao.entriesSince(userId, LedgerMetric.DEBT, since)

        if (entries.size < 2) {
            return FingerprintSignal(FingerprintRule.F3, triggered = false, confidence = FingerprintConfidence.NONE, sampleSize = entries.size, value = null)
        }

        // entriesSince orders appliedAt ASC (LedgerDao kdoc) — running-sum the deltas to get
        // the actual Debt trajectory over the window, not the raw per-entry deltas.
        var running = 0.0
        val trajectory = entries.map { running += it.delta; running }
        val rising = trajectory.last() > trajectory.first()

        // See class-doc note above on computeF3 for why this uses a wider window than
        // f3WindowDays — Data Model §3.4's own Ceiling-formula default (debtCeiling()'s
        // windowDays parameter default, 14), not F3's narrower 7-day slope window.
        val ceilingSince = now.minus(debtCeilingWindowDays.toLong(), ChronoUnit.DAYS)
        val resolvedMissions = missionDao.resolvedMissionsSince(userId, ceilingSince)
        val avgDuration = if (resolvedMissions.isEmpty()) {
            0.0
        } else {
            resolvedMissions.sumOf { it.plannedDurationMin }.toDouble() / resolvedMissions.size
        }
        val ceiling = debtCeiling(avgDuration)
        val currentRawDebt = ledgerDao.currentValue(userId, LedgerMetric.DEBT)
        val currentDebt = clampToDebtCeiling(currentRawDebt, ceiling)
        val overCeilingThreshold = ceiling > 0 && currentDebt > (policy.f3CeilingProportionThreshold() * ceiling)

        val triggered = rising && overCeilingThreshold

        // Spec §4: F3's sample-size gate is measured in *days* of Debt-sample history, not
        // entry count — distinct calendar days represented in the window's entries.
        val distinctDays = entries.map { it.appliedAt.atZone(ZoneOffset.UTC).toLocalDate() }.distinct().size

        return FingerprintSignal(
            rule = FingerprintRule.F3,
            triggered = triggered,
            confidence = confidenceFor(distinctDays, policy.f3MinSampleDays()),
            sampleSize = distinctDays,
            value = currentDebt,
        )
    }

    // -----------------------------------------------------------------
    // F4 — Reputation Decline Rate (internal-only, never user-facing)
    // -----------------------------------------------------------------

    /**
     * Spec §3, F4: "no UI surface at all until promoted." This pass ships the type and the
     * always-false stub deliberately, not a real projection — computing a genuine
     * days-to-tier-floor projection would need a Reputation trend model this codebase has no
     * other precedent for (F3's Debt slope is a much simpler "is it rising" check, not a
     * projection), and building one now for a signal with zero UI surface and no spec-given
     * formula would be exactly the kind of premature complexity Data Model §3.1's reasoning
     * argues against. Flagged as a real gap (ROADMAP.md), not silently no-op'd without a note.
     */
    private fun computeF4(): FingerprintSignal =
        FingerprintSignal(FingerprintRule.F4, triggered = false, confidence = FingerprintConfidence.NONE, sampleSize = 0, value = null)

    // -----------------------------------------------------------------
    // F5 — Mission Profile Drift
    // -----------------------------------------------------------------

    /**
     * Spec §3, F5: disputes/overrides against a Mission Profile's allow/blocklist, clustered
     * within [BehavioralFingerprintPolicy.f5WindowDays], compared against
     * [BehavioralFingerprintPolicy.f5DisputeClusterThreshold] (deliberately near-unreachable
     * pre-pilot — see that policy's kdoc). "Disputes/overrides" read here as any Violation
     * with [com.disciplineos.data.entity.DisputeStatus] != NONE (i.e. a dispute was actually
     * filed against it, regardless of outcome) attached to any of the user's Missions in the
     * window — the closest existing concept in this schema to "override," since there's no
     * separate override entity or Mission-Profile-scoped dispute table. Counted per-user
     * rather than per-Mission-Profile (no query yet joins through
     * [com.disciplineos.data.entity.MissionProfile] specifically), which is a simplification
     * flagged here rather than silently assumed exact — this codebase's current single-Profile
     * assumption ([com.disciplineos.data.dao.MissionProfileDao]'s "no picker UI yet" reasoning)
     * means per-user and per-Profile are equivalent for every user this app can currently have,
     * so the simplification has no observable effect today; revisit if/when multi-profile
     * support lands.
     */
    private suspend fun computeF5(userId: UUID, now: Instant): FingerprintSignal {
        val since = now.minus(policy.f5WindowDays().toLong(), ChronoUnit.DAYS)
        val resolvedMissions = missionDao.resolvedMissionsSince(userId, since)
        val activeMission = missionDao.activeMissionFor(userId)
        val missionIds = resolvedMissions.map { it.id } + listOfNotNull(activeMission?.id)

        var disputeCount = 0
        for (missionId in missionIds) {
            disputeCount += violationDao.forMission(missionId).count { it.disputeStatus != DisputeStatus.NONE }
        }

        val triggered = disputeCount >= policy.f5DisputeClusterThreshold()

        return FingerprintSignal(
            rule = FingerprintRule.F5,
            triggered = triggered,
            confidence = if (triggered) FingerprintConfidence.HIGH else if (disputeCount > 0) FingerprintConfidence.LOW else FingerprintConfidence.NONE,
            sampleSize = disputeCount,
            value = disputeCount,
        )
    }

    // -----------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------

    /** Spec §2: confidence derived from sample size relative to the rule's own minimum, not a fixed constant. */
    private fun confidenceFor(sampleSize: Int, minSampleSize: Int): FingerprintConfidence = when {
        sampleSize <= 0 -> FingerprintConfidence.NONE
        sampleSize < minSampleSize -> FingerprintConfidence.LOW
        sampleSize < minSampleSize * 2 -> FingerprintConfidence.MEDIUM
        else -> FingerprintConfidence.HIGH
    }

    /**
     * Fingerprint doc §5 / Onboarding §3.5: a live dismissal for [rule] suppresses its alert
     * on subsequent checks. "Live" means within [dismissalWindowDays] of [rule] — reusing
     * that rule's own detection window as the "same underlying pattern instance" horizon
     * [PredictiveFailureAlertDismissalDao.mostRecentSince]'s kdoc describes, since no separate
     * suppression-duration concept exists anywhere in the specs.
     */
    private suspend fun isDismissed(rule: FingerprintRule, now: Instant): Boolean {
        val windowDays = dismissalWindowDays(rule)
        val since = now.minus(windowDays.toLong(), ChronoUnit.DAYS)
        return dismissalDao.mostRecentSince(rule.name, since) != null
    }

    /** Per-rule suppression window, matching each rule's own detection window (see [isDismissed]). */
    private fun dismissalWindowDays(rule: FingerprintRule): Int = when (rule) {
        FingerprintRule.F1 -> 14 // F1 has no day-width of its own (Mission-window-scoped, not day-window-scoped) — 14 matches F2's neighboring lookback as a reasonable default, flagged here as a judgment call.
        FingerprintRule.F2 -> policy.f2WindowDays()
        FingerprintRule.F3 -> policy.f3WindowDays()
        FingerprintRule.F4 -> 0 // never surfaced — value unused
        FingerprintRule.F5 -> policy.f5WindowDays()
    }
}
