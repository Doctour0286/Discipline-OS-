package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.TierEventKind
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import java.time.Instant
import java.util.UUID

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: UUID): User?

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getSingleLocalUser(): User?
}

@Dao
interface TierDao {
    @Insert
    suspend fun insertEvent(event: TierEvent)

    @Query("SELECT * FROM tier_events WHERE userId = :userId ORDER BY occurredAt ASC")
    suspend fun eventsFor(userId: UUID): List<TierEvent>

    @Query(
        """
        SELECT * FROM tier_events
        WHERE userId = :userId AND kind = :kind
        ORDER BY occurredAt DESC
        LIMIT 1
        """
    )
    suspend fun mostRecentEventOfKind(userId: UUID, kind: TierEventKind): TierEvent?
}

@Dao
interface EnforcementSessionDao {
    @Insert
    suspend fun insert(mission: EnforcementSession)

    @Update
    suspend fun update(mission: EnforcementSession)

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun get(id: UUID): EnforcementSession?

    @Query("SELECT * FROM missions WHERE userId = :userId AND status = 'ACTIVE' LIMIT 1")
    suspend fun activeMissionFor(userId: UUID): EnforcementSession?
}

@Dao
interface MissionProfileDao {
    @Insert
    suspend fun insert(profile: MissionProfile)

    @Query("SELECT * FROM mission_profiles WHERE id = :id")
    suspend fun get(id: UUID): MissionProfile?

    @Query("SELECT * FROM mission_profiles WHERE userId = :userId LIMIT 1")
    suspend fun mostRecentFor(userId: UUID): MissionProfile?
}

/**
 * Minimal GoalMissionDao — retained because EnforcementSession.missionId is a non-null FK
 * to goal_missions, so the DebugSeeder (and eventually Console-initiated session creation)
 * must be able to insert GoalMission rows. Full query surface moved to web-app-reference/.
 */
@Dao
interface GoalMissionDao {
    @Insert
    suspend fun insert(goalMission: com.disciplineos.data.entity.GoalMission)

    @Query("SELECT * FROM goal_missions WHERE id = :id")
    suspend fun get(id: UUID): com.disciplineos.data.entity.GoalMission?
}

@Dao
interface ViolationDao {
    @Insert
    suspend fun insert(violation: Violation)

    @Update
    suspend fun update(violation: Violation)

    @Query("SELECT * FROM violations WHERE id = :id")
    suspend fun get(id: UUID): Violation?

    @Query("SELECT * FROM violations WHERE missionId = :missionId")
    suspend fun forMission(missionId: UUID): List<Violation>

    @Query("SELECT * FROM violations WHERE rootCauseClusterId = :clusterId")
    suspend fun forRootCauseCluster(clusterId: UUID): List<Violation>
}
