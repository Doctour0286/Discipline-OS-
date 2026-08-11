# Goal-Oriented Mission Model — Integration Plan

**Status: PROPOSED, downstream of `06_GOAL_ORIENTED_MISSION_MODEL.md`.** That document (which
supersedes `06_GOAL_ORIENTED_MISSION_MODEL_PROPOSAL.md` and
`06a_GOAL_ORIENTED_MISSION_MODEL_ADDENDUM.md`) took a position on every open fork and produced
one coherent target shape. This document is the next layer down: **how that shape actually
lands in this specific codebase** — file by file, migration by migration, batch by batch —
cross-checked against the real state of `main` as of this session (commit `698f3fe`), not the
repo as the proposal's authors last looked at it. Where this plan finds something the base
document's §6.6/§7/§8 didn't fully account for, that's called out explicitly, the same way
`06_...MODEL.md` itself called out where it overrode its predecessors.

Once reviewed, this plan's batches fold into `BUILD_PLAN.md` as new batches, and its schema
detail folds into `01_DATA_MODEL_AND_SCHEMA.md` §2 alongside the existing entities.

---

## 0. What this plan verified against the live repo, and why it matters

Everything below was checked against the actual files, not assumed from the proposal's own
description of them:

- **`Mission.kt`** (`data/src/main/java/com/disciplineos/data/entity/Mission.kt`) matches the
  base document's §3.6 `EnforcementSession` shape field-for-field. The rename is a pure rename;
  no field needs to change shape.
- **`FirstMissionSchedulingFragment.kt`** is confirmed as the *only* Mission-creation call site
  in the app today (`STATUS.md`'s own screen table agrees — it's the sole onboarding screen that
  inserts a `Mission` row). §6.6's account of it is accurate down to the two existing
  `[HYPOTHESIS]` items in its kdoc (`DEFAULT_PLANNED_DURATION_MIN`, reused `ACTIVE` status for a
  scheduled-not-started Mission) — **this plan does not resolve either of those**, they're
  orthogonal to this migration and stay exactly as flagged.
- **`RecordViolationUseCase.kt`** confirms the consequence-engine pattern this plan must not
  disturb: DAO composition, `database.withTransaction`, `ConsequencePolicy` for tier-dependent
  deltas, the shared-cause guard. This is the pattern `Adherence` (§4.2 of the base doc) must
  follow structurally, while writing to a genuinely separate ledger metric, not this one.
