package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Enforcer-local cache of User enforcement-relevant fields only.
 * Design doc §1.1 — "cached_user". Subset of the full User entity; non-enforcement fields
 * (flaggedCategories, unsupervisedReliabilityOptIn, onboardingConsentVersion,
 * consecutiveDaysBelowFloor) are excluded because zero enforcement-path code reads them.
 *
 * TODO(split): synced from Console on push. Replaces full User entity in Enforcer DB.
 */
@Entity(tableName = "cached_user")
data class CachedUser(
    @PrimaryKey val id: UUID,
    val currentTier: com.disciplineos.data.entity.Tier?,
    val tierSelectedAt: Instant?,
    val tierActivationAt: Instant?,
    val calibrationWindowDays: Int = 10,
    val debtAccrualPausedUntil: Instant? = null,
    val tribunalDeferredUntil: Instant? = null,
    val lastExplicitDowngradeAt: Instant? = null,
)
