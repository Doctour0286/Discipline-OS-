package com.disciplineos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.disciplineos.data.adherence.AdherenceLedgerDao
import com.disciplineos.data.adherence.AdherenceLedgerEntry
import com.disciplineos.data.dao.EnforcementSessionDao
import com.disciplineos.data.dao.GoalMissionDao
import com.disciplineos.data.dao.MilestoneDao
import com.disciplineos.data.dao.MissionLogEntryDao
import com.disciplineos.data.dao.MissionPeriodDao
import com.disciplineos.data.dao.MissionProfileDao
import com.disciplineos.data.dao.OnboardingEventDao
import com.disciplineos.data.dao.PredictiveFailureAlertDismissalDao
import com.disciplineos.data.dao.TierDao
import com.disciplineos.data.dao.TriggerDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.Milestone
import com.disciplineos.data.entity.MissionLogEntry
import com.disciplineos.data.entity.MissionPeriod
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.OnboardingScreenEvent
import com.disciplineos.data.entity.OutputArtifact
import com.disciplineos.data.entity.PredictiveFailureAlertDismissal
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.Trigger
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerEntry
import net.sqlcipher.database.SupportFactory

/**
 * Scoring/consequence database: User, EnforcementSession (formerly Mission), GoalMission and
 * its supporting entities, Violation, LedgerEntry, OutputArtifact.
 *
 * This database and [UnsupervisedDatabase] (see separate file in this package) are
 * DELIBERATELY separate Room databases — separate files, separate SQLite connections —
 * not separate tables in one database. Data Model doc §7 requires that "no enforcement
 * path can touch [UnsupervisedSignal]" be structurally true, not just policy-true. Two
 * Room databases cannot express a foreign key or a JOIN between them.
 *
 * Deliberately kept in its own file, separate from [UnsupervisedDatabase], even though
 * both are small — a shared top-level DI/wiring file is the one place that legitimately
 * needs to reference both database classes (see AppDatabaseModule in the app module, once
 * it exists), and keeping that reference out of both database files individually means
 * ArchitectureBoundaryTest's import-scan doesn't need a special-case exception for either.
 */
