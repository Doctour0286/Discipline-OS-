package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Enforcer-local cache of MissionProfile enforcement-relevant fields only.
 * Design doc §1.1 — "cached_mission_profile". Carries the blocklist/allowlist snapshot
 * needed for offline enforcement decisions at MissionAccessibilityService:125.
 *
 * TODO(split): synced from Console on push. Replaces full MissionProfile entity in Enforcer DB.
 */
@Entity(tableName = "cached_mission_profiles")
data class CachedMissionProfile(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val name: String,
    val allowlist: List<String>,
    val blocklist: List<String>,
    val createdAt: Instant,
)
