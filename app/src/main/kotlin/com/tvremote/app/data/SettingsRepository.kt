package com.tvremote.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tvremote_settings")

/** Small, non-secret user preferences. Pairing credentials live elsewhere. */
class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Whether the phone's hardware volume keys adjust the Apple TV while the
     * remote screen is open. On by default, because on that screen the phone
     * is being used as a remote.
     */
    val volumeKeysControlTv: Flow<Boolean> = appContext.settingsDataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it[KEY_VOLUME_KEYS] ?: true }

    suspend fun setVolumeKeysControlTv(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_VOLUME_KEYS] = enabled }
    }

    private companion object {
        val KEY_VOLUME_KEYS = booleanPreferencesKey("volume_keys_control_tv")
    }
}