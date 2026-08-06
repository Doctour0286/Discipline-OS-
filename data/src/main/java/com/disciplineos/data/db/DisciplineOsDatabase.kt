package com.disciplineos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.disciplineos.data.dao.MissionDao
import com.disciplineos.data.dao.UserDao
import com.disciplineos.data.dao.ViolationDao
import com.disciplineos.data.entity.Mission
import com.disciplineos.data.entity.OutputArtifact
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
    entities = [User::class, Mission::class, Violation::class, LedgerEntry::class, OutputArtifact::class],
    version = 2, // v2 (Phase 1): LedgerEntry.pausedAt added for §26.4 dispute-pause semantics
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DisciplineOsDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun missionDao(): MissionDao
    abstract fun violationDao(): ViolationDao
    abstract fun ledgerDao(): LedgerDao

    companion object {
        private const val DB_NAME = "disciplineos_core.db"

        /**
         * [passphrase] must come from Android Keystore-backed storage at the call site
         * (e.g. EncryptedSharedPreferences or a Keystore-wrapped key), not hardcoded and
         * not stored alongside the DB file. This module doesn't own key management —
         * that's an app-module concern — it only requires a passphrase to open.
         */
        fun build(context: Context, passphrase: ByteArray): DisciplineOsDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, DisciplineOsDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .build()
        }
    }
}
