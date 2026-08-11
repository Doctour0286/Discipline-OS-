package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.Milestone
import com.disciplineos.data.entity.MissionLogEntry
import com.disciplineos.data.entity.MissionPeriod
import com.disciplineos.data.entity.Trigger
import java.util.UUID

/**
 * Backs [GoalMission] (Goal-Oriented Mission Model, ROADMAP.md §5.32). Kept as its own `@Dao`
 * matching every other entity-scoped DAO in this file/module (`TierDao`, `MissionProfileDao`) —
 * a distinct table with its own query shape, not folded into `MissionDao` just because the two
 * entities are related (same reasoning `MissionProfileDao`'s own kdoc already states for the
 * identical question about itself).
 *
 * **Deliberately minimal for Batch G1** — this batch is additive schema only, zero behavior
 * change (`BUILD_PLAN.md` Batch G1's own stated scope). `insert`/`get`/`update` and the two
 * lookups Batch G2 will need immediately (`FirstMissionSchedulingFragment`'s real fix,
 * `06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §3) are included now since G2 is the very
 * next batch and there's no real call site benefit to splitting "add the DAO" from "add the two
 * obvious methods its first real caller needs" across two separate batches. Anything past that
 * — e.g. per-status filtering, a real profile-picker-style listing — is deferred to whichever
 * batch (G3–G6) actually needs it first, matching `MissionProfileDao`'s stated "no `@Update` yet"
 * precedent for the same kind of restraint.
 */
@Dao
interface GoalMissionDao {
    @Insert
    suspend fun insert(goalMission: GoalMission)

    @Update
    suspend fun update(goalMission: GoalMission)

    @Query("SELECT * FROM goal_missions WHERE id = :id")
    suspend fun get(id: UUID): GoalMission?

    /**
     * Backs Batch G2's re-entry-guard requirement — same "did this user already create one"
     * check pattern `MissionProfileDao.mostRecentFor`/`TierSelectionFragment`'s own re-entry
     * guards already use elsewhere in this codebase (see those kdocs for the shared reasoning),
     * applied here to onboarding's first-goal creation instead. `LIMIT 1` matches
     * `MissionProfileDao.mostRecentFor`'s identical single-row assumption for the same reason:
     * no picker UI exists yet to make "which of several" a real question.
     */
    @Query("SELECT * FROM goal_missions WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    suspend fun mostRecentFor(userId: UUID): GoalMission?
}

/**
 * Backs [MissionPeriod]. Deliberately minimal for Batch G1 — see [GoalMissionDao]'s kdoc for
 * the shared reasoning. No query beyond basic CRUD exists yet because no call site needs one
 * until Batch G2 (creation) and later batches (actually generating sessions from an active
 * period) land.
 */
@Dao
interface MissionPeriodDao {
    @Insert
    suspend fun insert(missionPeriod: MissionPeriod)

    @Update
    suspend fun update(missionPeriod: MissionPeriod)

    @Query("SELECT * FROM mission_periods WHERE id = :id")
    suspend fun get(id: UUID): MissionPeriod?

    @Query("SELECT * FROM mission_periods WHERE goalMissionId = :goalMissionId AND active = 1")
    suspend fun activeForGoal(goalMissionId: UUID): List<MissionPeriod>
}

/**
 * Backs [MissionLogEntry]. Deliberately minimal for Batch G1 — see [GoalMissionDao]'s kdoc for
 * the shared reasoning.
 */
@Dao
interface MissionLogEntryDao {
    @Insert
    suspend fun insert(entry: MissionLogEntry)

    @Query("SELECT * FROM mission_log_entries WHERE goalMissionId = :goalMissionId ORDER BY createdAt ASC")
    suspend fun forGoal(goalMissionId: UUID): List<MissionLogEntry>
}

/**
 * Backs [Trigger]. Deliberately minimal for Batch G1 — see [GoalMissionDao]'s kdoc for the
 * shared reasoning. Real condition-evaluation queries are Batch G5 scope.
 */
@Dao
interface TriggerDao {
    @Insert
    suspend fun insert(trigger: Trigger)

    @Update
    suspend fun update(trigger: Trigger)

    @Query("SELECT * FROM triggers WHERE goalMissionId = :goalMissionId AND active = 1")
    suspend fun activeForGoal(goalMissionId: UUID): List<Trigger>
}

/**
 * Backs [Milestone]. Deliberately minimal for Batch G1 — see [GoalMissionDao]'s kdoc for the
 * shared reasoning. Real progress-tracking queries are Batch G6 scope.
 */
@Dao
interface MilestoneDao {
    @Insert
    suspend fun insert(milestone: Milestone)

    @Update
    suspend fun update(milestone: Milestone)

    @Query("SELECT * FROM milestones WHERE goalMissionId = :goalMissionId ORDER BY id ASC")
    suspend fun forGoal(goalMissionId: UUID): List<Milestone>
}
