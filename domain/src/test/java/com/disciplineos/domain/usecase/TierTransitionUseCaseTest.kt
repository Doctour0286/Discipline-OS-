package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.TierEventKind
import com.disciplineos.data.entity.User
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * ROADMAP.md Phase 1 exit criteria: "Iron calibration gate is enforced at the point of tier
 * activation" and the Crisis Exit path referenced in `RecordViolationUseCase`'s §5.6 log
 * entry ("needs its own test once it exists").
 *
 * Same in-memory-Room-via-Robolectric approach as `RecordViolationUseCaseTest` — see that
 * file's kdoc for why (SQLCipher needs a real device, unavailable under Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
class TierTransitionUseCaseTest {

    private lateinit var db: DisciplineOsDatabase
    private lateinit var useCase: TierTransitionUseCase

    private val userId = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        useCase = TierTransitionUseCase(
            database = db,
            userDao = db.userDao(),
            tierDao = db.tierDao(),
            missionDao = db.missionDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedUser(
        tier: Tier = Tier.OPERATOR,
        tierSelectedAt: Instant = Instant.now(),
        calibrationWindowDays: Int = 10,
    ) {
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = tier,
                tierSelectedAt = tierSelectedAt,
                tierActivationAt = tierSelectedAt,
                calibrationWindowDays = calibrationWindowDays,
                onboardingConsentVersion = "v1",
            )
        )
    }

    private suspend fun seedMission(missionId: UUID, status: MissionStatus = MissionStatus.ACTIVE): Mission {
        val mission = Mission(
            id = missionId,
            userId = userId,
            scheduledStart = null,
            actualStart = Instant.now(),
            actualEnd = null,
            plannedDurationMin = 30,
            status = status,
            allowlist = emptyList(),
            blocklist = emptyList(),
            missionProfileId = UUID.randomUUID(),
        )
        db.missionDao().insert(mission)
        return mission
    }

    @Test
    fun `explicit downgrade moves tier and logs an event with no reason`() = runTest {
        seedUser(tier = Tier.WARDEN)

        val event = useCase.explicitDowngrade(userId, Tier.OPERATOR)

        assertEquals(TierEventKind.EXPLICIT_DOWNGRADE, event.kind)
        assertEquals(Tier.WARDEN, event.fromTier)
        assertEquals(Tier.OPERATOR, event.toTier)
        assertNull(event.reasonNote)
        assertEquals(Tier.OPERATOR, db.userDao().get(userId)!!.currentTier)
    }

    @Test
    fun `crisis downgrade always moves to Recruit and pauses debt accrual and Tribunal for 24 hours`() = runTest {
        seedUser(tier = Tier.IRON)
        // Truncated to millisecond precision: Room persists Instant via Converters.fromInstant
        // (toEpochMilli), which silently drops any sub-millisecond component. Instant.now()
        // often carries nanosecond precision on the JVM, so comparing an untruncated `now`
        // against a value re-read from the DB is flaky — equal only when `now` happens to
        // land on an exact millisecond boundary. Truncating here makes the in-memory value
        // and the DB round-trip agree deterministically.
        val now = Instant.ofEpochMilli(Instant.now().toEpochMilli())

        val event = useCase.crisisDowngrade(userId, triggerReason = "Tampering detected", now = now)

        assertEquals(TierEventKind.CRISIS_DOWNGRADE, event.kind)
        assertEquals(Tier.RECRUIT, event.toTier)
        assertEquals("Tampering detected", event.reasonNote)

        val user = db.userDao().get(userId)!!
        assertEquals(Tier.RECRUIT, user.currentTier)
        assertEquals(now.plus(24, ChronoUnit.HOURS), user.debtAccrualPausedUntil)
        assertEquals(now.plus(24, ChronoUnit.HOURS), user.tribunalDeferredUntil)
    }

    @Test
    fun `iron crisis exit marks the mission aborted, moves to Recruit, and logs a distinct event kind`() = runTest {
        seedUser(tier = Tier.IRON)
        val missionId = UUID.randomUUID()
        seedMission(missionId)

        val event = useCase.ironCrisisExit(userId, missionId)

        // Distinct from a standard §12.4.2 Explicit Downgrade, per PRD §12.4.4 — this is the
        // whole point of the kind existing separately.
        assertEquals(TierEventKind.IRON_CRISIS_EXIT, event.kind)
        assertEquals(Tier.RECRUIT, event.toTier)
        assertNull(event.reasonNote) // no reason entry, per spec

        assertEquals(Tier.RECRUIT, db.userDao().get(userId)!!.currentTier)
        assertEquals(MissionStatus.ABORTED_CRISIS_EXIT, db.missionDao().get(missionId)!!.status)
        assertNotNull(db.userDao().get(userId)!!.debtAccrualPausedUntil)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `iron crisis exit is rejected outside Iron tier`() = runTest {
        seedUser(tier = Tier.WARDEN)
        val missionId = UUID.randomUUID()
        seedMission(missionId)

        useCase.ironCrisisExit(userId, missionId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a mission marked aborted crisis exit by this use-case cannot be double-charged by RecordViolationUseCase`() = runTest {
        seedUser(tier = Tier.IRON)
        val missionId = UUID.randomUUID()
        seedMission(missionId)
        useCase.ironCrisisExit(userId, missionId)

        val recordViolation = RecordViolationUseCase(
            database = db,
            violationDao = db.violationDao(),
            missionDao = db.missionDao(),
            userDao = db.userDao(),
            ledgerDao = db.ledgerDao(),
            consequencePolicy = com.disciplineos.domain.policy.HypothesisConsequencePolicy(),
        )

        recordViolation.execute(
            com.disciplineos.data.entity.Violation(
                id = UUID.randomUUID(),
                missionId = missionId,
                detectedAt = Instant.now(),
                type = com.disciplineos.data.entity.ViolationType.NON_START,
            )
        )
    }

    @Test
    fun `activate iron succeeds once the calibration window has elapsed`() = runTest {
        val selectedAt = Instant.now().minus(11, ChronoUnit.DAYS)
        seedUser(tier = Tier.OPERATOR, tierSelectedAt = selectedAt, calibrationWindowDays = 10)

        val event = useCase.activateIron(userId)

        assertEquals(Tier.IRON, event.toTier)
        assertEquals(Tier.IRON, db.userDao().get(userId)!!.currentTier)
    }

    @Test(expected = IllegalStateException::class)
    fun `activate iron is rejected before the calibration window elapses, with no exception path`() = runTest {
        val selectedAt = Instant.now().minus(3, ChronoUnit.DAYS)
        seedUser(tier = Tier.OPERATOR, tierSelectedAt = selectedAt, calibrationWindowDays = 10)

        useCase.activateIron(userId)
    }

    @Test
    fun `activate iron failure leaves tier unmoved`() = runTest {
        val selectedAt = Instant.now().minus(3, ChronoUnit.DAYS)
        seedUser(tier = Tier.OPERATOR, tierSelectedAt = selectedAt, calibrationWindowDays = 10)

        try {
            useCase.activateIron(userId)
        } catch (_: IllegalStateException) {
            // expected — see the dedicated `@Test(expected = ...)` case above for that assertion.
        }

        // Tier must not have moved despite the attempt — the transaction should have rolled
        // back entirely, not partially applied before the check() failure.
        assertEquals(Tier.OPERATOR, db.userDao().get(userId)!!.currentTier)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `accept upgrade rejects Iron so the calibration gate cannot be bypassed`() = runTest {
        seedUser(tier = Tier.WARDEN)

        useCase.acceptUpgrade(userId, Tier.IRON)
    }

    @Test
    fun `accept upgrade to a non-Iron tier moves tier and logs UPGRADE_ACCEPTED`() = runTest {
        seedUser(tier = Tier.RECRUIT)

        val event = useCase.acceptUpgrade(userId, Tier.OPERATOR)

        assertEquals(TierEventKind.UPGRADE_ACCEPTED, event.kind)
        assertEquals(Tier.OPERATOR, db.userDao().get(userId)!!.currentTier)
    }

    @Test
    fun `standard downgrade requires a reason and is logged as its own kind`() = runTest {
        seedUser(tier = Tier.WARDEN)

        val event = useCase.standardDowngrade(userId, Tier.OPERATOR, reasonNote = "Debt trajectory rule F3 fired")

        assertEquals(TierEventKind.STANDARD_DOWNGRADE, event.kind)
        assertEquals("Debt trajectory rule F3 fired", event.reasonNote)
    }

    @Test
    fun `select initial tier creates the user, logs INITIAL_SELECTION, and sets fromTier equal to toTier`() = runTest {
        assertNull(db.userDao().get(userId)) // no User row exists yet — this is the point of the test

        val event = useCase.selectInitialTier(userId, Tier.OPERATOR, onboardingConsentVersion = "v1")

        assertEquals(TierEventKind.INITIAL_SELECTION, event.kind)
        assertEquals(Tier.OPERATOR, event.fromTier) // no real "prior tier" exists — see TierEventKind kdoc
        assertEquals(Tier.OPERATOR, event.toTier)
        assertNull(event.reasonNote)

        val user = db.userDao().get(userId)!!
        assertEquals(Tier.OPERATOR, user.currentTier)
        assertEquals("v1", user.onboardingConsentVersion)
        assertEquals(user.tierSelectedAt, user.tierActivationAt) // no calibration lag outside Iron
    }

    @Test
    fun `select initial tier accepts Recruit, Operator, and Warden`() = runTest {
        for (tier in listOf(Tier.RECRUIT, Tier.OPERATOR, Tier.WARDEN)) {
            val freshUserId = UUID.randomUUID()
            val event = useCase.selectInitialTier(freshUserId, tier, onboardingConsentVersion = "v1")
            assertEquals(tier, event.toTier)
            assertEquals(tier, db.userDao().get(freshUserId)!!.currentTier)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `select initial tier rejects Iron with no exception path, per PRD §12_6`() = runTest {
        useCase.selectInitialTier(userId, Tier.IRON, onboardingConsentVersion = "v1")
    }

    @Test
    fun `select initial tier rejecting Iron leaves no user row created`() = runTest {
        try {
            useCase.selectInitialTier(userId, Tier.IRON, onboardingConsentVersion = "v1")
        } catch (_: IllegalArgumentException) {
            // expected — asserted by the dedicated @Test(expected = ...) case above.
        }

        // The rejection happens via require() before the transaction body runs at all, so
        // no User row should exist afterward — not a half-created user stuck without a
        // valid tier.
        assertNull(db.userDao().get(userId))
    }

    @Test
    fun `tier events are queryable in occurred-at order`() = runTest {
        seedUser(tier = Tier.OPERATOR)
        useCase.explicitDowngrade(userId, Tier.RECRUIT)
        useCase.acceptUpgrade(userId, Tier.OPERATOR)

        val events = db.tierDao().eventsFor(userId)

        assertEquals(2, events.size)
        assertTrue(events[0].occurredAt <= events[1].occurredAt)
    }
}
