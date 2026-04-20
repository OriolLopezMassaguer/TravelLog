package com.travellog.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            gpsIntervalSeconds = prefs[GPS_INTERVAL_KEY]  ?: 10,
            adaptiveGps        = prefs[ADAPTIVE_GPS_KEY]  ?: true,
            trackingStartHour  = prefs[START_HOUR_KEY]    ?: 8,
            openAiApiKey       = prefs[OPENAI_KEY]        ?: "",
        )
    }

    suspend fun setGpsInterval(seconds: Int)       { dataStore.edit { it[GPS_INTERVAL_KEY] = seconds } }
    suspend fun setAdaptiveGps(enabled: Boolean)   { dataStore.edit { it[ADAPTIVE_GPS_KEY] = enabled } }
    suspend fun setTrackingStartHour(hour: Int)    { dataStore.edit { it[START_HOUR_KEY]   = hour    } }
    suspend fun setOpenAiApiKey(key: String)       { dataStore.edit { it[OPENAI_KEY]       = key     } }

    suspend fun getOpenAiApiKey(): String = dataStore.data.first()[OPENAI_KEY] ?: ""

    companion object {
        private val GPS_INTERVAL_KEY = intPreferencesKey("gps_interval_seconds")
        private val ADAPTIVE_GPS_KEY = booleanPreferencesKey("adaptive_gps")
        private val START_HOUR_KEY   = intPreferencesKey("tracking_start_hour")
        private val OPENAI_KEY       = stringPreferencesKey("openai_api_key")
    }
}
