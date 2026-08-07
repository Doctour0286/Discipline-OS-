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
- [x] `ArchitectureBoundaryTest` exists and passes against current tree (verified by import-scan, see `data/src/test/.../ArchitectureBoundaryTest.kt`) — **confirmed by real `./gradlew` run, 2026-08-07, see Phase 0.5**
- [x] Pure formula unit tests exist for Reliability Index, Debt Ceiling, clamping, quartile markers, Iron calibration gate
- [x] ~~**NOT YET DONE:** actually run `./gradlew test` in a real Android environment~~ — **DONE 2026-08-07.** See Phase 0.5. `:data:testDebugUnitTest` passes on real CI (GitHub Actions, JDK 17 + Android SDK). This was the single highest-priority unverified item in the project; it no longer is.

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
      ~~**Written but NOT YET run against a real compiler/Gradle**~~ — **compiled and tested
      for real, 2026-08-07 (Phase 0.5).** First real run found one genuine error — a
      cross-module smart-cast failure, not caught by manual review — fixed same session, see
      §5.8. `./gradlew :domain:testDebugUnitTest` now passes.
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
      Test written in `ResolveDisputeUseCaseTest`, **executed for real on CI, 2026-08-07,
      passes** (previously "written, hand-verified against real signatures, never executed,"
      same status as everything else that session — now confirmed).
- [x] Shared-cause guard (§27.2) has a real implementation, not just a schema column —
      needs a test proving two penalties from the same `rootCauseClusterId` don't double-apply
      — implemented in `RecordViolationUseCase.clusterAlreadyHasActiveEntry()`, backed by new
      `ViolationDao.forRootCauseCluster()` query. Test written in
      `RecordViolationUseCaseTest` (Robolectric + in-memory Room) — **executed for real on CI,
      2026-08-07, passes.** See §5.5/§5.6 below for the two judgment calls made here (both
      still open/tracked, unaffected by the compile-verification work).
- [x] Iron calibration gate is enforced at the point of tier activation, not just computable
      as a pure function someone has to remember to call
      — `TierTransitionUseCase.activateIron()` is now the single call site for
      `ironCalibrationSatisfied()` (Metrics.kt); a `check()` failure hard-blocks activation
      with no exception path, matching PRD §12.6. Tests in `TierTransitionUseCaseTest`
      cover both the pass and fail side, plus that a failed attempt leaves tier unmoved
      (transactional rollback, not partial application). **Written, manually cross-checked
      against real DAO/entity signatures the same way Phase 1's first session verified
      `RecordViolationUseCase` before CI existed — not yet run on real CI, see item 2 below.**
