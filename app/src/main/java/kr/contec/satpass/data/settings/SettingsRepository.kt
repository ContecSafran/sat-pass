package kr.contec.satpass.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "satpass_settings")

/**
 * 설정 저장소. DataStore(Preferences) 기반.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TLE_URL = stringPreferencesKey("tle_source_url")
        val FALLBACK_TLE_URL = stringPreferencesKey("fallback_tle_source_url")
        val MIN_SYNC_INTERVAL_HOURS = intPreferencesKey("min_sync_interval_hours")
        val PREDICTION_DAYS = intPreferencesKey("prediction_days")
        val MIN_ELEVATION_DEG = doublePreferencesKey("min_elevation_deg")
    }

    val settings: Flow<SatPassSettings> = context.dataStore.data.map { prefs ->
        SatPassSettings(
            tleSourceUrl = prefs[Keys.TLE_URL]?.takeIf { it.isNotBlank() }
                ?: SatPassSettings.DEFAULT_TLE_URL,
            // 폴백은 사용자가 일부러 비워 둘 수 있어야 하므로 저장된 값이 있으면 그대로 쓴다.
            fallbackTleSourceUrl = prefs[Keys.FALLBACK_TLE_URL]
                ?: SatPassSettings.DEFAULT_FALLBACK_TLE_URL,
            minSyncIntervalHours = prefs[Keys.MIN_SYNC_INTERVAL_HOURS]
                ?: SatPassSettings.DEFAULT_MIN_SYNC_INTERVAL_HOURS,
            predictionDays = prefs[Keys.PREDICTION_DAYS]
                ?: SatPassSettings.DEFAULT_PREDICTION_DAYS,
            minElevationDeg = prefs[Keys.MIN_ELEVATION_DEG]
                ?: SatPassSettings.DEFAULT_MIN_ELEVATION_DEG,
        )
    }

    suspend fun current(): SatPassSettings = settings.first()

    suspend fun setTleSourceUrl(url: String) {
        context.dataStore.edit { it[Keys.TLE_URL] = url.trim() }
    }

    suspend fun setFallbackTleSourceUrl(url: String) {
        context.dataStore.edit { it[Keys.FALLBACK_TLE_URL] = url.trim() }
    }

    suspend fun setMinSyncIntervalHours(hours: Int) {
        context.dataStore.edit { it[Keys.MIN_SYNC_INTERVAL_HOURS] = hours.coerceAtLeast(0) }
    }

    suspend fun setPredictionDays(days: Int) {
        context.dataStore.edit { it[Keys.PREDICTION_DAYS] = days.coerceIn(1, 14) }
    }

    suspend fun setMinElevationDeg(deg: Double) {
        context.dataStore.edit { it[Keys.MIN_ELEVATION_DEG] = deg.coerceIn(0.0, 89.0) }
    }
}
