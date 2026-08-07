package com.disciplineos.app.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64

/**
 * `DisciplineOsDatabase.build()`'s kdoc (`:data`) is explicit that passphrase management is an
 * `:app`-module concern: "[passphrase] must come from Android Keystore-backed storage at the
 * call site ... not hardcoded and not stored alongside the DB file." This is that call site.
 *
 * Uses `androidx.security.crypto`'s `EncryptedSharedPreferences` (itself backed by a
 * Keystore-generated `MasterKey`) to store a randomly-generated SQLCipher passphrase — the
 * passphrase bytes never leave the device, are generated once on first run, and are re-read
 * (not re-generated) on every subsequent launch so the same key reopens the same encrypted DB.
 *
 * [HYPOTHESIS] / judgment call, not spec-derived: neither Architecture doc §3.1 nor the Data
 * Model doc specifies *how* the SQLCipher key itself should be protected — they specify that
 * the DB must be encrypted (§3.1) but not the key-management mechanism. `EncryptedSharedPreferences`
 * + Keystore `MasterKey` is the standard Android-recommended pattern for exactly this ("a secret
 * that must survive app restarts but never be extractable without device unlock/Keystore
 * access"), so it's used here as the reasonable default rather than inventing a bespoke Keystore
 * wrapper — flagged in ROADMAP.md §5 as a judgment call worth a second pair of eyes before
 * pilot (Phase 5), same as every other unstated-in-spec default this codebase has logged there.
 */
object DbPassphraseProvider {

    private const val PREFS_FILE_NAME = "disciplineos_secure_prefs"
    private const val PASSPHRASE_KEY = "sqlcipher_passphrase_b64"
    private const val PASSPHRASE_BYTE_LENGTH = 32 // 256-bit key

    /**
     * Returns the device's SQLCipher passphrase, generating and persisting a new random one on
     * first call. Safe to call every app start — subsequent calls return the same bytes.
     */
    fun getOrCreate(context: Context): ByteArray {
        val prefs = encryptedPrefs(context)
        val existing = prefs.getString(PASSPHRASE_KEY, null)
        if (existing != null) {
            return Base64.getDecoder().decode(existing)
        }
        val generated = ByteArray(PASSPHRASE_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(PASSPHRASE_KEY, Base64.getEncoder().encodeToString(generated))
            .apply()
        return generated
    }

    private fun encryptedPrefs(context: Context) = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
