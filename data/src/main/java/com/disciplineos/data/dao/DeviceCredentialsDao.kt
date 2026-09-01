package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.disciplineos.data.entity.DeviceCredentials
import java.util.UUID

/**
 * Device pairing credentials. Design doc §1.1 — "device_credentials".
 * No enforcement-path reads; used by the sync client for auth.
 */
@Dao
interface DeviceCredentialsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(credentials: DeviceCredentials)

    @Query("SELECT * FROM device_credentials LIMIT 1")
    suspend fun get(): DeviceCredentials?

    @Query("SELECT * FROM device_credentials WHERE deviceId = :deviceId")
    suspend fun getById(deviceId: UUID): DeviceCredentials?
}
