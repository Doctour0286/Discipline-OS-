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
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: UUID,
    val createdAt: Instant,
    val currentTier: Tier,
    val tierSelectedAt: Instant,
    val tierActivationAt: Instant,
    val calibrationWindowDays: Int = 10, // [HYPOTHESIS] Data Model §5, PRD §42
    val onboardingConsentVersion: String,
    val unsupervisedReliabilityOptIn: Boolean = false,
    val unsupervisedReliabilityOptInAt: Instant? = null,
    val debtAccrualPausedUntil: Instant? = null, // §12.4.3 Crisis Downgrade
    val tribunalDeferredUntil: Instant? = null,  // §12.4.3 Crisis Downgrade ("defers the Tribunal 24 hours")
    // §5.9 (resolved 2026-08-09): demotion_triggered needs "N consecutive days below a
    // rank's floor," which is state that must survive across separate
    // ApplyReputationDecayUseCase invocations (one call per elapsed day, per that class's
    // kdoc) — a single call can't derive "consecutive" from nothing else stored. Reset to 0
    // the moment Reputation is at/above the current band's floor; incremented by 1 each
    // day the post-decay value is still below it. See ApplyReputationDecayUseCase for the
    // actual demotion-firing logic this field feeds.
    val consecutiveDaysBelowFloor: Int = 0,
    val lastExplicitDowngradeAt: Instant? = null, // §5.15: 24h rolling cooldown between Explicit Downgrade uses
)
