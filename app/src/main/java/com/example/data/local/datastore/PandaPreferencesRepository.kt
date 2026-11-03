package com.example.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pandaDataStore: DataStore<Preferences> by preferencesDataStore(name = "pandanex_preferences")

data class PandaSettings(
    // General & Aqua Dynamics
    val themeMode: AppThemeMode = AppThemeMode.DARK_AQUA,
    val animationsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val hideAquaDynamicsInFullScreen: Boolean = true,

    // Aqua Dynamics Capsule, Event Categories & Interaction Controls
    val aquaDynamicsEnabled: Boolean = true,
    val aquaCatPhoneEnabled: Boolean = true,
    val aquaPostCallSummaryEnabled: Boolean = true,
    val aquaCatPerformanceEnabled: Boolean = true,
    val aquaCatNetworkEnabled: Boolean = true,
    val aquaCatInteractionsEnabled: Boolean = true,
    val aquaCatSystemAlertsEnabled: Boolean = true,
    val aquaPriorityFilter: String = "ALL", // ALL, IMPORTANT_ONLY, CRITICAL_ONLY
    val aquaAutoMinimizeEnabled: Boolean = true,
    val aquaAutoMinimizeDelayMs: Long = 5500L,
    val aquaAnimationStyle: String = "FLUID_SPRING", // FLUID_SPRING, CRISP, REDUCED
    val aquaEventCooldownMs: Long = 10000L,
    val aquaStrictDismissLock: Boolean = true,
    val aquaPersistedDismissedCsv: String = "",

    // Monitoring Intervals
    val cpuRefreshMs: Long = 1500L,
    val ramRefreshMs: Long = 2000L,
    val batteryMonitoringEnabled: Boolean = true,
    val thermalMonitoringEnabled: Boolean = true,

    // Alert Thresholds & Categories
    val cpuAlertEnabled: Boolean = true,
    val cpuAlertThresholdPercent: Float = 85f,
    val batteryAlertEnabled: Boolean = true,
    val lowBatteryThresholdPercent: Int = 20,
    val highBatteryTempCelsius: Float = 41.0f,
    val chargingAlertsEnabled: Boolean = true,
    val thermalAlertEnabled: Boolean = true,
    val ramAlertEnabled: Boolean = true,
    val lowRamThresholdPercent: Float = 90f,
    val storageAlertEnabled: Boolean = true,
    val lowStorageThresholdPercent: Float = 90f,
    val networkAlertEnabled: Boolean = true,
    val smartActionAlertEnabled: Boolean = true,
    val systemAlertEnabled: Boolean = true,
    val alertSoundEnabled: Boolean = true,
    val alertVibrationEnabled: Boolean = true,
    val alertCooldownSeconds: Int = 180,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 7,

    // Smart Actions & Gestures (Flip is exclusively Flip to DND)
    val backgroundMonitoringEnabled: Boolean = false,
    val flipToDndEnabled: Boolean = false,
    val flipRestorePrevious: Boolean = true,
    val flipDelayMs: Long = 1500L,
    val shakeFlashlightEnabled: Boolean = false,
    val shakeSensitivity: Float = 13.5f, // m/s^2 threshold above gravity
    val gestureCooldownMs: Long = 2500L,
    val doubleShakeAction: String = "NONE", // NONE, FLASHLIGHT, DND_TOGGLE, VIBRATE_MODE
    val pickupDetectionEnabled: Boolean = false,
    val proximityPocketGuardEnabled: Boolean = false,

    // Shake to Camera (Moto-style Quick Capture)
    val shakeToCameraEnabled: Boolean = false,
    val shakeToCameraSensitivity: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val shakeToCameraVibrationEnabled: Boolean = true,
    val shakeToCameraCooldownMs: Long = 3000L,

    // Edge Glow Lighting (Incoming Calls & Notifications)
    val edgeGlowEnabled: Boolean = false,
    val edgeGlowCallsEnabled: Boolean = true,
    val edgeGlowNotificationsEnabled: Boolean = true,
    val edgeGlowStyle: String = "AQUA_PULSE", // AQUA_PULSE, CYBER_SWEEP, DUAL_WAVE, HORIZON_BEAM
    val edgeGlowCallColorHex: Long = 0xFF00E5FFL,
    val edgeGlowNotifColorHex: Long = 0xFF1DE9B6L,
    val edgeGlowStrokeDp: Float = 8f,
    val edgeGlowDurationMs: Long = 3500L,

    // Auto-Dimmed Apps / Dimmed App Feature (10%–90% intensity, individual app rules & temporary pause)
    val autoDimAppsEnabled: Boolean = false,
    val autoDimTargetBrightnessPercent: Int = 20,
    val autoDimIntensityPercent: Int = 55, // 10%..90% configurable dimming overlay intensity
    val autoDimUseExtraPrivacyShade: Boolean = true,
    val autoDimPackagesCsv: String = "",
    val autoDimDisabledRulesCsv: String = "",
    val autoDimTempPausedUntilMs: Long = 0L,

    // Smart Motion Gestures, Call Controls & Display Intelligence
    val raiseToWakeEnabled: Boolean = false,
    val raiseToWakeSensitivity: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val raiseToWakeVibrationEnabled: Boolean = true,
    val raiseToWakeCooldownMs: Long = 3500L,

    val pickUpToSilenceEnabled: Boolean = false,
    val pickUpToSilenceSensitivity: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val pickUpToSilenceVibrationEnabled: Boolean = true,

    val proximityCallAnswerEnabled: Boolean = false,
    val proximityAnswerDelayMs: Long = 800L,
    val proximityAnswerVibrationEnabled: Boolean = true,

    val putDownToSleepEnabled: Boolean = false,
    val putDownToSleepSensitivity: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val putDownToSleepDelayMs: Long = 1500L,
    val putDownToSleepCooldownMs: Long = 5000L,
    val putDownToSleepVibrationEnabled: Boolean = true,
    val putDownSurfaceMode: String = "BOTH", // BOTH, FACE_DOWN, FACE_UP

    // Smart Screen Saver – Motion Activated & DreamService
    val screenSaverEnabled: Boolean = false,
    val screenSaverStyle: String = "AQUA_DIGITAL", // AQUA_DIGITAL, MINIMAL_ANALOG, NEON_CLOCK, MINIMAL_AOD, AQUA_WAVES, DIGITAL_INFO, CUSTOM_MINIMAL
    val screenSaverUse24Hour: Boolean = false,
    val screenSaverShowDate: Boolean = true,
    val screenSaverShowBattery: Boolean = true,
    val screenSaverShowNotifications: Boolean = true,
    val screenSaverBrightnessPercent: Int = 28, // 5%..100%
    val screenSaverTimeoutSec: Int = 12, // 5s..60s
    val screenSaverStationaryOffSec: Int = 4, // 2s..15s
    val screenSaverMotionSensitivity: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val screenSaverCooldownMs: Long = 4500L,
    val screenSaverAnimationsEnabled: Boolean = true,
    val screenSaverVibrationEnabled: Boolean = false,
    val screenSaverCustomLayout: String = "STACKED", // STACKED, INLINE_CLASSIC, LEFT_EDITORIAL
    val screenSaverCustomTextScale: String = "BALANCED", // COMPACT, BALANCED, BOLD_LARGE
    val screenSaverCustomBgTheme: String = "PURE_OLED_BLACK", // PURE_OLED_BLACK, DEEP_ABYSS_TEAL, MIDNIGHT_AURORA

    // Quick Action Center Customizable Order (comma-separated IDs)
    val quickActionsOrder: String = "AQUA_DYNAMICS,FLASHLIGHT,DND,EDGE_GLOW,AUTO_DIM,WIFI,BLUETOOTH,HOTSPOT,AIRPLANE,BATTERY,HEALTH,NETWORK_DIAG,QR_SCANNER,SENSOR_LAB,DEV_TOOLS,SMART_ACTIONS"
) {
    val autoDimPackagesSet: Set<String>
        get() = autoDimPackagesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    val autoDimDisabledRulesSet: Set<String>
        get() = autoDimDisabledRulesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    val activeAutoDimPackagesSet: Set<String>
        get() = autoDimPackagesSet - autoDimDisabledRulesSet

    val persistedDismissedSignatures: Set<String>
        get() = aquaPersistedDismissedCsv.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}

class PandaPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HIDE_AQUA_IN_FULLSCREEN = booleanPreferencesKey("hide_aqua_in_fullscreen")

        val AQUA_DYNAMICS_ENABLED = booleanPreferencesKey("aqua_dynamics_enabled")
        val AQUA_CAT_PHONE_ENABLED = booleanPreferencesKey("aqua_cat_phone_enabled")
        val AQUA_POST_CALL_SUMMARY = booleanPreferencesKey("aqua_post_call_summary")
        val AQUA_CAT_PERFORMANCE_ENABLED = booleanPreferencesKey("aqua_cat_performance_enabled")
        val AQUA_CAT_NETWORK_ENABLED = booleanPreferencesKey("aqua_cat_network_enabled")
        val AQUA_CAT_INTERACTIONS_ENABLED = booleanPreferencesKey("aqua_cat_interactions_enabled")
        val AQUA_CAT_SYSTEM_ALERTS_ENABLED = booleanPreferencesKey("aqua_cat_system_alerts_enabled")
        val AQUA_PRIORITY_FILTER = stringPreferencesKey("aqua_priority_filter")
        val AQUA_AUTO_MINIMIZE_ENABLED = booleanPreferencesKey("aqua_auto_minimize_enabled")
        val AQUA_AUTO_MINIMIZE_DELAY_MS = longPreferencesKey("aqua_auto_minimize_delay_ms")
        val AQUA_ANIMATION_STYLE = stringPreferencesKey("aqua_animation_style")
        val AQUA_EVENT_COOLDOWN_MS = longPreferencesKey("aqua_event_cooldown_ms")
        val AQUA_STRICT_DISMISS_LOCK = booleanPreferencesKey("aqua_strict_dismiss_lock")
        val AQUA_DISMISSED_SIGNATURES = stringPreferencesKey("aqua_dismissed_signatures")

        val CPU_REFRESH_MS = longPreferencesKey("cpu_refresh_ms")
        val RAM_REFRESH_MS = longPreferencesKey("ram_refresh_ms")
        val BATTERY_MONITOR_ENABLED = booleanPreferencesKey("battery_monitor_enabled")
        val THERMAL_MONITOR_ENABLED = booleanPreferencesKey("thermal_monitor_enabled")

        val CPU_ALERT_ENABLED = booleanPreferencesKey("cpu_alert_enabled")
        val CPU_ALERT_THRESHOLD = floatPreferencesKey("cpu_alert_threshold")
        val BATTERY_ALERT_ENABLED = booleanPreferencesKey("battery_alert_enabled")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val HIGH_BATTERY_TEMP = floatPreferencesKey("high_battery_temp")
        val CHARGING_ALERTS_ENABLED = booleanPreferencesKey("charging_alerts_enabled")
        val THERMAL_ALERT_ENABLED = booleanPreferencesKey("thermal_alert_enabled")
        val RAM_ALERT_ENABLED = booleanPreferencesKey("ram_alert_enabled")
        val LOW_RAM_THRESHOLD = floatPreferencesKey("low_ram_threshold")
        val STORAGE_ALERT_ENABLED = booleanPreferencesKey("storage_alert_enabled")
        val LOW_STORAGE_THRESHOLD = floatPreferencesKey("low_storage_threshold")
        val NETWORK_ALERT_ENABLED = booleanPreferencesKey("network_alert_enabled")
        val SMART_ACTION_ALERT_ENABLED = booleanPreferencesKey("smart_action_alert_enabled")
        val SYSTEM_ALERT_ENABLED = booleanPreferencesKey("system_alert_enabled")
        val ALERT_SOUND_ENABLED = booleanPreferencesKey("alert_sound_enabled")
        val ALERT_VIBRATION_ENABLED = booleanPreferencesKey("alert_vibration_enabled")
        val ALERT_COOLDOWN_SEC = intPreferencesKey("alert_cooldown_sec")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")

        val BG_MONITOR_ENABLED = booleanPreferencesKey("bg_monitor_enabled")
        val FLIP_DND_ENABLED = booleanPreferencesKey("flip_dnd_enabled")
        val FLIP_RESTORE_PREV = booleanPreferencesKey("flip_restore_prev")
        val FLIP_DELAY_MS = longPreferencesKey("flip_delay_ms")
        val SHAKE_FLASH_ENABLED = booleanPreferencesKey("shake_flash_enabled")
        val SHAKE_SENSITIVITY = floatPreferencesKey("shake_sensitivity")
        val GESTURE_COOLDOWN_MS = longPreferencesKey("gesture_cooldown_ms")
        val DOUBLE_SHAKE_ACTION = stringPreferencesKey("double_shake_action")
        val PICKUP_DETECTION_ENABLED = booleanPreferencesKey("pickup_detection_enabled")
        val PROXIMITY_POCKET_ENABLED = booleanPreferencesKey("proximity_pocket_enabled")

        val SHAKE_CAMERA_ENABLED = booleanPreferencesKey("shake_camera_enabled")
        val SHAKE_CAMERA_SENSITIVITY = stringPreferencesKey("shake_camera_sensitivity")
        val SHAKE_CAMERA_VIBRATION = booleanPreferencesKey("shake_camera_vibration")
        val SHAKE_CAMERA_COOLDOWN_MS = longPreferencesKey("shake_camera_cooldown_ms")

        val EDGE_GLOW_ENABLED = booleanPreferencesKey("edge_glow_enabled")
        val EDGE_GLOW_CALLS_ENABLED = booleanPreferencesKey("edge_glow_calls_enabled")
        val EDGE_GLOW_NOTIFS_ENABLED = booleanPreferencesKey("edge_glow_notifs_enabled")
        val EDGE_GLOW_STYLE = stringPreferencesKey("edge_glow_style")
        val EDGE_GLOW_CALL_COLOR = longPreferencesKey("edge_glow_call_color")
        val EDGE_GLOW_NOTIF_COLOR = longPreferencesKey("edge_glow_notif_color")
        val EDGE_GLOW_STROKE_DP = floatPreferencesKey("edge_glow_stroke_dp")
        val EDGE_GLOW_DURATION_MS = longPreferencesKey("edge_glow_duration_ms")

        val AUTO_DIM_ENABLED = booleanPreferencesKey("auto_dim_enabled")
        val AUTO_DIM_BRIGHTNESS_PCT = intPreferencesKey("auto_dim_brightness_pct")
        val AUTO_DIM_INTENSITY_PCT = intPreferencesKey("auto_dim_intensity_pct")
        val AUTO_DIM_EXTRA_SHADE = booleanPreferencesKey("auto_dim_extra_shade")
        val AUTO_DIM_PACKAGES_CSV = stringPreferencesKey("auto_dim_packages_csv")
        val AUTO_DIM_DISABLED_RULES_CSV = stringPreferencesKey("auto_dim_disabled_rules_csv")
        val AUTO_DIM_TEMP_PAUSED_UNTIL = longPreferencesKey("auto_dim_temp_paused_until")

        val RAISE_TO_WAKE_ENABLED = booleanPreferencesKey("raise_to_wake_enabled")
        val RAISE_TO_WAKE_SENSITIVITY = stringPreferencesKey("raise_to_wake_sensitivity")
        val RAISE_TO_WAKE_VIBRATION = booleanPreferencesKey("raise_to_wake_vibration")
        val RAISE_TO_WAKE_COOLDOWN_MS = longPreferencesKey("raise_to_wake_cooldown_ms")

        val PICKUP_SILENCE_ENABLED = booleanPreferencesKey("pickup_silence_enabled")
        val PICKUP_SILENCE_SENSITIVITY = stringPreferencesKey("pickup_silence_sensitivity")
        val PICKUP_SILENCE_VIBRATION = booleanPreferencesKey("pickup_silence_vibration")

        val PROXIMITY_ANSWER_ENABLED = booleanPreferencesKey("proximity_answer_enabled")
        val PROXIMITY_ANSWER_DELAY_MS = longPreferencesKey("proximity_answer_delay_ms")
        val PROXIMITY_ANSWER_VIBRATION = booleanPreferencesKey("proximity_answer_vibration")

        val PUT_DOWN_SLEEP_ENABLED = booleanPreferencesKey("put_down_sleep_enabled")
        val PUT_DOWN_SLEEP_SENSITIVITY = stringPreferencesKey("put_down_sleep_sensitivity")
        val PUT_DOWN_SLEEP_DELAY_MS = longPreferencesKey("put_down_sleep_delay_ms")
        val PUT_DOWN_SLEEP_COOLDOWN_MS = longPreferencesKey("put_down_sleep_cooldown_ms")
        val PUT_DOWN_SLEEP_VIBRATION = booleanPreferencesKey("put_down_sleep_vibration")
        val PUT_DOWN_SURFACE_MODE = stringPreferencesKey("put_down_surface_mode")

        val SCREEN_SAVER_ENABLED = booleanPreferencesKey("screen_saver_enabled")
        val SCREEN_SAVER_STYLE = stringPreferencesKey("screen_saver_style")
        val SCREEN_SAVER_USE_24H = booleanPreferencesKey("screen_saver_use_24h")
        val SCREEN_SAVER_SHOW_DATE = booleanPreferencesKey("screen_saver_show_date")
        val SCREEN_SAVER_SHOW_BATTERY = booleanPreferencesKey("screen_saver_show_battery")
        val SCREEN_SAVER_SHOW_NOTIFS = booleanPreferencesKey("screen_saver_show_notifs")
        val SCREEN_SAVER_BRIGHTNESS_PCT = intPreferencesKey("screen_saver_brightness_pct")
        val SCREEN_SAVER_TIMEOUT_SEC = intPreferencesKey("screen_saver_timeout_sec")
        val SCREEN_SAVER_STATIONARY_OFF_SEC = intPreferencesKey("screen_saver_stationary_off_sec")
        val SCREEN_SAVER_SENSITIVITY = stringPreferencesKey("screen_saver_sensitivity")
        val SCREEN_SAVER_COOLDOWN_MS = longPreferencesKey("screen_saver_cooldown_ms")
        val SCREEN_SAVER_ANIMATIONS = booleanPreferencesKey("screen_saver_animations")
        val SCREEN_SAVER_VIBRATION = booleanPreferencesKey("screen_saver_vibration")
        val SCREEN_SAVER_CUSTOM_LAYOUT = stringPreferencesKey("screen_saver_custom_layout")
        val SCREEN_SAVER_CUSTOM_SCALE = stringPreferencesKey("screen_saver_custom_scale")
        val SCREEN_SAVER_CUSTOM_BG = stringPreferencesKey("screen_saver_custom_bg")

        val QUICK_ACTIONS_ORDER = stringPreferencesKey("quick_actions_order")
    }

    val settingsFlow: Flow<PandaSettings> = context.pandaDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK_AQUA.name
        val themeMode = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.DARK_AQUA)

        PandaSettings(
            themeMode = themeMode,
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            hapticFeedbackEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            hideAquaDynamicsInFullScreen = prefs[Keys.HIDE_AQUA_IN_FULLSCREEN] ?: true,
            aquaDynamicsEnabled = prefs[Keys.AQUA_DYNAMICS_ENABLED] ?: true,
            aquaCatPhoneEnabled = prefs[Keys.AQUA_CAT_PHONE_ENABLED] ?: true,
            aquaPostCallSummaryEnabled = prefs[Keys.AQUA_POST_CALL_SUMMARY] ?: true,
            aquaCatPerformanceEnabled = prefs[Keys.AQUA_CAT_PERFORMANCE_ENABLED] ?: true,
            aquaCatNetworkEnabled = prefs[Keys.AQUA_CAT_NETWORK_ENABLED] ?: true,
            aquaCatInteractionsEnabled = prefs[Keys.AQUA_CAT_INTERACTIONS_ENABLED] ?: true,
            aquaCatSystemAlertsEnabled = prefs[Keys.AQUA_CAT_SYSTEM_ALERTS_ENABLED] ?: true,
            aquaPriorityFilter = prefs[Keys.AQUA_PRIORITY_FILTER] ?: "ALL",
            aquaAutoMinimizeEnabled = prefs[Keys.AQUA_AUTO_MINIMIZE_ENABLED] ?: true,
            aquaAutoMinimizeDelayMs = prefs[Keys.AQUA_AUTO_MINIMIZE_DELAY_MS] ?: 5500L,
            aquaAnimationStyle = prefs[Keys.AQUA_ANIMATION_STYLE] ?: "FLUID_SPRING",
            aquaEventCooldownMs = prefs[Keys.AQUA_EVENT_COOLDOWN_MS] ?: 10000L,
            aquaStrictDismissLock = prefs[Keys.AQUA_STRICT_DISMISS_LOCK] ?: true,
            aquaPersistedDismissedCsv = prefs[Keys.AQUA_DISMISSED_SIGNATURES] ?: "",
            cpuRefreshMs = prefs[Keys.CPU_REFRESH_MS] ?: 1500L,
            ramRefreshMs = prefs[Keys.RAM_REFRESH_MS] ?: 2000L,
            batteryMonitoringEnabled = prefs[Keys.BATTERY_MONITOR_ENABLED] ?: true,
            thermalMonitoringEnabled = prefs[Keys.THERMAL_MONITOR_ENABLED] ?: true,
            cpuAlertEnabled = prefs[Keys.CPU_ALERT_ENABLED] ?: true,
            cpuAlertThresholdPercent = prefs[Keys.CPU_ALERT_THRESHOLD] ?: 85f,
            batteryAlertEnabled = prefs[Keys.BATTERY_ALERT_ENABLED] ?: true,
            lowBatteryThresholdPercent = prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 20,
            highBatteryTempCelsius = prefs[Keys.HIGH_BATTERY_TEMP] ?: 41.0f,
            chargingAlertsEnabled = prefs[Keys.CHARGING_ALERTS_ENABLED] ?: true,
            thermalAlertEnabled = prefs[Keys.THERMAL_ALERT_ENABLED] ?: true,
            ramAlertEnabled = prefs[Keys.RAM_ALERT_ENABLED] ?: true,
            lowRamThresholdPercent = prefs[Keys.LOW_RAM_THRESHOLD] ?: 90f,
            storageAlertEnabled = prefs[Keys.STORAGE_ALERT_ENABLED] ?: true,
            lowStorageThresholdPercent = prefs[Keys.LOW_STORAGE_THRESHOLD] ?: 90f,
            networkAlertEnabled = prefs[Keys.NETWORK_ALERT_ENABLED] ?: true,
            smartActionAlertEnabled = prefs[Keys.SMART_ACTION_ALERT_ENABLED] ?: true,
            systemAlertEnabled = prefs[Keys.SYSTEM_ALERT_ENABLED] ?: true,
            alertSoundEnabled = prefs[Keys.ALERT_SOUND_ENABLED] ?: true,
            alertVibrationEnabled = prefs[Keys.ALERT_VIBRATION_ENABLED] ?: true,
            alertCooldownSeconds = prefs[Keys.ALERT_COOLDOWN_SEC] ?: 180,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            quietStartHour = prefs[Keys.QUIET_START_HOUR] ?: 23,
            quietEndHour = prefs[Keys.QUIET_END_HOUR] ?: 7,
            backgroundMonitoringEnabled = prefs[Keys.BG_MONITOR_ENABLED] ?: false,
            flipToDndEnabled = prefs[Keys.FLIP_DND_ENABLED] ?: false,
            flipRestorePrevious = prefs[Keys.FLIP_RESTORE_PREV] ?: true,
            flipDelayMs = prefs[Keys.FLIP_DELAY_MS] ?: 1500L,
            shakeFlashlightEnabled = prefs[Keys.SHAKE_FLASH_ENABLED] ?: false,
            shakeSensitivity = prefs[Keys.SHAKE_SENSITIVITY] ?: 13.5f,
            gestureCooldownMs = prefs[Keys.GESTURE_COOLDOWN_MS] ?: 2500L,
            doubleShakeAction = prefs[Keys.DOUBLE_SHAKE_ACTION] ?: "NONE",
            pickupDetectionEnabled = prefs[Keys.PICKUP_DETECTION_ENABLED] ?: false,
            proximityPocketGuardEnabled = prefs[Keys.PROXIMITY_POCKET_ENABLED] ?: false,
            shakeToCameraEnabled = prefs[Keys.SHAKE_CAMERA_ENABLED] ?: false,
            shakeToCameraSensitivity = prefs[Keys.SHAKE_CAMERA_SENSITIVITY] ?: "MEDIUM",
            shakeToCameraVibrationEnabled = prefs[Keys.SHAKE_CAMERA_VIBRATION] ?: true,
            shakeToCameraCooldownMs = prefs[Keys.SHAKE_CAMERA_COOLDOWN_MS] ?: 3000L,
            edgeGlowEnabled = prefs[Keys.EDGE_GLOW_ENABLED] ?: false,
            edgeGlowCallsEnabled = prefs[Keys.EDGE_GLOW_CALLS_ENABLED] ?: true,
            edgeGlowNotificationsEnabled = prefs[Keys.EDGE_GLOW_NOTIFS_ENABLED] ?: true,
            edgeGlowStyle = prefs[Keys.EDGE_GLOW_STYLE] ?: "AQUA_PULSE",
            edgeGlowCallColorHex = prefs[Keys.EDGE_GLOW_CALL_COLOR] ?: 0xFF00E5FFL,
            edgeGlowNotifColorHex = prefs[Keys.EDGE_GLOW_NOTIF_COLOR] ?: 0xFF1DE9B6L,
            edgeGlowStrokeDp = prefs[Keys.EDGE_GLOW_STROKE_DP] ?: 8f,
            edgeGlowDurationMs = prefs[Keys.EDGE_GLOW_DURATION_MS] ?: 3500L,
            autoDimAppsEnabled = prefs[Keys.AUTO_DIM_ENABLED] ?: false,
            autoDimTargetBrightnessPercent = prefs[Keys.AUTO_DIM_BRIGHTNESS_PCT] ?: 20,
            autoDimIntensityPercent = (prefs[Keys.AUTO_DIM_INTENSITY_PCT] ?: 55).coerceIn(10, 90),
            autoDimUseExtraPrivacyShade = prefs[Keys.AUTO_DIM_EXTRA_SHADE] ?: true,
            autoDimPackagesCsv = prefs[Keys.AUTO_DIM_PACKAGES_CSV] ?: "",
            autoDimDisabledRulesCsv = prefs[Keys.AUTO_DIM_DISABLED_RULES_CSV] ?: "",
            autoDimTempPausedUntilMs = prefs[Keys.AUTO_DIM_TEMP_PAUSED_UNTIL] ?: 0L,
            raiseToWakeEnabled = prefs[Keys.RAISE_TO_WAKE_ENABLED] ?: false,
            raiseToWakeSensitivity = prefs[Keys.RAISE_TO_WAKE_SENSITIVITY] ?: "MEDIUM",
            raiseToWakeVibrationEnabled = prefs[Keys.RAISE_TO_WAKE_VIBRATION] ?: true,
            raiseToWakeCooldownMs = prefs[Keys.RAISE_TO_WAKE_COOLDOWN_MS] ?: 3500L,
            pickUpToSilenceEnabled = prefs[Keys.PICKUP_SILENCE_ENABLED] ?: false,
            pickUpToSilenceSensitivity = prefs[Keys.PICKUP_SILENCE_SENSITIVITY] ?: "MEDIUM",
            pickUpToSilenceVibrationEnabled = prefs[Keys.PICKUP_SILENCE_VIBRATION] ?: true,
            proximityCallAnswerEnabled = prefs[Keys.PROXIMITY_ANSWER_ENABLED] ?: false,
            proximityAnswerDelayMs = prefs[Keys.PROXIMITY_ANSWER_DELAY_MS] ?: 800L,
            proximityAnswerVibrationEnabled = prefs[Keys.PROXIMITY_ANSWER_VIBRATION] ?: true,
            putDownToSleepEnabled = prefs[Keys.PUT_DOWN_SLEEP_ENABLED] ?: false,
            putDownToSleepSensitivity = prefs[Keys.PUT_DOWN_SLEEP_SENSITIVITY] ?: "MEDIUM",
            putDownToSleepDelayMs = prefs[Keys.PUT_DOWN_SLEEP_DELAY_MS] ?: 1500L,
            putDownToSleepCooldownMs = prefs[Keys.PUT_DOWN_SLEEP_COOLDOWN_MS] ?: 5000L,
            putDownToSleepVibrationEnabled = prefs[Keys.PUT_DOWN_SLEEP_VIBRATION] ?: true,
            putDownSurfaceMode = prefs[Keys.PUT_DOWN_SURFACE_MODE] ?: "BOTH",
            screenSaverEnabled = prefs[Keys.SCREEN_SAVER_ENABLED] ?: false,
            screenSaverStyle = prefs[Keys.SCREEN_SAVER_STYLE] ?: "AQUA_DIGITAL",
            screenSaverUse24Hour = prefs[Keys.SCREEN_SAVER_USE_24H] ?: false,
            screenSaverShowDate = prefs[Keys.SCREEN_SAVER_SHOW_DATE] ?: true,
            screenSaverShowBattery = prefs[Keys.SCREEN_SAVER_SHOW_BATTERY] ?: true,
            screenSaverShowNotifications = prefs[Keys.SCREEN_SAVER_SHOW_NOTIFS] ?: true,
            screenSaverBrightnessPercent = (prefs[Keys.SCREEN_SAVER_BRIGHTNESS_PCT] ?: 28).coerceIn(5, 100),
            screenSaverTimeoutSec = (prefs[Keys.SCREEN_SAVER_TIMEOUT_SEC] ?: 12).coerceIn(5, 60),
            screenSaverStationaryOffSec = (prefs[Keys.SCREEN_SAVER_STATIONARY_OFF_SEC] ?: 4).coerceIn(2, 15),
            screenSaverMotionSensitivity = prefs[Keys.SCREEN_SAVER_SENSITIVITY] ?: "MEDIUM",
            screenSaverCooldownMs = prefs[Keys.SCREEN_SAVER_COOLDOWN_MS] ?: 4500L,
            screenSaverAnimationsEnabled = prefs[Keys.SCREEN_SAVER_ANIMATIONS] ?: true,
            screenSaverVibrationEnabled = prefs[Keys.SCREEN_SAVER_VIBRATION] ?: false,
            screenSaverCustomLayout = prefs[Keys.SCREEN_SAVER_CUSTOM_LAYOUT] ?: "STACKED",
            screenSaverCustomTextScale = prefs[Keys.SCREEN_SAVER_CUSTOM_SCALE] ?: "BALANCED",
            screenSaverCustomBgTheme = prefs[Keys.SCREEN_SAVER_CUSTOM_BG] ?: "PURE_OLED_BLACK",
            quickActionsOrder = prefs[Keys.QUICK_ACTIONS_ORDER]
                ?: "AQUA_DYNAMICS,FLASHLIGHT,DND,EDGE_GLOW,AUTO_DIM,WIFI,BLUETOOTH,HOTSPOT,AIRPLANE,BATTERY,HEALTH,NETWORK_DIAG,QR_SCANNER,SENSOR_LAB,DEV_TOOLS,SMART_ACTIONS"
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.pandaDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.pandaDataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.pandaDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setHideAquaDynamicsInFullScreen(enabled: Boolean) {
        context.pandaDataStore.edit { it[Keys.HIDE_AQUA_IN_FULLSCREEN] = enabled }
    }

    suspend fun setCpuRefreshMs(ms: Long) {
        context.pandaDataStore.edit { it[Keys.CPU_REFRESH_MS] = ms.coerceIn(500L, 10000L) }
    }

    suspend fun setRamRefreshMs(ms: Long) {
        context.pandaDataStore.edit { it[Keys.RAM_REFRESH_MS] = ms.coerceIn(500L, 10000L) }
    }

    suspend fun setBatteryMonitoringEnabled(enabled: Boolean) {
        context.pandaDataStore.edit { it[Keys.BATTERY_MONITOR_ENABLED] = enabled }
    }

    suspend fun setThermalMonitoringEnabled(enabled: Boolean) {
        context.pandaDataStore.edit { it[Keys.THERMAL_MONITOR_ENABLED] = enabled }
    }

    suspend fun updateAlertCategory(category: String, enabled: Boolean) {
        context.pandaDataStore.edit { prefs ->
            when (category) {
                "CPU" -> prefs[Keys.CPU_ALERT_ENABLED] = enabled
                "BATTERY" -> prefs[Keys.BATTERY_ALERT_ENABLED] = enabled
                "CHARGING" -> prefs[Keys.CHARGING_ALERTS_ENABLED] = enabled
                "THERMAL" -> prefs[Keys.THERMAL_ALERT_ENABLED] = enabled
                "RAM" -> prefs[Keys.RAM_ALERT_ENABLED] = enabled
                "STORAGE" -> prefs[Keys.STORAGE_ALERT_ENABLED] = enabled
                "NETWORK" -> prefs[Keys.NETWORK_ALERT_ENABLED] = enabled
                "SMART_ACTION" -> prefs[Keys.SMART_ACTION_ALERT_ENABLED] = enabled
                "SYSTEM" -> prefs[Keys.SYSTEM_ALERT_ENABLED] = enabled
            }
        }
    }

    suspend fun setThresholds(
        cpuPercent: Float? = null,
        lowBatteryPercent: Int? = null,
        highBatteryTempC: Float? = null,
        lowRamPercent: Float? = null,
        lowStoragePercent: Float? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            cpuPercent?.let { prefs[Keys.CPU_ALERT_THRESHOLD] = it.coerceIn(50f, 99f) }
            lowBatteryPercent?.let { prefs[Keys.LOW_BATTERY_THRESHOLD] = it.coerceIn(5, 50) }
            highBatteryTempC?.let { prefs[Keys.HIGH_BATTERY_TEMP] = it.coerceIn(35f, 52f) }
            lowRamPercent?.let { prefs[Keys.LOW_RAM_THRESHOLD] = it.coerceIn(65f, 98f) }
            lowStoragePercent?.let { prefs[Keys.LOW_STORAGE_THRESHOLD] = it.coerceIn(70f, 99f) }
        }
    }

    suspend fun setAlertBehavior(
        sound: Boolean? = null,
        vibration: Boolean? = null,
        cooldownSeconds: Int? = null,
        quietHours: Boolean? = null,
        quietStart: Int? = null,
        quietEnd: Int? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            sound?.let { prefs[Keys.ALERT_SOUND_ENABLED] = it }
            vibration?.let { prefs[Keys.ALERT_VIBRATION_ENABLED] = it }
            cooldownSeconds?.let { prefs[Keys.ALERT_COOLDOWN_SEC] = it.coerceIn(15, 3600) }
            quietHours?.let { prefs[Keys.QUIET_HOURS_ENABLED] = it }
            quietStart?.let { prefs[Keys.QUIET_START_HOUR] = it.coerceIn(0, 23) }
            quietEnd?.let { prefs[Keys.QUIET_END_HOUR] = it.coerceIn(0, 23) }
        }
    }

    suspend fun updateSmartActionSettings(
        backgroundMonitoring: Boolean? = null,
        flipToDnd: Boolean? = null,
        flipRestore: Boolean? = null,
        flipDelayMs: Long? = null,
        shakeFlashlight: Boolean? = null,
        shakeSensitivity: Float? = null,
        gestureCooldownMs: Long? = null,
        doubleShakeAction: String? = null,
        pickupDetection: Boolean? = null,
        proximityPocketGuard: Boolean? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            backgroundMonitoring?.let { prefs[Keys.BG_MONITOR_ENABLED] = it }
            flipToDnd?.let { prefs[Keys.FLIP_DND_ENABLED] = it }
            flipRestore?.let { prefs[Keys.FLIP_RESTORE_PREV] = it }
            flipDelayMs?.let { prefs[Keys.FLIP_DELAY_MS] = it.coerceIn(500L, 6000L) }
            shakeFlashlight?.let { prefs[Keys.SHAKE_FLASH_ENABLED] = it }
            shakeSensitivity?.let { prefs[Keys.SHAKE_SENSITIVITY] = it.coerceIn(8f, 24f) }
            gestureCooldownMs?.let { prefs[Keys.GESTURE_COOLDOWN_MS] = it.coerceIn(800L, 10000L) }
            doubleShakeAction?.let { prefs[Keys.DOUBLE_SHAKE_ACTION] = it }
            pickupDetection?.let { prefs[Keys.PICKUP_DETECTION_ENABLED] = it }
            proximityPocketGuard?.let { prefs[Keys.PROXIMITY_POCKET_ENABLED] = it }
        }
    }

    suspend fun updateShakeToCameraSettings(
        enabled: Boolean? = null,
        sensitivity: String? = null,
        vibrationEnabled: Boolean? = null,
        cooldownMs: Long? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.SHAKE_CAMERA_ENABLED] = it }
            sensitivity?.let {
                val normalized = it.uppercase()
                if (normalized in setOf("LOW", "MEDIUM", "HIGH")) {
                    prefs[Keys.SHAKE_CAMERA_SENSITIVITY] = normalized
                }
            }
            vibrationEnabled?.let { prefs[Keys.SHAKE_CAMERA_VIBRATION] = it }
            cooldownMs?.let { prefs[Keys.SHAKE_CAMERA_COOLDOWN_MS] = it.coerceIn(1500L, 10000L) }
        }
    }

    suspend fun updateEdgeGlowSettings(
        enabled: Boolean? = null,
        callsEnabled: Boolean? = null,
        notificationsEnabled: Boolean? = null,
        style: String? = null,
        callColorHex: Long? = null,
        notifColorHex: Long? = null,
        strokeDp: Float? = null,
        durationMs: Long? = null,
        hideInFullScreen: Boolean? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.EDGE_GLOW_ENABLED] = it }
            callsEnabled?.let { prefs[Keys.EDGE_GLOW_CALLS_ENABLED] = it }
            notificationsEnabled?.let { prefs[Keys.EDGE_GLOW_NOTIFS_ENABLED] = it }
            style?.let { prefs[Keys.EDGE_GLOW_STYLE] = it }
            callColorHex?.let { prefs[Keys.EDGE_GLOW_CALL_COLOR] = it }
            notifColorHex?.let { prefs[Keys.EDGE_GLOW_NOTIF_COLOR] = it }
            strokeDp?.let { prefs[Keys.EDGE_GLOW_STROKE_DP] = it.coerceIn(4f, 18f) }
            durationMs?.let { prefs[Keys.EDGE_GLOW_DURATION_MS] = it.coerceIn(1500L, 8000L) }
            hideInFullScreen?.let { prefs[Keys.HIDE_AQUA_IN_FULLSCREEN] = it }
        }
    }

    suspend fun updateAutoDimSettings(
        enabled: Boolean? = null,
        targetBrightnessPercent: Int? = null,
        intensityPercent: Int? = null,
        useExtraPrivacyShade: Boolean? = null,
        packagesSet: Set<String>? = null,
        disabledRulesSet: Set<String>? = null,
        tempPausedUntilMs: Long? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.AUTO_DIM_ENABLED] = it }
            targetBrightnessPercent?.let { prefs[Keys.AUTO_DIM_BRIGHTNESS_PCT] = it.coerceIn(5, 90) }
            intensityPercent?.let { pct ->
                val clamped = pct.coerceIn(10, 90)
                prefs[Keys.AUTO_DIM_INTENSITY_PCT] = clamped
                // Keep target brightness inversely synchronized (100 - intensity)
                prefs[Keys.AUTO_DIM_BRIGHTNESS_PCT] = (100 - clamped).coerceIn(10, 90)
            }
            useExtraPrivacyShade?.let { prefs[Keys.AUTO_DIM_EXTRA_SHADE] = it }
            packagesSet?.let { set ->
                prefs[Keys.AUTO_DIM_PACKAGES_CSV] = set.map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .sorted()
                    .joinToString(",")
            }
            disabledRulesSet?.let { set ->
                prefs[Keys.AUTO_DIM_DISABLED_RULES_CSV] = set.map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .sorted()
                    .joinToString(",")
            }
            tempPausedUntilMs?.let { prefs[Keys.AUTO_DIM_TEMP_PAUSED_UNTIL] = it.coerceAtLeast(0L) }
        }
    }

    suspend fun toggleAutoDimPackage(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        context.pandaDataStore.edit { prefs ->
            val current = (prefs[Keys.AUTO_DIM_PACKAGES_CSV] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            val disabled = (prefs[Keys.AUTO_DIM_DISABLED_RULES_CSV] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (current.contains(cleanPkg)) {
                current.remove(cleanPkg)
                disabled.remove(cleanPkg)
            } else {
                current.add(cleanPkg)
                disabled.remove(cleanPkg)
            }
            prefs[Keys.AUTO_DIM_PACKAGES_CSV] = current.sorted().joinToString(",")
            prefs[Keys.AUTO_DIM_DISABLED_RULES_CSV] = disabled.sorted().joinToString(",")
        }
    }

    suspend fun setIndividualAppDimRuleEnabled(packageName: String, ruleEnabled: Boolean) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        context.pandaDataStore.edit { prefs ->
            val disabled = (prefs[Keys.AUTO_DIM_DISABLED_RULES_CSV] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (ruleEnabled) {
                disabled.remove(cleanPkg)
            } else {
                disabled.add(cleanPkg)
            }
            prefs[Keys.AUTO_DIM_DISABLED_RULES_CSV] = disabled.sorted().joinToString(",")
        }
    }

    suspend fun resetAutoDimSettings() {
        context.pandaDataStore.edit { prefs ->
            prefs[Keys.AUTO_DIM_ENABLED] = false
            prefs[Keys.AUTO_DIM_INTENSITY_PCT] = 55
            prefs[Keys.AUTO_DIM_BRIGHTNESS_PCT] = 45
            prefs[Keys.AUTO_DIM_EXTRA_SHADE] = true
            prefs[Keys.AUTO_DIM_PACKAGES_CSV] = ""
            prefs[Keys.AUTO_DIM_DISABLED_RULES_CSV] = ""
            prefs[Keys.AUTO_DIM_TEMP_PAUSED_UNTIL] = 0L
        }
    }

    suspend fun updateSmartMotionAndCallSettings(
        raiseToWakeEnabled: Boolean? = null,
        raiseToWakeSensitivity: String? = null,
        raiseToWakeVibration: Boolean? = null,
        raiseToWakeCooldownMs: Long? = null,
        pickUpToSilenceEnabled: Boolean? = null,
        pickUpToSilenceSensitivity: String? = null,
        pickUpToSilenceVibration: Boolean? = null,
        proximityCallAnswerEnabled: Boolean? = null,
        proximityAnswerDelayMs: Long? = null,
        proximityAnswerVibration: Boolean? = null,
        putDownToSleepEnabled: Boolean? = null,
        putDownToSleepSensitivity: String? = null,
        putDownToSleepDelayMs: Long? = null,
        putDownToSleepCooldownMs: Long? = null,
        putDownToSleepVibration: Boolean? = null,
        putDownSurfaceMode: String? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            raiseToWakeEnabled?.let { prefs[Keys.RAISE_TO_WAKE_ENABLED] = it }
            raiseToWakeSensitivity?.let {
                val clean = it.uppercase()
                if (clean in setOf("LOW", "MEDIUM", "HIGH")) prefs[Keys.RAISE_TO_WAKE_SENSITIVITY] = clean
            }
            raiseToWakeVibration?.let { prefs[Keys.RAISE_TO_WAKE_VIBRATION] = it }
            raiseToWakeCooldownMs?.let { prefs[Keys.RAISE_TO_WAKE_COOLDOWN_MS] = it.coerceIn(1500L, 12000L) }

            pickUpToSilenceEnabled?.let { prefs[Keys.PICKUP_SILENCE_ENABLED] = it }
            pickUpToSilenceSensitivity?.let {
                val clean = it.uppercase()
                if (clean in setOf("LOW", "MEDIUM", "HIGH")) prefs[Keys.PICKUP_SILENCE_SENSITIVITY] = clean
            }
            pickUpToSilenceVibration?.let { prefs[Keys.PICKUP_SILENCE_VIBRATION] = it }

            proximityCallAnswerEnabled?.let { prefs[Keys.PROXIMITY_ANSWER_ENABLED] = it }
            proximityAnswerDelayMs?.let { prefs[Keys.PROXIMITY_ANSWER_DELAY_MS] = it.coerceIn(400L, 2500L) }
            proximityAnswerVibration?.let { prefs[Keys.PROXIMITY_ANSWER_VIBRATION] = it }

            putDownToSleepEnabled?.let { prefs[Keys.PUT_DOWN_SLEEP_ENABLED] = it }
            putDownToSleepSensitivity?.let {
                val clean = it.uppercase()
                if (clean in setOf("LOW", "MEDIUM", "HIGH")) prefs[Keys.PUT_DOWN_SLEEP_SENSITIVITY] = clean
            }
            putDownToSleepDelayMs?.let { prefs[Keys.PUT_DOWN_SLEEP_DELAY_MS] = it.coerceIn(800L, 4500L) }
            putDownToSleepCooldownMs?.let { prefs[Keys.PUT_DOWN_SLEEP_COOLDOWN_MS] = it.coerceIn(2500L, 12000L) }
            putDownToSleepVibration?.let { prefs[Keys.PUT_DOWN_SLEEP_VIBRATION] = it }
            putDownSurfaceMode?.let {
                val clean = it.uppercase()
                if (clean in setOf("BOTH", "FACE_DOWN", "FACE_UP")) prefs[Keys.PUT_DOWN_SURFACE_MODE] = clean
            }
        }
    }

    suspend fun updateScreenSaverSettings(
        enabled: Boolean? = null,
        style: String? = null,
        use24Hour: Boolean? = null,
        showDate: Boolean? = null,
        showBattery: Boolean? = null,
        showNotifications: Boolean? = null,
        brightnessPercent: Int? = null,
        timeoutSec: Int? = null,
        stationaryOffSec: Int? = null,
        motionSensitivity: String? = null,
        cooldownMs: Long? = null,
        animationsEnabled: Boolean? = null,
        vibrationEnabled: Boolean? = null,
        customLayout: String? = null,
        customTextScale: String? = null,
        customBgTheme: String? = null
    ) {
        val validStyles = setOf(
            "AQUA_DIGITAL", "MINIMAL_ANALOG", "NEON_CLOCK",
            "MINIMAL_AOD", "AQUA_WAVES", "DIGITAL_INFO", "CUSTOM_MINIMAL"
        )
        context.pandaDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.SCREEN_SAVER_ENABLED] = it }
            style?.let {
                val clean = it.uppercase()
                if (clean in validStyles) prefs[Keys.SCREEN_SAVER_STYLE] = clean
            }
            use24Hour?.let { prefs[Keys.SCREEN_SAVER_USE_24H] = it }
            showDate?.let { prefs[Keys.SCREEN_SAVER_SHOW_DATE] = it }
            showBattery?.let { prefs[Keys.SCREEN_SAVER_SHOW_BATTERY] = it }
            showNotifications?.let { prefs[Keys.SCREEN_SAVER_SHOW_NOTIFS] = it }
            brightnessPercent?.let { prefs[Keys.SCREEN_SAVER_BRIGHTNESS_PCT] = it.coerceIn(5, 100) }
            timeoutSec?.let { prefs[Keys.SCREEN_SAVER_TIMEOUT_SEC] = it.coerceIn(5, 60) }
            stationaryOffSec?.let { prefs[Keys.SCREEN_SAVER_STATIONARY_OFF_SEC] = it.coerceIn(2, 15) }
            motionSensitivity?.let {
                val clean = it.uppercase()
                if (clean in setOf("LOW", "MEDIUM", "HIGH")) prefs[Keys.SCREEN_SAVER_SENSITIVITY] = clean
            }
            cooldownMs?.let { prefs[Keys.SCREEN_SAVER_COOLDOWN_MS] = it.coerceIn(2000L, 15000L) }
            animationsEnabled?.let { prefs[Keys.SCREEN_SAVER_ANIMATIONS] = it }
            vibrationEnabled?.let { prefs[Keys.SCREEN_SAVER_VIBRATION] = it }
            customLayout?.let {
                val clean = it.uppercase()
                if (clean in setOf("STACKED", "INLINE_CLASSIC", "LEFT_EDITORIAL")) {
                    prefs[Keys.SCREEN_SAVER_CUSTOM_LAYOUT] = clean
                }
            }
            customTextScale?.let {
                val clean = it.uppercase()
                if (clean in setOf("COMPACT", "BALANCED", "BOLD_LARGE")) {
                    prefs[Keys.SCREEN_SAVER_CUSTOM_SCALE] = clean
                }
            }
            customBgTheme?.let {
                val clean = it.uppercase()
                if (clean in setOf("PURE_OLED_BLACK", "DEEP_ABYSS_TEAL", "MIDNIGHT_AURORA")) {
                    prefs[Keys.SCREEN_SAVER_CUSTOM_BG] = clean
                }
            }
        }
    }

    suspend fun setQuickActionsOrder(orderList: List<String>) {
        context.pandaDataStore.edit { prefs ->
            prefs[Keys.QUICK_ACTIONS_ORDER] = orderList.joinToString(",")
        }
    }

    suspend fun updateAquaDynamicsSettings(
        enabled: Boolean? = null,
        phoneEnabled: Boolean? = null,
        postCallSummaryEnabled: Boolean? = null,
        performanceEnabled: Boolean? = null,
        networkEnabled: Boolean? = null,
        interactionsEnabled: Boolean? = null,
        systemAlertsEnabled: Boolean? = null,
        priorityFilter: String? = null,
        autoMinimizeEnabled: Boolean? = null,
        autoMinimizeDelayMs: Long? = null,
        animationStyle: String? = null,
        eventCooldownMs: Long? = null,
        strictDismissLock: Boolean? = null,
        dismissedSignatures: Set<String>? = null
    ) {
        context.pandaDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.AQUA_DYNAMICS_ENABLED] = it }
            phoneEnabled?.let { prefs[Keys.AQUA_CAT_PHONE_ENABLED] = it }
            postCallSummaryEnabled?.let { prefs[Keys.AQUA_POST_CALL_SUMMARY] = it }
            performanceEnabled?.let { prefs[Keys.AQUA_CAT_PERFORMANCE_ENABLED] = it }
            networkEnabled?.let { prefs[Keys.AQUA_CAT_NETWORK_ENABLED] = it }
            interactionsEnabled?.let { prefs[Keys.AQUA_CAT_INTERACTIONS_ENABLED] = it }
            systemAlertsEnabled?.let { prefs[Keys.AQUA_CAT_SYSTEM_ALERTS_ENABLED] = it }
            priorityFilter?.let {
                val clean = it.uppercase()
                if (clean in setOf("ALL", "IMPORTANT_ONLY", "CRITICAL_ONLY")) {
                    prefs[Keys.AQUA_PRIORITY_FILTER] = clean
                }
            }
            autoMinimizeEnabled?.let { prefs[Keys.AQUA_AUTO_MINIMIZE_ENABLED] = it }
            autoMinimizeDelayMs?.let { prefs[Keys.AQUA_AUTO_MINIMIZE_DELAY_MS] = it.coerceIn(2500L, 15000L) }
            animationStyle?.let {
                val clean = it.uppercase()
                if (clean in setOf("FLUID_SPRING", "CRISP", "REDUCED")) {
                    prefs[Keys.AQUA_ANIMATION_STYLE] = clean
                }
            }
            eventCooldownMs?.let { prefs[Keys.AQUA_EVENT_COOLDOWN_MS] = it.coerceIn(2000L, 120000L) }
            strictDismissLock?.let { prefs[Keys.AQUA_STRICT_DISMISS_LOCK] = it }
            dismissedSignatures?.let { set ->
                prefs[Keys.AQUA_DISMISSED_SIGNATURES] = set
                    .map { s -> s.trim().replace("|", "_") }
                    .filter { s -> s.isNotEmpty() }
                    .take(40)
                    .joinToString("|")
            }
        }
    }
}
