package com.disciplineos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Device pairing credentials for Console communication.
 * Design doc §1.1 — "device_credentials". Token stored in EncryptedSharedPreferences
 * via DbPassphraseProvider pattern; this entity persists the device identity and
 * pairing metadata for the sync client to reference.
 *
 * TODO(split): populated by pairing flow (Design doc §3.1). No enforcement-path reads.
 */
@Entity(tableName = "device_credentials")
data class DeviceCredentials(
    @PrimaryKey val deviceId: UUID,
    val pairedAt: Instant,
    val accountUserId: UUID,
)
