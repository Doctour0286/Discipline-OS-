package com.disciplineos.data.adherence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant
import java.util.UUID

/**
 * Backs [AdherenceLedgerEntry] — see that class's kdoc for why this is a separate DAO/table
 * rather than folded into [com.disciplineos.data.ledger.LedgerDao].
 *
 * Deliberately a narrower method set than [com.disciplineos.data.ledger.LedgerDao]: no
 * pause/reverse/dispute-flow queries (no dispute flow exists for Adherence, see
 * [AdherenceLedgerEntry]'s kdoc), no per-Violation queries (Adherence entries are never tied to
 * a Violation). Only what [com.disciplineos.domain.usecase.ApplyAdherenceDecayUseCase] and a
 * future Mission detail screen (Batch G4) actually need.
 */
@Dao
interface AdherenceLedgerDao {
    @Insert
    suspend fun insert(entry: AdherenceLedgerEntry)

    /**
     * Current Adherence score contribution for one [GoalMission] — same "always derived, never
     * stored as a mutable column" shape [com.disciplineos.data.ledger.LedgerDao.currentValue]
     * uses. No `pausedAt`/`reversedAt` filtering (unlike that method) since neither column
     * exists on [AdherenceLedgerEntry] — every row here is always active.
     */
    @Query("SELECT COALESCE(SUM(delta), 0.0) FROM adherence_ledger_entries WHERE goalMissionId = :goalMissionId")
    suspend fun currentValue(goalMissionId: UUID): Double

    @Query(
        "SELECT * FROM adherence_ledger_entries WHERE goalMissionId = :goalMissionId " +
            "AND appliedAt >= :since ORDER BY appliedAt ASC"
    )
    suspend fun entriesSince(goalMissionId: UUID, since: Instant): List<AdherenceLedgerEntry>
}
