# Goal-Oriented Mission Model — Proposal

**Status: PROPOSED, not yet decided.** This document is a design proposal, not a spec. Nothing
in it should be treated as authoritative until it is reviewed, adjusted, and signed off the same
way §5.5/§5.9/§5.10/§5.15 were (see `ROADMAP.md`) — at which point the relevant pieces get
folded into `DisciplineOS_PRD_v3_6.md` and `01_DATA_MODEL_AND_SCHEMA.md` directly, and this file
is either deleted or reduced to a historical pointer at the decision log entry that replaced it
(expected to land as `ROADMAP.md` §5.32 or later).

**Why this document exists separately, for now:** the idea below is a real extension of what's
built, not a bug fix or a small gap-fill — it changes what the top-level unit of the product
*is*. Landing it directly into the PRD/Data Model doc without a discrete review step would bury
a load-bearing decision inside routine spec prose. This file exists to make the decision
reviewable as one coherent thing first.

---

## 0. One-sentence summary

**A Mission is currently an enforcement session (a scheduled, time-boxed, app-blocking period).
This proposal makes a Mission a goal — something the user is trying to accomplish or sustain —
with enforcement sessions as one optional tool a Mission can use, not what a Mission is.**

The existing enforcement engine (Tiers, Debt, Reputation, Interception, Violations) does not
get rebuilt. It gets *rehomed* — from being the top-level object to being a child mechanism a
goal-level Mission can attach.

---

## 1. Why the current model is too narrow

### 1.1 What's actually built today

`Mission` (see `01_DATA_MODEL_AND_SCHEMA.md` §2.2) is: a `scheduledStart` (nullable single
instant), an `actualStart`/`actualEnd`, a `plannedDurationMin`, a `status`, an allowlist/
blocklist, and a link to a `MissionProfile` (a named allow/blocklist bundle). It is, in
practice, **one scheduled focus session with an app-blocker attached.** Nothing above it
represents *why* the session exists or what it's supposed to accomplish beyond itself.

### 1.2 What a person actually means by "a discipline goal"

Real examples the product should be able to represent, none of which fit the current model
without distortion:

- Reduce weight from X to Y (a real-world numeric outcome, manually logged, no phone signal at
  all unless a connected scale/app exists)
- Hit a monthly revenue target (numeric outcome, manually logged, weekly or custom cadence)
- Read N pages / N books (numeric outcome, could be manually logged or, if reading happens in
  an app, partially inferable from usage)
- Spend N hours/week on a specific pursuit (a duration target, with a repeatable time window or
  a floating same-day/same-week target)
- Avoid a specific habit — gambling, doom-scrolling, smoking — which may be entirely on-phone
  (directly monitorable/blockable) or entirely offline (requires a log, no phone signal exists)
- Build a habit — meditate daily, stretch every morning — same on-phone/offline split as above

None of these are "a scheduled focus session." Several have no phone-enforceable session at
all. Several need a manually-entered number that persists and trends over time. None of this
exists in the current schema.

### 1.3 The actual seam connecting all of these

Every example above is the same underlying shape: **close a gap between where you are and where
you want to be, over time, with some mechanism forcing honesty about the gap.** What differs
between them is only:

1. **Where the truth comes from** — the phone can observe it directly, the user has to log it,
   or some blend of both.