- [x] Crisis exit (`ABORTED_CRISIS_EXIT`) provably does not write to Ledger — the asterisked
      gap from the previous session ("it doesn't yet prove nothing *else* in the app could
      route one there instead") is now closed: `TierTransitionUseCase.ironCrisisExit()` is
      that "whatever handles crisis exit directly" code, and it's the thing that actually
      sets `Mission.status = ABORTED_CRISIS_EXIT` before `RecordViolationUseCase`'s existing
      guard can ever see it. `TierTransitionUseCaseTest` adds an end-to-end test
      (`a mission marked aborted crisis exit by this use-case cannot be double-charged by
      RecordViolationUseCase`) that calls `ironCrisisExit()` and then feeds the same Mission
      into a real `RecordViolationUseCase`, asserting the latter still throws. No asterisk
      needed this time — the loop is closed, not just individually plausible.
- [x] Reputation decay rate constant is still `[HYPOTHESIS]`-tagged and easily swappable —
      `ApplyReputationDecayUseCase` now exists, depends on a new `ReputationDecayPolicy`
      interface (mirrors `ConsequencePolicy`'s reasoning exactly — see that file's kdoc),
      backed only by `HypothesisReputationDecayPolicy`, loudly labeled as a placeholder.
      **Two judgment calls made here — logged in §5.9 and §5.10 below, need your sign-off**,
      plus **one genuine spec gap found and deliberately left unresolved rather than
      guessed** — `demotion_triggered`'s `tier_floor`/`N` values don't exist anywhere in the
      PRD or Data Model doc (unlike the decay rate, which is at least flagged
      `[HYPOTHESIS]` — this is missing entirely, unflagged). `ApplyReputationDecayUseCase`
      computes and writes the running Reputation value; rank-band demotion logic is not
      implemented and is called out explicitly in that class's kdoc rather than silently
      absent.

**Why this phase before enforcement/UI:** the enforcement loop (Phase 2) and every UI screen
(Phase 3) will call into this layer. Building them against raw DAOs instead would mean the
transactional/guard logic gets duplicated or skipped at each call site — exactly the kind of
drift the specs' own CI-check instinct (§7) exists to prevent elsewhere.

---

### Phase 0.5 — Real Build Verification (CI)  ✅ **DONE** (2026-08-07)

**Delivers:** the Gradle project shell that item 2 in §4 had been flagging as missing since
Phase 0 — root `settings.gradle.kts` (`:app`, `:data`, `:domain`), root `build.gradle.kts`
(shared AGP/Kotlin/KSP/Room plugin versions), a minimal `:app` module skeleton (no real
app code — exists only so AGP has a full application variant to build, per Architecture doc
§3.2's phasing), the Gradle wrapper, and a GitHub Actions workflow
(`.github/workflows/build-and-test.yml`) that runs `:data:testDebugUnitTest`,
`:domain:testDebugUnitTest`, and `:app:assembleDebug` on every push.

**Why this had to happen before more Phase 1 work:** every exit-criteria checkbox in Phase 0
and Phase 1 above was marked done on the strength of manual cross-checking, not a real
compiler — flagged repeatedly (§4 item 2) as the single highest-priority unverified risk in
the project. This phase closes that gap for real, on GitHub's infrastructure, not a sandbox
that lacks Android SDK/Gradle network access.

**What running it for real actually found:** one genuine compile error, not zero — see §5.8.
This is the expected outcome of finally running a compiler on code that had only ever been
read, and is exactly why "looks correct on careful reading" and "compiles" were kept as
distinct claims throughout Phase 0/1 rather than conflated.

**Exit criteria:**
- [x] `./gradlew :data:testDebugUnitTest` passes on a real Android SDK + JDK 17 environment
- [x] `./gradlew :domain:testDebugUnitTest` passes — `RecordViolationUseCaseTest`,
      `ResolveDisputeUseCaseTest`, `DomainArchitectureBoundaryTest` all ran and passed, not
      just parsed
- [x] `./gradlew :app:assembleDebug` passes — confirms manifest merge, resource linking, and
      the `:data`/`:domain` dependency wiring all resolve, not just each module in isolation
- [x] CI re-runs automatically on every push to `main` (`build-and-test.yml`, `on: push`)

**Location:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/`,
`gradle/wrapper/`, `.github/workflows/build-and-test.yml`.

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

**Last updated:** 2026-08-07 (this session)
**Current phase:** Phase 0 and Phase 0.5 complete and CI-verified. Phase 1 is now
functionally complete — all four exit-criterion use-cases exist
(`RecordViolationUseCase`, `ResolveDisputeUseCase` from the prior session;
`TierTransitionUseCase` and `ApplyReputationDecayUseCase` written this session). Schema
bumped to v3 (`TierEvent` table; `User.debtAccrualPausedUntil` /
`User.tribunalDeferredUntil`) with an explicit migration-policy decision made (destructive
fallback, pre-launch only — see §5.7's update below) rather than left open a second time.

**Not yet done, flagging clearly rather than overclaiming:** this session's code has been
manually cross-checked field-by-field and call-by-call against the real entity/DAO
signatures already in the tree (same method the first Phase 1 session used before CI
existed — see §4 item 2's original text) and the two new SQL queries
(`missedDaysSince`/`completedMissionsSince`) were hand-simulated against real SQLite outside
Room to check their semantics, not just their syntax. **None of this has been run through
`./gradlew :domain:testDebugUnitTest` on a real environment yet** — that requires the same
GitHub Actions run Phase 0.5 used, which this sandbox still cannot do (no Gradle/Android SDK
network access here — see §4 item 2, unchanged). Push and let CI confirm before treating any
of this session's checkboxes as fully closed, not just carefully reasoned-through.

```
Phase 0 — Data Layer            ████████████████████░  ~95% (code done, verified on real CI)
Phase 1 — Domain/Use-Cases      ████████████████████░  ~90% (4 of 4 use-cases written; TierEvent
                                                        schema + TierDao added; not yet run on
                                                        real CI — see §4 item 2 below)
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
2. **Push this session's work and let real CI run.** The Gradle project shell, GitHub
   Actions workflow, and `:app` skeleton this item used to ask for are now done (Phase 0.5,
   prior session) — what's still true is that **this sandbox itself** has no Gradle/Android
   SDK/network access, so `TierTransitionUseCase`, `ApplyReputationDecayUseCase`, their
   tests, the `TierEvent`/`TierDao` additions, and the `DisciplineOsDatabase` v3 schema bump
   have never been through a real compiler — same unresolved risk category as before, now
   scoped to less code. What *was* done in lieu of that this session, matching the standing
   convention: every new method/field reference was manually cross-checked against the real
   signatures already in the tree (see the field-by-field greps logged informally during
   this session — not reproduced here, but every DAO call in `TierTransitionUseCase.kt` and
   `ApplyReputationDecayUseCase.kt` was checked this way before being treated as correct),
   and the two new hand-written `@Query` SQL strings (`missedDaysSince`,
   `completedMissionsSince`) were additionally simulated against real SQLite (Python's
   `sqlite3`, outside Room/Robolectric entirely) with constructed missed/completed-day
   scenarios to check *semantics*, not just that they parse — Room's own compile-time query
   validation still hasn't touched them for real. Treat this as reduced risk relative to
   pure manual review, not eliminated risk.
3. Two items from the previous round are now resolved this session rather than carried
   forward again: **§5.5** is still open (unchanged, still needs a real rolling-window
   decision once pilot data exists) but **§5.7's migration-policy question is now decided**
   — `fallbackToDestructiveMigration()` wired into `DisciplineOsDatabase.build()`, with the
   reasoning inline in that file's kdoc and restated in §5.11 below. Two *new* judgment
   calls from this session need your sign-off: §5.9 (decay vs. per-violation Reputation
   write are two distinct formula terms, not one mechanic re-implemented) and §5.10 (the
   crisis-stabilization pause reuses `debtAccrualPausedUntil` to also gate Reputation decay,
   which the PRD doesn't say explicitly either way).
4. All four Phase 1 use-cases now exist. **Phase 1's only remaining open item is the
   `demotion_triggered` gap** (§5.9 below) — `tier_floor` values per rank and the
   consecutive-day count `N` aren't specified anywhere in the PRD or Data Model doc, not
   even as `[HYPOTHESIS]`. This blocks implementing actual rank-band demotion (as opposed to
   just the running Reputation *value*, which `ApplyReputationDecayUseCase` already
   computes) — flag it back to whoever owns the spec docs rather than guessing tier floors
   for the seven §35 ranks.
5. Once CI confirms this session's code (item 2), Phase 1's checklist in §2 above should be
   fully checkable except the `demotion_triggered` gap — at that point Phase 2 (Core
   Enforcement Loop) is unblocked per the phase-ordering rule in §2's intro, and its own
   hard blocker (Accessibility Service Play Console declaration research, Architecture doc
   §1.2) should be picked up first within that phase.

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

### 5.11 — RESOLVED (this session) — Migration policy decided: destructive fallback, pre-launch only

**Where:** `data/.../db/DisciplineOsDatabase.kt`, `build()`

**The gap this closes:** §5.7 below (from the previous session) left the migration policy as
an explicit open action item after bumping the schema to v2 with no `Migration` and no
`fallbackToDestructiveMigration()` — meaning `.build()` would crash outright against an
existing v1 database file. This session bumped the schema again, to v3 (`TierEvent` table;
`User.debtAccrualPausedUntil`/`tribunalDeferredUntil`), which made resolving this
unavoidable rather than deferrable a second time.

**Call made:** `fallbackToDestructiveMigration()` wired into `.build()`. Acceptable
specifically because this app has never shipped to a real device — there is no existing
user's Debt/Reputation/Reputation-rank history to protect yet. Writing real `Migration`
objects now, against a schema that has changed twice in two sessions and will likely change
again before Phase 2/3 are done, would be exactly the kind of premature precision the specs
argue against elsewhere (Data Model doc §3.1's Discipline Score reasoning generalizes here:
don't build the durable version of something before there's a real installed base to make it
worth getting right).

**Revisit when:** before the first real pilot install (ROADMAP.md Phase 5, per §5.7's
original framing, unchanged) — destructive fallback stops being acceptable the moment a
schema bump could actually erase a real person's history, not just a developer's test data.

### 5.10 — OPEN, needs your sign-off — Crisis-stabilization pause reused for Reputation decay, not just Debt

**Where:** `domain/.../usecase/ApplyReputationDecayUseCase.execute()`

**The gap:** PRD §12.4.3 states Crisis Downgrade "pauses debt accrual" and frames the whole
event as "a stabilization event, not a punishment event"; §12.4.4 adds that the Iron Crisis
Exit specifically "carries no score penalty." None of this explicitly says whether
Reputation *decay* (the scheduled `decay_per_missed_day` term this use-case implements, as
opposed to a Violation's immediate per-event Reputation penalty, which is moot here since a
crisis-exit Mission never reaches `RecordViolationUseCase` at all) should also pause during
the same window.

**Call made:** treated `User.debtAccrualPausedUntil` as gating Reputation decay too, rather
than adding a second field or leaving decay unaffected. Reasoning: decay is itself a
Reputation-penalty mechanism, and continuing to silently apply it during a window the PRD
explicitly frames as protective sits uneasily against "stabilization event, not a punishment
event" — letting decay run during exactly the window meant to protect the user reads like
the punishment the PRD says this isn't. Reused the existing field rather than adding
`reputationDecayPausedUntil` as a separate column, since both `TierTransitionUseCase` paths
that would set either field (Crisis Downgrade, Iron Crisis Exit) already set
`debtAccrualPausedUntil` to the identical 24-hour instant, and the PRD gives no indication
the two are meant to diverge — a second field would be schema complexity with no behavioral
difference to justify it, at least until a scenario is identified where they should differ.

**Recovery credit is NOT paused by the same window** — crediting a completed Mission during
stabilization is never adverse to the user, so none of the "punishment event" concern above
applies to it, and pausing it would arguably undercut the stabilization framing rather than
support it (a user completing a Mission during a stabilization window is doing exactly what
recovery should reward).

**Why this needs sign-off rather than standing as settled:** this is an inference from
"stabilization event, not a punishment event" framing language, not a literal spec
instruction — a defensible reading, but a reading, and the field-reuse decision means
un-reusing it later (if the two should diverge) is a real schema change, not a one-line
policy swap.

### 5.9 — OPEN, flagged rather than guessed — `demotion_triggered`'s `tier_floor`/`N` are missing from the spec, not just unvalidated

**Where:** `domain/.../usecase/ApplyReputationDecayUseCase` (class-level kdoc has the full
version of this note; repeated here per this file's own "log at the time the call is made"
convention)

**The gap:** Data Model doc §3.5 states the demotion-trigger formula as `demotion_triggered
when Reputation < tier_floor for tier N consecutive days` — but neither `tier_floor` (a
value per rank, for the seven ranks in PRD §35: Undisciplined → Inconsistent → Reliable →
Disciplined → Relentless → Elite → Iron Will) nor `N` (the consecutive-day count) has a
value anywhere in the PRD or Data Model doc. This is a different category of gap from the
decay rate itself (`decay_per_missed_day`), which is at least explicitly flagged
`[HYPOTHESIS]` in §42 — `tier_floor`/`N` aren't flagged as unresolved anywhere; they're just
absent, which reads as an oversight in the spec rather than a deliberately-deferred decision.

**Call made:** did not implement `demotion_triggered` or any rank-band mapping.
`ApplyReputationDecayUseCase` computes and writes the running Reputation *value* via the
Ledger (the `decay_per_missed_day`/`recovery_per_completed_mission` terms, which *are*
specified, if only as placeholders) and stops there — it does not attempt to map that value
onto the seven §35 ranks or decide when a demotion event should fire. Per ROADMAP.md's own
standing instruction ("if you're about to invent a constant... stop"), seven tier-floor
values and a day-count would be eight invented numbers with zero grounding, which is a
larger and more consequential version of exactly the failure mode Data Model §3.1's cut
Discipline Score composite already established as unacceptable for this project.

**Why this is a Phase 1 exit-criterion gap worth naming explicitly rather than closing
Phase 1 as if it weren't there:** without `demotion_triggered`, nothing in the app can
actually move a user's *rank* (Undisciplined/Disciplined/etc. — the user-facing identity the
Reputation number is supposed to represent per §35) even though the underlying Ledger value
driving it is now fully wired. This is a real functional gap, not a cosmetic one — flagging
it here is what keeps it from being silently treated as "done" because the adjacent
machinery compiles and passes tests.

**Revisit when:** whoever owns the spec docs supplies real `tier_floor` values and `N` —
this is arguably better resolved by an explicit spec-doc revision (Data Model doc gains a
real §3.5 table, version-bumped per that doc's own convention) than by an engineering-side
guess, given how directly it defines what "Disciplined" vs. "Inconsistent" *means* to a
user — that's a product-design decision wearing a formula's clothing, not a pure engineering
gap like the shared-cause guard's window (§5.5).

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

**Action needed:** ~~decide the pre-launch migration policy (likely: destructive fallback is
fine until real users exist, write real `Migration`s once it doesn't) and wire whichever into
`DisciplineOsDatabase.build()`.~~ **Done — see §5.11.**

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