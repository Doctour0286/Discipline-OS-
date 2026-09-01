package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Sync state machine for violations recorded offline.
 * Design doc §1.1 — "pending_violation". Offline durability for violations until
 * reconciliation with the Console's authoritative Ledger.
 *
 * [syncState] transitions: PENDING → SYNCING → SYNCED (or FAILED → PENDING for retry).
 * [provisionalDebtDelta]/[provisionalReputationDelta] store the local estimate computed
 * by RecordViolationUseCase at detection time, so the Console can compare against its
 * authoritative calculation during reconciliation (Design doc §2.3).
 */
enum class ViolationSyncState { PENDING, SYNCING, SYNCED, FAILED }

@Entity(tableName = "pending_violations")
data class PendingViolation(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val detectedAt: Instant,
    val type: ViolationType,
    val disputeStatus: DisputeStatus = DisputeStatus.NONE,
    val disputeFlaggedAt: Instant? = null,
    val consequencePaused: Boolean = false,
    val rootCauseClusterId: UUID? = null,
    val syncState: ViolationSyncState = ViolationSyncState.PENDING,
    val provisionalDebtDelta: Double? = null,
    val provisionalReputationDelta: Double? = null,
    val createdAt: Instant = Instant.now(),
)