2. **How causally close enforcement is to the outcome** — blocking a doom-scroll app is a direct
   intervention on a screen-time goal, but only a weak, correlational lever on a revenue goal
   (it removes a plausible distraction, it doesn't make sales happen).
3. **How urgent a single miss is** — a missed log today is noise for a weight/revenue trend; a
   missed focus block might matter same-day. Reacting to single-day misses the way the current
   Debt/Reputation system reacts to a missed Mission would be wrong for most of these.

This is why the fix isn't "add a target field to Mission" — it's introducing a real layer above
the current Mission entity that separates **the goal**, **the behavior hypothesized to drive
it**, and **the enforcement mechanism**, which today are all fused into one object.

---

## 2. The three mission archetypes

Not a data-typing distinction — a distinction in what's driving the mission and what closes the
loop on whether it's working. The UI, success framing, and scoring should differ by archetype;
forcing all three into one generic form produces something too abstract to actually fill in.

### 2.1 Outcome-driven missions

*"Hit $10k revenue this month." "Get to 75kg." "Read 12 books this year."*

- A numeric outcome is the north star: a value, a direction (increase/decrease/maintain), and a
  cadence for re-checking it (daily/weekly/custom).
- One or more **behaviors** can be attached underneath as the hypothesized cause ("2h/day
  prospecting," "no snacking after 8pm," "20 pages/day"). Each behavior can carry its own
  enforcement period, or none.
- **Behaviors are optional and addable at any time — not required at creation.** See §5.
- The outcome trend and the behavior-adherence trend are tracked and *shown* as two separate,
  explicitly related signals — see §4.

### 2.2 Behavior-driven missions (no separate outcome)

*"Read 20 pages/day." "Gym 4x/week." "2 hours of deep work daily."*

- The behavior **is** the goal. There is no separate outcome number to decouple from it — this
  is the outcome-driven case where outcome = behavior, simplified.
- This is the most common case and the closest to what's already built — it's the existing
  Mission concept, generalized to have a cadence and (optionally) a repeatable period instead
  of being one-shot.

### 2.3 Constraint missions

*"No social media after 9pm." "Never open the gambling app." "Don't smoke."*

- Not building toward a target — holding a boundary. Success is an absence being sustained, not
  a number increasing.
- This is where blocking is the *most* directly causal of all three archetypes — the behavior
  being prevented and the thing being blocked are usually the same thing.
- May be entirely on-phone (blockable/monitorable directly) or entirely offline (log-only, no
  phone signal exists at all — e.g. "don't smoke").
- **Open question, flagged explicitly, not resolved here (see §6.2):** whether a constraint
  mission's on-phone enforcement should be allowed to run *always*, rather than only inside a
  scheduled period — which would cross a boundary the PRD has already drawn once, on purpose
  (§13.4's rejection of any enforcement outside a Mission window).

---

## 3. Proposed entities

These are shapes, not final field lists — exact types/nullability should be settled during
actual schema work, not frozen here.

### 3.1 `Mission` (redefined — this is the breaking change)

```
Mission {
  id: UUID
  userId: UUID
  title: String
  archetype: enum[OUTCOME_DRIVEN, BEHAVIOR_DRIVEN, CONSTRAINT]
  targetDirection: enum[INCREASE, DECREASE, MAINTAIN] | null   // null for pure habit/constraint missions with no number
  targetValue: Double | null
  unit: String | null                // free-form: "kg", "$", "pages", "hours" — display-only, not parsed
  cadenceType: enum[DAILY, WEEKLY, CUSTOM_DAYS, NONE]
  resetMode: enum[FIXED_CALENDAR, ROLLING_WINDOW]   // per-mission choice, not global
  measurementSource: enum[AUTOMATIC, MANUAL_LOG, BOTH]
  lifecycleStage: enum[OBSERVING, HYPOTHESIZING, ENFORCING, REVIEWING]   // see §5
  createdAt: Instant
  archivedAt: Instant | null
}
```

`lifecycleStage` is new and not cosmetic — see §5 for why a Mission needs to represent "I'm just
watching this for now" as a legitimate, non-transient state rather than assuming every Mission
immediately has an intervention attached.

### 3.2 `MissionPeriod` (new — zero or more per Mission)

The **behavior/enforcement layer**. A Mission can have none (pure outcome logging or a pure
log-only habit), one, or several (per the person's own stated preference: a mission may combine
a fixed daily window *and* a separate floating same-day target).

```
MissionPeriod {
  id: UUID
  missionId: UUID
  periodType: enum[FIXED_WINDOW, FLOATING_DEADLINE]
  daysOfWeek: Set<DayOfWeek>                 // repeatable
  // FIXED_WINDOW fields:
  windowStart: LocalTime | null
  windowEnd: LocalTime | null
  // FLOATING_DEADLINE fields:
  targetDurationMin: Int | null
  deadlineTime: LocalTime | null             // e.g. "before 23:59," or a custom cutoff
  enforcementProfileId: UUID | null          // → MissionProfile (allow/blocklist); null = no phone enforcement, tracked/logged only
}
```

### 3.3 `MissionLogEntry` (new — manual logging)

```
MissionLogEntry {
  id: UUID
  missionId: UUID
  loggedAt: Instant
  numericValue: Double | null      // for outcome-driven / numeric behavior missions
  didOccur: Boolean | null         // for habit/constraint missions with no number
  note: String | null
}
```

Logging frequency (daily/weekly/custom) is a **UI prompt derived from `cadenceType`**, not a
hard constraint — the user can log whenever they actually have data, the app just reminds them
on the stated cadence.

### 3.4 `EnforcementSession` (renamed from today's `Mission`)

Everything the current `Mission` entity already does stays essentially unchanged — it becomes a
child record instead of the top-level object:

```
EnforcementSession {
  id: UUID
  missionId: UUID              // NEW — the goal this session serves
  missionPeriodId: UUID | null // NEW — which period triggered/defined this session, if any
  userId: UUID
  scheduledStart: Instant?
  actualStart: Instant
  actualEnd: Instant?
  plannedDurationMin: Int
  status: enum[active, completed, violated, disputed, aborted_crisis_exit]
  allowlist: [package_id]
  blocklist: [package_id]
  missionProfileId: UUID
  outputArtifacts: [OutputArtifact]
}
```

**Everything downstream of this entity — `InterceptionController`, `MissionAccessibilityService`,
`Violation`, Debt, Reputation, Tier transitions — keeps working exactly as built**, scoped to
`EnforcementSession` instead of the old top-level `Mission`. This is the load-bearing reason the
migration is additive, not a rewrite: the existing, already-verified consequence engine doesn't
need to change shape, only its parent reference.

### 3.5 Computed: Mission progress and the outcome/behavior relationship view

Not a stored entity — a derived view, computed from `MissionLogEntry` + `EnforcementSession`
history against `targetValue`, using `resetMode` to decide the window (fixed calendar boundary
or trailing rolling window, per §6.1's resolution).

For outcome-driven missions specifically, this view tracks **two trends side by side, not one
merged score**: outcome movement, and behavior/period adherence. See §4.

---

## 4. The outcome/behavior relationship view (outcome-driven missions only)

This is a deliberate product decision, not a chart nicety. The reasoning:

Most habit-tracking failure looks like this: someone logs an outcome (weight, revenue) and
expects the log itself to be motivating — it isn't, because a number with no visible cause
attached doesn't tell you what to do differently. Most focus-app failure looks like the reverse:
strict behavior enforcement with zero connection to any outcome the person actually cares about,
so compliance holds only as long as the fear of being blocked does, and evaporates once that
stops feeling urgent.

Keeping outcome and behavior as two separately tracked trends, shown together with a
plain-language read, is the single highest-value thing this feature can do that isn't already
done well by a habit tracker or a focus app alone:

- **Behavior followed, outcome moving as expected** → reinforce, no action needed.
- **Behavior followed, outcome flat/wrong-direction** → the hypothesis linking behavior to
  outcome may be wrong. This is valuable, non-obvious information — the person did everything
  right and it still isn't working, which is a reason to change the behavior, not a personal
  failure. The product should say this plainly, not just show two flat lines.
- **Behavior skipped, outcome flat/wrong-direction** → expected; no surprise, tighten adherence.
- **Behavior skipped, outcome moving anyway** → the hypothesized behavior may not actually be
  the driver; worth reconsidering what's really causing the outcome to move.

This four-quadrant read (or something materially like it) should be a first-class, plain-
language surface on the Mission detail screen — not two overlaid line charts left for the user
to interpret unassisted. That interpretation is the actual value delivered.

---

## 5. Mission lifecycle: observe → hypothesize → enforce → review

A Mission does not need a behavior/period attached to exist. Requiring one at creation forces
the person to already know their hypothesis before they're allowed to start measuring, which is
often untrue — someone starting a weight goal frequently needs a few weeks of just logging
before they know whether their current habits are even the problem.

Proposed stages (the `lifecycleStage` field in §3.1):

1. **Observing** — outcome-only logging, no behavior/period attached yet. A fully valid,
   permanent end state for a Mission if the person only ever wants to track, not intervene.
2. **Hypothesizing** — after a minimum number of outcome logs with no behavior attached (a
   small number, e.g. 2–3 — exact threshold is a `[HYPOTHESIS]`-style open constant, not fixed
   here), the app may surface a single, dismissible, non-scored prompt: "want to attach a
   behavior you think is driving this?" — same pattern already established by Mission Profile
   Drift Detection (PRD §8.1). Never mandatory.
3. **Enforcing** — one or more `MissionPeriod`s attached, at least one with a non-null
   `enforcementProfileId` actively blocking during its window.
4. **Reviewing** — not a separate stored state so much as a recurring point (Weekly/Monthly
   Report cadence, matching existing §32–§34 infrastructure) where the outcome/behavior
   relationship view (§4) is surfaced and the person decides whether to keep, adjust, or drop
   the attached behavior — looping back to Hypothesizing if they want to try something else.

No stage is mandatory to progress through in order except that Enforcing requires at least one
`MissionPeriod` to exist. A Mission can be created directly into Enforcing (the common case for
simple behavior-driven missions per §2.2) or sit in Observing indefinitely.

---

## 6. Open decisions requiring sign-off before implementation

Following this repo's own convention (see `ROADMAP.md` §5, and `STATUS.md`'s "Open decisions"
table) — these are real judgment calls, not implementation details, and shouldn't be decided
unilaterally in code.

### 6.1 Reset mode default and scope

**Resolved in discussion, recorded here for the sign-off record:** `resetMode` is per-mission,
not global — each Mission chooses `FIXED_CALENDAR` or `ROLLING_WINDOW` independently. No further
decision needed unless a default is wanted for missions that don't specify one.

### 6.2 Constraint missions and always-on enforcement — genuinely open, not resolved

The PRD's §13.4 already rejected *any* enforcement outside a scheduled Mission window, for a
stated reason (reactance/surveillance-fatigue risk, backed by the same literature that shaped
the tier system generally). Constraint missions (§2.3) — "never open the gambling app," with no
natural time window — sit outside that rule's original scope entirely, since they have no
window to be "outside of."

Three options, none pre-selected here:

- **(a)** Constraint missions require at least one `MissionPeriod`, same as any other archetype
  — enforcement is still window-bound, just potentially a very wide window (e.g. all day, every
  day). Simplest, no new principle, but may feel like a workaround rather than a real fit for
  "never."
- **(b)** Introduce a genuinely new period type, `ALWAYS_ON`, explicitly carving out a narrow
  exception to §13.4's blanket rule for constraint missions specifically, reasoned through on
  its own terms rather than silently extended.
- **(c)** Constraint missions never get phone-level blocking at all — only logging/tracking
  (didOccur), with blocking available only if the person separately builds a `MissionPeriod`
  around it, keeping §13.4's boundary fully intact and treating "always block this app" as a
  device-level setting outside the Mission model entirely.

This needs an explicit decision before any constraint-mission enforcement code is written — not
because the options are hard to build, but because option (b) revisits a boundary the PRD drew
deliberately, for a cited reason, and shouldn't be reopened as a side effect of building
something else.

### 6.3 Causal-distance labeling for outcome-driven missions

For outcome-driven missions, an attached behavior's enforcement is sometimes directly causal
(blocking a distraction app during a "no distractions" outcome mission) and sometimes only
correlational (blocking Instagram as a hypothesized-but-unproven driver of a revenue goal).
Open question: should the product distinguish these explicitly (e.g. a "how confident are you
this behavior drives this outcome" field, shown back during the review stage) or leave that
entirely to the person's own judgment, with the plain-language read in §4 doing the work
implicitly? No position taken here; flagged for discussion before the outcome/behavior view is
actually built.

### 6.4 Scoring/consequence extension for non-time-based misses

Discipline Debt currently accrues in minutes against `avg_mission_duration_min` (Data Model doc
§3.5); Reputation and Tier floors are tuned against a Reliability Index defined as completed
sessions over attempted sessions. Neither concept has any meaning for "missed a weight log" or
"hit 60% of a revenue target." Two sub-questions, both open:

- Should missed `MissionLogEntry` cadence checkpoints feed Debt/Reputation at all, or should
  outcome-driven/habit missions be entirely outside the existing consequence system, with their
  own separate (lighter-weight, likely streak-based rather than debt-based) feedback loop?
- If they do feed the existing system, what's the conversion between "missed a log" and
  "missed N minutes of committed time" — there's no natural unit equivalence, and inventing one
  arbitrarily risks the same kind of unscoped, ungrounded default `[HYPOTHESIS]` items like
  `plannedDurationMin`'s current 25-minute constant already represent (see `ROADMAP.md` §5.24).

**Recommendation, not a decision:** keep non-time-based missions' feedback loop (streaks,
adherence percentage, the §4 relationship view) entirely separate from Debt/Reputation/Tier for
now, and revisit unification only if real usage data suggests it's needed — consistent with this
project's own stated pattern of not inventing scoring mechanics ahead of evidence
(`ROADMAP.md`/PRD §42's treatment of every other open hypothesis).

### 6.5 Habit-mission daily success framing

**Recommendation, not yet formally signed off:** for constraint/habit missions with no numeric
target, daily logging should be a simple done/not-done entry (supports streaks, keeps logging
lightweight), but **no single missed day should read as Mission failure** — only a sustained
trend (e.g., a rolling-window threshold, mirroring the existing decay-based Reputation demotion
pattern in PRD §32 rather than the old immediate-demotion approach it deliberately replaced).
This keeps logging honest by decoupling it from penalty — if logging a slip feels like
confessing to a punishment, people stop logging accurately, which defeats the measurement
entirely.

---

## 7. What does *not* change

Worth stating plainly, since this proposal touches the top-level entity: the existing, working,
already-verified machinery is not being replaced.

- `InterceptionController`, `MissionAccessibilityService`, the countdown/Break-Commitment/
  Return-to-Mission flow, Iron crisis exit — unchanged, just scoped to `EnforcementSession`.
- Debt, Reputation, Tier transitions, the shared-cause guard, decay-based demotion — unchanged
  in mechanic (see §6.4 for the one open question about whether/how they extend to new mission
  types; the existing mechanic for `EnforcementSession`-based misses is untouched either way).
  `MissionProfile` (allow/blocklist bundles) — unchanged in shape, just referenced from
  `MissionPeriod` instead of directly from the old top-level `Mission`.
- Onboarding, the design system, everything in Phase 3 — unaffected.

---

## 8. Suggested migration shape (high level, not a task breakdown)

1. Rename current `Mission` entity → `EnforcementSession`; add `missionId`/`missionPeriodId`
   foreign keys. Every existing call site (`DebugSeeder`, use-case tests, `InterceptionController`,
   `FirstMissionSchedulingFragment`/`Screen`) updates its type reference — the *behavior* of all
   of that code is unaffected, since none of it currently reasons about anything above the
   session level to begin with.
2. Introduce `Mission`, `MissionPeriod`, `MissionLogEntry` as new entities per §3.1–§3.3.
3. Resolve §6.2 (constraint-mission enforcement scope) before writing any constraint-mission
   enforcement code specifically — everything else in this proposal can proceed independently
   of that one decision.
4. Build the outcome/behavior relationship view (§4) only once at least one outcome-driven
   Mission with an attached behavior exists in practice to design the plain-language read
   against real data shapes, not hypothetical ones.
5. §5's lifecycle stages are primarily a UI/prompt concern (when to show the "attach a
   behavior?" nudge) — no schema blocker to sequencing this after the core entities land.

This document intentionally stops at "shape," not implementation detail — sequencing, exact
migration scripts, and UI mockups are downstream of sign-off on §6's open decisions, not before.
