# DisciplineOS — Build Roadmap & Agent Handoff

**Read this file first, before touching code or the spec docs.** It's the single source of
truth for *what stage this project is at* — the spec docs (`Discipline OS/Documents/`) say
what to build; this file says what's actually built, what's next, and what's already been
decided so it doesn't get relitigated.

**Update this file at the end of every work session**, even a short one. Future-you and any
other agent depend on this being current, not the code being self-explanatory. If you finish
a task, check it off *and* fill in the "Actual" column. If you make a judgment call the specs
didn't settle, log it in §5 before you move on — not after, when you'll have forgotten why.

---

## 0. What this project is, in one paragraph

DisciplineOS is a native Android app that enforces time-boxed "Missions" — during a Mission,
the phone becomes a purpose-built device (allowlist/blocklist enforced via Accessibility
Service), and violations cost the user Discipline Debt and Reputation, bounded by a hard
ceiling so consequences can't spiral past what's recoverable. Four consent-based enforcement
tiers (Recruit → Iron) let severity match the user, not assume maximum intensity for
everyone. It is being built for **personal/friends use, not a public store launch** — see
`Documents/05_CRISIS_AND_NON_DIAGNOSTIC_BOUNDARY.md` §Context on scope and
`Documents/02_SYSTEM_ARCHITECTURE.md` §3.2 for what that does and doesn't relax.

**The spec is unusually rigorous** — it self-audits, cites and re-verifies its own research
claims, and explicitly cuts features (a composite Discipline Score) rather than ship invented
numbers. Match that bar. If you're about to invent a constant, a threshold, or a plausible-
sounding default the spec doesn't give you, stop — mark it `[HYPOTHESIS]` and flag it here or
in code, the way the specs themselves do. Silently resolving an open question is the one
failure mode this project has repeatedly and explicitly designed itself away from.

---

## 1. Source documents (read in this order if you haven't)

| Doc | Role |
|---|---|
| `Documents/DisciplineOS_PRD_v3_6.md` | Product requirements — the "what and why." Start here. |
| `Documents/01_DATA_MODEL_AND_SCHEMA.md` | Every formula, table, confidence level. Source of truth for numbers. |
| `Documents/02_SYSTEM_ARCHITECTURE.md` | Where code runs, platform risk, AI Voice hosting, Play Store posture. |
| `Documents/03_ONBOARDING_CONSENT_AND_INTERACTION_SPEC.md` | Every screen, copy requirement, consent sequencing. |
| `Documents/04_BEHAVIORAL_FINGERPRINT_RULES_SPEC.md` | The five predictive rules (F1–F5), trigger conditions, sample-size gates. |
| `Documents/05_CRISIS_AND_NON_DIAGNOSTIC_BOUNDARY.md` | Non-diagnostic boundary, referral path. Short, load-bearing, don't skip it. |

None of these are superseded by this roadmap — this file sequences the work; it doesn't
change any decision the specs made. If something here ever conflicts with a spec doc, the
spec doc wins and this file has a bug — fix this file, don't quietly follow the roadmap
instead.

---

## 2. Build order — phases

