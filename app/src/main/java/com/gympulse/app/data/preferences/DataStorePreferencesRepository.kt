package com.gympulse.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gympulse.app.domain.model.ThemeMode
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.data.sync.SyncEntity
import com.gympulse.app.data.sync.SyncOutbox
import com.gympulse.app.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gympulse_preferences")

@Singleton
class DataStorePreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val outbox: SyncOutbox,
) : PreferencesRepository {

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val userName = stringPreferencesKey("user_name")
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val unit = stringPreferencesKey("weight_unit")
        val sound = booleanPreferencesKey("sound_enabled")
        val vibration = booleanPreferencesKey("vibration_enabled")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val defaultRest = intPreferencesKey("default_rest_seconds")
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val autoDetect = booleanPreferencesKey("auto_detect_location")
        val manualLocation = longPreferencesKey("manual_location_id")
    }

    override val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val defaults = UserPreferences()
            UserPreferences(
                onboardingCompleted = prefs[Keys.onboarding] ?: defaults.onboardingCompleted,
                userName = prefs[Keys.userName] ?: defaults.userName,
                themeMode = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.themeMode,
                dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
                weightUnit = prefs[Keys.unit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: defaults.weightUnit,
                soundEnabled = prefs[Keys.sound] ?: defaults.soundEnabled,
                vibrationEnabled = prefs[Keys.vibration] ?: defaults.vibrationEnabled,
                keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
                defaultRestSeconds = prefs[Keys.defaultRest] ?: defaults.defaultRestSeconds,
                weeklyGoal = prefs[Keys.weeklyGoal] ?: defaults.weeklyGoal,
                autoDetectLocation = prefs[Keys.autoDetect] ?: defaults.autoDetectLocation,
                manualLocationId = prefs[Keys.manualLocation],
            )
        }
        .distinctUntilChanged()

    override suspend fun setOnboardingCompleted(completed: Boolean) = set(Keys.onboarding, completed)
    override suspend fun setUserName(name: String) = set(Keys.userName, name.trim())
    override suspend fun setThemeMode(mode: ThemeMode) = set(Keys.theme, mode.name)
    override suspend fun setDynamicColor(enabled: Boolean) = set(Keys.dynamicColor, enabled)
    override suspend fun setWeightUnit(unit: WeightUnit) = set(Keys.unit, unit.name)
    override suspend fun setSoundEnabled(enabled: Boolean) = set(Keys.sound, enabled)
    override suspend fun setVibrationEnabled(enabled: Boolean) = set(Keys.vibration, enabled)
    override suspend fun setKeepScreenOn(enabled: Boolean) = set(Keys.keepScreenOn, enabled)
    override suspend fun setDefaultRestSeconds(seconds: Int) = set(Keys.defaultRest, seconds.coerceIn(0, 900))
    override suspend fun setWeeklyGoal(goal: Int) = set(Keys.weeklyGoal, goal.coerceIn(1, 14))
    override suspend fun setAutoDetectLocation(enabled: Boolean) = set(Keys.autoDetect, enabled)

    override suspend fun setManualLocationId(id: Long?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.manualLocation) else prefs[Keys.manualLocation] = id
        }
        queueUpload()
    }

    override suspend fun applyFromServer(preferences: UserPreferences) {
        context.dataStore.edit { prefs ->
            prefs[Keys.onboarding] = preferences.onboardingCompleted
            prefs[Keys.userName] = preferences.userName
            prefs[Keys.theme] = preferences.themeMode.name
            prefs[Keys.dynamicColor] = preferences.dynamicColor
            prefs[Keys.unit] = preferences.weightUnit.name
            prefs[Keys.sound] = preferences.soundEnabled
            prefs[Keys.vibration] = preferences.vibrationEnabled
            prefs[Keys.keepScreenOn] = preferences.keepScreenOn
            prefs[Keys.defaultRest] = preferences.defaultRestSeconds
            prefs[Keys.weeklyGoal] = preferences.weeklyGoal
            prefs[Keys.autoDetect] = preferences.autoDetectLocation
            val manual = preferences.manualLocationId
            if (manual == null) prefs.remove(Keys.manualLocation) else prefs[Keys.manualLocation] = manual
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
        queueUpload()
    }

    private suspend fun queueUpload() {
        outbox.upsert(SyncEntity.PREFERENCES, 0)
        outbox.requestSync()
    }
}
