package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.TierEventKind
import com.disciplineos.data.entity.User
import com.disciplineos.data.ledger.LedgerEntry
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.HypothesisReputationDecayPolicy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * ROADMAP.md Phase 1, final exit-criterion item — see `ApplyReputationDecayUseCase`'s own
 * kdoc for the full design reasoning (decay vs. recovery as distinct formula terms, the
 * crisis-stabilization pause judgment call). This test file exercises the placeholder
 * [HypothesisReputationDecayPolicy] values directly (1.0 decay / 1.5 recovery) — if that
 * policy's numbers ever change post-pilot, these assertions need updating too, which is a
 * deliberate coupling: a test that used its own made-up numbers instead would silently stop
 * testing the real (if placeholder) policy in use.
 */
@RunWith(RobolectricTestRunner::class)
class ApplyReputationDecayUseCaseTest {

    private lateinit var db: DisciplineOsDatabase
    private lateinit var useCase: ApplyReputationDecayUseCase

    private val userId = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        useCase = ApplyReputationDecayUseCase(
            database = db,
            userDao = db.userDao(),
            missionDao = db.missionDao(),
            ledgerDao = db.ledgerDao(),
            tierTransitionUseCase = TierTransitionUseCase(
                database = db,
                userDao = db.userDao(),
                tierDao = db.tierDao(),
                missionDao = db.missionDao(),
            ),
            policy = HypothesisReputationDecayPolicy(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedUser(
        debtAccrualPausedUntil: Instant? = null,
        tier: Tier = Tier.OPERATOR,
        consecutiveDaysBelowFloor: Int = 0,
    ) {
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = tier,
                tierSelectedAt = Instant.now(),
                tierActivationAt = Instant.now(),
                onboardingConsentVersion = "v1",
                debtAccrualPausedUntil = debtAccrualPausedUntil,
                consecutiveDaysBelowFloor = consecutiveDaysBelowFloor,
            )
        )
    }

    /** Directly seeds a Reputation ledger entry so `currentValue()` reflects [value] without going through decay math. */
    private suspend fun seedReputationValue(value: Double) {
        db.ledgerDao().insert(
            LedgerEntry(
                id = UUID.randomUUID(),
                userId = userId,
                violationId = null,
                metric = LedgerMetric.REPUTATION,
                delta = value,
                appliedAt = Instant.now().minus(10, ChronoUnit.DAYS),
            )
        )
    }

    private suspend fun seedMission(actualStart: Instant, status: MissionStatus) {
        db.missionDao().insert(
            EnforcementSession(
                id = UUID.randomUUID(),
                userId = userId,
                goalMissionId = null,
                scheduledStart = null,
                actualStart = actualStart,
                actualEnd = actualStart.plusSeconds(1800),
                plannedDurationMin = 30,
                status = status,
                allowlist = emptyList(),
                blocklist = emptyList(),
                missionProfileId = UUID.randomUUID(),
            )
        )
    }

    @Test
    fun `a missed day with a violated mission and no completed mission writes negative decay`() = runTest {
        seedUser()
        val since = Instant.now().minus(2, ChronoUnit.DAYS)
        seedMission(Instant.now().minus(1, ChronoUnit.DAYS), MissionStatus.VIOLATED)

        val entries = useCase.execute(userId, since).entries

        assertEquals(1, entries.size)
        assertEquals(LedgerMetric.REPUTATION, entries[0].metric)
        assertEquals(-1.0, entries[0].delta, 0.0001) // HypothesisReputationDecayPolicy: 1.0 * 1 missed day
        assertEquals(-1.0, db.ledgerDao().currentValue(userId, LedgerMetric.REPUTATION), 0.0001)
    }

    @Test
    fun `a completed mission on the same day as a violation is not counted as a missed day`() = runTest {
        seedUser()
        val since = Instant.now().minus(2, ChronoUnit.DAYS)
        val today = Instant.now().minus(1, ChronoUnit.DAYS)
        seedMission(today, MissionStatus.VIOLATED)
        seedMission(today, MissionStatus.COMPLETED)

        val entries = useCase.execute(userId, since).entries

        // Only the recovery credit should be written — the day has a completed Mission, so
        // it's excluded from missedDaysSince() regardless of the co-occurring violation.
        assertEquals(1, entries.size)
        assertTrue(entries[0].delta > 0)
        assertEquals(1.5, db.ledgerDao().currentValue(userId, LedgerMetric.REPUTATION), 0.0001)
    }

    @Test
    fun `completed missions write positive recovery credit, one credit each`() = runTest {
        seedUser()
        val since = Instant.now().minus(3, ChronoUnit.DAYS)
        seedMission(Instant.now().minus(2, ChronoUnit.DAYS), MissionStatus.COMPLETED)
        seedMission(Instant.now().minus(1, ChronoUnit.DAYS), MissionStatus.COMPLETED)

        val entries = useCase.execute(userId, since).entries

        assertEquals(1, entries.size) // one aggregated recovery entry, not one per Mission
        assertEquals(3.0, entries[0].delta, 0.0001) // 1.5 * 2 completed missions
    }

