package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class ViolationType { BLOCKLIST_ACCESS, EARLY_EXIT, NON_START }

enum class DisputeStatus { NONE, FLAGGED, UNDER_REVIEW, UPHELD, OVERTURNED }

/**
 * Data Model & Schema doc §2.3, wired to the dispute flow in §6.
 *
 * [rootCauseClusterId] is not in the original §2.3 block verbatim, but is required by the
 * shared-cause guard (§27.2 of PRD / §3.5 of Data Model doc): a single missed Mission must
 * not simultaneously max out Debt Ceiling contribution AND trigger Reputation demotion from
 * the same root cause without deduplication. Nullable because not every Violation is part of
 * a cluster with another consequence type.
 */
@Entity(tableName = "violations")
data class Violation(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val detectedAt: Instant,
    val type: ViolationType,
    val disputeStatus: DisputeStatus = DisputeStatus.NONE,
    val disputeFlaggedAt: Instant? = null,
    val consequencePaused: Boolean = false, // true while disputeStatus = FLAGGED or UNDER_REVIEW
    val rootCauseClusterId: UUID? = null, // §27.2 shared-cause guard
)
