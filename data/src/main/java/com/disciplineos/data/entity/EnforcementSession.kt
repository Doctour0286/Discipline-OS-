package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class MissionStatus { ACTIVE, COMPLETED, VIOLATED, DISPUTED, ABORTED_CRISIS_EXIT }

/**
 * Data Model & Schema doc §2.2a (renamed from `Mission`, §2.2 — superseded, ROADMAP.md §5.32).
 *
 * **Renamed from `Mission` to `EnforcementSession` as part of the Goal-Oriented Mission Model**
 * (`06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §2.1). This is the same entity that
 * existed under the name `Mission` since Phase 0 — same fields, same enforcement mechanics,
 * same table contents — renamed because the old name now describes something narrower than what
 * users mean by "Mission" in product copy: this row is one locked, monitored *enforcement
 * window*, as distinct from a [GoalMission] (the open-ended goal a session may or may not belong
 * to). See [GoalMission]'s kdoc for the full split rationale. `enum class MissionStatus`, the
 * `MissionDao` interface name, and the `missions` table name are all deliberately kept unchanged
 * in this rename — only the Kotlin entity class name changes, plus the handful of DAO method
 * signatures whose parameter/return types reference it. See `MissionDao`'s kdoc in
 * `CoreDaos.kt` for the low-churn reasoning behind keeping the interface and table names as-is.
 *
 * [goalMissionId] is new in this pass — nullable, since a standalone session with no parent goal
 * remains a fully valid, independently meaningful row (not every timed enforcement window needs
 * to belong to a larger goal). Every pre-existing row from before this migration lands with
 * `goalMissionId = null` after the rename (destructive-fallback reinstall, per this project's
 * existing pre-launch migration policy — see `DisciplineOsDatabase.kt`'s own kdoc; no real
 * installed base exists yet for this to be a data-loss concern).
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
data class EnforcementSession(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val goalMissionId: UUID?,
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
 *
 * [missionId] name kept unchanged through the `Mission` → [EnforcementSession] rename — this
 * column still refers to an [EnforcementSession.id], the rename only touched the Kotlin class
 * name (and, deliberately, nothing else — see [EnforcementSession]'s own kdoc) — renaming this
 * column would be pure churn with no semantic gain.
 */
@Entity(tableName = "output_artifacts", primaryKeys = ["missionId", "id"])
data class OutputArtifact(
    val id: UUID,
    val missionId: UUID,
    val kind: String, // e.g. "words", "commits", "exports" — free-form per PRD §13.7, not an enum in the spec
    val value: String, // stored as string; interpretation is kind-dependent and display-only
    val recordedAt: Instant,
)
