# DisciplineOS — Data Model & Schema
### Companion to PRD v3.6 — Blocking Document #1

**Purpose:** the PRD specifies *behavior* (what the Debt Ceiling does, what triggers Recovery Mode) but not *mechanism* (the actual formulas, storage schema, and computation rules). This doc is the source of truth for every number the app computes. Nothing here should contradict the PRD; where the PRD leaves a value unresolved (flagged in §42), this doc either proposes a default with a stated confidence level or explicitly marks it TBD-pre-launch.

---

## 1. Design Principles for This Document

1. Every derived value (score, index, decay curve) must have: a formula, an update trigger, a storage representation, and a stated confidence level (Validated / Hypothesis / Placeholder).
2. Nothing in here should silently resolve an Open Question from PRD §42 — where a formula requires picking a number the PRD flags as unvalidated, that number is marked `[HYPOTHESIS]` and logged back to the Metrics & Experimentation backlog, not treated as settled.
3. Unsupervised Reliability data (§13 of PRD) gets schema isolation, not just access-control isolation — enforced at the table/collection level, not just the query level, so "no enforcement path can touch this" is structurally true, not just policy-true.

---

## 2. Core Entities

### 2.1 User
```
User {
  id: UUID
  created_at: timestamp
  current_tier: enum[Recruit, Operator, Warden, Iron]
  tier_selected_at: timestamp
  tier_activation_at: timestamp        // may lag tier_selected_at — see §5 Iron calibration gate
  onboarding_consent_version: string   // which consent doc version they agreed to
  unsupervised_reliability_opt_in: bool
  unsupervised_reliability_opt_in_at: timestamp | null
  data_export_requests: [ExportRequest]
  deletion_requests: [DeletionRequest]
}
```

### 2.2 Mission
```
Mission {
  id: UUID
  user_id: UUID
  scheduled_start: timestamp | null    // null if ad hoc — feeds Self-Initiation Trend
  actual_start: timestamp
  actual_end: timestamp | null
  planned_duration_min: int
  status: enum[active, completed, violated, disputed, aborted_crisis_exit]
  allowlist: [package_id]
  blocklist: [package_id]
  mission_profile_id: UUID
  output_artifacts: [OutputArtifact]   // §13.7 Mission Output Intelligence — descriptive only, never scored
}
```

### 2.3 Violation
```
Violation {
  id: UUID
  mission_id: UUID
  detected_at: timestamp
  type: enum[blocklist_access, early_exit, non_start]
  dispute_status: enum[none, flagged, under_review, upheld, overturned]  // §26.4
  dispute_flagged_at: timestamp | null
  consequence_paused: bool             // true while dispute_status = flagged or under_review
}
```

### 2.2a Goal-Oriented Mission Model — supersedes 2.2's single-`Mission` shape

**Status: partially implemented — schema landing in Batch G1's conformance pass.** Full design
lives in `Documents/06_GOAL_ORIENTED_MISSION_MODEL.md`; this section is a schema-level summary,
not a duplicate — read the companion doc for the reasoning, examples, and open questions. Folded
in here per that doc's own §0 instruction ("once accepted... reduce to a historical pointer").

**Correction (this pass):** this section's field list and migration note previously diverged
from `06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §2.1/§9 — the actual engineering-ready
source, checked in unmodified in the same commit that added this summary. That divergence was
never a deliberate decision; it looks like this summary was drafted independently rather than
derived field-by-field from the plan it's meant to summarize. The field list and migration note
below are corrected to match the Integration Plan exactly. See `EnforcementSession.kt` and
`GoalMission.kt`'s own kdoc for the as-built shape.

**Why 2.2 is being superseded, not appended to:** the single flat `Mission` entity conflated two
different things — a *goal* ("finish the report," open-ended, may span days) and an *enforcement
window* (one locked, monitored session against a device). The live codebase's `Mission.kt`
already matches §2.2 exactly (confirmed directly against
`data/src/main/java/com/disciplineos/data/entity/Mission.kt` before writing this note, not
assumed) — so this is a real rename/restructure of existing shipped code, not a greenfield
addition. `BUILD_PLAN.md`'s Batch G1–G6 sequence is the implementation order; nothing below is
built yet.

**The five entities, at schema level** (see the companion doc for full field-by-field rationale):

```
GoalMission {
  id: UUID
  user_id: UUID
  title: string
  archetype: enum[outcome_driven, behavior_driven, constraint]
  target_direction: enum[increase, decrease, maintain] | null
  target_value: double | null
  unit: string | null
  cadence_type: enum[daily, weekly, custom_days, none]
  reset_mode: enum[fixed_calendar, rolling_window]
  measurement_source: enum[automatic, manual_log, both]
  lifecycle_stage: enum[observing, hypothesizing, enforcing, reviewing]
  adherence_score: double | null
  adherence_window: int | null                       // days
  created_at: timestamp
  archived_at: timestamp | null
  // no mission_profile_id — a goal has no default enforcement scope of its own; each
  // EnforcementSession under it carries its own mission_profile_id instead.
}

