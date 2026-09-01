package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.disciplineos.data.entity.CachedMissionProfile
import java.util.UUID

/**
 * Enforcer-local cached mission profiles. Synced from Console; carries blocklist/allowlist
 * snapshot for offline enforcement decisions.
 * Design doc §1.1 — "cached_mission_profile".
 */
@Dao
interface CachedMissionProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CachedMissionProfile)

    @Query("SELECT * FROM cached_mission_profiles WHERE id = :id")
    suspend fun get(id: UUID): CachedMissionProfile?

    @Query("SELECT * FROM cached_mission_profiles WHERE userId = :userId LIMIT 1")
    suspend fun mostRecentFor(userId: UUID): CachedMissionProfile?
}
