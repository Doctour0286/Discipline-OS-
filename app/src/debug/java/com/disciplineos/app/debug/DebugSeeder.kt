package com.disciplineos.app.debug

import com.disciplineos.data.db.DisciplineOsDatabase
import com.disciplineos.data.entity.CadenceType
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.LifecycleStage
import com.disciplineos.data.entity.MeasurementSource
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.MissionStatus
import com.disciplineos.data.entity.ResetMode
import com.disciplineos.data.entity.Tier
import com.disciplineos.data.entity.User
import java.time.Instant
import java.util.UUID

/**
 * ROADMAP.md §4(c) — Phase 2 device verification. There is currently no way to create a
 * [EnforcementSession] row on-device: no UI exists yet (Phase 3), and no seed mechanism existed
 * before this file. Without one, [com.disciplineos.app.enforcement.MissionAccessibilityService]
 * has nothing to enforce, so real interception can never be triggered and observed on a real
 * device — the one remaining gap the roadmap calls out before Phase 2's exit criteria are
 * honestly checkable end-to-end.
 *
 * **Why this exists instead of raw SQL or an instrumented test** (full reasoning in
 * ROADMAP.md §4(c)):
 * - Raw SQL against the Room DB file is out: the DB is SQLCipher-encrypted
 *   ([com.disciplineos.app.di.DbPassphraseProvider]), and hand-written INSERTs would bypass
 *   Room's type converters and FK handling regardless.
 * - An instrumented test via `adb shell am instrument` is out: Termux and the target device
 *   are the same physical phone, and `adb` has no "device talking to itself" path.
 * - So: a small, dedicated, debug-only seed class that calls the real [DisciplineOsDatabase]
 *   DAOs — the same `UserDao`/`EnforcementSessionDao` insert methods every use-case in this codebase
 *   already relies on — rather than any new or hand-rolled persistence logic.
 *
 * **Why this lives under `src/debug/`, not `src/main/`:** Gradle's `debug` source set is
 * compiled into debug builds only. This is a build-system-enforced guarantee that this class
 * structurally cannot ship in a release build, not a promise resting on someone remembering to
 * delete a debug-only code path later — the same reasoning this codebase already applies
 * elsewhere to hard boundaries (e.g. `UnsupervisedSignal`'s physical database isolation,
 * Data Model doc §13.3) rather than a runtime flag alone.
 *
 * **Idempotency:** [seedIfNeeded] is a no-op if an [EnforcementSession] already exists for the
 * seeded user. It is meant to be called once, deliberately, from application start — not on
 * every launch — so re-opening the app after the first seed does not duplicate or re-create
 * rows. Call-site wiring lives in [com.disciplineos.app.DisciplineOsApplication.onCreate],
 * gated behind `BuildConfig.DEBUG` there (belt-and-suspenders with this source set's own
 * debug-only placement — see that file's kdoc for why both guards exist).
 *
 * **What gets seeded:** one [User] at [Tier.WARDEN] (the middle of the four tiers — enough to
 * exercise the Warden Voice branch of [com.disciplineos.domain.policy.InterceptionPolicy]
 * without needing Iron's calibration-gate prerequisites just to see an interception at all)
 * and one [EnforcementSession] with `status = ACTIVE` and a non-empty
 * [EnforcementSession.blocklist], so `MissionAccessibilityService.activeMissionFor(userId)`
 * (see `EnforcementSessionDao`'s kdoc on that query) finds a real row to enforce against. Tier can be
 * changed by re-seeding with a different [Tier] once Recruit/Operator/Iron passes are also
 * needed (ROADMAP.md §4(c) step 7 calls for testing all three interception-relevant tiers
 * separately) — this class supports that by taking `tier` as a parameter rather than
 * hardcoding Warden, even though the current one-line call site only exercises the Warden case
 * by default.
 *
 * **Explicitly test infrastructure, not silent scaffolding** (ROADMAP.md §4(c) step 6): once
 * on-device verification across all three tiers is complete, the roadmap calls for an explicit
 * decision — recorded in ROADMAP.md, not left implicit — on whether this class stays (useful
 * for future manual QA passes) or gets deleted.
 */
object DebugSeeder {

