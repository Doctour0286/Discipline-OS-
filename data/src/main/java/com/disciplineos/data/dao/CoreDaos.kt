package com.disciplineos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.disciplineos.data.entity.DisputeStatus
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.OnboardingScreenEvent
import com.disciplineos.data.entity.OnboardingScreenEventOutcome
import com.disciplineos.data.entity.OutputArtifact
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.TierEventKind
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import java.time.Instant
import java.util.UUID

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: UUID): User?

    /**
     * Phase 2 (`MissionAccessibilityService`, ROADMAP.md): the accessibility-event handler
     * needs "the current user" on every foreground-app-change event, but this app has no
     * multi-profile/login concept anywhere in the spec — checked, and logged as a judgment
     * call in ROADMAP.md §5 rather than assumed silently. `LIMIT 1` here encodes that same
     * single-local-user assumption at the DAO layer instead of via ad hoc SQL at the call
     * site, matching this module's stated preference (every other DAO in this file) for
     * auditable named queries over raw `openHelper` access. Returns null before onboarding
     * has created a `User` row at all — callers must treat that as "nothing to enforce yet,"
     * not an error.
     */
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getSingleLocalUser(): User?
}

/**
 * Backs [com.disciplineos.domain.usecase.TierTransitionUseCase] — the append-only event log
 * described in [TierEvent]'s kdoc, plus the current-tier mutation on [User] itself. Kept as
 * its own `@Dao` (rather than folded into [UserDao]) because it reads/writes a distinct
 * table with its own query shapes, matching how [ViolationDao] and the ledger DAOs are each
 * scoped to one entity family rather than grouped by "things that touch tier."
 */
@Dao
interface TierDao {
    @Insert
    suspend fun insertEvent(event: TierEvent)

    @Query("SELECT * FROM tier_events WHERE userId = :userId ORDER BY occurredAt ASC")
    suspend fun eventsFor(userId: UUID): List<TierEvent>

    /**
     * Most recent event of a specific kind for a user, or null if none exists — used to
     * check e.g. "has this user already used the Iron Crisis Exit" without loading the full
     * history. `LIMIT 1` on a DESC-ordered query rather than a dedicated MAX/aggregate query,
     * matching the "simple enough to audit by reading it" posture the rest of this module
     * already favors over cleverness (see `ArchitectureBoundaryTest`'s own kdoc).
     */
    @Query(
        """
        SELECT * FROM tier_events
        WHERE userId = :userId AND kind = :kind
        ORDER BY occurredAt DESC
        LIMIT 1
        """
    )
    suspend fun mostRecentEventOfKind(userId: UUID, kind: TierEventKind): TierEvent?
}

/**
 * Backs [OnboardingScreenEvent] — see that file's kdoc for why this is its own narrow `@Dao`
 * rather than folded into [UserDao] or [TierDao]: write-only UX instrumentation, not
 * domain-behavioral state any other DAO's existing query shape fits.
 */
@Dao
interface OnboardingEventDao {
    @Insert
    suspend fun insert(event: OnboardingScreenEvent)

    /**
     * Backs the completion/drop-off measurement §2.7 itself asks for — a simple count of
     * how many [OnboardingScreenEvent] rows exist for a given [screenId]/[outcome] pair,
     * e.g. "how many VIEWED vs. how many ACCEPTED" to derive a completion rate. Not scoped to
     * a single [userId] — this is meant to answer a product/instrumentation question across
     * the whole install base, not "did this one user complete this screen" (callers wanting
     * that narrower answer should query row-by-row via a future per-user method once a real
     * call site needs it, per this project's stated preference for adding query shapes when a
     * real second call site shows up, not preemptively — see [MissionProfileDao]'s "no
     * `@Update` yet" note for the identical reasoning applied elsewhere).
     */
    @Query("SELECT COUNT(*) FROM onboarding_screen_events WHERE screenId = :screenId AND outcome = :outcome")
    suspend fun countByOutcome(screenId: String, outcome: OnboardingScreenEventOutcome): Int
}

/**
 * Backs [EnforcementSession] (renamed from `Mission`, ROADMAP.md §5.32 — see that entity's own
 * kdoc for the full rationale). Interface renamed `MissionDao` → `EnforcementSessionDao` per
 * Integration Plan §2.3 ("rename `abstract fun missionDao(): MissionDao` → `abstract fun
 * enforcementSessionDao(): EnforcementSessionDao`"), the plan's deliberate reversal of an earlier
 * low-churn "keep the DAO name" choice — every call site in `:app`/`:domain` updated in this same
 * pass. The `missions` table name and the `MissionStatus` enum are still kept unchanged (no
 * plan clause asks for either to move) — only the DAO interface name and its accessor method
 * changed, matching the entity rename it backs.
 */