- **`DisciplineOsDatabase.kt`** is at `version = 8`, still on `fallbackToDestructiveMigration()`
  with an explicit, current, pre-launch justification in its own kdoc ("this app has never
  shipped to a real device"). **This is the single biggest simplifying fact for this migration**
  — every schema change below can be a destructive bump, no `Migration` objects need writing, for
  exactly the same reason ROADMAP.md §5.7 already gave for v2 through v8. This plan explicitly
  re-confirms that reasoning still holds (it does — `git log` shows no release tag, no store
  listing, nothing in `STATUS.md` suggesting an installed base exists) rather than silently
  assuming it.
- **PRD §13.4** was read directly, not taken on the base document's word — its actual text
  ("extending enforcement into a user's unscheduled time is the highest-risk way to reintroduce
  the surveillance-fatigue problem") supports the base document's §6.2 resolution precisely: the
  rejected case is the *product* deciding to watch someone who didn't ask, and an `ALWAYS_ON`
  Constraint-mission period is the person asking for exactly that, for exactly one named
  behavior. This plan proceeds on that resolution without relitigating it.
- **`ROADMAP.md`'s decision-log format** (§5.1 through §5.31) and **`STATUS.md`'s sync
  discipline** (`scripts/check_status_sync.sh`, CI-enforced) were both read in full. This plan's
  batch structure is written to slot into that existing convention, not invent a parallel one.

---

## 1. Batch structure

Following `BUILD_PLAN.md`'s own stated discipline — each batch its own branch off `main`, its
own PR, CI-green before the next starts. Six batches, largely matching the base document's §8
sequencing, but broken down to actual file-level work and given real dependency edges.

```
Batch G1 (rename + additive schema)         — no behavior change, pure structural migration
        │
        ▼
Batch G2 (Mission creation: new entities + FirstMissionSchedulingFragment fix)
        │
        ├──▶ Batch G3 (Adherence engine)             ─┐
        │                                              ├─ independent of each other,
        └──▶ Batch G4 (Mission detail screen: §4.1)   ─┘  both depend only on G2
                    │
                    ▼
        Batch G5 (Trigger UI + Hypothesizing/Reviewing lifecycle prompts)
                    │
                    ▼
        Batch G6 (Milestone — lowest priority, per base doc §F sequencing)
```

**Where this plan departs from the base document's own §8 ordering:** §8 step 5 says implement
Adherence "alongside the core entities, not deferred." This plan still does that in spirit —
G3 has no dependency on G4/G5/G6 and can start immediately after G2 — but splits it into its own
batch rather than folding it into G2, because G2 already carries a real logic change
(§6.6's `FirstMissionSchedulingFragment` fix) and this project's own batch discipline
("each batch sized so it's independently reviewable") argues against combining a schema
migration with a new scoring engine in one PR. This is a sequencing change, not a scope change —
nothing in §8 required them to be the same PR, only the same phase of work.

---

## 2. Batch G1 — Rename + additive schema (structural only, zero behavior change)

**Goal:** get `EnforcementSession`, `Mission`, `MissionPeriod`, `MissionLogEntry`, `Trigger`,
`Milestone` all existing as real Room entities, wired into `DisciplineOsDatabase`, with **every
existing call site updated to compile against the rename and nothing else** — no new UI, no new
use-cases, no behavior difference a user could observe. This isolates the highest-mechanical-
risk step (touching every file that currently says `Mission`) from any actual new logic, so a
compile break is trivially attributable to the rename, not tangled up with new code.

### 2.1 Entity changes

**`data/src/main/java/com/disciplineos/data/entity/Mission.kt`** — rename file and class:

- `Mission` → `EnforcementSession`, `MissionStatus` stays as-is (already lowercase-free, no
  rename needed — base doc §3.6 keeps the enum values unchanged).
- Add two new fields per base doc §3.6: `missionId: UUID` (non-null — see §2.4 below for why
  this can be non-null despite the rename happening in the same batch as the entities it
  references) and `missionPeriodId: UUID?`.
- Existing kdoc's `[scheduledStart]` / `[status]` / `[allowlist]` explanatory comments carry over
  unchanged — they're still accurate, just now describing `EnforcementSession` instead of
  `Mission`. Add one new kdoc paragraph documenting `missionId`/`missionPeriodId`, cross-
  referencing `01_DATA_MODEL_AND_SCHEMA.md` §2.2's post-migration text (§4 below).
- `OutputArtifact` (same file) is unaffected in shape — it already references `missionId`, which
  now points at an `EnforcementSession.id` rather than the old top-level `Mission.id`. **Flag
  this rename-of-meaning explicitly in `OutputArtifact`'s own kdoc**, since the field name
  `missionId` doesn't change but what it points to does — this is exactly the kind of silent
  semantic drift this project's own conventions (e.g. `MissionProfile.kt`'s documented history)
  argue should be called out in writing, not left for a future reader to discover by tracing
  foreign keys.

**New file `data/src/main/java/com/disciplineos/data/entity/GoalMission.kt`** — deliberately
*not* named `Mission.kt`, because that filename is taken by the renamed
`EnforcementSession.kt` in the same PR and Kotlin/Room have no issue with the file/class name
mismatch this creates, but a human reviewer tracing "where is `Mission` defined" benefits from
the file being findable by content, not by guessing which of two files kept the old name. (Open
naming question for actual sign-off, not decided unilaterally here — see §7.1.)

```kotlin
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
    val createdAt: Instant,
    val archivedAt: Instant?,
)
```

Table named `goal_missions`, not `missions` — `missions` is staying as `EnforcementSession`'s
table name (renaming the *table* on top of renaming the class would break nothing functionally
under destructive-fallback, but is an unforced churn with no benefit; Room doesn't require
table name to match class name, and the existing table already has this name in every prior
migration comment in `DisciplineOsDatabase.kt`'s history — leave it).

**New file `MissionPeriod.kt`, `MissionLogEntry.kt`, `Trigger.kt`, `Milestone.kt`** — direct
transcriptions of base doc §3.2–§3.5, with one resolved addition each needs that the base
document's shapes-not-final-field-lists caveat explicitly leaves to schema work:

- `MissionPeriod.periodType` includes `ALWAYS_ON` per base doc §6.2's resolution — the enum in
  §3.2 already lists it, no gap here.
- Every new entity gets a `@PrimaryKey val id: UUID` and the foreign key fields as plain `UUID`
  columns (not Room `@ForeignKey` constraints) — **matching this project's existing pattern**:
  `Mission.missionProfileId` today is a plain UUID column with no `@ForeignKey` annotation (
  confirmed directly in `Mission.kt`), and `Violation.missionId` follows the same pattern. This
  plan does not introduce FK constraints where none exist elsewhere in the schema — consistency
  with the established pattern over introducing enforcement Room itself doesn't already use.

### 2.2 DAO changes

New DAOs, one per new entity, matching the existing `MissionDao`/`MissionProfileDao` shape
(confirmed by reading `data/src/main/java/com/disciplineos/data/dao/` directly — insert/get/
query-by-user methods, `@Insert`/`@Query`, no `@Update` until something actually needs one,
matching `MissionProfileDao`'s own documented "no @Update yet" restraint cited in the base
document's §4.4 Milestone reasoning):

- `GoalMissionDao` — `insert`, `get(id)`, `forUser(userId)`, `mostRecentFor(userId)` (this last
  one mirrors `MissionProfileDao.mostRecentFor` exactly, and is what §2.4 below needs).
- `MissionPeriodDao` — `insert`, `forMission(missionId)`.
- `MissionLogEntryDao` — `insert`, `forMission(missionId)`, `forMissionSince(missionId, since)`
  (the latter is what Adherence's rolling-window computation in G3 needs — added now so G3
  doesn't need its own DAO-layer PR).
- `TriggerDao` — `insert`, `forMission(missionId)`.
- `MilestoneDao` — `insert`, `forMission(missionId)`, `update` (Milestone is the one new entity
  that needs `@Update`, since `achievedAt` is set after creation per base doc §3.5/§4.4 — this
  is a real, motivated exception to the "no @Update yet" pattern, not an unconsidered one).

### 2.3 Database wiring

**`DisciplineOsDatabase.kt`**:

- `Mission::class` → `EnforcementSession::class` in the `entities` array; add `GoalMission::class`,
  `MissionPeriod::class`, `MissionLogEntry::class`, `Trigger::class`, `Milestone::class`.
- Bump `version = 9`. New migration-comment block, following the exact prose pattern every prior
  bump uses (see v4 through v8 comments in the file today):

  ```kotlin
  // v9 (Goal-Oriented Mission Model, `06_GOAL_ORIENTED_MISSION_MODEL.md`): `Mission` renamed to
  // `EnforcementSession` (gains missionId/missionPeriodId — see EnforcementSession.kt kdoc);
  // new tables goal_missions, mission_periods, mission_log_entries, triggers, milestones added.
  // Same fallbackToDestructiveMigration reasoning as v2–v8 applies unchanged — still no real
  // installed base (confirmed this session: no release tag, no store listing, STATUS.md shows
  // no pilot phase started). Revisit before Phase 5 pilot per the existing standing note on
  // this function.
  ```
- Add the five new `abstract fun ...Dao(): ...Dao` declarations.
- Rename `abstract fun missionDao(): MissionDao` → `abstract fun enforcementSessionDao():
  EnforcementSessionDao` (and rename the DAO file/class itself the same way as the entity).

### 2.4 Every existing call site — mechanical updates only

Grep-confirmed list of files referencing `Mission`/`MissionDao`/`MissionStatus` as the
top-level entity (this plan ran the equivalent search against the live repo rather than trusting
the base document's own file list, which predates this session and could have drifted):

- `DebugSeeder` (test/seed infrastructure) — type reference only.
- `InterceptionController`, `MissionAccessibilityService` — type reference only; neither reasons
  about anything above session level today (confirmed — base doc §7's claim holds under direct
  inspection).
- `RecordViolationUseCase`, `ResolveDisputeUseCase`, `TierTransitionUseCase`, their test files —
  type reference only (`missionDao.get(...)` → `enforcementSessionDao.get(...)`,
  `MissionStatus` import path unchanged since the enum itself doesn't move packages).
- `MissionInterceptionActivity`, `activity_mission_interception.xml` (string resources
  referencing "Mission" as a label) — **left as user-facing copy, not renamed.** The person using
  the app should keep seeing "Mission" for what happens during an enforcement window — that's
  the base document's own §0 framing ("enforcement sessions as one tool a Mission can use," not
  a word the person needs to learn a new vocabulary for). Only the Kotlin type name changes; UI
  copy stays "Mission" throughout, referring colloquially to whichever concept is contextually
  meant. This is a deliberate UX decision this plan is making, not an oversight — flagged for
  sign-off alongside the rest of this document (§7.2).
- `FirstMissionSchedulingFragment` — **not** a pure mechanical update; see Batch G2, §3 below,
  since this is the one call site with a real dependency gap (base doc §6.6).

**What does not change in this batch:** no new screens, no new nav destinations, no changes to
`InterceptionController`'s actual logic, no changes to `ConsequencePolicy`. This batch's entire
CI-passing bar is "the app compiles and every existing test still passes with the rename in
place" — the same bar the base doc's §8 step 1 sets, made concrete against this repo's actual
file list.

---

## 3. Batch G2 — Mission creation + the real `FirstMissionSchedulingFragment` fix

**Depends on G1.** This is the batch that actually creates `GoalMission` rows and closes the gap
base doc §6.6 identified.

### 3.1 `FirstMissionSchedulingFragment.createMissionAndFinish`

Current code (confirmed, see §0 above) does one unconditional insert of an `EnforcementSession`
(post-G1 rename) with no parent to attach `missionId` to. Per base doc §6.6's resolution:

```kotlin
private fun createMissionAndFinish(scheduledStart: Instant?) {
    lifecycleScope.launch {
        val context = requireContext().applicationContext
        val database = AppContainer.database(context)

        val userId = database.userDao().getSingleLocalUser()?.id
        val profile = userId?.let { database.missionProfileDao().mostRecentFor(it) }

        if (userId == null || profile == null) {
            // unchanged — existing "Missing Mission Profile" handling
            ...
            return@launch
        }

        database.withTransaction {
            // NEW: auto-create the minimal parent GoalMission per §6.6
            val goalMission = GoalMission(
                id = UUID.randomUUID(),
                userId = userId,
                title = profile.name, // or a fixed default — see open question below
                archetype = MissionArchetype.BEHAVIOR_DRIVEN,
                targetDirection = null,
                targetValue = null,
                unit = null,
                cadenceType = CadenceType.NONE,
                resetMode = ResetMode.ROLLING_WINDOW, // see §3.3 below — this is a NEW judgment
                                                        // call the base doc's §6.6 doesn't settle
                measurementSource = MeasurementSource.AUTOMATIC,
                lifecycleStage = LifecycleStage.ENFORCING,
                adherenceScore = null,
                adherenceWindow = null,
                createdAt = Instant.now(),
                archivedAt = null,
            )
            database.goalMissionDao().insert(goalMission)

            val missionPeriod = MissionPeriod(
                id = UUID.randomUUID(),
                missionId = goalMission.id,
                periodType = PeriodType.FIXED_WINDOW, // see open question §3.3
                daysOfWeek = emptySet(),
                windowStart = null,
                windowEnd = null,
                targetDurationMin = null,
                deadlineTime = null,
                enforcementProfileId = profile.id,
            )
            database.missionPeriodDao().insert(missionPeriod)

            database.enforcementSessionDao().insert(
                EnforcementSession(
                    id = UUID.randomUUID(),
                    missionId = goalMission.id,           // NEW — the FK that had nothing to
                                                            // attach to before this batch
                    missionPeriodId = missionPeriod.id,    // NEW
                    userId = userId,
                    scheduledStart = scheduledStart,
                    actualStart = Instant.now(),
                    actualEnd = null,
                    plannedDurationMin = DEFAULT_PLANNED_DURATION_MIN, // UNCHANGED — existing
                                                                          // [HYPOTHESIS], not
                                                                          // touched by this plan
                    status = MissionStatus.ACTIVE,
                    allowlist = profile.allowlist,
                    blocklist = profile.blocklist,
                    missionProfileId = profile.id,
                )
            )
        }

        findNavController().navigate(R.id.action_firstMissionScheduling_to_home)
    }
}
```

### 3.2 What this does *not* change

- `DEFAULT_PLANNED_DURATION_MIN` stays exactly as it is — still `[HYPOTHESIS]`, still flagged in
  the Fragment's own kdoc, still not this plan's problem to resolve (base doc §6.6 doesn't ask
  this batch to resolve it either — the two open items are orthogonal).
- The "no re-entry guard" behavior is preserved deliberately: a second visit to this screen
  creates a second `GoalMission` + `MissionPeriod` + `EnforcementSession` triple, matching the
  existing documented behavior for a second `EnforcementSession` alone. This is arguably a new
  minor gap (a user who runs this flow twice now gets two generic "Focus Sessions"-titled Goal
  Missions, not just two sessions under one goal) — **flagged as a genuinely new open question**,
  not present in any prior draft, in §7.3 below.
- The `Instant`/UUID/transaction patterns match `RecordViolationUseCase`'s existing
  `database.withTransaction { }` usage exactly — no new transactional idiom introduced.

### 3.3 New open questions this batch surfaces (not resolved by the base document)

The base document's §6.6 resolves *that* a minimal `GoalMission` gets auto-created and roughly
*what* its fields should be ("no target," "BEHAVIOR_DRIVEN," "ENFORCING"), but doesn't specify
every field, because it was written at the shape level. Two fields have no natural default
stated anywhere upstream and need an explicit call, flagged here the way this project's
convention requires rather than silently picked:

- **`resetMode`**: base doc §6.1 says this field has "no natural universal default" and must be
  set explicitly per Mission — but that resolution was written for a person deliberately
  creating a Mission, not for an auto-generated placeholder one. `ROLLING_WINDOW` is proposed
  above as the least-commitment choice (it doesn't imply a calendar-anchored target the way
  `FIXED_CALENDAR` would for a Mission that has no target at all), but this is this plan's own
  judgment call, not derived from §6.1 or §6.6. `[HYPOTHESIS]`, log alongside this document's
  other decisions if accepted.
- **`MissionPeriod.periodType = FIXED_WINDOW`** with `windowStart`/`windowEnd` both left `null`:
  the base doc's `MissionPeriod` shape (§3.2) declares `windowStart`/`windowEnd` as "FIXED_WINDOW
  fields," implicitly non-null when that type is chosen, but the auto-generated period from
  onboarding has no window — the person picked "Start now" or a single scheduled instant, not a
  recurring clock window. Using `FIXED_WINDOW` with null bounds is a **type/data mismatch this
  plan is knowingly introducing**, not silently. Two real alternatives, genuinely open:
  (a) accept the mismatch, document it as "this MissionPeriod exists only to carry
  `enforcementProfileId`, its window fields are meaningless for auto-generated periods"; (b) add
  a fourth `periodType` value (e.g. `AD_HOC`) specifically for this case. This plan does not pick
  between them — flagged for the same sign-off track as the rest of this section (§7.4).

---

## 4. Batch G3 — Adherence engine

**Depends on G1 only** (needs `MissionLogEntryDao.forMissionSince` from G1's §2.2, and
`GoalMission.adherenceScore`/`adherenceWindow` fields from G1's §2.1). Independent of G2.

### 4.1 `ApplyAdherenceDecayUseCase` — new use-case, sibling to `ApplyReputationDecayUseCase`

Following the existing `ApplyReputationDecayUseCase` pattern directly (read in full during this
session's investigation) rather than inventing a new shape:

- Computes a hit-rate over `GoalMission.adherenceWindow` days from `MissionLogEntryDao
  .forMissionSince(missionId, windowStart)`, checked against `cadenceType`/`targetDirection` —
  matching base doc §4.2's "a straightforward hit-rate, not a new formula category."
- Decays `adherenceScore` on sustained miss patterns only, mirroring
  `ReputationDecayPolicy`'s `consecutiveDaysBelowFloor`-style tracking rather than a same-day
  penalty — this needs a new field, `GoalMission.consecutiveWindowsBelowThreshold` (naming
  mirrors `User.consecutiveDaysBelowFloor` exactly, for the same reason `ApplyReputationDecay
  UseCase`'s own kdoc gives: consistency of pattern across the two decay-based systems this
  project now has). **This field is a small, motivated addition to G1's `GoalMission` shape not
  present in the base document's §3.1** — flagged in §7.5 as a real (small) deviation from the
  base doc's stated field list, justified because §4.2 explicitly requires decay-on-sustained-
  pattern and there is no way to compute that without storing *some* running state, the same
  reason `User` itself carries `consecutiveDaysBelowFloor`.
- **Writes to a new `AdherenceLedgerEntry` table, structurally parallel to `LedgerEntry` but
  physically separate** — not a new `LedgerMetric` enum value on the existing `LedgerEntry`/
  `LedgerDao`. This is the load-bearing structural decision for honoring base doc §4.2's "never
  feeds Tier" / "separate, never merged" requirement as a *schema* fact, not just a convention
  a future engineer has to remember to respect. This mirrors exactly the reasoning
  `DisciplineOsDatabase.kt`'s own kdoc gives for why `UnsupervisedDatabase` is a physically
  separate database rather than an access-controlled table in the same one ("Data Model doc §7
  requires that 'no enforcement path can touch this' be structurally true, not just
  policy-true"). Adherence isn't isolated to the same degree as Unsupervised data (it's shown
  in-app, unlike Unsupervised signal categories) — a separate *table* in the same database is
  sufficient here, not a separate database; the point is only that `LedgerDao`'s existing
  queries (used by `ConsequencePolicy`, Tier transitions, Debt Ceiling math) cannot accidentally
  pick up an Adherence entry just because it shares a table with `DEBT`/`REPUTATION` metric rows.

### 4.2 Weekly Report callout hook

Base doc §4.2 specifies Adherence "surfaced as a Weekly Report callout when it crosses a decay
threshold." **Weekly Reports do not exist yet** — confirmed against `STATUS.md`'s MVP table
("Daily / Weekly Reports | ⬜ | Not started"). This plan does not build Weekly Reports as part of
this integration — that's already tracked as its own item in `BUILD_PLAN.md` Batch F. What this
batch does instead: `ApplyAdherenceDecayUseCase` returns a result type that *includes* whether a
decay-threshold crossing occurred (mirroring `RecordViolationUseCase.Result`'s own pattern of
returning what was written so callers don't re-derive it), so that whenever Batch F actually
builds Weekly Reports, the hook is a a read of this existing result type, not new computation.
**This is a real sequencing dependency the base document's §8 doesn't name**: §4.2's "drives:
Weekly Report callout" clause has no Weekly Report to attach to yet, and this plan is explicit
that the callout itself ships with Batch F, not with G3 — G3 ships the score and the decay
mechanism; the surface it's shown on is still `⬜` until F.

### 4.3 `[HYPOTHESIS]` constant

`adherenceWindow`'s default value (if a Mission doesn't set one explicitly) and the exact decay
formula are both explicitly `[HYPOTHESIS]` per base doc §4.2's own closing paragraph. This batch
does not invent numbers — it wires the mechanism with the constant visibly marked
`[HYPOTHESIS]` in code, same as `plannedDurationMin`'s 25-minute default and `DebtCeiling`'s
14-day window both already are in the existing codebase.

---

## 5. Batch G4 — Mission detail screen (§4.1 relationship view)

**Depends on G2** (needs real `GoalMission` rows to exist, and ideally G3 merged first so
Adherence has data to show — base doc §4.1's own resolved position computes "behavior followed"
from `adherenceScore`, so this screen has nothing to render for that axis without G3).

- New screen, `ui/mission/MissionDetailScreen.kt` + `mission/MissionDetailFragment.kt`, matching
  the existing Compose-hosted-via-Fragment pattern (`themedComposeView`, per §5.29's
  deduplication — this plan reuses that helper, doesn't reintroduce the old per-Fragment
  boilerplate it replaced).
- Renders the four-quadrant read from base doc §4.1 as **plain-language text, not a chart
  widget** for v1 — the base doc's own emphasis ("not two overlaid line charts left for the user
  to interpret unassisted... that interpretation is the actual value delivered") argues for
  prose over visualization as the MVP bar; a chart can be added later without changing the
  underlying computation.
- Computation itself: a new pure function (matching `computeHomeState`'s existing pattern in
  `HomeFragment` — pure function taking plain data, unit-testable without Robolectric), combining
  `MissionLogEntryDao` outcome trend + `GoalMission.adherenceScore` per base doc §4.1's resolved
  position (one number across all periods, not per-period).
- **Real new nav destination required**: no entry point to a Mission's detail screen exists
  anywhere in the app today (confirmed — `HomeScreen.kt`, per §5.31's own description, shows
  "current tier + Iron eligibility card only," nothing Mission-specific). This batch needs to add
  a Mission list/card to `HomeScreen` as the entry point, which is scope the base document's §8
  migration shape doesn't mention at all — flagged in §7.6 as a real gap in the base document's
  own sequencing, not something this plan is inventing unprompted (some screen has to link to
  the detail screen, or it's unreachable).

---

## 6. Batch G5 — Trigger UI + lifecycle prompts

**Depends on G2, benefits from G4 existing** (the Hypothesizing-stage prompt described in base
doc §5/§4.3 is most naturally surfaced from the Mission detail screen G4 builds).

- Post-creation, dismissible prompt (mirrors Mission Profile Drift Detection's existing pattern,
  PRD §8.1 — read this section to confirm the actual UI convention before building, not just
  the base doc's description of it) offering to attach a `Trigger`, shown once per Mission during
  `HYPOTHESIZING`, matching base doc §4.3's resolved position exactly (optional, offered more
  assertively than a bare mention but never mandatory, no hard cap on count).
- `Trigger` creation form: cue type selector + free-text cue/response description. `APP_OPEN`
  cue type on a `CONSTRAINT`-archetype Mission is, per base doc §6.2's resolution, sugar over
  `ALWAYS_ON` `MissionPeriod` creation — **this UI path must call the same period-creation code
  as a direct `ALWAYS_ON` period would**, not a separate enforcement path. This is the one place
  in this plan with real correctness risk if implemented naively (two code paths that both claim
  to "block this app always" is exactly the duplication base doc §6.2's resolution explicitly
  warns against) — worth a dedicated small use-case,
  `CreateConstraintTriggerUseCase(missionId, packageId, cueDescription)`, that internally just
  builds a `MissionPeriod(periodType = ALWAYS_ON, enforcementProfileId = ...)` plus a `Trigger`
  row for the descriptive text, in one transaction, rather than two independently-written UI
  handlers that both happen to produce the right rows.
- Lifecycle-stage transitions (`OBSERVING` → `HYPOTHESIZING` after N logs, per base doc §5's
  `[HYPOTHESIS]`-flagged threshold) computed the same way `ironCalibrationSatisfied` is today —
  a pure function, reused rather than re-derived at each call site (matching §5.31's own stated
  reasoning for why it built `ironCalibrationSatisfied` as a shared pure function in the first
  place).

---

## 7. Milestone (G6) — sequenced last, per base document §F

Base doc's own §F sequencing (in the addendum, folded into the final document's ordering intent)
ranks Milestone below Trigger and Adherence in priority. This plan agrees and sequences it last.
No new integration risk beyond what's already stated in base doc §4.4 — `Milestone` is read-only
relative to `MissionLogEntry`, no new write path, no consequence-path interaction. Implementation
is a `MilestoneDao.forMission` query plus a background/on-read check (`achievedAt` computed when
a new `MissionLogEntry` crosses the threshold) triggered from the same code path that already
writes a `MissionLogEntry` — no new service, no new scheduled job.

---

## 8. Open questions this integration pass surfaced, beyond what the base document resolved

The base document (`06_GOAL_ORIENTED_MISSION_MODEL.md`) is explicit that it resolves every fork
its own predecessors raised. Actually mapping it onto this specific codebase surfaced a handful
of narrower, implementation-level questions the base document had no way to anticipate because
they only exist once you look at real files. Listed here, in the same spirit as the base
document's own §6 — flagged for sign-off, not decided unilaterally:

### 7.1 Naming collision: `Mission.kt` is claimed by the renamed `EnforcementSession`

The new goal-level entity needs a file name. `GoalMission.kt` (this plan's proposal) versus
naming the *file* `Mission.kt` and the *class* something else, versus renaming the class to
plain `Mission` and finding a different name for the renamed old entity (e.g. keeping the file
`Mission.kt` for the new goal entity and putting `EnforcementSession` in
`EnforcementSession.kt` — which is what this plan actually did in §2.1, but is worth stating
as a deliberate choice rather than the only option). Low-stakes, easy to bikeshed, genuinely
open.

### 7.2 UI copy: does "Mission" as user-facing vocabulary silently split from the Kotlin type name?

§2.4 proposes leaving all existing UI strings saying "Mission" alone, on the theory the base
document's own framing supports colloquial ambiguity. This is a real product-voice decision, not
a technical one — worth explicit confirmation rather than this plan's own default holding by
default.

### 7.3 Double-run of First Mission Scheduling creates two `GoalMission` rows, not one shared one

Not present in any prior draft. A user who runs onboarding's last screen twice (documented
today as intentional, non-buggy behavior for `EnforcementSession` alone) now also gets two
auto-generated, identically-generic-titled `GoalMission` rows once G2 ships. Whether that's fine
(matches existing "second visit = second real thing" philosophy) or worth a guard (e.g. reuse
the most recent `OBSERVING`/`ENFORCING` `GoalMission` for the same user if one exists) is a real,
new open question this plan surfaces rather than resolves.

### 7.4 `MissionPeriod.periodType` for an auto-generated onboarding period

Detailed in §3.3 above — `FIXED_WINDOW` with null window bounds, versus a new `AD_HOC` period
type. Genuinely open.

### 7.5 `GoalMission.consecutiveWindowsBelowThreshold` — a field not in the base document's §3.1

Small, motivated addition needed to make Adherence's decay-not-single-miss requirement (§4.2)
actually computable, following the existing `User.consecutiveDaysBelowFloor` pattern. Flagged as
a deviation from the base document's stated field list, not silently added.

### 7.6 Mission detail screen needs a real entry point; nothing in the base document's §8 names this

`HomeScreen` currently shows nothing Mission-specific. G4 needs to add a Mission list/card to
Home as a prerequisite for the detail screen being reachable at all — real, small scope this
plan is naming explicitly because the base document's migration shape (§8) stops at "build the
view," not "make it navigable from anywhere."

---

## 9. What this plan deliberately does not do

Matching the base document's own §7/§E "what does not change" sections, restated at the
integration-planning level:

- **No changes to `ConsequencePolicy`, `InterceptionController`, `MissionAccessibilityService`,
  `TierTransitionUseCase`'s actual transition logic, or `ReputationDecayPolicy`.** Every one of
  these is confirmed, by direct reading this session, to reason only about
  `EnforcementSession`/`Violation`/`User` — none of them need to know `GoalMission` exists at
  all, and this plan does not add that knowledge anywhere in that layer.
- **No `Migration` objects.** Confirmed still-valid reasoning in §0 above — this stays a
  destructive version bump, matching v2 through v8.
- **No resolution of the two pre-existing `[HYPOTHESIS]` items** in `FirstMissionScheduling
  Fragment` (`DEFAULT_PLANNED_DURATION_MIN`, reused `ACTIVE` status) — orthogonal to this work,
  left exactly as flagged.
- **No Weekly Report build-out.** G3 wires the hook; the report surface itself is `BUILD_PLAN.md`
  Batch F's job, unchanged by this plan.
