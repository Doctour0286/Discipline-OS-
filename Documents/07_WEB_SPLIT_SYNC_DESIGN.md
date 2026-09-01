# DisciplineOS — Web Split: Sync Protocol & Native Local-Cache Schema (v2)

**Status:** Design draft — Prompt 1 of 5, ready for review.

**Purpose:** Design the split of DisciplineOS into two applications — a thin native Android
"Enforcer" (device-dependent enforcement only) and a web "Console" (everything else). The
Console owns the authoritative database and Ledger; the Enforcer keeps a minimal local cache
sufficient for zero-network enforcement.

**Depends on:** Data Model & Schema doc §2.2a, §3, §6, §7; Architecture doc §1, §3.1;
PRD §42 (offline-first constraint), PRD "Not Building" section.

**Provenance:** drafted by two independent model runs against the same Prompt 1 brief;
this version was selected over the alternative because it isolates provisional ledger
estimates in their own table rather than adding a `synced` column to the authoritative
`LedgerEntry` table — consistent with this codebase's existing convention of physically
separating non-authoritative data from anything that can affect Debt/Reputation (see
`UnsupervisedDatabase`'s isolation, Data Model §7). One defect was fixed before this
entered the repo: §2.4's session-abort scenario was previously only a forward reference
in the Q4 table with a corrupted signal name; it's now a fully specified subsection
(`session_terminate`, §2.4) so Prompt 2 has a real contract to implement against.

---

## 1. Local Cache Schema — What the Enforcer Needs

The Enforcer's local Room database must be **small and enforcement-scoped**. Hard constraint:
it must be able to **start a new Mission and fully enforce it with zero network connectivity**
(PRD §42 / Architecture §3.1).

### 1.1 Tables the Enforcer keeps locally

| Table | Justification (which enforcement-path operation needs it) |
|---|---|
| `cached_user` (subset of `User`: `id`, `currentTier`, `tierSelectedAt`, `tierActivationAt`, `calibrationWindowDays`, `debtAccrualPausedUntil`, `tribunalDeferredUntil`, `lastExplicitDowngradeAt`) | `MissionAccessibilityService:119 handleForegroundChange` → `InterceptionController:60 countdownSpec()` / `66 stabilityControl()` are tier-dependent (`InterceptionPolicy`). `RecordViolationUseCase:79` checks `user.currentTier` — enforcement cannot branch without tier. No `flaggedCategories` / `unsupervisedReliabilityOptIn` / `onboardingConsentVersion` / `consecutiveDaysBelowFloor` — zero enforcement reads. |
| `cached_goal_mission` (subset of `GoalMission`: `id`, `userId`, `title`, `archetype`) | `EnforcementSession.missionId` is non-null (schema v10 fix, `EnforcementSession:50`) — every session must have a parent goal. To **start a new Mission offline** the Enforcer must attach to a cached goal or create a provisional one locally (mirrors `FirstMissionSchedulingFragment`'s G2 three-row insert). Without this, offline creation is impossible. Minimal fields only — no `adherenceScore`, `lifecycleStage`, etc. |
| `cached_mission_profile` (subset of `MissionProfile`: `id`, `name`, `allowlist`, `blocklist`) | `EnforcementSession.missionProfileId` is non-null (`EnforcementSession:59`) + `blocklist` is the field checked at `MissionAccessibilityService:125 packageName !in mission.blocklist`. To create a session offline the Enforcer needs at least one profile's blocklist locally — cannot fetch at mission-start time. Pre-synced on last online pull. |
| `enforcement_session` (full `EnforcementSession` row) | Hottest path: `MissionAccessibilityService:122 activeMissionFor(userId)` on every `TYPE_WINDOW_STATE_CHANGED`, then `125 blocklist` membership test. Stores blocklist/allowlist snapshot at creation so mid-mission profile edits don't mutate active enforcement. |
| `pending_violation` (local queue: mirrors `Violation` + `syncState: PENDING \| SYNCING \| SYNCED`, `provisionalDebtDelta`, `provisionalReputationDelta`) | Offline durability for violations until reconciliation. `RecordViolationUseCase:144 clusterAlreadyHasActiveEntry` reads sibling violations within 3-day window — must work offline against local queue, not just backend. |
| `provisional_ledger_entry` (mirrors `LedgerEntry` shape + `synced: Boolean` local-only column) | `InterceptionController` needs immediate Debt/Reputation feedback on interception overlay; `LedgerEntry:33` event-sourced `sum(delta) where reversedAt is null`. Separate table so provisional estimates never mix with authoritative ledger; discarded/corrected after sync (§2.3). |
| `sync_metadata` (single row: `lastSuccessfulSyncAt`, `lastSyncSequence`) + `device_credentials` (`deviceId`, `deviceToken`, `pairedAt`) | Sync bookkeeping + pairing token storage (EncryptedSharedPreferences via `DbPassphraseProvider` pattern). Not enforcement entities but required for offline→online transition. |

### 1.2 Provisional-write marker

Offline-recorded ledger entries live in `provisional_ledger_entry`, a **separate table** from the
authoritative `ledger_entries`. This avoids polluting the authoritative schema with a local-only
column. The provisional table mirrors `LedgerEntry`'s shape plus two extra columns:

```
provisional_ledger_entry {
  id: UUID
  userId: UUID
  violationId: UUID | null
  metric: enum[debt, reputation]
  delta: Double
  appliedAt: Instant
  synced: Boolean = false       // local-only: false = pending, true = confirmed by Console
  syncedEntryId: UUID | null    // after reconciliation: the authoritative LedgerEntry.id that replaced this
}
```

`synced = false` = written locally during an offline Mission, pending reconciliation.
`synced = true` = confirmed by Console after sync (the Console's authoritative entry matched
or replaced this one). Provisional entries can be reversed the same way authoritative entries
can — the Ledger's append-only, reversal-ready design (Data Model §6) already supports this.

### 1.3 Tables the Enforcer does NOT keep locally

| Table | Why not |
|---|---|
| `mission_periods` | Schedule template — Console resolves it into a concrete `EnforcementSession` before pushing to Enforcer. Offline creation uses `cached_mission_profile` directly (ad-hoc session, `missionPeriodId = null`). No enforcement read needs the template. |
| `mission_log_entries` (`numericValue`/`didOccur`) | Adherence hit-rate input — "descriptive only, never scored for Debt/Reputation" (`GoalMission:168`). Only `AdherenceLedgerEntry` derives from it; that table never feeds Tier. No enforcement path reads it. |
| `triggers` / `milestones` / `output_artifacts` | Implementation-intention cues, progress checkpoints, output metrics — all Console-only, explicitly "descriptive only, never feeds Reputation/Debt/Ledger" (`Milestone:258`, `Trigger:216`). Zero enforcement reads. |
| `adherence_ledger_entries` / `GoalMission.adherenceScore` | Second consequence track separate from Reputation (`AdherenceLedgerEntry:14` — physically separate table so `LedgerDao` cannot pick it up). Not enforcement-critical. |
| `unsupervised_signals` (+ `UnsupervisedDatabase`) | Hard-isolated DB with separate encryption key (`UnsupervisedDatabase:14`, Data Model §7). "Measurement never enforces" — structurally true, not policy-true. |
| `predictive_failure_alert_dismissals` / `onboarding_screen_events` | Alert accuracy / onboarding instrumentation — Console-only. |
| `tier_events` history beyond current | Enforcer needs only current `User` tier state. Cooldown check uses `User.lastExplicitDowngradeAt`, not a history scan. |
| `ledger_entries` authoritative history | Authoritative history lives on Console. Enforcer keeps only `provisional_ledger_entry` estimates + cached derived totals (`currentDebt`/`currentReputation` as `sum(delta)`) pulled on sync, not the full append-only log. |

### 1.4 What "minimal subset" means in practice

The Enforcer's local DB is **small in both row count and table count**. Historical data
(completed/abandoned sessions, all violations, all ledger entries) lives on the Console. The
Enforcer only caches:

1. The **currently-active** `EnforcementSession` (and its referenced `MissionProfile`).
2. **Recent violations** from the current session (for shared-cause guard, 3-day rolling window).
3. **Recent provisional ledger entries** (for shared-cause guard and local Debt/Reputation estimates).
4. The **current user state** (tier, pause flags, calibration window).

Stale data is pruned on each sync cycle — anything older than the current session + a grace
window is deletable.

---

## 2. Sync Protocol

### 2.1 Direction of data flow

| Data type | Direction | Mechanism | Reasoning |
|---|---|---|---|
| **Active session + profile** | Console → Enforcer | **Push** (WebSocket/SSE on session start/update) | Enforcement needs this data *before* it begins. Pull-only risks starting a Mission without the latest profile. |
| **User tier state** | Console → Enforcer | **Push** (same channel as sessions) | Tier changes (upgrade, downgrade, Tribunal outcome) must reach Enforcer before next interception. |
| **New violations** | Enforcer → Console | **Push** (HTTP POST on event, batched if offline) | Console needs violations for Ledger reconciliation and reporting. |
| **Provisional ledger entries** | Enforcer → Console | **Push** (bundled with violation push) | Sent alongside violation so Console can see local estimate, then reconcile against authoritative calculation. |
| **Tier events** | Enforcer → Console | **Push** (bundled with violation/tier-change push) | Enforcer may trigger tier transitions (Iron Crisis Exit, Explicit Downgrade during interception). |
| **Historical data** | Console → Enforcer | **Pull** (on-demand) | Enforcer rarely needs historical data. Avoids unnecessary sync traffic. |
| **Goal/Profile updates** | Console → Enforcer | **Push** (on profile edit, before next session) | If Console edits a profile while no Mission is active, update is cached. If Mission is active, see §2.4. |
| **Reconciled ledger entries** | Console → Enforcer | **Push** (after reconciliation) | Console sends authoritative entries back, replacing or reversing provisional estimates. |
| **Policy constants** | Console → Enforcer | **Push** (on config change) | When `[HYPOTHESIS]` constants are validated (Phase 5), Console pushes updated `ConsequencePolicy` values. Uses same push channel as profile updates. |

### 2.2 Push vs. pull reasoning

**Push for enforcement-critical data:** Stale data in the enforcement loop has immediate
consequences (wrong blocklist = unblocked app, wrong tier = wrong countdown duration). Push
ensures the Enforcer's cache is as fresh as the Console can make it.

**Pull for historical/non-critical data:** The Enforcer doesn't need to proactively fetch
GoalMission details, MissionLogEntry history, or Behavioral Fingerprint alerts. These are
read-on-demand when the user opens a detail view in the Console, not enforcement-path reads.

**WebSocket for real-time pushes:** A lightweight WebSocket (or SSE) connection from the Enforcer
to the Console's backend handles bidirectional pushes. When the connection is down, the Enforcer
queues outbound events locally and sends them on reconnect.

### 2.3 Offline-recorded Violation — reconciliation sequence

This is the critical path. When a Violation is recorded during an offline Mission:

```
1. Enforcer records Violation locally (pending_violation, syncState = PENDING)
2. Enforcer writes provisional LedgerEntry (provisional_ledger_entry, synced = false)
   for Debt + Reputation
3. Enforcer computes local Debt/Reputation estimate (existing ConsequencePolicy formulas)
4. [Connectivity returns]
5. Enforcer pushes: { violation, provisional_ledger_entries } to Console
6. Console receives, runs authoritative RecordViolationUseCase:
   a. Checks shared-cause guard against its own (authoritative) Ledger
   b. Computes tier/type-dependent penalties using authoritative ConsequencePolicy
   c. Writes authoritative LedgerEntry rows (in ledger_entries, not provisional)
   d. If provisional estimate differed from authoritative:
      - Writes reversal entries for the provisional entries, using LedgerEntry's existing
        reversal mechanism (Data Model §6: append-only, reversedAt/reversedReason)
   e. Sends reconciled state back to Enforcer: { confirmed_entries, reversed_entries }
7. Enforcer receives reconciliation:
   a. Marks provisional entries as synced (synced = true, syncedEntryId = authoritative id)
   b. Applies reversal entries for any reversed provisionals
   c. Applies any new authoritative entries from Console
   d. Local Debt/Reputation totals now match authoritative state
```

**Why this works with the existing Ledger design:** The Ledger is event-sourced and
append-only (Data Model §6). Reversal is already a first-class operation (`reversedAt`,
`reversedReason`). Provisional entries in a separate table are just local estimates —
when the Console sends back reversals, the Enforcer applies them the same way it would
apply any other reversal. No new reversal mechanism needed.

**Why provisional estimates are worth having:** Even though they're provisional, local
Debt/Reputation estimates let the Enforcer display current state during an offline Mission
(e.g. "Debt: 45/120" on the interception screen). Without them, the Enforcer shows stale
or blank values until sync completes. The estimates may differ from the Console's
authoritative calculation, but they're directionally correct and better than nothing.

### 2.4 Conflict handling

**Scenario: Console changes a MissionProfile's blocklist while a Mission is active on Enforcer.**

The Enforcer does **NOT** hot-swap the blocklist of an already-active session mid-Mission.
The active session carries its own resolved `blocklist`/`allowlist` at session start — changing
it mid-flight creates inconsistent enforcement state (user blocked from App X for 40 min,
then suddenly it's allowed). The profile update is cached for the *next* session. If the
Console decides the update is urgent (critical app added to blocklist), it can end the current
session and start a new one with the updated profile.

**Scenario: Console changes the user's tier while a Mission is active.**

- **Upgrade:** Apply immediately (more permissive, no enforcement harm).
- **Downgrade:** Defer until current Mission ends (downgrading mid-Mission changes countdown
  durations and crisis-exit availability — confusing and potentially dangerous at Iron tier).

**Scenario: Enforcer is offline for an extended period.**

Enforcer continues enforcing with cached state. Violations queue locally. When connectivity
returns, full reconciliation sequence (§2.3) runs. If cache is very stale, Console sends a
full state refresh rather than incremental deltas.

**Scenario: Console needs to end a session mid-flight (admin intervention, crisis).**

Rare, but the mechanism must exist — e.g. a Tribunal outcome or Crisis Downgrade resolved
on the Console requires stopping enforcement immediately rather than waiting for the
Mission's natural end. The Console pushes a `session_terminate` signal (same channel as
other Console→Enforcer pushes, §2.1) carrying the `sessionId` and a `reason` enum
(`ADMIN_INTERVENTION`, `CRISIS_DOWNGRADE`, `TRIBUNAL_OUTCOME`). On receipt, the Enforcer:

1. Marks the local `enforcement_session` row `COMPLETED` (not `VIOLATED` — this is not a
   failure state and must not read as one anywhere in the UI or in any Debt/Reputation
   calculation).
2. Stops the Accessibility Service's enforcement for that session immediately — the next
   `TYPE_WINDOW_STATE_CHANGED` event should see no active session and let the foreground
   app through unblocked.
3. Surfaces a plain, non-punitive notice to the user (e.g. "This Mission was ended early
   from your Console" ) rather than silence, since an abrupt unblock with no explanation
   would be confusing.

If the Enforcer is offline when this is pushed, it is queued like any other Console→Enforcer
push and applied on reconnect; until then, enforcement continues under the stale session as
normal (§2.4 general offline handling above) — a delayed termination is preferable to a
termination mechanism that silently fails to work offline.

### 2.5 Sync state tracking

Each sync message includes a `syncSequence` number (monotonically increasing per-device). The
Console tracks the last `syncSequence` it received from each Enforcer, and vice versa. This
enables:
- Gap detection (missing messages)
- Idempotent replay (re-sending same sequence = no-op)
- Conflict detection (two Enforcers offline simultaneously — rare, but mechanism exists for
  multi-device)

---

## 3. Auth/Pairing Model

### 3.1 Design

**Single-user, single-account, potentially-multiple-devices.** No multi-tenant or team
features (PRD "Not Building" section).

**Pairing flow:**
1. User creates an account on the Console (email + password, or email with magic link).
2. Console displays a pairing code (6-digit numeric, short-lived — 5 minutes) or QR code
   encoding the code + backend URL.
3. Enforcer app has a "Pair with Console" entry point (Settings or first-run flow).
4. User enters the code (or scans the QR).
5. Enforcer sends the code to Console backend over HTTPS.
6. Console validates the code, issues a **long-lived device token** (JWT or opaque token,
   stored in EncryptedSharedPreferences via the existing `DbPassphraseProvider` infrastructure).
7. Enforcer stores the token and uses it for all subsequent API calls.

**Token properties:**
- Long-lived (e.g. 90 days), with refresh mechanism (Enforcer refreshes before expiry while
  Console is reachable).
- Scoped to the one account it was issued for.
- Revocable from Console (Settings → Paired Devices → Revoke).
- One token per device — re-pairing revokes the old token.

**No biometric auth gate on the Enforcer for MVP.** The Enforcer is a personally-installed
app on a personal device; a biometric gate on top of the device's own lock screen is
redundant friction for the current scope. Revisit if distribution model changes
(Architecture §3.2).

### 3.2 Multi-device handling

Multiple devices can be paired to the same account. Each gets its own token and local cache.
Console pushes the same active session to all paired devices. The enforcement model assumes
at most one active Mission at a time across all devices (matching existing schema assumption —
`activeMissionFor` uses `LIMIT 1`). If two devices try to enforce simultaneously, Console
arbitrates: first device to push a violation wins; second device's violation is rejected as
duplicate (server-side dedup on `violationId`).

---

## 4. Explicit Non-Goals

1. **Real-time collaborative editing.** Two devices editing the same MissionProfile
   simultaneously is not a scenario this design handles. Each device sees Console's latest
   state; conflicts resolved by Console being authoritative, not by merging concurrent edits.

2. **Multi-user / team accountability.** PRD "Not Building" section explicitly excludes
   team-based accountability. Auth model is single-user, single-account. No shared
   dashboards, no team leaderboards, no shared Missions.

3. **Offline Console.** The Console (web app) requires network connectivity. The offline-first
   constraint (PRD §42) applies to the Enforcer only. The Console is a standard web app that
   reads/writes the authoritative backend.

4. **Push notifications for violation alerts.** Enforcer handles violations locally via the
   interception overlay. Console does not push real-time notifications when a violation occurs —
   the user is already looking at the interception screen. Console-side notifications
   (e.g. "You had 3 violations today") are pull-on-open, not push.

5. **Bidirectional real-time goal tracking.** Goal progress (MissionLogEntry, Milestone updates)
   flows Console-only. Enforcer does not display goal progress or milestone status. The
   enforcement loop is blind to goals — it only sees sessions.

---

## 5. Assumptions

1. **Single active Mission at a time.** Current schema already assumes this
   (`activeMissionFor` uses `LIMIT 1`). Split doesn't change this — if it ever needs to,
   both Enforcer local cache and sync protocol need rework.

2. **Console backend is a simple REST/JSON API.** Personal app, not SaaS. A single backend
   service (e.g. Kotlin/Spring or Go) with PostgreSQL is sufficient. WebSocket/SSE for push
   is a single persistent connection per device, not a message queue.

3. **Provisional ledger entries are acceptable for display.** Enforcer may show approximate
   Debt/Reputation during offline Mission. Based on local estimates that may differ from
   Console's authoritative calculation. Difference is bounded (both use same
   `[HYPOTHESIS]` ConsequencePolicy constants) and corrected on sync. Explicit tradeoff:
   approximate-now vs. exact-later.

4. **Existing `[HYPOTHESIS]` ConsequencePolicy constants are duplicated on both sides.**
   Enforcer needs `ConsequencePolicy` for local estimates. Console has authoritative copy.
   When validated constants ship (Phase 5), Console pushes updated constants to Enforcer as
   part of sync protocol — same "push config updates" mechanism profile-update path uses.

5. **Enforcer local DB schema version is separate from Console's.** Enforcer has smaller
   schema; evolves independently. Destructive migration (project's existing pre-launch policy)
   applies to Enforcer local cache — no installed base to preserve, same reasoning as
   `DisciplineOsDatabase.kt`'s existing kdoc.

---

## 6. Migration Path (Current → Split)

Current codebase: single Android app with `:data`, `:domain`, `:app` modules. The split
requires:

1. **Extract Enforcer subset from `:data`** — new `:enforcer-data` module with only entities
   in §1.1, plus a new `provisional_ledger_entries` table (§1.2).
2. **Extract enforcement loop from `:app`** — Accessibility Service, InterceptionController,
   MissionInterceptionActivity move to Enforcer app. Onboarding, Home, Tribunal, and all
   other UI screens move to Console web app.
3. **`:domain` module splits** — `RecordViolationUseCase`, `TierTransitionUseCase`,
   `InterceptionPolicy`, `ConsequencePolicy`, voice components stay with Enforcer. All other
   use-cases move to Console backend.
4. **New backend service** — receives pushes from Enforcer, serves Console web app, owns
   authoritative database. New infrastructure, not modification of existing code.

This is the work of subsequent prompts, not this document.

---

## 7. Open Questions

| # | Question | Recommendation | Status |
|---|---|---|---|
| Q1 | Should Enforcer cache historical sessions for post-Mission display? | Cache only current + most-recent session. Post-Mission summary shows immediately; historical browsing requires Console. | [HYPOTHESIS] — needs UX validation |
| Q2 | What happens if Enforcer is offline for multiple consecutive Missions? | Each Mission records violations locally; all reconcile in batch on reconnect. No special "extended offline" mode needed. | Assumed simple — validate with real offline stress test |
| Q3 | WebSocket reconnect strategy — aggressive or conservative? | Exponential backoff starting at 5s, capped at 5min. Violations queue locally regardless. | [HYPOTHESIS] — tune based on battery impact testing |
| Q4 | How does Enforcer handle Console-initiated session abort mid-flight? | See §2.4, "Console needs to end a session mid-flight" — `session_terminate` push, session marked `COMPLETED`, enforcement stops immediately. | Assumed — needs implementation validation |