@Dao
interface EnforcementSessionDao {
    @Insert
    suspend fun insert(mission: EnforcementSession)

    @Update
    suspend fun update(mission: EnforcementSession)

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun get(id: UUID): EnforcementSession?

    /**
     * Phase 2 (Accessibility Service, ROADMAP.md) needs this to answer "is there an active
     * Mission for this user right now, and if so what's its allow/blocklist" on every
     * foreground-app-change event — the highest-frequency read path in the whole app, so this
     * is a direct indexed-equality query rather than something derived from [resolvedMissionsSince]
     * (which deliberately excludes ACTIVE missions and scans a time window, both wrong for this
     * use). Returns null if the user has no Mission currently ACTIVE, which the interception
     * logic treats as "nothing to enforce right now" — not an error.
     *
     * Assumes at most one ACTIVE Mission per user at a time. Nothing in the PRD or Data Model
     * doc states this explicitly, but §7 (Mission Launch Protocol) and §14 (Distraction
     * Interception) both describe Mission state in the singular ("the device enters a Mission
     * Environment"), and `EnforcementSession.status` has no schema-level uniqueness constraint enforcing
     * it — so this is a real, currently-unenforced assumption, not a spec-derived guarantee.
     * `LIMIT 1` is defensive (never crash the enforcement loop on this query), not a claim that
     * a second concurrent ACTIVE row is an expected or handled case. Logged in ROADMAP.md §5.
     */
    @Query("SELECT * FROM missions WHERE userId = :userId AND status = 'ACTIVE' LIMIT 1")
    suspend fun activeMissionFor(userId: UUID): EnforcementSession?

    /**
     * Added ROADMAP.md §5.36/Batch G3. Backs `ApplyAdherenceDecayUseCase`'s scope check (base
     * doc §4.2: Adherence "applies to Outcome-driven and Constraint missions, and to
     * Behavior-driven missions that have no attached EnforcementSession") — a Behavior-driven
     * `GoalMission` is in scope for Adherence only if this returns empty for its id. Existence-
     * only, not `forMission`-style full-row retrieval (no other call site needs the rows
     * themselves, only whether any exist), so `COUNT(*) > 0` rather than a `List` return keeps
     * the query itself expressing exactly what's checked.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM missions WHERE missionId = :goalMissionId)")
    suspend fun hasAnySessionFor(goalMissionId: UUID): Boolean

    /**
     * Rolling window query backing Reliability Index (Data Model §3.2) and Debt Ceiling's
     * avg_mission_duration_min (§3.4). Excludes ACTIVE missions — only resolved ones count.
     */
    @Query(
        """
        SELECT * FROM missions
        WHERE userId = :userId
          AND actualStart >= :since
          AND status IN ('COMPLETED', 'VIOLATED')
        ORDER BY actualStart ASC
        """
    )
    suspend fun resolvedMissionsSince(userId: UUID, since: Instant): List<EnforcementSession>

    @Insert
    suspend fun insertOutputArtifact(artifact: OutputArtifact)

    @Query("SELECT * FROM output_artifacts WHERE missionId = :missionId")
    suspend fun outputArtifactsFor(missionId: UUID): List<OutputArtifact>

    /**
     * Backs `ApplyReputationDecayUseCase`'s `decay_per_missed_day` term (Data Model §3.5).
     * A "missed day" is scoped here as a calendar day with no COMPLETED Mission and at least
     * one VIOLATED or scheduled-but-never-started Mission — the spec names the constant
     * (`decay_per_missed_day`) but never defines what makes a day "missed" precisely, so this
     * query's WHERE clause is the concrete decision: any day with a completed Mission is
     * excluded regardless of other activity that day, since decay is meant to penalize
     * non-compliance, not coexist with a day the user actually showed up for at least one
     * Mission. Returns a count, not the individual missions, since the use-case only needs
     * "how many missed days in this window," not which ones.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT DATE(actualStart / 1000, 'unixepoch')) FROM missions
        WHERE userId = :userId AND actualStart >= :since
          AND status = 'VIOLATED'
          AND DATE(actualStart / 1000, 'unixepoch') NOT IN (
              SELECT DATE(actualStart / 1000, 'unixepoch') FROM missions
              WHERE userId = :userId AND status = 'COMPLETED' AND actualStart >= :since
          )
        """
    )
    suspend fun missedDaysSince(userId: UUID, since: Instant): Int

    /** Backs `recovery_per_completed_mission` (Data Model §3.5) — a simple count, one credit per completed Mission in the window. */
    @Query("SELECT COUNT(*) FROM missions WHERE userId = :userId AND status = 'COMPLETED' AND actualStart >= :since")
    suspend fun completedMissionsSince(userId: UUID, since: Instant): Int

