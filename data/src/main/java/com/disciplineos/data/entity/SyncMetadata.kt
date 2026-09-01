package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Sync bookkeeping — single row tracking when the last successful sync occurred and
 * the last sync sequence number for gap detection / idempotent replay.
 * Design doc §1.1 — "sync_metadata".
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val id: Int = 1, // singleton row
    val lastSuccessfulSyncAt: Instant? = null,
    val lastSyncSequence: Long = 0,
)
