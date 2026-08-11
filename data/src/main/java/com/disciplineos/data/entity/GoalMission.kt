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
 *
 * [triggerPromptDismissedAt] — added Batch G5, another small motivated field addition in the
 * same category as [consecutiveWindowsBelowThreshold] above. Base doc §4.3 / Integration Plan
 * §6: the "attach a Trigger?" prompt on Mission Detail is shown "once per Mission during
 * HYPOTHESIZING." That's per-Mission current-state, not an event log — no other row in this
 * schema needs to exist just to remember "did the person already dismiss this," so a nullable
 * timestamp field directly on [GoalMission] is simpler than a whole new dismissal table (the
 * alternative considered: mirroring
 * [com.disciplineos.data.entity.PredictiveFailureAlertDismissal]'s shape — rejected because
 * that table's `ruleId`-keyed shape answers a different question, "which of several rule types
 * fired," where this is a single boolean-shaped fact about one Mission). Null means never
 * dismissed; non-null is the dismissal timestamp, kept (not just a `Boolean`) in case a future
 * pass wants to know *when* without a second migration.
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
    val triggerPromptDismissedAt: Instant? = null,
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
 * Data Model & Schema doc §2.2a; base design doc §3.4/§4.3 (`06_GOAL_ORIENTED_MISSION_MODEL.md`,
 * restored content one commit before the pointer reduction — see that file's own kdoc).
 *
 * **Rebuilt this pass (ROADMAP.md §5.41) — the shape that shipped with Batch G1 was a different
 * entity entirely, not a variant of this one.** What actually shipped
 * (`TriggerConditionType { INACTIVITY, SCHEDULE_MISS, MANUAL }`, `conditionValue: String?`,
 * `active: Boolean`, `lastFiredAt: Instant?`) modeled a session-inactivity watchdog — "if no
 * session has run in N days, prompt." That is not what base doc §3.4/§4.3 specify for `Trigger`
 * at all: the base doc's `Trigger` is an **implementation-intention cue** (Gollwitzer's
 * "if-then" plans, d = 0.65 across a 94-study meta-analysis per §4.3) — a cue-response pair like
 * "when I finish dinner (cue), open the reading app (response)," entirely independent of whether
 * any `EnforcementSession` has or hasn't run. The two entities share almost no real fields and
 * answer different questions; this was not a narrower version of the spec, it was the wrong
 * entity, following the exact same "diverged from the document it was meant to summarize, only
 * caught while implementing the batch that actually needed the real shape" pattern §5.36/§5.37
 * already found twice before in this project (`MissionLogEntry.numericValue`/`didOccur`, and
 * `ApplyAdherenceDecayUseCase.Result`'s scope gate). See §5.41 for the full account of how this
 * was found and why fixing it in place (rather than adding a second, correctly-shaped entity)
 * was the right call: nothing in `:domain`/`:app` reads or writes the pre-fix shape as of this
 * pass — `TriggerDao.insert`/`forMission` exist but are called nowhere — so there is no call
 * site to migrate and no data to lose under this project's still-in-effect
 * `fallbackToDestructiveMigration()` policy.
 *
 * [cueType] — all values except [TriggerCueType.APP_OPEN] are **not independently
 * phone-enforceable**: structure for the person's own plan and material for a reminder/nudge
 * surface, not a new interception mechanism (§4.3). This keeps the addition inside PRD §13.4's
 * existing boundary (measurement/prompting only, outside a real `EnforcementSession` window).
 *
 * [cueType] = [TriggerCueType.APP_OPEN] **on a [MissionArchetype.CONSTRAINT] mission is sugar
 * over an [MissionPeriod.periodType] = `ALWAYS_ON` period with [cueTriggerPackageId] on its
 * blocklist** — base doc §6.2's resolved position, restated here since it's the one place this
 * entity's shape has real correctness risk if callers get it wrong: **this `Trigger` row must
 * never be treated as its own, second enforcement mechanism.** The actual blocking behavior is
 * always delegated to a real `MissionPeriod`/`enforcementProfileId`; this row only ever carries
 * the person's own descriptive cue/response text. See
 * [com.disciplineos.domain.usecase.CreateConstraintTriggerUseCase] (Batch G5) for the one
 * sanctioned call site that creates both rows together, in one transaction, so no second,
 * independently-written path can accidentally reimplement "block this app always" a second way.
 *
 * [active] — a small, motivated addition beyond base doc §3.4's field list, same category and
 * same justification this project already gives every other small flagged addition
 * (`GoalMission.consecutiveWindowsBelowThreshold`, `MilestoneDao`'s `@Update` exception): lets a
 * person deactivate a Trigger they're no longer using without deleting the row (and losing their
 * own cue/response text), the same "descriptive, not itself scored" posture this table already
 * has. `[HYPOTHESIS]`-style flag, not silently assumed: base doc §3.4 states no field like this;
 * if it should be removed rather than kept, that's a real product decision, not an oversight.
 */
enum class TriggerCueType { TIME_OF_DAY, PRECEDING_EVENT, LOCATION, APP_OPEN, MANUAL }

@Entity(tableName = "triggers")
data class Trigger(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val cueType: TriggerCueType,
    val cueDescription: String,
    val responseDescription: String,
    val createdAt: Instant,
    val missionPeriodId: UUID? = null,
    val cueTimeOfDay: LocalTime? = null,
    val cuePrecedingMissionId: UUID? = null,
    val cueLocationLabel: String? = null,
    val cueTriggerPackageId: String? = null,
    val active: Boolean = true,
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
 *
 * [targetValue]: [Double]?, matching Addendum §B.2's field list exactly ("targetValue: Double?
 * — null = ordinal-only checkpoint"). This shipped as `String?` in the original G1 schema pass
 * with a comment deferring interpretation to the parent [GoalMission]'s target shape — an
 * unflagged divergence from the spec, not a deliberate choice; a numeric-comparison field has no
 * legitimate reason to be a `String`. Fixed here rather than worked around, same treatment this
 * project has already given two prior instances of this exact category of finding
 * ([MissionLogEntry.numericValue], the [Trigger] entity shape fix). Confirmed zero call sites
 * constructed a [Milestone] anywhere in the codebase at the time of this fix, so there was no
 * pre-fix shape to migrate.
 *
 * [targetDate]: [Instant]?, also per Addendum §B.2 ("targetDate: Instant | null — null =
 * milestone is ordinal only ('halfway'), not date-bound"). Missing entirely pre-fix; added here
 * alongside [targetValue]'s type correction since both are the same spec's field list and both
 * were absent/wrong for the same reason (never revisited since the original G1 pass).
 */
@Entity(tableName = "milestones")
data class Milestone(
    @PrimaryKey val id: UUID,
    val missionId: UUID,
    val label: String,
    val targetValue: Double?,
    val targetDate: Instant?,
    val achievedAt: Instant?,
)