    /**
     * Backs Rule F1 (Time-of-Day Violation Clustering, Fingerprint doc §3): "the last 10
     * Missions with any violation" — a Mission-scoped window, not a violation-scoped one (a
     * single Mission can have more than one Violation, so these aren't the same count). Used
     * by [com.disciplineos.domain.usecase.ComputeBehavioralFingerprintUseCase] to bound which
     * Missions' violations [ViolationDao.violationTimestampsFor] results should even be
     * considered for, before binning by hour-of-day. `DISTINCT` + a subquery rather than a
     * plain join, since a Mission with 3 violations must still only count once toward the
     * "last 10 Missions" limit — an inner join would count it three times.
     */
    @Query(
        """
        SELECT id FROM missions
        WHERE userId = :userId
          AND id IN (SELECT DISTINCT missionId FROM violations)
        ORDER BY actualStart DESC
        LIMIT :limit
        """
    )
    suspend fun missionIdsWithAnyViolation(userId: UUID, limit: Int): List<UUID>

    /**
     * Backs Rule F2 (Pre-Mission Cancellation Pattern, Fingerprint doc §3): "Missions
     * cancelled/aborted within the first 5 minutes of `actual_start`, as a proportion of all
     * Missions" over the last 14 days. A Mission counts as an early cancellation here if it
     * reached a resolved (non-ACTIVE) state and `actualEnd - actualStart` is under
     * [maxEarlyMinutes] — VIOLATED and DISPUTED both qualify (F2 is about the Mission ending
     * fast, not about which resolution it ended in; a dispute doesn't undo the fact that the
     * user stopped almost immediately), matching how the spec's own framing ("the scope or
     * allowlist right for what you're trying to do") is about elapsed time, not disposition.
     * `ABORTED_CRISIS_EXIT` is deliberately excluded — Data Model §5 / PRD §12.4.4 already
     * treat a crisis exit as "not counted like a normal Violation for consequence purposes,"
     * and F2's own framing (a *design* signal about the Mission Profile) has nothing to do
     * with why a crisis exit happened, so folding it in here would misattribute a safety
     * mechanism as a scoping problem.
     */
    @Query(
        """
        SELECT COUNT(*) FROM missions
        WHERE userId = :userId AND actualStart >= :since
          AND status IN ('COMPLETED', 'VIOLATED', 'DISPUTED')
          AND actualEnd IS NOT NULL
          AND (actualEnd - actualStart) <= (:maxEarlyMinutes * 60000)
        """
    )
    suspend fun earlyCancelledMissionsSince(userId: UUID, since: Instant, maxEarlyMinutes: Int): Int

    /**
     * Denominator for Rule F2's proportion — every resolved Mission in the window, regardless
     * of how it ended. `ACTIVE` is excluded (a Mission still in progress can't yet be judged
     * as "cancelled early" or not).
     */
    @Query(
        """
        SELECT COUNT(*) FROM missions
        WHERE userId = :userId AND actualStart >= :since AND status != 'ACTIVE'
        """
    )
    suspend fun resolvedMissionCountSince(userId: UUID, since: Instant): Int
}

/**
 * Backs [MissionProfile] (see that file's kdoc for why this table exists at all — closes a
 * pre-existing gap, `EnforcementSession.missionProfileId` had nothing to point at before this).
 * Kept as
 * its own `@Dao` matching [TierDao]'s own stated reasoning: a distinct table with its own
 * query shape, not folded into [EnforcementSessionDao] just because the two entities are related.
 *
 * No `@Update` yet — Mission Profile Setup (Onboarding §2.8, this pass) only ever creates the
 * first Profile a user has. Editing an existing Profile is real future work (a profile-picker
 * UI doesn't exist yet either — see [MissionProfile]'s kdoc) but nothing in this pass needs
 * it, so it isn't speculatively added ahead of a real call site needing it.
 */
@Dao
interface MissionProfileDao {
    @Insert
    suspend fun insert(profile: MissionProfile)

    @Query("SELECT * FROM mission_profiles WHERE id = :id")
    suspend fun get(id: UUID): MissionProfile?

