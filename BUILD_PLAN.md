# DisciplineOS — Remaining Build Plan

## How to use this document

This is the plan from current state to MVP feature-complete (PRD §41), batched into chunks
sized so each one is independently reviewable and CI-verifiable before the next starts. It
does **not** replace `ROADMAP.md` (the authoritative build log and decision record) or
`STATUS.md` (the fast-read current-state summary) — it exists alongside them as the forward
-looking counterpart: those two answer "where are we," this answers "what's left and in what
order."

**Keep this in sync the same way `STATUS.md` is kept in sync with `ROADMAP.md`:** when a
batch starts, is reviewed, or merges, update that batch's status line here in the same
commit/PR as the actual work — don't let this drift into aspirational fiction the way a
zip-handoff-only status document would. This file doesn't have its own automated drift check
the way `STATUS.md` does (see `scripts/check_status_sync.sh`) — that's a real gap, not an
oversight; consider adding one if this file proves worth maintaining past the first few
batches.

**Batch discipline, non-negotiable:** each batch gets its own branch off `main`, its own PR,
and passes CI before the next batch starts work. No batch should be built on top of an
unmerged prior batch's branch unless explicitly noted as a dependency below (this happened
once already — `add-status-sync-check` was correctly branched off the then-unmerged
`onboarding-tier-selection` — and it's fine when the dependency is real and stated, not fine
as a default habit).

---

## Dependency order and why

```
Batch A (spec decisions)          — DONE, see ROADMAP.md §5.5/§5.9/§5.10/§5.15
Batch A.5 (implement A's decisions) — DONE, see "implement-decided-follow-ups" branch
        │
        ▼
Batch B (onboarding screens, 6 remaining)
        │
        ▼  (can partially overlap with B — no shared files, see note below)
Batch C (in-product screens: Tribunal, Dispute, Monthly Report, Predictive Failure Alert card)
        │
        ▼
Batch D (Recovery Mode)  ◄── independent of C, could run in parallel with C or even B
        │
        ▼
Batch E (Behavioral Fingerprint, F1–F5)  ◄── HARD dependency on C's alert-card pattern (§3.5)
        │
        ▼
Batch F (Daily/Weekly Reports + real AI Voice generation)
        │
        ▼
MVP feature-complete (PRD §41) → Phase 5 (pilot, no new code, resolves [HYPOTHESIS] constants)
```

**Why E cannot move earlier:** `ROADMAP.md` Phase 4 states Behavioral Fingerprint "depends on
Phase 3's alert-card pattern existing to render into" — and the Onboarding/Consent spec §3.5
confirms why: F1, F2, F3, and F5 all render through *one shared card pattern* that Batch C
builds. Building F1–F5's logic before that pattern exists means building alert UI twice.

**Why D is genuinely independent:** Recovery Mode (PRD §29) triggers off Discipline Score,
severe violations, failed days, or Debt Ceiling — all Phase 0/1 machinery that already
exists. It needs no output from B, C, or E. It's placed after C here purely because C is
higher-leverage per the original speed conversation, not because of a real blocking
dependency — if you'd rather pull D forward, that's a legitimate reordering.

