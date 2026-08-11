package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.entity.EnforcementSession
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

/**
 * ROADMAP.md Phase 1 exit criterion: "Disputing → overturning a Violation correctly reverses
 * ledger entries AND excludes it from Reliability Index AND doesn't leave Debt/Reputation
 * briefly wrong mid-flow."
 *
 * Same caveat as RecordViolationUseCaseTest: written against real DAO/entity signatures,
 * carefully cross-checked by hand, but **never executed** — no Android/Robolectric runtime
 * available in this sandbox. Run for real before merging.
 */
@RunWith(RobolectricTestRunner::class)
class ResolveDisputeUseCaseTest {

    private lateinit var db: DisciplineOsDatabase
    private lateinit var recordViolation: RecordViolationUseCase
    private lateinit var resolveDispute: ResolveDisputeUseCase

    private val userId = UUID.randomUUID()
    private val missionId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        recordViolation = RecordViolationUseCase(
            database = db,
            violationDao = db.violationDao(),
            missionDao = db.missionDao(),
            userDao = db.userDao(),
            ledgerDao = db.ledgerDao(),
            consequencePolicy = HypothesisConsequencePolicy(),
        )
        resolveDispute = ResolveDisputeUseCase(
            database = db,
            violationDao = db.violationDao(),
            ledgerDao = db.ledgerDao(),
        )

        db.userDao().insert(
            User(
                id = userId,
                createdAt = Instant.now(),
                currentTier = Tier.WARDEN,
                tierSelectedAt = Instant.now(),
                tierActivationAt = Instant.now(),
                onboardingConsentVersion = "v1",
            )
        )
        db.missionDao().insert(
            EnforcementSession(
                id = missionId,
                userId = userId,
                goalMissionId = null,
                scheduledStart = null,
                actualStart = Instant.now(),
                actualEnd = Instant.now(),
                plannedDurationMin = 30,
                status = MissionStatus.VIOLATED,
                allowlist = emptyList(),
                blocklist = emptyList(),
                missionProfileId = UUID.randomUUID(),
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun recordAndFileDispute(): Violation {
        val violation = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.BLOCKLIST_ACCESS,
        )
        val recordResult = recordViolation.execute(violation)
        assertNotNull(recordResult.debtEntry)
        assertNotNull(recordResult.reputationEntry)

        resolveDispute.fileDispute(violation.id)
        return violation
    }

    @Test
    fun `filing a dispute pauses already-written ledger entries`() = runTest {
        val violation = recordAndFileDispute()

        // Entries exist but no longer contribute to the running total while paused.
        assertEquals(0.0, db.ledgerDao().currentValue(userId, LedgerMetric.DEBT), 0.0001)
        assertEquals(0.0, db.ledgerDao().currentValue(userId, LedgerMetric.REPUTATION), 0.0001)

        val flagged = db.violationDao().get(violation.id)!!
        assertEquals(DisputeStatus.FLAGGED, flagged.disputeStatus)
        assertTrue(flagged.consequencePaused)
    }

    @Test
    fun `UPHELD resumes the original ledger entries unchanged`() = runTest {
        val violation = recordAndFileDispute()

        resolveDispute.execute(violation.id, DisputeStatus.UPHELD, reason = "re-checked, was accurate")

        // WARDEN tier (2.5x) * BLOCKLIST_ACCESS (30.0 debt / -3.0 reputation)
        assertEquals(75.0, db.ledgerDao().currentValue(userId, LedgerMetric.DEBT), 0.0001)
        assertEquals(-7.5, db.ledgerDao().currentValue(userId, LedgerMetric.REPUTATION), 0.0001)

        val resolved = db.violationDao().get(violation.id)!!
        assertEquals(DisputeStatus.UPHELD, resolved.disputeStatus)
    }

    @Test
    fun `OVERTURNED reverses the ledger entries permanently`() = runTest {
        val violation = recordAndFileDispute()

        resolveDispute.execute(violation.id, DisputeStatus.OVERTURNED, reason = "confirmed misclassified")

        assertEquals(0.0, db.ledgerDao().currentValue(userId, LedgerMetric.DEBT), 0.0001)
        assertEquals(0.0, db.ledgerDao().currentValue(userId, LedgerMetric.REPUTATION), 0.0001)
        assertTrue(db.ledgerDao().activeEntriesForViolation(violation.id).isEmpty())

        val resolved = db.violationDao().get(violation.id)!!
        assertEquals(DisputeStatus.OVERTURNED, resolved.disputeStatus)
    }

    @Test(expected = IllegalStateException::class)
    fun `resolving a violation with no active dispute fails loudly`() = runTest {
        val violation = Violation(
            id = UUID.randomUUID(),
            missionId = missionId,
            detectedAt = Instant.now(),
            type = ViolationType.NON_START,
        )
        recordViolation.execute(violation)
        // never disputed — disputeStatus is still NONE
        resolveDispute.execute(violation.id, DisputeStatus.UPHELD, reason = "n/a")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolving with a non-terminal outcome is rejected`() = runTest {
        val violation = recordAndFileDispute()
        resolveDispute.execute(violation.id, DisputeStatus.UNDER_REVIEW, reason = "n/a")
    }
}
