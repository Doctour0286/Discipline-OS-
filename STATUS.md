# DisciplineOS — Status at a Glance

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

**Last synced to ROADMAP.md:** 2026-08-08 (through §5.23 / Mission Profile Setup entry)

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
| 1 — Domain / Use-Case Layer | 🟢 | Functionally complete; one spec gap open (§5.9, tier-floor values missing from spec, not an engineering task). |
| 2 — Core Enforcement Loop | ✅ | Interception loop confirmed on-device via `DebugSeeder` (no real Mission-creation UI yet, so seeding is still required to trigger it). |
| 3 — Onboarding & Core UI | 🟡 | See screen-by-screen table below. Tier Selection/Confirmation and Mission Profile Setup exist on the unmerged `onboarding-tier-selection` branch — not yet on `main`. |
| 4 — Behavioral Fingerprint / Predictive Failure (F1–F5) | ⬜ | Not started. Depends on Phase 3's alert-card pattern existing to render into. |
| 5 — Pilot & Hypothesis Resolution | ⬜ | Not started. No new app code — this is "use it, gather data, resolve `[HYPOTHESIS]` constants." |

**Branch note:** as of this sync, `main` is 3 commits *behind* `onboarding-tier-selection`,
which has Tier Selection/Confirmation, Mission Profile Setup, and a restore of CI-workflow/
executable-bit state lost in an earlier zip round-trip. Treat `onboarding-tier-selection` as
current-state-of-truth until it's merged, not `main` alone.

---

## Onboarding screens (Phase 3), in flow order

| # | Screen | Status | Notes |
|---|---|---|---|
| 1 | Welcome / Product Philosophy | ⬜ | Placeholder (`OnboardingPlaceholderFragment`) |
| 2 | Goal Definition | ⬜ | Placeholder — **blocks** Mission Profile Setup's §2.8 default-suggestions requirement |
| 3 | Tier Explanation | ⬜ | Placeholder |
| 4 | Tier Selection | ✅ | Confirmed CI + device (on `onboarding-tier-selection`, unmerged) |
| 4a | ↳ Tier Confirmation | ✅ | Confirmed CI + device — Warden path only exercised so far |
| 4b | ↳ Iron Calibration Gate | ⬜ | Destination exists in nav graph; nothing routes to it yet (Iron path unexercised) |
| 5 | Mission Profile Setup | 🟡 | Written on `onboarding-tier-selection`, not yet CI-confirmed |
| 6 | Core Data Consent | ⬜ | Placeholder — next real screen to build |
| 7 | Unsupervised Reliability Opt-In | ⬜ | Placeholder |
| 8 | First Mission Scheduling | ⬜ | Placeholder |

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
| Reputation w/ decay-based demotion | 🟢 | `demotion_triggered` threshold values missing from spec (§5.9, open) |
| Four-Tier Enforcement + transitions | 🟢 | Upgrade/downgrade logic done; Iron path unexercised on-device |
| Mission Enforcement (lock/allowlist loop) | ✅ | Verified via `DebugSeeder`, not yet via real UI-created Missions |
| Distraction Interception | ✅ | |
| Mission Profiles | 🟡 | Setup screen written, not CI-confirmed; not yet wired into real Mission creation |
| Recovery Mode | ⬜ | Referenced by domain logic; no dedicated flow/UI |
| Reliability Index / Resistance Score / Focus Integrity | 🟢 | Formulas exist; no reporting UI surfaces them yet |
| Discipline Score | ✂️ | Deliberately cut from MVP (Data Model doc §3.1) — not a gap |
| AI Accountability (Warden + Recalibration Voice) | 🟡 | Gating logic + fallback bank exist; only `NoOpWardenVoiceGenerator` wired, no real cloud call |
| Behavioral Fingerprint + Predictive Failure Alerts | ⬜ | Phase 4, not started |
| Unsupervised Reliability (opt-in tracking) | ⬜ | Schema isolated (Phase 0); no capture/opt-in flow |
| Daily / Weekly Reports | ⬜ | Not started |

---

## Open decisions needing sign-off (full detail in ROADMAP.md §5)

| § | One-line summary |
|---|---|
| 5.5 | Shared-cause guard has no rolling-window cutoff — safe-direction default chosen, real window value still needed (Phase 5 data or explicit "doesn't matter" call) |
| 5.9 | `demotion_triggered`'s `tier_floor`/`N` values are **absent from the spec**, not just unvalidated — needs a spec-doc decision, not engineering |
| 5.10 | Crisis-stabilization pause reused to also gate Reputation decay, not just Debt — judgment call, not spec-stated |
| 5.15 | Explicit Downgrade's target tier (one-tier-down) — judgment call, not spec-stated |

---

## Known standing gaps (not blocking, but real)

- `OnboardingPlaceholderFragmentTest` doesn't exist despite `fragment-testing` dependency implying it does (either write it or drop the now-unjustified dependency).
- `androidx.security:security-crypto:1.1.0-alpha06` is an alpha version, pinned to match existing code rather than independently vetted.
- `onboarding-tier-selection` branch is unmerged and 3 commits ahead of `main` — until merged, anyone branching off `main` alone is missing real work, including the fix for a prior zip round-trip losing the CI workflow file and `gradlew`'s executable bit.
- The `Documents/` directory (PRD, data model, architecture, onboarding spec, crisis-boundary spec) referenced throughout `ROADMAP.md` §1 is **not checked into this repo** — only `docs/PHASE2_DEVICE_VERIFICATION.md` exists under version control. Worth deciding deliberately whether specs should be tracked here too, or intentionally kept elsewhere.
- No compiler in the authoring sandbox this file may be edited from — every new file ships as "manually cross-checked, not compiled" until CI confirms it.

---

## What's actually next (per ROADMAP.md §4, condensed)

1. Merge `onboarding-tier-selection` into `main` (or open/land its PR) — `main` is currently stale relative to real progress.
2. Confirm Mission Profile Setup's CI status on that branch.
3. On-device verify Mission Profile Setup the same way Tier Selection was verified.
4. Build Core Data Consent (§2.6) — Mission Profile Setup's nav action already points there.
5. Separately: build Goal Definition (§2.2) — it's the actual blocker for Mission Profile Setup's missing "default suggestions" behavior, not strictly ordered before/after #4.
