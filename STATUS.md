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

**Last synced to ROADMAP.md:** 2026-08-09 (through §5.24 — First Mission Scheduling, STATUS.md branch-note correction)

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
| 3 — Onboarding & Core UI | 🟡 | See screen-by-screen table below. Every onboarding screen except Iron Calibration Gate now has real content. Welcome, Goal Definition, Tier Explanation, Tier Selection/Confirmation, Mission Profile Setup, Core Data Consent, and Unsupervised Reliability Opt-In are merged to `main`. First Mission Scheduling is written this session (branch `onboarding-first-mission-scheduling`, see `ROADMAP.md` §5.24), not yet merged. Iron Calibration Gate remains a placeholder — deliberately, not a gap (see row 4b below). |
| 4 — Behavioral Fingerprint / Predictive Failure (F1–F5) | ⬜ | Not started. Depends on Phase 3's alert-card pattern existing to render into. |
| 5 — Pilot & Hypothesis Resolution | ⬜ | Not started. No new app code — this is "use it, gather data, resolve `[HYPOTHESIS]` constants." |

**Branch note (corrected 2026-08-09, was stale twice over):** `onboarding-tier-selection`
merged to `main` some time ago — this section previously said otherwise. `main` also now has
`add-status-sync-check`, `resolve-open-decisions`, `merge-welcome-and-tier-explanation`
(PR #10), `core-data-consent-screen` (PR #11), and `onboarding-unsupervised-reliability-opt-in`
(PR #12) all merged — the last of these was still listed as unmerged in the previous version
of this note, which was itself already wrong about two other branches. `implement-decided-
follow-ups` and `build-plan-document`, previously listed here as unmerged, no longer exist as
remote branches at all — most likely merged and deleted, but not independently confirmed by
this pass; if their content matters, verify via `git log main` rather than trusting that
inference. Check `git branch -r` and `git log main --oneline -10` for ground truth rather than
trusting this note indefinitely — this exact staleness, twice in a row, is why that check is
worth doing every session rather than skipping it.

---

## Onboarding screens (Phase 3), in flow order

| # | Screen | Status | Notes |
|---|---|---|---|
| 1 | Welcome / Product Philosophy | 🟡 | Merged to `main` (2026-08-09, `merge-welcome-and-tier-explanation`). Written, not yet CI-confirmed. No persistence — pure disclosure screen. |
| 2 | Goal Definition | 🟡 | Written, not yet CI-confirmed. Has a test file (`GoalDefinitionFragmentTest.kt`). No longer blocks Mission Profile Setup's §2.8 default-suggestions requirement now that it exists, though 2.8 itself hasn't been revisited to actually consume it yet — see `BUILD_PLAN.md`. |
| 3 | Tier Explanation | 🟡 | Merged to `main` (2026-08-09, `merge-welcome-and-tier-explanation`). Written, not yet CI-confirmed. All four tiers side-by-side, enforcement copy read from `InterceptionPolicy`'s real values. |
| 4 | Tier Selection | ✅ | Confirmed CI + device. Merged to `main` (was previously noted unmerged — stale, corrected) |
| 4a | ↳ Tier Confirmation | ✅ | Confirmed CI + device — Warden path only exercised so far. Merged to `main` |
| 4b | ↳ Iron Calibration Gate | ⬜ | **Corrected 2026-08-09** (previous note was wrong): not simply "unrouted" — Iron's RadioButton is disabled at Tier Selection itself, so Iron cannot be chosen at first-time onboarding at all, by design. The gate destination/action exist for a different, currently unmodeled flow (an existing user reaching Iron later via `TierTransitionUseCase.activateIron`), not for anything reachable in the current onboarding sequence. See `BUILD_PLAN.md` Batch B for detail. |
| 5 | Mission Profile Setup | 🟡 | Merged to `main`, has a test file (`MissionProfileSetupFragmentTest.kt`) — CI status on `main` itself not independently re-confirmed by this correction pass; verify before assuming green. |
| 6 | Core Data Consent | 🟡 | Written this session, not yet CI-confirmed (no compiler in this authoring sandbox — verified via static/manual review only, see the PR). Also overwrites the placeholder `onboardingConsentVersion` written earlier at Tier Selection/Confirmation with a real version (`CoreDataConsentFragment.CONSENT_VERSION`, `"v1"`) — see that Fragment's kdoc. |
| 7 | Unsupervised Reliability Opt-In | 🟡 | Merged to `main` (PR #12, `onboarding-unsupervised-reliability-opt-in`) — previously listed here as unmerged; corrected. Still not CI-confirmed (no compiler in this authoring sandbox — verified via static/manual review only, see the PR). Genuinely optional per §2.7/PRD §13.4 — Enable and Skip both route to the same next screen. Writes the previously-unused `User.unsupervisedReliabilityOptIn`/`optInAt` fields. New `OnboardingScreenEvent` table (DB v6→v7) instruments completion/drop-off per the spec's own explicit ask — see `OnboardingScreenEvent.kt` kdoc for why this is a narrow, screen-scoped log rather than general analytics. Has a test file (`UnsupervisedReliabilityOptInFragmentTest.kt`). |
| 8 | First Mission Scheduling | 🟡 | Written this session (branch `onboarding-first-mission-scheduling`), not yet CI-confirmed (no compiler in this authoring sandbox — verified via static/manual review only, see `ROADMAP.md` §5.24 and the PR). Closes onboarding — no outgoing nav action from this destination. Creates the first real `Mission` row (Start now vs. Schedule, setting `scheduledStart` meaningfully for the first time — see `FirstMissionSchedulingFragment`'s kdoc). Has a test file (`FirstMissionSchedulingFragmentTest.kt`). Two `[HYPOTHESIS]` judgment calls flagged in that kdoc: a hardcoded default Mission duration, and reusing `ACTIVE` status for a not-yet-started scheduled Mission (no "scheduled" status exists in `MissionStatus`). |

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
| Reputation w/ decay-based demotion | 🟡 | `demotion_triggered` implemented (§5.9: 7 tier bands + N=3, `[HYPOTHESIS]`) — written, not yet CI/device-verified (no compiler in authoring sandbox) |
| Four-Tier Enforcement + transitions | 🟢 | Upgrade/downgrade logic done; Iron path unexercised on-device |
| Mission Enforcement (lock/allowlist loop) | ✅ | Verified via `DebugSeeder`; First Mission Scheduling (§2.9, this session) is the first screen that creates a real Mission row via actual UI rather than seeding — not yet on-device verified through that specific path |
| Distraction Interception | ✅ | |
| Mission Profiles | 🟡 | Setup screen written, not CI-confirmed; not yet wired into real Mission creation |
| Recovery Mode | ⬜ | Referenced by domain logic; no dedicated flow/UI |
| Reliability Index / Resistance Score / Focus Integrity | 🟢 | Formulas exist; no reporting UI surfaces them yet |
| Discipline Score | ✂️ | Deliberately cut from MVP (Data Model doc §3.1) — not a gap |
| AI Accountability (Warden + Recalibration Voice) | 🟡 | Gating logic + fallback bank exist; only `NoOpWardenVoiceGenerator` wired, no real cloud call |
| Behavioral Fingerprint + Predictive Failure Alerts | ⬜ | Phase 4, not started |
| Unsupervised Reliability (opt-in tracking) | 🟡 | Schema isolated (Phase 0); opt-in flow now written this session (`UnsupervisedReliabilityOptInFragment`, §2.7), not yet CI-confirmed or merged — writes `User.unsupervisedReliabilityOptIn`/`optInAt`. No capture pipeline (actual passive signal collection into `UnsupervisedSignalDao`) built yet — this pass is the consent/opt-in screen only, not the measurement pipeline itself. |
| Daily / Weekly Reports | ⬜ | Not started |

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
- `onboarding-tier-selection` branch is unmerged and 3 commits ahead of `main` — until merged, anyone branching off `main` alone is missing real work, including the fix for a prior zip round-trip losing the CI workflow file and `gradlew`'s executable bit.
- The `Documents/` directory (PRD, data model, architecture, onboarding spec, crisis-boundary spec) referenced throughout `ROADMAP.md` §1 is **not checked into this repo** — only `docs/PHASE2_DEVICE_VERIFICATION.md` exists under version control. Worth deciding deliberately whether specs should be tracked here too, or intentionally kept elsewhere.
- No compiler in the authoring sandbox this file may be edited from — every new file ships as "manually cross-checked, not compiled" until CI confirms it.

---

## What's actually next (per ROADMAP.md §4, condensed)

Everything through Core Data Consent, Goal Definition, and Unsupervised Reliability Opt-In is
merged to `main` (corrected this session — see the Branch note above). **Done, this session:**
First Mission Scheduling (§2.9) — written, branch `onboarding-first-mission-scheduling`, not
yet merged (see `ROADMAP.md` §5.24 and the PR). That closes the onboarding sequence itself —
every screen except Iron Calibration Gate (deliberately deferred, row 4b) now has real content.

Real remaining items, in rough priority order:

1. **Merge `onboarding-first-mission-scheduling`** once CI confirms it green.
2. **Revisit Mission Profile Setup (§2.8)** to wire in Goal Definition's flagged-categories
   default suggestions — unblocked since Goal Definition merged, but still not consumed.
3. **On-device verify First Mission Scheduling** specifically the real-Mission-creation path
   (Start now and Schedule both) — this is the first screen that exercises Mission creation
   through actual UI rather than `DebugSeeder`, so it's a meaningfully higher-value on-device
   check than most onboarding screens got.
4. **Resolve the two `[HYPOTHESIS]` items §5.24 flagged**: a hardcoded default Mission
   duration with no spec source, and reusing `ACTIVE` status for a scheduled-but-not-yet-
   started Mission (no dedicated status exists for that state).
5. **CI-confirm the still-unconfirmed screens** generally (Welcome, Goal Definition, Tier
   Explanation, Core Data Consent, Unsupervised Reliability Opt-In, First Mission Scheduling) —
   several sessions in a row have shipped "written, not compiled" work; a real CI pass across
   all of it at once is overdue.
6. **Phase 4 (Behavioral Fingerprint / Predictive Failure)** can start once the above settles —
   it depends on Phase 3's alert-card pattern (§3.5 of the Onboarding spec already defines the
   pattern in full) existing to render into, which nothing above blocks structurally.