    /** ROADMAP.md §4(c) step 7 — on-device interception verification needs a package the
     * Accessibility Service will actually see resumed to the foreground, which means a real
     * installed app, not the placeholder id (`com.example.blocked`) used by
     * `InterceptionControllerTest`'s unit-level seeding helper. Using the on-device logcat
     * reader app (`com.dp.logcatapp`) here specifically because it's already installed for
     * this same verification pass and harmless to block (no functionality lost by having it
     * intercepted — it isn't something Missions elsewhere in this codebase depend on).
     *
     * **Temporary, for manual on-device testing only** — swap back to a real Mission-relevant
     * blocklist (or make this configurable) once §4(c) step 7's three-tier walk is done; see
     * this file's class-level kdoc, "Explicitly test infrastructure, not silent scaffolding,"
     * for the standing rule this falls under. */
    private const val SEEDED_BLOCKED_PACKAGE = "com.dp.logcatapp"

    /**
     * Seeds one [User] and one ACTIVE [EnforcementSession] if — and only if — no session
     * exists yet for that user. Safe to call on every app start; only the first call (per fresh
     * install, or after this method's own idempotency check finds nothing) actually writes
     * anything.
     *
     * @param tier which enforcement tier to seed the user at — determines which branch of
     *   [com.disciplineos.domain.policy.InterceptionPolicy] / the interception screen's
     *   tier-dependent content (Onboarding doc §3.1) gets exercised on trigger. Defaults to
     *   [Tier.WARDEN] per this file's own kdoc above.
     * @return the seeded [EnforcementSession], or null if seeding was skipped because one
     *   already existed (the idempotent no-op path) — callers that only care about "is there
     *   something to trigger interception against now" can treat both outcomes the same way by
     *   re-reading via `EnforcementSessionDao.activeMissionFor`, but the return value lets a caller (or
     *   [DebugSeederTest]) distinguish "created now" from "already there" directly.
     */
    suspend fun seedIfNeeded(database: DisciplineOsDatabase, tier: Tier = Tier.WARDEN): EnforcementSession? {
        val userDao = database.userDao()
        val enforcementSessionDao = database.enforcementSessionDao()
        val goalMissionDao = database.goalMissionDao()

        val existingUser = userDao.getSingleLocalUser()
        val userId = existingUser?.id ?: UUID.randomUUID()

        if (existingUser == null) {
            val now = Instant.now()
            userDao.insert(
                User(
                    id = userId,
                    createdAt = now,
                    currentTier = tier,
                    tierSelectedAt = now,
                    tierActivationAt = now,
                    onboardingConsentVersion = "debug-seed-v1",
                ),
            )
        }

        // Idempotency check: an ACTIVE Mission for this user already existing means seeding
        // already happened (or the real app created one) — never insert a second one.
        if (enforcementSessionDao.activeMissionFor(userId) != null) {
            return null
        }

        val now = Instant.now()
        // Minimal auto-created parent GoalMission — same shape as
        // FirstMissionSchedulingFragment's real fix (Integration Plan §3.1), since an
        // EnforcementSession can no longer exist without one (v10, EnforcementSession.missionId
        // is non-null).
        val goalMission = GoalMission(
            id = UUID.randomUUID(),
            userId = userId,
            title = "Debug Seed Session",
            archetype = MissionArchetype.BEHAVIOR_DRIVEN,
            targetDirection = null,
            targetValue = null,
            unit = null,
            cadenceType = CadenceType.NONE,
            resetMode = ResetMode.ROLLING_WINDOW,
            measurementSource = MeasurementSource.AUTOMATIC,
            lifecycleStage = LifecycleStage.ENFORCING,
            adherenceScore = null,
            adherenceWindow = null,
            createdAt = now,
            archivedAt = null,
        )
        goalMissionDao.insert(goalMission)

        val mission = EnforcementSession(
            id = UUID.randomUUID(),
            userId = userId,
            missionId = goalMission.id,
            missionPeriodId = null,
            scheduledStart = null,
            actualStart = now,
            actualEnd = null,
            plannedDurationMin = 30,
            status = MissionStatus.ACTIVE,
            allowlist = emptyList(),
            blocklist = listOf(SEEDED_BLOCKED_PACKAGE),
            missionProfileId = UUID.randomUUID(),
        )
        enforcementSessionDao.insert(mission)
        return mission
    }
}
