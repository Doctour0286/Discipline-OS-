package com.disciplineos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.CadenceType
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.LifecycleStage
import com.disciplineos.data.entity.MeasurementSource
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.PeriodType
import com.disciplineos.data.entity.ResetMode
import com.disciplineos.data.entity.TriggerCueType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

/**
 * Batch G5. See [CreateConstraintTriggerUseCase]'s own kdoc for the full base doc §6.2
 * correctness-risk reasoning this test exists to guard: an `APP_OPEN` Trigger on a Constraint
 * mission must produce exactly one [com.disciplineos.data.entity.MissionPeriod] (the real
 * blocking-template row) plus one [com.disciplineos.data.entity.Trigger] (the descriptive row),
 * never a second independent enforcement path.
 */
@RunWith(RobolectricTestRunner::class)
class CreateConstraintTriggerUseCaseTest {

    private lateinit var db: DisciplineOsDatabase
    private lateinit var useCase: CreateConstraintTriggerUseCase

    private val userId = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisciplineOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        useCase = CreateConstraintTriggerUseCase(
            database = db,
            goalMissionDao = db.goalMissionDao(),
            missionPeriodDao = db.missionPeriodDao(),
            missionProfileDao = db.missionProfileDao(),
            triggerDao = db.triggerDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedGoalMission(archetype: MissionArchetype): GoalMission {
        val mission = GoalMission(
            id = UUID.randomUUID(),
            userId = userId,
            title = "Never open the gambling app",
            archetype = archetype,
            targetDirection = null,
            targetValue = null,
            unit = null,
            cadenceType = CadenceType.NONE,
            resetMode = ResetMode.ROLLING_WINDOW,
            measurementSource = MeasurementSource.AUTOMATIC,
            lifecycleStage = LifecycleStage.OBSERVING,
            adherenceScore = null,
            adherenceWindow = null,
            createdAt = Instant.now(),
            archivedAt = null,
        )
        db.goalMissionDao().insert(mission)
        return mission
    }

    @Test
    fun `creates a MissionProfile, an ALWAYS_ON MissionPeriod, and an APP_OPEN Trigger in one call`() = runTest {
        val mission = seedGoalMission(MissionArchetype.CONSTRAINT)

        // Room's Instant converter round-trips through epoch-millis (Converters.kt), which
        // truncates any sub-millisecond precision Instant.now() may carry. Passing an
        // already-millis-truncated `now` here means `result.*` (in-memory) and the DB
        // round-trip below compare equal on `createdAt` — using a bare `Instant.now()` would
        // make this assertion flaky/failing depending on JVM clock precision, which is exactly
        // what broke this test in CI.
        val now = Instant.ofEpochMilli(Instant.now().toEpochMilli())

        val result = useCase.execute(
            missionId = mission.id,
            packageId = "com.example.gambling",
            cueDescription = "whenever I feel bored at night",
            now = now,
        )

        // The MissionProfile is scoped to exactly the one blocked package — see class kdoc's
        // MissionProfile-sourcing section for why this doesn't reuse the user's general profile.
        assertEquals(listOf("com.example.gambling"), result.missionProfile.blocklist)
        assertTrue(result.missionProfile.allowlist.isEmpty())

        // The MissionPeriod is the real blocking-template row — ALWAYS_ON, referencing the new
        // profile, not a second/parallel enforcement path.
        assertEquals(PeriodType.ALWAYS_ON, result.missionPeriod.periodType)
        assertEquals(result.missionProfile.id, result.missionPeriod.enforcementProfileId)
        assertEquals(mission.id, result.missionPeriod.missionId)

        // The Trigger is descriptive only — carries the person's own cue text and links back
        // to the MissionPeriod that actually does the blocking.
        assertEquals(TriggerCueType.APP_OPEN, result.trigger.cueType)
        assertEquals("com.example.gambling", result.trigger.cueTriggerPackageId)
        assertEquals("whenever I feel bored at night", result.trigger.cueDescription)
        assertEquals(result.missionPeriod.id, result.trigger.missionPeriodId)

        // All three rows are actually persisted, not just returned in-memory.
        assertEquals(result.missionProfile, db.missionProfileDao().get(result.missionProfile.id))
        assertEquals(
            listOf(result.missionPeriod),
            db.missionPeriodDao().forMission(mission.id),
        )
        assertEquals(listOf(result.trigger), db.triggerDao().forMission(mission.id))
    }

    // Both exception tests below use `@Test(expected = ...)` directly on a `suspend fun =
    // runTest { ... }`, matching RecordViolationUseCaseTest's identical
    // `a crisis-exit mission must not go through this use-case` test — NOT `assertThrows`
    // wrapping a second, nested `runTest { }`. The nested-runTest form is what actually broke
    // CI here: Room's `withTransaction` dispatches its lambda through its own dispatcher, and
    // an exception thrown inside a nested TestScope doesn't propagate out of `assertThrows`
    // as the original exception type — it surfaces as a generic AssertionError instead. The
    // single-runTest form used elsewhere in this codebase (RecordViolationUseCaseTest) does
    // not have this problem, since JUnit's `expected=` catches the exception at the top level
    // of the same coroutine `runTest` already runs the test body in.

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non-CONSTRAINT mission rather than silently coercing it`() = runTest {
        val mission = seedGoalMission(MissionArchetype.BEHAVIOR_DRIVEN)

        // require(), not requireNotNull() — throws IllegalArgumentException, matching Kotlin's
        // own require/requireNotNull split and this project's identical usage of it elsewhere.
        useCase.execute(
            missionId = mission.id,
            packageId = "com.example.gambling",
            cueDescription = "whenever I feel bored at night",
        )
    }

    @Test
    fun `a rejected non-CONSTRAINT mission writes nothing`() = runTest {
        val mission = seedGoalMission(MissionArchetype.BEHAVIOR_DRIVEN)

        runCatching {
            useCase.execute(
                missionId = mission.id,
                packageId = "com.example.gambling",
                cueDescription = "whenever I feel bored at night",
            )
        }

        // Nothing should have been written — the transaction never got past the guard.
        assertTrue(db.missionPeriodDao().forMission(mission.id).isEmpty())
        assertTrue(db.triggerDao().forMission(mission.id).isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects a missing mission id`() = runTest {
        // checkNotNull(), not requireNotNull() — throws IllegalStateException, matching every
        // other "should be structurally impossible" guard in this codebase's *intent* (see
        // CreateConstraintTriggerUseCase's own kdoc: requireNotNull actually throws
        // IllegalArgumentException in Kotlin, which is what broke this exact test in CI —
        // fixed by switching the use-case to checkNotNull rather than loosening this
        // assertion).
        useCase.execute(
            missionId = UUID.randomUUID(),
            packageId = "com.example.gambling",
            cueDescription = "whenever I feel bored at night",
        )
    }
}
