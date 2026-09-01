package com.disciplineos.app.sync

import java.util.UUID

/**
 * Stub interface for syncing cached mission profiles and goal missions from the Console.
 * TODO(split): Implement using Console REST API (design doc §3.2).
 */
interface MissionSyncClient {
    /** Pull latest GoalMission + MissionProfile for offline enforcement cache. */
    suspend fun pullMissionProfile(userId: UUID, missionId: UUID): SyncResult

    /** Push pending violations recorded while offline. */
    suspend fun pushPendingViolations(violations: List<PendingViolationPayload>): SyncResult
}

data class PendingViolationPayload(
    val violationId: UUID,
    val missionId: UUID,
    val userId: UUID,
    val rootCauseClusterId: UUID?,
    val recordedAt: Long,
    val source: String,
    val metadataJson: String?,
)

sealed class SyncResult {
    data object Success : SyncResult()
    data class Failure(val code: Int, val message: String) : SyncResult()
}
