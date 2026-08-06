package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class Tier { RECRUIT, OPERATOR, WARDEN, IRON }

/**
 * Data Model & Schema doc §2.1.
 *
 * Notes on fields that deviate from a naive read of the spec:
 * - [tierActivationAt] may lag [tierSelectedAt] — this is the Iron calibration gate
 *   (PRD §12.6 / Data Model §5). Enforced in [UserDao] / tier-transition logic, not here.
 * - [calibrationWindowDays] is stored per-user (not a global constant) per Data Model §5,
 *   specifically so the future signal-quality scaling (PRD §42) doesn't require a schema
 *   migration later. Defaults to 10 (Hypothesis, flagged §42).
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: UUID,
    val createdAt: Instant,
    val currentTier: Tier,
    val tierSelectedAt: Instant,
    val tierActivationAt: Instant,
    val calibrationWindowDays: Int = 10, // [HYPOTHESIS] Data Model §5, PRD §42
    val onboardingConsentVersion: String,
    val unsupervisedReliabilityOptIn: Boolean = false,
    val unsupervisedReliabilityOptInAt: Instant? = null,
)
