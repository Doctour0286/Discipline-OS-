package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class MissionStatus { ACTIVE, COMPLETED, VIOLATED, DISPUTED, ABORTED_CRISIS_EXIT }

/**
 * Data Model & Schema doc §2.2.
 *
 * [scheduledStart] null means ad hoc — feeds Self-Initiation Trend (§3.6).
 * [status] = ABORTED_CRISIS_EXIT is distinct from VIOLATED (Data Model §5, Iron crisis exit
 * §12.4.4) and must NOT write to Debt or Reputation — treated like an overturned dispute
 * for consequence purposes. That rule lives in the ledger/consequence layer, not here;
 * this entity only records the fact of the status.
 *
 * [allowlist] / [blocklist] store package ids as strings rather than a foreign key to a
 * separate app-catalog table — MVP has no need for one, and the PRD doesn't specify one.
 */
@Entity(tableName = "missions")
data class Mission(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val scheduledStart: Instant?,
    val actualStart: Instant,
    val actualEnd: Instant?,
    val plannedDurationMin: Int,
    val status: MissionStatus,
    val allowlist: List<String>,
    val blocklist: List<String>,
    val missionProfileId: UUID,
)

/**
 * Data Model §2.2 `output_artifacts` — Mission Output Intelligence, PRD §13.7.
 * Descriptive only. Must never be read by any scoring/consequence calculator —
 * same category of hard boundary as UnsupervisedSignal (§13.3), just narrower in scope
 * (this is barred from consequence paths specifically, not isolated at the schema level
 * the way UnsupervisedSignal is — see OutputArtifactDao notes).
 */
@Entity(tableName = "output_artifacts", primaryKeys = ["missionId", "id"])
data class OutputArtifact(
    val id: UUID,
    val missionId: UUID,
    val kind: String, // e.g. "words", "commits", "exports" — free-form per PRD §13.7, not an enum in the spec
    val value: String, // stored as string; interpretation is kind-dependent and display-only
    val recordedAt: Instant,
)
