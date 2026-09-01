package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.LedgerMetric
import com.disciplineos.data.entity.ProvisionalLedgerEntry
import java.util.UUID

/**
 * Enforcer-local provisional ledger entries. Design doc §1.2 — "provisional_ledger_entry".
 * Separate table from authoritative ledger_entries so provisional estimates never mix
 * with Console-authoritative data.
 *
 * Current value computation mirrors LedgerDao.currentValue's logic but reads from
 * provisional_ledger_entries: sum(delta) where synced = false.
 */
@Dao
interface ProvisionalLedgerDao {
    @Insert
    suspend fun insert(entry: ProvisionalLedgerEntry)

    @Update
    suspend fun update(entry: ProvisionalLedgerEntry)

    @Query(
        """
        SELECT COALESCE(SUM(delta), 0.0) FROM provisional_ledger_entries
        WHERE userId = :userId AND metric = :metric AND synced = 0
        """
    )
    suspend fun currentValue(userId: UUID, metric: LedgerMetric): Double

    @Query("SELECT * FROM provisional_ledger_entries WHERE userId = :userId AND synced = 0")
    suspend fun unsyncedEntries(userId: UUID): List<ProvisionalLedgerEntry>

    @Query("SELECT * FROM provisional_ledger_entries WHERE violationId = :violationId")
    suspend fun forViolation(violationId: UUID): List<ProvisionalLedgerEntry>

    /**
     * Shared-cause guard support: active (non-synced, non-reversed) provisional entries
     * for a set of violation IDs. Matches LedgerDao.activeEntriesForViolations's logic.
     */
    @Query(
        """
        SELECT * FROM provisional_ledger_entries
        WHERE violationId IN (:violationIds) AND metric = :metric AND synced = 0
        """
    )
    suspend fun activeEntriesForViolations(
        violationIds: List<UUID>,
        metric: LedgerMetric,
    ): List<ProvisionalLedgerEntry>
}
