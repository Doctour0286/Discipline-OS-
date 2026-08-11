# DisciplineOS — Status at a Glance

## Handoff — read this first, every session

**If you're an AI assistant (or anyone) picking this project up cold, do this before
anything else:**

1. Clone `main` (or whatever branch you're told to use — but default to `main`).
2. Read this file top to bottom. It's short on purpose.
3. If anything here is ambiguous or seems stale, cross-check `ROADMAP.md` directly — it's
   the authoritative source, this file just summarizes it.
4. Run `bash scripts/check_status_sync.sh` from repo root. If it fails, this file is known
   stale — go read `ROADMAP.md`'s recent entries yourself before trusting anything below.
5. State what you think the current task/next-step is *before* starting work, so a human can
   correct a wrong read before time is spent on it, not after.

**What the human should provide at handoff, if not already obvious from the repo:**
- The actual task for this session (e.g. "build Core Data Consent," "resolve §5.9").
- Nothing else should be necessary. If you find yourself needing the human to re-explain
  project history, current state, or what's built — that's a bug in this file or in
  `ROADMAP.md`, not a normal handoff requirement. Flag it and fix the doc, don't just proceed
  on verbally-provided context that won't survive to the next session.

**What NOT to do:** don't ask for a zip of the project, don't ask the human to re-describe
what's been built. Both of those were the old workflow and are exactly what this file and
git history replaced. If a repo clone plus this file isn't enough to orient, that's a real
gap — say so, don't route around it by asking for a manual re-brief.

---

**This file is a derived view, not a source of truth.** `ROADMAP.md` is still the
authoritative record — its prose, decision log (§5), and reasoning are not duplicated here.
This file exists so "what's actually built and verified" is a 10-second read instead of a
scroll through ~2000 lines. If this table and `ROADMAP.md` ever disagree, `ROADMAP.md` wins
and this file has a bug — fix this file.

**Update this alongside `ROADMAP.md`, same commit or same PR, not after.** A status table
that drifts from the roadmap it's summarizing is worse than no table.

**This is enforced, not just requested:** `scripts/check_status_sync.sh` runs in CI
(`.github/workflows/build-and-test.yml`) and fails the build if `ROADMAP.md` has a dated
entry newer than this file's "Last synced" line below. It only catches "STATUS.md wasn't
touched at all" — it can't verify the *content* below still matches, that's still a human
judgment call each sync. See the script's own header for exactly what it does and doesn't
check.

**Last synced to ROADMAP.md:** 2026-08-11 (through §5.32 — Goal-Oriented Mission Model
accepted and folded into the spec docs, **docs-only, no code**. Four uploaded documents
(a base design doc, an engineering-ready integration plan, two now-superseded earlier drafts)
resolved into: `Documents/01_DATA_MODEL_AND_SCHEMA.md` §2.2a (new §2.2a section, plus a new §8
row), `Documents/06_GOAL_ORIENTED_MISSION_MODEL.md` (reduced to a historical pointer, per its
own §0 instruction), `Documents/06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` (checked in
unmodified), and `BUILD_PLAN.md` (new Batches G1–G6, dependency-graphed against A–F with an
explicit merge-collision warning since G1 renames the `Mission` table). See §5.32 for the full
account, including one call-site gap found in the uploaded plan and corrected before it reached
`BUILD_PLAN.md`. **Prior sync (2026-08-10, §5.31 — post-onboarding Home shell, Iron Calibration
flow, merged via PR #21) unchanged below**, carried forward as-is.)

---

## Legend

| Symbol | Meaning |
|---|---|
| ✅ | Written, CI-confirmed green, **and** on-device verified |
| 🟢 | Written and CI-confirmed green — on-device verification not yet done or N/A |
| 🟡 | Written, not yet CI-confirmed (manually cross-checked only) |
| ⬜ | Not started |
| ✂️ | Deliberately cut from MVP (a decision, not a gap) |

---

## Phases

| Phase | Status | Notes |
|---|---|---|
| 0 — Data Layer | ✅ | Entities, dual encrypted DBs, event-sourced Ledger, formula tests. |
| 0.5 — Real Build Verification (CI) | ✅ | Gradle/CI pipeline itself confirmed working. |
| 1 — Domain / Use-Case Layer | 🟢 | Functionally complete. §5.9's tier-floor gap resolved (see MVP table below) — no open spec gaps remain. |
| 2 — Core Enforcement Loop | ✅ | Interception loop confirmed on-device via `DebugSeeder` (no real Mission-creation UI yet, so seeding is still required to trigger it). |
| 3 — Onboarding & Core UI | ✅ | See screen-by-screen table below. Every onboarding screen except Iron Calibration Gate now has real content, is merged to `main`, and is confirmed **CI green and on-device** (PR #16, 2026-08-10) — all 9 screens now render via Compose (`ui/onboarding/*Screen.kt`), a single design system (`ui/theme/Color.kt`/`Type.kt`/`Theme.kt`, §5.26), no plain-XML-inflating onboarding screen remains. Iron Calibration Gate remains a placeholder — deliberately, not a gap (see row 4b below). **Post-onboarding hand-off (§5.31):** onboarding previously dead-ended (no outgoing action from its last screen, no post-onboarding destination anywhere) — a new `homeFragment` now closes that gap, plus a new `ironCalibrationFragment` giving `TierTransitionUseCase.activateIron` its first real call site. Merged to `main` via PR #21. See §5.31 for full detail; still 🟡 below pending CI + on-device confirmation on `main` specifically (merged ≠ confirmed — those are two separate checks this file tracks separately, same as every other entry in this table).
| 4 — Behavioral Fingerprint / Predictive Failure (F1–F5) | ⬜ | Not started. Depends on Phase 3's alert-card pattern existing to render into. |
| 5 — Pilot & Hypothesis Resolution | ⬜ | Not started. No new app code — this is "use it, gather data, resolve `[HYPOTHESIS]` constants." |

**Branch note (corrected 2026-08-10):** `design-system-compose-theme` (PR #15) and
`onboarding-compose-migration-batch2` (PR #16) are both merged to `main` — the design system
plus all 9 onboarding Compose screens are now on `main`, not on separate unmerged branches as
prior versions of this note described. Checked directly via `git log origin/main --oneline`
and `git branch -a` rather than inferred, per this note's own recurring advice below. No
onboarding-related branch is currently unmerged. Check `git branch -r` and
`git log main --oneline -10` for ground truth rather than trusting this note indefinitely —
prior versions of this exact note went stale twice in a row for the same reason.
`iron-calibration-gate-and-home-shell` (§5.31) **is now merged to `main`** via PR #21. This
note previously said "not yet merged, PR not yet opened" — that was correct when written and
became stale before the next sync. Standing advice unchanged: check `git branch -r` and
`git log main --oneline -10` yourself rather than trusting this note indefinitely.

---

## Onboarding screens (Phase 3), in flow order

| # | Screen | Status | Notes |
|---|---|---|---|
| 1 | Welcome / Product Philosophy | ✅ | Confirmed CI + device (PR #16). Merged to `main`. Compose (`WelcomeScreen.kt`), hosted via `ComposeView`. No persistence — pure disclosure screen. |
| 2 | Goal Definition | ✅ | Confirmed CI + device (PR #16). Has a test file (`GoalDefinitionFragmentTest.kt`). Compose (`GoalDefinitionScreen.kt`). No longer blocks Mission Profile Setup's §2.8 default-suggestions requirement now that it exists, though 2.8 itself hasn't been revisited to actually consume it yet — see `BUILD_PLAN.md`. |
| 3 | Tier Explanation | ✅ | Confirmed CI + device (PR #16). Merged to `main`. All four tiers side-by-side (Compose `LazyRow`, `TierExplanationScreen.kt`), enforcement copy read from `InterceptionPolicy`'s real values. |
| 4 | Tier Selection | ✅ | Confirmed CI + device. Merged to `main`. Compose (`TierSelectionScreen.kt`, PR #16) — Iron stays non-selectable (`enabled = false`), same guarantee as the pre-Compose XML version; `submitInitialTier`'s re-entry guard and id-reuse logic unchanged. |
| 4a | ↳ Tier Confirmation | ✅ | Confirmed CI + device — Warden path only exercised so far. Merged to `main`. Compose (`TierConfirmationScreen.kt`, PR #16); `confirmTierAndContinue()` unchanged. |
| 4b | ↳ Iron Calibration Gate | ⬜ | **Corrected 2026-08-09** (previous note was wrong): not simply "unrouted" — Iron's RadioButton/RadioButton-equivalent is disabled at Tier Selection itself, so Iron cannot be chosen at first-time onboarding at all, by design. The gate destination/action exist for a different, currently unmodeled flow (an existing user reaching Iron later via `TierTransitionUseCase.activateIron`), not for anything reachable in the current onboarding sequence. See `BUILD_PLAN.md` Batch B for detail. |
| 5 | Mission Profile Setup | ✅ | Confirmed CI + device (PR #16). Has a test file (`MissionProfileSetupFragmentTest.kt`). Compose (`MissionProfileSetupScreen.kt`); insert + re-entry guard unchanged. |
| 6 | Core Data Consent | ✅ | Confirmed CI + device (PR #16). Compose (`CoreDataConsentScreen.kt`). Also overwrites the placeholder `onboardingConsentVersion` written earlier at Tier Selection/Confirmation with a real version (`CoreDataConsentFragment.CONSENT_VERSION`, `"v1"`) — see that Fragment's kdoc. |
| 7 | Unsupervised Reliability Opt-In | ✅ | Confirmed CI + device (PR #16). Merged to `main` (originally PR #12). Compose (`UnsupervisedReliabilityOptInScreen.kt`); VIEWED/ACCEPTED/DECLINED instrumentation unchanged. Genuinely optional per §2.7/PRD §13.4 — Enable and Skip both route to the same next screen. Writes `User.unsupervisedReliabilityOptIn`/`optInAt`. `OnboardingScreenEvent` table (DB v6→v7) instruments completion/drop-off per the spec's own explicit ask — see `OnboardingScreenEvent.kt` kdoc. Has a test file (`UnsupervisedReliabilityOptInFragmentTest.kt`). |
| 8 | First Mission Scheduling | ✅ | Confirmed CI + device. Merged to `main` (originally PR #13; Compose migration in PR #15 as the design-system proof-of-concept, now itself CI + device confirmed). Closes onboarding — no outgoing nav action from this destination. Creates the first real `Mission` row (Start now vs. Schedule, setting `scheduledStart` meaningfully for the first time — see `FirstMissionSchedulingFragment`'s kdoc). Has a test file (`FirstMissionSchedulingFragmentTest.kt`). Two `[HYPOTHESIS]` judgment calls remain open, flagged in that kdoc and `ROADMAP.md` §5.24: a hardcoded default Mission duration, and reusing `ACTIVE` status for a not-yet-started scheduled Mission (no "scheduled" status exists in `MissionStatus`) — device confirmation exercises the happy path, it does not resolve either open judgment call. |

**All 9 onboarding screens now render via Compose** (`ui/onboarding/*Screen.kt`), replacing the
last plain-XML-inflating Fragments in this sequence — see `ROADMAP.md` §5.26/§5.27. Every
Fragment's business logic (DB writes, re-entry guards, nav routing, instrumentation) was carried
over unchanged; this was a presentation-layer migration only, confirmed by this pass's CI +
on-device pass, not a logic change needing separate re-verification.

**Other Phase 3 screens (not in the onboarding sequence):**

| Screen | Status |
|---|---|
| Mission Interception / Countdown | ✅ (built as part of Phase 2, not Phase 3) |
| Tribunal Screen | ⬜ |
| Dispute Flag Screen | ⬜ |
| Monthly Intelligence Report | ⬜ |
| Predictive Failure Alert card pattern | ⬜ (blocks Phase 4) |

---

## MVP feature list, rolled up

| Feature | Status | Notes |
|---|---|---|
| Discipline Debt + Ceiling, tier decay | ✅ | Formulas + Ledger + shared-cause guard |
| Reputation w/ decay-based demotion | 🟢 | `demotion_triggered` implemented (§5.9: 7 tier bands + N=3, `[HYPOTHESIS]`) — CI-confirmed green as of PR #13's merge, not yet device-verified |
| Four-Tier Enforcement + transitions | 🟡 | Upgrade/downgrade logic done (`:domain`, CI-confirmed). **Iron path now has a real UI call site (§5.31, this session)** — `home/IronCalibrationFragment` calls `activateIron()` — but this is written-not-yet-CI-confirmed, and the Iron path remains entirely unexercised on-device (never has been, per this row's prior note). Downgrades from `main` to 🟡 pending CI on §5.31, not a regression. |
| Mission Enforcement (lock/allowlist loop) | ✅ | Verified via `DebugSeeder`; First Mission Scheduling is now confirmed CI + on-device (PR #16) as the first screen that creates a real Mission row via actual UI rather than seeding |
| Distraction Interception | ✅ | |
| Mission Profiles | ✅ | Setup screen confirmed CI + on-device (PR #16); wired into real Mission creation via First Mission Scheduling (§2.9) |
| Post-Onboarding Home shell | 🟡 | §5.31 — did not exist before this pass. Minimal, not a dashboard: current tier + Iron eligibility card only. Merged to `main` via PR #21; still 🟡 (not ✅) because CI-on-`main` and on-device confirmation for this specific merge haven't been separately verified yet — merged is not the same claim as confirmed. |
| Recovery Mode | ⬜ | Referenced by domain logic; no dedicated flow/UI |
| Reliability Index / Resistance Score / Focus Integrity | 🟢 | Formulas exist; no reporting UI surfaces them yet |
| Discipline Score | ✂️ | Deliberately cut from MVP (Data Model doc §3.1) — not a gap |
| AI Accountability (Warden + Recalibration Voice) | 🟡 | Gating logic + fallback bank exist; only `NoOpWardenVoiceGenerator` wired, no real cloud call |
| Behavioral Fingerprint + Predictive Failure Alerts | ⬜ | Phase 4, not started |
| Unsupervised Reliability (opt-in tracking) | ✅ | Schema isolated (Phase 0); opt-in flow (`UnsupervisedReliabilityOptInFragment`, §2.7) merged and confirmed CI + on-device (PR #16) — writes `User.unsupervisedReliabilityOptIn`/`optInAt`. No capture pipeline (actual passive signal collection into `UnsupervisedSignalDao`) built yet — this pass is the consent/opt-in screen only, not the measurement pipeline itself. |
| Daily / Weekly Reports | ⬜ | Not started |
| Goal-Oriented Mission Model (`GoalMission`/`EnforcementSession` split) | ⬜ | **Accepted, docs-only (§5.32, 2026-08-11) — no code written yet.** Not part of PRD §41's MVP bar (post-v3.6 addition). Tracked as `BUILD_PLAN.md` Batches G1–G6, separate from the A–F sequence above. See `Documents/01_DATA_MODEL_AND_SCHEMA.md` §2.2a for the accepted shape. |

---

## Open decisions needing sign-off (full detail in ROADMAP.md §5)

**None open as of this sync.** §5.5, §5.9, §5.10, and §5.15 — the four items previously
listed here — were all resolved in a single product-owner sign-off session on 2026-08-09.
Full rationale for each lives in `ROADMAP.md` §5; not duplicated here.

| § | Resolved as |
|---|---|
| 5.5 | 3-day rolling window for the shared-cause guard |
| 5.9 | 7 Reputation tier bands (0–20/21–40/41–54/55–69/70–84/85–94/95–100) + `N=3` days, `[HYPOTHESIS]` |
| 5.10 | Reputation decay pauses during crisis-stabilization too, same as Debt accrual |
| 5.15 | Explicit Downgrade: one tier down, 24h rolling cooldown between uses |

**Implemented, this session** — §5.5, §5.9, and §5.15 are now coded, not just decided:
`RecordViolationUseCase` (3-day window), `ApplyReputationDecayUseCase` + `ReputationDecayPolicy`
+ `User.consecutiveDaysBelowFloor` (tier bands + demotion_triggered), `TierTransitionUseCase`
+ `User.lastExplicitDowngradeAt` (24h cooldown). DB bumped v4→v5 for the two new `User` fields.
Written but **not yet run through a real compiler** — no Gradle/Android toolchain reachable
from the authoring sandbox (same standing gap noted elsewhere in this file). Run
`./gradlew :domain:test :data:test` before merging.

**Reviewed and corrected before merge (separate session, same day):** two issues found by
manually tracing the code against its own tests before applying — (1) a real inverted-boolean
bug in the §5.15 cooldown check that would have blocked available attempts and allowed
mid-cooldown ones, now fixed; (2) §5.9's demotion firing bypassed `TierTransitionUseCase`
instead of reusing it, now fixed to call `standardDowngrade` properly. Full detail in
`ROADMAP.md`'s matching entry. Neither issue would have been caught without this review step —
worth remembering for every future patch, not just this one.

**One open judgment call from this work, flagged for sign-off before Phase 5:** §5.9's bands
describe Reputation *value* ranges, not tiers — nothing in the PRD/Data Model doc states which
band each tier's "floor" is. Implementation assumes tier rank position maps to band rank
position (Operator's floor = INCONSISTENT, Warden's = RELIABLE, Iron's = DISCIPLINED). See
`ApplyReputationDecayUseCase`'s kdoc for the full reasoning — confirm or correct this mapping.

---

## Known standing gaps (not blocking, but real)

- `OnboardingPlaceholderFragmentTest` doesn't exist despite `fragment-testing` dependency implying it does (either write it or drop the now-unjustified dependency).
- `androidx.security:security-crypto:1.1.0-alpha06` is an alpha version, pinned to match existing code rather than independently vetted.
- ~~The `Documents/` directory (PRD, data model, architecture, onboarding spec, crisis-boundary spec) referenced throughout `ROADMAP.md` §1 is **not checked into this repo**~~ — **corrected 2026-08-11 (§5.32): this was false as of this pass.** All six spec docs (PRD, data model, architecture, onboarding spec, behavioral fingerprint spec, crisis-boundary spec) are tracked under `Documents/`, confirmed directly via `ls Documents/` rather than trusted from this note. Unclear how long this note had already been stale before being caught — flagging that as a reminder to spot-check "known standing gaps" entries occasionally rather than assume they age correctly on their own.
- No compiler in the authoring sandbox this file may be edited from — every new file ships as "manually cross-checked, not compiled" until CI confirms it.
- `MainActivity`/`activity_main.xml` still carries the pre-Compose
  `@android:style/Theme.NoTitleBar.Fullscreen` Activity theme rather than a Compose-first
  equivalent. Not resolved by §5.29's theme deduplication — that pass consolidated per-Fragment
  theme application into one shared helper (`themedComposeView`), but didn't touch the Activity
  theme itself, since `MainActivity` has no Activity-level Compose tree to move it into without a
  Compose Navigation rewrite (see §5.29's own investigation). Low priority — no observed visual
  or functional issue from the mismatch, just a naming/consistency loose end.

---

## What's actually next (per ROADMAP.md §4, condensed)

**The entire onboarding sequence is now done, in the fullest sense this project has used that
word for anywhere else: merged to `main`, CI-confirmed green, and on-device confirmed** (PR #16,
2026-08-10). All 9 screens (Welcome through First Mission Scheduling) render via Compose on a
single design system; only Iron Calibration Gate remains a placeholder, deliberately (row 4b —
that's a different, currently-unbuilt flow, not an onboarding gap). Onboarding as a body of work
is closed; what's left below is cleanup and the next phase, not more onboarding screens.

Both cleanup items flagged when onboarding's Compose migration finished (dead XML layouts,
per-Fragment theme duplication) are done — §5.28 and §5.29. Mission Profile Setup's default
suggestions are also done — §5.30. The Iron Calibration real-destination flow named below as
item 2 is also done and merged — §5.31, PR #21. **Correction:** an earlier version of this
section said all of the above were "pending push + CI + on-device confirmation" — that's now
inaccurate for the merge step specifically (§5.28–§5.31 are all on `main`); CI-on-`main` and
on-device confirmation are the remaining open piece, not the merge itself. Real remaining
items, in rough priority order:

1. **Resolve the two `[HYPOTHESIS]` items §5.24 flagged**: a hardcoded default Mission
   duration with no spec source, and reusing `ACTIVE` status for a scheduled-but-not-yet-
   started Mission (no dedicated status exists for that state). Neither blocks anything, but
   both are open judgment calls a product-owner sign-off pass (like the one that closed
   §5.5/§5.9/§5.10/§5.15) could resolve properly instead of leaving as engineering defaults.
2. **~~Build the Iron Calibration Gate's real destination flow~~ — done, §5.31, pending CI +
   on-device confirmation.** A new `homeFragment` (also new — onboarding had no post-completion
   destination at all until this pass) hosts an Iron eligibility card that opens a new
   `ironCalibrationFragment`, which calls `TierTransitionUseCase.activateIron` directly — the
   first real UI call site that use-case has ever had. Not the onboarding-time
   `ironCalibrationGateFragment` placeholder, which stays unreachable by design (row 4b) — see
   §5.31 for why these are deliberately two separate things. Graduates this row to fully
   struck-through, not just noted, once CI + on-device both confirm it.
3. **Phase 4 (Behavioral Fingerprint / Predictive Failure)** — the next full *phase*, not a
   screen-level item. Depends on Phase 3's alert-card pattern (§3.5 of the Onboarding spec
   already defines the pattern in full) existing to render into, which nothing above blocks
   structurally. This is the largest genuinely new chunk of work once item 1 settles, and the
   natural "what's next" once onboarding cleanup and its remaining open loose ends close.
