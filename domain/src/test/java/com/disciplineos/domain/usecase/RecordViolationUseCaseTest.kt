package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.entity.ViolationType
import com.disciplineos.data.ledger.LedgerMetric
import com.disciplineos.domain.policy.HypothesisConsequencePolicy
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
import java.util.UUID

/**
 * ROADMAP.md Phase 1 exit criteria, specifically covering:
 * - "Recording a Violation and applying its Debt/Reputation penalty happens in one
 *   transactional use-case, never as separate uncoordinated DAO calls"
 * - "Shared-cause guard (§27.2) has a real implementation ... needs a test proving two
 *   penalties from the same rootCauseClusterId don't double-apply"
 *
 * Uses an in-memory (unencrypted) Room database via Robolectric rather than
 * [DisciplineOsDatabase.build]'s SQLCipher-backed factory — SQLCipher needs a real device/
 * emulator's native library, which isn't available under Robolectric. This is a deliberate,
 * narrower substitution (swap the open helper factory, keep everything else about the
 * schema and DAOs real) rather than mocking the DAOs themselves, so the test still exercises
 * real SQL, real @Transaction/withTransaction behavior, and the real Converters.
 *
 * NOTE: written but not executed in this environment — there's no Android/Robolectric
 * runtime available in this sandbox to run `./gradlew :domain:test` against. Run it for real
 * before merging.
 */
@RunWith(RobolectricTestRunner::class)
class RecordViolationUseCaseTest {

    private lateinit var db: DisciplineOsDatabase
    private lateinit var useCase: RecordViolationUseCase

    private val userId = UUID.randomUUID()
    private val missionId = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        useCase = RecordViolationUseCase(
            database = db,
            violationDao = db.violationDao(),
            missionDao = db.missionDao(),
            userDao = db.userDao(),
            ledgerDao = db.ledgerDao(),
            consequencePolicy = HypothesisConsequencePolicy(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedUserAndMission(tier: Tier = Tier.OPERATOR, missionStatus: MissionStatus = MissionStatus.VIOLATED) {
        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = tier,
                tierSelectedAt = Instant.now(),
                tierActivationAt = Instant.now(),
                onboardingConsentVersion = "v1",
            )
        )
        db.missionDao().insert(
            Mission(
                id = missionId,
                userId = userId,
                scheduledStart = null,
                actualStart = Instant.now(),
                actualEnd = Instant.now(),
                plannedDurationMin = 30,
                status = missionStatus,
                allowlist = emptyList(),
                blocklist = emptyList(),
                missionProfileId = UUID.randomUUID(),
            )
        )
    }

    @Test
    fun `recording a violation writes exactly one Debt and one Reputation entry`() = runTest {
        seedUserAndMission()
        val violation = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.EARLY_EXIT,
        )

        val result = useCase.execute(violation)

