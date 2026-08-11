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
      (transactional rollback, not partial application). **Executed for real on CI,
      2026-08-07 (Phase 0.5) — passes.** This test file is also where the flaky
      `Instant`-precision assertion in the crisis-downgrade test was caught and fixed
      (§5.12); `activateIron`'s own tests were unaffected by that bug and passed on the
      first run.
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

**What running it for real actually found: two genuine bugs, not zero, across two separate
runs.** This is the expected outcome of finally running a compiler and a real test runner on
code that had only ever been read, and is exactly why "looks correct on careful reading" and
"passes on a real toolchain" were kept as distinct claims throughout Phase 0/1 rather than
conflated. Neither bug was visible to manual cross-checking; both are exactly the category of
thing §4 item 2 predicted this sandbox's lack of a real compiler/test runner would eventually
miss.

- **Run 1 — real compile error** (see §5.8): a cross-module Kotlin smart-cast failure in
  `RecordViolationUseCase.kt`. `Violation.rootCauseClusterId` is declared in `:data`;
  `RecordViolationUseCase` lives in `:domain`, a separate compiled module. Kotlin does not
  extend smart-cast guarantees for a nullable property across a module boundary (it can't
  verify the other module's getter is stable), so `violation.rootCauseClusterId != null &&
  clusterAlreadyHasActiveEntry(violation.rootCauseClusterId, ...)` failed to compile even
  though the logic is correct. Fixed by binding to a local `val clusterId` first. Common,
  well-known multi-module Kotlin gotcha — not a design flaw.
- **Run 2 — real (flaky) test failure** (see §5.12): `TierTransitionUseCaseTest`'s crisis
  downgrade test asserted exact `Instant` equality between an in-memory `Instant.now()`
  (JVM nanosecond precision) and the same value read back from Room, which persists
  `Instant` as epoch **milliseconds** (`Converters.fromInstant`/`toInstant`) and silently
  drops any sub-millisecond component on write. The two values were therefore only equal
  when `now()` happened to land on an exact millisecond boundary — a flaky assertion, not
  a bug in the code under test. Fixed by truncating `now` to millisecond precision in the
  test, at the point of capture, so both sides of the comparison agree deterministically.
  Every other `Instant.now()` + DB-read comparison in both new test files was scanned for
  the same risk and confirmed clear (they compare enums/doubles/nulls, none of which lose
  precision on the millis round-trip).

**Both fixes verified on a third CI run: full green — `:data`, `:domain`, and
`:app:assembleDebug` all passed in one job**, not just the previously-failing piece in
isolation. This is the first fully-green build this project has had.

**Exit criteria:**
- [x] `./gradlew :data:testDebugUnitTest` passes on a real Android SDK + JDK 17 environment
- [x] `./gradlew :domain:testDebugUnitTest` passes — `RecordViolationUseCaseTest`,
      `ResolveDisputeUseCaseTest`, `DomainArchitectureBoundaryTest`, `TierTransitionUseCaseTest`,
      `ApplyReputationDecayUseCaseTest` all ran and passed, not just parsed (29/29 domain tests
      green as of the third run)
- [x] `./gradlew :app:assembleDebug` passes — confirms manifest merge, resource linking, and
      the `:data`/`:domain` dependency wiring all resolve, not just each module in isolation
- [x] CI re-runs automatically on every push to `main` (`build-and-test.yml`, `on: push`)
- [x] Two real bugs found by the real toolchain were fixed and re-verified green, not just
      found and left open

**Location:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/`,
`gradle/wrapper/`, `.github/workflows/build-and-test.yml`, `scripts/deploy_update.sh` (the
zip-to-repo deploy script — see "Deploy script" below; also expected to live at
`~/scripts/deploy_update.sh` on-device, outside the repo, since it needs to run *before* the
repo's working tree is in a known state).

**Known non-blocking noise:** CI currently emits two deprecation warnings (Node.js 20 in
`actions/*`, `setup-java@v4`) from GitHub's own Actions runner, unrelated to this project's
code. Worth a five-minute bump to the actions' newer major versions at some point; does not
affect build correctness and isn't blocking anything.

**Push workflow actually used to get code from the authoring environment onto the real
repo** (worth recording since this project is authored in a sandbox with no git credentials
and no direct device access — the round trip is manual by necessity, not a preference):

1. Code is written/edited in an AI sandbox with no GitHub write access and no persistent
   storage between sessions. Every session's changes are packaged as a zip of the **entire**
   repo (not a diff) and handed off as a downloadable file.
2. That zip lands in the phone's `Downloads` folder via the browser/chat app.
3. On the phone, **Termux** holds the actual working git clone (`~/projects/disciplineos`,
   with the real `.git` history/remote). Termux's home directory is sandboxed from normal
   Android storage, so `termux-setup-storage` is run once to expose `~/storage/downloads/`
   into Termux's filesystem.
4. **Run `~/scripts/deploy_update.sh <path-to-zip>` instead of the manual mv/cp sequence
   previously documented here** — see "Deploy script" subsection immediately below for why
   this replaced the manual version and what it actually does.
5. The script stops after staging the new tree and prints `git status` for review — it does
   not commit or push on its own. Treat that printed diff as the real verification step: it
   should show only the files a given session claims to have touched. Anything unexpected (a
   file the session didn't mention, or an expected change missing) is a signal to stop and
   check before committing, not to commit through.
6. Commit and push as normal:
   ```bash
   git add -A
   git commit -m "<describes what actually changed, e.g. 'Fix flaky Instant precision assertion'>"
   git push
   ```
7. Once confirmed working (diff reviewed, push succeeded, CI later confirms green), delete
   the backup the script made: `rm -rf ~/projects/disciplineos-old-backup`.
8. Check the Actions tab a few minutes later for the real result — a green check is the
   only claim in this whole loop that isn't provisional.

**Deploy script (`~/scripts/deploy_update.sh`) — why the manual steps above got replaced:**

The original version of this section (steps 4 above, prior to this entry) documented a
manual `mv`/`cp` sequence: rename the current repo to a backup folder, unzip, `mv` the
unzipped folder to `disciplineos`, `cp -r` the backup's `.git` into it. In practice this
failed **three separate times in one real session** before the Phase 2 CI-artifact push
landed successfully, all from the same underlying problem: the sequence has no built-in
verification, so a small mistake compounds silently instead of stopping the person running
it. Concretely, across that session:

- The zip's internal folder is named `updated`, not the zip's own filename
  (`disciplineos-validated-phase2-updated.zip`) — the manual `mv <unzipped-folder-name>
  disciplineos` step assumes those match. They don't, and nothing about the zip format makes
  that obvious ahead of time.
- `mv`/`cp -r` behave differently depending on whether the destination already exists (a
  clean rename vs. "move source *inside* the existing destination") — a first failed attempt
  left debris that changed what the *next* command silently did, compounding rather than
  failing loudly.
- One of those silent failures resulted in `.git`'s contents landing bare in the project root
  (instead of inside a `.git` subfolder), which made `git status` report every real file as
  "deleted" — genuinely alarming output, but not actual data loss, since the append-only
  `disciplineos-old-backup/.git` was never touched by any of it.
- Recovering required manually re-verifying, at every step, exactly the kind of thing a
  script should check automatically: does the zip actually contain what I think it does, does
  the destination already exist, does the result look structurally complete before I delete
  anything irreversible.

The script fixes this by making each step check its own precondition and refuse to proceed
silently:
- Finds the real project root inside the extracted zip by locating the directory that
  actually contains `build.gradle.kts` + `settings.gradle.kts` together, rather than assuming
  a folder name.
- Extracts into an isolated, disposable temp folder (`~/projects/.deploy-tmp-extract`) —
  never directly into `~/projects` — so a bad extraction can't collide with anything live.
- Refuses to proceed if the new tree is missing `app/`, `data/`, `domain/`, or `ROADMAP.md`,
  instead of silently swapping in a half-broken copy.
- Restores the `gradlew` executable bit automatically — the zip round-trip through a browser
  download and Termux's storage bridge was separately observed, in the same session, to
  silently drop it (`old mode 100755, new mode 100644` in the resulting diff). Undetected,
  this would have made `./gradlew` fail with a permissions error despite identical file
  content.
- Only deletes the live `disciplineos/` directory after the new tree has passed all of the
  above checks, and never touches `disciplineos-old-backup/` if one already exists from an
  earlier run.
- Ends by printing `git status` for review — it deliberately does not run `git add`/`commit`/
  `push` itself, keeping that as a manual, reviewed step per point 5 above.

`set -euo pipefail` at the top means any unexpected error anywhere in the script stops it
immediately rather than continuing past a failure — the exact opposite of what the manual
sequence did when `mv`/`rmdir` failed quietly in the middle of a chain of commands.

**Setup, one-time:**
```bash
mkdir -p ~/scripts
# save deploy_update.sh into ~/scripts/ (paste via `micro ~/scripts/deploy_update.sh`, or
# download it like any other session artifact and `mv` it into place)
chmod +x ~/scripts/deploy_update.sh
```

**Usage, every session after that:**
```bash
bash ~/scripts/deploy_update.sh ~/storage/downloads/<zip-name>.zip
```

**Auth note, logged because it already caused two dead-ends:** a classic PAT embedded
directly in the git remote URL (`https://<user>:<token>@github.com/...`) stops working the
moment that token is revoked/regenerated, and the remote URL keeps the dead token cached
until explicitly reset with `git remote set-url`. Regenerating a token requires re-running
`git remote set-url origin https://<user>:<NEW_TOKEN>@github.com/...`. `git config --global
credential.helper 'cache --timeout=31536000'` avoids re-embedding a token in the URL on
future rotations, at the cost of caching a credential in plaintext-adjacent form for up to a
year — a reasonable trade for a personal project, worth reconsidering if this repo ever
stops being solo/private.

---

### Phase 2 — Core Enforcement Loop (Android-specific)  🟡 **IN PROGRESS** (2026-08-07)

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
- [x] Accessibility Service detects foreground app changes and can intercept a blocklisted
      app during an active Mission — `MissionAccessibilityService` written; foreground
      detection + blocklist matching against `MissionDao.activeMissionFor()` implemented.
      Now has a manifest `<service>` declaration and `res/xml/` config too (this session's
      continuation) — see below for what's still unverified.
- [x] Interception screen shows Warden Voice (Warden/Iron tiers) or informational content
      (Recruit/Operator), per tier-dependent content in Onboarding doc §3.1 —
      `MissionInterceptionActivity` + `InterceptionPolicy` implement the tier branching, now
      with a real `res/layout/activity_mission_interception.xml` and `res/values/strings.xml`
      (this session's continuation) — the screen has an actual layout and copy to render.
- [x] Iron-tier crisis exit reachable *from the interception screen itself*, not buried in
      settings (Onboarding doc §3.1, hard requirement) — wired into
      `MissionInterceptionActivity` via `TierTransitionUseCase.ironCrisisExit()`, available
      for the full countdown per §12.4.4.
- [x] AI Voice call has a hard local fallback bank if generation times out (~2s starting
      ceiling, Onboarding doc §3.1) — `WardenVoiceProvider` orchestrates timeout → gate →
      fallback, never throws/blanks; unit-tested for every failure mode (cloud timeout,
      cloud error, null, gate-rejection). Real cloud generator is intentionally a
      `NoOpWardenVoiceGenerator` placeholder (see §5.13) — always defers to fallback bank
      until a real backend exists.
- [x] Fallback bank content passes the same behavior-vs-identity review as generated content
      (Architecture §2.1) — `VoiceLineGate` is the structural pre-display filter mandated by
      §2.2; `FallbackVoiceBankTest` exhaustively runs every bank line through it. This
      session's continuation extended the same review discipline to the interception
      screen's *static* UI copy too (`strings.xml`'s top comment) — Onboarding doc §4 asks
      for that review on every user-facing string, not just Warden Voice content.

**Resource/manifest layer — completed this session's continuation:**
- [x] `res/layout/activity_mission_interception.xml` — full layout matching every view ID
      `MissionInterceptionActivity` references (`voiceLineText`, `countdownText`,
      `returnToMissionButton`, `breakCommitmentButton`, `stabilityControlButton`,
      `breakReasonInput`). Structural only, per Onboarding doc §5's deferral of visual
      design/theming to a follow-on doc.
- [x] `res/values/strings.xml` — every user-facing string the Activity and the Accessibility
      Service's on-device description need, hand-checked against the behavior-vs-identity
      shape (Onboarding §4 / PRD §22.1) before being written, not just copied in.
- [x] `res/xml/mission_accessibility_service_config.xml` — the Android-framework-required
      static Accessibility Service declaration (distinct from the Play Console declaration,
      which stays out of scope — see below). Description string verified against the actual
      service code (`onAccessibilityEvent` reads only `event.packageName`, never window
      content) before being written, so the "does not read screen content" claim in the
      string is checked, not asserted on faith.
- [x] `AndroidManifest.xml` — `<service>` for `MissionAccessibilityService`
      (`BIND_ACCESSIBILITY_SERVICE` + config XML meta-data pointer) and `<activity>` for
      `MissionInterceptionActivity` (`showWhenLocked`/`turnScreenOn`/`excludeFromRecents`/
      `singleTask`) both added. A first draft mistakenly added a `PACKAGE_USAGE_STATS`
      permission for UsageStatsManager — caught before finishing, since that's explicitly
      out of `MissionAccessibilityService`'s own stated scope (its kdoc's "what this class
      deliberately excludes" list) and adding the permission preemptively would be exactly
      the kind of scope creep ROADMAP.md's conventions ask to avoid. Removed.
- [x] `InterceptionControllerTest` — written this session's continuation
      (`app/src/test/java/.../InterceptionControllerTest.kt`), same in-memory-Room-under-
      Robolectric pattern as `:domain`'s use-case tests. Covers `resolveVoiceLine` (Recruit
      null vs. Operator+ generated-then-fallback), `countdownSpec`/`stabilityControl`
      delegation, `returnToMission` (asserted as a genuine no-op via the Violation table),
      `breakCommitment` (Iron's mandatory-reason requirement, both the throwing and
      succeeding paths), and `ironCrisisExit` (Recruit landing + Mission
      `ABORTED_CRISIS_EXIT` + the resulting `RecordViolationUseCase` closed-loop guard).
      `app/build.gradle.kts` updated to add the matching Robolectric/Room test dependencies
      (mirrors `:domain`'s set exactly) plus `testOptions.unitTests.isIncludeAndroidResources`.

**Three real compile bugs found and fixed across this phase, all now CI-confirmed:**

Two are the same root cause, found during the writing pass, before ever reaching CI: the
prior session's `MissionAccessibilityService` and `MissionInterceptionActivity` both called
`lifecycleScope`, but neither class had a base type that actually provides it.
- `MissionAccessibilityService extends AccessibilityService` — plain framework class, not a
  `LifecycleOwner`, and AndroidX has no `LifecycleAccessibilityService` equivalent of
  `LifecycleService` to opt into. **Fix:** replaced with a manually-managed
  `CoroutineScope(SupervisorJob())`, created as a field and cancelled in a new
  `onDestroy()` override — the standard pattern for a `Service`/`AccessibilityService` that
  needs coroutines without being a `LifecycleService`.
- `MissionInterceptionActivity extends Activity` (plain `android.app.Activity`) — same
  problem. **Fix:** switched the base class to `androidx.activity.ComponentActivity`, the
  minimal AndroidX class that provides real `lifecycleScope` support; added
  `androidx.activity:activity-ktx` and `androidx.lifecycle:lifecycle-runtime-ktx` to
  `app/build.gradle.kts` (neither was a direct dependency before — `:app` had no real code
  using them until this phase). Verified every other `Activity` API this class calls
  (`window`, `setContentView`, `findViewById`, `finish()`, `startActivity`, `getString`) is
  inherited from `android.app.Activity`, which `ComponentActivity` extends, so nothing else
  needed to change.

Neither of those two was caught during the session that introduced it — both are exactly the
kind of thing invisible to line-by-line reading and only reliably caught by an actual
compiler, which the authoring sandbox doesn't have for Android/Kotlin (see §4's standing
caution on this). Caught before CI only because writing `InterceptionControllerTest` required
tracing through `InterceptionController`'s actual call sites carefully enough to notice the
pattern repeat across both files.

A third, genuinely only findable by CI: **`:app:compileDebugKotlin` failed on its first real
run** — `DisciplineOsDatabase`'s `RoomDatabase` supertype and `DbPassphraseProvider`'s
`androidx.security.crypto` types (`MasterKey`, `EncryptedSharedPreferences`) both unresolved.
Root cause and fix logged in full at §5.16 — in short: `:data`'s Room dependencies were
`implementation`-scoped (not visible transitively to `:app`, which now touches
`DisciplineOsDatabase` directly for the first time this phase) and `security-crypto` was
never declared anywhere despite `DbPassphraseProvider.kt` needing it since that file was
written. Fixed by changing `:data`'s Room dependencies to `api(...)` and adding
`security-crypto` to `:app`. **Confirmed green on real CI** (`build-and-test` run #6,
`:app:assembleDebug`, 3m3s) — this is the first time any `:app`-module code, including both
`lifecycleScope` fixes above, has actually compiled on a real toolchain rather than just
read correctly.

A fourth, found only once the third's own fix (wiring `:app:testDebugUnitTest` into CI)
actually ran: `kotlin-test` was never declared, so `InterceptionControllerTest` couldn't
compile (`assertFailsWith` unresolved). Root cause and fix logged in full at §5.17. Fixed by
adding `testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")` to
`app/build.gradle.kts`. **Confirmed green on real CI** (`build-and-test` run #8,
`:app:testDebugUnitTest`, 3m8s) — the first time `InterceptionControllerTest` has actually
executed, not just compiled. This run also incidentally re-exercised §5.16's `RoomDatabase`/
`security-crypto` classpath fix under a second, independent CI run — functioning as the
"second confirming run" §5.12's standard asks for, even though re-confirming §5.16 wasn't
run #8's primary purpose.

**Still open before this phase can be marked DONE:**
- [x] ~~None of this phase's code has been through real Gradle/CI yet~~ — **DONE.**
      `:app:assembleDebug` (run #6) and `:app:testDebugUnitTest` (run #8) both green. All
      four compile/dependency bugs above are now confirmed fixed, not just plausibly fixed.
- [x] ~~Per §5.12's own standard, one green run isn't automatically "permanently
      resolved"~~ — **effectively satisfied.** Run #8 independently re-exercised §5.16's
      `RoomDatabase`/`security-crypto` fix under a second CI run without incident, alongside
      confirming §5.17. Not a dedicated, deliberate re-run of §5.16 specifically, but the
      practical bar (does this classpath configuration hold up across more than one real CI
      run) has been met.
- [ ] **Not yet independently verified:** run #8's job-level "Success" was confirmed directly
      (screenshot); the `test-reports` artifact's per-test breakdown — that all 9
      `InterceptionControllerTest` cases specifically passed, not some subset skipped — was
      not separately pulled and checked. Low-risk given a failed test would fail the job, but
      flagged per this doc's own "don't call something checked when it was only inferred"
      standard (see §5.17's own note on this).
- [ ] `androidx.security:security-crypto:1.1.0-alpha06` is an alpha version, used because it
      matched `DbPassphraseProvider.kt`'s existing API usage — not independently re-checked
      against whatever stable `androidx.security` release exists now. Lower priority given
      sideload-only distribution, but worth confirming before any non-sideload distribution.
- [ ] Hard blocker research (Play Console declaration + comparable-apps check, Architecture
      §1.2) remains explicitly skipped, since distribution is sideload-only for now.
      **Revisit if distribution scope ever changes** — don't let this stay silently skipped
      if the app is ever submitted to Play.
- [ ] **The single clearest remaining gap:** no on-device or emulator install/run has
      happened — CI confirms the code compiles, packages, and its own tests pass, not that it
      behaves correctly on a real device. An actual install-and-trigger-an-interception pass
      is still owed before this phase's exit criteria are honestly checkable end-to-end.

---

### Phase 3 — Onboarding & Core UI  🟡 **IN PROGRESS** (2026-08-07)

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

**Last updated:** 2026-08-09 (this session — Core Data Consent, §5.23). Welcome, Goal
Definition, Tier Explanation, Tier Selection, Tier Confirmation, and Mission Profile Setup are
all merged to `main`; Tier Selection/Confirmation are **confirmed done, both on CI and
on-device**. Welcome, Goal Definition, Tier Explanation, Mission Profile Setup, and this
session's Core Data Consent are all **written, not yet CI-confirmed** (no compiler in this
authoring sandbox — verified via static/manual review only; see each screen's own PR/kdoc) —
don't conflate that status with Tier Selection/Confirmation's actual on-device confirmation
when picking this up next.

```
Onboarding sequence, screen by screen (Onboarding doc §1, 8 steps as actually wired):
1. Welcome                    — REAL, written (not yet CI-confirmed)
2. Goal Definition             — REAL, written (not yet CI-confirmed)
3. Tier Explanation            — REAL, written (not yet CI-confirmed)
4. Tier Selection              — REAL, confirmed CI + device (§5.21)
   ↳ Tier Confirmation         — REAL, confirmed CI + device (§5.21) — Warden path only
   ↳ Iron Calibration Gate     — placeholder, unreached (Iron unselectable at onboarding)
5. Mission Profile Setup       — REAL, written (§5.22) — not yet CI-confirmed
6. Core Data Consent           — REAL, written this session (§5.23) — not yet CI-confirmed
7. Unsupervised Reliability    — placeholder — next real screen to build
8. First Mission Scheduling    — placeholder
```

**Prior entry — 2026-08-07 (sixth continuation — Phase 3 nav skeleton
**CI-confirmed green**). `MainActivity`, `OnboardingPlaceholderFragment`, `activity_main.xml`,
`fragment_onboarding_placeholder.xml`, `onboarding_nav_graph.xml` (9 destinations,
Recruit-through-Iron sequence per Onboarding doc §1), manifest launcher entry, and matching
strings are all written and pushed. Two real bugs were caught in this slice, both now fixed
and confirmed on real CI:
- §5.20 — stale `nextActionId` XML arguments left over from an abandoned resource-ID-default
  approach; `OnboardingPlaceholderFragment.kt` itself was already correct, only the graph had
  drifted.
- §5.21 — the fix attempted for §5.20 introduced a second, real compile failure
  (`NavDestination.actions` is private in Navigation Component 2.7.7, not public API as
  assumed); replaced with a hardcoded `when` on `currentDestination?.id` against the graph's
  own `R.id.action_*` constants. **This fix's own push has now come back green** —
  `:app:compileDebugKotlin` and `:app:testDebugUnitTest` both pass (§5.21's status line
  updated accordingly).

**Known gap, not yet closed:** `OnboardingPlaceholderFragmentTest` does not exist.
`app/build.gradle.kts` carries `androidx.fragment:fragment-testing:1.8.2` with a comment
that justifies the dependency specifically by reference to this test, but no such file has
ever actually been delivered across any session — flagged rather than silently left implicit,
per this project's own convention. Either write it against the Fragment/graph as they
currently stand, or remove the now-unjustified test dependency; leaving both as-is
(dependency present, nothing using it) is the wrong resting state.

**DONE — on-device install-and-tap pass confirmed, 2026-08-08.** Screenshots taken walking
the full sequence: Step 1 of 8 (Welcome / Product Philosophy) through Step 8 of 8 (First
Mission Scheduling), Next/Back both present and functional on every intermediate screen.
Step 8 correctly shows **only Back, no Next** — matches the Phase 3 exit criteria exactly
("confirm the last screen has no visible Next button").

**One thing this confirms rather than contradicts:** the counter reads "Step N of 8," not
"of 9," across the whole walk — this is *not* a bug. The hardcoded `when`-mapping added in
§5.21 always takes the non-Iron path from Tier Selection (step 4) straight to Mission Profile
Setup (step 5); the Iron Calibration Gate destination exists in the graph (per §5.21's own
note, its inbound action is "deliberately unused by this placeholder") but nothing currently
routes to it, so it's correctly invisible in this walk. Seeing 8 steps, not 9, with the
Iron Calibration Gate never appearing, is the *expected* current behavior, not a gap this
session found — that branch is simply not wired to anything yet, and wiring it belongs to
whatever slice actually implements Tier Selection's real UI (choosing a tier, not just tapping
Next) rather than to this placeholder skeleton.

This is the first genuine on-device confirmation this nav skeleton has ever had — CI confirms
compilation and unit tests; this confirms the actual navigation graph behaves correctly for a
real user tapping through a real device. Phase 3's nav-skeleton portion of its exit criteria
can now be considered met for the linear (non-Iron) path specifically.

**Phase 2 status (below) is unchanged by this session** — no enforcement-loop code was
touched. What follows is the prior session's snapshot, left as-is for history:

**Prior entry — 2026-08-07 (fourth continuation — `:app:testDebugUnitTest`
wired into CI and confirmed green for the first time)**
**Current phase:** Phase 0 and Phase 0.5 remain complete and confirmed on real CI, fully
green (unchanged since last entry). Phase 1 remains functionally complete (§5.9's spec gap
aside). **Phase 2 is now CI-confirmed to compile, package, AND pass its own test suite** —
the resource/manifest layer (layout, strings, accessibility service config, manifest
entries) exists, `InterceptionController` has real test coverage, and all four genuine
compile/dependency bugs found across this phase (the two `lifecycleScope` base-class bugs,
§5.16's `RoomDatabase`/`security-crypto` classpath gap, §5.17's missing `kotlin-test`
dependency) are fixed and confirmed on green CI runs (`:app:assembleDebug` on run #6,
`:app:testDebugUnitTest` on run #8 — the latter only possible because run #6's own follow-up
found the workflow had never actually run that task before). Treat Phase 2 as "compiles,
packages, and its tests pass, all confirmed on CI" rather than "done" — no on-device
install/run has happened yet, which is now the one clearly-remaining gap before this phase's
exit criteria (§2) are honestly checkable end-to-end. Per §5.12's own standard, §5.16's
`RoomDatabase`/`security-crypto` fix has one green run behind it (not yet a *second*
confirming run) — worth keeping in mind, though the practical risk is low since §5.17's
independent CI run (#8) exercised the same dependency graph without incident.

**What got built in the first Phase 2 pass (prior entry, unchanged):**
- `:domain/voice/` — `VoiceLineGate`, `FallbackVoiceBank`, `WardenVoiceProvider`, all
  unit-tested.
- `:domain/policy/InterceptionPolicy.kt` — PRD §14 countdown durations, unit-tested.
- `:data` — `MissionDao.activeMissionFor(userId)`, `UserDao.getSingleLocalUser()`.
- `:app` — `MissionAccessibilityService`, `MissionInterceptionActivity`,
  `InterceptionController`, `AppContainer`, `DbPassphraseProvider`,
  `NoOpWardenVoiceGenerator`.

**What got built/fixed in this continuation:**
- `res/layout/activity_mission_interception.xml`, `res/values/strings.xml`,
  `res/xml/mission_accessibility_service_config.xml`, and `AndroidManifest.xml`'s `<service>`
  + `<activity>` entries — the full resource/manifest layer Phase 2's first pass was missing.
  One self-caught mistake along the way: a first manifest draft added
  `PACKAGE_USAGE_STATS` for a feature (`UsageStatsManager`) explicitly out of this phase's
  scope per the service's own kdoc — caught and removed before finishing, not left in as
  "might need it later."
- `InterceptionControllerTest` — real Robolectric+Room test coverage for the one Phase 2
  class that had none, plus the matching `app/build.gradle.kts` test-dependency additions.
- **Two real compile bugs found and fixed:** `MissionAccessibilityService` and
  `MissionInterceptionActivity` both called `lifecycleScope` from base classes
  (`AccessibilityService`, plain `Activity`) that don't provide it — see Phase 2's own
  section above for the full account and fixes (manual `CoroutineScope` for the Service,
  `ComponentActivity` base class for the Activity). Neither was caught in the session that
  wrote them; both surfaced while tracing through real call sites to write the controller
  test, not from a compiler (none is available in this sandbox for Android/Kotlin).

**What "confirmed on real CI" actually took, for everything predating Phase 2:** not a
single clean push. Two real, distinct bugs were found by the real toolchain across two
separate CI runs — a cross-module Kotlin smart-cast compile error (§5.8) and a flaky
`Instant`-precision test assertion (§5.12) — both invisible to manual line-by-line review,
both fixed narrowly, and both re-verified green on a subsequent run before being logged here
as resolved. Phase 2's own two `lifecycleScope` bugs are the same story playing out again,
now twice over, before this phase has even reached CI once — worth taking as further
confirmation that "read carefully" and "compiles" are genuinely different bars, not just a
one-time lesson from Phase 0.5.

```
Phase 0 — Data Layer            █████████████████████  100% (code done, real CI green)
Phase 1 — Domain/Use-Cases      ████████████████████░  ~97% (4 of 4 use-cases written and
                                                        passing on real CI; demotion_triggered
                                                        rank-band values AND firing logic now
                                                        implemented §5.9 (2026-08-09) — written,
                                                        not yet CI/device-verified)
Phase 2 — Enforcement Loop      ██████████████████░░░  ~85% (all logic, resources, manifest,
                                                        and tests written, self-consistent,
                                                        AND now confirmed compiling,
                                                        packaging, AND passing its own test
                                                        suite on real CI; not yet run on a
                                                        device — see "still open")
Phase 3 — Onboarding & UI       ░░░░░░░░░░░░░░░░░░░░░  0%
Phase 4 — Fingerprint Rules     ░░░░░░░░░░░░░░░░░░░░░  0%
Phase 5 — Pilot                 ░░░░░░░░░░░░░░░░░░░░░  0%
```

**`app` module compiles, packages, AND its own tests pass — still not run on a device.**
Every file Phase 2 needs to compile, link resources, and install exists: Kotlin classes,
layout, strings, accessibility config, and manifest entries. `:app:assembleDebug` is
confirmed green (build-and-test run #6, §5.16), and — new this entry — `:app:testDebugUnitTest`
is also confirmed green (run #8, §5.17), meaning `InterceptionControllerTest` has actually
executed on a real Robolectric+Room toolchain, not just compiled. This is a materially
different state from the prior snapshot ("compiles and packages, tests not yet run") — the
test suite has now actually been exercised, not just written and read carefully. "Tests pass
in CI" and "confirmed installing/running on a device" are still not the same claim, though —
that step remains genuinely undone, and is now the single clearest remaining gap before this
phase's exit criteria (§2) are honestly checkable end-to-end. Four real bugs were found
across this phase before reaching this state (two `lifecycleScope` base-class errors, the
`RoomDatabase`/`security-crypto` classpath gap, the missing `kotlin-test` dependency) — each
one only surfaced by actually running the real toolchain, not by re-reading the code more
carefully, which is itself the concrete argument for not skipping the remaining device-install
verification either.

**This session's additions (no code logic touched, tooling/docs only):**
- `.github/workflows/build-and-test.yml` — added an "Upload debug APK" step
  (`app-debug-apk`, from `app/build/outputs/apk/debug/app-debug.apk`, 14-day retention,
  no `if: always()` since a failed-build APK isn't worth keeping). Previously the workflow
  only uploaded test reports; the actual built app was produced by `:app:assembleDebug` and
  then discarded when the runner tore down. This closes that gap — the ready-to-install debug
  APK can now be pulled directly from a run's Actions page instead of requiring a full
  on-device Termux Android SDK build every time (see `docs/PHASE2_DEVICE_VERIFICATION.md`,
  "Option B"). **Confirmed on a real run** — run #9 (`Add debug APK CI upload, fix stale app
  module comment, add device verification runbook`), Success, 3m56s, both `app-debug-apk`
  (9.03 MB) and `test-reports` (33.1 KB) present and downloadable from the Actions page.
  Screenshot-verified directly, not inferred from job-level green alone.
- `app/build.gradle.kts` — fixed a stale top comment that still described this module as
  containing "no app code beyond a launcher-less Application class." Phase 2 already added
  the real Accessibility Service, interception Activity, and their resources/tests; the
  comment predated that and was never updated. Comment-only, no logic change.
- New file: `docs/PHASE2_DEVICE_VERIFICATION.md` — a step-by-step on-device verification
  runbook for Phase 2's one remaining gap (real device/emulator install-and-trigger, §2/§4
  above). Written because verifying this requires an actual Android device/emulator/adb, none
  of which exist in the sandbox this project is authored in — same limitation this file's own
  Phase 0.5 push workflow already works around. Covers: pre-flight static checks (view-ID
  cross-reference, manifest/config consistency — done and clean, no bugs found),
  get-code-onto-phone, build-or-pull-APK, install, enable Accessibility Service, seed a test
  Mission and trigger interception at each tier, verify Iron crisis-exit timing and
  no-Ledger-write, verify survival through process death mid-countdown, and pull the CI
  `test-reports` artifact to directly confirm all 9 `InterceptionControllerTest` cases passed
  individually (§4 item 5(b)'s "inferred, not directly checked" gap).
- New file: `scripts/deploy_update.sh` — replaces the manual `mv`/`cp` zip-to-repo sequence
  previously documented in this section, after that manual sequence failed three separate
  times in the same real session (folder-name mismatch between the zip's internal directory
  and its filename, `mv`/`cp` behaving differently depending on pre-existing destination
  state, and a bare `.git` ending up in the project root instead of a subfolder — see the
  "Deploy script" writeup above this entry for the full account). The script verifies each
  step's precondition before acting (finds the project root by content rather than assumed
  name, extracts into a disposable temp folder, refuses to proceed on a structurally
  incomplete tree, restores `gradlew`'s executable bit which the zip round-trip was separately
  observed to silently drop) and stops on first error (`set -euo pipefail`) instead of
  continuing past a failure the way the manual chained commands did. Confirmed working this
  session — the run #9 push above went through this script's staged tree without incident.
  Deliberately does not run `git add`/`commit`/`push` itself; ends by printing `git status`
  for manual review, keeping that human-checked step exactly where it was in the original
  workflow.

---

## 4. Immediate next action

**Updated 2026-08-09 — §5.5, §5.9, §5.10, and §5.15 are now all RESOLVED** (product owner
sign-off session). See each section for the actual decisions. Historical item 4 below, which
lists them as open, is superseded — left as a record of what was outstanding at the time, not
current status.

**Updated 2026-08-09 (later same day) — §5.5, §5.9, and §5.15 are now also IMPLEMENTED**, not
just decided: `RecordViolationUseCase` (3-day window), `ApplyReputationDecayUseCase` +
`ReputationDecayPolicy` + `User.consecutiveDaysBelowFloor` (tier bands + demotion_triggered),
`TierTransitionUseCase` + `User.lastExplicitDowngradeAt` (24h cooldown). DB bumped v4→v5.
Written but not yet run through a real compiler (no Gradle/Android toolchain reachable from
the authoring sandbox) — run `./gradlew :domain:test :data:test` before merging. One open
judgment call from this pass, needs sign-off before Phase 5: which Reputation band each tier's
"floor" corresponds to isn't spec-stated; implementation assumes tier rank ↔ band rank
(Operator↔INCONSISTENT, Warden↔RELIABLE, Iron↔DISCIPLINED) — see
`ApplyReputationDecayUseCase`'s kdoc.

**Updated 2026-08-09 (reviewed before merge, separate session) — two issues found and fixed
in the above, neither caught by the authoring session since no compiler was reachable there
either:**
1. **Real bug:** `TierTransitionUseCase.explicitDowngrade`'s cooldown check had an inverted
   boolean (`elapsed < cooldown` where `elapsed >= cooldown` was intended) — it would have
   blocked users whose cooldown had genuinely elapsed and let through users mid-cooldown, the
   exact opposite of §5.15's purpose. Caught by manually tracing the patch's own three
   boundary tests (23h/24h/25h) against the literal code before applying it — all three would
   have failed had a real compiler run them. Fixed; see the method's inline comment for the
   corrected logic and reasoning.
2. **Design fix, not a bug:** `ApplyReputationDecayUseCase` originally wrote demotion
   `TierEvent`s directly via `TierDao`, bypassing `TierTransitionUseCase.standardDowngrade`
   even though its own kdoc described calling that method. Confirmed `standardDowngrade`'s
   internals do nothing beyond what was being duplicated (no missed side effect), so this
   wasn't a correctness bug — but fixed to actually call `standardDowngrade`, removing the
   duplication. Required restructuring `execute()` so the tier-change call happens outside
   the method's own `withTransaction` block (see that method's kdoc for why) — a real,
   non-obvious ordering constraint worth understanding before touching this method again.

Net: this is a concrete example of why every change gets reviewed before merge rather than
pushed straight through, especially given no session in this project's history has had a
working compiler — see STATUS.md's standing gaps list.

**Updated 2026-08-08 (§5.22) — items 2–6 below predate Tier Selection/Confirmation's
CI+device confirmation and Mission Profile Setup's construction; left as history, not
current status. Read this paragraph first, then treat the numbered list as background, not
as what's actually next.**

**What's actually next, as of this entry:**
1. **Push this session's work (`MissionProfile` entity/DAO, schema v4, `MissionProfileSetupFragment`,
   layout, strings, nav graph edit, `OnboardingPlaceholderFragment` dead-branch removal, new
   test file) and confirm CI green** — `:data:testDebugUnitTest` and `:app:testDebugUnitTest`
   both need to pick up and pass the new `MissionProfileSetupFragmentTest` cases, and the
   schema v4 bump needs to not break anything already passing. Nothing here has been through
   a real compiler yet (§5.22 has the full "manually checked, not compiled" caveat).
2. **Once green, on-device verify Mission Profile Setup** the same way §5.21 verified Tier
   Selection/Confirmation: walk Welcome → ... → Tier Selection/Confirmation → Mission Profile
   Setup on a real device, confirm the screen renders, Continue actually writes a
   `MissionProfile` row (check via logcat or a follow-up read), the empty-input case doesn't
   crash, and Back returns to the prior screen without writing anything.
3. **After that, the next real screen to build is Core Data Consent (§2.6)** — Mission Profile
   Setup's own action already routes there
   (`action_missionProfileSetup_to_coreDataConsent`), it's just still placeholder content on
   the receiving end.
4. **Separately, not blocking the above:** Goal Definition (§2.2) is still a placeholder and
   is the actual blocker for closing the gap §5.22 flagged (Mission Profile Setup's missing
   §2.8 "default suggestions"). Worth picking up before or after Core Data Consent, not
   strictly ordered relative to it — but closing §2.8's gap requires this screen specifically,
   not just "onboarding progress" in general.

**Historical items below (2026-08-07 and earlier) — superseded by the above, kept for
context on decisions already made, not as a current task list:**

1. Read §0–§1 of this file (you're doing that now).
2. **RESOLVED — the Phase 3 nav skeleton's fix is now CI-confirmed.** The first push failed
   real CI (run #12, §5.21) — `NavDestination.actions` is private, not the public API it was
   assumed to be. Fixed by hardcoding the next-action mapping as a Kotlin `when` on
   `currentDestination?.id` instead. **The fix's own push has now come back green**
   (`:app:compileDebugKotlin` and `:app:testDebugUnitTest` both pass) — §5.21 is updated to
   reflect this, no longer "fixed on paper." This item no longer needs anyone to watch Actions
   for it.
   **On-device verification: DONE, 2026-08-08.** Screenshots confirm Next/Back walks the full
   linear sequence correctly, Step 1 of 8 through Step 8 of 8, with Step 8 (First Mission
   Scheduling) correctly showing no Next button. The counter reading "of 8" rather than "of 9"
   is expected, not a gap — the Iron Calibration Gate destination exists in the graph but the
   hardcoded mapping never routes to it yet, so it correctly never appeared in this walk. This
   closes the nav skeleton's on-device verification for the non-Iron path. **Two things remain
   open, not this item:** (a) the Iron Calibration Gate branch has no real trigger yet — it's a
   reachable graph destination with nothing wired to send a user there, which belongs to
   whatever slice gives Tier Selection real tier-choosing UI rather than to this placeholder
   skeleton; (b) `OnboardingPlaceholderFragmentTest` still doesn't exist despite the
   `fragment-testing` dependency implying it does — see the current-state snapshot above.
3. **CI is green — this is no longer a "push and wait" item** for everything that predates
   item 2 above. Phase 0.5 is fully done:
   Gradle project shell, GitHub Actions workflow, `:app` skeleton, and now two real bugs
   found and fixed with a confirmed green re-run (§5.8, §5.12). Don't re-flag "hasn't been
   through a real compiler" for any code that predates this entry — it has. Anything *new*
   written after this entry is, as always, unverified until it's pushed and CI confirms it;
   the standing discipline (manual cross-check against real signatures, hand-simulate any
   new hand-written SQL against real SQLite, then push and let CI have the final word) still
   applies to new work, just not to what's already green.
4. **§5.5 is still open** — the shared-cause guard's rolling-window cutoff still needs either
   a real value (once Phase 5 pilot data exists) or an explicit decision that cluster IDs are
   always short-lived enough not to matter. **§5.9 is still open** — `demotion_triggered`'s
   `tier_floor`/`N` values are absent from the spec, not just unvalidated; this needs a
   spec-doc revision from whoever owns the PRD/Data Model doc, not an engineering guess.
   **§5.10 is still open** — the crisis-stabilization pause reusing `debtAccrualPausedUntil`
   to also gate Reputation decay is a judgment call the PRD doesn't make explicitly; flagged
   for sign-off, not silently assumed correct. **§5.15 is now also open** — Explicit
   Downgrade's target tier (one-tier-down) is a Phase 2 judgment call with the same
   "flagged, not assumed" status as §5.5/§5.9/§5.10.
5. **Phase 1 is functionally complete and CI-verified** except the `demotion_triggered` gap
   (§5.9) — that's a spec gap, not an engineering task, and shouldn't block Phase 2.
6. **Phase 2's code/resource/manifest/test work is done and CI-confirmed — what's left is
   verification, not authoring.** The Play Console research blocker in Architecture §1.2 was
   explicitly and correctly skipped for this pass (sideload-only distribution, your call) —
   don't redo that research unless distribution scope changes. Everything below is now true,
   not aspirational: the Voice layer, `InterceptionPolicy`, `InterceptionController`,
   `MissionAccessibilityService`, `MissionInterceptionActivity`, the layout/strings/
   accessibility-config resources, the manifest `<service>`/`<activity>` entries, and
   `InterceptionControllerTest` all exist, are self-consistent, and — as of this entry —
   `:app:assembleDebug` is confirmed green on real CI (§5.16). **What's actually left:**
   a. **A second confirming CI run.** Per §5.12's own standard, one green run isn't
      automatically "permanently resolved" — the next push should re-confirm §5.16's fix
      before it's treated as fully settled, not assumed stable on the strength of one run.
   b. **`:app:testDebugUnitTest` — now actually run, found a real bug, fixed, and
      confirmed green.** Wiring the task into CI this session (§5.16's addendum)
      immediately surfaced a second genuine gap: `kotlin-test` was never declared, so
      `InterceptionControllerTest` couldn't compile (`assertFailsWith` unresolved — §5.17).
      Fixed and confirmed on run #8. **What's still worth a direct look, not assumed:** §5.17
      confirms the job as a whole went green; it does not independently confirm the
      `test-reports` artifact shows all 9 `InterceptionControllerTest` cases specifically
      passing rather than some subset skipped. Worth 30 seconds pulling that artifact open
      before treating this as fully airtight.
   c. **An actual on-device or emulator install-and-run.** CI confirms compiling and
      packaging, nothing about runtime behavior — no interception has ever actually been
      triggered and observed. This is the real remaining gap before Phase 2's exit criteria
      checklist (§2) is honestly checkable end-to-end, not a formality.
   d. **`androidx.security:security-crypto:1.1.0-alpha06`** — flagged in §5.16 as an alpha
      version pinned because it matched existing code, not independently vetted. Worth a
      deliberate look at whether a stable release now covers `DbPassphraseProvider`'s needs,
      lower priority than (a)–(c) given sideload-only scope.

**(c) is now in progress — plan below.** Real device install (Termux → SAI → sideload,
`docs/PHASE2_DEVICE_VERIFICATION.md` steps 1–4) is done: app installs, launches, has no
launcher icon (correct — no `MAIN`/`LAUNCHER` intent-filter exists, by design, until Phase 3),
Accessibility Service registers and enables (initially blocked by Android's Restricted
Settings anti-malware gate on sideloaded apps — resolved via SAI as installer, not a bug in
the app; full account logged as §5.18). What's left for (c): trigger an actual interception,
which needs a `Mission` row to exist. There is currently no way to create one — no UI (Phase
3), no seed mechanism. Chosen approach and why, in order of what was considered:

- **Raw SQL against the Room DB file directly** — rejected. The DB is SQLCipher-encrypted
  (`DbPassphraseProvider`), so plain `sqlite3` from a shell can't open it without replicating
  the passphrase logic outside the app; and even with that solved, hand-written INSERTs bypass
  Room's type converters and FK constraints entirely, risking a malformed row that fails
  silently at the SQL level and only surfaces later as a confusing crash deep in app code —
  the wrong place to be debugging when the actual open question is "does interception work,"
  not "did I write correct SQL by hand."
- **Instrumented test invoking the real DAOs via `adb shell am instrument`** — this is
  actually the most rigorous option (reuses already-CI-verified `MissionDao`/`UserDao` code
  paths, touching the same encrypted-DB machinery production code already exercises,
  correctness inherited rather than re-risked) but requires `adb` targeting a separate
  device, which doesn't apply here — Termux and the target device are the same phone, and
  `adb` has no concept of "device talking to itself" (confirmed this session:
  `adb: no devices/emulators found` even with the daemon running).
- **Chosen: a small, dedicated debug-buildType seed class, tested like real code, not
  improvised inline.** Concretely:
  1. New file `app/src/debug/java/com/disciplineos/app/debug/DebugSeeder.kt`, living under
     Gradle's `debug` source set (not `main`) so it is compiled into debug builds only and
     structurally cannot ship in a release build regardless of anyone forgetting to remove
     it later — enforced by the build system, not by memory or a code comment.
  2. It calls the real `UserDao`/`MissionDao` insert methods already used by
     `RecordViolationUseCase` and friends — no new persistence logic, no hand-rolled SQL,
     just constructing one valid `User` row and one valid `Mission` row (tier, blocklist,
     `status = ACTIVE`, sane timestamps via the existing `Converters.kt` path) and calling
     `.insert()`.
  3. Invoked once, deliberately (a one-line call from `AppContainer.onCreate` gated behind
     `if (BuildConfig.DEBUG && <no existing Mission>)`), not on every launch — avoids
     silently re-seeding or duplicating rows on subsequent opens.
  4. **Gets its own unit test** (`DebugSeederTest`, same rigor as any other class in this
     codebase) asserting: seeding is a no-op if a Mission already exists (idempotency), and
     the round-tripped row reads back with the exact tier/blocklist/status values written —
     catching the exact class of "subtly wrong foreign key or enum value" bug that raw SQL or
     an untested inline hook would only surface at runtime, on-device, mid-verification.
  5. Ships through the same review discipline as everything else in this file: written,
     manually cross-checked against real DAO signatures (no compiler in the authoring
     sandbox — same standing caveat as all other new code per item 2 above), pushed, CI
     green required before treating it as real, only then relied on for the actual
     interception test.
  6. **Explicitly test infrastructure, logged as such, not silent scaffolding to forget
     about** — matching this file's own standard for the dispute flow and crisis boundary:
     once (c) is fully verified across all three tiers, decide explicitly whether
     `DebugSeeder` stays (useful for future manual QA passes) or gets deleted, and record
     that decision here rather than leaving it to linger unexamined.
  7. Once seeded, walk `docs/PHASE2_DEVICE_VERIFICATION.md` §5 exactly as written: trigger
     interception at Recruit, then Warden, then Iron (separate passes — the branching logic
     differs materially), confirming Iron's crisis exit specifically writes no Ledger entry
     and exits with no delay.

**`DebugSeeder` — written this session, not yet run through real CI.** All six items in the
plan above are implemented:
- `app/src/debug/java/com/disciplineos/app/debug/DebugSeeder.kt` — debug-source-set-only,
  calls real `UserDao`/`MissionDao` insert methods (no hand-rolled SQL), idempotent
  (`seedIfNeeded` returns `null` and writes nothing if an ACTIVE Mission already exists for
  the single local user), seeds at a caller-supplied `Tier` (defaults to Warden) so
  Recruit/Iron passes are reachable later by re-seeding, not a second class.
- `app/src/main/java/com/disciplineos/app/DisciplineOsApplication.kt` — **new**, this
  codebase's first `Application` subclass (nothing needed one before this). Calls
  `DebugSeeder.seedIfNeeded` from `onCreate()`, gated behind `BuildConfig.DEBUG` as a second,
  independent guard alongside the debug source set itself. Registered in
  `AndroidManifest.xml` via `android:name=".DisciplineOsApplication"`.
- `app/build.gradle.kts` — added `buildFeatures { buildConfig = true }`, required for the
  `BuildConfig.DEBUG` reference above; nothing in this module referenced `BuildConfig` before
  now so it was never turned on.
- `app/src/test/java/com/disciplineos/app/debug/DebugSeederTest.kt` — same in-memory
  Room-under-Robolectric pattern as `InterceptionControllerTest`. Covers: a fresh seed creates
  exactly one User + one ACTIVE Mission with the right blocklist; the written `Tier` round-trips
  correctly; a second `seedIfNeeded` call is a no-op (`null` return, no duplicate Mission row);
  a repeat call does not create a second User row either (checked directly, not just inferred
  from the Mission-level idempotency check passing).

**Standing caveat, same as every other new file in this project until it's been through real
CI:** manually cross-checked against real DAO/entity signatures (`UserDao.insert`,
`MissionDao.insert`, `MissionDao.activeMissionFor`, `User`/`Mission` constructors all matched
field-for-field against `data/.../entity/` and `data/.../dao/CoreDaos.kt` as they exist in
this tree, not from memory), but not yet compiled — no Android/Kotlin compiler in this
authoring sandbox, per §4 item 2's standing note. Push, let CI confirm
`:app:testDebugUnitTest` picks up and passes `DebugSeederTest`, then this note graduates the
same way §5.8/§5.12/§5.16/§5.17 did. Once CI is green, proceed to plan step 7 above: build or
pull the CI debug APK, reinstall on-device (the new `Application` class and `BuildConfig`
build-feature flag both mean the APK has changed since the last install), confirm the app
still launches (an `Application.onCreate()` crash would be silent/fatal at every launch, not
just during seeding — the `try/catch` around the seeding coroutine specifically guards
against a seeding *failure* taking down startup, but a crash in `AppContainer.database()`
itself, e.g. a passphrase-provider issue, is not caught by that same block and is worth
watching for on first relaunch), then check logcat for the `DisciplineOsApp` tag to confirm
seeding ran, then walk the three-tier interception verification in
`docs/PHASE2_DEVICE_VERIFICATION.md` §5.

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

### 5.5 — RESOLVED — Shared-cause guard: 3-day rolling window

**Where:** `domain/.../usecase/RecordViolationUseCase.clusterAlreadyHasActiveEntry()`

**The gap:** Data Model §3.5 specifies the shared-cause guard should check for existing
same-cluster consequences "within a rolling window," but no window length was given anywhere
in the spec (unlike Reliability Index's explicit 14-day default, or the Debt Ceiling's
explicit 14-day default).

**What was implemented before this decision:** no window at all — the guard checked for
*any* active (non-reversed) same-cluster entry, regardless of age. This meant if a
`rootCauseClusterId` was reused far apart in time, a second violation in that cluster would
never get penalized, even months later — which silently undermines the purpose of tracking
recurring patterns at all.

**Call made (product owner sign-off, this session):** implement a **3-day rolling window**.
A same-cluster violation is deduplicated (not double-penalized) only if a prior active entry
for that `rootCauseClusterId` exists within the last 3 days; outside that window, a repeat of
the same root cause is treated as a new, independently-real pattern.

**Rationale:** an unconditional (forever) guard, while the strictly "safe" direction in the
sense of never double-penalizing, actively works against the app's own purpose — genuinely
recurring problems (e.g. the same excuse reused every few weeks) would be permanently
invisible to Debt Ceiling accounting, which undermines the "effort and lapses stay real"
principle Data Model §3.1 is built around. 3 days is long enough to cover a single genuinely
connected cluster of misses (e.g. one bad week caused by one disruption) without either
under- or over-counting, and short enough that a recurrence next month is correctly treated
as a new instance of a pattern worth flagging, not a mechanical repeat of the old one. No
external research literature gives a "correct" number for this — it is a product-scale
judgment call, made explicitly rather than left as an unbounded default, and flagged
`[HYPOTHESIS]` per this project's standing convention pending Phase 5 pilot data.

**Revisit when:** Phase 5 pilot data exists to check whether 3 days over- or under-catches
real shared-cause clusters in practice.

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

**Update, Phase 0.5:** this path is now exercised directly, not just reasoned about —
`TierTransitionUseCaseTest`'s `` `a mission marked aborted crisis exit by this use-case cannot
be double-charged by RecordViolationUseCase` `` test constructs exactly this scenario
(`TierTransitionUseCase.ironCrisisExit()` marks a Mission `ABORTED_CRISIS_EXIT`, then a
`RecordViolationUseCase.execute()` call against that same Mission is asserted to throw) and
passed on real CI. The `require` above is no longer just a defensive check nobody's called —
it's now proven to actually fire for the one caller (`TierTransitionUseCase`) that could
plausibly race it.

### 5.8 — RESOLVED (Phase 0.5, real CI) — Cross-module smart-cast failure in `RecordViolationUseCase`

**Where:** `domain/.../usecase/RecordViolationUseCase.execute()`, the `debtBlocked` /
`reputationBlocked` computation (originally around line 87–90).

**What CI actually found, first real compile error this project has had:**
```
val debtBlocked = violation.rootCauseClusterId != null &&
    clusterAlreadyHasActiveEntry(violation.rootCauseClusterId, violation.id, LedgerMetric.DEBT)
```
failed to compile with a smart-cast error on the second `violation.rootCauseClusterId` use.

**Why manual review didn't catch it:** the logic reads as obviously correct — a `!= null`
guard immediately followed by a use of the same property inside the `&&`. This is a standard,
safe Kotlin idiom *within a single module*. The failure is specific to `:data` and `:domain`
being separate compiled Gradle modules: `Violation.rootCauseClusterId` (`val
rootCauseClusterId: UUID? = null`) is declared in `:data`; the use site is in `:domain`.
Kotlin's smart-cast requires the compiler to prove nothing else could change the property's
value between the null-check and the use — and it does not extend that proof across a module
boundary, since it can't verify another module's getter is stable/non-overridable from where
it's compiling. This is a well-documented, common multi-module Kotlin gotcha, not a sign of a
deeper design problem, and not something a `:data`/`:domain`-unaware read of the file would
surface.

**Fix:** bind the nullable value to a local `val` first, so the compiler is reasoning about a
local variable's stability (never in question) rather than a cross-module property's:
```
val clusterId = violation.rootCauseClusterId
val debtBlocked = clusterId != null &&
    clusterAlreadyHasActiveEntry(clusterId, violation.id, LedgerMetric.DEBT)
val reputationBlocked = clusterId != null &&
    clusterAlreadyHasActiveEntry(clusterId, violation.id, LedgerMetric.REPUTATION)
```

**Scope check performed:** `ResolveDisputeUseCase.kt` (the only other file touching
`Violation`) was checked and does not read `rootCauseClusterId` at all — not affected. The
rest of `:domain` was scanned for the same nullable-property-then-immediate-use pattern
against any `:data`-declared property; no other instance found.

**Confirms:** the standing caution in §4/§0 about "looks correct on careful reading" and
"compiles" being distinct claims — this is a concrete instance of that gap, found by the
real toolchain within the first CI run that included new code, not by inspection however
careful.

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

### 5.10 — RESOLVED — Crisis-stabilization pause applies to Reputation decay too, not just Debt

**Where:** `domain/.../usecase/ApplyReputationDecayUseCase.execute()`

**The gap:** PRD §12.4.3 states Crisis Downgrade "pauses debt accrual" and frames the whole
event as "a stabilization event, not a punishment event"; §12.4.4 adds that the Iron Crisis
Exit specifically "carries no score penalty." None of this explicitly said whether
Reputation *decay* (the scheduled `decay_per_missed_day` term this use-case implements, as
opposed to a Violation's immediate per-event Reputation penalty, which is moot here since a
crisis-exit Mission never reaches `RecordViolationUseCase` at all) should also pause during
the same window.

**Call made (product owner sign-off, this session):** confirmed — `User.debtAccrualPausedUntil`
gates Reputation decay too, not just Debt accrual, using the same field rather than a second
one.

**Rationale:** if Reputation kept quietly draining during the one window explicitly designed
to protect the user, the app would be punishing them during the exact moment it claims not
to be — a direct contradiction between the PRD's stated framing ("stabilization event, not a
punishment event") and actual behavior. Pausing both mechanisms together keeps that framing
honest rather than technically-true-but-misleading. Reusing the existing field (rather than
adding `reputationDecayPausedUntil` separately) remains correct: both `TierTransitionUseCase`
paths that set either field already set `debtAccrualPausedUntil` to the identical 24-hour
instant, and no scenario has been identified where the two should diverge.

**Recovery credit is NOT paused by the same window** — crediting a completed Mission during
stabilization is never adverse to the user, so pausing it would undercut the stabilization
framing rather than support it.

### 5.9 — RESOLVED — `demotion_triggered`'s `tier_floor`/`N` values, decided by product owner

**Where:** `domain/.../usecase/ApplyReputationDecayUseCase` (class-level kdoc has the full
version of this note; repeated here per this file's own "log at the time the call is made"
convention)

**The gap:** Data Model doc §3.5 states the demotion-trigger formula as `demotion_triggered
when Reputation < tier_floor for tier N consecutive days` — but neither `tier_floor` (a
value per rank, for the seven ranks in PRD §35: Undisciplined → Inconsistent → Reliable →
Disciplined → Relentless → Elite → Iron Will) nor `N` (the consecutive-day count) had a
value anywhere in the PRD or Data Model doc — not flagged `[HYPOTHESIS]` like the decay rate,
just absent.

**Call made (product owner sign-off, this session):** Reputation is a 0–100 scale, banded as
follows, explicitly marked `[HYPOTHESIS]` pending Phase 5 pilot data:

| Rank | Reputation range | Width |
|---|---|---|
| Undisciplined | 0–20 | 21 |
| Inconsistent | 21–40 | 20 |
| Reliable | 41–54 | 14 |
| Disciplined | 55–69 | 15 |
| Relentless | 70–84 | 15 |
| Elite | 85–94 | 10 |
| Iron Will | 95–100 | 6 |

`N = 3` consecutive days below a rank's floor before `demotion_triggered` fires — consistent
with §5.5's 3-day window, on the same reasoning: long enough to reflect a real pattern rather
than one bad day, short enough to react promptly.

**Rationale for band shape:** middle bands (Reliable, Disciplined) are widest since most
users should live there day-to-day; top and bottom bands are narrower so that "Elite" and
"Iron Will" mean something rare and earned, and "Undisciplined" isn't trivially exited.
Bands were checked programmatically to be contiguous with no gaps or overlaps, covering
0–100 exactly.

**Why this was a product decision, not an engineering guess:** no external research
literature supplies "correct" tier-floor values for a bespoke 7-rank reputation system — this
directly defines what "Disciplined" vs. "Inconsistent" *means* to a user, which is a
product-design decision, not a formula gap. Recorded here as the product owner's explicit
call rather than an invented default.

**Revisit when:** Phase 5 pilot data exists to check whether these bands over- or
under-demote in practice; expect the boundaries (not necessarily the rank order or count) to
move once real usage data exists.

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

### 5.13 — RESOLVED, needs eventual sign-off — Single-local-user assumption for Phase 2 enforcement

**Where:** `data/.../dao/CoreDaos.kt` (`UserDao.getSingleLocalUser()`),
`app/.../MissionAccessibilityService.kt`, `app/.../di/AppContainer.kt`.

**Call made:** `MissionAccessibilityService` needs "the current user" on every foreground-app
change event, but nothing in the PRD, Data Model doc, or Onboarding spec addresses
multi-profile/login — checked directly, confirmed absent, not just unnoticed. Treated this as
a single local device-user app: one person, one install, one `User` row. `getSingleLocalUser()`
is `SELECT * FROM users LIMIT 1`, returning null before onboarding creates the row at all
(callers must treat null as "nothing to enforce yet," not an error).

**Why this reading and not shared-device-multi-account:** "personal use, then friends" (doc 05's
scoping) reads as friends running their own separate installs, not multiple people sharing one
device's Mission/Ledger state — sharing enforcement state across people on one phone would be a
much stranger product than the specs otherwise describe.

**Revisit when:** if a real multi-profile need ever surfaces (e.g., one person, multiple
devices needing synced state — a different problem than multi-account), this DAO method and
every call site built on top of the "there is exactly one user" assumption needs a real audit,
not a quick patch. Flagging now specifically so that audit isn't a surprise later.

### 5.14 — RESOLVED — Full-screen Activity chosen over `SYSTEM_ALERT_WINDOW` overlay for interception

**Where:** `app/.../MissionAccessibilityService.kt` (launches `MissionInterceptionActivity`),
`app/.../MissionInterceptionActivity.kt`.

**Call made:** the interception screen is a full-screen `Activity` launched by the
Accessibility Service on blocklist match, not a `TYPE_APPLICATION_OVERLAY` window drawn over
the foreground app.

**Why:** a `SYSTEM_ALERT_WINDOW`-style overlay needs its own separate runtime permission grant
(`Settings.canDrawOverlays()`), which means a second permission dialog during setup — real
friction for a family/friend install where the whole point is a quick, low-effort setup.
Launching an `Activity` from the Accessibility Service achieves the same practical effect
(interrupts the blocked app, shows the interception screen) without that extra permission.
Architecture doc §6 explicitly defers interception screen *layout* to Phase 3 but doesn't
mandate the overlay *mechanism* specifically — §1.1's "interception overlay" language is used
functionally elsewhere in this codebase's own comments, not as a literal `TYPE_APPLICATION_
OVERLAY` requirement.

**Revisit when:** if a future requirement needs the interception screen to draw over a locked
device or in some other context an `Activity` launch can't reach (overlay windows and
activities have different capabilities here), this choice needs re-examining — it was made for
setup-friction reasons, not because overlays are impossible.

### 5.15 — RESOLVED — Explicit Downgrade: one tier down, with a 24h rolling cooldown

**Where:** `app/.../MissionInterceptionActivity.kt` (`oneTierDown()`),
`domain/.../TierTransitionUseCase.kt` (`explicitDowngrade`).

**The gap:** PRD §12.4.2 describes Explicit Downgrade as "a persistent, always-visible 'this
is too much right now' control," but — unlike §12.4.3's Crisis Downgrade, which explicitly
names Recruit as the fixed landing tier — §12.4.1/§12.4.2 never states what tier an Explicit
Downgrade actually lands on.

**Call made (product owner sign-off, this session):** one tier down from the user's current
tier (Iron→Warden→Operator→Recruit), no-op at Recruit — confirmed as originally implemented.
**New:** a **24-hour rolling cooldown** between uses, tracked from the timestamp of the last
use (not a calendar-day reset), surfaced in the UI so the user can see when the control is
next available.

**Rationale:** an always-available "too much right now" control reads as calibration, not
crisis — jumping straight to Recruit the way Crisis Downgrade does would conflate a user's
own "dial it back" self-report with the Tampering/Critical-violation trigger §12.4.3 is
reserved for, and could paradoxically discourage early use of a preventive tool by making it
feel as heavy as the emergency one. One-tier-down is the smallest change that still respects
the button's stated purpose. The cooldown exists because an unlimited, repeatable
tier-down action — even though each individual use only ever reduces enforcement, never
increases it — could otherwise function as a de facto bypass of the tier system entirely if
chained rapidly. A 24h *rolling* window (not a calendar-day cap) was chosen specifically to
avoid the midnight-boundary loophole a calendar-day reset would allow (e.g. using it at
11:58pm and again at 12:01am, three minutes later, under a calendar-day rule).

**Not yet implemented as of this decision:** the cooldown tracking field/logic itself —
`explicitDowngrade` currently has no cooldown enforcement. This is now a scoped, well-defined
follow-up task rather than an open design question.

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

### 5.12 — RESOLVED (Phase 0.5, real CI, second run) — Flaky `Instant` precision assertion in `TierTransitionUseCaseTest`

**Where:** `domain/src/test/.../usecase/TierTransitionUseCaseTest.kt`, `` `crisis downgrade
always moves to Recruit and pauses debt accrual and Tribunal for 24 hours` ``

**What CI actually found, second real failure this project has had (a test failure, not a
compile error — the fix in §5.8 was already pushed and compiled cleanly; 28 of 29 domain
tests passed this run):**
```
com.disciplineos.domain.usecase.TierTransitionUseCaseTest > crisis downgrade always moves to
Recruit and pauses debt accrual and Tribunal for 24 hours FAILED
    java.lang.AssertionError at TierTransitionUseCaseTest.kt:111
```

**Why manual review didn't catch it:** the test looks correct — it captures `val now =
Instant.now()`, passes it into `crisisDowngrade(..., now = now)`, then asserts
`now.plus(24, HOURS)` equals `user.debtAccrualPausedUntil` after re-reading the user from the
DB. Same `now`, same arithmetic, should be bit-for-bit equal. The actual failure is a
precision mismatch introduced entirely by the persistence round-trip: `Instant.now()`
frequently carries **nanosecond** precision on the JVM, but `Converters.fromInstant` stores
`Instant` via `.toEpochMilli()` — **millisecond** precision — and `Converters.toInstant`
reconstructs via `Instant.ofEpochMilli(...)`, which always has a zeroed sub-millisecond
component. The assertion therefore compares a nanosecond-precision in-memory value against a
millisecond-truncated, DB-round-tripped one — equal only when `now()` happened to land on an
exact millisecond boundary. This is flakiness, not incorrect production logic: the actual
`TierTransitionUseCase.crisisDowngrade()` code being tested was never in question.

**Fix:** truncate `now` to millisecond precision at the point of capture in the test, so the
in-memory value used for the assertion and the DB-round-tripped value being compared against
agree deterministically:
```kotlin
val now = Instant.ofEpochMilli(Instant.now().toEpochMilli())
```

**Scope check performed:** every other `Instant.now()` + DB-read comparison across both
`TierTransitionUseCaseTest.kt` and `ApplyReputationDecayUseCaseTest.kt` was enumerated and
checked. No other instance is at risk — every other DB-backed assertion in both files
compares enums (`Tier`, `MissionStatus`, `TierEventKind`), `Double` values (via
`LedgerDao.currentValue`, which uses a delta epsilon already), or null-checks
(`assertNotNull`), none of which lose precision through the millis round-trip. This was an
isolated instance, not a systemic pattern needing a broader fix.

**Confirms, alongside §5.8:** this project's now-standing pattern of "real toolchain finds
something manual review didn't, fix is narrow and well-understood, re-verify on CI rather
than assume the fix is correct" — both of Phase 0.5's real failures were found, fixed, and
then actually re-confirmed green on a subsequent CI run before being logged here as
resolved, not logged as resolved on the strength of the fix looking correct.

---

### 5.16 — RESOLVED (Phase 2, real CI, first run against the full interception-screen tree) — `:app:compileDebugKotlin` couldn't resolve `RoomDatabase` or `androidx.security.crypto`

**Where:** `app/build.gradle.kts`, `data/build.gradle.kts` — not any of the Kotlin source
files themselves; every file the Phase 2 continuation actually wrote or edited
(`MissionInterceptionActivity.kt`, `MissionAccessibilityService.kt`, `AppContainer.kt`,
`DbPassphraseProvider.kt`, the new layout/strings/xml resources, `InterceptionControllerTest.kt`)
was correct as written — this was a classpath/dependency-declaration gap, not a logic bug.

**What CI actually found, third real failure this project has had (a compile error, `:app`
module's first real compile against a full source tree — everything before this either
compiled cleanly or was `:data`/`:domain` only):**
```
e: Supertypes of the following classes cannot be resolved. Please make sure you have the
required dependencies in the classpath:
    class com.disciplineos.data.db.DisciplineOsDatabase, unresolved supertypes:
    androidx.room.RoomDatabase
...
e: .../DbPassphraseProvider.kt:4:17 Unresolved reference: security
e: .../DbPassphraseProvider.kt:52:25 Unresolved reference: MasterKey
e: .../DbPassphraseProvider.kt:55:9 Unresolved reference: EncryptedSharedPreferences
```
Every `RoomDatabase`-supertype error traced to `AppContainer.kt`, `MissionAccessibilityService.kt`,
and `MissionInterceptionActivity.kt` — i.e. every `:app`-module file that touches
`DisciplineOsDatabase` directly, which Phase 2 is the first phase to actually do (Phase 0/0.5
only exercised `:data`/`:domain` in isolation).

**Why manual review didn't catch it, twice over:**
1. `DisciplineOsDatabase : RoomDatabase()` lives in `:data`, and `:data`'s own
   `build.gradle.kts` declares `implementation("androidx.room:room-runtime:...")` /
   `room-ktx` — correct for `:data` compiling itself, but `implementation`-scoped
   dependencies are deliberately **not** exposed transitively to modules that depend on
   `:data`. `:app` depends on `:data` and references `DisciplineOsDatabase` directly, but
   never declared its own Room dependency (nothing in Phase 0/0.5/2 had needed `:app` to see
   Room's types before — this module was "deliberately minimal" per its own build file's
   header comment right up until Phase 2 gave it real code). A partial fix was floated first
   (add Room directly to `:app`'s own dependency block) but rejected in favor of the more
   correct fix below.
2. `androidx.security:security-crypto` was never declared anywhere, in either module.
   `DbPassphraseProvider.kt`'s own kdoc says its job is exactly what `DisciplineOsDatabase`'s
   kdoc requires ("[passphrase] must come from Android Keystore-backed storage at the call
   site") — the file was written correctly against that requirement, but the dependency that
   makes `MasterKey`/`EncryptedSharedPreferences` resolvable was simply never added when the
   file was.

**Fix — two changes, one per module, not a workaround in either Kotlin file:**
- `data/build.gradle.kts`: changed `implementation("androidx.room:room-runtime:2.6.1")` and
  `implementation("androidx.room:room-ktx:2.6.1")` to `api(...)`. This is the more correct
  fix over duplicating the dependency in `:app` — `DisciplineOsDatabase` extending
  `RoomDatabase` makes Room part of `:data`'s public surface by construction (any consumer
  that touches `DisciplineOsDatabase` needs `RoomDatabase` visible), so `api` is the accurate
  Gradle modeling of that reality, not `implementation` in two places with the version
  string duplicated.
- `app/build.gradle.kts`: added `implementation("androidx.security:security-crypto:1.1.0-alpha06")`
  — this one is a genuine `:app`-only dependency (nothing in `:data`/`:domain` uses it), so
  `api` in `:data` wouldn't have helped here; it needed adding where it's actually consumed.

**Not yet done, flagged rather than silently assumed:** the `security-crypto` version pinned
(`1.1.0-alpha06`) is an alpha release — this was the version already implied by
`DbPassphraseProvider.kt`'s API usage (`MasterKey`, `EncryptedSharedPreferences`) at the time
of writing, not independently re-checked against whatever `androidx.security` releases exist
now. Worth confirming a stable release exists before this goes anywhere near a non-sideload
distribution; sideload-only per current scope (§0) makes this a lower-priority check than it
would otherwise be, not a reason to skip it indefinitely.

**Confirmed on CI:** `build-and-test` run #6, `:app:assembleDebug` (and therefore
`:app:compileDebugKotlin`) green, 3m3s. Per this project's own §5.12 standard, this should
still get a second confirming run before being treated as fully settled — logged here as
resolved on the strength of one green run plus a clear, narrow root cause, matching how §5.8
(not §5.12) was initially logged, with the same caveat: watch for this recurring before
calling it permanently closed.

**A second, distinct gap this entry uncovered — the workflow itself never ran
`InterceptionControllerTest` at all.** Checking whether run #6 had actually executed
`:app`'s test suite (not just compiled it) surfaced that `.github/workflows/build-and-test.yml`
only ran `:data:testDebugUnitTest`, `:domain:testDebugUnitTest`, and `:app:assembleDebug` —
`assembleDebug` proves compilation and packaging, nothing about whether the Robolectric+Room
test suite (`InterceptionControllerTest`, the first real `:app`-module test this project has)
actually passes. This is exactly the gap the §4/item-5(b) "still open" note was flagging, made
concrete: a green CI run had been silently read as covering more than it actually checked.
**Fix:** added a `Run :app unit tests` step (`./gradlew :app:testDebugUnitTest --stacktrace`)
between the `:domain` tests and `:app:assembleDebug` steps, and extended the test-report
upload path to include `app/build/reports/tests`. This CI run (build-and-test run #7) is
that confirming run — and it correctly caught something real: see §5.17.

---

### 5.17 — RESOLVED, confirmed on CI run #8 — `kotlin-test` never declared, `InterceptionControllerTest` couldn't compile

**Where:** `app/build.gradle.kts` — again a classpath/dependency-declaration gap, not a
problem with `InterceptionControllerTest.kt` itself, which was correct as written.

**What CI actually found — the very first run of `:app:compileDebugUnitTestKotlin`, made
possible only because §5.16's addendum wired `:app:testDebugUnitTest` into the workflow this
same session (run #6 never exercised this task at all):**
```
e: .../InterceptionControllerTest.kt:32:15 Unresolved reference: test
e: .../InterceptionControllerTest.kt:243:9 Unresolved reference: assertFailsWith
e: .../InterceptionControllerTest.kt:243:64 Suspension functions can be called only within
   coroutine body
```
(and three more matching pairs, at lines 244, 273/274, 290 — every `assertFailsWith` call
site in the file, plus the cascading "suspension function" errors that follow once the
import itself won't resolve.)

**Why manual review didn't catch it:** logged back when this test was first written (this
project's own prior-session account, not fabricated after the fact): `assertFailsWith`
from `kotlin.test` was deliberately chosen over JUnit's `assertThrows` specifically because
nesting `runTest { }` inside `assertThrows`'s synchronous lambda is the wrong pattern for a
suspend-function assertion — `assertFailsWith` called directly inside the outer `runTest`
coroutine body is the idiomatic fix, and that reasoning was correct. What wasn't checked at
the time: whether `kotlin-test` was actually on `:app`'s test classpath to import from in the
first place. `:domain`'s own tests never surfaced this gap because none of them use
`assertFailsWith` — this was genuinely specific to `InterceptionControllerTest`, and nothing
had ever compiled `:app`'s test sourceset against a real toolchain before run #7.

**Fix:** added `testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")` to
`app/build.gradle.kts` — version pinned to match `org.jetbrains.kotlin.android`'s own
`1.9.24` in the root `build.gradle.kts`, not a separately-guessed number.

**Confirmed on CI:** yes — `build-and-test` run #8, green, 3m8s (`:app:testDebugUnitTest`
included in the job and did not fail it). This is the confirming run flagged as owed above —
`InterceptionControllerTest` has now actually executed on a real toolchain for the first
time, not just compiled. **One honest caveat kept, not silently dropped:** run #8's overall
job-level success was confirmed directly; the per-test breakdown (that all 9
`InterceptionControllerTest` cases specifically passed, rather than some subset being skipped
or the task reporting 0 tests found) was not independently re-verified against the
`test-reports` artifact GitHub produced for that run. A green job strongly implies this — a
failed test would fail the job — but per this project's own §4/§5.12 standard of not treating
"looks correct" as equivalent to "checked," that artifact is worth a direct look before this
entry's confirmation is treated as airtight rather than "green build page + the same
inference every prior CI-confirmed entry in this doc relies on."

---

### 5.18 — RESOLVED, device-verification finding (not a code bug) — Android Restricted
Settings blocks Accessibility Service toggle on sideloaded builds

**What happened:** first real on-device install (Phase 2's remaining verification step,
§4(c)) succeeded — app installed, no crash, correctly has no launcher icon (no
`MAIN`/`LAUNCHER` intent-filter exists by design, pending Phase 3). But enabling the
Accessibility Service in Settings → Accessibility was blocked: no toggle, no
"Allow restricted settings" option in the app-info three-dot menu either — confirmed absent
on this device (Infinix Hot 40i, XOS).

**Root cause, not app-side:** Android 13+ ships a security feature ("Restricted Settings")
that blocks enabling sensitive permissions — Accessibility Service access chief among them —
for apps installed via direct APK sideload (tapped open from a file manager/downloads
folder), specifically to stop malware from sideloading itself and immediately grabbing
accessibility access. This has nothing to do with `mission_accessibility_service_config.xml`,
the manifest `<service>` entry, or any code in this repo — all of that is correct and was
already confirmed structurally sound by static review before this device test (§ device
verification doc, "pre-flight" section). Some OEM skins (this device's XOS among them, per
this session) additionally strip the standard override UI that stock Android and some
skins expose, making the usual fix ("tap the app info, allow restricted settings") not just
inconvenient but genuinely absent from the UI.

**Fix:** reinstall via a third-party package-installer app (this session used SAI — Split
APKs Installer) rather than tapping the APK directly in a file manager. An app installed
*through* another installer app isn't flagged the same way a direct sideload is, so the
Restricted Settings gate doesn't trigger, and the Accessibility toggle became available
normally afterward. Confirmed working this session.

**Why this is logged here rather than left as a one-off Termux transcript:** anyone else
sideloading this app for manual testing (future contributors, or this same author on a
different device) will hit the identical wall on any Android 13+ device, and the fix is
non-obvious and OEM-dependent enough to be worth a permanent note rather than rediscovering
it. **Not an app-code decision, not something to "fix" in the manifest or service config —
it's a distribution-mechanism fact of the platform**, filed here as decision-log precedent
rather than under Phase 2's engineering exit criteria, since no code change is needed or
possible on this end.

---

### 5.19 — RESOLVED, temporary test-infra change — `DebugSeeder`'s blocklist swapped from
placeholder to a real installed package

**What happened:** §4(c) step 7 (trigger real interception on-device) turned out to be
blocked by an assumption baked into `DebugSeeder`'s original placeholder blocklist entry,
`com.example.blocked` — copied from `InterceptionControllerTest`'s own seeding helper, where
it works fine because Robolectric never actually resolves the foreground package to a real
installed app. On a real device, `MissionAccessibilityService` only ever sees packages that
are actually resumed to the foreground, so a non-installed placeholder package can never be
observed as "opened" — there was no way to manually trigger interception at all with the
original seed data.

**Fix:** `SEEDED_BLOCKED_PACKAGE` changed to `com.dp.logcatapp` (the logcat reader app already
installed on the test device for this same verification pass, and harmless to block — no
other part of this codebase or the verification plan depends on it). `DebugSeederTest`'s
blocklist assertion updated to match. `InterceptionControllerTest` was deliberately left
untouched — its own seeding helper is fully self-contained and never depended on
`DebugSeeder`'s constant.

**Status: temporary, per this file's "explicitly test infrastructure, not silent scaffolding"
standard (see `DebugSeeder.kt`'s class-level kdoc).** Once the three-tier interception walk
(§4(c) step 7, Recruit/Warden/Iron) is complete, revisit: either make the blocked package
configurable (e.g. a build-config field or a second debug-only settings toggle) so it isn't
hardcoded to whatever happened to be installed on one specific test device, or accept the
current hardcoding as fine for its narrow purpose and say so explicitly here rather than
leaving it to be rediscovered as "why is the logcat app blocklisted" later.

---

### 5.20 — RESOLVED, found before ever reaching CI — stale `nextActionId` arguments left in
`onboarding_nav_graph.xml` after the resource-ID-default pattern was abandoned

**What happened:** mid-session, while first drafting the Phase 3 nav skeleton,
`OnboardingPlaceholderFragment` originally took its "next" destination as a passed-in
`Bundle` argument — `<argument android:name="nextActionId" app:argType="integer"
android:defaultValue="@id/action_xxx">` in the nav graph XML, with the Fragment reading it back
via `arguments?.getInt(ARG_NEXT_ACTION_ID)`. This was deliberately dropped before being pushed
or run through CI: there's no existing precedent anywhere in this codebase for resolving a
resource ID via an `<argument>` default value (`MissionInterceptionActivity`'s Intent-extras
pattern, the only real precedent, always passes primitive values it constructs itself, never a
resource ID), and per §4 item 2's standing caution about reasoning past what's actually been
compiler-verified, shipping a nav skeleton's first real test of an untested XML-resolution
trick was the wrong risk to take. The Fragment was rewritten to instead ask
`findNavController()` for its *current destination's* own outgoing actions directly at
click-time — an API shape with real, ordinary precedent in Navigation Component's documented
usage, not a resource-default trick unique to this file.

**The bug:** the Kotlin-side rewrite was done correctly — `OnboardingPlaceholderFragment.kt`
never reads `nextActionId` and has no `ARG_NEXT_ACTION_ID` constant. But the seven
`<argument android:name="nextActionId" ...>` declarations in `onboarding_nav_graph.xml` (one
per destination except `firstMissionSchedulingFragment`, which correctly has none) were never
removed when the Kotlin side changed. This left the exact XML pattern the rewrite existed to
avoid still sitting live in the graph — unread by any code, but still present, and still
carrying the same "genuinely not fully certain this resolves correctly at build time" risk the
rewrite's own reasoning flagged. `firstMissionSchedulingFragment`'s surrounding comment also
still described the Fragment's Next-button-hiding behavior in terms of the old mechanism
("nextActionId is left unset (0)") rather than the new one, which would have misled the next
reader (human or agent) about which mechanism was actually live.

**Caught:** during a manual cross-check pass matching this file's own §4 item 2 standing
discipline (no compiler in the authoring sandbox for Android/Kotlin, so every cross-file
reference gets read against the others by hand before being trusted) — not by CI, since this
was found and fixed before ever being pushed.

**Fix:** removed all seven stale `nextActionId` `<argument>` declarations from
`onboarding_nav_graph.xml`, leaving only the three arguments the Fragment actually reads
(`title`, `stepNumber`, `totalSteps`). Updated `firstMissionSchedulingFragment`'s comment to
describe the actual current mechanism (`findNavController().currentDestination?.actions`
coming back empty) instead of the abandoned one. No Kotlin changes needed — `Onboarding
PlaceholderFragment.kt` was already correct; only the XML had drifted from it.

**Why this is logged even though it never reached CI:** this file's own standard (§0, §6) is
to log a judgment call or a caught mistake when it happens, not just when CI catches it —
CI-confirmed bugs (§5.8, §5.12, §5.16, §5.17) get logged because they were real and costly to
find; this one is logged because it's the same *category* of drift (code and its written
rationale disagreeing about which mechanism is actually live) and leaving it undocumented
would mean the next person has no record that the nav-arg approach was tried, rejected, and
then almost partially un-rejected by an incomplete cleanup.

**Also flagged, not yet decided:** `MainActivity` has no declared `android:theme`, unlike
`MissionInterceptionActivity` (`Theme.NoTitleBar.Fullscreen`, set explicitly). Not a bug —
falls back to the platform default — but worth an explicit decision before real onboarding
screen content goes in rather than staying implicit. Low priority for a skeleton; noted here
so it isn't silently decided either way.

**Status: fixed and pushed; CI ran (`build-and-test` run #12) and did find a real problem —
but not the one this entry anticipated.** `:app:assembleDebug`/`compileDebugKotlin` failed,
correctly, on a *different* line in the same file this entry touched. Removing the stale
`nextActionId` arguments here was not itself the cause — see §5.21 for the actual failure,
which was already latent in `OnboardingPlaceholderFragment.kt`'s `findNavController()
.currentDestination?.actions` lookup (introduced in the same session, before this §5.20 fix,
and not caught by review either). Leaving this status line as originally written ("removing
unread arguments shouldn't change compiled behavior") would misrepresent what actually
happened once real CI ran; correcting it here rather than editing it invisibly is required by
this project's own convention of not quietly resolving mistakes out of the historical record.

---

### 5.21 — RESOLVED, real CI failure (`build-and-test` run #12) — `NavDestination.actions` is
private; §5.20's own "safer" rewrite was itself unverified and wrong

**What happened:** `:app:compileDebugKotlin` failed on push, in
`OnboardingPlaceholderFragment.kt`:
```
e: .../OnboardingPlaceholderFragment.kt:76:66 Cannot access 'actions': it is private in 'NavDestination'
e: .../OnboardingPlaceholderFragment.kt:78:69 Cannot access 'actions': it is private in 'NavDestination'
```
Both call sites were the exact lookup — `findNavController().currentDestination?.actions` —
that had earlier replaced the resource-ID-as-`Bundle`-argument approach specifically because
that approach was judged too uncertain to build a skeleton on without compiler verification
(see this class's own kdoc, prior revision). `NavDestination.actions` turns out to be a
`private` backing property in the Navigation Component version this project pins (2.7.7) —
not part of the public API surface at all, despite reading as ordinary, idiomatic Navigation
Component usage.

**Why this matters beyond "one more bug":** the original resource-default approach was
dropped for being unverified; its replacement was reasoned to be safer specifically because it
used "real Navigation Component API" — but "real API that exists in the library" and "public
API this code is actually allowed to call" turned out to be different claims, and the
difference was never checked against anything compiler-verified before being relied on. This
is the same root failure mode as §5.8, §5.12, §5.16, §5.17 (code that reads as obviously
correct, wrong in a way only a real compiler surfaces) — but notable here specifically because
it happened *inside a fix whose own stated purpose was avoiding exactly that failure mode*.
Worth naming plainly rather than smoothing over: swapping one unverified pattern for another
unverified pattern, and describing the swap as risk reduction, is not actually risk reduction.

**Fix:** `NavDestination` does expose one small, stably-public lookup —
`getAction(actionId: Int): NavAction?`, a single-ID accessor, not the private map — which
means "ask the current destination what its outgoing action is" has no generic public-API
answer at all; something has to supply which action ID to ask about. Rather than reaching for
a third clever lookup with no way to compiler-verify it in this authoring sandbox,
`OnboardingPlaceholderFragment` now hardcodes the eight-destination next-action mapping as a
plain Kotlin `when` on `currentDestination?.id`, matched against the graph's own generated
`R.id.action_*` constants — cross-checked by hand against every `<action>` declared in
`onboarding_nav_graph.xml` (all eight referenced IDs exist in the graph; the graph's ninth
action, `action_tierSelection_to_ironCalibrationGate`, is deliberately unused by this
placeholder, matching the graph's own "always takes the non-Iron path for now" note).
Every API used in the replacement — generated `R.id.*` constants, a `when` expression,
`NavController.navigate(Int)` — has real, already-compiler-verified precedent elsewhere in
this codebase (`MissionInterceptionActivity`, this same class's own `R.id.stepTitleText` etc.),
rather than being trusted on inspection a third time.

**One thing deliberately checked before treating the `if (nextActionId != null)` guard as
safe:** §5.8's smart-cast failure was specific to smart-casting a *cross-module class property*
(`violation.rootCauseClusterId`) — `nextActionId` here is a plain local `val: Int?` computed
once from a `when` expression and immediately null-checked, the standard single-module Kotlin
idiom §5.8's own writeup explicitly says compiles fine. Checked rather than assumed, given this
entry's whole point is not re-trusting "this looks like it should work."

**Status: CONFIRMED GREEN.** `build-and-test` ran again after this fix was pushed and passed —
`:app:compileDebugKotlin` succeeds against the `when`-on-`currentDestination?.id` mapping, and
`:app:testDebugUnitTest` ran clean alongside it. This is the first real run of this specific
fix, not a second run of something already believed to work (§5.12's bar doesn't apply the same
way here as it did to §5.16/§5.17 — this is fix #1's confirmation, not fix #1's re-confirmation),
so nothing further is owed before treating §5.21 as settled. The nav-arg cleanup this entry's
fix sits on top of (§5.20) is still only confirmed via this same run, since both changes shipped
in one commit — no dedicated second run isolating §5.20 alone has happened, but §5.20's change
(deleting unused XML arguments) is lower-risk by construction than §5.21's (swapping which API
is called), so treating them as jointly confirmed by this run is reasonable rather than a gap
worth blocking on.

---

### 5.22 — RESOLVED, confirmed on real CI + real device — Tier Selection (§2.4) and Tier
Confirmation (§2.4) given real content, replacing `OnboardingPlaceholderFragment`

**What changed, concretely:**
- `:data` — `TierEventKind.INITIAL_SELECTION` added. Every other kind on this enum represents
  a transition between two tiers an existing `User` already has; onboarding's first choice has
  no prior tier to transition from. `TierEvent.fromTier` is set equal to `toTier` for this kind
  specifically (documented on both the entity and the use-case method) rather than left null,
  since `fromTier` is non-nullable schema-wide and a real "no prior tier" sentinel would need a
  migration this pass doesn't take on.
- `:domain` — `TierTransitionUseCase.selectInitialTier(userId, tier, onboardingConsentVersion,
  now)` added. Creates the first-ever `User` row (every other method on this class assumes one
  already exists), hard-rejects `Tier.IRON` via `require()` with no exception path (PRD §12.6,
  revised v3.5 — "not selectable at first-time onboarding regardless of stated user intent"),
  writes the `INITIAL_SELECTION` event. Five new tests: happy path, all three legal tiers,
  the Iron rejection, and confirming a rejected Iron attempt leaves no half-created `User` row
  behind (the `require()` fires before the transaction body runs).
- `:app` — two real Fragments:
  - `TierSelectionFragment` — four tiers listed (Iron's `RadioButton` `android:enabled="false"`,
    shown not hidden, per §2.3's "side by side" / §12.6's no-exception-path framing).
    Recruit/Operator call `selectInitialTier()` directly and proceed; Warden routes to
    `TierConfirmationFragment` first, per §2.4's "distinct secondary confirmation screen for
    Warden/Iron specifically."
  - `TierConfirmationFragment` — the §2.4 confirmation screen for Warden. Written to carry a
    `tier` nav argument so it can be reused for Iron's own confirmation once Iron is reachable
    post-calibration, even though nothing routes Iron here yet.
  - Two new layouts, new strings (hand-checked against §4's behavior-vs-identity /
    anti-shaming requirements — no per-tier visual treatment coding Iron as "the real choice").
  - `app/build.gradle.kts` — removed the `androidx.fragment:fragment-testing` dependency,
    which existed only to support an `OnboardingPlaceholderFragmentTest` that was discussed
    across several sessions but never written (§3's prior "known gap" entry). A dependency
    justified by a nonexistent test is the wrong resting state; removed rather than left as
    scaffolding.

**Bug found and fixed before this entry was written, not after:** both new Fragments'
submit paths originally called `UUID.randomUUID()` inline and threw the id away without
persisting it anywhere the rest of the app could read it back — relying entirely on
`UserDao.getSingleLocalUser()`'s `LIMIT 1` for every downstream read (which happens to work at
runtime, since this project is deliberately single-local-user by design, but is fragile and
was undocumented). Concretely: `UserDao.insert()` has no `onConflict` strategy (defaults to
`ABORT`), so a user tapping Back from Mission Profile Setup and re-submitting Tier Selection —
or a slow double-tap on Continue/Confirm before navigation completes — would call
`selectInitialTier()` a second time with a *second* fresh random UUID, which would either abort
on the DB constraint or, if it somehow didn't, leave two `User` rows for `getSingleLocalUser()`
to arbitrarily choose between afterward. Fixed in both `TierSelectionFragment.submitInitialTier`
and `TierConfirmationFragment`'s confirm handler: each now checks
`database.userDao().getSingleLocalUser() == null` before calling `selectInitialTier()`, making
re-entry to either screen idempotent (already-onboarded → just continue forward) instead of
crashing or silently duplicating the user row. This was caught by re-reading the actual call
sites against `UserDao`'s real `@Insert` annotation, not assumed safe because the happy path
looked right.

**What was checked, textually, against real declarations (not eyeballed, actually diffed):**
- Every `R.id.action_*` referenced in the three onboarding Fragments against every `<action
  android:id=...>` declared in `onboarding_nav_graph.xml` — exact match, nothing referenced
  that isn't declared. (`action_tierSelection_to_ironCalibrationGate` exists in the graph but
  is correctly unreferenced from Kotlin — Iron never reaches that branch by construction.)
- Every destination `R.id.*Fragment` referenced against the graph's declared destination IDs —
  exact match — and confirmed `tierSelectionFragment` / `tierConfirmationFragment` now point at
  the real classes (`TierSelectionFragment` / `TierConfirmationFragment`), not
  `OnboardingPlaceholderFragment`.
- Every `@string/*` reference in the two new layouts against `strings.xml`'s actual declared
  `<string name=...>` entries — all 16 references resolve, checked against 28 real declared
  strings (not an empty-vs-empty false pass).
- Every `findViewById<...>(R.id....)` target in both new Fragments against each layout's
  declared `android:id`s — exact match.
- `selectInitialTier`'s real 4-parameter signature (`userId`, `tier`, `onboardingConsentVersion`,
  `now` defaulted) against every call site (both Fragments, all four new tests) — all pass named
  arguments matching the real parameter names, not a stale `toTier` name from an earlier draft
  of this method that never shipped.
- `UserDao.getSingleLocalUser()` and `AppContainer.database(context)` — both real, already-
  existing methods being called correctly, not invented API.

**What this entry did NOT claim when first written, unlike §5.16/§5.17/§5.21 above it:** those
entries say "CONFIRMED GREEN" because a real CI run backed the claim. This one couldn't say
that at the time — the authoring environment this pass ran in has no Android SDK, no Google
Maven access, and no Kotlin compiler on `PATH` (`gradlew` itself fails outside the sandbox
network allowlist, before ever reaching a compile step). Every check listed above was real and
was actually run, but textual cross-referencing is not a compiler, and this project's own
stated lesson (§5.8, §5.16, §5.17, §5.21) is that "reads correct, cross-checked by hand" and
"compiles" are different claims that have diverged before, more than once, in this exact
codebase. Left in place below, unedited, as the accurate record of what was and wasn't known
at commit time — the status line above is what changed, not this reasoning.

**Status: CONFIRMED GREEN — both on CI and on a real device.** `build-and-test` ran clean on
push to a feature branch (not `main` directly, given this pass had zero prior compiler
verification) after the fixes described above landed. Separately, and more strongly than any
CI run alone: manually verified on a physical device — Tier Selection renders correctly (four
tiers visible, Recruit pre-selected as PRD §12.6 requires, Iron shown-disabled with its
calibration-window reason stated in the label itself), Recruit/Operator submit directly to
Mission Profile Setup with no extra friction, and Warden correctly routes through
`TierConfirmationFragment`'s distinct confirmation screen before reaching Mission Profile
Setup — both branches §2.4 requires were exercised, not just the happy-path one. This is the
first onboarding screen in this project to be confirmed both ways (CI + device), not just one.

---

**§5.22 — Mission Profile Setup built; closes a real, previously-undocumented spec gap
(`MissionProfile` never existed as an entity).** Picked up this session per §4's own
"immediate next action" — except that section (and §3's snapshot) was itself stale by the
time this session started: the §5.21 entry immediately above already recorded Tier
Selection/Tier Confirmation as **CONFIRMED GREEN on both CI and device**, but §3/§4 still
described Phase 3 in terms that predated that confirmation and didn't name Mission Profile
Setup as the next real screen at all. Corrected here rather than silently worked around — see
the §3/§4 edits accompanying this entry.

**The gap found, checked before assuming it was real:** `Mission.missionProfileId` /
`mission_profile_id` has existed since Phase 0 (both in code and in Data Model doc §2.2), but
nothing has ever backed it — grepped the entire tree (`data/`, `domain/`, `app/`) for
`MissionProfile`: every single reference across four `:domain` test files, `DebugSeeder`, and
`InterceptionControllerTest` is `UUID.randomUUID()`, never a real row. Checked the Data Model
doc itself too, not just the code — it only ever shows `mission_profile_id: UUID` on the
`Mission` block, never a `MissionProfile { }` block of its own. This is a genuine spec gap
(the schema doc's own §2 never defined the entity it references), not a Phase 0–2 shortcut
this session is only now noticing.

**Built, matching every existing convention checked against real code, not assumed:**
- `:data` — new `MissionProfile` entity (`data/.../entity/MissionProfile.kt`): `id`, `userId`,
  `name`, `allowlist`/`blocklist` (reuses `Converters.fromStringList`/`toStringList`, already
  registered — no new converter needed), `createdAt`. New `MissionProfileDao`
  (`insert`, `get(id)`, `mostRecentFor(userId)` — the last one backing the same re-entry-guard
  pattern `TierSelectionFragment`/`TierConfirmationFragment` already use for `User`, applied
  here to this table instead). Added to `DisciplineOsDatabase`'s entity list, schema bumped
  **v3 → v4** — no migration written, same explicit pre-launch reasoning `DisciplineOsDatabase`
  already states for `fallbackToDestructiveMigration()` (v2, v3) applies unchanged to v4; not
  a new decision, just this bump inheriting it.
- `:app` — `MissionProfileSetupFragment` (Onboarding §2.8), replacing
  `OnboardingPlaceholderFragment` at that one nav-graph destination, same pattern as
  `TierSelectionFragment`/`TierConfirmationFragment`'s own recent replacement of the
  placeholder at their destinations. Collects a name + allowlist + blocklist (one package id
  per line, plain `EditText` — no installed-app picker exists anywhere in this project yet to
  build a real picker against) and inserts one `MissionProfile` row directly via
  `MissionProfileDao`, **not** wrapped in a new `:domain` use-case — a single unconditional
  insert with no other table to coordinate in the same transaction doesn't meet the bar
  `RecordViolationUseCase`/`TierTransitionUseCase` exist for; logged as a judgment call rather
  than silently deciding "always add a use-case" as a blanket rule.
- `onboarding_nav_graph.xml` — `missionProfileSetupFragment` now points at the real Fragment
  class; dropped its placeholder-only `title`/`stepNumber`/`totalSteps` arguments, matching
  what happened to `tierSelectionFragment`/`tierConfirmationFragment` when they got real
  content.
- `OnboardingPlaceholderFragment.kt` — removed the now-dead
  `R.id.missionProfileSetupFragment -> ...` branch from the hardcoded next-action `when` (same
  reasoning already applied there to `tierSelectionFragment`/`tierConfirmationFragment`: a
  destination this class no longer serves shouldn't keep a mapping nothing can ever hit).
- New layout (`fragment_mission_profile_setup.xml`) and 13 new strings, all cross-referenced
  by hand against real declared IDs/strings (see "What was checked" below) — not eyeballed.
- New test file `MissionProfileSetupFragmentTest.kt` — DAO-level coverage (round-trip of
  name/allowlist/blocklist including the empty-list case, `mostRecentFor`'s re-entry-guard
  query) plus direct tests of the Fragment's `parseLines` line-parsing logic, duplicated as a
  private function in the test rather than reflectively invoked (see file's own kdoc for why:
  this module deliberately has no `fragment-testing` dependency — it was removed in the §5.21
  session specifically because it existed only to justify a test that was never written, and
  re-adding it for one more screen would repeat exactly the mistake that removal corrected).

**A real, previously-unflagged gap this pass explicitly did NOT paper over:** Onboarding
§2.8 says this screen "should default to suggestions drawn from §2.2's flagged categories
rather than a blank list, to reduce first-session abandonment." That data doesn't exist —
Goal Definition (§2.2, step 2, two screens earlier in this same sequence) is still
`OnboardingPlaceholderFragment` content, with nowhere to persist a flagged-category list even
if it had real UI. Rather than inventing a plausible-looking default list (which is exactly
the "silently resolving an open question" failure mode §0 of this file names explicitly), the
allowlist/blocklist fields ship empty, with the dependency gap stated in both the layout's own
top comment and this entry. **Not this pass's job to close** — closing it means giving Goal
Definition real content first, which is separate, un-started work (Phase 3 still lists it as
0%, see §3).

**What was checked, textually, against real declarations (same discipline §5.21 and earlier
entries used, not a lighter pass because this felt like a smaller screen):**
- Every `findViewById<...>(R.id....)` target in `MissionProfileSetupFragment.kt` against
  `fragment_mission_profile_setup.xml`'s declared `android:id`s — diffed programmatically,
  exact match, nothing referenced that isn't declared.
- Every `@string/*` reference in the new layout against `strings.xml`'s real declared
  entries — diffed programmatically, all 14 references resolve (13 new + the existing
  `onboarding_placeholder_back`, reused rather than redeclared).
- `R.id.action_missionProfileSetup_to_coreDataConsent` (the one nav action this Fragment
  calls) confirmed still declared in the graph, unchanged by this session's edit to that
  destination's own `<fragment>` block.
- `MissionProfileDao.insert`/`get`/`mostRecentFor` signatures in the Fragment and test file
  both checked against the DAO as actually written in this same pass (not from memory of an
  earlier draft) — named arguments throughout, matching real parameter names.
- `User`'s real 9-field constructor (6 required, 3 defaulted) checked directly against
  `User.kt` before writing the test's `seedUser()` helper — confirmed named-argument call
  supplies all 6 required fields with correct names, relies on defaults for the rest, same
  pattern `DebugSeederTest`/`RecordViolationUseCaseTest` already establish.

**Same standing caveat as every other new file in this project until it's been through real
CI:** manually cross-checked as above, not compiled — no Android/Kotlin compiler in this
authoring sandbox (§4 item 2's standing note, unchanged). Push, let CI confirm
`:data:testDebugUnitTest` and `:app:testDebugUnitTest` both pick up and pass the new test
file and the schema-v4 bump doesn't break anything already-passing, then this note graduates
the same way §5.8/§5.16/§5.17/§5.21 did before it. On-device verification (does the real
screen render, does Continue actually write a row, does the empty-input case behave as
designed) is a separate, still-open step after that — not yet attempted this session.

---

### 5.23 — RESOLVED, written this session, not yet CI-confirmed — Core Data Consent (§2.6)
given real content, replacing `OnboardingPlaceholderFragment`; Welcome (§2.1) and Tier
Explanation (§2.3) merged to `main` from two previously-unmerged branches

**Two unrelated pieces of work landed together this session — recorded separately below.**

**Part one: `onboarding-welcome-screen` and `onboarding-tier-explanation-screen` merged.**
Both branches had already been written and pushed in a prior session (see the Welcome/Tier
Explanation entries in §3's history above) but neither had been merged to `main`, and neither
merges cleanly against the other — both touch `OnboardingPlaceholderFragment.kt` (each removes
a different destination from the routing `when` block), `onboarding_nav_graph.xml` (a
doc-comment listing which destinations have real content), and `strings.xml` (both insert a
new string block at the same anchor point). All three conflicts are mechanical, not semantic —
no string names collide, and the actual `<fragment>`/`<action>` XML tags in the nav graph
auto-merged cleanly with no conflict, only the surrounding doc-comment did. Resolved by
merging both branches into a dedicated branch (`merge-welcome-and-tier-explanation`), keeping
both branches' edits in each of the three conflicting files, then merging that branch to
`main` as PR #10. Verified before pushing: no leftover conflict markers, all touched XML
well-formed, every `R.string`/`R.id`/action reference used by either new Fragment resolves to
a real declaration, onboarding step-progress numbering (1–5 of 8) consistent across both new
screens.

**Part two: Core Data Consent (§2.6).** `CoreDataConsentFragment` replaces
`OnboardingPlaceholderFragment` at `coreDataConsentFragment`. Two content requirements per
spec, both in `strings.xml`: local-storage-is-required (Mission enforcement is core function,
must work offline — Architecture doc §3.1), and a plain-language local-first + optional-
cloud-sync explanation (same §3.1 citation). A third paragraph mentions the local
delete-my-data affordance Architecture doc §3.2's rescoped-for-personal-use section calls out
as the one concrete requirement that section still imposes. Required, not optional — no
decline path, since there's nothing to decline into (matches this destination's own existing
nav-graph comment, "required, not optional like the next screen").

**Closes a gap `TierSelectionFragment`/`TierConfirmationFragment` deliberately left open.**
Both screens run earlier in the flow (screens 4/4a vs. this screen's 6) but
`TierTransitionUseCase.selectInitialTier` requires a non-null `onboardingConsentVersion`
argument at call time, so both wrote a plain placeholder constant
(`"unversioned-pre-consent-copy"`) with an explicit kdoc instruction: bump it once Core Data
Consent has real content, not before. Rather than bump that shared constant from a screen that
doesn't own it, `CoreDataConsentFragment` instead **overwrites** whatever value is on the
user's row with a real version (`CONSENT_VERSION = "v1"`) the moment the user actually reaches
and agrees to this screen's copy — the version now genuinely describes "the consent copy this
user agreed to," which the placeholder never could, since it was written before any such copy
existed. Both tier screens' kdocs updated to point at this resolution instead of describing an
open gap.

**Versioning scheme:** a plain hand-bumped literal, not derived from a hash or resource id —
checked, nothing currently reads or compares `onboardingConsentVersion` programmatically
(display/storage only), so there's no migration logic this scheme needs to satisfy yet. Bump
by hand if this screen's copy is ever materially rewritten.

**No DAO round-trip test file for this screen, deliberately** — the write is a single
unconditional field overwrite with no branch logic (unlike `GoalDefinitionFragment`'s genuine
insert-vs-update split), so a DAO-level test wouldn't catch anything the compiler doesn't
already guarantee. Revisit if this screen ever grows a second write path.

**Same standing caveat as every other new file in this project until it's been through real
CI:** manually cross-checked (XML well-formedness, every resource reference resolves, brace/
paren balance), not compiled — no Android/Kotlin compiler in this authoring sandbox. Push, let
CI confirm, then this note graduates the same way §5.21/§5.22 did before it. On-device
verification is a separate, still-open step after that.

---

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

---

### 5.24 — RESOLVED, written this session, not yet CI-confirmed — First Mission Scheduling
(§2.9) given real content, closing onboarding; STATUS.md branch note corrected a second time

**Part one: STATUS.md correction.** The "Branch note" section and the Unsupervised Reliability
Opt-In row both still said `onboarding-unsupervised-reliability-opt-in` was unmerged — it had
actually been merged to `main` as PR #12 in the session that wrote it. `implement-decided-
follow-ups` and `build-plan-document`, also listed there as unmerged, no longer exist as
remote branches at all (`git branch -r` confirms) — likely already merged and deleted, but not
independently re-verified this pass beyond confirming they're gone. Both corrected in place.
Second time this exact section has needed a correction — worth treating as a standing hazard
for this doc specifically (a narrative note describing branch state ages faster than the
merges it's describing), not a one-off mistake.

**Part two: First Mission Scheduling (§2.9).** `FirstMissionSchedulingFragment` replaces
`OnboardingPlaceholderFragment` at `firstMissionSchedulingFragment` — the last remaining real
screen in the onboarding sequence (Iron Calibration Gate stays a placeholder deliberately, see
row 4b of STATUS.md's screen table). Two actions, per spec's "schedule vs. start-now choice is
itself the first data point for Self-Initiation Trend (§3.6)" language: **Start now** creates a
`Mission` row with `scheduledStart = null`, `status = ACTIVE`, `actualStart = now()`; **Schedule
Mission** takes a plain `yyyy-MM-dd HH:mm` text field (parsed via `DateTimeFormatter`, same
plain-`EditText`-over-a-picker precedent `MissionProfileSetupFragment`'s layout already set)
and writes that parsed time as `scheduledStart` instead. Neither button is styled as the
recommended path — spec is explicit that this choice "doesn't affect this screen's design"
despite being a real measurement, so the screen doesn't nudge either way.

**This is the first real call site that ever sets `Mission.scheduledStart` to anything other
than an implicit always-null placeholder** — every existing Mission-creating code (`DebugSeeder`,
every `:domain` use-case test) has only ever needed `ACTIVE`/`COMPLETED`/`VIOLATED` rows for
scoring-formula testing, never touched this field meaningfully. `Mission.kt`'s own kdoc already
documented the field's intended meaning ("null means ad hoc — feeds Self-Initiation Trend")
before any code actually wrote a non-null value here — this pass is what makes that comment
true in practice, not just in the entity's own documentation.

**Reads the `MissionProfile` `Mission Profile Setup` (§2.8) already wrote** — same
`MissionProfileDao.mostRecentFor(userId)` query that screen's own re-entry guard already
relies on — and carries its `allowlist`/`blocklist`/`id` straight onto the new `Mission` row.
A null profile (should be unreachable via this graph's own ordering) shows an inline error and
creates no row, rather than crashing — same defensive posture every other screen in this
package already takes for its own equivalent "should be impossible, handle gracefully anyway"
case.

**Two judgment calls, both `[HYPOTHESIS]`, both flagged in the Fragment's own kdoc rather than
silently assumed:**
1. **`Mission.plannedDurationMin`** has no spec-mandated value anywhere in §2.9, the Data Model
   doc, or the PRD. This pass hardcodes a fixed default (25 minutes) rather than adding a
   duration picker this screen was never asked to have. Revisit once a real duration control
   exists somewhere upstream (Mission Profile Setup or a future Mission Launch Protocol screen)
   to source this from instead of a bare constant.
2. **Both "Start now" and "Schedule Mission" set `status = ACTIVE` immediately**, including the
   scheduled case, where the Mission hasn't actually started yet at the moment the row is
   created. No status exists in `MissionStatus` for "scheduled, not yet started" — the Data
   Model doc only ever describes `scheduledStart` as a nullable field on the existing
   four-status enum, never as implying a fifth status — so this pass reuses `ACTIVE` rather
   than inventing one unprompted. A real Mission Launch Protocol screen (PRD §7), if/when
   built, would likely need to properly gate a scheduled Mission's actual activation instead of
   this shortcut — noted here so that future work doesn't have to rediscover the gap from
   scratch.

**No re-entry guard, deliberately** — unlike Mission Profile Setup's genuine "don't create a
second Profile" guard, creating a second Mission for the same user on a repeat visit to this
screen is correct behavior (that's what using the app a second time looks like), not a bug to
defend against.

**Test file**: `FirstMissionSchedulingFragmentTest.kt`, same DAO-level round-trip strategy as
every other screen-level test in this package — covers the null-vs-non-null `scheduledStart`
branch, the allowlist/blocklist/missionProfileId carry-over from the existing `MissionProfile`,
the missing-profile edge case, and confirms no re-entry guard blocks a second Mission.

**Same standing caveat as every other new file in this project until it's been through real
CI:** manually cross-checked (XML well-formedness, every `R.layout`/`R.id`/`R.string` reference
in the new Fragment resolves to a real declaration in the new layout/strings.xml additions,
`Mission`/`MissionProfile` constructor calls checked against each entity's actual field order
and types) — no Android/Kotlin compiler reachable in this authoring sandbox (`./gradlew
--version` fails: `services.gradle.org` isn't on this sandbox's allowed network egress list,
same standing gap noted elsewhere in this file). Push, let CI confirm, then this note graduates
the same way §5.21/§5.22/§5.23 did before it. On-device verification is a separate, still-open
step after that — and this screen, being the one that finally exercises the real
Mission-creation path (as opposed to `DebugSeeder`'s synthetic rows), is a meaningfully higher-
value on-device check than most of onboarding's other screens once CI is green.

---

### 5.25 — CONFIRMED — full onboarding sequence (screens 1–3, 5–8) CI-green as of PR #13's
merge to `main`

**No code change this entry** — this is the CI-confirmation graduation §5.23/§5.24 each
predicted would happen "once CI confirms it." The person confirmed PR #13 (First Mission
Scheduling, §5.24) built cleanly. Checked `git log origin/main` directly rather than taking
that at face value alone: PR #13 is merged (`54deb99`), and `build-and-test.yml` runs the full
`:app`/`:data`/`:domain` compile-and-test suite (not just the diff) on every push to `main` —
so this one green run confirms every onboarding screen currently in the tree, not only First
Mission Scheduling's own files.

**Practical effect:** every screen previously marked 🟡 "written, not yet CI-confirmed" purely
for lack of a real compiler in the authoring sandbox — Welcome (§2.1), Goal Definition (§2.2),
Tier Explanation (§2.3), Mission Profile Setup (§2.8), Core Data Consent (§2.6), Unsupervised
Reliability Opt-In (§2.7), First Mission Scheduling (§2.9) — graduates to 🟢 in STATUS.md's
tables this same pass. This does **not** upgrade any of them to ✅ — on-device verification is
a separate, still-open step for all of them except Tier Selection/Confirmation (§2.4/4a), which
were already device-checked in an earlier session. Two related MVP-table rows (Reputation
decay-based demotion, §5.9; Mission Profiles) also graduate 🟡→🟢 for the same reason — both
had code sitting in the same CI-confirmed tree with no separate compilation boundary of their
own.

**What this does not resolve:** the two `[HYPOTHESIS]` judgment calls §5.24 flagged (default
Mission duration; reusing `ACTIVE` for a scheduled-but-not-started Mission) are still open —
CI confirms the code compiles and its own tests pass, not that either judgment call is the
right one. Those remain real open items, not closed by this entry.

### 5.26 — RESOLVED, written this session, not yet CI-confirmed — Design system (Color/Type/Theme,
Compose scaffolding) + First Mission Scheduling migrated to Compose as proof-of-concept

**Written concurrently with, and rebased onto, §5.25's CI-confirmation entry above** — this
entry was authored in a session that branched from `main` before PR #13 (§2.9) had gone through
its own CI-confirmation pass, so it inherits §5.25's confirmation for everything up through
First Mission Scheduling's original (pre-Compose) content. The Compose migration below is new
code on top of that already-confirmed baseline, and is itself not yet CI-confirmed — same
standing caveat as every entry before §5.25 graduated.

**Why now, not "a follow-on doc" indefinitely:** Onboarding spec §5 explicitly defers "visual
design system, color, typography" as a follow-on, not blocking engineering. It was never blocked
— it just hadn't been done. Separately, and more forcingly: Google announced at I/O 2026 that the
Views-based UI toolkit (`android.widget`, including MDC-Android specifically) is now in
maintenance mode, with the platform officially Compose-first going forward — verified this
session against developer.android.com directly (not secondhand), not just the MDC-Android repo's
own README. This project's existing plain-View screens (`activity_mission_interception.xml`'s own
comment: "no Compose dependency exists yet... no framework not already justified") were a
reasonable choice at the time; that comment is now stale, not wrong for when it was written —
recorded honestly rather than silently contradicted.

**Decision: adopt Compose, migrate incrementally, scope to onboarding only.** Considered and
rejected: (a) staying on Views + Material Components indefinitely — still technically works
("maintenance mode" means no new features, not broken — bugs still get fixed), but every future
onboarding screen built on it is debt paid down later on a pre-launch app with zero real users,
which is exactly when migration cost is lowest; (b) a full rewrite of the whole app in one pass —
rejected as too large a single change, and Phase 2 (interception/accessibility-service screens)
is meaningfully gnarlier to move than onboarding's plain Fragment+DAO screens. Landed on: Compose
for onboarding going forward, migrated one screen at a time via `ComposeView` hosted inside the
existing Fragment/Jetpack-Navigation shell — the officially recommended incremental strategy
(developer.android.com/develop/ui/compose/migrate/strategy, checked this session), not a
big-bang rewrite. Phase 2 stays on plain Views, untouched by this pass, migrated later as its own
deliberate decision if ever.

**Version pinning, checked against real compatibility docs, not assumed:** this project stays on
Kotlin 1.9.24 / AGP 8.5.2 / compileSdk 34 (no bump bundled into this design-system change). Compose
Compiler pinned to `1.5.14` — verified via Google's own release notes ("this compiler release is
targeting Kotlin 1.9.24") rather than guessed, using the legacy `composeOptions {
kotlinCompilerExtensionVersion }` approach since Kotlin < 2.0 predates the newer Compose Compiler
Gradle plugin model. Compose BOM pinned to `2024.09.00` — the BOM current when compileSdk 34 was
the norm; later BOMs (1.12.0/2026.04+) require compileSdk 37+, which this pass deliberately does
not force.

**Color.kt / Type.kt / Theme.kt** (`app/src/main/java/com/disciplineos/app/ui/theme/`) — the
actual design system, grounded in spec text checked directly this session, not paraphrased from
a prior summary:
- Onboarding spec §2.3's explicit anti-pattern ("do not visually code Iron as the real/serious
  choice and Recruit as for beginners") and §3.5's explicit rejection of red/yellow severity
  coding across Predictive Failure Alerts drove a hard rule: no tier/severity color-coding
  anywhere in this palette, one deliberate accent color total, no red/yellow/green role at all
  (`Error` is a desaturated terracotta, reserved for genuine destructive/validation-failure UI
  states, never for coding a Mission/tier/violation as more or less serious).
- The single accent (`#B08D57`, muted brass/ochre) is deliberately low-saturation — reads as an
  instrument-panel dial, not a brand color or gamified "tap here" CTA — consistent with §3.4's
  "progress-toward-a-boundary, not gamified reward-progress" language for Debt Ceiling markers,
  and with the PRD's own stated identity ("exactly as strict as the person needs, no stricter
  than consented to"). Re-reading the actual onboarding copy (`welcome_tone_body` etc.) this
  session confirmed the app's dominant voice is calm and plain-spoken even when disclosing
  confrontational higher-tier behavior — the visual identity should be restrained by default to
  match, not aggressive-by-default the way a naive "discipline enforcer" reading might suggest.
- M3's tone-based `surfaceContainer*` ladder used throughout (not a single flat "card background"
  color) — current M3 guidance (verified this session) replaced M2's opacity-based elevation
  overlays with this model.
- Every text-on-fill color pairing checked against the real WCAG 2 contrast formula (relative
  luminance), computed with a standalone script this session rather than eyeballed or asserted —
  see Color.kt's kdoc table for the actual ratios. One real finding from that check: the initial
  `Outline` color (`#5B5F68`) measured 2.93:1 against `Background`, short of the 3:1 UI-component
  threshold — bumped to `#6B6F78` (4.12:1) before use, not shipped at the failing value.
- Two system fonts only (`FontFamily.Monospace` for numeric/instrument-style content — tier
  labels, step counters; `FontFamily.SansSerif` for everything else) — zero bundled font files,
  a deliberate v1 simplification noted as such, not a silently skipped step.
- Dark-only for this pass, no dynamic color (Material You wallpaper-derived theming) — both
  logged as deliberate scope limits in Theme.kt's kdoc, not silent gaps: a light palette would
  need its own contrast pass, and dynamic color could inject an arbitrary wallpaper-derived hue
  that undermines the anti-severity-coding constraint above.

**FirstMissionSchedulingFragment/Screen migrated as the proof-of-concept.** Chosen because it's
onboarding's newest, most isolated screen (§5.24, same session line of work) — no deep nav
dependents, no shared cross-screen state. `FirstMissionSchedulingScreen.kt`
(`ui/onboarding/`) is presentation only; every line of actual business logic (`parseScheduledTime`,
`createMissionAndFinish`, the Mission-creation semantics §5.24 already reasoned through) stays in
the Fragment, completely unchanged — this migration swaps the rendering layer, not what the
screen does. Fragment now hosts the composable via a single `ComposeView` +
`ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` (the officially documented pattern
for ComposeView-in-Fragment, verified this session) rather than inflating XML. §2.9's "doesn't
affect this screen's design" requirement carried over unchanged: Start Now and Schedule Mission
are both `Button` (equal visual weight), neither demoted to a lower-emphasis style, so neither
reads as the recommended path.

`fragment_first_mission_scheduling.xml` is left in place, marked unreferenced in its own top
comment, rather than deleted in this same commit — cleanup of now-dead XML layouts left behind by
the Compose migration is tracked as its own future step (see STATUS.md's known standing gaps), so
it doesn't get interleaved with this migration's own diff.

**Same standing caveat as every other new file in this project until it's been through real CI:**
manually cross-checked this session — every touched/added XML file re-verified well-formed via
Python's `xml.etree.ElementTree`, every new/edited Kotlin file brace/paren-balance-checked, every
`R.string.*` reference in the new Compose screen confirmed to resolve against `strings.xml`'s
actual declarations, `darkColorScheme()`'s parameter names (including the newer
`surfaceContainerLowest`/`surfaceContainerLow`/etc. roles) checked against current API
documentation rather than assumed from memory. No Android/Kotlin compiler reachable in this
authoring sandbox (`./gradlew --version` still fails: `services.gradle.org` isn't on this
sandbox's allowed network egress list, same standing gap noted throughout this file) — push, let
CI confirm, then this note graduates the same way prior entries have.

**Real follow-up work this pass deliberately did not attempt, flagged rather than silently
deferred:**
1. Only one screen (First Mission Scheduling) migrated. The other seven onboarding screens still
   render their existing XML layouts unchanged — next sessions should migrate them incrementally,
   a few at a time, verified at each step, matching this project's own established "small,
   reviewable, one-concern-per-PR" convention (see §5's own prior entries on Batch B screens).
2. `fragment_first_mission_scheduling.xml` (and, as more screens migrate, their XML layouts) is
   dead code, marked but not removed — a real, tracked cleanup gap, not an oversight.
3. No light theme, no dynamic color — both explicit scope cuts, not gaps, per Theme.kt's kdoc;
   revisit only on a deliberate future ask.
4. `MainActivity`/`activity_main.xml` (the `NavHostFragment` container) was not touched — it stays
   a plain Activity theme (`@android:style/Theme.NoTitleBar.Fullscreen`, unchanged); nothing in
   this pass wires `DisciplineOsTheme` at the Activity level, since each migrated Fragment applies
   its own `DisciplineOsTheme { ... }` wrapper independently. Worth revisiting once most/all
   onboarding screens are migrated, to theme once at the Activity level instead of per-screen —
   not done here to keep this pass's diff minimal and screen-scoped.

---

### 5.27 — CONFIRMED — remaining 8 onboarding screens migrated to Compose (PR #16) and, together
with First Mission Scheduling, confirmed CI **and** on-device

**Closes the follow-up §5.26 itself flagged as item 1: "next sessions should migrate them
incrementally."** This entry is that migration, done in one PR rather than several smaller ones
— the eight screens share one established pattern (ComposeView +
`DisposeOnViewTreeLifecycleDestroyed`, `DisciplineOsTheme` wrapper per Fragment, business logic
left untouched in the Fragment) that First Mission Scheduling's proof-of-concept already proved
out, so splitting further would have meant re-reviewing the same pattern eight times rather than
once. Migrated: Welcome (§2.1), Goal Definition (§2.2), Tier Explanation (§2.3), Tier Selection
(§2.4), Tier Confirmation (§2.4 Warden path), Mission Profile Setup (§2.8), Core Data Consent
(§2.6), Unsupervised Reliability Opt-In (§2.7).

**Verification method, stated plainly since it differs from most prior entries in this file:**
this branch's own author (the AI assistant that wrote the migration) had no reachable Android
compiler in its sandbox — same standing gap as §5.26 and every entry before it. Static checks
were run (XML well-formedness, Kotlin brace balance, every `R.string.*`/`R.id.*` reference
cross-checked against `strings.xml`/`onboarding_nav_graph.xml`, and old-vs-new business-logic
function names diffed 1:1 to confirm nothing was dropped in the rewrite), but none of that is a
substitute for a real compile. **The person then built the branch through real CI and confirmed
it on-device themselves** — that confirmation, not the static checks, is what this entry
actually records. Recorded exactly that way rather than letting the static-check language imply
more than it does.

**One correction to how this session started:** a prior session's own transcript claimed this
same 8-screen migration was finished, verified, and ready to push — it was not. `git log
origin/main` showed no trace of any of it; everything from that session had stayed in an
ephemeral sandbox and was lost when it ended. This entry's work started over from the person's
uploaded files (the one surviving artifact of that prior session), not from an assumption that
the transcript's summary was accurate. Worth remembering generally: a session's own closing
summary is not itself evidence a push happened — check `git log`/`git branch -r` directly, the
same standing advice this file and `STATUS.md`'s branch notes have repeated after being burned
by exactly this before (see the branch-note corrections earlier in this file and in
`STATUS.md`).

**Practical effect:** every onboarding screen (1–3, 5–8, per `STATUS.md`'s numbering) plus First
Mission Scheduling (8) graduates to ✅ (CI + on-device confirmed) in `STATUS.md`'s tables this
same pass — the first time the *entire* onboarding sequence has reached that mark together,
not just CI-green. Only Iron Calibration Gate (row 4b) remains a placeholder, deliberately.

**What this does not resolve:** the two `[HYPOTHESIS]` judgment calls §5.24 flagged for First
Mission Scheduling (default Mission duration; reusing `ACTIVE` for a scheduled-but-not-started
Mission) are unaffected by this migration or its confirmation — device testing exercised the
happy path of an already-decided implementation, it didn't revisit either open judgment call.
Also unresolved: the 9 now-dead XML layouts this migration (and §5.26 before it) leaves marked
UNREFERENCED but not deleted, and `DisciplineOsTheme` still being applied per-Fragment rather
than once at the Activity level — both explicitly deferred, tracked as their own follow-up items
in `STATUS.md`'s "what's actually next," not silently dropped.

---

### 5.28 — RESOLVED — 9 now-dead onboarding XML layouts deleted

**Closes STATUS.md's "what's actually next" item 1, queued in §5.27 pending confirmation.**
§5.27's own confirmation (CI + on-device, all 9 onboarding screens on Compose) is exactly the
condition that entry set for doing this — so this pass deletes rather than defers further:

- `fragment_welcome.xml`
- `fragment_goal_definition.xml`
- `fragment_tier_explanation.xml`
- `fragment_tier_selection.xml`
- `fragment_tier_confirmation.xml`
- `fragment_mission_profile_setup.xml`
- `fragment_core_data_consent.xml`
- `fragment_unsupervised_reliability_opt_in.xml`
- `fragment_first_mission_scheduling.xml`

Each had been left in place through §5.26/§5.27, marked UNREFERENCED in its own top comment,
specifically so the Compose migration's diff stayed easy to review without interleaving unrelated
deletions — see either entry's own reasoning. That reason no longer applies once the migration
itself is fully confirmed, so keeping the files around any longer would just be dead weight, not
a real safety margin.

**Checked before deleting, not assumed:** grepped the full tree for `R.layout.<name>`,
`layout/<name>.xml`, and `@layout/<name>` references across every `.kt`/`.xml` file. Found one
live reference — `TierExplanationFragment.kt`'s own kdoc still pointed at
`[R.layout.fragment_tier_explanation]` as the place enforcing §2.3's anti-severity-coding
structural guarantee, stale since that screen's own §5.27 migration moved the actual guarantee
into `TierExplanationScreen.kt`'s shared `TierCard` composable. Updated that kdoc to point at the
real current source rather than leaving a dangling reference to a file this entry deletes.
`fragment_onboarding_placeholder.xml` was deliberately left alone — still live, still backing
`OnboardingPlaceholderFragment` at the one destination that hasn't been given real content
(Iron Calibration Gate, row 4b), not part of this cleanup.

**Not touched:** `strings.xml`. All string resources the deleted layouts once referenced (via
`android:text="@string/..."`) are still actively read by the Compose screens that replaced them
(via `stringResource(R.string....)`), so nothing here is orphaned — confirmed by the same
string-resolution check §5.27 already ran, re-run against the post-deletion tree.

**Same standing verification caveat as every other entry in this file:** no Android/Kotlin
compiler reachable in this authoring sandbox. Layout XML across the tree re-verified well-formed
via Python's `xml.etree.ElementTree` after deletion; the one kdoc edit above is prose, not a
code change, and doesn't affect compilation either way. Push, let CI confirm, then this note
graduates the same way prior entries have.

---

### 5.29 — RESOLVED — DisciplineOsTheme deduplicated behind a single themedComposeView helper

**Closes STATUS.md's "known standing gaps" DisciplineOsTheme note and `What's actually next`
item 1, queued in §5.28 pending item 1's own confirmation.**

**Investigated the literal ask first, before writing any code.** "Move DisciplineOsTheme to the
Activity level" implies an Activity-level `setContent { }` to hoist the theme into. Checked
`MainActivity` and `activity_main.xml` directly: `MainActivity` is a plain `FragmentActivity`,
not `ComponentActivity`/`AppCompatActivity`, and hosts its `NavHostFragment` declaratively via
`app:navGraph` in XML — there is no Activity-level Compose tree anywhere in this app to hoist a
theme wrapper into. Each of the 9 onboarding Fragments independently creates its own
`ComposeView`. A literal "Activity-level theme" would require replacing Jetpack Navigation's
Fragment-based graph with Compose Navigation — a materially bigger, riskier change (touches
every Fragment, the nav graph, and needs its own full on-device re-verification pass) than what
this item was ever scoped to be, and out of proportion with this project's own "small,
reviewable, one-concern-per-PR" convention.

**Right-sized fix, given the real architecture:** deduplicate the 9 identical
`ComposeView(requireContext()).apply { setViewCompositionStrategy(...); setContent { DisciplineOsTheme { FooScreen(...) } } }`
blocks — byte-identical boilerplate repeated across every onboarding Fragment (§5.26 established
the pattern with First Mission Scheduling, §5.27 repeated it 8 more times) — into one shared
`Fragment.themedComposeView { }` extension function, `ui/theme/ComposeFragment.kt`. This is the
practical equivalent of "one place the theme is applied" given this app's Fragment-hosted-Compose
architecture: still invoked per-Fragment, but from exactly one definition, matching this
project's own "add shared structure once a real second/third call site shows up" convention —
9 call sites is well past that bar.

**Behavior-preserving, not a refactor with side effects.** `themedComposeView` reproduces the
exact `ComposeView` construction, `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`
setting, and `DisciplineOsTheme { }` wrapping every migrated Fragment already used — confirmed by
diffing each Fragment's `onCreateView` before and after the transform, not just by inspection.
Applied by hand, file by file (not a blind regex/script pass), since several Fragments have
non-trivial content inside the lambda (`UnsupervisedReliabilityOptInFragment`'s `logViewedEvent()`
call before the return; `TierSelectionFragment`'s conditional `onContinue`;
`FirstMissionSchedulingFragment`'s `Toast`/`getString` calls inside the callback) that a naive
transform could have mangled.

**A second, speculative `themedComposeView(layoutRes)`-style overload for XML-hosted screens was
considered and dropped** — zero real call sites exist for it (`OnboardingPlaceholderFragment` is
the only remaining XML-based Fragment, and it isn't Compose-hosted at all), so adding it now
would be exactly the premature structure this project's own convention argues against.

**Files touched:** `ui/theme/ComposeFragment.kt` (new), and all 9 onboarding Fragments'
`onCreateView` + imports (`WelcomeFragment`, `GoalDefinitionFragment`, `TierExplanationFragment`,
`TierSelectionFragment`, `TierConfirmationFragment`, `MissionProfileSetupFragment`,
`CoreDataConsentFragment`, `UnsupervisedReliabilityOptInFragment`,
`FirstMissionSchedulingFragment`). `OnboardingPlaceholderFragment` untouched — still XML-based,
not part of this migration. No `MainActivity`/`activity_main.xml` change — see the investigation
above for why that turned out not to be the right-sized fix.

**Not touched:** any business logic. Every `submitX`/`recordX`/`createX` method, every DAO call,
every re-entry guard is byte-identical to before this pass — this is presentation-layer
deduplication only, same posture §5.26/§5.27 already took for the original per-screen migration.

**Same standing verification caveat as every other entry in this file:** no Android/Kotlin
compiler reachable in this authoring sandbox. Verified manually: every fragment still imports
`themedComposeView` and no fragment retains the old inline `ComposeView`/`ViewCompositionStrategy`
/`DisciplineOsTheme` imports or construction in live code (kdoc prose describing the old pattern,
for historical record, is expected and left alone). Push, let CI confirm, then on-device check
before this note graduates the same way §5.27's did.

---

### 5.30 — RESOLVED — Mission Profile Setup's blocklist now defaults to Goal Definition's flagged categories

**Closes STATUS.md's "what's actually next" item, queued since Goal Definition (§2.2) shipped
real content — this item was explicitly left unblocked-but-undone in
[MissionProfileSetupFragment]'s own kdoc pending this pass.**

**§2.8's actual requirement, re-read before wiring anything:** "should default to suggestions
drawn from §2.2's flagged categories rather than a blank list, to reduce first-session
abandonment." Re-checked §2.2's own text too, since "flagged categories" undersells what that
screen actually asks for: its spec line calls the same field "high-value" *and* "high-risk"
apps/categories — a deliberately mixed bag — but `GoalDefinitionScreen`'s shipped
implementation (an earlier pass, unchanged by this one) already collapsed that into one
undifferentiated free-text field, and `User.flaggedCategories` is a single `List<String>` with
no column distinguishing which flagged entry was meant as which.

**Decision: pre-fill blocklist only, not allowlist, and not a heuristic split of one list into
both.** No data in this codebase says which flagged category a given user meant as "protect
this" vs. "restrict this" — inventing a split (e.g. keyword-guessing "social media" as
high-risk) would be exactly the kind of invented behavior this project's own convention argues
against, and would risk silently mis-suggesting a high-value entry into the blocklist. The
categories field's own hint text (`"social media", "news", "games"`) and this app's whole
premise (restricting distractions during a Mission) both point toward blocklist as the honest
target. Allowlist starts empty, same as before this pass — not touched.

**Still just a starting point, not a locked default.** The suggestion pre-fills the blocklist
`OutlinedTextField`'s value; nothing prevents the user from clearing or editing it like any
other field content, matching §2.8's own "reduce first-session abandonment" framing (a
starting point removes the blank-page problem; it isn't meant to remove the user's choice).

**Async-load wiring, not a Compose antipattern shortcut.** `MissionProfileSetupFragment.onCreateView`
must return a `View` synchronously — it can't `await` the DB read before building the
composable. `loadSuggestedBlocklist()` kicks off the read via the same `lifecycleScope.launch`
pattern every other Fragment method in this package already uses, and pushes the result into a
`mutableStateOf` the Fragment owns; `MissionProfileSetupScreen` observes it via
`LaunchedEffect(suggestedBlocklist)`, applying it exactly once (a `hasAppliedSuggestion` guard
stops a later recomposition from re-firing the effect and clobbering a user's own edits made in
the meantime). This is the first `LaunchedEffect` usage anywhere in this codebase — checked, no
prior precedent to match against, so this follows Compose's own documented idiom for "external
async value populates local UI state" rather than inventing a different pattern.

**A small "pre-filled from the categories you flagged earlier" note** is shown under the
blocklist field, but only once the suggestion is actually applied (not just whenever the param
is non-empty) — makes the source of the pre-filled text visible, matching §2.2's own
"interaction detail" requirement (the same "make the link visible" precedent that screen's
`goal_definition_link_note` string already established) rather than leaving the user to wonder
where the text came from.

**Files touched:** `MissionProfileSetupScreen.kt` (`suggestedBlocklist` param,
`LaunchedEffect`, conditional note), `MissionProfileSetupFragment.kt`
(`loadSuggestedBlocklist()`, `mutableStateOf` wiring, updated kdoc), `strings.xml` (one new
string, `mission_profile_setup_blocklist_suggested_note`). `parseLines`, the `MissionProfile`
insert, and its re-entry guard are all unchanged — this pass only affects what the blocklist
field starts with, not what gets persisted or how.

**Same standing verification caveat as every other entry in this file:** no Android/Kotlin
compiler reachable in this authoring sandbox. Verified manually: brace/paren balance on both
touched `.kt` files, `strings.xml` well-formedness. Push, let CI confirm, then on-device check
before this note graduates the same way prior entries have — the `LaunchedEffect` timing in
particular is worth confirming visually (does the suggestion actually appear, and does the
note only show when it should) since it's this codebase's first use of that API.

---

### 5.31 — RESOLVED 2026-08-10, written this session, not yet CI-confirmed — Post-onboarding
Home shell + real Iron Calibration flow (`TierTransitionUseCase.activateIron` finally has a
call site)

**What this closes:** STATUS.md's "what's actually next" item 2 — "Build the Iron Calibration
Gate's real destination flow... not the onboarding-time placeholder (correctly unreachable by
design), but the actual 'existing user reaches Iron later via `TierTransitionUseCase.activateIron`'
flow." Tracing that item surfaced a bigger, previously-undocumented gap: there was no
post-onboarding destination of *any* kind. `firstMissionSchedulingFragment` (onboarding's last
screen) declared no outgoing `<action>` at all, and `MainActivity` only ever ran
`onboarding_nav_graph.xml` — a user who finished onboarding had nowhere to land. Confirmed via
`onboarding_nav_graph.xml`'s pre-this-pass content directly, not inferred.

**Scope decision, made explicitly rather than assumed:** given that finding, "build the Iron
Calibration Gate's real destination" is really two things — (a) a minimal post-onboarding home
shell to land on at all, and (b) the Iron Calibration screen hosted in it. Confirmed with the
product owner to build both, not just (b) with a bare nav stub — see this session's own
back-and-forth before any code was written.

**Four new files, all in the same session:**
- `ui/home/HomeScreen.kt` / `home/HomeFragment.kt` — the minimum post-onboarding shell. Shows
  current tier; if the user isn't already Iron, an "Iron" card shows either a days-remaining
  countdown or an "eligible now" call to action, computed via
  [`ironCalibrationSatisfied`][com.disciplineos.data.metrics.ironCalibrationSatisfied] — the
  same pure function `activateIron()` itself gates on, reused rather than re-derived, so the
  two can't drift. Deliberately *not* a full dashboard — Daily/Weekly Reports, Reliability
  Index reporting UI, etc. all stay `⬜` per STATUS.md; this is only the entry point the Iron
  flow needed to exist somewhere.
- `ui/home/IronCalibrationScreen.kt` / `home/IronCalibrationFragment.kt` — calls
  `TierTransitionUseCase.activateIron()` directly. Its `IllegalStateException` (gate not yet
  satisfied — a real, expected outcome per that method's own kdoc's "no exception path"
  language, not a bug) is caught and rendered as a real UI state with a freshly-recomputed
  days-remaining figure, not swallowed or left to crash.

**Nav graph changes:** `onboarding_nav_graph.xml` gains `homeFragment` and
`ironCalibrationFragment` destinations, plus a new outgoing `action_firstMissionScheduling_to_home`
from onboarding's last screen — with `popUpTo`/`popUpToInclusive` clearing the entire onboarding
back-stack on arrival, since Back from Home returning into a finished onboarding sequence would
be wrong. Deliberately still one graph, one `NavHostFragment` — no second graph or Activity
introduced, matching this project's existing single-NavHost pattern rather than adding
structure the app doesn't need yet.

**`ironCalibrationGateFragment` (the onboarding-time placeholder) is untouched, on purpose.**
It stays unreachable by design (Iron's RadioButton is disabled at Tier Selection itself, §12.6)
— this pass builds the *different* flow that destination's own long-standing comment already
said didn't exist yet ("that flow is a Tier Selection outside onboarding, which this graph
doesn't model at all"), rather than repurposing the placeholder destination itself. Both the
graph's top-of-file comment and that destination's own comment are updated to say so explicitly,
so a future reader doesn't have to re-derive which of the two "Iron Calibration"-named things is
which.

**Stale kdoc corrected in the same pass, not left to rot further:** `MainActivity.kt` and
`activity_main.xml`'s comments still described every onboarding destination as
`OnboardingPlaceholderFragment` — untrue since PR #16 (per STATUS.md), and now additionally
missing the new Home/Iron Calibration destinations. Both corrected in place, with an explicit
"Corrected, this pass" marker in `MainActivity.kt` so a future reader isn't left to wonder
whether the correction itself is current.

**A real bug caught and fixed before this entry was written, not after:** the nav-graph action
was added first, but `FirstMissionSchedulingFragment.createMissionAndFinish` was never actually
updated to call `findNavController().navigate(...)` — an action that exists in the graph but
that no code fires is exactly as dead as no action at all. Caught by re-reading the Fragment
after wiring the graph, not by a compiler (none reachable here) — worth noting as a concrete
example of why this file's standing "no compiler in this sandbox" caveat matters in practice,
not just as a disclaimer.

**Tests:** `HomeFragmentTest.kt` — pure-function coverage of `computeHomeState` (null user,
already-Iron, no-tier-selected-yet, mid-window, window-elapsed, exact-boundary cases), no
Robolectric needed since the function takes plain `User?`/`Instant` and returns a plain data
class. `IronCalibrationFragmentTest.kt` — DAO-level coverage of the Fragment's wrapper logic
specifically (the `activateIron()` catch branch and days-remaining recomputation), same
Robolectric-plus-in-memory-Room strategy as `TierTransitionUseCaseTest`; deliberately does not
re-assert what that use-case-level test file already covers for `activateIron()`'s own gate
logic in isolation.

**Files touched:** `ui/home/HomeScreen.kt`, `home/HomeFragment.kt`, `ui/home/IronCalibrationScreen.kt`,
`home/IronCalibrationFragment.kt` (all new), `onboarding_nav_graph.xml` (new destinations +
actions, updated top comment), `FirstMissionSchedulingFragment.kt` (navigates onward now,
corrected kdoc), `MainActivity.kt` / `activity_main.xml` (corrected stale comments),
`strings.xml` (13 new strings), `HomeFragmentTest.kt` / `IronCalibrationFragmentTest.kt` (new).

**Same standing verification caveat as every other entry in this file:** no Android/Kotlin
compiler reachable in this authoring sandbox. Verified manually this pass: every new
`R.string`/`R.id` reference cross-checked against `strings.xml`/`onboarding_nav_graph.xml`
directly (not assumed), nav-graph XML confirmed well-formed via `xml.dom.minidom`, `User`
constructor field names in both new test files cross-checked against `User.kt` directly. Push,
let CI confirm, then on-device check before this note graduates — the Iron path in particular
has never been on-device exercised at all before this pass (STATUS.md's Phase 1 row already
flags "Iron path unexercised on-device"), so that on-device check is a meaningfully higher-value
one than most of onboarding's own screens got.

### 5.32 — RESOLVED 2026-08-11, docs-only pass, no code — Goal-Oriented Mission Model accepted
and folded into the spec docs

**What this closes:** four external documents (a base design doc, an integration plan, and two
now-superseded earlier drafts) describing a structural rework of `Mission` into a
`GoalMission`/`EnforcementSession` split, plus three new supporting entities
(`MissionPeriod`, `MissionLogEntry`, `Trigger`/`Milestone`). Per the base document's own §0
instruction ("once accepted, the relevant pieces fold into `DisciplineOS_PRD_v3_6.md` and
`01_DATA_MODEL_AND_SCHEMA.md` directly and this file is reduced to a historical pointer at the
decision log entry that replaces it") — this entry is that decision log entry.

**Explicitly scoped as docs-only, per product-owner instruction this session:** no code was
written. This is the same "design doc → sign-off → `BUILD_PLAN.md` batch → code" sequencing
this project already uses elsewhere (see Batch A's own "no code, no branch of its own
conceptually" framing) — Batches G1–G6 now exist in `BUILD_PLAN.md` as real, sequenced,
dependency-ordered work items, but none of them have started.

**Verified against the live tree before folding anything in, not taken on faith from the
uploaded documents:** cross-checked `data/src/main/java/com/disciplineos/data/entity/Mission.kt`,
`CoreDaos.kt`, `DisciplineOsDatabase.kt`, `RecordViolationUseCase.kt`,
`ApplyReputationDecayUseCase.kt`, `LedgerEntry.kt`/`LedgerDao.kt`,
`FirstMissionSchedulingFragment.kt`, `HomeFragment.kt`/`HomeScreen.kt`,
`Converters.kt`, `MissionProfile.kt`, `AppContainer.kt`, and every call site the uploaded
Integration Plan claimed to have found (`InterceptionController`, `MissionAccessibilityService`,
`MissionInterceptionActivity`, `ComputeBehavioralFingerprintUseCase`, `TierTransitionUseCase`,
`DebugSeeder`) directly, one by one, via `grep`/`view` rather than trusting the uploaded
document's own claims unverified. **One gap found in the uploaded Integration Plan's own
call-site inventory, corrected here rather than silently carried forward:** `AppContainer.kt`
(the app's DI wiring) also needs the `MissionDao`→`EnforcementSessionDao` rename and doesn't
appear in the uploaded plan's file-by-file list — noted in `BUILD_PLAN.md`'s Batch G1 scope so
it isn't missed when that batch is actually built. Also confirmed `ResolveDisputeUseCase.kt`
does **not** reference `Mission`/`MissionDao` at all (only its test file does), correcting an
inference that could otherwise have added an unnecessary call site to G1's scope.

**A live-repo fact this pass surfaced that the uploaded documents didn't have:** the current DB
version, per `DisciplineOsDatabase.kt`, is v7 as of this pass — Batch G1's migration needs to be
written against whatever version is actually current *when G1 is built*, not against v7 as a
fixed number, since other batches (B's three-way v5 collision is the standing cautionary
example) can and do bump the version in the meantime.

**A pre-existing `STATUS.md` "known standing gap" corrected while touching adjacent docs:**
that file's "Known standing gaps" section claimed `Documents/` (the PRD, data model,
architecture, onboarding spec, crisis-boundary spec) is "not checked into this repo — only
`docs/PHASE2_DEVICE_VERIFICATION.md` exists under version control." That's false as of this
pass — confirmed directly via `ls Documents/`, all six spec docs are tracked. Corrected in
`STATUS.md` in this same pass rather than left to compound; unclear how long it had already been
stale, worth a general reminder that "known standing gaps" sections are exactly the kind of note
that goes unverified longest, precisely because nothing re-checks them by default.

**Disposition of the four uploaded documents, per the base document's own instructions, followed
exactly rather than improvised:**
- `06_GOAL_ORIENTED_MISSION_MODEL_PROPOSAL.md` and `06a_GOAL_ORIENTED_MISSION_MODEL_ADDENDUM.md`
  (the two earlier, superseded drafts) — the base document's own §0 says to delete these once
  reviewed. Confirmed via `ls Documents/` that neither was ever actually checked into this repo
  in the first place (only uploaded directly, never committed) — so there was nothing to
  delete; noted here so a future reader doesn't wonder why no deletion commit exists.
- `06_GOAL_ORIENTED_MISSION_MODEL.md` (the base design doc) — reduced to a historical pointer,
  exactly as its own §0 specifies, rather than checked in verbatim. Its content is not
  duplicated in git history beyond this pass's own commit (the pointer file states this
  explicitly) — a future reader who needs the full six-fork rationale and worked examples
  should look at the commit that replaced this file's body, not expect it preserved elsewhere.
- `06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` — checked in in full, unmodified. This
  document is the engineering-ready artifact (exact file diffs, verified call sites, per-batch
  open questions) and doesn't get compressed the way the base design doc does; `BUILD_PLAN.md`'s
  new Batches G1–G6 point to its sections directly rather than re-deriving their content.

**What's now live, for a future reader orienting on this decision:**
- `Documents/01_DATA_MODEL_AND_SCHEMA.md` §2.2a — schema-level summary of the five
  new/renamed entities, the boundary rule (a `GoalMission` cannot be violated, only an
  `EnforcementSession` under it can — same isolation pattern as §7's `UnsupervisedSignal`
  boundary), and the migration note.
- `Documents/01_DATA_MODEL_AND_SCHEMA.md` §8 — new open-items row pointing here.
- `Documents/06_GOAL_ORIENTED_MISSION_MODEL.md` — now a pointer file only.
- `Documents/06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` — full implementation plan,
  checked in unmodified.
- `BUILD_PLAN.md` — Batches G1–G6, inserted into the dependency diagram and given full detail
  sections in the same style as Batches A–F, including a standing merge-collision warning
  (mirroring Batch A.5's three-way v5 note) since G1 renames a table every other in-flight
  batch may also touch.
- `STATUS.md` — sync line, phase table, and known-standing-gaps section updated to match (see
  that file's own edit, this same pass).

**Nothing in this pass changed `DisciplineOS_PRD_v3_6.md` itself** — the base design document's
§0 named both the schema doc and the PRD as fold-in targets, but the Goal-Oriented Mission
Model is explicitly a post-v3.6 addition (schema doc §8: "Not in PRD at all"), and no PRD
section currently describes or contradicts it. Revisit whether the PRD needs its own update
once G1–G6 are actually built and the shape has been through real implementation, rather than
editing a numbered, versioned product document speculatively ahead of working code — flagged
here rather than silently skipped.

**Same standing verification caveat as every other entry in this file:** no Android/Kotlin
compiler reachable in this authoring sandbox — moot for this specific pass since it wrote zero
code, but the next reader picking up Batch G1 should not assume this entry did any code-level
verification beyond reading the existing files directly, which is what it actually did.

### 5.33 — Batch G1 shipped diverged from the checked-in plan; conformance pass, now complete
pending CI/on-device

**What happened, plainly:** Batch G1 was built and merged to `main` (PR #27, commit `c0a4598`
+ two CI-fixup commits) claiming to implement
`06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §2 — but on inspection it diverged from
that document in three real ways, not just naming color:

- Plan §2.1: `EnforcementSession.missionId: UUID` (non-null). Shipped: `goalMissionId: UUID?`
  (nullable) — a different field name and a different nullability contract.
- Plan §2.3: `MissionDao` interface renamed to `EnforcementSessionDao`. Shipped: kept the
  `MissionDao` interface name (only the entity type parameter changed).
- Plan §2.1: `GoalMission` carries no `missionProfileId`. Shipped: carried one over from the old
  shape anyway.
- Separately, Batch G2 scope (Integration Plan §3.1 — `FirstMissionSchedulingFragment`
  auto-creating a parent `GoalMission`/`MissionPeriod`) was left unimplemented, with
  `goalMissionId` set to `null` at that call site — consistent with G1 being scoped to schema
  only, but worth stating since it means the shipped `EnforcementSession` rows had no working
  parent-attachment path at all until this pass.

None of this was hidden — the shipping commit's own message describes each deviation and its
reasoning (low-churn DAO naming, carrying the field over "to minimize call-site churn"). It's
flagged here as a real process gap, not a cover-up: the plan is the checked-in, engineering-
ready spec this project's own conventions treat as authoritative (see §5.32's framing above),
and a batch that diverges from it should either update the plan first or flag the divergence in
that same commit/PR for sign-off — neither happened here. The right fix, once noticed
(same day, this repo's history shows), was a conformance pass bringing the code back to what
the checked-in plan actually specifies, not silently updating the plan after the fact to match
what shipped.

**First conformance attempt (`goal-oriented-mission-model-g1-conform-to-plan` branch, commit
`c4b94ec`):** rebuilt `EnforcementSession`/`GoalMission`/DAOs to match plan §2.1–§2.3 exactly
(`missionId` non-null, `EnforcementSessionDao` rename, `GoalMission` field list trimmed to the
plan's exact set), and additionally implemented the real Batch G2 fix (§3.1's auto-create-
parent-`GoalMission` logic in `FirstMissionSchedulingFragment`) rather than leaving that gap
open. Also caught and fixed a second, independent problem while there: `01_DATA_MODEL_AND_
SCHEMA.md` §2.2a and the Integration Plan itself disagreed with each other (different
`GoalMission` field shapes; the schema doc incorrectly claimed a real Room `Migration` was
required, which the plan's §9 explicitly rules out pre-launch) — both authored in the same
commit (`e35948a`) without being cross-checked against each other. Corrected the schema doc to
match the plan, not the other way — the plan is the artifact whose file diffs and call sites
were actually verified against the live tree (§5.32's own framing).

**Left explicitly incomplete by that commit, per its own message:** 9 test files (5 in
`:domain`, 4 in `:app`) still referenced the pre-conformance `.missionDao()` accessor and/or
`goalMissionId` field name and would not compile. Pushed anyway, deliberately, as a checkpoint
rather than held for one large PR — this project's own stated preference (see multiple prior
entries in this file for the same "checkpoint working-so-far progress" pattern).

**This pass (same branch, on top of `c4b94ec`):** fixed all 9 flagged test files. Mechanical
pattern throughout, cross-checked file-by-file against the actual current `EnforcementSession`/
`EnforcementSessionDao`/use-case constructor signatures on this branch rather than assumed from
the commit message alone:

- `db.missionDao()` → `db.enforcementSessionDao()` (accessor rename) everywhere it appeared,
  including nested constructor calls (e.g. `ApplyReputationDecayUseCaseTest`'s embedded
  `TierTransitionUseCase` construction).
- `EnforcementSession(..., goalMissionId = null, ...)` → `EnforcementSession(..., missionId =
  UUID.randomUUID(), missionPeriodId = null, ...)` at every construction site — a fresh random
  UUID rather than a real inserted `GoalMission` row, matching how these same tests already
  reference `missionProfileId` (by UUID, no FK, no insert) since none of these use-cases
  actually join against `GoalMission` and no `@ForeignKey` constraint exists at the DB level
  (Integration Plan §2.1's explicit "no FK constraints where none exist elsewhere" reasoning
  extends to why this is safe for unit tests too, not just production code).
- Confirmed the use-case constructor parameter *name* (`missionDao =`) did not need to change
  in any of these files — only its declared type moved, from `MissionDao` to
  `EnforcementSessionDao`; the parameter itself was never renamed by the plan.

**Files touched this pass, all in `:app`/`:domain` test source sets, zero production code
changed:** `RecordViolationUseCaseTest.kt`, `TierTransitionUseCaseTest.kt`,
`ComputeBehavioralFingerprintUseCaseTest.kt`, `ApplyReputationDecayUseCaseTest.kt`,
`ResolveDisputeUseCaseTest.kt`, `InterceptionControllerTest.kt`, `DebugSeederTest.kt`,
`IronCalibrationFragmentTest.kt`, `FirstMissionSchedulingFragmentTest.kt`.

**Verification done, and its real limits:** full-repo grep sweep (including `androidTest`
source sets and non-`.kt` files) confirms zero remaining `.missionDao(`, `goalMissionId`, or
bare `MissionDao` type references anywhere except correct historical kdoc prose narrating the
rename itself. Brace/paren balance checked per edited file. Every added `UUID.randomUUID()`
call site confirmed to have `java.util.UUID` already imported in that file. `AppContainer.kt`'s
DAO wiring (already updated by the `c4b94ec` pass) cross-checked as consistent with these
fixes, not independently re-verified. **What this pass did not do:** run an actual compiler —
still no Android/Kotlin toolchain reachable in this authoring sandbox, same standing gap every
prior entry in this file has flagged. Every fix above is manually cross-checked against the
current source tree's real signatures, not build-verified. `ArchitectureBoundaryTest`
(data module) read directly and confirmed structurally unaffected — it's a static import-scan
for `UnsupervisedSignalDao`+`LedgerDao` co-imports, and nothing touched by this Goal-Oriented
Mission Model work imports `UnsupervisedSignalDao` — but this was reasoning about the check,
not running it.

**What's still open before this branch is a mergeable PR:**
1. CI must actually run and confirm green — this branch has never been through a real build.
2. On-device verification of the real change in this pass — `FirstMissionSchedulingFragment`'s
   new three-row transactional insert (`GoalMission` → `MissionPeriod` → `EnforcementSession`)
   has not been exercised on a device at all; it was written in the `c4b94ec` checkpoint and
   has had no runtime verification of any kind yet.
3. The Integration Plan's own flagged open questions (§3.3/§7.3/§7.4 — `resetMode` choice,
   `FIXED_WINDOW` with null bounds, the no-re-entry-guard "second visit creates a second
   GoalMission" behavior) remain genuinely open, not resolved by this pass or the one before
   it — carried forward exactly as flagged, not silently narrowed.
4. `STATUS.md`'s MVP table row for the Goal-Oriented Mission Model still reads "⬜ — Accepted,
   docs-only... no code written yet," which is now stale (real code exists on this branch,
   though unmerged and CI-unconfirmed) — updated in this same pass, see that file's own edit.
5. Once CI/on-device both confirm, this branch should be evaluated by the person as a normal PR
   against `main` — not merged as part of this pass.

### 5.34 — PR #28 merged, CI green; on-device partially exercised, not fully confirmed

**Closes item 5 above, partially.** The person confirmed CI compiled successfully on the
`goal-oriented-mission-model-g1-conform-to-plan` branch and merged PR #28 to `main` (commit
`cdde397`). Pulled and spot-checked directly against `main` rather than trusted from the merge
alone: `EnforcementSession.missionId: UUID` (non-null) and `EnforcementSessionDao` are both
confirmed present, and a full-repo grep on `main` itself confirms zero remaining
`.missionDao()`/`goalMissionId`/bare `MissionDao`-type references outside correct historical
kdoc prose. Batch G1's schema/DAO conformance is genuinely done, on `main`, CI-confirmed.

**On-device, item 2 above — status update, not closure:** the person installed and opened the
app on a real device and completed onboarding through `FirstMissionSchedulingFragment` without
a crash, confirmed via the nav graph that this Fragment is reachable through the real onboarding
flow (not just reachable in principle) — so the new three-row transactional insert did execute.
**This is a real but partial result, stated precisely rather than rounded up:** "the flow
completed without crashing" is not the same claim as "the three rows were written correctly and
linked correctly," which is what Batch G2's verification checklist actually asks for. As of
this pass, nothing in the app's UI surfaces a `GoalMission`, its `MissionPeriod`, or the linkage
between either and the `EnforcementSession` they produced — there is no screen, debug panel, or
log output a person could check right now even if they wanted to verify the row-level claim.
The person noticed and named this gap directly rather than treating "it didn't crash" as
sufficient — noted here because that's the right instinct and worth preserving as the standard
for this project's on-device checks going forward, not just this one.

**What would actually close this:** a debug DB inspector (Room has a stock one, or Android
Studio's Database Inspector against a debug build), a temporary log line in
`createMissionAndFinish` printing the three inserted ids, or an instrumented test reading the
rows back post-flow — any of these would let "correctly linked" actually be checked rather than
assumed from "no exception was thrown." None built yet; flagged as real remaining work, not
busywork, since a get-parent-id-wrong bug in a `withTransaction` block would produce exactly
this symptom (no crash, silently wrong `missionId`/`missionPeriodId` linkage) and nothing today
would catch it before a much later, harder-to-trace symptom downstream (e.g. Batch G3's
Adherence engine reading from a `GoalMission` that no `EnforcementSession` actually points at).

**`BUILD_PLAN.md` updated to match, same pass:** G1's status line moved from "conformance pass
in progress, unmerged" to "on `main`, CI green"; G1's verification checklist's stale real-
`Migration` item struck through and annotated (the shipped code correctly uses
`fallbackToDestructiveMigration()` per this project's standing pre-launch policy — the checklist
item reflected the plan text's assumption, not what was actually decided or built). G2's status
line updated to state the "ran without crashing, rows not confirmed" distinction explicitly,
and its own checklist's on-device line marked partial rather than either fully checked or fully
unchecked, with the specific remaining gap named rather than left as a bare unchecked box.
`STATUS.md`'s sync line and MVP table row updated to match — see that file's own edit, this same
pass.

**Still open:** the "what would actually close this" list above (debug inspector, logging, or
an instrumented test) — none started yet, no owner assigned in this pass. Item 3 from §5.33
(the Integration Plan's own open questions) remains untouched by this pass, exactly as it was
left before — still genuinely open, not implicitly resolved by CI passing or the app not
crashing.

---

### 5.35 — DAO-level linkage test added for Batch G2's open verification-checklist item; not yet CI-confirmed, does not close the on-device row

**Closes part of item 5 above, precisely stated.** §5.34 named three options that would let
"the three rows were written correctly and linked correctly" (Batch G2's own checklist wording)
actually be checked instead of assumed from "no exception was thrown": a debug DB inspector,
added logging, or an instrumented test reading the rows back. This pass takes the third option,
consistent with this module's existing convention (`fragment-testing` deliberately removed —
`app/build.gradle.kts` — so every screen-level test file in this package is already a
Robolectric + in-memory-Room DAO test, not an on-device instrumented test).

**What was built:** `FirstMissionSchedulingFragmentTest` (`:app`) gained a private
`insertGoalMissionChain` helper that mirrors `createMissionAndFinish`'s real
`database.withTransaction { }` block field-for-field — same `GoalMission` field values
(`archetype = BEHAVIOR_DRIVEN`, `resetMode = ROLLING_WINDOW`, same known `MissionPeriod`
`FIXED_WINDOW`/null-window mismatch §3.3/§7.4 already flagged, not silently fixed by the test),
same insert order, real generated ids threaded through rather than fabricated random ones (the
pre-existing tests in this file insert a standalone `EnforcementSession` with a random
disconnected `missionId` — they never exercised the real chain and could not have caught a
wrong-parent-id bug). Four new test cases use it:

- Exactly one `GoalMission` and one `EnforcementSession` exist after one call, and the full
  chain resolves correctly both ways — `EnforcementSession.missionId` → the real `GoalMission`,
  `EnforcementSession.missionPeriodId` → a real `MissionPeriod` whose own `missionId` points
  back to that same `GoalMission`. This is the specific claim §5.34 left unconfirmed.
- `GoalMission.title`/`MissionPeriod.enforcementProfileId`/`EnforcementSession.missionProfileId`
  and allow/blocklist carry-over from the `MissionProfile` all match, matching this file's
  existing style of checking field carry-over explicitly rather than assuming it.
- The known, flagged (not fixed) no-re-entry-guard gap now covers the full three-row chain, not
  just a standalone `EnforcementSession` as the pre-existing test checked — two calls produce
  two distinct `GoalMission` rows, documenting rather than resolving Integration Plan §7.3's
  open question.
- The Schedule-Mission (`scheduledStart` non-null) path links identically to Start-now —
  `scheduledStart` only changes which button path was taken, not the chain's shape.

**What this does and does not confirm — stated precisely, same standard §5.34 set:** this test
runs against an in-memory Room database under Robolectric, not a real device, and has not yet
been run through a real compiler or CI (no Gradle/Android toolchain reachable from the authoring
sandbox — confirmed again this pass: `./gradlew --version` fails with an HTTP 403 fetching the
Gradle distribution, network egress is domain-allowlisted and `services.gradle.org` isn't on
it). It gives strong reason to expect the real transaction links correctly, since the test
mirrors that transaction's code verbatim rather than testing a hand-simplified stand-in — but
"passes a mirrored test, once it's actually run" and "confirmed against real on-device rows" are
still two different claims, and this pass only has grounds to make progress on the first. The
on-device checklist row in `BUILD_PLAN.md` Batch G2 stays open.

**`BUILD_PLAN.md` updated to match, same pass:** Batch G2's linkage checklist item is split into
two rows instead of one — the DAO-level test (now checked, with the precise caveat above) and
the on-device confirmation (still open) — rather than checking one box that would blur the two
claims together, the same mistake §5.34 itself was written to correct.

**Still open:** on-device confirmation itself (debug DB inspector, logging, or an on-device
instrumented test, per §5.34's original list — the DAO-level test doesn't substitute for any of
the three); this test's own first real CI run; Integration Plan §7.3's open question (whether a
re-entry guard should exist at all), left exactly as open as §5.34 found it.

---

### 5.36 — Batch G3 shipped (Adherence engine); two real, unflagged base-doc divergences found and handled — one fixed, one flagged, neither guessed — PR #31 merged, CI green, installed

**Closes Batch G3** (`BUILD_PLAN.md`), Integration Plan §4. **Update, PR #31:** merged to
`main`, real CI (`services.gradle.org` reachable from GitHub Actions, unlike the authoring
sandbox) caught one failure — `ApplyAdherenceDecayUseCaseTest`'s "DAILY cadence expects one
entry per window day" — on the first run. Root cause was in the test, not the production code:
every test called `useCase.execute(mission.id)` without passing `now`, so the use-case's own
`Instant.now()` default ran strictly after the test had already seeded log entries via its own,
separately-called `Instant.now()`. The DAILY test seeds an entry at `now(test) − 7 days`;
`windowStart` is computed as `now(usecase) − 7 days`; since `now(usecase) > now(test)`,
`windowStart` lands after the seeded entry's `createdAt`, and `MissionLogEntryDao
.forMissionSince`'s `>=` filter drops it, undercounting hits by one. This was latent in every
window-boundary-adjacent test in the file, not just the one that happened to trip — fixed by
pinning a single `val now = Instant.now()` per test and passing it explicitly to
`execute(mission.id, now)`, the same pattern `ApplyReputationDecayUseCaseTest` already uses for
its own now-sensitive assertions. Second push, CI green, merged. **Confirmed installed** on a
real device.

This is a real, useful data point for this project's own "not yet CI-confirmed" caveats
elsewhere (§5.7 onward): the manual-review pass below did catch every schema/signature/import
issue by hand, and the actual bug CI found was in test scaffolding's implicit-`now()` timing,
not in anything the manual review was checking for — a category of bug (nondeterministic time
handling across two independently-called clock reads in the same test) that's realistically
only caught by actually running the test, not by reading it. Worth remembering next time this
project is tempted to treat a careful manual review as a substitute for real CI rather than a
stopgap for it.

Below is the original pre-merge account, left as written rather than rewritten to look
prescient — the divergence-tracing work and what got built are both unchanged by the fix above.

**What was found before any code was written, not silently resolved:**

1. **Real schema regression, fixed this pass.** `MissionLogEntry` — the table Integration Plan
   §4.1 says Adherence's hit-rate is computed from — shipped in Batch G1 with only
   `{id, missionId, createdAt, note: String}`. The base design doc
   (`06_GOAL_ORIENTED_MISSION_MODEL.md` §3.3, confirmed against the original proposal and
   addendum drafts as well) always specified `numericValue: Double?` and `didOccur: Boolean?` as
   the actual hit/miss signal Adherence measures — "computed from MissionLogEntry
   presence/value." The Integration Plan's own §2.1 field list simply dropped both fields when
   summarizing the base doc, and G1 shipped that drop unflagged. Without them, the hit-rate math
   Integration Plan §4.1 asks for was literally uncomputable from a freeform note string. Fixed
   this pass: both fields added, `note` made nullable (a numeric/boolean-only log entry with no
   note is a normal case, e.g. logging "ran 3 miles" via `numericValue` alone). DB version bumped
   to v11 alongside the new Adherence tables (see `DisciplineOsDatabase.kt`'s own v11 comment) —
   bundled in one version bump rather than two, matching v6's own precedent for folding
   independent additions together.

2. **Real, smaller divergence, flagged not fixed.** `MissionPeriod.enforcementProfileId` shipped
   non-null; the base doc §3.2 specifies `UUID | null` ("null = tracked/logged only, no
   blocking") — the mechanism §4.2's "Behavior-driven mission with no attached
   EnforcementSession" scope rule was expected to key off. Not fixed this pass: a clean
   workaround exists (`EnforcementSessionDao.hasAnySessionFor` — does a real `EnforcementSession`
   row exist for this `GoalMission`, checked directly rather than via the field's nullability),
   so `ApplyAdherenceDecayUseCase` doesn't depend on this gap being closed. Noted in
   `MissionPeriod`'s own kdoc as a real, unresolved divergence for a future pass — every
   `MissionPeriod` row currently claims a concrete enforcement profile even for a purely
   log-only, no-blocking period the base doc's model explicitly allows.

Both were traced by reading the original `06_GOAL_ORIENTED_MISSION_MODEL_PROPOSAL.md`,
`06a_..._ADDENDUM.md`, and `06_GOAL_ORIENTED_MISSION_MODEL.md` drafts directly rather than
trusting the Integration Plan's own claim to be "direct transcriptions" of them — that claim
turned out not to be literally true in both cases above.

**What was built:**

- `MissionLogEntry` gains `numericValue`/`didOccur`; `note` becomes nullable
  (`data/entity/GoalMission.kt`).
- `GoalMission` gains `consecutiveWindowsBelowThreshold: Int = 0` (Integration Plan §7.5),
  mirroring `User.consecutiveDaysBelowFloor` exactly — running state across calls is the only
  way to detect "sustained miss patterns, not single misses" (base doc §4.2).
- `GoalMissionDao` gains `@Update` — a real, motivated exception to its "no `@Update` yet"
  precedent, same category `MilestoneDao.update` already established for `achievedAt`.
- `EnforcementSessionDao` gains `hasAnySessionFor(goalMissionId)` — existence-only
  (`SELECT EXISTS(...)`), backing the scope check above.
- New `AdherenceLedgerEntry` + `AdherenceLedgerDao` (`data/adherence/`) — a **physically
  separate table** from `LedgerEntry`/`LedgerDao`, not a new `LedgerMetric` value, per
  Integration Plan §4.1's "never feeds Tier" requirement expressed as a schema fact rather than
  a convention. `DisciplineOsDatabase` bumped to v11, new DAO accessor added.
- New `AdherenceDecayPolicy` + `HypothesisAdherenceDecayPolicy` (`domain/policy/`), mirroring
  `ReputationDecayPolicy`'s shape exactly. **The `[HYPOTHESIS]` constant Integration Plan §4.3
  calls out is actually four placeholder numbers, all flagged in code, none derived from any
  spec doc:** default window length (7 days), hit-rate threshold (0.7), consecutive windows
  before decay (2), decay per crossing (10.0 points). Swap the implementation, don't tune these
  in place, once real pilot data exists — same standing instruction
  `HypothesisReputationDecayPolicy` already states for its own numbers.
- New `ApplyAdherenceDecayUseCase` (`domain/usecase/`) — the engine itself. Scope-gates by
  archetype (OUTCOME_DRIVEN/CONSTRAINT always in scope; BEHAVIOR_DRIVEN only if no
  EnforcementSession is attached yet). Computes a hit-rate per entry via `didOccur` first, else
  `numericValue` checked against `targetDirection`/`targetValue` (a 10% tolerance for `MAINTAIN`
  — `[HYPOTHESIS]`, no tolerance is stated in either spec doc). Expected-entry denominator
  derived from `cadenceType` (DAILY = window days, WEEKLY = `ceil(windowDays / 7.0)`, CUSTOM_DAYS
  = DAILY's expectation as a placeholder pending a real per-week schedule field — Integration
  Plan doesn't add one, NONE = 1). Decays only on sustained miss *windows*
  (`consecutiveWindowsBelowThreshold` reaching the policy's threshold), never on a single miss.
  Writes only to `AdherenceLedgerDao`/`GoalMissionDao` — never calls `TierTransitionUseCase`,
  never touches `LedgerDao`. Returns `Result.thresholdCrossing` as the Weekly Report callout hook
  (Integration Plan §4.2) — Batch F, when it exists, reads `AdherenceLedgerEntry
  .thresholdCrossing` rows rather than computing anything new.
- `ApplyAdherenceDecayUseCaseTest` (`:domain`) — scope-gating (all four archetype/session
  combinations), hit-rate math (`didOccur`, all three `TargetDirection` branches, presence-only,
  note-only exclusion, window boundary), expected-count-by-cadence (DAILY/WEEKLY/NONE), decay
  behavior (single miss vs. threshold-reaching miss vs. a met window resetting the counter), the
  "first call establishes 0.0, not null" behavior, "score always reflects ledger sum even when
  this call writes nothing," and an explicit assertion that `LedgerDao`'s REPUTATION/DEBT values
  never move as a result of Adherence decay.

**What this does and does not confirm (as originally written, pre-merge):** same precise
standard §5.34/§5.35 already set for this project — this is a manual-review-plus-mirrored-test
pass, not a compiler- or CI-confirmed one. Every changed file's imports, DAO method signatures,
and enum `when` exhaustiveness were checked by hand against the actual shipped schema (not
assumed from the specs) before this entry was written; that's real evidence the code is
internally consistent, not evidence it compiles. **Superseded by the update above: CI has now
run for real, found one bug (in the test, not the reviewed production code), and that bug is
fixed.**

**Still open, updated post-merge:** `MissionPeriod.enforcementProfileId`'s nullability
divergence (flagged above, deliberately not fixed this pass — still true); Integration Plan
§7.1-style CUSTOM_DAYS cadence schedule gap (no per-week schedule field exists — flagged in
`ApplyAdherenceDecayUseCase`'s own kdoc, not resolved here — still true). No longer open: first
real CI run (done, green, PR #31 merged); installed confirmation (done). Still true that no UI
call site exists yet for this use-case, matching `ApplyReputationDecayUseCase`'s own
still-unbuilt-scheduler state — Batch G4 (Mission detail screen) is the natural first caller.

---

### 5.37 — Batch G4 blocked, then unblocked: base design doc source material restored to git history for real; found and will fix a real G3 scope bug §4.2's full text exposed

**Started while beginning Batch G4** (`BUILD_PLAN.md`), Integration Plan §5. Hit the same class
of gap §5.36 hit for §4.2, this time for §4.1's "four-quadrant relationship view" — the
Integration Plan *names* it ("Renders the four-quadrant read from base doc §4.1... combining
`MissionLogEntryDao` outcome trend + `GoalMission.adherenceScore`") but never actually defines
what the four quadrants are, what text each one produces, or how the two inputs combine into
them. Checked `git log --all` for `06_GOAL_ORIENTED_MISSION_MODEL.md`: **its very first commit
(`e35948a`) already contains the file pre-reduced to a pointer** — the fold-in that produced it
was written from an earlier chat session's memory of four uploaded documents, not from those
documents being committed to this repo first. That pointer's own text claimed the full content
"lives in git history on this exact path if it's ever needed verbatim" — false. No commit, on
any branch, ever contained the real text. Same root cause as §5.36's §4.2 gap, not a new kind of
problem — just not yet fixed at the source the first time it was found.

**Fixed properly this time, not just worked around for one section.** Rather than resolving only
the immediate §4.1 blocker, the user supplied all four original documents (base doc, proposal,
addendum, Integration Plan) and asked that they be verified and committed before G4 continued,
so this gap can't recur for whichever section G5 or G6 need next. What was done, in order:

1. Diffed the re-supplied Integration Plan against what's already checked in — byte-identical,
   confirming this is the same document set, not a revised one.
2. Restored `06_GOAL_ORIENTED_MISSION_MODEL.md` to its full original text (verbatim from the
   upload), plus committed `06_GOAL_ORIENTED_MISSION_MODEL_PROPOSAL.md` and
   `06a_GOAL_ORIENTED_MISSION_MODEL_ADDENDUM.md` in full — both previously deleted, per the base
   doc's own "delete once reviewed" instruction, before any commit had captured their content.
   This is a separate commit from the pointer re-reduction below, so "content restored" and
   "pointer reduction re-applied" are independently auditable, matching this project's existing
   preference (§5.35/§5.36) for not blurring two distinct claims into one commit/checkbox.
3. Re-reduced the base doc back to a pointer in a second, separate commit — same convention the
   doc's own §0 calls for — but this time the pointer's "recoverable from git history" claim is
   actually true, and the pointer text says so explicitly rather than repeating the same
   unverified claim.
4. **Deliberately did not re-delete the proposal/addendum this pass.** The base doc's own
   instruction says to delete them once reviewed; doing that literally is what caused this gap.
   They stay checked in indefinitely — the cost of ~800 extra lines of superseded markdown is
   far smaller than the cost of this investigation recurring for G5/G6.

**Real bug found while reading the restored §4.2 text, not a documentation nicety.** Base doc
§4.2, read in full for the first time this session (previously only reachable via the Integration
Plan's partial paraphrase), says:

> "A Behavior-driven mission with a `MissionPeriod` that *does* have `enforcementProfileId` set
> already gets Reputation/Debt treatment via its sessions — **Adherence still computes for it**
> (log-only days between scheduled sessions are real signal too), but is shown as a secondary
> number, never substituted for Reputation on that mission."

`ApplyAdherenceDecayUseCase`'s shipped scope-gate (Batch G3, §5.36) only implements the *first*
half of this — a Behavior-driven mission with any attached `EnforcementSession` is currently
treated as fully out of scope (`Result.outOfScope()`), silently dropping the Adherence signal
entirely rather than computing it and marking it secondary. The prior session's own kdoc quoted
"Behavior-driven missions that have no attached EnforcementSession" as base doc §4.2's "exact
wording" — accurate as a fragment, but it was working from the Integration Plan's paraphrase,
which itself never restates this scope rule at all (checked directly: Integration Plan §4's G3
section describes the hit-rate/decay/ledger mechanics but has no scope-gating language of its
own), and the base doc's own text was unreachable at the time. Not a documentation-only finding —
`Result.inScope` is a real, tested boolean callers branch on, and every currently-passing
`ApplyAdherenceDecayUseCaseTest` scope-gating test for the "BEHAVIOR_DRIVEN with a session"
case encodes the narrower (wrong) behavior. **Fix scoped as a small follow-up, tracked here
rather than silently folded into G4's own PR** — G4 depends on `adherenceScore` existing to
render, and this bug means that score is currently `null`/absent for a real, valid mission
shape the base doc says should have one. Will be fixed before or alongside G4, whichever the
dependency graph makes cleaner once G4's actual screen work starts.

**Still open, unchanged by this entry:** the CUSTOM_DAYS cadence schedule gap and
`MissionPeriod.enforcementProfileId`'s nullability divergence, both already flagged in §5.36,
remain exactly as open as that entry left them.

### 5.38 Fix §5.37's scope bug: Adherence computes for every GoalMission; primary/secondary moves to `Result.isSecondary`

Small, standalone fix, landed as its own PR ahead of G4 per §5.37's own note that it should be
resolved "before or alongside G4, whichever the dependency graph makes cleaner." Doing it first
turned out cleaner — G4's screen work reads `adherenceScore`/`Result`, so it should see the
correct shape from the start rather than being built against the known-wrong one and patched
after.

`ApplyAdherenceDecayUseCase.execute`'s scope gate previously excluded a Behavior-driven mission
with any attached `EnforcementSession` entirely (`Result.outOfScope()`, no ledger write, no
score). Per base doc §4.2's full text (quoted in §5.37), that's wrong — Adherence still computes
for that case, it's only the *display priority* that changes (secondary vs. primary), which is a
G4 rendering concern, not something G3's computation should gate on.

**Fix:** `inScope` now only reflects whether `goalMissionId` exists — every archetype computes an
Adherence number. A new `Result.isSecondary: Boolean?` carries the primary/secondary distinction
forward: true exactly when the mission is Behavior-driven with an attached `EnforcementSession`
(the one case base doc §4.2 names as secondary), false for every other in-scope case, null when
out of scope. `ApplyAdherenceDecayUseCaseTest`'s scope-gating block updated to match — the old
"BEHAVIOR_DRIVEN with a session is out of scope" test is now "...still computes but is flagged
secondary," asserting `inScope == true`, `isSecondary == true`, and a non-null `hitRate`.

### 5.39 G4: Mission Detail screen + Home entry point (Integration Plan §5/§7.6, base doc §4.1/§4.2)

Builds on §5.38's corrected `Result` shape. New: `MissionDetailFragment`/`MissionDetailScreen`
(Adherence display + the four-quadrant outcome/behavior relationship read for
`OUTCOME_DRIVEN` missions), plus a Mission list card on `HomeScreen` — the "real new nav
destination required" entry point Integration Plan §7.6 names explicitly, since nothing before
this batch let a user reach a `GoalMission`'s detail screen at all.

**Behavior axis reads `ApplyAdherenceDecayUseCase.Result.hitRate` against
`AdherenceDecayPolicy.hitRateThreshold()`, not `adherenceScore`'s sign and not
`consecutiveWindowsBelowThreshold`.** `[HYPOTHESIS]`, departs from base doc §4.1's literal
`adherenceScore` wording — full reasoning (why `adherenceScore` is too lagging a signal for a
"what's happening right now" screen, why a streak-style boolean was rejected in favor of a
rolling compliance-rate read, and the habit-tracking research that informed the near-miss
banding) is in `MissionDetailFragment`'s own class kdoc rather than restated here. The threshold
itself is read live from `AppContainer.adherenceDecayPolicy().hitRateThreshold()` at the call
site and passed into the pure `computeMissionDetailState`/`behaviorReadFor` functions as a
parameter — not duplicated as a second hardcoded literal — so the display layer can't silently
drift from the domain layer's real threshold.

**Outcome axis** (outcome-driven missions only): recent-half vs. earlier-half mean of logged
`numericValue`s, direction-checked against `targetDirection`. `[HYPOTHESIS]` trend algorithm,
same "plainest thing that answers the question" bias as every other placeholder in this
codebase — neither spec doc states how "moving" should be computed from raw log rows.

**Unit tests:** `MissionDetailFragmentTest` covers `computeMissionDetailState` directly (pure
function, no Robolectric), matching `HomeFragmentTest`'s precedent — behavior-axis
classification at and around the threshold/near-miss boundary, all four relationship quadrants,
the `MAINTAIN` tolerance band, and the never-evaluated (`null` hitRate) case.

### 5.40 Doc sync: PR #35 (G4, Mission Detail screen) merged, CI green, confirmed installed

`BUILD_PLAN.md`'s G4 checklist and status line still said "NOT STARTED"/unchecked after the PR
actually merged — same category of stale-doc gap §5.36's entry closed out for G3. Updates both
to match reality:

- CI failed on the first push: `Unresolved reference: RelationshipView` at
  `MissionDetailFragment.kt:157`. Root cause was a missing import, not a logic error —
  `RelationshipView` (a `data class` in `com.disciplineos.app.ui.mission`, `MissionDetailScreen.kt`)
  was used directly in `computeMissionDetailState` but never imported into
  `MissionDetailFragment.kt`, even though every sibling type from that same package
  (`BehaviorReadClassification`, `MissionDetailScreen`, `MissionDetailUiState`,
  `RelationshipQuadrant`) was. Extensive manual pre-push review (schema/DAO/policy method
  existence, enum value spelling, string-resource key matching, constructor-signature
  cross-checks) caught the file's *only other* real defect — a stale hardcoded threshold
  constant left over from a mid-session refactor — but missed this one. Second push, CI green.
- Confirmed installed on-device: Mission list card visible on Home, Mission Detail screen
  reachable and rendering.
- `BUILD_PLAN.md`'s G4 checklist: all three rows checked off (CI green, on-device confirmed,
  entry-point decision recorded — Home Mission list, per Integration Plan §7.6, logged in
  §5.39 above). G5's status note updated to reflect both its dependencies (G1's `Trigger`
  entity, G4's screen) as now satisfied.

**Same reflection §5.36 already recorded for G3, worth restating rather than treating as a
one-off:** manual review without a real compiler in the loop reliably catches schema/contract
mismatches (wrong field names, wrong enum values, signature drift) but is a weak substitute for
actually compiling when the defect is a plain missing import — a category of error that's
trivial for a compiler to catch and easy for a human read-through to skip past, especially in a
file with a long import block where the missing entry is one line among many that are all
otherwise correct. Doesn't change the practice (this project still can't run Gradle in the
authoring environment), but is worth naming again as a known, recurring blind spot rather than
assuming §5.36's finding was a one-time fluke.

### 5.41 Fix Batch G1's `Trigger` entity: shipped shape was a different entity, not a variant of the specced one

Found while starting Batch G5 (Trigger UI), before writing any G5 code — the same "read the real
spec text before building against a summary of it" discipline that caught §5.36/§5.37's findings.

**What was wrong:** Batch G1 shipped `Trigger` as `TriggerConditionType { INACTIVITY,
SCHEDULE_MISS, MANUAL }` / `conditionValue: String?` / `active: Boolean` / `lastFiredAt:
Instant?` — a session-inactivity watchdog ("if no session has run in N days, prompt"). Base doc
§3.4/§4.3 specify `Trigger` as an **implementation-intention cue** entity entirely: Gollwitzer's
"if-then" plans (d = 0.65, 94-study meta-analysis) binding a specific cue to a specific response
— "when I finish dinner, open the reading app" — with `cueType: TIME_OF_DAY|PRECEDING_EVENT|
LOCATION|APP_OPEN|MANUAL`, `cueDescription`, `responseDescription`, `cueTimeOfDay`,
`cuePrecedingMissionId`, `cueLocationLabel`, `cueTriggerPackageId`. These are not two versions of
the same idea — they answer different questions and share almost no fields. The Integration
Plan's own §2.1 explicitly called `Trigger.kt` a "direct transcription of base doc §3.2–§3.5,"
so this was never a deliberate narrowing either; it's an unflagged divergence from the document
it was meant to summarize, the same category §5.36 (`MissionLogEntry`) and §5.37 (`Result`'s
scope gate) already found twice in this same integration effort.

**Why fix in place rather than add a second, correctly-shaped entity or a migration:** confirmed
directly (`grep` across `:domain`/`:app`) that nothing reads or writes `Trigger` anywhere —
`TriggerDao.insert`/`forMission` exist but are called from zero real call sites. No data to lose,
no call site to migrate, no reason to carry the wrong shape forward "for compatibility" when
there is nothing to be compatible with. Fixed as its own small, standalone PR ahead of G5's own
UI work, same "small motivated fix landed first, so the batch that actually needs the entity
sees the correct shape from the start" pattern §5.38 used for the `Result.isSecondary` fix ahead
of G4.

**Fix:** `Trigger` rebuilt to match base doc §3.4 exactly, plus one small flagged addition:
`active: Boolean = true` (base doc states no such field) — lets a person deactivate a Trigger
without deleting the row and losing their own cue/response text, same "small motivated addition,
logged rather than silently assumed" category as `GoalMission.consecutiveWindowsBelowThreshold`
(§5.36) and `MilestoneDao`'s `@Update` exception. `TriggerDao`'s method set/queries were
already spec-shaped and needed no change. DB version bumped to 12 (`fallbackToDestructiveMigration`
still in effect, still pre-launch, still no real installed base — same standing reasoning as
every prior bump).

**Restated once more, since this is now the third time it's mattered in this integration
effort:** an Integration Plan section that says "direct transcription of §X" is a claim to
verify against the actual source text, not a claim to trust because a prior batch already built
against it. Trusting summaries of summaries is exactly how this class of drift compounds
silently across batches.

### 5.42 G5: Trigger UI + lifecycle prompts (Integration Plan §6, base doc §4.3/§5/§6.2) — pushed, PR open, not yet merged

**Status note up front, since every prior G-batch entry in this log describes a merged state:**
this entry documents work **pushed to `g5-trigger-ui-lifecycle-prompts` and opened as a PR
(#38 on GitHub; CI job "build-and-test" ran as #151 then #154), not yet merged to `main`.** The
doc sync below reflects that — `BUILD_PLAN.md`'s G5 status line moves to "in review," not
"DONE," and `STATUS.md`'s Goal-Oriented Mission Model row is updated to name the open PR rather
than claim G5 shipped. This is a deliberate departure from every prior `gN-docsync-post-merge`
branch this project has used — this one is named `g5-docsync-pending-merge` for exactly that
reason, so the branch name itself doesn't misstate where the work actually sits.

**What G5 builds**, across four slices, all pushed:

- **`hypothesizingStageSatisfied`** (`data/.../metrics/Metrics.kt`) — the `OBSERVING` →
  `HYPOTHESIZING` lifecycle-stage transition, base doc §5 step 2: a `[HYPOTHESIS]`-flagged
  threshold (3, picked per this project's existing "round number, no derivation" convention for
  unresolved constants) of outcome logs with no behavior (`MissionPeriod`) attached. Built as a
  pure function alongside `ironCalibrationSatisfied`, per Integration Plan §6's own explicit
  instruction to reuse that precedent rather than re-derive the pattern. Full test coverage in
  `MetricsTest.kt`.
- **`CreateConstraintTriggerUseCase`** (`domain/.../usecase/`) — the one sanctioned call site
  for an `APP_OPEN`-cue `Trigger` on a `CONSTRAINT`-archetype Mission, per base doc §6.2's
  resolution: one transaction, one `MissionProfile` (fresh, scoped to exactly the one blocked
  package — see the class's own kdoc for why reusing the user's general-purpose
  `MissionProfileDao.mostRecentFor` profile would have been wrong, flagged `[HYPOTHESIS]` since
  neither spec doc states where this profile should come from), one `MissionPeriod`
  (`ALWAYS_ON`), one `Trigger` (descriptive only, never a second enforcement path). Explicitly
  documented in its own kdoc that this use-case stores the data shape but does not itself wire
  up live enforcement — nothing in `:domain`/`:app` reads `MissionPeriodDao`/`ALWAYS_ON` yet to
  turn this into a real `EnforcementSession`; that bridge is separate, unbuilt work, not a claim
  this batch makes.
- **Trigger creation UI** (`TriggerCreationScreen.kt`/`TriggerCreationFragment.kt`) — cue type
  selector + free-text cue/response fields, routing `APP_OPEN` on `CONSTRAINT` missions through
  `CreateConstraintTriggerUseCase` and every other `(cueType, archetype)` combination through a
  plain `TriggerDao.insert` (base doc §4.3: non-`APP_OPEN` cues are "not independently
  phone-enforceable," matching this project's existing bias against use-case ceremony for a
  trivial single-table write — `HomeFragment.recordDismissal`'s identical precedent).
- **Mission Detail trigger prompt** — a dismissible card on `MissionDetailScreen`, shown once
  per Mission while `lifecycleStage == HYPOTHESIZING`, matching Integration Plan §6's own stated
  placement ("most naturally surfaced from the Mission detail screen"). Checked directly against
  PRD §8.1 (Mission Profile Drift Detection) as the Integration Plan instructed rather than
  trusted from the plan's characterization — found that §8.1 describes an unimplemented
  detection *mechanism* (override/dispute-rate driven), not an existing UI pattern with code to
  literally mirror, so the card was built matching §8.1's abstract shape (dismissible,
  non-mandatory, single surfacing) rather than a claimed code reuse that doesn't exist.
  Dismissal tracked via a new `GoalMission.triggerPromptDismissedAt: Instant?` field (DB v12→v13)
  rather than a new dismissal table — simpler for a per-Mission, no-history-needed flag, same
  "small motivated field addition" category as `consecutiveWindowsBelowThreshold` (§5.36).
  Prompt hides once dismissed OR once any `Trigger` already exists for the Mission — both
  independently satisfy "don't nag someone who already has one," an explicit judgment call since
  neither spec doc states which of the two should gate it.

**A real design gap found and resolved before writing `CreateConstraintTriggerUseCase`, not
guessed:** Integration Plan §6 names the use-case's exact signature
(`CreateConstraintTriggerUseCase(missionId, packageId, cueDescription)`) but never states where
the `MissionPeriod.enforcementProfileId` it needs should come from. Confirmed directly (reading
`CoreDaos.kt`) that `MissionProfileDao.mostRecentFor` is the only existing read method and
returns the user's single general-purpose profile — reusing it wholesale would silently change
enforcement for every other `EnforcementSession` referencing the same `MissionProfile.id`.
Resolved (`[HYPOTHESIS]`) as: create a fresh, minimal `MissionProfile` per Constraint Trigger,
scoped to exactly the one blocked package, matching base doc §6.2's own framing of `ALWAYS_ON`
as being "for exactly the one behavior they named." Revisit if a future profile-picker UI
(already flagged as future work in `MissionProfile`'s own kdoc) makes reuse-by-choice possible.

**A pre-existing test suite found before touching it, not broken silently:**
`MissionDetailFragmentTest.kt` already had 17 tests calling `computeMissionDetailState` with a
fixed 4-parameter signature, all by explicit parameter name. The new trigger-prompt logic needed
`hasAnyBehaviorAttached`/`outcomeLogCount`/`hasExistingTrigger` as additional inputs — added as
new parameters with defaults so all 17 existing calls keep compiling and passing unmodified,
rather than restructuring the signature. 5 new tests added covering `showTriggerPrompt`'s
visibility logic specifically.

**CI failure, first push (PR #38, CI run #151, `build-and-test` job) — two real bugs in the new
test file, not in `CreateConstraintTriggerUseCase` itself:**

1. `creates a MissionProfile, an ALWAYS_ON MissionPeriod, and an APP_OPEN Trigger in one call`
   failed an `AssertionError` — the first test in this codebase to `assertEquals()` a full
   entity against its own DB round-trip (`db.missionProfileDao().get(...)`, etc.). Room's
   `Instant` converter (`Converters.kt`) stores `Instant` as epoch-millis, truncating any
   sub-millisecond precision a bare `Instant.now()` carries — so the in-memory result and the
   DB-round-tripped copy failed data-class equality on `createdAt`. Fixed by passing an
   already-millis-truncated `now` into `useCase.execute(...)` in the test, so both sides compare
   equal by construction. No production code changed.
2. `rejects a non-CONSTRAINT mission rather than silently coercing it` failed an
   `AssertionError` wrapping an unexpected `IllegalStateException` — the test wrapped
   `assertThrows` around a **second, nested** `runTest { }`. Room's `withTransaction` dispatches
   its lambda via its own dispatcher, and an exception thrown inside a nested `TestScope`
   doesn't propagate out of `assertThrows` as its original type. `RecordViolationUseCaseTest`'s
   `a crisis-exit mission must not go through this use-case` test already establishes this
   project's working pattern for exactly this situation — `@Test(expected = ...)` directly on
   the outer `suspend fun = runTest`, no nesting — and wasn't followed here. Fixed by switching
   both exception tests (non-`CONSTRAINT` archetype, missing mission id) to that pattern, and
   splitting the non-`CONSTRAINT` test's "writes nothing" postcondition into its own separate,
   non-exception test, since `@Test(expected=)` can't both catch the expected exception and let
   the test body continue to assert afterward.

Both fixes pushed as a follow-up commit on the same branch (`1907e49`). CI re-run pending as of
this entry.

**CI failure, second push (PR #38, CI run #154) — one more real bug, this time in production
code, not test code:** `rejects a missing mission id` failed with an unexpected
`IllegalArgumentException` where `IllegalStateException` was expected, thrown from
`CreateConstraintTriggerUseCase.kt:101` — the `requireNotNull(goalMissionDao.get(missionId))`
guard itself. The use-case's own kdoc claimed `requireNotNull` throws `IllegalStateException`;
that claim is simply wrong. Kotlin's `requireNotNull` always throws `IllegalArgumentException` —
`checkNotNull` is the stdlib function for the "structurally impossible, not a normal caller
error" `IllegalStateException` case this use-case's design actually wants (a real, distinct
posture from `require`'s "bad archetype" `IllegalArgumentException` case a few lines below it).
Fixed by swapping `requireNotNull` → `checkNotNull` in the use-case, preserving the intended
`require`-vs-`checkNotNull` semantic split rather than flattening both guards to the same
exception type. Pushed as a second follow-up commit (`208f0bc`).

**This latent bug is not unique to this use-case — found while checking, not fixed elsewhere in
this pass:** `grep -rl "requireNotNull(" domain/src/main/java app/src/main/java` turns up the
same "should be structurally impossible, therefore `requireNotNull`" pattern in
`RecordViolationUseCase` (the use-case this batch's kdoc originally cited as the precedent to
match), `ApplyReputationDecayUseCase`, `ResolveDisputeUseCase`, `TierTransitionUseCase`,
`MissionInterceptionActivity`, and `TriggerCreationFragment` (this same batch's own new file).
None of those currently have a test that asserts the resulting exception's *type* (only this
batch's new test did, which is exactly why this was never caught before now) — so none of them
are demonstrably broken yet, but all of them almost certainly throw `IllegalArgumentException`
where their own surrounding comments/intent describe an `IllegalStateException` posture. **Not
fixed here** — out of this batch's scope, and changing five other files' exception types with no
test coverage to verify each one risks its own regressions with no way to confirm correctness in
this sandbox. Flagged as a real, standalone follow-up item (added to `STATUS.md`'s "known
standing gaps").

**Worth naming again, third or fourth time this project's history has hit some version of
it:** a manual cross-check pass (imports, DAO signatures, enum spelling, schema registration —
all confirmed correct on this batch) catches structural mismatches reliably but cannot catch
either a test-authoring bug that only a real coroutine/Room runtime exposes (the `Instant`
precision mismatch, the nested-`runTest` exception-propagation quirk — both fixed in the first
follow-up commit) or a genuine stdlib-semantics error stated confidently in a kdoc and never
verified (`requireNotNull` vs. `checkNotNull` — fixed in the second). The second one is the more
concerning of the two: it's a real behavioral bug in shipped-shape production code, not test
scaffolding, and it existed unnoticed in at least one other already-merged use-case
(`RecordViolationUseCase`) before this batch's test suite happened to be the first to actually
assert on the exception type closely enough to expose it. Same standing gap `STATUS.md`'s "known
standing gaps" section already names (no compiler/toolchain reachable from the authoring
sandbox) — not new information, but this batch is the first time that gap produced a real CI
failure in production code specifically, not just test code, worth tracking as its own
sub-category rather than folding silently into the existing note.

### 5.43 Audit: `requireNotNull`-for-a-"structurally-impossible"-guard pattern across the codebase

Follow-up to §5.42's second CI failure. That entry found `CreateConstraintTriggerUseCase` used
`requireNotNull` for a missing-parent-row guard while its own kdoc claimed the resulting
exception would be `IllegalStateException` — wrong; `requireNotNull` always throws
`IllegalArgumentException`, `checkNotNull` is the stdlib function for the "structurally
impossible" `IllegalStateException` posture. That entry flagged, but deliberately did not fix,
the same pattern in five other files: `RecordViolationUseCase`, `ApplyReputationDecayUseCase`,
`ResolveDisputeUseCase`, `TierTransitionUseCase`, `MissionInterceptionActivity`, and
`TriggerCreationFragment`. This entry is that audit.

**Method, for each file:** read every `requireNotNull` call site and its surrounding kdoc/comment
in full, classify it as either (a) a missing-row / "should be structurally impossible" guard —
the `checkNotNull` posture — or (b) genuine bad-caller-input / UI-should-have-prevented-this —
the `require`/`requireNotNull` posture that's actually correct as-is; then grep each file's test
suite for any existing assertion on the specific exception *type* thrown by that exact call site,
so no fix is made blind to a test that might currently depend on the (wrong) behavior.

**Five files, twelve call sites, all confirmed category (a) and fixed to `checkNotNull`:**

- `RecordViolationUseCase.execute` — three sites: missing `Mission`, missing `User`, missing
  `user.currentTier`. All three kdoc-adjacent comments already used "structurally impossible"
  language. `RecordViolationUseCaseTest`'s only exception-type test
  (`a crisis-exit mission must not go through this use-case`, `@Test(expected =
  IllegalArgumentException::class)`) exercises the separate `require(mission.status != ...)`
  call a few lines below these guards, not any of the three changed here — confirmed
  unaffected before changing.
- `ApplyReputationDecayUseCase.execute` — two sites: missing `User`, missing
  `user.currentTier`. Both comments state the same posture ("should never run before
  onboarding completes"). No test file for this class exists at all — no assertion risk.
- `ResolveDisputeUseCase` — two sites, one each in `execute` and `fileDispute`: missing
  `Violation`. `execute`'s own kdoc for the `violationId` parameter explicitly states a bad id
  is "a caller bug (surfaced as `IllegalStateException`, not silently accepted)" — the fix
  makes the code match its own documented intent. `ResolveDisputeUseCaseTest` has one real
  `@Test(expected = IllegalStateException::class)` (`resolving a violation with no active
  dispute fails loudly`) — traced and confirmed it exercises the separate `check
  (violation.disputeStatus == ...)` call, using a real, successfully-created `Violation`, not
  either changed `requireNotNull`. Unaffected.
- `TierTransitionUseCase` — seven sites across `explicitDowngrade`, `crisisDowngrade`,
  `ironCrisisExit`, `activateIron`, and the private `transition` helper, plus the shared
  private `requireUser(userId)` helper five of those six call. Every site's surrounding
  comment uses the same "precondition should already be satisfied by `selectInitialTier`" or
  "structurally impossible" framing; none is a bad-caller-input case (those, e.g.
  `ironCrisisExit`'s `require(currentTier == Tier.IRON)`, already correctly used `require`
  and were left untouched). `TierTransitionUseCaseTest`'s several real exception-type tests
  (cooldown-not-elapsed, tier-rejection, calibration-window) were each traced to their own
  `check(...)`/`require(...)` calls, none of them the six `requireNotNull` sites or the shared
  helper — confirmed unaffected. The helper itself renamed `requireUser` → `checkUser` for
  the same reason `CreateConstraintTriggerUseCase`'s naming already implied it should be:
  a function whose entire job is to throw `checkNotNull`'s exception type shouldn't carry
  `require`'s name. All six call sites and the kdoc paragraph explaining why the helper
  can't also assert `currentTier` non-null (a few lines below the helper) updated to match.
- `MissionInterceptionActivity` — one site: missing `user.currentTier`, after `mission`/`user`
  themselves are already null-checked separately via a plain `if (... == null) { finish() }`
  (a different, deliberately-graceful path, left alone). The comment states the same
  "structurally impossible... worth a loud crash" posture as the others. No test targets this
  Activity directly — `InterceptionControllerTest` exercises a different class
  (`InterceptionController`) entirely. No assertion risk.

**One file, one site, confirmed category (b) and deliberately left unchanged:**

- `TriggerCreationFragment`'s `requireNotNull(packageId)` guard. Unlike every site above, this
  isn't a missing-DB-row case — `packageId` is a plain nullable function parameter, and the
  comment states the precondition is enforced by `TriggerCreationScreen`'s own `canCreate` UI
  gate, not by a database invariant. The sibling guard three lines above it
  (`require(goalMission != null && goalMission.archetype == MissionArchetype.CONSTRAINT)`)
  describes the identical "screen-enforced, defense-in-depth" posture and deliberately uses
  `require`/`IllegalArgumentException`, and the class kdoc explicitly cites
  `TierSelectionFragment`'s `require(tier != Tier.IRON)` as the matching precedent for this
  exact pattern. Changing this one guard to `checkNotNull` while its neighbor stays `require`
  would split one function's two structurally-identical guards into two different exception
  types for no reason grounded in either guard's actual posture — left as `requireNotNull`,
  correctly, as-is.

**One stale cross-reference fixed as a side effect:** `CreateConstraintTriggerUseCase`'s own
kdoc (written in §5.42, before this audit existed) stated as fact that `RecordViolationUseCase`
"actually uses `requireNotNull`... not fixed... flagged as a real, separate follow-up." That
claim is no longer true as of this entry — updated to describe the audit as completed rather
than pending, and to name all five fixed files rather than singling out
`RecordViolationUseCase` alone.

**Not run: an actual compile.** Same standing gap as every other entry in this log — no
Kotlin/Gradle toolchain reachable from this sandbox (Gradle's own bootstrap fails outward
network calls here). Verification for this pass was manual: every changed call site's
surrounding kdoc/comment read in full before changing it, every test file in scope grepped for
exception-type assertions and each hit traced to confirm which specific `require`/`check`/
`requireNotNull` call it actually exercises (never assumed from proximity alone), and a brace-
balance check across all six touched files after editing. This is the same posture as every
prior entry's caveat — real, not closed by this pass, worth remaining on `STATUS.md`'s known
standing gaps list as a general sandbox limitation even though this specific finding is now
resolved.

### 5.44 G6: Milestone UI (Integration Plan §7, base design doc Addendum §B.2) — pushed, PR open, not yet merged

**Data layer was already secretly built.** `Milestone` entity, `MilestoneDao`
(`insert`/`update`/`forMission`), and full `DisciplineOsDatabase` wiring have existed since the
original G1 schema pass (`v9`) — contradicting `BUILD_PLAN.md`'s "NOT STARTED" status for this
batch, discovered only while starting this pass. What was actually missing, confirmed by
grepping `:domain` and `:app` for any reference, was the entire domain/UI layer: zero code
outside `:data` touched `Milestone` at all.

**Real bug found and fixed en route:** `Milestone.targetValue` shipped as `String?` since the
original G1 pass ("interpretation depends on the parent `GoalMission`'s target shape," per the
old in-code comment) — but Addendum §B.2's actual field list always specifies `Double?`. Same
category of finding as two prior entries in this log (§5.11's `MissionLogEntry` gap, §5.41's
`Trigger` shape fix): an unflagged divergence from the document the shipped code was meant to
summarize, caught only while implementing the batch that needed the real shape. Confirmed zero
call sites constructed a `Milestone` anywhere in the codebase before this fix, so no migration
risk. Added the also-missing `targetDate: Instant?` (present in the Addendum's field list,
absent pre-fix) in the same pass, since both fields trace to the same never-revisited gap.
`DisciplineOsDatabase` bumped `v13` -> `v14`; same `fallbackToDestructiveMigration()` reasoning
as every prior bump in that file's own version-history comment (still pre-launch, no real
installed base).

**Scope decisions, resolved rather than left as an open question a second time** (both
questions Addendum §B.3 explicitly leaves un-signed-off):
- **Person-authored milestones only, no auto-generation.** This project's established
  convention (`MissionProfile`'s kdoc rejecting an unfounded allowlist/blocklist split, this same
  log's own decision entries) is to not invent behavior a spec leaves open. Auto-generation
  (e.g. proposing evenly-spaced milestones for a cadenced mission) remains a real, named future
  option, not resolved by this pass.
- **Missed/regressed milestones are purely descriptive, never a consequence path.** Matches the
  entity's own already-documented "descriptive only, same boundary as `MissionLogEntry`" kdoc —
  a milestone being hit or missed never feeds Reputation, Discipline Debt, or any `LedgerEntry`.
  Once achieved, always achieved — no un-achieve path if a later log entry regresses (e.g. a
  numeric value creeping back past a crossed threshold); no spec language or product sign-off
  supports building one.

**Where the achievement check actually runs, and why it diverges from the Integration Plan's
literal framing:** §7 states `achievedAt` is "computed when a new `MissionLogEntry` crosses the
threshold" — implying a hook on the log-entry *write* path. Checked at implementation time: no
production code anywhere writes a `MissionLogEntry` yet (only a test constructs one), so that
write path doesn't exist to hook into. Given that, `milestoneAchievementSatisfied()` (a pure,
DB-free function in `Metrics.kt`, matching the `ironCalibrationSatisfied`/
`hypothesizingStageSatisfied` pattern already established in that file) runs instead on
`MissionDetailFragment`'s existing load pass — the one place `MissionLogEntry` rows for a
mission are actually read today, reused by both `loadMissionDetailState` and
`dismissTriggerPrompt` via a shared `checkAndPersistMilestoneAchievements` helper. A manual
"mark achieved" checkbox (`MilestoneRow`, one-directional only — disabled once already achieved)
covers the case the pure function can never resolve on its own: an ordinal-only milestone with
no numeric target.

**Shipped this pass:** `Milestone` entity fix + `v14` bump; `milestoneAchievementSatisfied()` +
full `MetricsTest.kt` coverage; `MilestoneCreationScreen.kt`/`MilestoneCreationFragment.kt`
(person-authored creation form, matching `TriggerCreationScreen`/`TriggerCreationFragment`'s
established shape exactly); milestones card + `MilestoneRow` on `MissionDetailScreen.kt`;
`MissionDetailFragment.kt` wiring (achievement check on load, manual-achieve write path, both
new callbacks); nav graph destination + action (`milestoneCreationFragment`, matching G5's
trigger-creation destination shape); `MissionDetailFragmentTest.kt` coverage for
`milestones`/`targetDirection` pass-through on `computeMissionDetailState`.

**Not run: an actual compile.** Same standing gap as every entry in this log back through
§5.41/§5.43 — no Kotlin/Gradle toolchain reachable from this sandbox. Verification was manual:
every edited file diffed against current `main` before changes were applied (confirming the
starting point matched what this entry describes, not a stale assumption), brace-balance checked
after editing, and every cross-file reference (`MilestoneDao` accessor name, `TargetDirection`
enum, `MissionLogEntry` field names) grepped and confirmed against the real files on disk rather
than assumed from memory of the spec docs alone.
