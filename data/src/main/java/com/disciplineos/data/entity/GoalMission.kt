package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Enforcer-retained entity. EnforcementSession.missionId is non-null (schema v10 fix),
 * so this table must exist for the FK to resolve. Kept with full fields to avoid breaking
 * existing use-case references; the Enforcer uses CachedGoalMission (synced from Console)
 * for new offline-created sessions.
 */
enum class MissionArchetype { OUTCOME_DRIVEN, BEHAVIOR_DRIVEN, CONSTRAINT }

@Entity(tableName = "goal_missions")
data class GoalMission(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val title: String,
    val archetype: MissionArchetype,
    val targetDirection: com.disciplineos.data.entity.TargetDirection? = null,
    val targetValue: Double? = null,
    val unit: String? = null,
    val cadenceType: com.disciplineos.data.entity.CadenceType = com.disciplineos.data.entity.CadenceType.NONE,
    val resetMode: com.disciplineos.data.entity.ResetMode = com.disciplineos.data.entity.ResetMode.ROLLING_WINDOW,
    val measurementSource: com.disciplineos.data.entity.MeasurementSource = com.disciplineos.data.entity.MeasurementSource.MANUAL_LOG,
    val lifecycleStage: com.disciplineos.data.entity.LifecycleStage = com.disciplineos.data.entity.LifecycleStage.OBSERVING,
    val adherenceScore: Double? = null,
    val adherenceWindow: Int? = null,
    val consecutiveWindowsBelowThreshold: Int = 0,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
    val triggerPromptDismissedAt: Instant? = null,
)

// Minimal enum stubs retained for GoalMission column compatibility.
// Full enum definitions moved to web-app-reference/ with the non-enforcement code.
enum class TargetDirection { INCREASE, DECREASE, MAINTAIN }
enum class CadenceType { DAILY, WEEKLY, CUSTOM_DAYS, NONE }
enum class ResetMode { FIXED_CALENDAR, ROLLING_WINDOW }
enum class MeasurementSource { AUTOMATIC, MANUAL_LOG, BOTH }
enum class LifecycleStage { OBSERVING, HYPOTHESIZING, ENFORCING, REVIEWING }
