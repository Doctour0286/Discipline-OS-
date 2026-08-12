# Add Mission — Batch G7 Plan

**Status: PLANNED, not started.** This document is the plan only — no `.kt`/`.xml` file in this
repo has been touched to build any of what follows. Written in response to
`AUDIT_2026-08-11.md` (functional/spec-completeness audit, on-device walkthrough), which found
the single highest-impact gap in the whole app: **there is no way to create, view, or manage a
Mission after the one auto-generated row onboarding produces.** Every other Goal-Oriented
Mission Model batch (G1–G6) shipped a real UI slice; this is the one still missing, and it's the
one that makes the rest of the model actually usable rather than just present in the schema.

**Relationship to G1–G6:** those batches are all merged to `main`, CI green (`ROADMAP.md` §5.45).
This is not a continuation of an open batch — it's new, sequenced after G6 because it depends on
nothing G1–G6 didn't already ship (the `GoalMission`/`MissionPeriod`/`Trigger`/`Milestone` schema,
`GoalMissionDao`/`MissionPeriodDao`, `HomeFragment`'s mission list, `MissionDetailFragment`'s
Trigger/Milestone attach points) and blocks nothing else currently planned. Numbered G7 to keep
this project's existing batch-letter sequence intact, not because it's part of the same PR chain.

**Source of truth for every field/behavior below:** `06_GOAL_ORIENTED_MISSION_MODEL.md` §2
(archetypes) and §3.1–§3.2 (`Mission`/`MissionPeriod` entity shape), the same base document G1–G6
built against, cross-checked directly against the real shipped entity
(`data/src/main/java/com/disciplineos/data/entity/GoalMission.kt`) rather than trusted from a
summary — matching this project's own repeatedly-restated lesson (§5.36/§5.37/§5.41: summaries of
summaries are exactly how spec drift compounds silently across batches).

---

## 1. Why this is scoped as two slices, not one

