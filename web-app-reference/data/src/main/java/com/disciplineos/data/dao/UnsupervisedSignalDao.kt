package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.disciplineos.data.entity.SignalType
import com.disciplineos.data.entity.UnsupervisedSignal
import java.time.Instant
import java.util.UUID

/**
 * Lives in UnsupervisedDatabase only — see UnsupervisedSignal's kdoc and
 * DisciplineOsDatabase.kt for why this is a separate physical database, not just a
 * separate table. Constructor-inject this DAO ONLY into classes computing
 * Unsupervised Reliability Trend / Self-Initiation Trend (§3.3, §3.6) — never into
 * anything that also holds a LedgerDao or TierDao. ArchitectureBoundaryTest checks this.
 */
@Dao
interface UnsupervisedSignalDao {
    @Insert
    suspend fun insert(signal: UnsupervisedSignal)

    @Query(
        """
        SELECT * FROM unsupervised_signals
        WHERE userId = :userId AND signalType = :type AND capturedAt >= :since
        ORDER BY capturedAt ASC
        """
    )
    suspend fun signalsSince(userId: UUID, type: SignalType, since: Instant): List<UnsupervisedSignal>

    @Query("SELECT * FROM unsupervised_signals WHERE userId = :userId AND capturedAt >= :since")
    suspend fun allSince(userId: UUID, since: Instant): List<UnsupervisedSignal>

    @Query("DELETE FROM unsupervised_signals WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: UUID) // separately-deletable category, PRD §40
}