@Database(
    entities = [
        User::class,
        EnforcementSession::class,
        Violation::class,
        LedgerEntry::class,
        OutputArtifact::class,
        TierEvent::class,
        MissionProfile::class,
        OnboardingScreenEvent::class,
        PredictiveFailureAlertDismissal::class,
        GoalMission::class,
        MissionPeriod::class,
        MissionLogEntry::class,
        Trigger::class,
        Milestone::class,
        AdherenceLedgerEntry::class,
    ],
    // v2 (Phase 1): LedgerEntry.pausedAt added for §26.4 dispute-pause semantics.
    // v3 (Phase 1, TierTransitionUseCase): TierEvent table added; User gained
    // debtAccrualPausedUntil / tribunalDeferredUntil (§12.4.3 Crisis Downgrade mechanics —
    // see User.kt kdoc). Still no real Migration written — see ROADMAP.md §5.7, which this
    // bump inherits unchanged: the app has never shipped to a real user, so
    // fallbackToDestructiveMigration() below is the decision that item asked for, made
    // explicitly rather than left open a second time.
    // v4 (Phase 3, Mission Profile Setup): MissionProfile table added — closes the
    // pre-existing gap where Mission.missionProfileId referenced nothing real (see
    // MissionProfile.kt kdoc for the full account; logged ROADMAP.md §5). No migration
    // written, same v3 reasoning applies unchanged — still pre-launch, still no real
    // installed base whose data fallbackToDestructiveMigration() would put at risk.
    // v5 (§5.5/§5.9/§5.15 implementation): no schema change for §5.5 (window is computed
    // from existing Violation.detectedAt, nothing new stored). User gained
    // consecutiveDaysBelowFloor (§5.9 demotion tracking) and lastExplicitDowngradeAt (§5.15
    // cooldown tracking) — see User.kt kdoc for both. Same v3/v4 fallbackToDestructiveMigration
    // reasoning applies unchanged.
    // v6 (Batch B, resolving merge conflict with the v5 above — both bumped independently
    // from the same v4 base): User gained flaggedCategories + 4 nullable tier fields — see
    // User.kt. Same fallbackToDestructiveMigration reasoning applies unchanged; still no real
    // installed base.
    // v7 (Batch B, Unsupervised Reliability Opt-In, §2.7): onboarding_screen_events table
    // added — see OnboardingScreenEvent.kt kdoc for why this is its own narrow table rather
    // than reusing TierEvent or a general-purpose analytics scheme. No User schema change
    // this bump: unsupervisedReliabilityOptIn / unsupervisedReliabilityOptInAt already existed
    // on User (added in an earlier phase, unused until this pass wires a real screen to
    // write them — see User.kt). Same fallbackToDestructiveMigration reasoning applies
    // unchanged; still no real installed base.
    // v8 (Phase 4, Behavioral Fingerprint / Predictive Failure Rules, F1–F5):
    // predictive_failure_alert_dismissals table added — see
    // PredictiveFailureAlertDismissal.kt kdoc. Backs Fingerprint doc §5's "logged, not just
    // discarded" dismissal-accuracy requirement. No other schema change this bump — F1–F5's
    // signals are all computed on read from existing Mission/Violation/LedgerEntry rows
    // rather than persisted as a separate BehavioralFingerprint/FingerprintSignal table,
    // matching this project's stated bias (MissionProfileDao's "no @Update yet" reasoning,
    // restated here) against schema that isn't earning its keep yet — nothing currently
    // needs a durable FingerprintSignal row, only the accuracy log does. Same
    // fallbackToDestructiveMigration reasoning applies unchanged; still no real installed
    // base.
    // v9 (Goal-Oriented Mission Model, ROADMAP.md §5.32/§G1, first pass): `Mission` renamed to
    // `EnforcementSession` (same `missions` table, same columns, plus one new nullable
    // `goalMissionId` column). Five new tables added: `goal_missions`, `mission_periods`,
    // `mission_log_entries`, `triggers`, `milestones`. Superseded by v10 below — this first pass
    // shipped a GoalMission shape and DAO naming that diverged from
    // `06_GOAL_ORIENTED_MISSION_MODEL_INTEGRATION_PLAN.md` §2.1/§2.2's actual spec (see v10
    // comment for what changed and why). Kept here, not rewritten, so the migration-comment
    // history stays an accurate record of what each version actually shipped.
    // v10 (Goal-Oriented Mission Model, Integration Plan §2.1/§2.2, conformance pass): brings the
    // schema in line with the Integration Plan's literal field/DAO spec, which v9 diverged from.
    // `EnforcementSession.goalMissionId: UUID?` -> `missionId: UUID` (non-null) +
    // `missionPeriodId: UUID?` (see EnforcementSession.kt kdoc — Batch G2's
    // FirstMissionSchedulingFragment fix, landing in this same pass, guarantees a parent
    // GoalMission always exists, so missionId no longer needs to be nullable). `GoalMission`
    // rebuilt to the Plan's exact field list (archetype/targetDirection/cadenceType/resetMode/
    // measurementSource/lifecycleStage/adherenceScore/adherenceWindow, no missionProfileId — see
    // GoalMission.kt kdoc). `MissionPeriod` rebuilt to periodType/windowStart/windowEnd/
    // targetDurationMin/deadlineTime/enforcementProfileId. `goalMissionId` FK columns on
    // MissionPeriod/MissionLogEntry/Trigger/Milestone renamed to `missionId`, matching the Plan's
    // naming. `missionDao()`/`MissionDao` renamed to `enforcementSessionDao()`/
    // `EnforcementSessionDao` per Plan §2.3, reversing v9's low-churn "keep the DAO name" choice.
    // Still no real Migration object — Integration Plan §9 explicitly rules one out
    // ("this stays a destructive version bump, matching v2 through v8"), re-confirming the same
    // fallbackToDestructiveMigration reasoning as every bump above: still pre-launch, still no
    // real installed base (no release tag, no store listing — re-checked this pass). Same
    // "DebugSeeder already assumes a fresh reinstall re-seeds from scratch" reasoning v9 gave
    // still applies. Revisit before the first real pilot install (Phase 5).
    // v11 (Batch G3, Adherence engine, ROADMAP.md §5.36): two schema changes, bundled in one
    // bump per this project's own precedent (v6 folded two independent additions the same way).
    // (1) `MissionLogEntry` gains `numericValue: Double?` and `didOccur: Boolean?`, and `note`
    // becomes nullable — correcting a real gap: the base design doc
    // (`06_GOAL_ORIENTED_MISSION_MODEL.md` §3.3) always specified both fields as Adherence's
    // actual hit/miss input ("computed from MissionLogEntry presence/value"), but the Integration
    // Plan's §2.1 field list dropped them and v9/v10 shipped without them — an unflagged
    // divergence from the document it was meant to summarize, only caught while implementing this
    // batch (no prior code depended on the missing fields, so nothing else changes). See
    // MissionLogEntry.kt kdoc for the full account. (2) `GoalMission` gains
    // `consecutiveWindowsBelowThreshold: Int = 0` (Integration Plan §7.5, a small motivated
    // addition the Plan itself already flagged as a deviation from the base doc's stated field
    // list — needed to detect sustained-miss-pattern decay the same way
    // `User.consecutiveDaysBelowFloor` does for Reputation). New table `adherence_ledger_entries`
    // (`AdherenceLedgerEntry::class`) — see that class's kdoc for why this is a physically
    // separate table from `ledger_entries`, not a new `LedgerMetric` value. Same
    // fallbackToDestructiveMigration reasoning as every bump above: still pre-launch, still no
    // real installed base (re-checked this pass).
    // v12 (fix/g1-trigger-entity-shape, ROADMAP.md §5.41): `Trigger` fully rebuilt — the shape
    // that shipped with Batch G1 (`TriggerConditionType { INACTIVITY, SCHEDULE_MISS, MANUAL }`,
    // `conditionValue: String?`, `lastFiredAt: Instant?`) modeled a session-inactivity watchdog,
    // not the implementation-intention cue entity base doc §3.4/§4.3 actually specify
    // (`cueType: TIME_OF_DAY|PRECEDING_EVENT|LOCATION|APP_OPEN|MANUAL`, `cueDescription`,
    // `responseDescription`, `cueTimeOfDay`, `cuePrecedingMissionId`, `cueLocationLabel`,
    // `cueTriggerPackageId`) — same "diverged from the document it was meant to summarize,
    // caught only while implementing the batch that needed the real shape" pattern v11 already
    // found twice (MissionLogEntry, ApplyAdherenceDecayUseCase.Result). See Trigger.kt's own
    // kdoc for the full account. No prior code read or wrote the old shape (`TriggerDao.insert`/
    // `forMission` exist but were called nowhere), so nothing else changes and no data is lost
    // under this project's still-in-effect fallbackToDestructiveMigration() policy.
    version = 12,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DisciplineOsDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun enforcementSessionDao(): EnforcementSessionDao
    abstract fun violationDao(): ViolationDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun tierDao(): TierDao
    abstract fun missionProfileDao(): MissionProfileDao
    abstract fun onboardingEventDao(): OnboardingEventDao
    abstract fun predictiveFailureAlertDismissalDao(): PredictiveFailureAlertDismissalDao
    abstract fun goalMissionDao(): GoalMissionDao
    abstract fun missionPeriodDao(): MissionPeriodDao
    abstract fun missionLogEntryDao(): MissionLogEntryDao
    abstract fun triggerDao(): TriggerDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun adherenceLedgerDao(): AdherenceLedgerDao

    companion object {
        private const val DB_NAME = "disciplineos_core.db"

        /**
         * [passphrase] must come from Android Keystore-backed storage at the call site
         * (e.g. EncryptedSharedPreferences or a Keystore-wrapped key), not hardcoded and
         * not stored alongside the DB file. This module doesn't own key management —
         * that's an app-module concern — it only requires a passphrase to open.
         *
         * ROADMAP.md §5.7 asked for an explicit pre-launch migration policy rather than a
         * silently-picked default. Decision, made here rather than left open a second time
         * this phase: `fallbackToDestructiveMigration()` is acceptable pre-launch — this app
         * has never shipped to a real device, so there is no existing user's Debt/Reputation
         * history to protect yet, and writing real `Migration` objects now, against a schema
         * that's still changing every phase, would be exactly the kind of premature
         * complexity the specs otherwise argue against (Data Model doc §3.1's reasoning
         * applies just as well to migration code as to invented formula weights: don't build
         * the precise version of something before there's real data — or in this case, a
         * real installed base — to make it worth getting right). Revisit before the first
         * real pilot install (ROADMAP.md Phase 5) — destructive fallback stops being
         * acceptable the moment "your discipline history randomly resets" could happen to an
         * actual person using the app, not just a developer iterating on schema.
         */
        fun build(context: Context, passphrase: ByteArray): DisciplineOsDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, DisciplineOsDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
