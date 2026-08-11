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
import java.time.Instant
import java.util.UUID

/**
 * Backs [GoalMission] (Goal-Oriented Mission Model, ROADMAP.md §5.32). Kept as its own `@Dao`
 * matching every other entity-scoped DAO in this file/module (`TierDao`, `MissionProfileDao`) —
 * a distinct table with its own query shape, not folded into `EnforcementSessionDao` just
 * because the two entities are related (same reasoning `MissionProfileDao`'s own kdoc already
 * states for the identical question about itself).
 *
 * Method set matches Integration Plan §2.2 exactly: `insert`/`get`/`forUser`/`mostRecentFor`,
 * deliberately no `@Update` yet — no call site needs one as of this pass, matching
 * `MissionProfileDao`'s stated "no `@Update` yet" precedent for the same kind of restraint.
 */
@Dao
interface GoalMissionDao {
    @Insert
    suspend fun insert(goalMission: GoalMission)

    /**
     * Added ROADMAP.md §5.36/Batch G3 — the first real writer of `adherenceScore`/
     * `consecutiveWindowsBelowThreshold` post-creation. Same "real, motivated exception to
     * 'no @Update yet'" pattern [MilestoneDao.update] already established for `achievedAt`
     * (see that DAO's own kdoc) — this class's original "no call site needs one as of this
     * pass" note no longer holds once `ApplyAdherenceDecayUseCase` exists.
     */
    @Update
    suspend fun update(goalMission: GoalMission)

    @Query("SELECT * FROM goal_missions WHERE id = :id")
    suspend fun get(id: UUID): GoalMission?

    @Query("SELECT * FROM goal_missions WHERE userId = :userId")
    suspend fun forUser(userId: UUID): List<GoalMission>

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
 * Backs [MissionPeriod]. Method set matches Integration Plan §2.2: `insert`, `forMission
 * (missionId)`.
 */
@Dao
interface MissionPeriodDao {
    @Insert
    suspend fun insert(missionPeriod: MissionPeriod)

    @Query("SELECT * FROM mission_periods WHERE missionId = :missionId")
    suspend fun forMission(missionId: UUID): List<MissionPeriod>
}

/**
 * Backs [MissionLogEntry]. Method set matches Integration Plan §2.2: `insert`, `forMission
 * (missionId)`, `forMissionSince(missionId, since)` — the latter is what Adherence's
 * rolling-window computation in Batch G3 needs (`ApplyAdherenceDecayUseCase`, Integration Plan
 * §4.1), added now so G3 doesn't need its own DAO-layer PR. Method set itself needed no change
 * for G3 — only [MissionLogEntry]'s own fields did (`numericValue`/`didOccur`, ROADMAP.md §5.36).
 */
@Dao
interface MissionLogEntryDao {
    @Insert
    suspend fun insert(entry: MissionLogEntry)

    @Query("SELECT * FROM mission_log_entries WHERE missionId = :missionId ORDER BY createdAt ASC")
    suspend fun forMission(missionId: UUID): List<MissionLogEntry>

    @Query(
        "SELECT * FROM mission_log_entries WHERE missionId = :missionId AND createdAt >= :since " +
            "ORDER BY createdAt ASC"
    )
    suspend fun forMissionSince(missionId: UUID, since: Instant): List<MissionLogEntry>
}

/**
 * Backs [Trigger]. Method set matches Integration Plan §2.2: `insert`, `forMission(missionId)`.
 * Real condition-evaluation queries are Batch G5 scope.
 */
@Dao
interface TriggerDao {
    @Insert
    suspend fun insert(trigger: Trigger)

    @Query("SELECT * FROM triggers WHERE missionId = :missionId AND active = 1")
    suspend fun forMission(missionId: UUID): List<Trigger>
}

/**
 * Backs [Milestone]. Method set matches Integration Plan §2.2: `insert`, `forMission(missionId)`,
 * `update` — Milestone is the one new entity that needs `@Update`, since `achievedAt` is set
 * after creation per base doc §3.5/§4.4 — a real, motivated exception to the "no `@Update` yet"
 * pattern the other new DAOs in this file follow, not an unconsidered one.
 */
@Dao
interface MilestoneDao {
    @Insert
    suspend fun insert(milestone: Milestone)

    @Update
    suspend fun update(milestone: Milestone)

    @Query("SELECT * FROM milestones WHERE missionId = :missionId ORDER BY id ASC")
    suspend fun forMission(missionId: UUID): List<Milestone>
}
