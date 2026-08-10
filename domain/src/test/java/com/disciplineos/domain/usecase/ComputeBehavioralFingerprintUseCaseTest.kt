package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.PredictiveFailureAlertDismissal
import com.disciplineos.data.entity.PredictiveFailureAlertOutcome
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.entity.ViolationType
import com.disciplineos.data.ledger.LedgerEntry
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.HypothesisBehavioralFingerprintPolicy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * ROADMAP.md Phase 4 exit criterion — F1–F5 rule implementations, `04_BEHAVIORAL_FINGERPRINT
 * _RULES_SPEC.md` in full. Same in-memory-Room-via-Robolectric approach as
 * [RecordViolationUseCaseTest]/[ApplyReputationDecayUseCaseTest] — see those files' kdoc for
 * why (SQLCipher needs a real device, so the encrypted-DB factory is swapped for Room's plain
 * in-memory builder while everything else about the schema/DAOs stays real).
 *
 * A user is seeded with `tierSelectedAt` far enough in the past that
 * [com.disciplineos.data.metrics.ironCalibrationSatisfied] is always true for these tests
 * (calibration-window gating is a cross-cutting concern tested once, separately, in
 * [calibration window gate suppresses every rule's user-facing alert] below) — every other
 * test in this file is about one rule's own trigger condition, not the calibration gate
 * layered on top of it.
 *
 * NOTE: written but not executed in this environment — same standing gap
 * [RecordViolationUseCaseTest] already flags: no Android/Robolectric runtime available in this
 * sandbox to run `./gradlew :domain:test` against. Run it for real before merging.
 */
@RunWith(RobolectricTestRunner::class)
class ComputeBehavioralFingerprintUseCaseTest {

    private lateinit var db: DisciplineOsDatabase
    private lateinit var useCase: ComputeBehavioralFingerprintUseCase
    private val policy = HypothesisBehavioralFingerprintPolicy()

    private val userId = UUID.randomUUID()
    private val missionProfileId = UUID.randomUUID()
    private val now = Instant.parse("2026-08-10T12:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        useCase = ComputeBehavioralFingerprintUseCase(
            missionDao = db.missionDao(),
            violationDao = db.violationDao(),
            ledgerDao = db.ledgerDao(),
            userDao = db.userDao(),
            dismissalDao = db.predictiveFailureAlertDismissalDao(),
            policy = policy,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedUser(tier: Tier = Tier.OPERATOR, tierSelectedAt: Instant = now.minus(365, ChronoUnit.DAYS)) {
        db.userDao().insert(
            User(
                id = userId,
                createdAt = tierSelectedAt,
                currentTier = tier,
                tierSelectedAt = tierSelectedAt,
                tierActivationAt = tierSelectedAt,
                calibrationWindowDays = 10,
                onboardingConsentVersion = "v1",
            )
        )
    }

    private suspend fun seedMissionWithViolation(
        actualStart: Instant,
        detectedAt: Instant,
        status: MissionStatus = MissionStatus.VIOLATED,
    ): UUID {
        val missionId = UUID.randomUUID()
        db.missionDao().insert(
            Mission(
                id = missionId,
                userId = userId,
                scheduledStart = null,
                actualStart = actualStart,
                actualEnd = actualStart.plusSeconds(60),
                plannedDurationMin = 30,
                status = status,
                allowlist = emptyList(),
                blocklist = emptyList(),
                missionProfileId = missionProfileId,
            )
        )
        db.violationDao().insert(
            Violation(
                id = UUID.randomUUID(),
                missionId = missionId,
                detectedAt = detectedAt,
                type = ViolationType.BLOCKLIST_ACCESS,
            )
        )
        return missionId
    }

    // ---------------------------------------------------------------------
    // F1 — Time-of-Day Violation Clustering
    // ---------------------------------------------------------------------

    @Test
    fun `F1 does not trigger with no violations`() = runTest {
        seedUser()
        val result = useCase.execute(userId, now)
        val f1 = result.signals.first { it.rule == FingerprintRule.F1 }
        assertFalse(f1.triggered)
        assertTrue(result.activeAlerts.none { it.rule == FingerprintRule.F1 })
    }

    @Test
    fun `F1 triggers internally once 3 violations cluster in a 2-hour window`() = runTest {
        seedUser()
        // Three violations all around 21:00-22:00, spread across 2 distinct weeks so the
        // user-facing gate can also clear in the sample-size test below.
        val week1 = now.minus(20, ChronoUnit.DAYS)
        val week2 = now.minus(6, ChronoUnit.DAYS)
        seedMissionWithViolation(week1, week1.atZoneSameHour(21))
        seedMissionWithViolation(week1.plusSeconds(3600), week1.atZoneSameHour(21).plusSeconds(1800))
        seedMissionWithViolation(week2, week2.atZoneSameHour(22))

        val result = useCase.execute(userId, now)
        val f1 = result.signals.first { it.rule == FingerprintRule.F1 }
        assertTrue(f1.triggered)
    }

    @Test
    fun `F1 does not surface a user-facing alert below the sample-size gate`() = runTest {
        seedUser()
        // Only 3 violations total — well under f1MinSampleSize() (10) — so even though the
        // cluster itself triggers internally, no alert should surface per Spec §4.
        val week1 = now.minus(20, ChronoUnit.DAYS)
        seedMissionWithViolation(week1, week1.atZoneSameHour(21))
        seedMissionWithViolation(week1.plusSeconds(3600), week1.atZoneSameHour(21).plusSeconds(1800))
        seedMissionWithViolation(week1.plusSeconds(7200), week1.atZoneSameHour(22))

        val result = useCase.execute(userId, now)
        assertTrue(result.activeAlerts.none { it.rule == FingerprintRule.F1 })
    }

    @Test
    fun `F1 surfaces a user-facing alert once sample size and distinct-week gates both clear`() = runTest {
        seedUser()
        val week1 = now.minus(20, ChronoUnit.DAYS)
        val week2 = now.minus(6, ChronoUnit.DAYS)
        // 10 violations total (meets f1MinSampleSize), clustered around hour 21, spanning 2
        // distinct calendar weeks (meets f1MinDistinctWeeks).
        repeat(5) { i -> seedMissionWithViolation(week1.plusSeconds(i * 120L), week1.atZoneSameHour(21).plusSeconds(i * 120L)) }
        repeat(5) { i -> seedMissionWithViolation(week2.plusSeconds(i * 120L), week2.atZoneSameHour(21).plusSeconds(i * 120L)) }

        val result = useCase.execute(userId, now)
        val alert = result.activeAlerts.firstOrNull { it.rule == FingerprintRule.F1 }
        assertTrue(alert != null)
        assertEquals(FollowUpAction.REVIEW_EVENING_MISSION_PROFILE, alert!!.followUpAction)
    }

    // ---------------------------------------------------------------------
    // F2 — Pre-Mission Cancellation Pattern
    // ---------------------------------------------------------------------

    @Test
    fun `F2 does not trigger when early-cancellation proportion is below threshold`() = runTest {
        seedUser()
        // 8 resolved Missions (meets f2MinSampleSize), only 1 early-cancelled (12.5%, under 25%).
        repeat(7) { i -> seedResolvedMission(now.minus(1, ChronoUnit.DAYS).plusSeconds(i * 3600L), durationMinutes = 30, status = MissionStatus.COMPLETED) }
        seedResolvedMission(now.minus(2, ChronoUnit.DAYS), durationMinutes = 2, status = MissionStatus.VIOLATED)

        val result = useCase.execute(userId, now)
        val f2 = result.signals.first { it.rule == FingerprintRule.F2 }
        assertFalse(f2.triggered)
    }

    @Test
    fun `F2 triggers and surfaces an alert when early-cancellation proportion exceeds threshold`() = runTest {
        seedUser()
        // 8 resolved Missions, 3 early-cancelled (37.5%, over 25%).
        repeat(5) { i -> seedResolvedMission(now.minus(1, ChronoUnit.DAYS).plusSeconds(i * 3600L), durationMinutes = 30, status = MissionStatus.COMPLETED) }
        repeat(3) { i -> seedResolvedMission(now.minus(2, ChronoUnit.DAYS).plusSeconds(i * 3600L), durationMinutes = 2, status = MissionStatus.VIOLATED) }

        val result = useCase.execute(userId, now)
        val f2 = result.signals.first { it.rule == FingerprintRule.F2 }
        assertTrue(f2.triggered)
        val alert = result.activeAlerts.firstOrNull { it.rule == FingerprintRule.F2 }
        assertTrue(alert != null)
        assertEquals(FollowUpAction.REVIEW_MISSION_PROFILE_SCOPE, alert!!.followUpAction)
    }

    @Test
    fun `F2 excludes ABORTED_CRISIS_EXIT missions from the early-cancellation count`() = runTest {
        seedUser()
        repeat(5) { i -> seedResolvedMission(now.minus(1, ChronoUnit.DAYS).plusSeconds(i * 3600L), durationMinutes = 30, status = MissionStatus.COMPLETED) }
        // 3 crisis exits, all "early" by duration — should NOT count toward F2 at all (denominator
        // also excludes them, since resolvedMissionCountSince includes them but they were coded
        // deliberately excluded from the early-cancellation numerator only — see DAO kdoc).
        repeat(3) { i -> seedResolvedMission(now.minus(2, ChronoUnit.DAYS).plusSeconds(i * 3600L), durationMinutes = 2, status = MissionStatus.ABORTED_CRISIS_EXIT) }

        val result = useCase.execute(userId, now)
        val f2 = result.signals.first { it.rule == FingerprintRule.F2 }
        assertFalse(f2.triggered)
    }

    // ---------------------------------------------------------------------
    // F3 — Debt Trajectory Slope
    // ---------------------------------------------------------------------

    @Test
    fun `F3 does not trigger when Debt is flat`() = runTest {
        seedUser()
        seedResolvedMission(now.minus(1, ChronoUnit.DAYS), durationMinutes = 45, status = MissionStatus.COMPLETED)
        // Single flat entry, no rising slope.
        db.ledgerDao().insert(
            LedgerEntry(id = UUID.randomUUID(), userId = userId, violationId = null, metric = LedgerMetric.DEBT, delta = 10.0, appliedAt = now.minus(3, ChronoUnit.DAYS))
        )

        val result = useCase.execute(userId, now)
        val f3 = result.signals.first { it.rule == FingerprintRule.F3 }
        assertFalse(f3.triggered)
    }

    @Test
    fun `F3 triggers and links to Recovery Mode when Debt is rising and over half the ceiling`() = runTest {
        seedUser()
        // avgMissionDurationMin = 45 -> debtCeiling = 14 * 45 = 630. Need currentDebt > 315.
        repeat(3) { i -> seedResolvedMission(now.minus(10, ChronoUnit.DAYS).plusSeconds(i * 86_400L), durationMinutes = 45, status = MissionStatus.COMPLETED) }
        // Rising Debt over the last 7 days, ending well above 315.
        for (day in 6 downTo 0) {
            db.ledgerDao().insert(
                LedgerEntry(
                    id = UUID.randomUUID(),
                    userId = userId,
                    violationId = null,
                    metric = LedgerMetric.DEBT,
                    delta = 50.0,
                    appliedAt = now.minus(day.toLong(), ChronoUnit.DAYS),
                )
            )
        }

        val result = useCase.execute(userId, now)
        val f3 = result.signals.first { it.rule == FingerprintRule.F3 }
        assertTrue(f3.triggered)
        val alert = result.activeAlerts.firstOrNull { it.rule == FingerprintRule.F3 }
        assertTrue(alert != null)
        assertEquals(FollowUpAction.OPEN_RECOVERY_MODE, alert!!.followUpAction)
    }

    @Test
    fun `F3 does not trigger when Debt is rising but still under half the ceiling`() = runTest {
        seedUser()
        repeat(3) { i -> seedResolvedMission(now.minus(10, ChronoUnit.DAYS).plusSeconds(i * 86_400L), durationMinutes = 45, status = MissionStatus.COMPLETED) }
        // Rising, but small deltas -> stays well under 315.
        for (day in 6 downTo 0) {
            db.ledgerDao().insert(
                LedgerEntry(
                    id = UUID.randomUUID(),
                    userId = userId,
                    violationId = null,
                    metric = LedgerMetric.DEBT,
                    delta = 2.0,
                    appliedAt = now.minus(day.toLong(), ChronoUnit.DAYS),
                )
            )
        }

        val result = useCase.execute(userId, now)
        val f3 = result.signals.first { it.rule == FingerprintRule.F3 }
        assertFalse(f3.triggered)
    }

    // ---------------------------------------------------------------------
    // F4 — internal-only, never user-facing
    // ---------------------------------------------------------------------

    @Test
    fun `F4 never produces a user-facing alert regardless of state`() = runTest {
        seedUser()
        val result = useCase.execute(userId, now)
        assertTrue(result.activeAlerts.none { it.rule == FingerprintRule.F4 })
        val f4 = result.signals.first { it.rule == FingerprintRule.F4 }
        assertFalse(f4.triggered) // this pass ships no real F4 projection — see class kdoc
    }

    // ---------------------------------------------------------------------
    // F5 — Mission Profile Drift (mechanism only; threshold deliberately unreachable pre-pilot)
    // ---------------------------------------------------------------------

    @Test
    fun `F5 counts disputes but does not trigger against the deliberately-high placeholder threshold`() = runTest {
        seedUser()
        val missionId = seedResolvedMission(now.minus(1, ChronoUnit.DAYS), durationMinutes = 30, status = MissionStatus.COMPLETED)
        db.violationDao().insert(
            Violation(
                id = UUID.randomUUID(),
                missionId = missionId,
                detectedAt = now.minus(1, ChronoUnit.DAYS),
                type = ViolationType.EARLY_EXIT,
                disputeStatus = DisputeStatus.FLAGGED,
            )
        )

        val result = useCase.execute(userId, now)
        val f5 = result.signals.first { it.rule == FingerprintRule.F5 }
        assertFalse(f5.triggered) // policy.f5DisputeClusterThreshold() defaults to 999 — see policy kdoc
        assertEquals(1, f5.value)
    }

    // ---------------------------------------------------------------------
    // Cross-cutting: calibration window + dismissal suppression
    // ---------------------------------------------------------------------

    @Test
    fun `calibration window gate suppresses every rule's user-facing alert`() = runTest {
        // tierSelectedAt = now -> still inside the 10-day calibration window.
        seedUser(tierSelectedAt = now)
        val week1 = now.minus(6, ChronoUnit.DAYS)
        val week2 = now.minus(1, ChronoUnit.DAYS)
        repeat(5) { i -> seedMissionWithViolation(week1.plusSeconds(i * 120L), week1.atZoneSameHour(21).plusSeconds(i * 120L)) }
        repeat(5) { i -> seedMissionWithViolation(week2.plusSeconds(i * 120L), week2.atZoneSameHour(21).plusSeconds(i * 120L)) }

        val result = useCase.execute(userId, now)
        // F1's internal signal still triggers (calibration only gates the user-facing alert).
        assertTrue(result.signals.first { it.rule == FingerprintRule.F1 }.triggered)
        assertTrue(result.activeAlerts.isEmpty())
    }

    @Test
    fun `a recorded dismissal suppresses that rule's alert on the next check`() = runTest {
        seedUser()
        // Same F2-triggering setup as the earlier positive test.
        repeat(5) { i -> seedResolvedMission(now.minus(1, ChronoUnit.DAYS).plusSeconds(i * 3600L), durationMinutes = 30, status = MissionStatus.COMPLETED) }
        repeat(3) { i -> seedResolvedMission(now.minus(2, ChronoUnit.DAYS).plusSeconds(i * 3600L), durationMinutes = 2, status = MissionStatus.VIOLATED) }

        // Confirm it would otherwise trigger.
        val before = useCase.execute(userId, now)
        assertTrue(before.activeAlerts.any { it.rule == FingerprintRule.F2 })

        db.predictiveFailureAlertDismissalDao().insert(
            PredictiveFailureAlertDismissal(
                id = UUID.randomUUID(),
                ruleId = FingerprintRule.F2.name,
                outcome = PredictiveFailureAlertOutcome.ACKNOWLEDGED,
                dismissedAt = now,
            )
        )

        val after = useCase.execute(userId, now.plusSeconds(60))
        assertTrue(after.activeAlerts.none { it.rule == FingerprintRule.F2 })
    }

    @Test
    fun `null user is handled defensively with no user-facing alerts`() = runTest {
        // No seedUser() call at all — mirrors HomeFragment's "shouldn't be reachable" defensive
        // handling for a missing User row (see HomeFragment.kt kdoc for the identical reasoning).
        val result = useCase.execute(UUID.randomUUID(), now)
        assertTrue(result.activeAlerts.isEmpty())
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    private suspend fun seedResolvedMission(actualStart: Instant, durationMinutes: Long, status: MissionStatus): UUID {
        val missionId = UUID.randomUUID()
        db.missionDao().insert(
            Mission(
                id = missionId,
                userId = userId,
                scheduledStart = null,
                actualStart = actualStart,
                actualEnd = actualStart.plusSeconds(durationMinutes * 60),
                plannedDurationMin = durationMinutes.toInt(),
                status = status,
                allowlist = emptyList(),
                blocklist = emptyList(),
                missionProfileId = missionProfileId,
            )
        )
        return missionId
    }

    /** Test-only helper: same Instant but with [hour] substituted (UTC), keeping day/month/year. */
    private fun Instant.atZoneSameHour(hour: Int): Instant {
        val zoned = this.atZone(java.time.ZoneOffset.UTC).withHour(hour).withMinute(0).withSecond(0).withNano(0)
        return zoned.toInstant()
    }
}
