package com.disciplineos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.disciplineos.data.dao.MissionDao
import com.disciplineos.data.dao.MissionProfileDao
import com.disciplineos.data.dao.TierDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.MissionProfile
import com.disciplineos.data.entity.OutputArtifact
import com.disciplineos.data.entity.TierEvent
import com.disciplineos.data.entity.User
import com.disciplineos.data.entity.Violation
import com.disciplineos.data.ledger.LedgerDao
import com.disciplineos.data.ledger.LedgerEntry
import net.sqlcipher.database.SupportFactory

/**
 * Scoring/consequence database: User, Mission, Violation, LedgerEntry, OutputArtifact.
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
        Mission::class,
        Violation::class,
        LedgerEntry::class,
        OutputArtifact::class,
        TierEvent::class,
        MissionProfile::class,
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
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DisciplineOsDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun missionDao(): MissionDao
    abstract fun violationDao(): ViolationDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun tierDao(): TierDao
    abstract fun missionProfileDao(): MissionProfileDao

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
