package com.disciplineos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.disciplineos.data.dao.UnsupervisedSignalDao
import com.disciplineos.data.entity.UnsupervisedSignal
import net.sqlcipher.database.SupportFactory

/**
 * Unsupervised Reliability database: UnsupervisedSignal only. Separate encryption key from
 * [DisciplineOsDatabase] per Architecture doc §3.1 ("needs its own encryption key/scope so
 * a deletion request for this category alone doesn't require touching or re-encrypting
 * Mission/Score data").
 *
 * Deliberately in its own file — see the note on [DisciplineOsDatabase] for why this file
 * never imports LedgerDao, TierDao, or any other consequence-side type.
 */
@Database(
    entities = [UnsupervisedSignal::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class UnsupervisedDatabase : RoomDatabase() {
    abstract fun unsupervisedSignalDao(): UnsupervisedSignalDao

    companion object {
        private const val DB_NAME = "disciplineos_unsupervised.db"

        fun build(context: Context, passphrase: ByteArray): UnsupervisedDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, UnsupervisedDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .build()
        }

        /**
         * PRD §40: Unsupervised Reliability data is separately deletable without affecting
         * Mission history, Discipline Score, or Reliability Index. Deleting the DB file
         * (rather than just DELETE-ing rows) also drops it from the encrypted-at-rest
         * store entirely, which is the stronger guarantee "walk away cleanly" implies
         * (Architecture doc §3.2).
         */
        fun deleteEntirely(context: Context) {
            context.deleteDatabase(DB_NAME)
        }
    }
}
