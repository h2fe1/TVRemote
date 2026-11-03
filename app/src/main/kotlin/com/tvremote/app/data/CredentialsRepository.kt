package com.tvremote.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

private val Context.pairingDataStore by preferencesDataStore(name = "tvremote_pairings")

/**
 * Stores pyatv-format Companion credentials per stable Apple TV identity.
 * Values are encrypted with [KeystoreCrypto] before they reach DataStore.
 */
class CredentialsRepository(context: Context) {
    private val appContext = context.applicationContext

    private fun keyFor(deviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(deviceId.toByteArray(Charsets.UTF_8))
        return "cred_" + digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun load(deviceId: String): String? {
        val preferences = appContext.pairingDataStore.data.first()
        val wrapped = preferences[stringPreferencesKey(keyFor(deviceId))] ?: return null
        return KeystoreCrypto.decrypt(wrapped)
    }

    suspend fun save(deviceId: String, credentials: String) {
        val wrapped = KeystoreCrypto.encrypt(credentials)
        appContext.pairingDataStore.edit { it[stringPreferencesKey(keyFor(deviceId))] = wrapped }
    }

    suspend fun delete(deviceId: String) {
        appContext.pairingDataStore.edit { it.remove(stringPreferencesKey(keyFor(deviceId))) }
    }

    /** Stored hashes, used only to know whether a device is paired. */
    suspend fun pairedKeys(): Set<String> {
        val preferences = appContext.pairingDataStore.data.first()
        return preferences.asMap().keys
            .map { it.name }
            .filter { it.startsWith("cred_") }
            .toSet()
    }

    fun isPairedKey(key: String): Boolean = key.startsWith("cred_")

    /** Stable identifier used for a discovered device. */
    fun deviceKey(deviceId: String): String = keyFor(deviceId)
}
