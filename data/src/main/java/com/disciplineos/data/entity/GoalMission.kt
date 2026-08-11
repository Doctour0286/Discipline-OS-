package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Data Model & Schema doc §2.2a — Goal-Oriented Mission Model (accepted 2026-08-11,
 * ROADMAP.md §5.32). See `Documents/06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §2 for
 * the full field-by-field rationale this kdoc summarizes.
 *
 * A [GoalMission] is the *goal* — "finish the report," open-ended, may span days — as distinct
 * from an [EnforcementSession], which is one locked, monitored enforcement window against a
 * device. This split didn't exist before this entity: the pre-2026-08-11 schema (§2.2, now
 * superseded) conflated the two into a single flat `Mission` row. `EnforcementSession` is that
 * same pre-existing entity, renamed in this same pass — its own kdoc explains why the rename
 * happened alongside this addition rather than as a separate pass.
 *
 * [targetType]/[targetValue] together describe what "done" means for this goal — the shape of
 * [targetValue] depends on [targetType] (a JSON blob rather than three separate nullable
 * columns, since at most one of "a quantity," "a boolean," or "a deadline" is ever meaningful
 * for a given [GoalMission], and adding a fourth target type later shouldn't require a schema
 * migration to a new column). Interpretation of that blob is a domain-layer concern, not an
 * entity-layer one — this class does not attempt to parse or validate it.
 *
 * [missionProfileId] is nullable here (unlike [EnforcementSession.missionProfileId], which is
 * not) — a [GoalMission] can exist purely as an organizing goal with no default enforcement
 * scope yet decided; an [EnforcementSession] cannot exist without one, since enforcement always
 * needs a concrete allow/blocklist to run against.
 *
 * **Hard boundary, restated from the schema doc so it isn't lost in translation to code:** a
 * [GoalMission] cannot be violated — only an [EnforcementSession] under it can. No `Violation`,
 * `LedgerEntry`, Reputation, or Discipline Debt calculation may reference `GoalMission` directly;
 * they all key off `EnforcementSession.id`, same as before this model existed. This mirrors the
 * `UnsupervisedSignal` isolation pattern (§7) — scoring reaches down to the session, never up to
 * the goal.
 */
enum class GoalMissionStatus { ACTIVE, COMPLETED, ABANDONED }

enum class GoalMissionTargetType { QUANTITY, BOOLEAN, DEADLINE }

@Entity(tableName = "goal_missions")
data class GoalMission(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val title: String,
    val targetType: GoalMissionTargetType,
    val targetValue: String?, // jsonb-equivalent — shape depends on targetType, see class kdoc
    val status: GoalMissionStatus,
    val createdAt: Instant,
    val completedAt: Instant?,
    val missionProfileId: UUID?,
)

/**
 * Data Model & Schema doc §2.2a. A recurring-schedule template a [GoalMission] can generate
 * [EnforcementSession]s from — e.g. "every weekday at 9am for 90 minutes." Distinct from a
 * single scheduled [EnforcementSession.scheduledStart]: a [MissionPeriod] is the *template*,
 * not an individual occurrence. Which use-case actually turns an active [MissionPeriod] into a
 * concrete [EnforcementSession] row (and when) is Batch G2 scope
 * (`06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §3) — this entity only stores the
 * template shape, nothing here generates sessions on its own.
 *
 * [daysOfWeek] stored via the same `List<String>`-backed `Converters.fromStringList`/
 * `toStringList` pattern [EnforcementSession.allowlist]/[EnforcementSession.blocklist] already
 * use — [DayOfWeek] values serialized as their `.name` strings, converted at the DAO/repository
 * boundary rather than adding a dedicated enum-list converter for a one-entity need.
 *
 * [startTime] needs a new `Converters` entry ([LocalTime] has no existing converter in this
 * codebase as of this pass — [EnforcementSession]/[GoalMission] only ever needed [Instant]
 * before now) — see `Converters.kt`'s new `fromLocalTime`/`toLocalTime` pair, added in this
 * same commit.
 */
@Entity(tableName = "mission_periods")
data class MissionPeriod(
    @PrimaryKey val id: UUID,
    val goalMissionId: UUID,
    val daysOfWeek: List<String>, // DayOfWeek.name values; see class kdoc
    val startTime: LocalTime,
    val plannedDurationMin: Int,
    val active: Boolean,
)

/**
 * Data Model & Schema doc §2.2a. A freeform user note or checkpoint against a [GoalMission] —
 * "descriptive only, never scored," same category and same wording as
 * [EnforcementSession]'s own `OutputArtifact` boundary (Data Model §2.2/PRD §13.7) applied here
 * to goal-level notes instead of session-level output. No code path may read this table to
 * compute Reputation, Discipline Debt, or any Ledger entry — same restriction, extended to a
 * new table rather than re-derived independently.
 */
@Entity(tableName = "mission_log_entries")
data class MissionLogEntry(
    @PrimaryKey val id: UUID,
    val goalMissionId: UUID,
    val createdAt: Instant,
    val note: String,
)

/**
 * Data Model & Schema doc §2.2a. A condition that can auto-generate or flag a new
 * [EnforcementSession] under a [GoalMission] — e.g. "if no session has run in 3 days, prompt."
 * [Trigger] and [Milestone] are two distinct entities with distinct roles (a `Trigger` acts on
 * *sessions*, a `Milestone` reports on *progress within the goal itself* — see [Milestone]'s own
 * kdoc for the second half of that distinction) and are deliberately not merged into one table
 * despite both being "small, goal-scoped, and not directly enforcement-facing" — collapsing them
 * would blur a genuine semantic difference (an actionable condition vs. a descriptive
 * checkpoint) the same way merging `GoalMission` and `EnforcementSession` themselves would have.
 *
 * [conditionType]/[conditionValue] follow the same jsonb-equivalent pattern as
 * [GoalMission.targetType]/[targetValue] for the same reason: the set of possible trigger
 * conditions is expected to grow, and a free-form value column avoids a schema migration per
 * new condition type. Real condition types, evaluation timing, and exactly which UI surfaces a
 * fired `Trigger` are Batch G5 scope
 * (`06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §6) — this entity only stores the
 * template, same division of responsibility as [MissionPeriod].
 */
enum class TriggerConditionType { INACTIVITY, SCHEDULE_MISS, MANUAL }

@Entity(tableName = "triggers")
data class Trigger(
    @PrimaryKey val id: UUID,
    val goalMissionId: UUID,
    val conditionType: TriggerConditionType,
    val conditionValue: String?,
    val active: Boolean,
    val lastFiredAt: Instant?,
)

/**
 * Data Model & Schema doc §2.2a. A progress checkpoint within a `QUANTITY`- or
 * `DEADLINE`-type [GoalMission] — e.g. "50% of target reached." **Descriptive only, same
 * boundary as [MissionLogEntry]** — a [Milestone] being hit or missed never feeds Reputation,
 * Discipline Debt, or any [com.disciplineos.data.ledger.LedgerEntry]; only
 * [EnforcementSession]-level violations do (see [GoalMission]'s class kdoc for the restated
 * boundary this entity is bound by). [achievedAt] null means not yet reached — a [Milestone] row
 * always exists once defined, whether or not it's been hit, so "not achieved" is representable
 * without deleting/recreating rows.
 */
@Entity(tableName = "milestones")
data class Milestone(
    @PrimaryKey val id: UUID,
    val goalMissionId: UUID,
    val label: String,
    val targetValue: String?, // interpretation depends on the parent GoalMission's targetType
    val achievedAt: Instant?,
)
