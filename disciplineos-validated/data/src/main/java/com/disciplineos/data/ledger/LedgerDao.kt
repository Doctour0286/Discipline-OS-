package com.disciplineos.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import java.util.UUID

@Dao
interface LedgerDao {

    @Insert
    suspend fun insert(entry: LedgerEntry)

    /**
     * Current value of a metric for a user — always derived, never stored directly.
     * Data Model §6: "Current Debt/Reputation values are always sum(delta) where
     * reversed_at is null." Extended (Phase 1) to also exclude paused entries — see
     * [LedgerEntry.pausedAt] kdoc for why a flagged-dispute entry must stop contributing
     * without being marked reversed.
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
        WHERE violationId = :violationId AND reversedAt IS NULL AND pausedAt IS NULL
        """
    )
    suspend fun activeEntriesForViolation(violationId: UUID): List<LedgerEntry>

    /**
     * §27.2 shared-cause guard support: active (non-reversed, non-paused) ledger entries of
     * a given metric for any violation in a list — used to check "has this cluster already
     * had a Debt or Reputation penalty applied" before applying a second one for the same
     * root cause. Room doesn't support passing a List directly into a hand-written IN clause
     * portably across all backends the way it does with generated queries, so this takes
     * the list as-is; Room expands it correctly for a `violationId IN (:violationIds)`
     * binding.
     */
    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE violationId IN (:violationIds) AND metric = :metric
          AND reversedAt IS NULL AND pausedAt IS NULL
        """
    )
    suspend fun activeEntriesForViolations(violationIds: List<UUID>, metric: LedgerMetric): List<LedgerEntry>

    /**
     * §26.4 dispute flow: pauses every currently-active (non-reversed, not-already-paused)
     * ledger entry tied to a Violation — called when a dispute is filed, so its Debt/
     * Reputation contribution stops counting immediately without being struck permanently.
     * Symmetric with [unpauseEntriesForViolation] (UPHELD path) and
     * [reverseEntriesForViolation] (OVERTURNED path, which also clears any pause).
     */
    @Query(
        """
        UPDATE ledger_entries SET pausedAt = :pausedAt
        WHERE violationId = :violationId AND reversedAt IS NULL AND pausedAt IS NULL
        """
    )
    suspend fun pauseEntriesForViolation(violationId: UUID, pausedAt: Instant)

    /**
     * §26.4 UPHELD path: "the original consequence applies retroactively as if never
     * paused" — clears [LedgerEntry.pausedAt] on every paused entry for this Violation so it
     * resumes counting, without touching [LedgerEntry.appliedAt] (the entry's original
     * applied time is preserved; it was never actually removed from the ledger, only
     * excluded from the running sum while paused).
     */
    @Query(
        """
        UPDATE ledger_entries SET pausedAt = NULL
        WHERE violationId = :violationId AND reversedAt IS NULL AND pausedAt IS NOT NULL
        """
    )
    suspend fun unpauseEntriesForViolation(violationId: UUID)

    /**
     * Reverses every active ledger entry tied to a violation — used when a dispute is
     * overturned (§26.4: "Debt penalty reversed, Reputation penalty reversed").
     * Does NOT delete rows; sets reversedAt/reversedReason so the ledger stays append-only
     * and auditable. Matches both paused and unpaused entries (a dispute can be overturned
     * from the paused state, which is in fact the normal case — see §6 flagged →
     * consequence_paused).
     */
    @Query(
        """
        UPDATE ledger_entries SET reversedAt = :reversedAt, reversedReason = :reason
        WHERE violationId = :violationId AND reversedAt IS NULL
        """
    )
    suspend fun reverseEntriesForViolation(violationId: UUID, reversedAt: Instant, reason: String)

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE userId = :userId AND metric = :metric AND appliedAt >= :since
        ORDER BY appliedAt ASC
        """
    )
    suspend fun entriesSince(userId: UUID, metric: LedgerMetric, since: Instant): List<LedgerEntry>

    @Update
    suspend fun update(entry: LedgerEntry)
}
