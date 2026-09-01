package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.CachedUser
import java.util.UUID

/**
 * Enforcer-local cached user state. Synced from Console; enforcement-path reads only.
 * Design doc §1.1 — "cached_user".
 */
@Dao
interface CachedUserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: CachedUser)

    @Query("SELECT * FROM cached_user LIMIT 1")
    suspend fun get(): CachedUser?

    @Query("SELECT * FROM cached_user WHERE id = :id")
    suspend fun getById(id: UUID): CachedUser?

    @Update
    suspend fun update(user: CachedUser)
}
