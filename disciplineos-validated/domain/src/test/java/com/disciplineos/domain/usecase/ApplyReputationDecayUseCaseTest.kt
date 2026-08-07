package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.HypothesisReputationDecayPolicy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
            policy = HypothesisReputationDecayPolicy(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedUser(debtAccrualPausedUntil: Instant? = null) {
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = Tier.OPERATOR,
                tierSelectedAt = Instant.now(),
                tierActivationAt = Instant.now(),
                onboardingConsentVersion = "v1",
                debtAccrualPausedUntil = debtAccrualPausedUntil,
            )
        )
    }

    private suspend fun seedMission(actualStart: Instant, status: MissionStatus) {
        db.missionDao().insert(
            Mission(
                id = UUID.randomUUID(),
                userId = userId,
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

        val entries = useCase.execute(userId, since)

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

        val entries = useCase.execute(userId, since)

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

        val entries = useCase.execute(userId, since)

        assertEquals(1, entries.size) // one aggregated recovery entry, not one per Mission
        assertEquals(3.0, entries[0].delta, 0.0001) // 1.5 * 2 completed missions
    }

    @Test
    fun `no missions in the window writes nothing`() = runTest {
        seedUser()
        val since = Instant.now().minus(1, ChronoUnit.DAYS)

        val entries = useCase.execute(userId, since)

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

        val entries = useCase.execute(userId, since, now)

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

        val entries = useCase.execute(userId, since, now)

        assertEquals(1, entries.size)
        assertTrue(entries[0].delta < 0)
    }
}
