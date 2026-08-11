package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.CadenceType
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.LifecycleStage
import com.disciplineos.data.entity.MeasurementSource
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.MissionLogEntry
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.ResetMode
import com.disciplineos.data.entity.TargetDirection
import com.disciplineos.domain.policy.HypothesisAdherenceDecayPolicy
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
 * ROADMAP.md §5.36, Batch G3 — see `ApplyAdherenceDecayUseCase`'s own kdoc for the full scope/
 * hit-rate/decay design. Exercises the placeholder `HypothesisAdherenceDecayPolicy` values
 * directly (7-day window, 0.7 hit-rate threshold, 2 consecutive windows, 10.0 decay) — same
 * deliberate coupling `ApplyReputationDecayUseCaseTest`'s own kdoc states for its policy: if
 * those numbers change post-pilot, these assertions need updating too, so a test that invented
 * its own numbers instead would silently stop testing the real (if placeholder) policy in use.
 */
@RunWith(RobolectricTestRunner::class)
class ApplyAdherenceDecayUseCaseTest {

    private lateinit var db: DisciplineOsDatabase
    private lateinit var useCase: ApplyAdherenceDecayUseCase

    private val userId = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        useCase = ApplyAdherenceDecayUseCase(
            database = db,
            goalMissionDao = db.goalMissionDao(),
            missionLogEntryDao = db.missionLogEntryDao(),
            enforcementSessionDao = db.enforcementSessionDao(),
            adherenceLedgerDao = db.adherenceLedgerDao(),
            policy = HypothesisAdherenceDecayPolicy(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Defaults to an OUTCOME_DRIVEN mission with a null adherenceWindow (so the policy's 7-day
     * default applies) and consecutiveWindowsBelowThreshold = 0, matching a freshly-created
     * GoalMission. Callers override only the fields a given test cares about.
     */
    private suspend fun seedGoalMission(
        id: UUID = UUID.randomUUID(),
        archetype: MissionArchetype = MissionArchetype.OUTCOME_DRIVEN,
        cadenceType: CadenceType = CadenceType.DAILY,
        targetDirection: TargetDirection? = TargetDirection.INCREASE,
        targetValue: Double? = 1.0,
        adherenceScore: Double? = null,
        adherenceWindow: Int? = null,
        consecutiveWindowsBelowThreshold: Int = 0,
    ): GoalMission {
        val mission = GoalMission(
            id = id,
            userId = userId,
            title = "Test mission",
            archetype = archetype,
            targetDirection = targetDirection,
            targetValue = targetValue,
            unit = null,
            cadenceType = cadenceType,
            resetMode = ResetMode.ROLLING_WINDOW,
            measurementSource = MeasurementSource.MANUAL_LOG,
            lifecycleStage = LifecycleStage.ENFORCING,
            adherenceScore = adherenceScore,
            adherenceWindow = adherenceWindow,
            consecutiveWindowsBelowThreshold = consecutiveWindowsBelowThreshold,
            createdAt = Instant.now(),
            archivedAt = null,
        )
        db.goalMissionDao().insert(mission)
        return mission
    }

    private suspend fun seedLogEntry(
        missionId: UUID,
        createdAt: Instant,
        numericValue: Double? = null,
        didOccur: Boolean? = null,
        note: String? = null,
    ) {
        db.missionLogEntryDao().insert(
            MissionLogEntry(
                id = UUID.randomUUID(),
                missionId = missionId,
                createdAt = createdAt,
                note = note,
                numericValue = numericValue,
                didOccur = didOccur,
            )
        )
    }

    private suspend fun seedEnforcementSession(missionId: UUID) {
        db.enforcementSessionDao().insert(
            EnforcementSession(
                id = UUID.randomUUID(),
                userId = userId,
                missionId = missionId,
                missionPeriodId = null,
                scheduledStart = null,
                actualStart = Instant.now(),
                actualEnd = Instant.now().plusSeconds(1800),
                plannedDurationMin = 30,
                status = MissionStatus.COMPLETED,
                allowlist = emptyList(),
                blocklist = emptyList(),
                missionProfileId = UUID.randomUUID(),
            )
        )
    }

    // --- Scope gating ------------------------------------------------------------------

    @Test
    fun `a nonexistent GoalMission is out of scope`() = runTest {
        val now = Instant.now()
        val result = useCase.execute(UUID.randomUUID(), now)

        assertFalse(result.inScope)
        assertEquals(emptyList<Any>(), result.entries)
        assertNull(result.hitRate)
        assertFalse(result.thresholdCrossing)
    }

    @Test
    fun `an OUTCOME_DRIVEN mission is always in scope`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(archetype = MissionArchetype.OUTCOME_DRIVEN)

        val result = useCase.execute(mission.id, now)

        assertTrue(result.inScope)
    }

    @Test
    fun `a CONSTRAINT mission is always in scope`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(archetype = MissionArchetype.CONSTRAINT, targetDirection = null, targetValue = null)

        val result = useCase.execute(mission.id, now)

        assertTrue(result.inScope)
    }

