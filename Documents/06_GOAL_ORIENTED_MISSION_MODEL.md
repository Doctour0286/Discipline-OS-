# Goal-Oriented Mission Model

**Status: PROPOSED, not yet decided — but every open question below is resolved with a stated
position, not left as an unweighted menu.** This document replaces
`06_GOAL_ORIENTED_MISSION_MODEL_PROPOSAL.md` and
`06a_GOAL_ORIENTED_MISSION_MODEL_ADDENDUM.md`, which are superseded by this file and should be
deleted once this is reviewed. Those two documents did the hard work of scoping the problem and
surfacing every real fork in the design; this document's job is different — it takes a position
on each fork, states why, and produces one coherent shape a product owner can sign off on in a
single pass, the same way §5.5/§5.9/§5.10/§5.15 were signed off together (see `ROADMAP.md` §5).
Once accepted, the relevant pieces fold into `DisciplineOS_PRD_v3_6.md` and
`01_DATA_MODEL_AND_SCHEMA.md` directly and this file is reduced to a historical pointer at the
decision log entry that replaces it.

**Where this document overrides its predecessors' own stated recommendations, that override is
called out explicitly** rather than silently blended in — see §6.1 in particular, which reverses
the base proposal's own §6.4 recommendation. Disagreement between drafts is a normal part of
getting this right, not something to paper over.

---

## 0. One-sentence summary

**A Mission is currently an enforcement session (a scheduled, time-boxed, app-blocking period).
This document makes a Mission a goal — something the user is trying to accomplish or sustain —
with enforcement sessions as one tool among several a Mission can use, not what a Mission is.**

The existing enforcement engine (Tiers, Debt, Reputation, Interception, Violations) does not get
rebuilt. It gets *rehomed* — from the top-level object to a child mechanism a goal-level Mission
can attach, renamed `EnforcementSession`.

---

## 1. Why the current model is too narrow

### 1.1 What's actually built today

`Mission` (`01_DATA_MODEL_AND_SCHEMA.md` §2.2; verified directly against
`data/entity/Mission.kt`) is: a `scheduledStart` (nullable single instant), an
`actualStart`/`actualEnd`, a `plannedDurationMin`, a `status`, an allowlist/blocklist, and a link
to a `MissionProfile`. It is, in practice, **one scheduled focus session with an app-blocker
attached.** Nothing above it represents *why* the session exists or what it's supposed to
accomplish beyond itself.

### 1.2 What a person actually means by "a discipline goal"

Real examples the product should represent, none of which fit the current model without
distortion:

- Reduce weight from X to Y (numeric outcome, manually logged, no phone signal unless a
  connected scale/app exists)
- Hit a monthly revenue target (numeric outcome, manually logged, weekly or custom cadence)
- Read N pages / N books (numeric outcome, manually logged or partially inferable from app usage)
- Spend N hours/week on a specific pursuit (a duration target, repeatable window or floating
  same-day/same-week target)
- Avoid a habit — gambling, doom-scrolling, smoking (on-phone and directly blockable, entirely
  offline and log-only, or a mix)
- Build a habit — meditate daily, stretch every morning (same on-phone/offline split)

None of these are "a scheduled focus session." Several have no phone-enforceable session at all.
Several need a manually-entered number that persists and trends over time. None of this exists
in the current schema.

### 1.3 The actual seam connecting all of these

Every example is the same underlying shape: **close a gap between where you are and where you
want to be, over time, with some mechanism forcing honesty about the gap.** What differs is only:

1. **Where the truth comes from** — the phone observes it directly, the user logs it, or a blend.
2. **How causally close enforcement is to the outcome** — blocking a doom-scroll app is a direct
   intervention on a screen-time goal, but only a weak, correlational lever on a revenue goal (it
   removes a plausible distraction; it doesn't make sales happen).
3. **How urgent a single miss is** — a missed log today is noise for a weight/revenue trend; a
   missed focus block might matter same-day. Reacting to single-day misses the way Debt/
   Reputation reacts to a missed Mission today would be wrong for most of these.

This is why the fix isn't "add a target field to Mission" — it's a real layer above the current
Mission entity separating **the goal**, **the behavior hypothesized to drive it**, and **the
enforcement mechanism**, which today are fused into one object.

---

## 2. The three mission archetypes

Not a data-typing distinction — a distinction in what's driving the mission and what closes the
loop on whether it's working. UI, success framing, and scoring differ by archetype; forcing all
three into one generic form produces something too abstract to fill in.

### 2.1 Outcome-driven

*"Hit $10k revenue this month." "Get to 75kg." "Read 12 books this year."*

A numeric outcome is the north star: value, direction (increase/decrease/maintain), and a
re-check cadence. One or more **behaviors** can attach underneath as the hypothesized cause
("2h/day prospecting," "no snacking after 8pm"), each with its own enforcement period or none.
**Behaviors are optional and addable at any time — not required at creation** (§5). Outcome
trend and behavior-adherence trend are tracked and shown as two separate, explicitly related
signals (§4).

### 2.2 Behavior-driven (no separate outcome)

*"Read 20 pages/day." "Gym 4x/week." "2 hours of deep work daily."*

The behavior **is** the goal — this is the outcome-driven case where outcome = behavior,
simplified. Most common case, closest to what's already built: the existing Mission concept,
generalized to have a cadence and (optionally) a repeatable period instead of being one-shot.

### 2.3 Constraint

*"No social media after 9pm." "Never open the gambling app." "Don't smoke."*

Not building toward a target — holding a boundary. Success is an absence being sustained. This
is where blocking is most directly causal of all three archetypes, since the behavior being
prevented and the thing being blocked are usually the same thing. May be entirely on-phone
(blockable/monitorable), entirely offline (log-only), or mixed. See §6.2 for the resolved
enforcement-scope decision this archetype required.

---

## 3. Entities

Shapes, not final field lists — exact types/nullability get settled during actual schema work.

### 3.1 `Mission` (redefined — the breaking change)

```
Mission {
  id: UUID
  userId: UUID
  title: String
  archetype: enum[OUTCOME_DRIVEN, BEHAVIOR_DRIVEN, CONSTRAINT]
  targetDirection: enum[INCREASE, DECREASE, MAINTAIN] | null   // null for pure habit/constraint
  targetValue: Double | null
  unit: String | null                // free-form: "kg", "$", "pages" — display-only, not parsed
  cadenceType: enum[DAILY, WEEKLY, CUSTOM_DAYS, NONE]
  resetMode: enum[FIXED_CALENDAR, ROLLING_WINDOW]   // per-mission choice — §6.1(a), resolved
  measurementSource: enum[AUTOMATIC, MANUAL_LOG, BOTH]
  lifecycleStage: enum[OBSERVING, HYPOTHESIZING, ENFORCING, REVIEWING]   // §5
  adherenceScore: Double | null       // §6.1(c), resolved — see §4.1
  adherenceWindow: Int | null
  createdAt: Instant
  archivedAt: Instant | null
}
```

`lifecycleStage` is new and not cosmetic — see §5 for why a Mission needs to represent "I'm just
watching this for now" as a legitimate, non-transient state, not an assumption that every Mission
immediately has an intervention attached.

`stakesType`/`stakesDescription`, proposed in the addendum draft, is **not included** — see
§6.4 for why.

### 3.2 `MissionPeriod` (zero or more per Mission)

The clock-bound enforcement/tracking layer. A Mission can have none (pure outcome logging or a
pure log-only habit), one, or several — e.g. a fixed daily window *and* a separate floating
same-day target.

```
MissionPeriod {
  id: UUID
  missionId: UUID
  periodType: enum[FIXED_WINDOW, FLOATING_DEADLINE, ALWAYS_ON]   // ALWAYS_ON — §6.2, resolved
  daysOfWeek: Set<DayOfWeek>
  // FIXED_WINDOW fields:
  windowStart: LocalTime | null
  windowEnd: LocalTime | null
  // FLOATING_DEADLINE fields:
  targetDurationMin: Int | null
  deadlineTime: LocalTime | null
  enforcementProfileId: UUID | null   // → MissionProfile; null = tracked/logged only, no blocking
}
```

### 3.3 `MissionLogEntry` (manual logging)

```
MissionLogEntry {
  id: UUID
  missionId: UUID
  loggedAt: Instant
  numericValue: Double | null      // outcome-driven / numeric behavior missions
  didOccur: Boolean | null         // habit/constraint missions with no number
  note: String | null
}
```

Logging frequency (daily/weekly/custom) is a **UI prompt derived from `cadenceType`**, not a
hard constraint — the user logs whenever they have data; the app reminds on the stated cadence.

### 3.4 `Trigger` (implementation-intention cue, zero or more per Mission or MissionPeriod)

```
Trigger {
  id: UUID
  missionId: UUID
  missionPeriodId: UUID | null       // optional — a trigger can exist without a clock window
  cueType: enum[TIME_OF_DAY, PRECEDING_EVENT, LOCATION, APP_OPEN, MANUAL]
  cueDescription: String             // free text: "after my first coffee"
  responseDescription: String        // free text: "open the reading app"
  cueTimeOfDay: LocalTime | null
  cuePrecedingMissionId: UUID | null // chains to another Mission/EnforcementSession completing
  cueLocationLabel: String | null    // display-only; no geofencing implied
  cueTriggerPackageId: String | null // for APP_OPEN — same package-id-string convention as
                                      // Mission.allowlist/blocklist
}
```

See §4.3 for why this is kept, and §6.2 for how `APP_OPEN` on a Constraint mission is resolved
into that same decision rather than treated as a separate mechanism.

### 3.5 `Milestone` (derived checkpoint, zero or more per outcome-driven Mission)

```
Milestone {
  id: UUID
  missionId: UUID
  targetValue: Double
  targetDate: Instant | null       // null = ordinal only ("halfway"), not date-bound
  label: String | null
  achievedAt: Instant | null       // computed when a MissionLogEntry crosses this value
}
```

Read-only relative to `MissionLogEntry` — the person never logs against a Milestone directly.
See §4.4.

### 3.6 `EnforcementSession` (renamed from today's `Mission`)

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

**Everything downstream — `InterceptionController`, `MissionAccessibilityService`, `Violation`,
Debt, Reputation, Tier transitions — keeps working exactly as built**, scoped to
`EnforcementSession` instead of the old top-level `Mission`. This is the load-bearing reason the
migration is additive, not a rewrite: the existing, already-verified consequence engine doesn't
change shape, only its parent reference. See §7 for the concrete call-site migration, including
the one real gap the earlier drafts didn't name.

---

## 4. Derived views and mechanisms

### 4.1 The outcome/behavior relationship view (outcome-driven missions)

Not a chart nicety — a deliberate product decision. Most habit-tracking failure looks like this:
someone logs an outcome (weight, revenue) and expects the log itself to be motivating — it isn't,
because a number with no visible cause attached doesn't tell you what to do differently. Most
focus-app failure looks like the reverse: strict behavior enforcement with zero connection to any
outcome the person actually cares about, so compliance holds only as long as the fear of being
blocked does.

Keeping outcome and behavior as two separately tracked trends, shown together with a
plain-language read, is the single highest-value thing this feature can do that isn't already
done well by a habit tracker or a focus app alone:

- **Behavior followed, outcome moving as expected** → reinforce, no action needed.
- **Behavior followed, outcome flat/wrong-direction** → the hypothesis linking behavior to
  outcome may be wrong. Valuable, non-obvious information: the person did everything right and
  it still isn't working — a reason to change the behavior, not a personal failure. Say this
  plainly, not just two flat lines.
- **Behavior skipped, outcome flat/wrong-direction** → expected; no surprise, tighten adherence.
- **Behavior skipped, outcome moving anyway** → the hypothesized behavior may not actually be the
  driver; worth reconsidering what's really causing the outcome to move.

This four-quadrant read should be a first-class, plain-language surface on the Mission detail
screen, not two overlaid line charts left for the user to interpret unassisted.

**Resolved (earlier drafts left this open): what "behavior followed" means when a Mission has
multiple `MissionPeriod`s.** A Mission's behavior-adherence signal for this view is the
`adherenceScore` defined in §4.2, computed once per Mission across all of its periods and log
entries combined — not a per-period breakdown plotted separately. Reasoning: the four-quadrant
read is meant to answer one question ("is the behavior I'm doing working?"), and a person with
three periods under one Mission has one behavior hypothesis, not three. If real usage shows
people attaching periods that represent genuinely different hypotheses under one Mission, the
right fix is prompting them to split into separate Missions, not multiplying the relationship
view's axes. Building against a single number now keeps this buildable; splitting is easy to add
later, collapsing three plotted lines into a read a person can act on later is not.

### 4.2 Adherence — resolved as a real, lightweight consequence path

**This document resolves the base proposal's own §6.4 open question, and does so by overriding
the recommendation that document made.** The base proposal recommended keeping outcome-driven
and habit missions entirely outside Debt/Reputation, revisiting only if usage data demanded it.
That recommendation optimizes for not inventing scoring ahead of evidence — a reasonable default
in general, and the right call for *how existing scoring mechanics extend*. But applied here,
literally, it means two of the three archetypes (Outcome-driven, most of Constraint) would ship
with **no feedback loop beyond a log entry existing or not.** For a product whose stated purpose
is enforcing discipline and accountability, not just tracking, that's not a safe default — it's a
gap dressed as caution.

The evidence for why "just log it" underperforms is not speculative: habit-tracker critique
converges on a consistent failure mode independent of any particular product — a bare checkmark
is too weak a signal to compete with the reward of the behavior it's meant to replace, and the
majority of users on streak-only systems quit within 30 days. Building the goal layer this
document proposes and then shipping two-thirds of it with no consequence mechanism at all
produces exactly that failure mode by construction, not by omission.

**Resolution: introduce Adherence, a second consequence track, separate from Reputation and
never merged into it.**

- Computed from `MissionLogEntry` presence/value against `cadenceType` and `targetDirection` over
  `adherenceWindow` (a rolling window, mirroring the shared-cause guard's own rolling-window
  pattern, `ROADMAP.md` §5.5, rather than inventing a new windowing convention) — a straightforward
  hit-rate, not a new formula category.
- Decays on **sustained miss patterns, not single misses** — directly implementing the daily
  success-framing position in §4.5, using the same decay-based-not-immediate-demotion precedent
  already committed to for Reputation (`ApplyReputationDecayUseCase`, `ROADMAP.md` §5.9/§5.10)
  rather than inventing a different philosophy for a second metric.
- **Never feeds Tier.** Mixing a log-adherence metric into Tier transitions would let a person's
  standing in the enforcement tier system — built and validated against session-completion
  behavior — move based on a structurally different kind of behavior it was never designed to
  measure. This is the same scope discipline that already bars Unsupervised Reliability data from
  Tier/Debt/Reputation (PRD §13.3). Two separately-legible numbers is more honest than one merged
  number built from incompatible units.
- **Applies to Outcome-driven and Constraint missions, and to Behavior-driven missions that have
  no attached `EnforcementSession`.** A Behavior-driven mission with a `MissionPeriod` that
  *does* have `enforcementProfileId` set already gets Reputation/Debt treatment via its sessions
  — Adherence still computes for it (log-only days between scheduled sessions are real signal
  too), but is shown as a secondary number, never substituted for Reputation on that mission.
  This resolves the base proposal's own open question in §6.4/addendum §C.3 rather than leaving
  it open: the two tracks are not mutually exclusive, they're scoped to different underlying
  events (session completion vs. log presence), and a Mission can legitimately have both.
- **Drives:** shown on the Mission detail screen (always); surfaced as a Weekly Report callout
  when it crosses a decay threshold (reusing the existing Weekly Report cadence infrastructure,
  PRD §32–§34) — no blocking, no scoring consequence beyond that. This is a deliberately
  restrained ceiling: the floor (purely informational) was the earlier drafts' safe default; this
  document moves one step past the floor — a report surface, nothing that touches enforcement —
  because a number that decays and is never shown anywhere is not a consequence path, it's a
  hidden field.

**Remaining open constant (not resolved here, consistent with how this project treats every
other tunable):** exact decay formula and window length are `[HYPOTHESIS]`, the same way
`plannedDurationMin`'s 25-minute default and §5.9's tier bands were both flagged rather than
silently fixed (`ROADMAP.md` §5.24, §5.9). This needs pilot data, not a guess dressed as a
default.

### 4.3 Trigger — implementation intentions, kept as a distinct mechanism from `MissionPeriod`

Gollwitzer's implementation-intention research — "if-then" plans binding a specific situational
cue to a specific response — shows a medium-to-large effect (d = 0.65 across a 94-study
meta-analysis) on translating intention into actual behavior, versus forming a goal without
naming the cue. The mechanism is associative, not motivational: naming the cue creates a
stimulus-response link strong enough that encountering the cue is sufficient to launch the
behavior, shifting initiation from effortful deliberation to environmental signal. Wendy Wood's
habit-formation research treats this as one of the concrete mechanisms by which deliberate
behavior becomes automatic.

This is a different mechanism from a scheduled clock window. "9:00–10:00 PM, block Instagram" is
time-boxed enforcement — already well-modeled by `MissionPeriod`. "When I finish dinner, open the
reading app" is a cue-response pair with no necessary clock time at all — already-modeled by
nothing. The effect size is large enough that a product whose stated purpose is behavior change
should model this shape directly rather than approximate it with a clock window that doesn't fit.

All `cueType` values except `APP_OPEN` are **not independently phone-enforceable** — structure
for the person's own plan and material for the reminder/nudge surface, not a new interception
mechanism. This keeps the addition inside PRD §13.4's existing boundary (measurement/prompting
only, outside a real `EnforcementSession` window).