        assertTrue(result.debtEntry != null)
        assertTrue(result.reputationEntry != null)
        assertNull(result.skippedReason)
        // OPERATOR multiplier (1.5) * EARLY_EXIT base (20.0 debt / -2.0 reputation)
        assertEquals(30.0, result.debtEntry!!.delta, 0.0001)
        assertEquals(-3.0, result.reputationEntry!!.delta, 0.0001)
        assertEquals(30.0, db.ledgerDao().currentValue(userId, LedgerMetric.DEBT), 0.0001)
        assertEquals(-3.0, db.ledgerDao().currentValue(userId, LedgerMetric.REPUTATION), 0.0001)
    }

    @Test
    fun `shared-cause guard blocks a second penalty from the same root cause cluster`() = runTest {
        seedUserAndMission()
        val clusterId = UUID.randomUUID()

        val first = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.EARLY_EXIT,
            rootCauseClusterId = clusterId,
        )
        val second = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.BLOCKLIST_ACCESS,
            rootCauseClusterId = clusterId,
        )

        val firstResult = useCase.execute(first)
        val secondResult = useCase.execute(second)

        assertTrue(firstResult.debtEntry != null)
        assertTrue(firstResult.reputationEntry != null)

        // Second violation, same cluster: both metrics already have an active entry from
        // `first`, so neither should write again.
        assertNull(secondResult.debtEntry)
        assertNull(secondResult.reputationEntry)
        assertEquals(RecordViolationUseCase.SkipReason.SHARED_CAUSE_GUARD_BOTH, secondResult.skippedReason)

        // Ledger total should reflect only the first violation's penalties, not both.
        assertEquals(
            firstResult.debtEntry!!.delta,
            db.ledgerDao().currentValue(userId, LedgerMetric.DEBT),
            0.0001,
        )
    }

    @Test
    fun `violations in different clusters both apply penalties`() = runTest {
        seedUserAndMission()

        val first = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.EARLY_EXIT,
            rootCauseClusterId = UUID.randomUUID(),
        )
        val second = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.EARLY_EXIT,
            rootCauseClusterId = UUID.randomUUID(),
        )

        useCase.execute(first)
        val secondResult = useCase.execute(second)

        assertTrue(secondResult.debtEntry != null)
        assertTrue(secondResult.reputationEntry != null)
        assertNull(secondResult.skippedReason)
    }

    @Test
    fun `a dispute-flagged violation is recorded but writes no ledger entries`() = runTest {
        seedUserAndMission()
        val violation = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.NON_START,
            disputeStatus = DisputeStatus.FLAGGED,
            disputeFlaggedAt = Instant.now(),
            consequencePaused = true,
        )

        val result = useCase.execute(violation)

        assertNull(result.debtEntry)
        assertNull(result.reputationEntry)
        assertEquals(RecordViolationUseCase.SkipReason.CONSEQUENCE_PAUSED, result.skippedReason)
        assertEquals(0.0, db.ledgerDao().currentValue(userId, LedgerMetric.DEBT), 0.0001)
        assertEquals(0.0, db.ledgerDao().currentValue(userId, LedgerMetric.REPUTATION), 0.0001)
        // The Violation itself is still persisted even though consequences are frozen.
        assertTrue(db.violationDao().get(violation.id) != null)
    }

    @Test
    fun `shared-cause guard does not block a sibling outside the 3-day window`() = runTest {
        seedUserAndMission()
        val clusterId = UUID.randomUUID()
        val now = Instant.now()

        val first = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = now.minus(java.time.Duration.ofDays(4)),
            type = ViolationType.EARLY_EXIT,
            rootCauseClusterId = clusterId,
        )
        val second = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = now,
            type = ViolationType.BLOCKLIST_ACCESS,
            rootCauseClusterId = clusterId,
        )

        useCase.execute(first)
        val secondResult = useCase.execute(second)

        // 4 days apart, outside the 3-day window — the guard must not treat this as the
        // same incident, so the second violation applies its own penalty.
        assertTrue(secondResult.debtEntry != null)
        assertTrue(secondResult.reputationEntry != null)
        assertNull(secondResult.skippedReason)
    }

    @Test
    fun `shared-cause guard blocks a sibling just inside the 3-day window`() = runTest {
        seedUserAndMission()
        val clusterId = UUID.randomUUID()
        val now = Instant.now()

        val first = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = now.minus(java.time.Duration.ofDays(3)).plusSeconds(60),
            type = ViolationType.EARLY_EXIT,
            rootCauseClusterId = clusterId,
        )
        val second = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = now,
            type = ViolationType.BLOCKLIST_ACCESS,
            rootCauseClusterId = clusterId,
        )

        useCase.execute(first)
        val secondResult = useCase.execute(second)

        assertNull(secondResult.debtEntry)
        assertNull(secondResult.reputationEntry)
        assertEquals(RecordViolationUseCase.SkipReason.SHARED_CAUSE_GUARD_BOTH, secondResult.skippedReason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a crisis-exit mission must not go through this use-case`() = runTest {
        seedUserAndMission(missionStatus = MissionStatus.ABORTED_CRISIS_EXIT)
        val violation = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.NON_START,
        )

        useCase.execute(violation)
    }
}
