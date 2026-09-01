package com.disciplineos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.disciplineos.data.dao.CachedGoalMissionDao
import com.disciplineos.data.dao.CachedMissionProfileDao
import com.disciplineos.data.dao.CachedUserDao
import com.disciplineos.data.dao.GoalMissionDao
import com.disciplineos.data.dao.DeviceCredentialsDao
import com.disciplineos.data.dao.EnforcementSessionDao
import com.disciplineos.data.dao.MissionProfileDao
import com.disciplineos.data.dao.PendingViolationDao
import com.disciplineos.data.dao.ProvisionalLedgerDao
import com.disciplineos.data.dao.TierDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.entity.CachedGoalMission
import com.disciplineos.data.entity.CachedMissionProfile
import com.disciplineos.data.entity.CachedUser
import com.disciplineos.data.entity.DeviceCredentials
import com.disciplineos.data.entity.EnforcementSession
import com.disciplineos.data.entity.GoalMission
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.PendingViolation
import com.disciplineos.data.entity.ProvisionalLedgerEntry
import com.disciplineos.data.entity.SyncMetadata
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerEntry
import net.sqlcipher.database.SupportFactory

/**
 * Enforcer database — stripped to enforcement-path entities only.
 *
 * Design doc §1 / §6: the Enforcer keeps a minimal local cache sufficient for zero-network
 * enforcement. Non-enforcement entities (MissionPeriod, MissionLogEntry, Trigger, Milestone,
 * OutputArtifact, OnboardingScreenEvent, PredictiveFailureAlertDismissal, UnsupervisedSignal,
 * AdherenceLedgerEntry) have been moved to web-app-reference/.
 *
 * New Enforcer-specific tables added per design doc §1.1:
 * - cached_user, cached_goal_missions, cached_mission_profiles (synced from Console)
 * - pending_violations (offline violation queue)
 * - provisional_ledger_entries (local Debt/Reputation estimates)
 * - sync_metadata, device_credentials (sync bookkeeping)
 *
 * Uses fallbackToDestructiveMigration() — same pre-launch policy as the original v14 DB
 * (DisciplineOsDatabase.kt kdoc). Version bumped to 20 to clearly separate from the
 * pre-split schema lineage.
 */
@Database(
    entities = [
        // Enforcement-path entities (retained from pre-split schema)
        User::class,
        EnforcementSession::class,
        Violation::class,
        LedgerEntry::class,
        TierEvent::class,
        MissionProfile::class,
        GoalMission::class,
        // Enforcer-specific entities (design doc §1.1)
        CachedUser::class,
        CachedGoalMission::class,
        CachedMissionProfile::class,
        PendingViolation::class,
        ProvisionalLedgerEntry::class,
        SyncMetadata::class,
        DeviceCredentials::class,
    ],
    // v20: Enforcer split — non-enforcement entities removed, new cached/provisional tables added.
    // Destructive migration (same pre-launch policy as v2-v14). See design doc §5.5.
    version = 20,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DisciplineOsDatabase : RoomDatabase() {
    // Enforcement-path DAOs
    abstract fun userDao(): UserDao
    abstract fun goalMissionDao(): GoalMissionDao
    abstract fun enforcementSessionDao(): EnforcementSessionDao
    abstract fun violationDao(): ViolationDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun tierDao(): TierDao
    abstract fun missionProfileDao(): MissionProfileDao

    // Enforcer-specific DAOs (design doc §1.1)
    abstract fun cachedUserDao(): CachedUserDao
    abstract fun cachedGoalMissionDao(): CachedGoalMissionDao
    abstract fun cachedMissionProfileDao(): CachedMissionProfileDao
    abstract fun pendingViolationDao(): PendingViolationDao
    abstract fun provisionalLedgerDao(): ProvisionalLedgerDao
    abstract fun deviceCredentialsDao(): DeviceCredentialsDao

    companion object {
        private const val DB_NAME = "disciplineos_core.db"

        fun build(context: Context, passphrase: ByteArray): DisciplineOsDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, DisciplineOsDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