**Resolved (earlier drafts flagged this as overlapping the cadence-reminder mechanism without
examining it): `Trigger` and `MissionLogEntry`'s cadence-derived reminder are not two competing
reminder systems.** `cadenceType`-derived prompts ("you haven't logged today") are a *measurement*
nudge — they exist because `MissionLogEntry` needs data to compute trends against. `Trigger` is a
*behavioral* nudge — it exists to help the person actually do the thing, independent of whether
they log it. A Mission can have zero, one, or both: a pure-logging outcome mission needs only the
cadence reminder; a behavior-driven mission benefits from both (the trigger launches the behavior,
the cadence reminder catches anyone who forgets to log after). These render as different UI
surfaces with different copy ("time to log your weight" vs. "after your coffee, open the reading
app") and shouldn't be collapsed into one mechanism — collapsing them would either weaken the
Trigger's cue-specificity (the entire source of its effect size) or turn every log reminder into
a full implementation-intention prompt nobody asked to write.

**Resolved: `Trigger` is optional at Mission creation, offered as a post-creation prompt, not
required.** The base proposal's own reasoning against forcing a hypothesis at creation ("often
untrue — someone starting a weight goal frequently needs a few weeks of just logging before they
know") applies here with equal force: a person may not know their best cue on day one either.
Given the effect size, the prompt should be offered more assertively than the Hypothesizing nudge
in §5 (e.g. shown once per Mission during Hypothesizing rather than requiring the person to find
it), but never mandatory — mirroring, not exceeding, the existing Mission Profile Drift Detection
pattern (PRD §8.1).

**Resolved: no hard cap on triggers per Mission**, but the creation UI defaults to prompting for
one, not many — additional triggers are something a person adds deliberately after the first is
working, not something the initial flow solicits several of at once. This avoids both an
arbitrary schema limit and a form that asks a new user for more structure than they have yet.

### 4.4 Milestone — long-horizon outcome missions

Locke and Latham's goal-setting theory — roughly 1,000 studies over 50 years — treats goal
difficulty and goal proximity as separate moderators: a distant, high-difficulty goal with no
intermediate structure risks the same failure mode the stretch-goal literature documents —
motivation and self-efficacy erode when the gap between current state and target feels too large
to act on directly, absent nearer checkpoints that make progress legible.

This is distinct from what `MissionLogEntry` solves. A log entry records what *happened*. Nothing
represents what was *supposed to happen* by a given point — so there's no way to compute whether
the person is ahead, on pace, or falling behind the plan itself, only whether the raw number
moved in the right direction at all.

Milestones are derived checkpoints on the same trend `MissionLogEntry` already builds, not a new
logging surface — `achievedAt` is computed from existing log entries crossing the threshold. No
new write path, no new consequence path.

**Resolved: milestones are person-authored by default, not auto-generated, for v1.** Even though
a `cadenceType` with a natural pace could suggest evenly spaced milestones computationally, this
project's own stated convention (`MissionProfile`'s kdoc explicitly rejecting an invented
allowlist/blocklist split with no data behind it, §5.30's decision log entry) argues against
inventing the spacing logic without evidence it's the right spacing. Auto-suggestion is a
reasonable v2 once real Mission data exists to design the suggestion against — flagged as future
work, not built now.

**Resolved: a missed milestone (target date passed, `achievedAt` still null) is purely a display
concept** — a marker on the outcome trend in §4.1's relationship view, feeding nothing. Same
reasoning as §4.2's decision to keep non-time-based misses out of Tier: a missed *checkpoint* on
a goal the person is still actively pursuing is weaker signal than a missed *log*, and if even
log misses only decay Adherence on a sustained pattern, a single missed milestone clearly
shouldn't do anything stronger.

**Resolved: milestones with a `targetDate` require `resetMode = FIXED_CALENDAR`.** A
`ROLLING_WINDOW` mission's window has no fixed calendar anchor for a date-bound checkpoint to be
measured against; a `ROLLING_WINDOW` mission that wants milestones uses ordinal ones
(`targetDate = null`, e.g. "halfway") instead. Enforced at the UI level (date-picker hidden for
`ROLLING_WINDOW` missions), not a hard schema constraint, since nothing prevents a future
`resetMode` value from having its own sensible date semantics.

### 4.5 Habit-mission daily success framing

For constraint/habit missions with no numeric target, daily logging is a simple done/not-done
entry (supports streaks, keeps logging lightweight), but **no single missed day reads as Mission
failure** — only a sustained trend, computed via Adherence's decay (§4.2), mirroring the existing
decay-based Reputation demotion pattern (PRD §32) rather than the old immediate-demotion approach
it deliberately replaced. This keeps logging honest by decoupling it from penalty — if logging a
slip feels like confessing to a punishment, people stop logging accurately, which defeats the
measurement entirely.

---

## 5. Mission lifecycle: observe → hypothesize → enforce → review

A Mission does not need a behavior/period attached to exist. Requiring one at creation forces the
person to already know their hypothesis before they're allowed to start measuring, which is often
untrue — someone starting a weight goal frequently needs a few weeks of just logging before they
know whether their current habits are even the problem.

1. **Observing** — outcome-only logging, no behavior/period attached yet. A fully valid,
   permanent end state for a Mission if the person only ever wants to track, not intervene.
2. **Hypothesizing** — after a minimum number of outcome logs with no behavior attached (a small
   number, e.g. 2–3 — exact threshold is `[HYPOTHESIS]`, not fixed here), the app may surface a
   single, dismissible, non-scored prompt to attach a behavior *and* (§4.3) a trigger — same
   pattern as Mission Profile Drift Detection (PRD §8.1). Never mandatory.
3. **Enforcing** — one or more `MissionPeriod`s attached, at least one with a non-null
   `enforcementProfileId` actively blocking during its window, or, for Constraint missions using
   `ALWAYS_ON` (§6.2), the always-on period itself.
4. **Reviewing** — not a separate stored state so much as a recurring point (Weekly/Monthly
   Report cadence) where the outcome/behavior relationship view (§4.1) and Adherence (§4.2) are
   surfaced and the person decides whether to keep, adjust, or drop the attached behavior —
   looping back to Hypothesizing if they want to try something else.

No stage is mandatory to progress through in order except that Enforcing requires at least one
`MissionPeriod` (including `ALWAYS_ON`) to exist. A Mission can be created directly into
Enforcing (the common case for simple behavior-driven missions per §2.2) or sit in Observing
indefinitely.

---

## 6. Decisions

Every open question from the two prior drafts, resolved. Reasoning favors what the evidence and
this project's own established conventions support, not what's fastest to build.

### 6.1 Reset mode default and scope — resolved

`resetMode` is per-mission, not global — each Mission chooses `FIXED_CALENDAR` or
`ROLLING_WINDOW` independently. No default needed: the Mission creation flow requires this field
be set explicitly (it has no natural universal default — a revenue goal wants `FIXED_CALENDAR`,
a "hours this week" goal often wants `ROLLING_WINDOW`), consistent with not inventing a default
where the two options serve genuinely different mission shapes.

### 6.2 Constraint missions and always-on enforcement — resolved as (b), `ALWAYS_ON` period type

PRD §13.4 rejected *any* enforcement outside a scheduled Mission window, for a stated reason
(reactance/surveillance-fatigue risk). Constraint missions ("never open the gambling app," no
natural time window) sit outside that rule's original scope entirely — they have no window to be
"outside of."

Three options were on the table:

- **(a)** Require a `MissionPeriod` for every Constraint mission, potentially spanning all day —
  simplest, no new principle, but a workaround: a person setting "never open the gambling app"
  shouldn't have to construct a fake 24/7 schedule to express "never."
- **(b)** Introduce `ALWAYS_ON` as a genuine `periodType`, explicitly carving out a narrow
  exception to §13.4's blanket rule for Constraint missions specifically.
- **(c)** Constraint missions never get phone-level blocking — logging/tracking only, with
  blocking available only via a separately-built `MissionPeriod`, keeping §13.4 fully intact.

**Resolved: (b).** The reasoning behind §13.4 is specifically about extending enforcement into a
person's *unscheduled* time — time they haven't designated for a Mission. A Constraint mission's
`ALWAYS_ON` window is not that: it is time the person *has* designated, deliberately and
explicitly, at Mission creation, by choosing "never" as the shape of their own goal. §13.4's
reactance concern is about the product deciding to watch someone when they didn't ask for it; an
`ALWAYS_ON` Constraint mission is the person asking for exactly that, for exactly the one behavior
they named. Option (a) achieves the identical technical outcome through a worse UX (a fake
schedule), and option (c) refuses to build the single most causally direct enforcement case this
entire model identifies (§2.3) for the archetype that most needs it, which fails the user for the
sake of a boundary that was never written with this case in mind.

This resolution folds in the addendum's parallel question: **`Trigger` with `cueType = APP_OPEN`
on a Constraint mission is the same underlying mechanism as an `ALWAYS_ON` `MissionPeriod` with
that package on its blocklist, entered through a different UI path (a cue-response description
instead of a period configuration).** Rather than maintain two schema paths to the same
enforcement outcome, `APP_OPEN` triggers on Constraint missions are implemented as sugar over
`ALWAYS_ON` period creation — the Trigger row still exists (it carries the person's own
description of the cue, useful context nothing else stores), but the actual blocking behavior is
delegated to a real `MissionPeriod`/`enforcementProfileId`, not a second enforcement code path.

### 6.3 Causal-distance labeling for outcome-driven missions — resolved: not built for v1

For outcome-driven missions, an attached behavior's enforcement is sometimes directly causal and
sometimes only correlational. The question was whether to make the person state a confidence
level explicitly, or leave it to the plain-language read in §4.1 to do the work implicitly.

**Resolved: leave it implicit, via §4.1's four-quadrant read, for v1.** A confidence field adds a
step to Mission creation for a judgment most people can't actually make accurately before they
have data — the entire point of §4.1's "behavior followed, outcome flat" quadrant is to *reveal*
whether the hypothesis was right after the fact, which is more honest than asking someone to
guess their own causal confidence up front. If usage data later shows people consistently
misjudging causal closeness in a way the four-quadrant view doesn't correct for, an explicit field
is easy to add later; asking for it now is solving a problem §4.1 already solves differently.

### 6.4 Stakes/commitment field — resolved: not included

Commitment devices are well-evidenced — a 2024 randomized trial found anti-charity financial
stakes increased goal completion by 34% over neutral stakes, and commitment-contract platforms
(stickK, Beeminder) have running track records on the mechanism. The question was whether to add
a minimal `stakesType`/`stakesDescription` field now, with only `NONE`/`SELF_DECLARED`
implemented and `SOCIAL`/`FINANCIAL` reserved in the enum for later.

**Resolved: not included in this pass.** The evidence supports commitment devices in general; it
does not support that a bare free-text field with no computed effect and no working
`SOCIAL`/`FINANCIAL` implementation delivers any of that evidence's benefit. `SELF_DECLARED`, as
scoped, is indistinguishable in function from writing the same sentence into
`MissionLogEntry.note`, which already exists — the field would add a dropdown and a second text
box for zero new capability. Shipping `SOCIAL`/`FINANCIAL` as enum values with no backing
implementation is exactly the kind of "invented plausible-looking option, not yet earned" pattern
this project's own Data Model doc explicitly rejected for Discipline Score's weights (§3.1) and
`MissionProfile` rejected for an ungrounded allowlist/blocklist split (§5.30). If and when
`SOCIAL` or `FINANCIAL` stakes are actually going to be built — a real payment or contact-sharing
integration — that's the point to add the field, sized to what's actually being built, not before.

### 6.5 Habit-mission daily success framing — resolved

See §4.5 — folded in as a resolved position rather than a standalone recommendation, since it
depends directly on §4.2's Adherence resolution.

### 6.6 First Mission Scheduling's onboarding gap — new decision, not present in either prior draft

`FirstMissionSchedulingFragment` (verified directly against the current codebase) is the only
real Mission-creation call site in the app today, and it does a single unconditional insert of
one row with no goal concept above it, immediately flipping status to `ACTIVE`. Neither prior
draft's migration section (§8 of the base proposal) accounted for this: renaming `Mission` to
`EnforcementSession` is not enough on its own, because this screen has nothing to attach the new
`EnforcementSession.missionId` foreign key to — there is no parent `Mission` in existence at the
point this screen runs.

**Resolved: First Mission Scheduling is extended, not replaced, to auto-create a minimal
Behavior-driven Mission as the parent of the session it already creates**, using the same title
implicitly already present in the Mission Profile the person just configured (e.g. "Focus
Sessions" as a default title, editable later) — not left silently null, and not blocking
onboarding on a new "define your goal" step the person hasn't been asked to think about yet.
This is a narrow, explicit exception to this project's general convention against inventing
unrequested structure, justified because the alternative — a broken foreign key, or a second
onboarding step asking a brand-new user to articulate a goal and archetype before they've used
the product once — is worse on both engineering and UX grounds. The generated Mission starts in
`ENFORCING` (it already has the `MissionPeriod` implied by the session being created) with
`archetype = BEHAVIOR_DRIVEN`, `cadenceType = NONE`, and no target — the minimum viable shape that
satisfies the schema without asserting anything about the person's actual goal that they haven't
stated. The Mission detail screen (once built) is where they can rename it, add a target, or
change its archetype — this default is a placeholder, not a claim about what their goal is.

This is flagged, not silently assumed — the same standing this project gives every other
onboarding shortcut (`plannedDurationMin`'s 25-minute default, the "Start now" `ACTIVE` shortcut,
both in `FirstMissionSchedulingFragment`'s own kdoc) — and should be logged in `ROADMAP.md` §5
alongside the rest of this document's decisions if accepted.

---

## 7. What does *not* change

- `InterceptionController`, `MissionAccessibilityService`, the countdown/Break-Commitment/
  Return-to-Mission flow, Iron crisis exit — unchanged, just scoped to `EnforcementSession`.
- Debt, Reputation, Tier transitions, the shared-cause guard, decay-based demotion — unchanged in
  mechanic. `MissionProfile` (allow/blocklist bundles) — unchanged in shape, just referenced from
  `MissionPeriod` instead of directly from the old top-level `Mission`.
- Onboarding, the design system, everything in Phase 3 — unaffected, except the one explicit
  extension in §6.6.

---

## 8. Migration shape

1. Rename current `Mission` entity → `EnforcementSession`; add `missionId`/`missionPeriodId`
   foreign keys. Every existing call site (`DebugSeeder`, use-case tests, `InterceptionController`,
   `MissionProfileSetupFragment`) updates its type reference — the *behavior* of that code is
   unaffected, since none of it currently reasons about anything above the session level.
2. Introduce `Mission`, `MissionPeriod`, `MissionLogEntry`, `Trigger`, `Milestone` as new
   entities per §3.
3. Extend `FirstMissionSchedulingFragment` per §6.6 — this is the one call site that needs real
   logic changes, not just a type-reference update, because it's the only place a `Mission` and
   its first `EnforcementSession` are created together with nothing pre-existing to attach to.
4. Build the outcome/behavior relationship view (§4.1) only once at least one outcome-driven
   Mission with an attached behavior exists in practice, to design the plain-language read
   against real data shapes.
5. Implement Adherence (§4.2) alongside the core entities, not deferred — per §6.1's override of
   the earlier "wait for evidence" default, this is judged necessary for the archetypes that would
   otherwise ship with no feedback loop at all, not an enhancement to add later.
6. §5's lifecycle stages and §4.3's Trigger-prompt timing are primarily a UI/prompt concern — no
   schema blocker to sequencing these after the core entities land.

This document stops at "shape," not implementation detail — sequencing beyond the above, exact
migration scripts, and UI mockups are downstream of sign-off on §6's decisions, not before.
