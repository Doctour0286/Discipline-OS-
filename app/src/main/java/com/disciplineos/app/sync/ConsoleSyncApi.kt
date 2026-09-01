package com.disciplineos.app.sync

/**
 * REST client interface for the Console backend.
 * TODO(split): Implement with Retrofit/ktor. This stub defines the contract the Enforcer
 * will use once sync is wired.
 */
interface ConsoleSyncApi {
    /** GET /api/v1/users/:id — fetch user profile for cache. */
    suspend fun getUser(userId: String): ApiResult<ApiUser>

    /** GET /api/v1/missions/:id — fetch mission detail for cache. */
    suspend fun getMission(missionId: String): ApiResult<ApiMission>

    /** POST /api/v1/sync/violations — push pending violations recorded offline. */
    suspend fun pushViolations(payload: List<PendingViolationPayload>): ApiResult<Unit>

    /** GET /api/v1/sync/pending — pull pending triggers/actions queued by Console for the Enforcer. */
    suspend fun pullPendingActions(userId: String): ApiResult<List<PendingAction>>
}

data class ApiUser(
    val id: String,
    val name: String,
    val email: String,
    val tier: String,
    val globalReputationScore: Double,
)

data class ApiMission(
    val id: String,
    val userId: String,
    val title: String,
    val archetype: String,
    val targetDirection: String?,
    val targetValue: Double?,
    val unit: String?,
    val cadenceType: String,
    val measurementSource: String,
    val lifecycleStage: String,
    val adherenceScore: Double?,
)

data class PendingAction(
    val actionType: String,
    val payloadJson: String,
)

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
}
