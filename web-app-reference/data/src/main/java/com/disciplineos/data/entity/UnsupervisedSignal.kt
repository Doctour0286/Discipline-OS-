package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class SignalType {
    VOLUNTARY_HIGH_VALUE_RETURN,
    VOLUNTARY_HIGH_RISK_AVOIDANCE,
    SELF_INITIATED_MISSION_START,
    UNSCHEDULED_USE_PATTERN,
    SELF_REPORT_CAPACITY, // §13.2.1 — Brief Self-Control Scale, monthly cadence
}

/**
 * Data Model & Schema doc §2.4 and §7.
 *
 * STRUCTURAL ISOLATION, not policy isolation: this entity lives in [UnsupervisedDatabase],
 * a physically separate Room database from [DisciplineDatabase] (which holds Ledger,
 * Mission, Violation, Tier). There is no shared connection, no cross-database foreign key
 * (Room/SQLite cannot express one anyway), and no DAO in this module may accept both a
 * UnsupervisedSignalDao and a LedgerDao as constructor parameters — see
 * `ArchitectureBoundaryTest` for the enforcement mechanism.
 *
 * This is what makes "no enforcement path can touch this" true by construction rather than
 * by convention, per Data Model §1 principle 3 and §7.
 */
@Entity(tableName = "unsupervised_signals")
data class UnsupervisedSignal(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val capturedAt: Instant,
    val signalType: SignalType,
    val valueJson: String, // jsonb in spec; stored as JSON string, interpretation is signalType-dependent
)
