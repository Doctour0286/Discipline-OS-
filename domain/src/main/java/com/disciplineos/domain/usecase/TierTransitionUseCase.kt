package com.disciplineos.domain.usecase

import androidx.room.withTransaction
import com.disciplineos.data.dao.MissionDao
import com.disciplineos.data.dao.TierDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.TierEventKind
import com.disciplineos.data.entity.User
import com.disciplineos.data.metrics.ironCalibrationSatisfied
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ROADMAP.md Phase 1, remaining exit-criteria items:
 * - "Iron calibration gate is enforced at the point of tier activation, not just computable
 *   as a pure function someone has to remember to call"
 * - Crisis exit handling — referenced in `RecordViolationUseCase`'s §5.6 decision-log entry
 *   as "whatever crisis-exit-specific logic Phase 1 still needs to write."
 *
 * Wires together, per transition kind, atomically:
 * - the [Metrics.ironCalibrationSatisfied] pure check — enforced here as a gate on
 *   [activateIron], not left as a function nothing calls
 * - a [User.currentTier] mutation
 * - an append-only [TierEvent] record (see that file's kdoc for why a raw tier mutation
 *   alone can't satisfy §12.4.4's "logged as a distinct event type" requirement)
 * - for Crisis Downgrade and Iron Crisis Exit specifically: setting
 *   [com.disciplineos.data.entity.User.debtAccrualPausedUntil] /
 *   [com.disciplineos.data.entity.User.tribunalDeferredUntil] per PRD §12.4.3's "pauses debt
 *   accrual, defers the Tribunal 24 hours"
 * - for Iron Crisis Exit specifically: marking the triggering Mission
 *   `ABORTED_CRISIS_EXIT` — which is what makes `RecordViolationUseCase`'s existing hard
 *   `require()` (§5.6 of ROADMAP.md) actually prevent a Ledger write for this path, closing
 *   the loop that decision log entry flagged as still open ("it doesn't yet prove nothing
 *   *else* in the app could route one there instead")
 *
 * Deliberately does NOT import [com.disciplineos.data.dao.UnsupervisedSignalDao] — same
 * boundary reasoning as `RecordViolationUseCase`'s own kdoc: tier transitions are
 * consequence-path code, and `DomainArchitectureBoundaryTest` covers this file too.
 */
class TierTransitionUseCase(
    private val database: DisciplineOsDatabase,
    private val userDao: UserDao,
    private val tierDao: TierDao,
    private val missionDao: MissionDao,
) {

    /**
     * PRD §12.4.3: Crisis Downgrade duration for debt-pause / Tribunal-deferral —
     * "pauses debt accrual, defers the Tribunal 24 hours." 24 hours is stated explicitly in
     * the PRD text itself (not flagged [HYPOTHESIS] anywhere in §42), so this is a real
     * spec value, not an invented constant — unlike e.g. the Reputation decay rate.
     */
    private val crisisStabilizationWindow: Duration = Duration.ofHours(24)

    /**
     * §12.4.1 Standard Downgrade — "triggered by sustained depletion signal across a rolling
     * window, not a single bad day." This use-case does not itself decide *whether* the
     * signal warrants a downgrade — that's the Behavioral Fingerprint / Predictive Failure
     * Engine's job (ROADMAP.md Phase 4, not yet built) — it only performs the transition
     * once a caller has already decided it should happen. [reasonNote] should describe which
     * signal drove the call, since unlike Explicit Downgrade or Iron Crisis Exit, a Standard
     * Downgrade is not "no reason entry" by spec — nothing in §12.4.1 says it's
     * friction-free the way §12.4.2/§12.4.4 explicitly are.
     *
     * No score penalty is written here — consistent with every other downgrade path in
     * §12.4 — a tier change is never itself a Ledger event.
     */
    suspend fun standardDowngrade(userId: UUID, toTier: Tier, reasonNote: String, now: Instant = Instant.now()) =
        transition(userId, toTier, TierEventKind.STANDARD_DOWNGRADE, reasonNote, now)

    /**
     * §12.4.2 Explicit Downgrade — "a persistent, always-visible 'this is too much right
     * now' control, honored immediately with no friction, no delay, no score consequence."
     * No reason entry, by spec — [TierEvent.reasonNote] is left null here rather than
     * accepting a caller-supplied one, since requiring or even offering a reason field on
     * this specific control would add exactly the friction §12.4.2 says this path must not
     * have.
     *
     * **Target tier and cooldown decided (2026-08-09, ROADMAP.md §5.15) but cooldown is NOT
     * YET IMPLEMENTED here.** Product-owner sign-off: one tier down per use (already how the
     * caller in [com.disciplineos.app.enforcement.MissionInterceptionActivity.oneTierDown]
     * computes [toTier]), plus a **24-hour rolling cooldown** between uses — tracked from the
     * timestamp of the last use, not a calendar-day reset, to avoid a midnight-boundary
     * loophole. This method currently has no cooldown enforcement at all; a caller can invoke
     * it repeatedly with no restriction. Do not treat this as "done" for §5.15 purposes until
     * the cooldown check exists (likely a new `User` field for last-use timestamp, checked
     * here or in the caller before invoking) and is CI-verified. See ROADMAP.md §5.15 for
     * full rationale.
     */
    suspend fun explicitDowngrade(userId: UUID, toTier: Tier, now: Instant = Instant.now()) =
        transition(userId, toTier, TierEventKind.EXPLICIT_DOWNGRADE, reasonNote = null, now = now)

    /**
     * §12.4.3 Crisis Downgrade — "reserved for Tampering/Critical violations. Moves the user
     * to Recruit immediately, pauses debt accrual, defers the Tribunal 24 hours." The move
     * to Recruit specifically (not just "downgrade") is a hard requirement of this path, not
     * a caller choice — unlike [standardDowngrade]/[explicitDowngrade], this method doesn't
     * take a `toTier` parameter.
     *
     * [triggerReason] is required (unlike Explicit Downgrade) since a Crisis Downgrade is,
     * by definition, triggered by something specific (a Tampering or Critical violation) that
     * a later Tribunal review will need to reference — see PRD §30's Tribunal question "what
     * triggered it?"
     */
    suspend fun crisisDowngrade(userId: UUID, triggerReason: String, now: Instant = Instant.now()): TierEvent {
        return database.withTransaction {
            val user = requireUser(userId)
            val currentTier = requireNotNull(user.currentTier) {
                "crisisDowngrade called for user $userId with no currentTier — " +
                    "selectInitialTier must complete before any tier-transition method runs " +
                    "(User.kt kdoc, Batch B)"
            }
            val event = writeEvent(userId, currentTier, Tier.RECRUIT, TierEventKind.CRISIS_DOWNGRADE, triggerReason, now)
            userDao.update(
                user.copy(
                    currentTier = Tier.RECRUIT,
                    debtAccrualPausedUntil = now.plus(crisisStabilizationWindow),
                    tribunalDeferredUntil = now.plus(crisisStabilizationWindow),
                )
            )
            event
        }
    }

    /**
     * §12.4.4 Iron-Tier Crisis Exit. Per PRD, this control:
     * - is honored immediately, "no delay, no additional confirmation screen, no reason
     *   entry" — so, like [explicitDowngrade], no caller-supplied reason
     * - "triggers a Crisis Downgrade (§12.4.3)" — same Recruit move, same debt-pause/
     *   Tribunal-defer mechanics, reused here rather than duplicated
     * - "is logged as a distinct event type from a standard §12.4.2 invocation" — this is
     *   why this is its own method and its own [TierEventKind.IRON_CRISIS_EXIT], not a call
     *   into [crisisDowngrade] alone; both a `CRISIS_DOWNGRADE`-shaped tier change *and* an
     *   `IRON_CRISIS_EXIT`-kind record need to exist, since the PRD's own comparison point
     *   for "distinct" is specifically against a §12.4.2 (Explicit Downgrade) invocation —
     *   the underlying tier mechanics matching Crisis Downgrade doesn't relax that
     * - must mark the triggering Mission `ABORTED_CRISIS_EXIT` so `RecordViolationUseCase`'s
     *   existing guard (ROADMAP.md §5.6) actually prevents any Ledger write reaching this
     *   Mission — this is the piece that decision-log entry flagged as not yet proven end to
     *   end
     *
     * @param missionId the Mission active at the moment the exit was used — required (unlike
     *   [crisisDowngrade]'s free-text trigger) because the PRD ties this control to "the
     *   interception screen itself," which only exists in the context of one specific
     *   in-progress Mission.
     */
    suspend fun ironCrisisExit(userId: UUID, missionId: UUID, now: Instant = Instant.now()): TierEvent {
        return database.withTransaction {
            val user = requireUser(userId)
            val currentTier = user.currentTier
            require(currentTier == Tier.IRON) {
                "ironCrisisExit called for user $userId at tier $currentTier — " +
                    "this control only exists on the Iron-tier interception screen (PRD §12.4.4)"
            }
            val mission = requireNotNull(missionDao.get(missionId)) {
                "ironCrisisExit references missing Mission $missionId"
            }
            require(mission.userId == userId) {
                "Mission $missionId does not belong to user $userId"
            }

            missionDao.update(mission.copy(status = MissionStatus.ABORTED_CRISIS_EXIT, actualEnd = now))

            val event = writeEvent(
                userId, currentTier, Tier.RECRUIT, TierEventKind.IRON_CRISIS_EXIT,
                reasonNote = null, occurredAt = now,
            )
            userDao.update(
                user.copy(
                    currentTier = Tier.RECRUIT,
                    debtAccrualPausedUntil = now.plus(crisisStabilizationWindow),
                    tribunalDeferredUntil = now.plus(crisisStabilizationWindow),
                )
            )
            event
        }
    }

    /**
     * Onboarding §2.4 Tier Selection — the user's *first-ever* tier choice, made before any
     * [User] row exists. Every other public method on this class transitions an existing
     * user between tiers; this one creates the user.
     *
     * **Hard-rejects [Tier.IRON]**, unconditionally — Onboarding doc §2.5 / PRD §12.6:
     * "Iron is not selectable at first-time onboarding regardless of stated user intent,"
     * with "no exception path." This isn't a UI-layer nicety to enforce by graying out a
     * button; §12.6 states it as a hard requirement on the same footing as the calibration
     * gate itself, so it's enforced here, at the one call site that can create the user's
     * very first tier, the same way [activateIron] enforces the calibration *window* at the
     * one call site that can activate Iron later. A caller that wants Iron still reaches it
     * only through the normal path: select Recruit or Operator here, accumulate the
     * [User.calibrationWindowDays] window, then call [activateIron].
     *
     * [tier] is otherwise unrestricted — Recruit, Operator, or Warden are all valid
     * first-onboarding choices per §12.6 ("subject to the existing Warden confirmation
     * screen"); that confirmation-screen requirement is Onboarding doc §2.4 UI, not something
     * this use-case can check, so it's the caller's responsibility to have shown it before
     * calling this with [Tier.WARDEN].
     *
     * [TierEvent.fromTier] is set equal to [tier] (see [TierEventKind.INITIAL_SELECTION]'s
     * kdoc for why — [TierEvent.fromTier] is non-nullable and there is no real prior tier to
     * put there).
     *
     * `tierActivationAt` is set equal to `tierSelectedAt` (both `now`) for every tier this
     * method accepts — the calibration-window gap between the two fields exists specifically
     * for Iron ([activateIron] is the only place that ever sets them apart), and this method
     * never produces Iron, so there is no lag to represent here.
     */
    suspend fun selectInitialTier(
        userId: UUID,
        tier: Tier,
        onboardingConsentVersion: String,
        now: Instant = Instant.now(),
    ): TierEvent {
        require(tier != Tier.IRON) {
            "selectInitialTier rejects IRON — Onboarding doc §2.5 / PRD §12.6: Iron is not " +
                "selectable at first-time onboarding regardless of stated intent, no exception " +
                "path. Select Recruit/Operator/Warden here, then reach Iron later via activateIron() " +
                "once the calibration window has elapsed."
        }
        return database.withTransaction {
            // Batch B (BUILD_PLAN.md), User.kt kdoc: a "draft" User row may already exist by
            // the time this runs — GoalDefinitionFragment (onboarding screen 2, before this
            // method's caller at screen 4a) now creates the row early so its own data has
            // somewhere durable to be written, well before a tier is known. That draft row
            // has currentTier/tierSelectedAt/tierActivationAt/onboardingConsentVersion all
            // null and everything else (flaggedCategories, etc.) already meaningfully set.
            // UPDATE that row in place rather than unconditionally INSERT — an unconditional
            // insert here would throw on the primary key conflict once a draft row exists,
            // and even if it didn't conflict, a fresh User() would silently discard whatever
            // Goal Definition already wrote. Fall back to a real INSERT only if no row exists
            // at all yet (e.g. a future flow reaches tier selection without going through
            // Goal Definition first — not how onboarding_nav_graph.xml is currently wired,
            // but this method shouldn't assume its caller's nav graph is the only possible
            // caller).
            val existing = userDao.get(userId)
            val user = existing?.copy(
                currentTier = tier,
                tierSelectedAt = now,
                tierActivationAt = now,
                onboardingConsentVersion = onboardingConsentVersion,
            ) ?: User(
                id = userId,
                createdAt = now,
                currentTier = tier,
                tierSelectedAt = now,
                tierActivationAt = now,
                onboardingConsentVersion = onboardingConsentVersion,
            )
            if (existing != null) userDao.update(user) else userDao.insert(user)
            writeEvent(userId, fromTier = tier, toTier = tier, TierEventKind.INITIAL_SELECTION, reasonNote = null, now)
        }
    }

    /**
     * §12.3 Upgrade — "recommended, never imposed." This records that the user accepted an
     * already-presented recommendation; it does not decide whether to recommend one (that
     * belongs to whatever surfaces the Recalibration Voice prompt, not this use-case) and it
     * performs no eligibility check of its own for Recruit→Operator→Warden — those
     * thresholds (85%/10 days, no Critical violations in 14 days) are display/recommendation
     * logic, not a gate on the transition itself, per §12.3's own framing ("recommends,"
     * never blocks a user from choosing a lower tier they already qualify for).
     *
     * Iron is the one exception: activating Iron is gated by [activateIron] below, not this
     * method, because Iron's gate is a hard requirement (§12.6) rather than a recommendation
     * threshold — attempting to activate Iron through this generic path would bypass that
     * gate silently, so [toTier] is rejected outright if it's `IRON`.
     */
    suspend fun acceptUpgrade(userId: UUID, toTier: Tier, now: Instant = Instant.now()): TierEvent {
        require(toTier != Tier.IRON) {
            "acceptUpgrade rejects IRON — Iron activation must go through activateIron() so " +
                "the §12.6 calibration gate is actually checked, not bypassed"
        }
        return transition(userId, toTier, TierEventKind.UPGRADE_ACCEPTED, reasonNote = null, now = now)
    }

    /**
     * §12.6 / Data Model §5 Iron calibration gate — "enforced at the point of tier
     * activation, not just computable as a pure function someone has to remember to call"
     * (ROADMAP.md Phase 1 exit criterion, previously unchecked). Reuses
     * [ironCalibrationSatisfied] (the pure function that already existed and was tested in
     * `MetricsTest`) as the actual check, rather than re-deriving the same logic here —
     * this method's entire job is to be the single call site that function needed and
     * didn't have.
     *
     * @throws IllegalStateException if the calibration window (`user.calibrationWindowDays`
     *   from `user.tierSelectedAt`) hasn't elapsed yet. Deliberately a hard failure, not a
     *   silent no-op or an automatic wait — a caller reaching this method believing Iron is
     *   available needs to know immediately that it isn't, matching the "no exception path"
     *   language in PRD §12.6.
     */
    suspend fun activateIron(userId: UUID, now: Instant = Instant.now()): TierEvent {
        return database.withTransaction {
            val user = requireUser(userId)
            val currentTier = requireNotNull(user.currentTier) {
                "activateIron called for user $userId with no currentTier — " +
                    "selectInitialTier must complete before any tier-transition method runs " +
                    "(User.kt kdoc, Batch B)"
            }
            val tierSelectedAt = requireNotNull(user.tierSelectedAt) {
                "activateIron called for user $userId with no tierSelectedAt — same " +
                    "precondition as currentTier above, set together by selectInitialTier"
            }
            check(
                ironCalibrationSatisfied(
                    tier = Tier.IRON,
                    tierSelectedAtEpochMilli = tierSelectedAt.toEpochMilli(),
                    calibrationWindowDays = user.calibrationWindowDays,
                    nowEpochMilli = now.toEpochMilli(),
                )
            ) {
                "Iron calibration gate not satisfied for user $userId: selected at " +
                    "$tierSelectedAt, window ${user.calibrationWindowDays} days, now $now " +
                    "(PRD §12.6 — no exception path)"
            }
            val event = writeEvent(userId, currentTier, Tier.IRON, TierEventKind.UPGRADE_ACCEPTED, reasonNote = null, now)
            userDao.update(user.copy(currentTier = Tier.IRON, tierActivationAt = now))
            event
        }
    }

    // --- shared plumbing -----------------------------------------------------------------

    private suspend fun transition(
        userId: UUID,
        toTier: Tier,
        kind: TierEventKind,
        reasonNote: String?,
        now: Instant,
    ): TierEvent {
        return database.withTransaction {
            val user = requireUser(userId)
            val currentTier = requireNotNull(user.currentTier) {
                "transition called for user $userId with no currentTier — " +
                    "selectInitialTier must complete before any tier-transition method runs " +
                    "(User.kt kdoc, Batch B)"
            }
            val event = writeEvent(userId, currentTier, toTier, kind, reasonNote, now)
            userDao.update(user.copy(currentTier = toTier))
            event
        }
    }

    private suspend fun writeEvent(
        userId: UUID,
        fromTier: Tier,
        toTier: Tier,
        kind: TierEventKind,
        reasonNote: String?,
        occurredAt: Instant,
    ): TierEvent {
        val event = TierEvent(
            id = UUID.randomUUID(),
            userId = userId,
            kind = kind,
            fromTier = fromTier,
            toTier = toTier,
            occurredAt = occurredAt,
            reasonNote = reasonNote,
        )
        tierDao.insertEvent(event)
        return event
    }

    /**
     * Every public method in this class transitions an *existing* tier to a new one — none of
     * them are the "no tier chosen yet" case, which is [selectInitialTier]'s job alone (and
     * that method deliberately doesn't call this helper — see its own body). Does NOT assert
     * [User.currentTier] non-null here, even though every caller needs that to be true: Kotlin
     * smart-casts from a null-check don't survive across a function boundary — asserting inside
     * this function would not change what type `requireUser(...)`'s *return value* is seen as
     * by callers, so it would be a no-op fix that looks like it works but doesn't (caught before
     * merge; see BUILD_PLAN.md Batch B note — this is a second concrete example, after
     * TierTransitionUseCase.explicitDowngrade's inverted-boolean bug, of exactly why nothing in
     * this authoring pipeline gets trusted without being checked here, since no compiler is
     * reachable to catch a mistake like this automatically). Each of this class's public methods
     * asserts `user.currentTier` non-null itself, right where it's used — see e.g.
     * [ironCrisisExit]'s existing `require(user.currentTier == Tier.IRON)`, which already
     * happened to do this correctly by coincidence (it needs the *value*, not just non-null,
     * so it was never at risk of this mistake) — the other methods needed the same treatment
     * added deliberately.
     */
    private suspend fun requireUser(userId: UUID): User =
        requireNotNull(userDao.get(userId)) { "No User found for id $userId" }
}
