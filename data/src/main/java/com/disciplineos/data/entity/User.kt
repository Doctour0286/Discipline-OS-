package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class Tier { RECRUIT, OPERATOR, WARDEN, IRON }

/**
 * Data Model & Schema doc §2.1.
 *
 * Notes on fields that deviate from a naive read of the spec:
 * - [tierActivationAt] may lag [tierSelectedAt] — this is the Iron calibration gate
 *   (PRD §12.6 / Data Model §5). Enforced in [UserDao] / tier-transition logic, not here.
 * - [calibrationWindowDays] is stored per-user (not a global constant) per Data Model §5,
 *   specifically so the future signal-quality scaling (PRD §42) doesn't require a schema
 *   migration later. Defaults to 10 (Hypothesis, flagged §42).
 * - [debtAccrualPausedUntil] / [tribunalDeferredUntil] — added Phase 1
 *   (`TierTransitionUseCase`), not in the Data Model doc's original §2.1 sketch. PRD §12.4.3
 *   (Crisis Downgrade) requires "pauses debt accrual, defers the Tribunal 24 hours" and
 *   §12.4.4 (Iron Crisis Exit) triggers the same §12.4.3 mechanics. Neither the PRD nor the
 *   Data Model doc says *how* "paused" and "deferred" are represented — both read naturally
 *   as time-bounded states (a pause that resumes, a deferral with a due time), not
 *   open-ended booleans a separate process has to remember to clear, so both are nullable
 *   `Instant` "until" fields rather than `Boolean` flags. This mirrors the reasoning already
 *   used for [LedgerEntry.pausedAt] (see that file's kdoc) — a resumable pause is a distinct
 *   state from a permanent one, and encoding *when* it ends is cheaper and less bug-prone
 *   than a flag plus a separate scheduled job to unset it.
 *   - `debtAccrualPausedUntil = null` means accrual is active (the normal case).
 *   - Non-null means new Debt ledger entries must not be written for this user until that
 *     instant — this is a `RecordViolationUseCase`-side check (Phase 1 follow-up: that use
 *     case does not yet read this field — see ROADMAP.md §5 decision log), not something
 *     enforced by the DB layer itself.
 *   - `tribunalDeferredUntil` is display/gating information only (PRD §30's mandatory
 *     Tribunal at Warden/Iron becomes *available* rather than required until this instant
 *     passes) — Tribunal UI/enforcement is Phase 3, this field just carries the fact.
 *
 * **`currentTier`/`tierSelectedAt`/`tierActivationAt`/`onboardingConsentVersion` are now
 * nullable (Batch B, BUILD_PLAN.md, this session) — a real, deliberate schema change, not an
 * incidental one.** Found while building GoalDefinitionFragment (§2.2, onboarding screen 2):
 * the User row has never existed before [TierTransitionUseCase.selectInitialTier] runs
 * (screen 4a, Tier Confirmation) — meaning any earlier onboarding screen that needs to
 * persist something (Goal Definition's flagged categories being the first real case) had
 * nowhere durable to write it. Buffering that data in memory (nav-graph arguments, screen to
 * screen) was the alternative considered and rejected: a process death anywhere between
 * screens 2 and 4a — plausible, onboarding is exactly when a user is likely to background the
 * app — would silently lose everything typed, with no error surfaced. Creating the User row
 * earlier (at Goal Definition) and writing tier-related fields once they're actually known
 * (Tier Confirmation) is the durable option, at the cost of these four fields legitimately
 * not existing yet for a brief, real window in the row's life — which nullable honestly
 * represents, rather than a sentinel/placeholder value pretending a tier was chosen when it
 * wasn't.
 *
 * **Every non-test call site reading these four fields was checked before this change** (four
 * files: `MissionInterceptionActivity`, `TierEvent`, `RecordViolationUseCase`,
 * `TierTransitionUseCase`) — all four only ever run after onboarding completes, never during
 * it, so none of them can observe a null in practice. They still need `!!`/safe-call handling
 * now that the type allows it, purely to satisfy the compiler; treat any of them throwing or
 * behaving oddly on a genuinely-null value as a real bug to investigate (a row reaching
 * enforcement/violation code with no tier ever selected should not be possible), not an
 * expected case to code around gracefully.
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: UUID,
    val createdAt: Instant,
    val currentTier: Tier?,
    val tierSelectedAt: Instant?,
    val tierActivationAt: Instant?,
    val calibrationWindowDays: Int = 10, // [HYPOTHESIS] Data Model §5, PRD §42
    val onboardingConsentVersion: String?,
    val unsupervisedReliabilityOptIn: Boolean = false,
    val unsupervisedReliabilityOptInAt: Instant? = null,
    val debtAccrualPausedUntil: Instant? = null, // §12.4.3 Crisis Downgrade
    val tribunalDeferredUntil: Instant? = null,  // §12.4.3 Crisis Downgrade ("defers the Tribunal 24 hours")
    // §2.2 Goal Definition (Batch B, BUILD_PLAN.md): free-text + structured tags for
    // "high-value"/"high-risk" categories, stored as a flat string list (same
    // Converters.fromStringList/toStringList convention as MissionProfile.allowlist/
    // blocklist — no new converter needed). Lives on User, not MissionProfile, because §2.2's
    // own spec language scopes this as a cross-Mission-Profile concept: "the apps you flag
    // here are the *only* ones we'll ever look at outside your Missions" (§2.2, feeding
    // §2.7's Unsupervised Reliability scope) — that's a statement about the user's declared
    // intent overall, not about any one Mission Profile's technical allow/blocklist, which is
    // a different, narrower thing MissionProfile already owns. Defaults to empty rather than
    // null: "no categories flagged yet" and "flagged zero categories on purpose" are the same
    // state for every consumer of this field (Unsupervised Reliability scope, Mission Profile
    // Setup's default-suggestions), so there's no meaningful null case to represent.
    val flaggedCategories: List<String> = emptyList(),
)
