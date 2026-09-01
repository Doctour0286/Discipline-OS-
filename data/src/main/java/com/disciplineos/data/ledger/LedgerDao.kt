package com.disciplineos.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant
import java.util.UUID

/**
 * Enforcer-trimmed LedgerDao. Only methods needed by RecordViolationUseCase retained.
 * Dispute/resolve/reverse methods moved to web-app-reference/ with ResolveDisputeUseCase.
 *
 * TODO(split): In the full Enforcer, provisional ledger entries go to ProvisionalLedgerDao.
 * Authoritative ledger_entries stay on the Console. This DAO is retained for the interim
 * so RecordViolationUseCase compiles without modification.
 */
@Dao
interface LedgerDao {

    @Insert
    suspend fun insert(entry: LedgerEntry)

    /**
     * Current value of a metric for a user — always derived, never stored directly.
     * Data Model §6: "Current Debt/Reputation values are always sum(delta) where
     * reversed_at is null."
     */
    @Query(
        """
        SELECT COALESCE(SUM(delta), 0.0) FROM ledger_entries
        WHERE userId = :userId AND metric = :metric AND reversedAt IS NULL AND pausedAt IS NULL
        """
    )
    suspend fun currentValue(userId: UUID, metric: LedgerMetric): Double

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE violationId IN (:violationIds) AND metric = :metric
          AND reversedAt IS NULL AND pausedAt IS NULL
        """
    )
    suspend fun activeEntriesForViolations(violationIds: List<UUID>, metric: LedgerMetric): List<LedgerEntry>
}
