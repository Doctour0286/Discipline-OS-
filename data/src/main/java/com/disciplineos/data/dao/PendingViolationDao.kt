package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.PendingViolation
import com.disciplineos.data.entity.ViolationSyncState
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Local queue for violations recorded offline. Design doc §1.1 — "pending_violation".
 * Supports the shared-cause guard (RecordViolationUseCase:144) which needs to read
 * sibling violations within a 3-day rolling window, working against the local queue
 * rather than the (unavailable) backend.
 */
@Dao
interface PendingViolationDao {
    @Insert
    suspend fun insert(violation: PendingViolation)

    @Update
    suspend fun update(violation: PendingViolation)

    @Query("SELECT * FROM pending_violations WHERE id = :id")
    suspend fun get(id: UUID): PendingViolation?

    @Query("SELECT * FROM pending_violations WHERE syncState = :state")
    suspend fun getByState(state: ViolationSyncState): List<PendingViolation>

    /**
     * Shared-cause guard support: sibling violations sharing a rootCauseClusterId,
     * within [window] of [referenceTime]. Matches RecordViolationUseCase's existing
     * 3-day rolling window logic.
     */
    @Query(
        """
        SELECT * FROM pending_violations
        WHERE rootCauseClusterId = :clusterId AND id != :excludingId
        """
    )
    suspend fun forRootCauseCluster(clusterId: UUID, excludingId: UUID): List<PendingViolation>

    @Query("SELECT * FROM pending_violations WHERE missionId = :missionId")
    suspend fun forMission(missionId: UUID): List<PendingViolation>

    @Query("SELECT COUNT(*) FROM pending_violations WHERE syncState = 'PENDING'")
    suspend fun pendingCount(): Int
}
