package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.disciplineos.data.entity.CachedGoalMission
import java.util.UUID

/**
 * Enforcer-local cached goal missions. Synced from Console; minimal fields for
 * enforcement-path FK satisfaction and offline session creation.
 * Design doc §1.1 — "cached_goal_mission".
 */
@Dao
interface CachedGoalMissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goalMission: CachedGoalMission)

    @Query("SELECT * FROM cached_goal_missions WHERE id = :id")
    suspend fun get(id: UUID): CachedGoalMission?

    @Query("SELECT * FROM cached_goal_missions WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    suspend fun mostRecentFor(userId: UUID): CachedGoalMission?
}