    @Test
    fun `a BEHAVIOR_DRIVEN mission with no attached EnforcementSession is in scope`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(archetype = MissionArchetype.BEHAVIOR_DRIVEN)

        val result = useCase.execute(mission.id, now)

        assertTrue(result.inScope)
    }

    @Test
    fun `a BEHAVIOR_DRIVEN mission with an attached EnforcementSession is out of scope`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(archetype = MissionArchetype.BEHAVIOR_DRIVEN)
        seedEnforcementSession(mission.id)

        val result = useCase.execute(mission.id, now)

        assertFalse(result.inScope)
        assertEquals(emptyList<Any>(), result.entries)
    }

    // --- Hit-rate computation ------------------------------------------------------------

    @Test
    fun `didOccur true counts as a hit regardless of numericValue`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = null, targetValue = null)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = true)

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `didOccur false is a miss even with a qualifying numericValue`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = TargetDirection.INCREASE, targetValue = 1.0)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = false, numericValue = 5.0)

        val result = useCase.execute(mission.id, now)

        assertEquals(0.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `INCREASE direction hits when numericValue is at or above target`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = TargetDirection.INCREASE, targetValue = 10.0)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), numericValue = 10.0)

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `INCREASE direction misses when numericValue is below target`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = TargetDirection.INCREASE, targetValue = 10.0)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), numericValue = 5.0)

        val result = useCase.execute(mission.id, now)

        assertEquals(0.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `DECREASE direction hits when numericValue is at or below target`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = TargetDirection.DECREASE, targetValue = 10.0)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), numericValue = 8.0)

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `MAINTAIN direction hits within tolerance and misses outside it`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = TargetDirection.MAINTAIN, targetValue = 100.0)
        // Within 10% tolerance (90-110)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), numericValue = 105.0)

        val withinResult = useCase.execute(mission.id, now)
        assertEquals(1.0, withinResult.hitRate!!, 0.0001)
    }

    @Test
    fun `MAINTAIN direction misses outside tolerance`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = TargetDirection.MAINTAIN, targetValue = 100.0)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), numericValue = 150.0)

        val result = useCase.execute(mission.id, now)

        assertEquals(0.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `a numericValue entry with no targetValue set is a hit by presence alone`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = TargetDirection.INCREASE, targetValue = null)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), numericValue = 1.0)

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `a note-only entry counts toward neither hits nor expected count`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = null, targetValue = null)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), note = "just a check-in")

        val result = useCase.execute(mission.id, now)

        // NONE cadence expects 1 entry; the note-only entry doesn't count as a hit, so 0/1.
        assertEquals(0.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `entries outside the window are excluded`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = null, targetValue = null, adherenceWindow = 7)
        seedLogEntry(mission.id, now.minus(30, ChronoUnit.DAYS), didOccur = true)

        val result = useCase.execute(mission.id, now)

        assertEquals(0.0, result.hitRate!!, 0.0001)
    }

    // --- Expected-entry count by cadence --------------------------------------------------

    @Test
    fun `DAILY cadence expects one entry per window day`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.DAILY, targetDirection = null, targetValue = null, adherenceWindow = 7)
        // 7 hits out of 7 expected (one per day) = 1.0
        for (i in 1..7) {
            seedLogEntry(mission.id, now.minus(i.toLong(), ChronoUnit.DAYS), didOccur = true)
        }

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `WEEKLY cadence expects ceil(windowDays div 7)`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.WEEKLY, targetDirection = null, targetValue = null, adherenceWindow = 7)
        // Expected = ceil(7/7.0) = 1. One hit -> 1/1 = 1.0
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = true)

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `NONE cadence expects only one entry total regardless of window length`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = null, targetValue = null, adherenceWindow = 30)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = true)

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    @Test
    fun `hit rate is capped at 1_0 even with more hits than expected`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = null, targetValue = null)
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = true)
        seedLogEntry(mission.id, now.minus(2, ChronoUnit.DAYS), didOccur = true)
        seedLogEntry(mission.id, now.minus(3, ChronoUnit.DAYS), didOccur = true)

        val result = useCase.execute(mission.id, now)

        assertEquals(1.0, result.hitRate!!, 0.0001)
    }

    // --- Decay on sustained miss windows ----------------------------------------------------

    @Test
    fun `a single missed window increments the counter but writes no ledger entry`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(
            cadenceType = CadenceType.DAILY,
            targetDirection = null,
            targetValue = null,
            adherenceWindow = 7,
            consecutiveWindowsBelowThreshold = 0,
        )
        // No log entries at all in the window -> hitRate 0.0, below 0.7 threshold.

        val result = useCase.execute(mission.id, now)

        assertFalse(result.thresholdCrossing)
        assertEquals(0, result.entries.size)
        assertEquals(1, db.goalMissionDao().get(mission.id)!!.consecutiveWindowsBelowThreshold)
    }

    @Test
    fun `reaching the consecutive-windows threshold writes a decay entry and resets the counter`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(
            cadenceType = CadenceType.DAILY,
            targetDirection = null,
            targetValue = null,
            adherenceWindow = 7,
            consecutiveWindowsBelowThreshold = 1, // already 1 below; this call is the 2nd (policy threshold = 2)
        )

        val result = useCase.execute(mission.id, now)

        assertTrue(result.thresholdCrossing)
        assertEquals(1, result.entries.size)
        assertEquals(-10.0, result.entries[0].delta, 0.0001) // HypothesisAdherenceDecayPolicy: 10.0 per crossing
        assertTrue(result.entries[0].thresholdCrossing)

        val updated = db.goalMissionDao().get(mission.id)!!
        assertEquals(0, updated.consecutiveWindowsBelowThreshold) // reset after firing
        assertEquals(-10.0, updated.adherenceScore!!, 0.0001)
    }

    @Test
    fun `a met window resets a nonzero counter to zero and writes nothing`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(
            cadenceType = CadenceType.NONE,
            targetDirection = null,
            targetValue = null,
            adherenceWindow = 7,
            consecutiveWindowsBelowThreshold = 1,
        )
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = true) // NONE expects 1 -> hitRate 1.0

        val result = useCase.execute(mission.id, now)

        assertFalse(result.thresholdCrossing)
        assertEquals(0, result.entries.size)
        assertEquals(0, db.goalMissionDao().get(mission.id)!!.consecutiveWindowsBelowThreshold)
    }

    @Test
    fun `the first call establishes adherenceScore as 0_0 rather than leaving it null`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(
            cadenceType = CadenceType.NONE,
            targetDirection = null,
            targetValue = null,
            adherenceScore = null,
        )
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = true)

        useCase.execute(mission.id, now)

        assertEquals(0.0, db.goalMissionDao().get(mission.id)!!.adherenceScore!!, 0.0001)
    }

    @Test
    fun `adherenceScore always reflects the current ledger sum even when this call writes nothing`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(cadenceType = CadenceType.NONE, targetDirection = null, targetValue = null)
        // Directly seed a prior ledger entry, bypassing decay math, to simulate an existing score.
        db.adherenceLedgerDao().insert(
            com.disciplineos.data.adherence.AdherenceLedgerEntry(
                id = UUID.randomUUID(),
                goalMissionId = mission.id,
                delta = -10.0,
                appliedAt = now.minus(20, ChronoUnit.DAYS),
                thresholdCrossing = true,
            )
        )
        seedLogEntry(mission.id, now.minus(1, ChronoUnit.DAYS), didOccur = true) // met window this call

        useCase.execute(mission.id, now)

        // No new entry written this call, but adherenceScore should reflect the prior -10.0 sum.
        assertEquals(-10.0, db.goalMissionDao().get(mission.id)!!.adherenceScore!!, 0.0001)
    }

    @Test
    fun `never writes to the shared LedgerDao`() = runTest {
        val now = Instant.now()
        val mission = seedGoalMission(
            cadenceType = CadenceType.DAILY,
            targetDirection = null,
            targetValue = null,
            adherenceWindow = 7,
            consecutiveWindowsBelowThreshold = 1,
        )

        useCase.execute(mission.id, now) // fires a threshold crossing

        assertEquals(0.0, db.ledgerDao().currentValue(userId, com.disciplineos.data.ledger.LedgerMetric.REPUTATION), 0.0001)
        assertEquals(0.0, db.ledgerDao().currentValue(userId, com.disciplineos.data.ledger.LedgerMetric.DEBT), 0.0001)
    }
}