Each phase lists: what it delivers, which spec sections it implements, and its exit
criteria (how you know it's actually done, not just started). **Do not start a phase until
the previous phase's exit criteria are met** — the dependency order is real, not just
tidiness (e.g. you cannot pilot Reputation decay rate without Missions actually producing
Violations to decay against).

### Phase 0 — Data Layer  ✅ **DONE** (this session)

**Delivers:** Room entities, two physically-isolated encrypted databases, event-sourced
Ledger, pure metric-formula functions, the §7 structural-isolation boundary test.

**Implements:** Data Model doc §2 (entities), §3 (formulas), §6 (Ledger/dispute flow), §7
(isolation enforcement); Architecture doc §3.1 (SQLCipher, local-first).

**Exit criteria:**
- [x] `User`, `Mission`, `Violation`, `OutputArtifact`, `LedgerEntry`, `UnsupervisedSignal` entities exist and match spec fields
- [x] `DisciplineOsDatabase` and `UnsupervisedDatabase` are separate Room databases, separately encrypted
- [x] Ledger is append-only; current value always derived via `SUM(delta) WHERE reversedAt IS NULL`
- [x] `ArchitectureBoundaryTest` exists and passes against current tree (verified by import-scan, see `data/src/test/.../ArchitectureBoundaryTest.kt`)
- [x] Pure formula unit tests exist for Reliability Index, Debt Ceiling, clamping, quartile markers, Iron calibration gate
- [ ] **NOT YET DONE:** actually run `./gradlew test` in a real Android environment — this sandbox has no Android SDK/Gradle, so everything above is verified by careful reading and import-grep, not a real build. **This is the single highest-priority unverified item in the whole project.** First thing the next session should do, before writing more code on top of it.

**Location:** `data/` module. See `data/src/main/java/com/disciplineos/data/`.

**Known open items carried forward** (not blockers, just not yet resolved — tracked in §5):
- `ViolationDao.countingViolationsSince()` resolves a PRD §3.2-vs-§2.3 ambiguity (prose vs.
  enum semantics) one specific way — flagged in §5.1 below, needs your sign-off.
- No `TierDao` yet — tier transition logic (Iron calibration gate enforcement, Crisis
  Downgrade, Standard Downgrade) doesn't exist as code yet, only as the pure
  `ironCalibrationSatisfied()` function. Real tier *state machine* logic is Phase 1.
- No Reputation write logic yet (formula exists in Data Model §3.5 but no DAO method
  applies it) — needs the shared-cause guard (§27.2) implemented, which needs
  `rootCauseClusterId` wiring that doesn't exist yet.
- No dispute *resolution* logic yet — `ViolationDao.resolveDispute()` exists as a raw update,
  but nothing calls `LedgerDao.reverseEntriesForViolation()` from it yet. These two need to
  be wired together transactionally (Room `@Transaction`) — currently they're two separate
  DAO calls a caller could invoke out of order or only one of.

---

### Phase 1 — Domain / Use-Case Layer  🟡 **IN PROGRESS**

**Delivers:** the actual business logic sitting on top of Phase 0's storage — a
`RecordViolationUseCase`, `ResolveDisputeUseCase` (wiring Ledger reversal + Violation status
transactionally), `TierTransitionUseCase` (Iron calibration gate, Standard/Crisis Downgrade,
Recovery Mode activation), `ApplyReputationDecayUseCase` (with the §27.2 shared-cause guard
actually implemented), and `TierDao`/`Tier` state persistence (currently `User.currentTier`
is a raw field with no transition-logic wrapper).

**Implements:** PRD §12 (tiers, downgrades), §26.4 (dispute flow — the *logic*, Phase 0 only
built the *storage*), §27.2 (shared-cause guard), §29 (Recovery Mode triggers), §35
(Reputation decay-based demotion), §12.4.4 (Iron crisis exit — must not write to
Debt/Reputation, same handling as an overturned dispute).

**Exit criteria:**
- [x] Recording a Violation and applying its Debt/Reputation penalty happens in one
      transactional use-case, never as separate uncoordinated DAO calls
      — `RecordViolationUseCase` (`domain/.../usecase/`), wrapped in `DisciplineOsDatabase.withTransaction`.
      **Written but NOT YET run against a real compiler/Gradle** — see item 2 in §4 below,
      still the single highest-priority unverified item in the project.
- [x] Disputing → overturning a Violation correctly reverses ledger entries AND excludes it
      from Reliability Index AND doesn't leave Debt/Reputation briefly wrong mid-flow
      — `ResolveDisputeUseCase` (`domain/.../usecase/`), covering both `fileDispute()`
      (flag → pause) and `execute()` (UPHELD → unpause / OVERTURNED → reverse). Reliability
      Index exclusion already existed via `countingViolationsSince()`'s `!= 'OVERTURNED'`
      filter (Phase 0) — confirmed correct against the PRD in §5.1 below, not re-implemented.
      **Required a real schema change**, not just new use-case code: see §5.7 below —
      `LedgerEntry` had no way to express "paused, not yet reversed," which PRD §26.4
      requires (filing a dispute must pause *already-written* ledger entries, since a
      Violation normally already has entries by the time it can be disputed). Added
      `LedgerEntry.pausedAt`, bumped `DisciplineOsDatabase` to schema version 2 (no migration
      written — see §5.7, this is a pre-launch app with no migration story at all yet).
      Test written in `ResolveDisputeUseCaseTest`, same "written, hand-verified against real
      signatures, never executed" status as everything else this session.
- [x] Shared-cause guard (§27.2) has a real implementation, not just a schema column —
      needs a test proving two penalties from the same `rootCauseClusterId` don't double-apply
      — implemented in `RecordViolationUseCase.clusterAlreadyHasActiveEntry()`, backed by new
      `ViolationDao.forRootCauseCluster()` query. Test written in
      `RecordViolationUseCaseTest` (Robolectric + in-memory Room) but **not yet executed** —
      no Android/Robolectric runtime in this sandbox. See §5.5/§5.6 below for the two
      judgment calls made here.
- [ ] Iron calibration gate is enforced at the point of tier activation, not just computable
      as a pure function someone has to remember to call
- [x]* Crisis exit (`ABORTED_CRISIS_EXIT`) provably does not write to Ledger — this needs its
      own test, given how much the specs treat this as a hard requirement (Data Model §5)
      — `RecordViolationUseCase.execute()` hard-`require`s the Mission is not
      `ABORTED_CRISIS_EXIT` (fails loudly rather than silently no-op'ing); test in
      `RecordViolationUseCaseTest`. *Marked with an asterisk: this only proves
      `RecordViolationUseCase` refuses crisis-exit missions — it doesn't yet prove nothing
      *else* in the app could route one there instead. `TierTransitionUseCase`/whatever
      handles crisis exit directly still needs to be written and needs its own equivalent
      test once it exists.
- [ ] Reputation decay rate constant is still `[HYPOTHESIS]`-tagged and easily swappable —
      do NOT pick a "reasonable-sounding" number here; use the spec's placeholder posture
      — `ApplyReputationDecayUseCase` not yet written.

**Why this phase before enforcement/UI:** the enforcement loop (Phase 2) and every UI screen
(Phase 3) will call into this layer. Building them against raw DAOs instead would mean the
transactional/guard logic gets duplicated or skipped at each call site — exactly the kind of
drift the specs' own CI-check instinct (§7) exists to prevent elsewhere.

---

### Phase 2 — Core Enforcement Loop (Android-specific)  ⬜ **NOT STARTED**

**Delivers:** the actual Accessibility Service, foreground-app detection, Mission
interception overlay, and the countdown/crisis-exit screen's underlying logic (not yet
final visual design — Architecture doc §6 explicitly defers screen layout to Phase 3).

**Implements:** Architecture doc §1 (Accessibility Service mechanism), §2 (AI Accountability
Engine — Warden/Recalibration Voice split, on-device latency requirements, fallback bank),
PRD §12.4 (interception/countdown mechanics).

**Hard blocker before this phase starts, per Architecture doc §1.2:** draft the Accessibility
Service Play Console declaration text and confirm 2–3 comparable apps are still live (not
delisted) — **this needs a fresh web search when you get here, not reliance on training data,
since Play Store enforcement posture changes.** Even though this build targets personal/
friends distribution today (doc 05's scoping), Architecture §4 treats this as worth resolving
early rather than assuming it's moot — cheap to check now, expensive to discover later if
distribution scope ever changes.

**Exit criteria:**
- [ ] Accessibility Service detects foreground app changes and can intercept a blocklisted
      app during an active Mission
- [ ] Interception screen shows Warden Voice (Warden/Iron tiers) or informational content
      (Recruit/Operator), per tier-dependent content in Onboarding doc §3.1
- [ ] Iron-tier crisis exit reachable *from the interception screen itself*, not buried in
      settings (Onboarding doc §3.1, hard requirement)
- [ ] AI Voice call has a hard local fallback bank if generation times out (~2s starting
      ceiling, Onboarding doc §3.1) — screen must never show blank/error at this moment
- [ ] Fallback bank content passes the same behavior-vs-identity review as generated content
      (Architecture §2.1) — this is a content review task, not just an engineering one; don't
      let it slip through as "just placeholder text for now"

---

### Phase 3 — Onboarding & Core UI  ⬜ **NOT STARTED**

**Delivers:** the full onboarding sequence and the in-product screens that aren't the
interception overlay (Tribunal, dispute flag, reports, Predictive Failure Alert cards).

**Implements:** Onboarding, Consent & Interaction Spec doc, in full — this doc already
specifies screens/copy/sequencing in enough detail to build directly from it.

**Exit criteria:** see the doc's own §2 screen-by-screen list — treat each screen's stated
content requirements as this phase's checklist rather than duplicating it here.

---

### Phase 4 — Behavioral Fingerprint / Predictive Failure Rules  ⬜ **NOT STARTED**

**Delivers:** F1–F5 rule implementations, the shared Predictive Failure Alert UI pattern
(Onboarding doc §3.5), per-rule accuracy tracking.

**Implements:** `04_BEHAVIORAL_FINGERPRINT_RULES_SPEC.md` in full.

**Depends on:** Phase 1 (needs real Mission/Violation data flowing) and Phase 3's alert card
pattern existing to render into.

---

### Phase 5 — Pilot & Hypothesis Resolution  ⬜ **NOT STARTED**

**Delivers:** nothing new in the app — this phase is about *using* the app (yourself, a few
friends) to gather enough real data to move the `[HYPOTHESIS]`-flagged constants to
`[VALIDATED]`: Debt Ceiling scaling, Reputation decay rate, Iron calibration window,
Mission Profile Drift threshold (F5), Reliability Index thresholds.

**Exit criteria:** each item in Data Model doc §8's table has moved from Hypothesis to a
decided value with a stated rationale, or an explicit "still insufficient data, revisit
after N more days" note — never a silently-picked number.

---

## 3. Current state snapshot

**Last updated:** 2026-08-06 (this session)
**Current phase:** Phase 0 complete (pending real-environment test verification); Phase 1
in progress — `RecordViolationUseCase` and `ResolveDisputeUseCase` written this session,
`TierTransitionUseCase` and `ApplyReputationDecayUseCase` still not started. **Nothing in
either module has been through a real compiler yet** — see §4 item 2.

```
Phase 0 — Data Layer            ████████████████████░  ~95% (code done, untested in real Gradle/Android env)
Phase 1 — Domain/Use-Cases      █████████░░░░░░░░░░░░  ~40% (2 of ~4 use-cases written, none compiled/run)
Phase 2 — Enforcement Loop      ░░░░░░░░░░░░░░░░░░░░░  0%
Phase 3 — Onboarding & UI       ░░░░░░░░░░░░░░░░░░░░░  0%
Phase 4 — Fingerprint Rules     ░░░░░░░░░░░░░░░░░░░░░  0%
Phase 5 — Pilot                 ░░░░░░░░░░░░░░░░░░░░░  0%
```

**No `app` module exists yet** — only `data` and (new this session) `domain`, both Android
library modules. There is nothing installable yet, even for a smoke test, and no
`settings.gradle.kts` tying them together into one buildable project. That's expected at this
stage (Phase 1–2 need to exist first) but flagging it so nobody's surprised there's no APK to
try.

---

## 4. Immediate next action

**If you are the next agent picking this up: do this first, in order.**

1. Read §0–§1 of this file (you're doing that now).
2. **Still not done:** set up a real Android project shell (root `settings.gradle.kts`
   including both `:data` and `:domain`, `app` module skeleton — doesn't need real app code
   yet) so these modules can actually be compiled and tested end-to-end, not just read.
   **This sandbox cannot do this** — no Gradle, no Android SDK, and the network allowlist
   blocks both `dl.google.com` (Android Gradle Plugin, AndroidX) and `repo1.maven.org`
   (everything else), so a real `./gradlew :data:test :domain:test` run has never happened
   and can't happen from in here. **This needs a real machine with Android Studio / a full
   Gradle+SDK setup — the next session or a human needs to actually run it.**
   What *was* done in lieu of that (this session): every method/field call in
   `RecordViolationUseCase.kt` and its test was manually cross-checked, one by one, against
   the literal signatures already in `CoreDaos.kt`, `LedgerDao.kt`, and the entity files —
   not just "read and assumed correct." This catches wrong-arg-count/wrong-type/typo'd-name
   errors, which is most of what a compiler would catch for glue code like this. It does
   **not** catch: Room's own SQL validation of `@Query` strings against the schema (Room
   verifies these at compile time via annotation processing — `forRootCauseCluster()`'s and
   `activeEntriesForViolations()`'s SQL has never been through that), Robolectric actually
   resolving/running (`RecordViolationUseCaseTest`'s imports are correct paths but the test
   has never executed), or AGP/KSP wiring problems in the new `domain/build.gradle.kts`
   (untested — first time this project has had two Android library modules depending on
   each other). Treat all three as real open risk, not paranoia.
3. §5.1 is now resolved (citation-backed, this session). Two things still need your
   sign-off before more Phase 1 work builds on them: §5.5 (shared-cause guard has no rolling
   window — could silently suppress penalties long-term) and §5.7 (schema bumped to v2 with
   no migration strategy — `DisciplineOsDatabase.build()` will crash on any existing v1 DB
   file until this is decided).
4. `RecordViolationUseCase` and `ResolveDisputeUseCase` are both written. Next up in Phase 1
   is `TierTransitionUseCase` (Iron calibration gate enforcement, Standard/Crisis Downgrade,
   Recovery Mode activation) or `ApplyReputationDecayUseCase`.

---

## 5. Decision log

Every judgment call made where the spec was ambiguous, silent, or where a plausible default
had to be chosen. **Log entries here at the time the call is made, not retroactively** — the
whole point is capturing the reasoning while it's still available, per this project's own
stated distaste for unearned/unexplained precision (Data Model doc §3.1's Discipline Score
removal is the model to follow).

### 5.1 — RESOLVED (this session, with citation) — Violation dispute-status semantics

**Where:** `data/.../dao/CoreDaos.kt`, `ViolationDao.countingViolationsSince()`

**The conflict:** Data Model doc §3.2 says Reliability Index excludes violations
"`dispute_status = upheld` in the *user's* favor." But the `DisputeStatus` enum (§2.3, §6)
defines `UPHELD` = the original violation stands (NOT the user's favor) and `OVERTURNED` =
the dispute succeeded (IS the user's favor). The prose and the enum contradict each other.

**Resolution:** checked the PRD directly (§26.4, "False-Positive Dispute Path") instead of
inferring from the Data Model doc alone. The PRD states explicitly that a flagged violation
resolves to either upheld — the original consequence applies retroactively as if never
paused — or overturned — the violation and its consequences are struck from the record
entirely. This unambiguously matches the enum semantics (`UPHELD` = violation stands,
`OVERTURNED` = struck from record) and directly contradicts the Data Model doc §3.2 prose,
which used "upheld" loosely/incorrectly as a synonym for "resolved in the user's favor."

**What's implemented:** unchanged from before — `countingViolationsSince()` excludes
`OVERTURNED` violations. This was already correct; it's now confirmed against the PRD's own
mechanics section rather than resting on "the enum is more precisely specified than the
prose," which was a reasonable inference but not a citation.

**Follow-up, not blocking:** Data Model doc §3.2's prose should get corrected to say
"overturned" if anyone revises that doc — flagged here rather than edited directly, since
changing spec docs isn't this codebase's call to make unilaterally.

### 5.5 — OPEN, needs your sign-off — Shared-cause guard has no rolling-window cutoff

**Where:** `domain/.../usecase/RecordViolationUseCase.clusterAlreadyHasActiveEntry()`

**The gap:** Data Model §3.5 specifies the shared-cause guard should check for existing
same-cluster consequences "within a rolling window," but no window length is given anywhere
in the spec (unlike Reliability Index's explicit 14-day default, or the Debt Ceiling's
explicit 14-day default).

**What's implemented now:** no window at all — the guard checks for *any* active (non-reversed)
same-cluster entry, regardless of age. This means if a `rootCauseClusterId` is reused far
apart in time (the spec doesn't say clusters expire), a second violation in that cluster will
never get penalized, even months later.

**Why implemented this way rather than picking a plausible window:** picking a number (e.g.
"same day," "7 days") would be exactly the invented-precision failure mode this project's own
docs argue against (Data Model §3.1). An unconditional guard is the strictly safer of the two
unvalidated options in one direction — it can only under-penalize, never double-penalize,
which matches the spec's stated intent ("must not... without a deduplication check") even if
it's more conservative than the spec's "rolling window" language implies.

**Why this matters enough to block on:** if `rootCauseClusterId` values get reused or
long-lived in whatever code eventually creates Violations (not yet written), this could
silently suppress legitimate penalties indefinitely. Needs either (a) a real window length
once Phase 5 pilot data exists, or (b) a decision that cluster IDs are always short-lived
enough that this doesn't matter, made explicitly rather than assumed.

**Action needed:** confirm the no-window behavior is acceptable for now, or supply a window
value to encode as `[HYPOTHESIS]` (same posture as the Reputation decay rate).

### 5.6 — RESOLVED — Crisis-exit missions hard-fail in `RecordViolationUseCase`, not silently skipped

**Where:** `domain/.../usecase/RecordViolationUseCase.execute()`

**Call made:** if `execute()` is called with a Violation whose Mission has status
`ABORTED_CRISIS_EXIT`, it throws `IllegalArgumentException` rather than quietly recording the
Violation with no ledger writes.

**Why:** Data Model §5 / PRD §12.4.4 treat crisis exit as a *distinct path* from a normal
Violation — the correct handling is that crisis exit never produces a Violation that reaches
this use-case at all, not that this use-case receives one and decides not to penalize it. A
future caller wiring crisis exit into `RecordViolationUseCase` (instead of whatever
crisis-exit-specific logic Phase 1 still needs to write) would be a real bug worth surfacing
immediately, not something to paper over by making this use-case quietly correct for an input
it should never receive.

**Revisit when:** the actual crisis-exit handling path is built (Phase 1, not yet started) —
confirm it never calls `RecordViolationUseCase` in the first place, at which point this
`require` becomes a pure defensive check rather than a load-bearing one.

### 5.7 — RESOLVED, but with an unaddressed follow-on — `LedgerEntry.pausedAt` added; no migration written

**Where:** `data/.../ledger/LedgerEntry.kt`, `LedgerDao.kt`, `DisciplineOsDatabase.kt`
(schema bumped v1 → v2)

**The gap found:** PRD §26.4 says filing a dispute flag "immediately pauses that specific
violation's contribution" to Debt/Reputation. Data Model §6's original schema only had
`reversedAt`/`reversedReason` — a two-state model (active / permanently reversed) with no way
to express "temporarily not counting, may resume." Since a Violation normally already has
Phase-1-written ledger entries by the time a dispute can be filed against it (you can't
dispute a Violation that doesn't exist yet), "pauses... contribution" has to mean something
about entries that already exist, not just "don't write new ones."

**Call made:** added `LedgerEntry.pausedAt: Instant?`, distinct from `reversedAt`. Active =
both null. Paused = `pausedAt` set, `reversedAt` null (cleared back to null on UPHELD).
Reversed = `reversedAt` set (permanent, regardless of prior pause state). `currentValue()`
and both `activeEntriesFor...` queries now exclude paused entries. Considered and rejected
reusing `reversedAt` for pausing (see `LedgerEntry`'s kdoc for the full reasoning) — it would
conflate two different facts under one timestamp and undermine the ledger's audit-trail
guarantee, which this project's docs treat as load-bearing, not incidental.

**What's NOT done — flagging explicitly rather than silently leaving it:** bumping
`@Database(version = 2)` with an existing `version = 1` schema and no
`Migration`/`fallbackToDestructiveMigration()` set on the builder means `DisciplineOsDatabase
.build()` will crash (`IllegalStateException`) the moment it's opened against a v1 database
file. Did not add `fallbackToDestructiveMigration()` unilaterally — whether losing all local
Debt/Reputation/Violation history on a schema bump is acceptable is a product call (even at
personal/friends scale, "your discipline history randomly resets" is a real thing to decide
on purpose), not a default an agent should pick silently. Given the app has never shipped, a
destructive fallback is probably fine, but say so explicitly rather than assume it.

**Action needed:** decide the pre-launch migration policy (likely: destructive fallback is
fine until real users exist, write real `Migration`s once it doesn't) and wire whichever into
`DisciplineOsDatabase.build()`.

### 5.2 — RESOLVED — Export/deletion request tracking dropped from `User` entity

**Where:** `data/.../entity/User.kt`

**Call made:** did not implement `data_export_requests: [ExportRequest]` or
`deletion_requests: [DeletionRequest]` from the original §2.1 sketch. Deletion is instead a
direct DB-file operation (`UnsupervisedDatabase.deleteEntirely()`); no export mechanism
exists at all yet.

**Why:** Architecture doc §3.2 explicitly rescoped this for personal/friends distribution —
"if a friend wants their data or wants out, that's a conversation, not a compliance
obligation." Building a request-tracking entity for a compliance workflow that doc says
doesn't apply at this scale would be exactly the kind of unearned complexity the specs
otherwise avoid.

**Revisit when:** Architecture doc §3.2 says to revisit this section first if distribution
ever moves toward public/store — same trigger applies here.

### 5.3 — RESOLVED — Two databases split into two files, not one

**Where:** `data/.../db/DisciplineOsDatabase.kt`, `db/UnsupervisedDatabase.kt`

**Call made:** originally drafted both `@Database` classes in one file for convenience; split
them after realizing the file would import both `LedgerDao` and `UnsupervisedSignalDao`,
which is exactly the pattern `ArchitectureBoundaryTest` exists to catch — even though this
particular case is architecturally fine (a DB-wiring file is a legitimate place to reference
both, same as a future DI module will need to). Splitting the files was cheaper than adding
an exception rule to the boundary test, and keeps the test's logic simple enough to still be
auditable by reading it (Data Model doc §7's actual stated goal).

**Pattern to follow going forward:** if a future file needs to legitimately reference both
"sides" of the measurement/enforcement boundary (this will come up again — a top-level DI
/ Hilt module wiring the app together is the obvious next case), prefer isolating that
reference in its own small file rather than adding exceptions to
`ArchitectureBoundaryTest`. The test staying simple is more valuable than it being perfectly
precise.

### 5.4 — RESOLVED — `minSdk = 26` in `data/build.gradle.kts`

**Where:** `data/build.gradle.kts`

**Call made:** set `minSdk = 26` (Android 8.0), not stated anywhere in the specs.

**Why:** entities use `java.time.Instant` directly; API 26 is where that's natively
available without desugaring config. This is a real constraint the specs didn't anticipate
because they don't specify a language/API level at all.

**Revisit when:** if you need to support pre-8.0 devices (unlikely for a personal/friends
build in 2026, but flagging since it's an unstated assumption), switch to desugaring or a
different timestamp representation — don't just lower minSdk and let Instant silently break.

---

## 6. Conventions for whoever works on this next

- **Tag every unresolved number `[HYPOTHESIS]`** in code comments, matching the spec docs'
  own convention. Never let a placeholder look like a decided value.
- **Cite the spec section** in a kdoc comment wherever code implements something specific
  enough to trace back (see any file in `data/` for the pattern). This is what let this
  handoff doc be written accurately — don't break the chain for the next phase.
- **This file is a living document, not a one-time artifact.** Update §3's snapshot and
  add a §5 entry any time you make a call the specs didn't make for you. If you finish a
  phase, check its exit criteria and update §2/§4's "immediate next action."
- **When a spec doc's own Open Questions (PRD §42) get resolved by real data (Phase 5),**
  update both the spec doc itself (bump its version per its own revision-note convention)
  and this roadmap — don't let the roadmap and the spec drift apart on what's still open.