    /**
     * Onboarding §2.8's "should default to suggestions... rather than a blank list" requires
     * knowing what a user already has, if anything — used by [MissionProfileSetupFragment]'s
     * same re-entry guard every other onboarding screen in this project already applies
     * (Back-then-resubmit, or a slow double-tap, must not create a second Profile row; see
     * `TierSelectionFragment`/`TierConfirmationFragment`'s own kdoc for the identical
     * reasoning applied to `User`). `LIMIT 1` matches this project's existing single-profile
     * assumption for this pass — see [MissionProfile]'s kdoc on why a picker UI is future
     * work, not this pass's scope.
     */
    @Query("SELECT * FROM mission_profiles WHERE userId = :userId LIMIT 1")
    suspend fun mostRecentFor(userId: UUID): MissionProfile?
}

@Dao
interface ViolationDao {
    @Insert
    suspend fun insert(violation: Violation)

    @Update
    suspend fun update(violation: Violation)

    @Query("SELECT * FROM violations WHERE id = :id")
    suspend fun get(id: UUID): Violation?

    @Query("SELECT * FROM violations WHERE missionId = :missionId")
    suspend fun forMission(missionId: UUID): List<Violation>

    /**
     * §3.5 / §27.2 shared-cause guard support: every Violation sharing a `rootCauseClusterId`,
     * regardless of which Mission it's attached to. `rootCauseClusterId` is not scoped to a
     * single Mission in the schema (Data Model §2.3 imposes no such constraint), so the guard
     * in [com.disciplineos.domain.usecase.RecordViolationUseCase] needs this rather than
     * [forMission] to find every sibling penalty candidate.
     */
    @Query("SELECT * FROM violations WHERE rootCauseClusterId = :clusterId")
    suspend fun forRootCauseCluster(clusterId: UUID): List<Violation>

    /**
     * §26.4 dispute flow entry point: flags a violation and freezes consequences.
     * consequencePaused = true stops Debt/Reputation writes for this violation until
     * resolution — the ledger-writing code must check this flag before applying a new
     * penalty (existing entries are handled separately via LedgerDao.reverseEntriesForViolation
     * if the dispute is later overturned).
     */
    @Query(
        """
        UPDATE violations
        SET disputeStatus = 'FLAGGED', disputeFlaggedAt = :flaggedAt, consequencePaused = 1
        WHERE id = :violationId
        """
    )
    suspend fun flagDispute(violationId: UUID, flaggedAt: Instant)

    @Query("UPDATE violations SET disputeStatus = :status, consequencePaused = :paused WHERE id = :violationId")
    suspend fun resolveDispute(violationId: UUID, status: DisputeStatus, paused: Boolean)

    /**
     * Reliability Index denominator per Data Model §3.2: excludes missions with
     * dispute_status = upheld IN THE USER'S FAVOR — i.e. OVERTURNED, not UPHELD.
     * ("Upheld" in the enum means the original violation stands; "overturned" means the
     * user's dispute succeeded. The PRD's prose uses "upheld" loosely to mean "resolved in
     * the user's favor" — this query follows the enum semantics in §2.3/§6, not the prose.)
     */
    @Query(
        """
        SELECT COUNT(*) FROM violations v
        INNER JOIN missions m ON v.missionId = m.id
        WHERE m.userId = :userId AND m.actualStart >= :since AND v.disputeStatus != 'OVERTURNED'
        """
    )
    suspend fun countingViolationsSince(userId: UUID, since: Instant): Int

    /**
     * Backs Rule F1 (Time-of-Day Violation Clustering, Behavioral Fingerprint & Predictive
     * Failure Rules Spec §3) — [detectedAt] timestamps for every Violation attached to any
     * Mission in [missionIds] (see [EnforcementSessionDao.missionIdsWithAnyViolation] for how that list is
     * bounded to "the last 10 Missions with any violation" per that spec). Same `IN (:list)`
     * pattern [forRootCauseCluster]'s neighbor [LedgerDao.activeEntriesForViolations] already
     * uses elsewhere in this codebase. Deliberately does NOT exclude OVERTURNED disputes the
     * way [countingViolationsSince] does — F1 asks "when do violations cluster," a
     * descriptive/pattern question, not "how many violations count against Reliability Index,"
     * a scoring question; those are legitimately different filters over the same table, not an
     * oversight.
     */
    @Query("SELECT detectedAt FROM violations WHERE missionId IN (:missionIds)")
    suspend fun detectedAtTimestampsForMissions(missionIds: List<UUID>): List<Instant>
}
