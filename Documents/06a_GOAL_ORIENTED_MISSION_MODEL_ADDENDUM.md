# Goal-Oriented Mission Model — Addendum: Coverage and Enforcement Extensions

**Status: PROPOSED, not yet decided.** Same standing as `06_GOAL_ORIENTED_MISSION_MODEL_PROPOSAL.md`
itself (see that document's own header) — this is a design proposal, reviewable as its own
discrete unit, not yet authoritative. If and when §6 of the base proposal and §A–§D below go
through product-owner sign-off, the accepted pieces fold into the base proposal (or directly
into the PRD/Data Model doc) together, not separately.

**Relationship to the base proposal:** `06_...md` is not being revised here — its entity shapes
(§3), lifecycle (§5), and open decisions (§6) stand as written. This addendum identifies four
places where that model, as scoped, would under-serve the stated goal of covering "almost any
kind of mission" with real enforcement/accountability teeth, and proposes extensions — not
replacements — for each. Every claim below that isn't this project's own established convention
is sourced; every extension is framed as an addition to §3's entity list or a new §6-style open
decision, not a fait accompli.

---

## 0. Why this addendum exists

The base proposal solves the right problem: `Mission` as pure enforcement-session is too narrow
to represent a goal. But "flexible enough to cover almost any kind of mission" and "extremely
useful in enforcing discipline and accountability" are a higher bar than the three archetypes
plus a period/log-entry pair currently clear, for reasons grounded in the behavior-change
literature this product already leans on elsewhere (§13.1's own citation practice — Castelo et
al. 2025 — is the precedent for holding new mechanics to the same standard):

1. **The base proposal's `MissionPeriod` encodes *when* an enforcement window runs, but not
   *what triggers engagement with the goal in the first place.*** A scheduled clock window and
   a behavioral cue are different mechanisms with different evidence behind them (see §A).
2. **Long-horizon outcome missions have no way to be broken into intermediate checkpoints.**
   `MissionLogEntry` records what happened; nothing represents what was supposed to happen at
   the halfway point. Goal-setting research is explicit that undifferentiated, distant goals
   lose directive power without intermediate structure (see §B).
3. **§6.4 of the base proposal already flags that Debt/Reputation/Tier has no natural meaning
   for non-time-based misses, and recommends — but does not resolve — punting to a separate,
   lighter feedback loop.** If the product's stated goal is enforcing discipline and
   accountability *across all three archetypes*, this can't stay permanently unresolved; see §C
   for why and what the options actually are.
4. **The product has no explicit stakes/commitment concept anywhere in the schema.** The
   existing Tier/Debt/Reputation system is a stakes mechanism, but only for time-boxed
   enforcement sessions. The literature on commitment devices generally is one of the more
   robust bodies of evidence behind any single lever discussed here; not representing it at all
   in the goal-level schema is a gap, not a decision (see §D).

Each section below states the research basis, the concrete schema/UX extension, and — following
this repo's own §6 convention — the open questions that make it a decision rather than a
fait accompli.

---

## A. Implementation intentions: a `Trigger` concept, distinct from `MissionPeriod`'s clock window

### A.1 The evidence

Gollwitzer's implementation-intention research — "if-then" plans that bind a specific
situational cue to a specific response — is supported by a meta-analysis across 94 studies
showing a medium-to-large effect (d = 0.65) on translating intention into actual behavior,
compared to forming a goal without naming the cue. The mechanism is not motivational, it's
associative: naming the cue in advance creates a stimulus-response link strong enough that
encountering the cue is sufficient to launch the behavior, shifting initiation from effortful
deliberation to environmental signal. Wendy Wood's habit-formation research treats this as one
of the concrete mechanisms by which deliberate behavior becomes automatic — the plan is
scaffolding, the habit is what forms on top of it through repeated execution.

This is a different mechanism from a scheduled clock window. "9:00–10:00 PM, block Instagram"
is a time-boxed enforcement period — useful, already well-modeled by `MissionPeriod`. "When I
finish dinner, open the reading app" is a cue-response pair with no necessary clock time at
all — the cue is a preceding event, not an hour. The research above is specifically about the
latter shape, and the effect size is large enough that a product whose stated purpose is
behavior change probably shouldn't model only the former.

### A.2 Proposed extension

Add an optional `Trigger` concept, attachable to a `MissionPeriod` or directly to a `Mission`
for archetypes with no period at all:

```
Trigger {
  id: UUID
  missionId: UUID
  missionPeriodId: UUID | null       // optional — a trigger can exist without a clock window
  cueType: enum[TIME_OF_DAY, PRECEDING_EVENT, LOCATION, APP_OPEN, MANUAL]
  cueDescription: String             // free text: "after my first coffee," "when I open Twitter"
  responseDescription: String        // free text: "open the reading app," "do 10 pushups"
  // CUE_TYPE-conditional fields, all optional:
  cueTimeOfDay: LocalTime | null
  cuePrecedingMissionId: UUID | null // chains to another Mission/EnforcementSession completing
  cueLocationLabel: String | null    // display-only; no geofencing implied by this shape
  cueTriggerPackageId: String | null // for APP_OPEN — same package-id-string convention as
                                      // Mission.allowlist/blocklist
}
```

`cueType = APP_OPEN` deserves its own note: it's the one cue type this product can act on
*directly* rather than just log, since `MissionAccessibilityService` already observes app opens
as its core mechanism (per `02_SYSTEM_ARCHITECTURE.md` and the existing interception loop). A
Trigger with `cueType = APP_OPEN` on a Constraint mission ("if I open the gambling app, that IS
the violation") is close to describing what Constraint missions already need structurally —
worth resolving alongside §6.2 of the base proposal rather than as a fully separate concern; see
§A.4.

All other `cueType` values (`TIME_OF_DAY`, `PRECEDING_EVENT`, `LOCATION`, `MANUAL`) are
**not independently phone-enforceable** — they're structure for the person's own plan and
material for the reminder/nudge surface, not a new interception mechanism. This matters because
it keeps this addition inside §13.4's existing boundary (measurement/prompting only, outside a
real `EnforcementSession` window) rather than quietly expanding phone-level enforcement through
a side door.

### A.3 Why this is additive, not a competitor to `MissionPeriod`

A `Mission` can have a `MissionPeriod` (clock-bound enforcement), a `Trigger` (cue-bound
behavioral plan), both, or neither:

- Outcome-driven mission, no period, one trigger: "after I check my bank balance each morning,
  log today's spend" — no enforcement, pure cue-based logging discipline.
- Behavior-driven mission, period + trigger: "read 20 pages/day" with a `FIXED_WINDOW` period
  (9–10 PM) *and* a trigger ("after I put my phone on the charger") — the period gives
  enforcement teeth, the trigger gives the cue research says actually launches the behavior.
  These reinforce each other; nothing about the base proposal's shape prevents this combination,
  it just doesn't currently have a field for the trigger half.

### A.4 Open questions

- **Should `Trigger` be surfaced at Mission creation as a required field, an optional field, or
  a post-creation prompt** (mirroring the base proposal's own "Hypothesizing" nudge pattern in
  §5)? Given the effect size, there's a real argument for prompting for it more assertively than
  a fully optional field would — but the base proposal's own §5 reasoning against forcing a
  hypothesis at creation ("often untrue — someone starting a weight goal frequently needs a few
  weeks of just logging before they know") arguably applies here too: a person may not know
  their best cue on day one. **No position taken here; flagged for the same sign-off track as
  §6.**
- **Does `cueType = APP_OPEN` on a Constraint mission become a real enforcement signal, or stay
  descriptive-only?** This directly overlaps §6.2 of the base proposal (constraint missions and
  always-on enforcement) and should be resolved as one decision, not two — an `APP_OPEN` trigger
  that *also* blocks is functionally identical to option (b) in §6.2 (a new always-on period
  type), just entered through the trigger concept instead of the period concept. Recommend
  folding this question into §6.2's resolution rather than deciding it separately.
- **Multiple triggers per Mission** — the shape above allows it (one-to-many via `missionId`),
  but is there a reasonable cap, or UI complexity ceiling, worth stating now? Not resolved here.

---

## B. Long-horizon outcome missions: a `Milestone` concept

### B.1 The evidence

Locke and Latham's goal-setting theory — built on roughly 1,000 studies over 50 years — is
unambiguous that specific, difficult goals outperform vague ones, which the base proposal's
`targetValue`/`unit`/`cadenceType` fields already respect for outcome-driven missions. But the
same body of research treats goal difficulty and goal proximity as separate moderators: a
distant, high-difficulty goal with no intermediate structure risks the same failure mode the
stretch-goal literature documents — motivation and self-efficacy erode when the gap between
current state and the target feels too large to act on directly, absent nearer checkpoints that
make progress legible.

This is a distinct problem from what `MissionLogEntry` already solves. A log entry records what
*happened* ("logged 71kg today"). Nothing in the current schema represents what was *supposed to
happen* by a given point ("should be at 78kg by week 4 of a 12-week, 85kg→70kg mission") — so
there's no way to compute whether the person is ahead, on pace, or falling behind the plan
itself, only whether the raw number moved in the right direction at all.

### B.2 Proposed extension

```
Milestone {
  id: UUID
  missionId: UUID
  targetValue: Double              // an intermediate point on the way to Mission.targetValue
  targetDate: Instant | null       // null = milestone is ordinal only ("halfway"), not date-bound
  label: String | null             // optional display text, e.g. "Week 4 checkpoint"
  achievedAt: Instant | null       // set when a MissionLogEntry crosses this value in the
                                    // Mission's targetDirection; null = not yet reached
}
```

Milestones are **derived checkpoints on the same trend `MissionLogEntry` already builds**, not a
new logging surface — the person doesn't log against a Milestone directly, `achievedAt` is
computed from existing log entries crossing the threshold. This keeps the addition read-only
relative to the entities the base proposal already defines; no new write path, no new
consequence path, no interaction with §6.4's open question about scoring.

Auto-generation is worth naming as an option but not deciding here: for a `cadenceType` that
implies a natural pace (e.g. a 12-week weight goal with weekly cadence could suggest 12 evenly
spaced milestones), the app *could* propose milestones rather than require the person to define
them by hand — but per this repo's own convention of not inventing behavior the spec doesn't
call for (`MissionProfile`'s own kdoc explicitly rejects inventing an allowlist/blocklist split
with no data behind it, §5.30's decision log entry), auto-generation should be a stated,
sign-off'd default, not an implementation-time guess.

### B.3 Open questions

- **Auto-generated vs. person-authored milestones** — flagged above, not resolved.
- **Does a missed milestone (target date passed, `achievedAt` still null) feed anything, or is
  it purely a display concept** (a line on the outcome trend in §4's relationship view)?
  Recommend the latter for the same reason §6.4's own recommendation argues for keeping
  non-time-based misses out of Debt/Reputation for now — but this should be decided alongside
  §6.4, not as a separate carve-out.
- **Interaction with `resetMode`** (§6.1 of the base proposal, already resolved as per-mission
  `FIXED_CALENDAR`/`ROLLING_WINDOW`) — milestones with a `targetDate` presumably only make sense
  under `FIXED_CALENDAR`; a `ROLLING_WINDOW` mission would need ordinal milestones
  (`targetDate = null`) instead. Worth stating explicitly in the eventual spec rather than left
  implicit.

---

## C. Resolving §6.4: a real, lightweight consequence path for non-time-based misses

### C.1 Why this can't stay open if the goal is "enforce discipline across all mission types"

The base proposal's §6.4 is honest that Debt (minutes against `avg_mission_duration_min`) and
Reputation/Tier (built on a Reliability Index of completed-over-attempted *sessions*) have no
natural unit for "missed a weight log" or "hit 60% of a revenue target," and recommends keeping
non-time-based missions outside that system entirely, revisiting only if usage data demands it.
That recommendation is reasonable as a v1 default — but it also means, as scoped, two of the
three archetypes (Outcome-driven and most of Constraint) would have **no enforcement or
accountability mechanism at all** beyond a log entry existing or not, which sits in tension with
this being explicitly framed as a discipline/accountability product, not a plain tracker.

The habit-tracking literature is specific about what a "just log it" system tends to produce
without a real feedback loop: a large body of habit-tracker critique (independent of any
particular product) converges on the same failure mode — a bare checkmark is too weak a signal
to compete with the reward of the behavior it's meant to replace, and apps that rely on
streak-break-and-abandon dynamics see the majority of users quit within 30 days. The base
proposal's own §6.5 recommendation (no single missed day should read as failure) is the right
instinct against streak-fragility specifically, but a decayed-trend concept that never actually
does anything when it decays isn't a consequence system — it's a dashboard.

### C.2 Proposed resolution direction (not decided — offered as the concrete option §6.4 didn't fully spell out)

Introduce a second, parallel consequence track — **Adherence**, distinct from Reputation —
scoped to non-`EnforcementSession` misses:

```
Mission.adherenceScore: Double            // 0-100, same display grammar as Reputation for
                                            // familiarity, but a SEPARATE number, separately
                                            // stored, never merged into Reputation itself
Mission.adherenceWindow: Int              // rolling window length in cadence units (days/weeks),
                                            // mirroring the shared-cause guard's own rolling-
                                            // window pattern (ROADMAP.md §5.5) rather than
                                            // inventing a new windowing convention
```

- Computed from `MissionLogEntry` presence/value against `cadenceType` and `targetDirection`
  over `adherenceWindow` — a straightforward hit-rate, not a new formula category.
- Decays on sustained miss patterns, **not single misses** — directly implementing §6.5's own
  recommendation, using the same decay-based-not-immediate-demotion precedent this project
  already committed to for Reputation itself (`ApplyReputationDecayUseCase`, ROADMAP.md §5.9/
  §5.10) rather than inventing a different philosophy for a second metric.
- **Explicitly does not feed Tier.** This is the one place this addendum takes a firmer position
  than "fully open": mixing a log-adherence metric into Tier transitions would let a person's
  standing in the *enforcement* tier system (built and validated against session-completion
  behavior) move based on a structurally different kind of behavior it was never designed to
  measure — a scope violation in the same spirit as why Unsupervised Reliability data is barred
  from Tier/Debt/Reputation per §13.3 of the PRD. Two separately-legible numbers is more honest
  than one merged one built from incompatible units.
- What Adherence *does* drive is left open — options range from "purely informational, shown on
  the Mission detail screen" to "feeds its own lightweight nudge/prompt cadence (e.g., a
  Weekly Report callout), with no blocking or scoring consequence attached." The floor (purely
  informational) is the safe default if no stronger option gets signed off; the ceiling stops
  well short of anything resembling Debt or Violation.

### C.3 Open questions

- **Does Adherence apply to Behavior-driven missions that also have an `EnforcementSession`
  attached, or only to missions with no session at all?** A behavior-driven mission with a
  `MissionPeriod` already gets Reputation/Debt treatment via its sessions — Adherence might be
  redundant there, or might be a useful secondary signal for the log-only days between
  scheduled sessions. Not resolved here.
- **Exact decay formula and window length** — deliberately left as `[HYPOTHESIS]`-style open
  constants, consistent with how `plannedDurationMin`'s 25-minute default and §5.9's tier bands
  were both flagged rather than silently fixed (ROADMAP.md §5.24, §5.9).
- **Should Adherence be visible to the person only, or does it participate in any of the
  existing report surfaces (Daily/Weekly/Monthly)?** No position taken.

---

## D. A stakes/commitment field: representing accountability mechanism, not building payment infrastructure

### D.1 The evidence

Commitment devices — pre-committing consequences for failing a goal — are among the more
robustly evidenced levers in this literature specifically. A 2024 randomized trial found
anti-charity financial stakes (money going to a cause the person opposes on failure) increased
goal completion by 34% over neutral stakes; commitment-contract platforms built entirely around
this mechanism (stickK, Beeminder) have running track records built on it. Public/social
accountability compounds the effect independently — research on accountability partnerships
finds specific, trackable public pledges outperform vague ones, which is the same specificity
principle Locke and Latham's work already establishes for goals generally, just applied to the
act of declaring the goal rather than the goal's content.

This product already has a real stakes mechanism — Tier standing, Debt, Reputation — it's just
currently entirely scoped to `EnforcementSession`. The gap this section identifies isn't "build
financial stakes," it's narrower: **the goal-level `Mission` entity has no field at all
representing what kind of accountability mechanism, if any, backs it** — which becomes a real
limitation once Outcome-driven and Constraint missions (per §C) get their own lighter Adherence
track rather than inheriting Debt/Reputation automatically. Without a stakes concept on
`Mission` itself, there's no way to represent "this Outcome mission has a real financial/social
stake attached" even at the level of recording that fact, let alone acting on it later.

### D.2 Proposed extension

A minimal, forward-compatible field — deliberately not a payment/social-integration build:

```
Mission {
  ...
  stakesType: enum[NONE, SELF_DECLARED, SOCIAL, FINANCIAL] | null   // null = not yet decided
  stakesDescription: String | null   // free text: "tell my brother if I miss," "$50 to a cause
                                       // I dislike" — descriptive, not a payment integration
}
```

- `NONE`/`SELF_DECLARED` require no new infrastructure — `SELF_DECLARED` is just a person
  writing down their own stated consequence, stored and shown back to them, same spirit as
  `MissionLogEntry.note`.
- `SOCIAL` and `FINANCIAL` are explicitly **not** proposed as buildable now — no payment
  processor, no contact-sharing mechanism exists anywhere in this codebase, and building either
  would be a materially larger scope addition than anything else in this addendum, well outside
  "shape, not implementation." They're included in the enum so the schema doesn't need a
  breaking migration later if the product direction moves toward them — the same
  "shapes, not final field lists" caveat the base proposal states for its own entities in §3
  applies here.

### D.3 Open questions

- **Is representing this field now (with no `SOCIAL`/`FINANCIAL` implementation) worth the
  schema surface, or premature** given this project's own repeated stated preference for not
  building ahead of evidence (ROADMAP.md/PRD §42's pattern, cited approvingly in the base
  proposal's own §6.4 recommendation)? Genuinely open — the counter-argument for including it
  now is narrow (avoiding a future migration), not a strong one.
- **If included, does `stakesType` interact with Adherence (§C) or Reputation at all**, or is it
  purely descriptive with zero computed effect anywhere, matching `OutputArtifact`'s existing
  "descriptive only, never scored" precedent (Data Model §2.2, PRD §13.7)? Recommend the latter
  as the safe default if this section is adopted at all.

---

## E. What does *not* change (extending the base proposal's own §7)

Everything the base proposal's §7 already states holds. In addition:

- **§A–§D introduce zero new consequence paths that write to Debt, Reputation, or Tier.**
  Trigger (§A) is descriptive/reminder-only outside the one `APP_OPEN`-on-Constraint case
  explicitly deferred to §6.2. Milestone (§B) is a derived read-only view. Adherence (§C) is a
  deliberately separate, non-Tier-feeding track. Stakes (§D) is descriptive-only per §D.3's
  recommended default.
- **No existing entity from the base proposal's §3 is modified** — every extension here is a new
  entity (`Trigger`, `Milestone`) or a new optional field on `Mission` (`adherenceScore`/
  `adherenceWindow`, `stakesType`/`stakesDescription`), additive in the same sense §3.4 of the
  base proposal already establishes for `EnforcementSession` itself.
- **§13.4's boundary (no enforcement outside a scheduled Mission window) is not crossed anywhere
  in this addendum**, except the one case already flagged as overlapping §6.2 and explicitly
  deferred to that decision rather than resolved unilaterally here.

---

## F. Suggested sequencing relative to the base proposal's §8

This addendum's four extensions are independently adoptable — accepting one doesn't require
accepting the others. Suggested order, if some but not all are taken forward:

1. **§C (Adherence) is the highest-priority extension** if the product goal is genuinely
   "enforcement and accountability for every archetype" — without it, two of three archetypes
   have no consequence mechanism at all, which is a bigger gap than anything else here.
2. **§A (Trigger) is the highest-evidence extension** (largest cited effect size) but is
   additive UI/prompt surface more than core schema — can land independently of §C, and shares
   one open question (`APP_OPEN` on Constraint) with the base proposal's own §6.2, which should
   be resolved once, together.
3. **§B (Milestone)** is useful but lower-urgency — most valuable for long-horizon Outcome
   missions specifically, a narrower slice of the three archetypes than §A or §C touch.
4. **§D (Stakes field)** is the most speculative — reasonable to defer entirely until real usage
   data says whether `SOCIAL`/`FINANCIAL` stakes are worth building, consistent with this
   project's own standing preference for evidence before mechanism.

As with the base proposal's own §8: this stops at shape. Migration scripts, exact formulas, and
UI mockups are downstream of sign-off on this addendum's open questions and the base proposal's
§6, not before.
