package com.berrf35.atlas.core.datastore

import android.content.Context
import android.content.SharedPreferences
import com.berrf35.atlas.core.model.AtlasSettings
import com.berrf35.atlas.core.model.BatteryProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AtlasPreferencesDataSource @Inject constructor() {

    private var sharedPrefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(AtlasSettings())
    val settings: StateFlow<AtlasSettings> = _settings.asStateFlow()

    fun attachContext(context: Context) {
        val prefs = context.getSharedPreferences("atlas_prefs", Context.MODE_PRIVATE)
        sharedPrefs = prefs

        val loaded = AtlasSettings(
            trackingEnabled = prefs.getBoolean("tracking_enabled", false),
            pauseUntilUtcMs = if (prefs.contains("pause_until")) prefs.getLong("pause_until", 0L).takeIf { it > 0 } else null,
            batteryProfile = try {
                BatteryProfile.valueOf(prefs.getString("battery_profile", "BALANCED") ?: "BALANCED")
            } catch (_: Exception) {
                BatteryProfile.BALANCED
            },
            theme = prefs.getString("theme", "SYSTEM") ?: "SYSTEM",
            reduceMotion = prefs.getBoolean("reduce_motion", false),
            appLockEnabled = prefs.getBoolean("app_lock_enabled", false),
            isDemoMode = prefs.getBoolean("is_demo_mode", false),
            isOnboardingCompleted = prefs.getBoolean("is_onboarding_completed", false),
            ambientCacheMb = prefs.getInt("ambient_cache_mb", 256),
            tileServerUrl = prefs.getString("tile_server_url", "https://tiles.openfreemap.org") ?: "https://tiles.openfreemap.org",
            reverseGeocodingEnabled = prefs.getBoolean("reverse_geocoding_enabled", false),
            screenshotSafeHeatmap = prefs.getBoolean("screenshot_safe_heatmap", false)
        )
        _settings.value = loaded
    }

    private fun persist(action: SharedPreferences.Editor.() -> Unit) {
        sharedPrefs?.edit()?.apply {
            action()
            apply()
        }
    }

    fun updateSettings(transform: (AtlasSettings) -> AtlasSettings) {
        _settings.update(transform)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        persist { putBoolean("is_onboarding_completed", completed) }
        _settings.update { it.copy(isOnboardingCompleted = completed) }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        persist { putBoolean("tracking_enabled", enabled) }
        _settings.update { it.copy(trackingEnabled = enabled) }
    }

    fun setPauseUntil(pauseUntilUtcMs: Long?) {
        persist {
            if (pauseUntilUtcMs != null) putLong("pause_until", pauseUntilUtcMs)
            else remove("pause_until")
        }
        _settings.update { it.copy(pauseUntilUtcMs = pauseUntilUtcMs) }
    }

    fun setBatteryProfile(profile: BatteryProfile) {
        persist { putString("battery_profile", profile.name) }
        _settings.update { it.copy(batteryProfile = profile) }
    }

    fun setTheme(theme: String) {
        persist { putString("theme", theme) }
        _settings.update { it.copy(theme = theme) }
    }

    fun setReduceMotion(reduce: Boolean) {
        persist { putBoolean("reduce_motion", reduce) }
        _settings.update { it.copy(reduceMotion = reduce) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        persist { putBoolean("app_lock_enabled", enabled) }
        _settings.update { it.copy(appLockEnabled = enabled) }
    }

    fun setDemoMode(demoMode: Boolean) {
        persist { putBoolean("is_demo_mode", demoMode) }
        _settings.update { it.copy(isDemoMode = demoMode) }
    }

    fun setAmbientCacheMb(mb: Int) {
        persist { putInt("ambient_cache_mb", mb) }
        _settings.update { it.copy(ambientCacheMb = mb) }
    }

    fun setTileServerUrl(url: String) {
        persist { putString("tile_server_url", url) }
        _settings.update { it.copy(tileServerUrl = url) }
    }

    fun setReverseGeocodingEnabled(enabled: Boolean) {
        persist { putBoolean("reverse_geocoding_enabled", enabled) }
        _settings.update { it.copy(reverseGeocodingEnabled = enabled) }
    }

    fun setScreenshotSafeHeatmap(safe: Boolean) {
        persist { putBoolean("screenshot_safe_heatmap", safe) }
        _settings.update { it.copy(screenshotSafeHeatmap = safe) }
    }
}