EnforcementSession {                 // renamed from Mission (§2.2) — same enforcement mechanics, new parent
  id: UUID
  user_id: UUID
  mission_id: UUID                   // non-null — every session has a real parent GoalMission
  mission_period_id: UUID | null     // null = not generated from a recurring template
  scheduled_start: timestamp | null
  actual_start: timestamp
  actual_end: timestamp | null
  planned_duration_min: int
  status: enum[active, completed, violated, disputed, aborted_crisis_exit]
  allowlist: [package_id]
  blocklist: [package_id]
  mission_profile_id: UUID
  output_artifacts: [OutputArtifact]
}

MissionPeriod {                      // recurring-schedule template a GoalMission can generate sessions from
  id: UUID
  mission_id: UUID
  period_type: enum[fixed_window, deadline, always_on]
  days_of_week: [enum[mon..sun]]
  window_start: local_time | null    // FIXED_WINDOW fields
  window_end: local_time | null
  target_duration_min: int | null
  deadline_time: local_time | null   // DEADLINE field
  enforcement_profile_id: UUID
}

MissionLogEntry {                    // freeform user note/checkpoint against a GoalMission — descriptive only, never scored
  id: UUID
  mission_id: UUID
  created_at: timestamp
  note: string
}

Trigger / Milestone {                // see companion doc §2 for the two entities' distinct roles
  // Trigger: condition that can auto-generate or flag an EnforcementSession under a GoalMission
  // Milestone: a progress checkpoint within a GoalMission with a numeric/deadline-style target,
  //   descriptive only
}
```

**Hard boundary carried over unchanged from §2.2/§2.3, restated explicitly so the rename doesn't
blur it:** `Violation`, `LedgerEntry`, Reputation, and Discipline Debt all key off
`EnforcementSession`, never off `GoalMission` directly. A `GoalMission` cannot be violated —
only a session enforced under it can. This mirrors §7's `UnsupervisedSignal` isolation pattern:
scoring reaches down to the session, not up to the goal.

**Migration note:** per Integration Plan §9, this is **not** a real Room `Migration` — a
destructive version bump (`fallbackToDestructiveMigration()`), matching this project's v2
through v8 precedent. No release tag, no store listing, no real installed base exists as of this
pass, so there's nothing a real Migration would meaningfully preserve. This corrects this
section's prior claim that a real `Migration` was required — that claim was never derived from
the Integration Plan's own reasoning (§0/§9), which re-confirms the destructive-fallback
justification explicitly rather than assuming it. See
`data/src/main/java/com/disciplineos/data/db/DisciplineOsDatabase.kt`'s own version-history
comments for the exact current version and full migration chain.

---

### 2.4 UnsupervisedSignal — isolated schema/namespace, per §13.3
```
// Stored in a physically or logically separate store from all scoring tables.
// No foreign key from any scoring/consequence table may reference this table.
UnsupervisedSignal {
  id: UUID
  user_id: UUID
  captured_at: timestamp
  signal_type: enum[
    voluntary_high_value_return,
    voluntary_high_risk_avoidance,
    self_initiated_mission_start,
    unscheduled_use_pattern,
    self_report_capacity        // §13.2.1 — Brief Self-Control Scale, monthly cadence
  ]
  value: jsonb
}
```

---

## 3. Scored Metrics — Formulas and Confidence Levels

### 3.1 Discipline Score — CUT FROM MVP `[REVISED — post-draft review]`
- **Confidence:** Placeholder → **removed.** An earlier draft of this doc shipped with invented weights (0.35/0.25/0.25/0.15) presented as a "proposed default." Those numbers were not derived from anything in the PRD or from any data — they were filled in to make the formula complete, which is exactly the kind of unearned precision this PRD's own revision history (§0.1's Castelo citation correction, §0's repeated self-audits) treats as a defect, not a style choice. Inventing plausible-looking constants and labeling them "tunable" is worse than leaving the gap visible, because it invites the number to be trusted before it's earned that.
- **Decision:** do not compute or display a single composite Discipline Score for MVP. Surface the four inputs — Reliability Index, Resistance Score, Focus Integrity, Discipline Reserve — as separate values instead. Nothing in the PRD's MVP scope (§41) actually requires a single composite number; "Discipline Score" is named as an MVP-included feature, but the PRD never specifies it as a composite versus a dashboard of components, so this satisfies the requirement without fabricating a formula.
- **Path back in, if wanted post-launch:** weights should be *fit*, not chosen — e.g. against which combination of the four components best predicts 30-Day Retention or Tier Stability Rate, both of which are already being tracked (§41, §42). Until there's enough Mission data to do that, any single number here is decoration, not measurement.
- **If a composite is wanted sooner than that:** the fallback is equal weighting (0.25 each), stated as equal weighting in-product rather than presented as tuned — "we don't yet know which of these matters most, so we're not pretending to" is a defensible thing to ship; invented decimals are not.
- **Never includes:** any UnsupervisedSignal value (hard constraint, §13.3) — this holds regardless of whether a composite ships.

### 3.2 Reliability Index (co-primary KPI, PRD §5.1)
- **Confidence:** Validated as a metric definition; **thresholds are Hypothesis** (85%/10 days flagged unvalidated in §42)
- **Formula:**
  ```
  ReliabilityIndex = completed_missions / (completed_missions + violated_missions)
                      over a rolling N-day window (default N=14, matches Debt Ceiling window)
  ```
- Excludes missions with `dispute_status = upheld` in the *user's* favor (overturned violations don't count against them) — this wiring is a **hard requirement**, not optional, per §26.4.

### 3.3 Unsupervised Reliability Trend (co-primary KPI, PRD §5.1)
- **Confidence:** Hypothesis (this is the newest co-primary signal; no production data yet)
- **Formula:** trend line, not a point score — reported as direction (↑/→/↓) over rolling 90-day window of UnsupervisedSignal favorable-vs-unfavorable event ratio.
- **Critical constraint:** this value is computed and displayed, but has **zero write access** to Tier, Discipline Score, or any consequence table. Enforce this in code review with an explicit lint/CI check (see §7).

### 3.4 Discipline Debt & Debt Ceiling
- **Confidence:** Ceiling *mechanism* validated (bounded consequences is a core, settled decision); **ceiling value and quartile markers are Hypothesis**
- **Formula:**
  ```
  DebtCeiling = 14 * avg_mission_duration_min   // [HYPOTHESIS — flagged §42: should this scale with tier?]
  Debt += violation_penalty (tier-dependent) on each Violation
  Debt -= decay_rate_per_day * elapsed_days      // tier-dependent decay rate, [HYPOTHESIS]
  Debt = clamp(Debt, 0, DebtCeiling)
  ```
- **Quartile markers (§27.1.1, v3.6):** display-only sub-goals at 25%/50%/75% of ceiling. No functional effect on enforcement — purely a UI/motivation layer. Store as computed display values, not persisted state.

### 3.5 Reputation (decay-based demotion)
- **Confidence:** Hypothesis (decay rate explicitly unresolved, PRD §42)
- **Formula:**
  ```
  Reputation -= decay_per_missed_day   // [HYPOTHESIS — tunable constant, needs post-launch data]
  Reputation += recovery_per_completed_mission
  demotion_triggered when Reputation < tier_floor for tier N consecutive days
  ```
- **Shared-cause guard (§27.2 — hard engineering requirement, not optional):** a single missed Mission must not simultaneously max out Debt Ceiling contribution *and* trigger Reputation demotion from the same root cause without a deduplication check. Implementation: tag each Violation with a `root_cause_cluster_id`; Debt and Reputation consequence calculators must both check for existing same-cluster consequences within a rolling window before applying a second penalty.

### 3.6 Self-Initiation Trend (§13.2.2)
- **Confidence:** Hypothesis (brand new in v3.5)
- Lives entirely in the UnsupervisedSignal namespace. Computed as ratio of `self_initiated_mission_start` events to system-prompted starts, rolling 90-day window. **Reported, never scored** — same hard constraint as §3.3.

---

## 4. Behavioral Fingerprint & Predictive Failure Engine

- **Confidence:** Hypothesis (model architecture unspecified in PRD — flagged here as a real gap, not just unresolved constants)
- Inputs: Mission history, Violation timing/type, UnsupervisedSignal (feeds prediction quality only, per §13.5 — does not feed scoring).
- **Decision:** rules-based heuristic for MVP, not a trained model — for the reasons given in the previous draft (no cold-start data volume pre-launch; auditability matters at Warden/Iron where a wrong prediction carries real weight; a black-box model reopens the false-positive problem §26.4 was built to handle for violations, without even §26.4's clear dispute semantics, since disputing a *prediction* rather than a *violation* isn't well-defined).
- **The actual rules are specified in a companion document, not here** — `04_BEHAVIORAL_FINGERPRINT_RULES_SPEC.md`. This doc previously left "rules-based" as an unbuilt placeholder in exactly the way it warns against elsewhere (§1, principle 2) — naming an approach without specifying it is a smaller version of the same problem as the invented Discipline Score weights above. See that doc for the actual rule set, confidence levels per rule, and the migration path to a trained model once there's enough production data to justify one.

---

## 5. Tier State Machine

```
Recruit → Operator → Warden → Iron
```
- **Iron calibration gate (§12.6, hard requirement):** `tier_activation_at` for Iron cannot precede `(tier_selected_at + calibration_window)`. Default calibration window = 10 days **[HYPOTHESIS — flagged §42 for scaling by signal quality]**. Store `calibration_window_days` as a per-user computed field, not a global constant, so the future scaling logic in §42 doesn't require a schema migration later.
- **Crisis exit (§12.4.4, hard requirement):** Iron-tier Missions must expose an in-context exit from the interception screen itself. Schema needs a `Mission.aborted_crisis_exit` status distinct from `violated` — this must **not** write to Debt or Reputation. Treat it like an overturned dispute for consequence purposes.

---

## 6. Dispute Flow (§26.4)

```
Violation.dispute_status: none → flagged → under_review → {upheld | overturned}
```
- On `flagged`: `consequence_paused = true` freezes both Debt and Reputation writes for that Violation until resolution.
- On `overturned`: Violation excluded from Reliability Index denominator (§3.2), Debt penalty reversed, Reputation penalty reversed.
- **Engineering note:** this requires Debt/Reputation to be event-sourced (append-only ledger of penalty events tied to `violation_id`), not simple mutable counters — otherwise "reverse this penalty" has no clean implementation. Recommend an `Ledger` table:
```
LedgerEntry {
  id: UUID
  user_id: UUID
  violation_id: UUID | null
  metric: enum[debt, reputation]
  delta: float
  applied_at: timestamp
  reversed_at: timestamp | null
  reversed_reason: string | null
}
```
Current Debt/Reputation values are always `sum(delta) where reversed_at is null`.

---

## 7. Structural Enforcement of "Measurement Never Enforces" (§13.3)

This is a hard product requirement, but it needs a **hard technical enforcement**, not just a code-review convention:
- `UnsupervisedSignal` table has no foreign key relationships to `LedgerEntry`, `Mission`, `Violation`, or any Tier/consequence table.
- CI check (recommend a simple static analysis or lint rule): fail the build if any function reading from `UnsupervisedSignal` writes to `LedgerEntry`, `DisciplineScore`, `ReliabilityIndex`, or `Tier`.
- This makes §13.3 a build-breaking violation to bypass, not a policy someone can quietly route around under deadline pressure.

---

## 8. Open Items Requiring Pre-Launch Resolution (cross-referenced to PRD §42)

| Item | PRD Reference | Status |
|---|---|---|
| Debt Ceiling scaling by tier | §42 | Hypothesis — default flat 14-day window, needs decision before Iron launches |
| Reputation decay rate | §42 | Hypothesis — placeholder constant, needs A/B or pilot data |
| Iron calibration window scaling by signal quality | §42, v3.5 | Schema supports it (`calibration_window_days` per-user); logic itself TBD |
| Discipline Score composite | Not in PRD at all | **Resolved this revision** — cut from MVP, four components shown separately; see §3.1 |
| Behavioral Fingerprint rule set | Not in PRD at all | **Resolved this revision** — see companion doc `04_BEHAVIORAL_FINGERPRINT_RULES_SPEC.md` |
| Debt-Reliability Divergence false-positive rate | §42 | Explicitly post-launch only per PRD — don't gate MVP on this |
| Goal-Oriented Mission Model (`GoalMission`/`EnforcementSession` split) | Not in PRD — post-v3.6 addition | **Accepted, not yet implemented.** See §2.2a above, `06_GOAL_ORIENTED_MISSION_MODEL.md`, and `BUILD_PLAN.md` Batches G1–G6. |

---

## 9. What This Document Does Not Cover

- UI representation of these values (→ Consent & Onboarding / UX doc)
- Where computation happens (client/on-device vs. server) — that's an architecture decision, not a schema decision (→ Architecture doc)
- Actual ML model selection for Behavioral Fingerprint, if it goes that route
