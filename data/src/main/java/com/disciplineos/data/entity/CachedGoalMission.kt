package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Enforcer-local cache of GoalMission enforcement-relevant fields only.
 * Design doc §1.1 — "cached_goal_mission". Minimal fields needed to satisfy
 * EnforcementSession.missionId's non-null FK constraint and to enable offline session creation.
 *
 * TODO(split): synced from Console on push. Replaces full GoalMission entity in Enforcer DB.
 */
@Entity(tableName = "cached_goal_missions")
data class CachedGoalMission(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val title: String,
    val archetype: MissionArchetype,
)
