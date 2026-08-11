package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Data Model & Schema doc §2.2a — Goal-Oriented Mission Model (accepted 2026-08-11,
 * ROADMAP.md §5.32). Field shapes here follow
 * `Documents/06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §2.1 directly — that document,
 * not the schema doc's own §2.2a summary, is this project's engineering-ready source for these
 * entities (checked in unmodified as the verified artifact in the same commit that added the
 * schema-doc summary; the summary itself is corrected in this same pass to match this file
 * rather than the other way around — see that doc's own note on the correction).
 *
 * A [GoalMission] is the *goal* — "finish the report," open-ended, may span days — as distinct
 * from an [EnforcementSession], which is one locked, monitored enforcement window against a
 * device. This split didn't exist before this entity: the pre-2026-08-11 schema (§2.2, now
 * superseded) conflated the two into a single flat `Mission` row. `EnforcementSession` is that
 * same pre-existing entity, renamed in this same pass — its own kdoc explains why the rename
 * happened alongside this addition rather than as a separate pass.
 *
 * [archetype]/[targetDirection]/[cadenceType]/[resetMode]/[measurementSource]/[lifecycleStage]
 * together describe the goal's shape and how it's tracked, per Integration Plan §2.1's field
 * list — interpretation of these (e.g. how [cadenceType]/[resetMode] combine to define a
 * tracking window) is a domain-layer concern, not an entity-layer one; this class does not
 * attempt to validate combinations.
 *
 * [GoalMission] itself carries no `missionProfileId` — per Integration Plan §2.1's exact field
 * list, a goal has no default enforcement scope of its own; each [EnforcementSession] under it
 * still carries its own `missionProfileId` (non-null, since enforcement always needs a concrete
 * allow/blocklist to run against), set per-session as onboarding's `FirstMissionSchedulingFragment`
 * fix (§3.1) does.
 *
 * **Hard boundary, restated from the schema doc so it isn't lost in translation to code:** a
 * [GoalMission] cannot be violated — only an [EnforcementSession] under it can. No `Violation`,
 * `LedgerEntry`, Reputation, or Discipline Debt calculation may reference `GoalMission` directly;
 * they all key off `EnforcementSession.id`, same as before this model existed. This mirrors the
 * `UnsupervisedSignal` isolation pattern (§7) — scoring reaches down to the session, never up to
 * the goal.
 *
 * [consecutiveWindowsBelowThreshold] — added ROADMAP.md §5.36/Integration Plan §7.5, a small
 * motivated addition not in the base document's §3.1 field list. Mirrors
 * [com.disciplineos.data.entity.User.consecutiveDaysBelowFloor] exactly: Adherence decays "on
 * sustained miss patterns, not single misses" (base doc §4.2), and there is no way to detect a
 * sustained pattern without storing *some* running state across calls to
 * `ApplyAdherenceDecayUseCase`. Counts consecutive `adherenceWindow`-length windows whose
 * hit-rate fell below the decay threshold; reset to 0 the first window that clears it, same
 * counter-reset semantics `ApplyReputationDecayUseCase` already uses for the Reputation
 * equivalent.
 */
enum class MissionArchetype { OUTCOME_DRIVEN, BEHAVIOR_DRIVEN, CONSTRAINT }
enum class TargetDirection { INCREASE, DECREASE, MAINTAIN }
enum class CadenceType { DAILY, WEEKLY, CUSTOM_DAYS, NONE }
enum class ResetMode { FIXED_CALENDAR, ROLLING_WINDOW }
enum class MeasurementSource { AUTOMATIC, MANUAL_LOG, BOTH }
enum class LifecycleStage { OBSERVING, HYPOTHESIZING, ENFORCING, REVIEWING }

@Entity(tableName = "goal_missions")
data class GoalMission(
    @PrimaryKey val id: UUID,
    val userId: UUID,
    val title: String,
    val archetype: MissionArchetype,
    val targetDirection: TargetDirection?,
    val targetValue: Double?,
    val unit: String?,
    val cadenceType: CadenceType,
    val resetMode: ResetMode,
    val measurementSource: MeasurementSource,
    val lifecycleStage: LifecycleStage,
    val adherenceScore: Double?,
    val adherenceWindow: Int?,
    val consecutiveWindowsBelowThreshold: Int = 0,
    val createdAt: Instant,
    val archivedAt: Instant?,
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
 * [periodType] includes `ALWAYS_ON` per Integration Plan §2.1/base doc §6.2's resolution.
 * `FIXED_WINDOW` with [windowStart]/[windowEnd] both null is a known, documented mismatch for
 * auto-generated onboarding periods (Integration Plan §3.3/§7.4 — genuinely open, not resolved
 * by this pass) rather than something every `FIXED_WINDOW` row is guaranteed to populate.
 *
 * [daysOfWeek] stored via the same `List<String>`-backed `Converters.fromStringList`/
 * `toStringList` pattern [EnforcementSession.allowlist]/[EnforcementSession.blocklist] already
 * use — [DayOfWeek] values serialized as their `.name` strings, converted at the DAO/repository
 * boundary rather than adding a dedicated enum-list converter for a one-entity need.
 *
 * [windowStart]/[windowEnd]/[deadlineTime] reuse `Converters.fromLocalTime`/`toLocalTime` (added
 * for this entity — [LocalTime] has no other user in this codebase as of this pass).
 *
 * **[enforcementProfileId] is non-null here; the base design doc's §3.2 specifies it as
 * `UUID | null` ("null = tracked/logged only, no blocking") — a second unflagged divergence
 * from that document, same category as the one ROADMAP.md §5.36 found and fixed on
 * [MissionLogEntry] (`numericValue`/`didOccur`), noted here rather than silently fixed a second
 * time in the same pass.** Not fixed alongside that one because nothing currently blocks on it:
 * Adherence's "Behavior-driven mission with no attached EnforcementSession" scope check
 * (base doc §4.2) is implemented via [EnforcementSessionDao.hasAnySessionFor] instead — existence
 * of a real `EnforcementSession` row, not this field's nullability — so `ApplyAdherenceDecayUseCase`
 * doesn't depend on this gap being closed. Still a real, unresolved divergence: as shipped, every
 * `MissionPeriod` claims a concrete enforcement profile even for a purely log-only, no-blocking
 * period the base doc's own model explicitly allows. Flagging for a future pass rather than
 * expanding this batch's scope to fix it.
 */
enum class PeriodType { FIXED_WINDOW, DEADLINE, ALWAYS_ON }

@Entity(tableName = "mission_periods")
data class MissionPeriod(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val periodType: PeriodType,
    val daysOfWeek: List<String>, // DayOfWeek.name values; see class kdoc
    val windowStart: LocalTime?,
    val windowEnd: LocalTime?,
    val targetDurationMin: Int?,
    val deadlineTime: LocalTime?,
    val enforcementProfileId: UUID,
)

/**
 * Data Model & Schema doc §2.2a. A freeform user note or checkpoint against a [GoalMission] —
 * "descriptive only, never scored" *for the [note] field specifically* — same category and same
 * wording as [EnforcementSession]'s own `OutputArtifact` boundary (Data Model §2.2/PRD §13.7)
 * applied here to goal-level notes instead of session-level output.
 *
 * **[numericValue]/[didOccur] added ROADMAP.md §5.36 — correcting a real schema gap, not a new
 * feature.** The base design doc (`06_GOAL_ORIENTED_MISSION_MODEL.md` §3.3) always specified
 * both fields — present in every prior draft (`..._PROPOSAL.md` §3.3, `..._ADDENDUM.md`) as well
 * — as the actual hit/miss signal Adherence (§4.2) computes against: "Computed from
 * MissionLogEntry presence/value against cadenceType and targetDirection." Batch G1's shipped
 * code (`06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §2.1) dropped both fields, leaving
 * only [note] — the Integration Plan's own field list simply didn't carry them over, an
 * unflagged divergence from the document it summarizes, not a deliberate simplification stated
 * anywhere. This was only caught while implementing Batch G3, which cannot compute a hit-rate
 * from a freeform string. See ROADMAP.md §5.36 for the full account.
 *
 * These two fields are read-only inputs to Adherence's hit-rate math (§4.2), never themselves
 * scored or written to Reputation/Debt directly — same "descriptive/measurement data feeds a
 * derived score, never the raw row itself" boundary [GoalMission.adherenceScore] already
 * enforces at the class level, restated here at the field level. Restated as a hard boundary,
 * not just framing: **no code path may read this table to compute Reputation, Discipline Debt,
 * or any [com.disciplineos.data.ledger.LedgerEntry]** — only [AdherenceLedgerEntry] may derive
 * from it, and that table is itself barred from feeding Tier (§5.36/Integration Plan §4.1).
 *
 * Exactly one of [numericValue]/[didOccur] is expected to be non-null for any log entry that's
 * meant to count toward Adherence — [numericValue] for outcome-driven/numeric-behavior Missions,
 * [didOccur] for habit/constraint Missions with no number (base doc §3.3's own framing). Both
 * null is valid and means "a note-only entry, doesn't count toward the hit-rate" — e.g. a
 * MissionLogEntry that's purely a freeform check-in. Both non-null is not rejected at the schema
 * level (no CHECK constraint) but isn't a state any known call site produces; the domain layer
 * (`ApplyAdherenceDecayUseCase`) is defensive about it rather than the entity enforcing it,
 * matching this codebase's existing preference for validation at the use-case boundary over
 * entity-level constraints (see e.g. `GoalMission`'s own kdoc: "this class does not attempt to
 * validate combinations").
 */
@Entity(tableName = "mission_log_entries")
data class MissionLogEntry(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val createdAt: Instant,
    val note: String?,
    val numericValue: Double? = null,
    val didOccur: Boolean? = null,
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
 * [conditionType]/[conditionValue] follow a jsonb-equivalent pattern for the same reason: the
 * set of possible trigger conditions is expected to grow, and a free-form value column avoids a
 * schema migration per new condition type. Real condition types, evaluation timing, and exactly
 * which UI surfaces a fired `Trigger` are Batch G5 scope
 * (`06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §6) — this entity only stores the
 * template, same division of responsibility as [MissionPeriod].
 */
enum class TriggerConditionType { INACTIVITY, SCHEDULE_MISS, MANUAL }

@Entity(tableName = "triggers")
data class Trigger(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val conditionType: TriggerConditionType,
    val conditionValue: String?,
    val active: Boolean,
    val lastFiredAt: Instant?,
)

/**
 * Data Model & Schema doc §2.2a. A progress checkpoint within a [GoalMission] with a numeric or
 * deadline-style target — e.g. "50% of target reached." **Descriptive only, same boundary as
 * [MissionLogEntry]** — a [Milestone] being hit or missed never feeds Reputation, Discipline
 * Debt, or any [com.disciplineos.data.ledger.LedgerEntry]; only [EnforcementSession]-level
 * violations do (see [GoalMission]'s class kdoc for the restated boundary this entity is bound
 * by). [achievedAt] null means not yet reached — a [Milestone] row always exists once defined,
 * whether or not it's been hit, so "not achieved" is representable without deleting/recreating
 * rows.
 */
@Entity(tableName = "milestones")
data class Milestone(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val label: String,
    val targetValue: String?, // interpretation depends on the parent GoalMission's target shape
    val achievedAt: Instant?,
)