**Why A.5 (implementing the sign-off decisions) had to happen before this document was
written:** this document cites current file/method names throughout (e.g. Batch D's
trigger conditions, Batch E's alert-card integration points) — writing it against
soon-to-change internals would have made it stale before it was ever used.

---

## Batch A — Spec decisions (§5.5, §5.9, §5.10, §5.15)

**Status: DONE.** Recorded in `ROADMAP.md` under each section, dated 2026-08-09.

No code, no branch of its own conceptually — this was a product-owner sign-off session.
Listed here only so the dependency chain above is complete and legible on its own.

---

## Batch A.5 — Implement Batch A's decisions

**Status: DONE**, on branch `implement-decided-follow-ups` (PR open, pending your merge as of
this writing — check `git branch -r` / the repo's Pull Requests tab for current state).

**What it contains:**
- `RecordViolationUseCase`: 3-day rolling window for the shared-cause guard (§5.5)
- `ReputationDecayPolicy` + new `ReputationBand` enum + `ApplyReputationDecayUseCase`: the 7
  tier bands, `N=3` consecutive-day counter, and real `demotion_triggered` firing via
  `TierTransitionUseCase.standardDowngrade` (§5.9)
- `TierTransitionUseCase.explicitDowngrade`: 24h rolling cooldown, `User.lastExplicitDowngradeAt`
  (§5.15)
- DB bumped v4→v5 for two new `User` fields — **only true on the `implement-decided-follow-ups`
  branch, not yet on `main`** as of this document's original writing (`main` is still at v4).

**UPDATE 2026-08-09, escalating this from a two-way to a THREE-way v5 collision:** the
`batch-b-onboarding-screens` branch (this session, Goal Definition) *also* bumps v4→v5,
independently, for a completely different reason — `User.currentTier`/`tierSelectedAt`/
`tierActivationAt`/`onboardingConsentVersion` becoming nullable (see this document's Batch B
table, §2.2 row, for the full account of why). None of these three v5s agree with each other.
Whichever of `implement-decided-follow-ups` / `batch-b-onboarding-screens` merges to `main`
second (in either order) WILL need a manual v6 bump and a real rebase-and-review, not a
mechanical merge-tool resolution — the two schema changes are both real and both needed, they
just can't both claim v5. Flagging this loudly, here, before it's discovered as a surprise
merge conflict: whoever handles that merge should re-read both branches' `User.kt`/
`DisciplineOsDatabase.kt` diffs together, not just take "theirs" or "ours."

**Reviewed before merge** (a real inverted-boolean bug in the cooldown check, and a
design-quality fix to stop bypassing `TierTransitionUseCase`) — see `ROADMAP.md`'s
2026-08-09 "reviewed before merge" entry for full detail. This is the concrete example this
whole plan's batching discipline is built to catch early: review happened *before* merge,
not after it shipped.

**One open item carried forward, not blocking:** the tier↔band floor correspondence
(Operator's floor = INCONSISTENT, Warden's = RELIABLE, Iron's = DISCIPLINED) is a reasonable
reading, not spec-stated — flagged in that class's kdoc, worth a product-owner confirmation
pass before Phase 5, but doesn't block any batch below.

**Verification checklist:**
- [ ] PR merged to `main`
- [ ] `./gradlew :domain:test :data:test` run and green (not yet done by any session — no
      Gradle/Android toolchain has been reachable from any authoring sandbox so far; this
      needs to happen on a machine that has one, or via the existing GitHub Actions CI)
- [ ] On-device smoke test: trigger a real demotion via 3 consecutive low-Reputation days,
      confirm the tier actually drops and a `TierEvent` is recorded

---

## Batch B — Onboarding screens (6 remaining)

**Status: NOT STARTED.**

**Scope** (Onboarding/Consent spec §2, screen numbers as given there):

| # | Screen | Real requirement, not just a placeholder to fill |
|---|---|---|
| 2.1 | Welcome / Product Philosophy | Must state plainly that Missions restrict phone function and that higher tiers include confrontational language by design. Explicitly **no urgency/dark-pattern copy** — flagged as an app-review risk elsewhere in the specs, and this is the one screen every user sees regardless of tier choice. |
| 2.2 | Goal Definition | **Written, not yet CI-confirmed (2026-08-09).** Free-text + structured tags for high-value/high-risk categories. This output is a **hard input** to §2.7's Unsupervised Reliability scope later — UI must make that link visible to the user (done, `goal_definition_link_note`). **Real sequencing bug found and fixed while building this screen:** this screen runs before any `User` row exists (previously only created at Tier Confirmation, screen 4a) — so its data had nowhere to persist. Fixed by having this screen create a "draft" `User` row itself; `User.currentTier`/`tierSelectedAt`/`tierActivationAt`/`onboardingConsentVersion` are now nullable to represent that pre-tier-selection state honestly (DB v4→v5 on this branch — see the merge-hazard note in Batch A.5 above, now a THREE-way v5 collision across three unmerged branches, not two). `TierTransitionUseCase.selectInitialTier` updates that draft row in place rather than always inserting; `TierSelectionFragment`/`TierConfirmationFragment`'s re-entry guards corrected to match (was "does a row exist," now "has a tier been selected"). |
| 2.3 | Tier Explanation | **Written, not yet CI-confirmed (this pass).** All four tiers rendered side-by-side in a horizontal scroll strip (not a vertical/sequential list), one identical card template per tier so no tier can end up visually coded as more/less serious. Enforcement copy per tier is read directly from `InterceptionPolicy`'s real countdown/dismissal/reason-entry values, not invented. Voice-tone copy quotes an actual `FallbackVoiceBank` line. Warden/Iron's no-casual-exit disclosure — spec's own "single most important disclosure in the whole flow" — gets its own dedicated line per tier rather than being folded into the enforcement paragraph. |
| 2.4 | Tier Selection | Already built (`onboarding-tier-selection`, merged). Listed here only for flow completeness. |
| 2.4a | Tier Confirmation | Already built, merged. Warden path confirmed on-device; **Iron path still unexercised** — worth a real on-device pass once 2.5 exists, since Iron routes through it. |
| 2.5 | Iron Calibration Gate | **Correction after checking the real nav graph (not just grepping for it):** this isn't simply "unrouted." `TierSelectionFragment`'s Iron option is disabled in the layout itself — Iron cannot be *selected* at first-time onboarding at all, by design (§12.6, no exception path), so `action_tierSelection_to_ironCalibrationGate` has no code path that can ever fire it. The destination and action are deliberately left in the graph for a *different*, currently unmodeled flow: an existing user reaching Iron later, once their calibration window elapses, via `TierTransitionUseCase.activateIron` (already built and tested). Building this screen's content is still real work, but building it alone won't make it reachable — that also needs the separate "Tier Selection outside onboarding" flow, which doesn't exist yet in any nav graph. Worth deciding whether that flow is in-scope for this batch or its own follow-up. Whenever it's built, must frame the 10-day calibration window as "protecting the parameters that will govern Iron," not as the system doubting the user — same behavior-vs-identity principle as Warden Voice, applied to system copy. |
| 2.6 | Core Data Consent | Standard local-storage/Mission-enforcement consent, required to use the app. Plain-language local-first + optional cloud sync explanation. |
| 2.7 | Unsupervised Reliability Opt-In | **Must be its own screen**, not bundled into 2.6 — explicit PRD §13.4 requirement, no "agree to everything" screen. Must state: measurement only, never enforcement, separately deletable anytime, only covers §2.2's flagged categories. Previews the monthly Brief Self-Control Scale self-report by name. **Instrument completion/drop-off from day one** — the PRD itself flags this rate as an open question. |
| 2.8 | Mission Profile Setup | Already built (schema v4), not yet CI-confirmed per `STATUS.md`. Should default suggestions from §2.2's flagged categories — **this is currently blocked by 2.2 not existing yet**, so building 2.2 unblocks 2.8's stated requirement even though 2.8's screen itself already has code. |
| 2.9 | First Mission Scheduling | Closes onboarding. Schedule-vs-start-now choice here is the first Self-Initiation Trend data point (measurement-only, doesn't change this screen's design). |

**Batching approach:** build a shared scaffold once (layout conventions, Fragment base
class or composable pattern, nav-graph entry shape, strings.xml conventions, DAO
round-trip test shape) extracted from how Tier Selection/Confirmation and Mission Profile
Setup were actually built — then apply it to 2.1, 2.2, 2.3, 2.5, 2.6, 2.7, 2.9 (2.4/2.4a
already exist; 2.8 already exists but gets revisited once 2.2 unblocks its default-suggestion
requirement).

**Suggested internal ordering within this batch** (not a hard dependency chain — mostly
sequencing to unblock 2.8's stated gap early):
1. Extract the shared scaffold (no user-visible output, but de-risks everything after)
2. 2.2 Goal Definition — unblocks 2.8's default-suggestions requirement
3. 2.1 Welcome, 2.3 Tier Explanation — no dependencies, can be built in either order or
   parallel
4. 2.5 Iron Calibration Gate — exercises the currently-untested Iron path through 2.4a
5. 2.6 Core Data Consent — Mission Profile Setup's nav action already points here per
   `STATUS.md`
6. 2.7 Unsupervised Reliability Opt-In
7. 2.9 First Mission Scheduling
8. Revisit 2.8 to wire in 2.2's default suggestions

**Verification checklist (per screen, not just once for the batch):**
- [ ] CI green
- [ ] On-device confirmed (per `STATUS.md`'s legend: 🟡 written → 🟢 CI-green → ✅ on-device)
- [ ] DAO round-trip test exists and passes
- [ ] For 2.7 specifically: completion/drop-off instrumentation actually wired, not just
      planned

---

## Batch C — In-product screens

**Status: NOT STARTED.**

**Scope** (Onboarding/Consent spec §3):

### 3.1 Mission Interception / Countdown
Already built as part of Phase 2. Listed for completeness — not new work in this batch.

### 3.2 Tribunal Screen (PRD §30, mandatory at Warden/Iron)
- **Recalibration Voice only** — structurally enforced, never Warden Voice, "regardless of
  the tier or severity of the violation that triggered it" (§30.1, v3.6 addition). This is a
  hard requirement grounded in real research the spec cites (After-Action Review, blameless
  postmortem practice) — mandatory reviews conducted without psychological safety produce
  self-protective (inaccurate) answers, which defeats the Tribunal's entire diagnostic
  purpose.
- Structured format, not a blank text box: what was the commitment, what happened, what does
  the user think changed, what adjusts going forward.
- "What protection failed?" must be implemented as parallel to "was it avoidable," not as an
  implicit accusation — the resulting Behavioral Correction Plan should be able to conclude
  either "the protection was insufficient" or "an avoidable choice was made," never defaulting
  to the latter.

### 3.3 Dispute Flag Screen (PRD §26.4)
- Reachable directly from a Violation record.
- On submission: **immediate visible confirmation** that `consequence_paused = true` has taken
  effect — not a silent backend flag the user has to trust happened.

### 3.4 Monthly Intelligence Report (PRD §34)
- The **only** place Unsupervised Reliability Trend, Self-Initiation Trend, and self-report
  data surface — "no default visibility" is a hard rule (PRD §13.3), not a design preference.
- Self-Initiation Trend and the raw self-initiated-starts count must be **visually distinct**
  elements, not collapsed into one metric (open question the spec explicitly carries).
- Debt Ceiling quartile markers render display-only, and must **not** read as gamified
  reward-progress — the PRD's own v3.6 note is explicit that goal-gradient research doesn't
  cleanly map onto an aversive ceiling; the visual design shouldn't oversell a motivational
  framing the mechanic doesn't support.

### 3.5 Predictive Failure Alert UI pattern — **build this even though F1–F5 aren't built yet**
This is the piece Batch E is hard-blocked on, so it belongs in this batch, not that one.

- **One shared card pattern** used by F1, F2, F3, and F5 (F4 has no UI surface for MVP — see
  Batch E). Renders in exactly one place: a dedicated, dismissible card on the home/dashboard
  screen, checked on app open and after each Mission completion. **Never** on the Mission
  Interception screen — predictive-pattern alerts and in-the-moment enforcement are a
  distinction the rest of the system works hard to keep clean, and merging them into one
  surface would blur it.
- **Card anatomy, same for every rule:**
  1. One observation sentence, exact advisory language per rule (Fingerprint doc §3 has the
     actual copy) — no "Warning"/"Alert" badge language, which would smuggle interception-
     screen urgency onto a reflective surface.
  2. One follow-up action, rule-dependent (F1→evening Mission Profile review, F2→profile
     scope/allowlist review, F3→direct link into Recovery Mode, F5→Mission Profile Drift
     review). This is exactly why Batch D (Recovery Mode) should exist before F3 can fully
     resolve, even though this batch (C) doesn't strictly need D to build the *card itself*.
  3. **Two separate dismissal controls**, not one: "Not accurate" (logged, feeds the
     Fingerprint doc's required accuracy tracking) and "Got it" (acknowledges without
     disputing). Collapsing these into one "Dismiss" loses the accuracy signal the rules spec
     requires from day one.
- **Explicit non-goals for this pattern:** no stacking multiple alerts into one card (one
  rule, one card, sequential if more than one triggers); no severity color coding (all
  neutral — a red card for F2 would undo that rule's own "this might be a mis-scoped Mission,
  not a discipline failure" framing, regardless of the copy underneath).

**Suggested internal ordering:** 3.5 (alert card) first, since it unblocks Batch E entirely
and has no dependency on 3.2/3.3/3.4. Then 3.3 (Dispute, simplest), then 3.2 (Tribunal,
highest-stakes copy — budget real review time for the Recalibration-Voice-only requirement),
then 3.4 (Monthly Report, depends conceptually on data Batches B/D/E will eventually feed,
though it can be built and tested with seeded/fake data before those land).

**Verification checklist:**
- [ ] CI green per screen
- [ ] On-device confirmed per screen
- [ ] 3.2 specifically: a manual content-review pass confirming zero Warden Voice language
      leaked into any Tribunal-surfaced copy, across all severity levels that can trigger it
- [ ] 3.5 specifically: confirm the two dismissal controls write distinguishable, separately
      queryable records (the accuracy-tracking requirement is meaningless if "not accurate"
      and "got it" land in the same undifferentiated log)

---

## Batch D — Recovery Mode

**Status: NOT STARTED.**

**Scope** (PRD §29):

- **Activation triggers:** Discipline Score < 60, multiple severe violations, two failed
  days, or reaching the Debt Ceiling (§27.1 — noted in the spec as an *additional* trigger
  beyond the original three).
- **Rules while active:** reduced commitments, increased enforcement (bounded by the user's
  current tier ceiling — Recovery Mode never pushes enforcement past what the user's tier
  already permits), mandatory recovery Missions, daily AI review.
- **Exit condition:** 3 successful days.
- **Explicitly distinct from Crisis Downgrade** (§12.4.3) — Crisis Downgrade is
  tampering-triggered and moves tiers; Recovery Mode does not change tier, it changes
  enforcement intensity within the existing tier.

**What this needs from already-built code:** Discipline Score, Debt Ceiling, and violation
severity are all Phase 0/1 machinery — this batch is UI plus a triggering/state-tracking
use-case wrapping existing domain logic, not new domain math.

**What's genuinely new:** the "daily AI review" component while Recovery Mode is active —
worth deciding early in this batch whether that's a lightweight rule-based check-in or
routes through the same AI Voice generation Batch F builds out fully. Building a throwaway
version here that Batch F later replaces is worse than sequencing Recovery Mode's daily
review to land after Batch F's real generation exists — **worth an explicit decision before
starting this batch's code**, not an implementation-time improvisation.

**Verification checklist:**
- [ ] CI green
- [ ] On-device confirmed: manually drive a test user to each of the four activation
      triggers independently, confirm Recovery Mode activates from each
- [ ] Confirm enforcement increase is genuinely bounded by tier ceiling (test at Recruit vs.
      Iron, confirm the increase differs and neither exceeds its own tier's normal ceiling)
- [ ] Confirm exit fires exactly at 3 successful days, not before or after

---

## Batch E — Behavioral Fingerprint / Predictive Failure Rules (F1–F5)

**Status: NOT STARTED. Hard-blocked on Batch C's §3.5 alert card pattern.**

**Scope** (Behavioral Fingerprint & Predictive Failure Rules Spec):

| Rule | Signal | Trigger | User-facing? |
|---|---|---|---|
| F1 — Time-of-Day Clustering | Violation timestamps, hour-binned | ≥3 violations in the same 2h window across last 10 violated Missions | Yes, once `sample_size ≥ 10` AND clustering holds across ≥2 distinct calendar weeks |
| F2 — Pre-Mission Cancellation | Missions aborted within first 5 min of `actual_start` | >25% of Missions over last 14 days, AND `sample_size ≥ 8` in that window | Yes, framed as a Mission Profile design question, never a discipline-failure framing |
| F3 — Debt Trajectory Slope | Discipline Debt, sampled daily | Net rise over 7 consecutive days (real linear slope > 0) AND above 50% of Debt Ceiling | Yes — links directly into Batch D's Recovery Mode |
| F4 — Reputation Decline Rate | Reputation trend | Projected to hit `tier_floor` within N days at current decay rate | **No — internal only for MVP.** Compounds two unvalidated hypotheses (decay rate + linear projection); do not build user-facing surface until the decay rate itself is Validated (Data Model doc §8) |
| F5 — Mission Profile Drift | Dispute/override frequency & clustering per Mission Profile | **Threshold is an explicit placeholder, not a number to guess** — PRD §42 states this can only be set from real post-launch data. Implement the counting/clustering *mechanism* now; default the threshold conservatively high (fewer false triggers) | Yes, once threshold is set — framed as "this profile may no longer fit your goals," never as a violation |

**Shared requirement across F1, F2, F3, F5 (§4 of that spec):** the minimum sample-size gate
applies uniformly — read that section's exact wording before implementing any single rule, so
the gate logic is written once and shared, not reimplemented per rule with subtle drift
between them.

**What happens when a rule is wrong (§5 of that spec):** every rule needs its false-positive
handling path wired to the "Not accurate" dismissal control Batch C already built — this
batch is where that control's logged data actually gets consumed/reviewed, not just stored.

**Suggested internal ordering:** F1 and F2 first (independent, no cross-rule dependency,
both purely advisory). F3 next (needs Batch D's Recovery Mode to exist for its link target —
confirms Batch D should genuinely precede this batch, not just conceptually). F4 last and
internal-only (lowest priority, no UI work at all for MVP). F5's mechanism can be built
anytime after F1/F2 establish the pattern, but its threshold stays a placeholder regardless
of when the mechanism ships.

**Verification checklist:**
- [ ] CI green per rule
- [ ] Sample-size gate logic shared, not duplicated per rule — confirm via code review, not
      just tests, since duplicated-with-drift is a code-smell tests alone won't catch
- [ ] F3 specifically: confirm dispute-and-overturn on a Violation correctly flags that
      alert's contribution to Debt Trajectory history for review (§5's requirement)
- [ ] F4 specifically: confirm zero user-facing surface exists — this is a rule where
      "nothing renders" is the correct, testable outcome, not a gap
- [ ] F5 specifically: confirm the threshold is a named, documented, easily-found
      configuration value — not buried as a magic number, since it's explicitly meant to be
      tuned later from real data

---

## Batch F — Daily/Weekly Reports + real AI Voice generation

**Status: NOT STARTED.**

**Scope, two related but separable pieces:**

**F.1 — Daily/Weekly Reports.** Not yet detailed in any spec section read closely so far in
this project's history — before starting this batch, locate (or write, if genuinely absent)
the spec section governing report content/cadence. Given the Monthly Intelligence Report
(Batch C, §3.4) already exists as a spec'd pattern for periodic data surfacing, treat that as
the closest existing precedent for tone/visibility rules (no default visibility, etc.) rather
than inventing a different convention for daily/weekly cadence.

**F.2 — Real AI Voice generation, replacing the current `NoOpWardenVoiceGenerator` stub.**
- Architecture doc's Warden Voice / Recalibration Voice split (§2.1–2.2) governs tone; this
  batch is the plumbing to actually call a real generation backend rather than the fallback
  bank exclusively.
- **Latency requirement already spec'd** (Onboarding/Consent spec §3.1): the Mission
  Interception screen "must never show a blank/error state waiting on AI generation" —
  fallback bank triggers if generation exceeds a defined timeout (spec recommends 2 seconds
  as a starting ceiling, tunable). This requirement predates this batch but only becomes
  testable once real generation exists to time.
- If Batch D deferred its "daily AI review" component pending this batch (see Batch D's
  note), that integration point lands here too.

**Verification checklist:**
- [ ] CI green
- [ ] On-device confirmed: force a slow/failed generation, confirm fallback bank triggers
      within the timeout ceiling and the interception screen never blanks
- [ ] Confirm Warden Voice and Recalibration Voice are never cross-wired (Tribunal always
      gets Recalibration regardless of what generation backend produces by default)

---

## After Batch F — MVP feature-complete, then Phase 5

Once F lands, cross-check every row in `STATUS.md`'s "MVP feature list" table — everything
currently ⬜ or 🟡 should be ✅ or a consciously-cut ✂️ item. At that point:

- Full onboarding flow (Batch B) should be walkable start-to-finish on a real device by
  someone who has never seen the app.
- Phase 5 (pilot) begins — no new app code, this is real usage generating the data needed to
  move every `[HYPOTHESIS]`-tagged constant toward Validated (Data Model doc §8): the 3-day
  shared-cause window (§5.5), the 7 Reputation bands and N=3 (§5.9), the tier↔band floor
  mapping (Batch A.5's open item), F1/F2/F3/F5's thresholds, and the decay/recovery rate
  constants from the original Phase 1 build.