    @Test
    fun `no missions in the window writes nothing`() = runTest {
        seedUser()
        val since = Instant.now().minus(1, ChronoUnit.DAYS)

        val entries = useCase.execute(userId, since).entries

        assertEquals(0, entries.size)
    }

    @Test
    fun `decay is suppressed during an active crisis stabilization pause, recovery is not`() = runTest {
        val now = Instant.now()
        seedUser(debtAccrualPausedUntil = now.plus(12, ChronoUnit.HOURS))
        val since = now.minus(3, ChronoUnit.DAYS)
        // Isolated violated-only day (no completed Mission that day) — would normally count
        // as a missed day and produce a negative decay entry; the pause should suppress it.
        seedMission(now.minus(2, ChronoUnit.DAYS), MissionStatus.VIOLATED)
        // Separate day, completed — recovery credit should still be written despite the pause.
        seedMission(now.minus(1, ChronoUnit.DAYS), MissionStatus.COMPLETED)

        val entries = useCase.execute(userId, since, now).entries

        // Only recovery should be written; decay must be fully suppressed while the pause is
        // active, per the class kdoc's crisis-stabilization reasoning.
        assertEquals(1, entries.size)
        assertTrue(entries[0].delta > 0)
    }

    @Test
    fun `decay resumes once the stabilization pause has expired`() = runTest {
        val now = Instant.now()
        seedUser(debtAccrualPausedUntil = now.minus(1, ChronoUnit.HOURS)) // already expired
        val since = now.minus(2, ChronoUnit.DAYS)
        seedMission(now.minus(1, ChronoUnit.DAYS), MissionStatus.VIOLATED)

        val entries = useCase.execute(userId, since, now).entries

        assertEquals(1, entries.size)
        assertTrue(entries[0].delta < 0)
    }

    // --- §5.9 demotion_triggered ------------------------------------------------------

    @Test
    fun `below-floor day increments the counter but does not demote before N is reached`() = runTest {
        // OPERATOR's floor band is INCONSISTENT (21). Seed Reputation at 15 (UNDISCIPLINED,
        // below floor), no prior missed/completed missions so decay math itself writes
        // nothing this call — isolates the demotion-counter behavior.
        seedUser(tier = Tier.OPERATOR, consecutiveDaysBelowFloor = 0)
        seedReputationValue(15.0)
        val since = Instant.now().minus(1, ChronoUnit.DAYS)

        val result = useCase.execute(userId, since)

        assertNull(result.demotionEvent)
        assertEquals(1, db.userDao().get(userId)!!.consecutiveDaysBelowFloor)
        assertEquals(Tier.OPERATOR, db.userDao().get(userId)!!.currentTier)
    }

    @Test
    fun `reaching N consecutive below-floor days fires a standard downgrade and resets the counter`() = runTest {
        // Already at 2 consecutive days below floor; this call is the 3rd (N=3 per §5.9).
        seedUser(tier = Tier.OPERATOR, consecutiveDaysBelowFloor = 2)
        seedReputationValue(15.0) // still below OPERATOR's INCONSISTENT floor
        val since = Instant.now().minus(1, ChronoUnit.DAYS)

        val result = useCase.execute(userId, since)

        requireNotNull(result.demotionEvent)
        assertEquals(TierEventKind.STANDARD_DOWNGRADE, result.demotionEvent!!.kind)
        assertEquals(Tier.OPERATOR, result.demotionEvent!!.fromTier)
        assertEquals(Tier.RECRUIT, result.demotionEvent!!.toTier)

        val user = db.userDao().get(userId)!!
        assertEquals(Tier.RECRUIT, user.currentTier)
        assertEquals(0, user.consecutiveDaysBelowFloor) // reset after firing

        val events = db.tierDao().eventsFor(userId)
        assertEquals(1, events.size)
    }

    @Test
    fun `at-or-above floor resets the counter even after prior below-floor days`() = runTest {
        seedUser(tier = Tier.OPERATOR, consecutiveDaysBelowFloor = 2)
        seedReputationValue(25.0) // at/above INCONSISTENT (21) floor
        val since = Instant.now().minus(1, ChronoUnit.DAYS)

        val result = useCase.execute(userId, since)

        assertNull(result.demotionEvent)
        assertEquals(0, db.userDao().get(userId)!!.consecutiveDaysBelowFloor)
        assertEquals(Tier.OPERATOR, db.userDao().get(userId)!!.currentTier)
    }

    @Test
    fun `recruit has no floor to fall below and never demotes further`() = runTest {
        seedUser(tier = Tier.RECRUIT, consecutiveDaysBelowFloor = 5)
        seedReputationValue(0.0)
        val since = Instant.now().minus(1, ChronoUnit.DAYS)

        val result = useCase.execute(userId, since)

        assertNull(result.demotionEvent)
        assertEquals(Tier.RECRUIT, db.userDao().get(userId)!!.currentTier)
    }
}
