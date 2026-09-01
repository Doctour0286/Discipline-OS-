package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.disciplineos.data.ledger.LedgerMetric
import java.time.Instant
import java.util.UUID

/**
 * Enforcer-local provisional ledger entry.
 * Design doc §1.2 — "provisional_ledger_entry". Separate table from the authoritative
 * ledger_entries so provisional estimates never mix with Console-authoritative data.
 *
 * [synced] = false: written locally during an offline Mission, pending reconciliation.
 * [synced] = true: confirmed by Console after sync.
 * [syncedEntryId]: after reconciliation, the authoritative LedgerEntry.id that replaced this.
 *
 * TODO(split): provisional entries are discarded/corrected after sync per Design doc §2.3.
 */
@Entity(tableName = "provisional_ledger_entries")
data class ProvisionalLedgerEntry(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val violationId: UUID?,
    val metric: LedgerMetric,
    val delta: Double,
    val appliedAt: Instant,
    val synced: Boolean = false,
    val syncedEntryId: UUID? = null,
)