`AUDIT_2026-08-11.md` Part 4 lists nine things a fully spec-built Add Mission screen needs.
Building all nine in one PR — a new archetype-conditional multi-step form, a `MissionPeriod`
picker reusing Mission Profile selection, and an inline Trigger attach step, all new — is a
large, hard-to-review, hard-to-manually-verify (no compiler in this authoring sandbox, see
`STATUS.md`'s standing gap) unit. This project's own history is consistent on what happens when a
batch tries to do too much at once before any real device/CI feedback exists (§5.33's G1
divergences, §5.41's Trigger entity mismatch) — both were caught only once a *later*, narrower
pass actually read the shipped code against spec line by line. Splitting into two slices means
Slice 1 gets CI + on-device feedback before Slice 2's more spec-sensitive pieces
(`resetMode`'s "no natural default" requirement, `MissionPeriod`'s three-way `periodType` branch)
are built on top of it.

**Slice 1 (this batch's actual scope): a real Add Mission screen covering every `GoalMission`
core field, reachable from Home, replacing onboarding's auto-generated row as the only way a
Mission gets created.** No `MissionPeriod`, no inline Trigger attach — a Mission created by Slice
1 has zero periods, same "log-only, no enforcement scope yet" state the base doc's own §3.2
explicitly allows (`enforcementProfileId` null meaning tracked/logged only — see §5 below for the
one place Slice 1 must deliberately diverge from that exact statement, and why).

**Slice 2 (separate, sequenced immediately after, not part of this PR): add at least one
`MissionPeriod` to the creation flow**, reusing the existing Mission Profile picker UI pattern
(`MissionProfileSetupFragment`'s allow/blocklist chooser) for `enforcementProfileId`, plus the
inline "attach a Trigger" step Audit Part 3.4 names. Not designed in detail here — Slice 1's own
open questions (§6 below) partly determine Slice 2's shape, so fully speccing Slice 2 now risks
the same "designed against a stale picture" failure this doc's own §1 above is trying to avoid.

---

## 2. Slice 1 — screen flow

One new destination, `addMissionFragment`, reached from a new persistent button on
`HomeScreen` (not a one-shot prompt — Audit §1.3's "no persistent add-mission affordance" finding
is the specific gap this closes). Single-screen form, not a multi-step wizard — every field below
fits on one scrollable screen without the branching complexity Slice 2's `MissionPeriod` picker
would add; matches this project's existing single-screen precedent (`MissionProfileSetupFragment`,
`MilestoneCreationScreen`) over introducing wizard/step-state machinery this codebase has never
needed before.

**Fields, in order (per Audit Part 4, items 1–7 — items 8–9 are Slice 2):**

1. **Archetype** — `MissionArchetype` (`OUTCOME_DRIVEN` / `BEHAVIOR_DRIVEN` / `CONSTRAINT`), radio
   group, no default selection. First field, since base doc §2 is explicit later fields'
   relevance/labels branch on this choice — matches `TierSelectionFragment`'s own "no default
   RadioButton pre-checked" precedent for a similarly consequential first choice.
2. **Title** — free text, required. Currently silently defaulted to the Mission Profile's name
   (Audit Part 2); this is the fix — the person types their own title, always.
3. **Target fields — shown only if archetype is `OUTCOME_DRIVEN`:**
   - `targetDirection` (`INCREASE`/`DECREASE`/`MAINTAIN`) — radio group.
   - `targetValue` — numeric text field, same `toDoubleOrNull`/"invalid disables Create" pattern
     `MilestoneCreationScreen.targetValueText` already established, reused verbatim rather than
     re-derived.
   - `unit` — free text, optional, display-only label (base doc: not parsed).
   All three fields hidden (not just disabled) for `BEHAVIOR_DRIVEN`/`CONSTRAINT` — matches base
   doc §2.2/§2.3's own framing that a pure habit/constraint Mission has no numeric target at all,
   not a target of zero or blank.
4. **Cadence type** — `CadenceType` (`DAILY`/`WEEKLY`/`CUSTOM_DAYS`/`NONE`), dropdown or radio
   group (four options; radio group matches this project's existing preference for a small fixed
   enum shown as `RadioButton`s over a `DropdownMenu`, per `TierSelectionFragment`/archetype
   above). `CUSTOM_DAYS`' actual day-of-week selection is Slice 2 scope (it belongs with
   `MissionPeriod.daysOfWeek`, not `GoalMission` — see `MissionPeriod`'s own kdoc, cadence and
   period-level day selection are two different fields answering two different questions); Slice
   1 stores the enum choice only.
5. **Reset mode** — `ResetMode` (`FIXED_CALENDAR`/`ROLLING_WINDOW`). **No default selection** —
   base doc §6.1 states this explicitly has "no natural universal default," and onboarding's
   existing hardcoded `ROLLING_WINDOW` is a named `[HYPOTHESIS]`-shortcut the base doc itself says
   not to repeat once a real creation flow exists (Audit Part 4, item 5's own wording). Create
   button stays disabled until one is chosen, same "no default, must be an explicit choice"
   enforcement `TierSelectionFragment` already uses for tier choice.
6. **Measurement source** — `MeasurementSource` (`AUTOMATIC`/`MANUAL_LOG`/`BOTH`), radio group.
7. **Starting lifecycle stage** — `LifecycleStage`, but **not all four values offered here.**
   Base doc §5 (quoted in Audit Part 4, item 7): a Mission should be able to start as
   "just watching this for now," not forced straight to `ENFORCING`. `REVIEWING` is a
   Mission's *later* state (base doc §5's own lifecycle-transition framing — a Mission arrives at
   `REVIEWING`, it doesn't start there), so offering it at creation time would be inventing a
   creation-time meaning the spec never gives it. **Slice 1 offers `OBSERVING` and
   `HYPOTHESIZING` only, defaulting to `OBSERVING`** (base doc §5's own ordering — "watching" is
   the more minimal, more clearly creation-appropriate starting point than "actively forming a
   plan"; `HYPOTHESIZING` remains one tap away for a person who already knows they want to move
   straight to Trigger-attaching). `ENFORCING` is deliberately not offered at creation — see §6
   below, this is a real, flagged divergence from "all six field values are just a picker,"
   worth a second look before Slice 1 ships.

**Create button:** disabled until every required field for the current archetype is valid
(archetype chosen, title non-blank, reset mode chosen, and — for `OUTCOME_DRIVEN` only —
`targetValue` either blank or a valid number, `targetDirection` chosen). Same validation-surfaces-
at-the-call-site posture `MilestoneCreationScreen`'s own kdoc states, not pushed into the entity
layer (`GoalMission`'s own kdoc: "this class does not attempt to validate combinations").

**On create:** inserts one `GoalMission` row via `GoalMissionDao.insert` directly — **not** a new
`:domain` use-case. Reasoning: Slice 1 is a single-table write with no cross-entity invariant to
protect (no `MissionPeriod`, no `MissionProfile`, nothing else created alongside it this slice) —
same "trivial write doesn't need use-case ceremony" posture this project already applies to
`MilestoneCreationFragment.createMilestoneAndFinish` and
`HomeFragment.recordDismissal`. **This will very likely change in Slice 2** — once
`MissionPeriod`/`MissionProfile` creation joins the same flow, that's a multi-row transactional
write with a real invariant (`MissionPeriod.enforcementProfileId` must resolve, same shape
`CreateConstraintTriggerUseCase` already handles) and should become a real `CreateGoalMissionUseCase`
wrapped in `database.withTransaction`, matching that use-case's own precedent. Flagged now so
Slice 2 doesn't have to rediscover this reasoning from scratch.

**On success:** navigate to `MissionDetailFragment` for the newly created Mission (`popBackStack`
to Home first is the wrong choice here — matches this project's existing "land on the thing you
just created" precedent, e.g. nothing currently does this differently for a comparable creation
flow, so this is Slice 1's own judgment call, flagged as such rather than copied from a
nonexistent precedent).

---

## 3. Nav graph change

One new destination and one new action, added to `onboarding_nav_graph.xml`:

```xml
<!-- Home's outgoing action -->
<action
    android:id="@+id/action_home_to_addMission"
    app:destination="@id/addMissionFragment" />

<!-- New destination -->
<fragment
    android:id="@+id/addMissionFragment"
    android:name="com.disciplineos.app.mission.AddMissionFragment"
    android:label="Add Mission">
    <action
        android:id="@+id/action_addMission_to_missionDetail"
        app:destination="@id/missionDetailFragment" />
</fragment>
```

`missionDetailFragment` already exists (G4) and already takes a `missionId: String` argument —
the "on success" navigation above reuses `MissionDetailFragment.ARG_MISSION_ID`
(`bundleOf(...)`), same pattern `HomeFragment.onOpenMissionDetail` already uses, not a new
argument-passing convention.

---

## 4. New/changed files (Slice 1)

- **New:** `app/src/main/java/com/disciplineos/app/mission/AddMissionFragment.kt` — reads/writes,
  same split every screen in this project follows.
- **New:** `app/src/main/java/com/disciplineos/app/ui/mission/AddMissionScreen.kt` — presentation
  only, all seven fields from §2 above.
- **Changed:** `app/src/main/java/com/disciplineos/app/home/HomeFragment.kt` /
  `.../ui/home/HomeScreen.kt` — new persistent "Add Mission" button, always visible (not
  conditional on `missions.isEmpty()` — Audit §1.3's core finding is that there's no way to add a
  *second* Mission either, so this must not be a first-mission-only affordance).
- **Changed:** `app/src/main/res/navigation/onboarding_nav_graph.xml` — §3 above.
- **New:** `app/src/main/res/values/strings.xml` additions — `add_mission_*` keys, following
  `milestone_creation_*`'s existing naming convention.
- **New:** `app/src/test/java/com/disciplineos/app/mission/AddMissionFragmentTest.kt` and/or a
  pure-function test for whatever validation logic (§2's Create-button-enabled predicate) is
  worth extracting the same way `computeHomeState`/`computeMissionDetailState` were — exact test
  shape decided when the screen is actually written, not speculated here.

**Not touched:** `GoalMission`/`GoalMissionDao` (already correct, per Audit Part 3 — schema has
every field this slice needs already), `DisciplineOsDatabase` (no schema version bump — no entity
change), `FirstMissionSchedulingFragment` (explicitly out of scope — see §5 below).

---

## 5. What this batch deliberately does not change

- **`FirstMissionSchedulingFragment`'s auto-generated Mission is left as-is, not removed.**
  Audit Part 2 confirms this is a real, self-flagged placeholder (Integration Plan §3.1/§3.3,
  two `[HYPOTHESIS]`-tagged field choices), not a defect needing deletion — base doc §6.6 already
  accepted its shape as the deliberate minimum-viable onboarding behavior (`ROADMAP.md` §5.45,
  Finding 1). Once this batch ships, onboarding still creates one starter Mission the way it
  always has; the difference is a person now has a real way to create *additional* ones (or, once
  Mission deletion exists — flagged as a real gap in Audit §1.3, not this batch's scope either —
  to eventually replace the starter one). Conflating "add real mission creation" with "remove the
  onboarding placeholder" would be scope creep this plan deliberately avoids.
- **No Mission deletion/archival UI.** `GoalMission.archivedAt` already exists in the schema
  (unused by any UI, same as every other field this audit found). Real, named gap — Audit §1.3
  calls it out specifically — but a separate batch: deleting/archiving a Mission touches
  different screens (wherever the Mission list/detail actions live) and has its own open questions
  (does archiving cascade to its `MissionPeriod`s? what happens to an in-progress
  `EnforcementSession`?) this plan hasn't worked through.
- **No settings screen**, despite onboarding's Core Data Consent screen promising one
  ("wipe your local data at any time from Settings" — Audit §1.3 confirms no Settings destination
  exists anywhere in the nav graph). Real, separate gap, unrelated to Mission creation.
- **`MissionPeriod`, enforcement-profile picking, and inline Trigger attach — Slice 2, §1 above.**
- **No changes to `ConsequencePolicy`, `InterceptionController`, `MissionAccessibilityService`,
  `TierTransitionUseCase`, or `ReputationDecayPolicy`** — same boundary Integration Plan §9
  already states for G1–G6, restated here because it still holds: a Slice-1-created Mission with
  zero `MissionPeriod`s cannot be violated (no `EnforcementSession` references it), so nothing
  about the enforcement/consequence layer needs to know this batch exists.

---

## 6. Open questions — flagged for sign-off, not decided unilaterally

Same posture as every other batch's own open-questions section (Integration Plan §8) — these are
real forks this plan's author does not have standing to resolve alone.

### 6.1 Is excluding `ENFORCING` from the creation-time lifecycle picker correct?

§2 item 7 above chose to offer only `OBSERVING`/`HYPOTHESIZING` at creation, reasoning that
`ENFORCING` needs an actual `MissionPeriod`/enforcement scope to mean anything (an `ENFORCING`
Mission with no period is a state nothing else in the schema produces or expects). But Slice 1
ships *before* Slice 2 adds period creation — so under this plan, **no Slice-1-created Mission can
reach `ENFORCING` at all until Slice 2 ships**, even manually. Two ways to resolve, genuinely
open:
- Accept this as correct and temporary — Slice 1 Missions are observe/hypothesize-only by
  construction until Slice 2 lands, which could be the very next PR.
- Offer `ENFORCING` anyway, accepting a Mission can exist in that state with zero periods (already
  representable by the schema, just not meaningfully enforcing anything) until the person also
  builds a period some other way.

This plan's default is the first option (§2 above already reflects it) — flagged for explicit
confirmation before Slice 1 ships, not a silent assumption.

### 6.2 Should Slice 1 route through Mission Detail's existing "attach a Trigger" prompt?

`MissionDetailFragment` already shows a one-shot Trigger-attach prompt during `HYPOTHESIZING`
(G5). If Slice 1 defaults new Missions to `OBSERVING` (per §6.1's default), that prompt won't
fire immediately for a freshly created Mission — matches base doc §4.3's "during HYPOTHESIZING"
condition exactly, so likely correct behavior, not a gap — but worth confirming this interaction
was actually intended, not just an accident of two batches never being designed against each
other directly.

### 6.3 `CUSTOM_DAYS` cadence with no day-of-week UI in Slice 1

§2 item 4 stores the `CadenceType.CUSTOM_DAYS` enum value in Slice 1 without collecting which
days — that data lives on `MissionPeriod.daysOfWeek` (Slice 2), not `GoalMission`. A person who
picks `CUSTOM_DAYS` in Slice 1 gets a Mission whose cadence type says "custom days" with no days
actually specified anywhere yet. Options: hide `CUSTOM_DAYS` from Slice 1's cadence picker
entirely (three choices, not four, until Slice 2 exists to complete it) versus allow it and treat
"no period yet" as the reason the days aren't set (same "no period = tracked/logged only, nothing
to configure yet" framing this whole slice already rests on). Not resolved here — worth deciding
before writing the picker, not after.

---

## 7. Verification checklist (mirrors this project's standard batch checklist)

- [ ] CI green (`:app:testDebugUnitTest` at minimum — no compiler in the authoring sandbox, per
      `STATUS.md`'s standing gap; every claim below "written, not yet CI-confirmed" until a real
      run says otherwise)
- [ ] On-device confirmed: Add Mission reachable from Home via the new persistent button (not
      just once, not just when the mission list is empty), all seven fields render and validate
      per §2, Create writes a row that's immediately visible on Home's mission list and openable
      via Mission Detail
- [ ] Confirm directly (not assumed) that a Mission created via this screen with archetype
      `BEHAVIOR_DRIVEN`/`CONSTRAINT` never shows the three `OUTCOME_DRIVEN`-only fields, and that
      switching archetype mid-form correctly clears any values already entered in fields that
      just became hidden (an easy state bug in a conditional form — worth an explicit device
      check, not just a code read)
- [ ] Confirm §6's three open questions were actually signed off on, not silently shipped with
      this plan's own stated defaults
