package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.common.SystemUtils
import com.example.data.local.database.AlertNotificationEntity
import com.example.data.local.database.PandaDao
import com.example.data.local.datastore.PandaPreferencesRepository
import com.example.data.local.datastore.PandaSettings
import com.example.monitoring.BatterySnapshot
import com.example.monitoring.CpuSnapshot
import com.example.monitoring.RamSnapshot
import com.example.monitoring.StorageSnapshot
import com.example.monitoring.ThermalLevel
import com.example.monitoring.ThermalSnapshot
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

object NotificationChannels {
    const val CHANNEL_CRITICAL = "pandanex_critical_alerts"
    const val CHANNEL_WARNING = "pandanex_warning_alerts"
    const val CHANNEL_SMART = "pandanex_smart_actions"
    const val CHANNEL_SERVICE = "pandanex_foreground_service"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val critical = NotificationChannel(
            CHANNEL_CRITICAL,
            context.getString(R.string.channel_critical_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_critical_desc)
            enableVibration(true)
        }

        val warning = NotificationChannel(
            CHANNEL_WARNING,
            context.getString(R.string.channel_warning_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_warning_desc)
        }

        val smart = NotificationChannel(
            CHANNEL_SMART,
            context.getString(R.string.channel_smart_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_smart_desc)
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_service_desc)
            setShowBadge(false)
        }

        nm.createNotificationChannels(listOf(critical, warning, smart, service))
    }
}

/**
 * PandaNex Alert Engine
 * Implements threshold crossing detection, cooldown deduplication, recovery alerts, quiet hours,
 * persistent local Room notification history, and Android system notifications.
 */
class AlertEngine(
    private val context: Context,
    private val dao: PandaDao,
    private val preferencesRepository: PandaPreferencesRepository
) {
    private val lastAlertTimeByKey = ConcurrentHashMap<String, Long>()
    private val activeThresholdStates = ConcurrentHashMap<String, Boolean>()
    private var previousChargingState: Boolean? = null
    private var previousInternetState: Boolean? = null
    var onNotificationDispatched: ((title: String) -> Unit)? = null
    var onStructuredAlertDispatched: ((dedupKey: String, category: String, severity: String, title: String, message: String, statusDetail: String) -> Unit)? = null

    suspend fun evaluateTelemetry(
        cpu: CpuSnapshot?,
        ram: RamSnapshot?,
        battery: BatterySnapshot?,
        thermal: ThermalSnapshot?,
        storage: StorageSnapshot?,
        internetConnected: Boolean?
    ) {
        val settings = preferencesRepository.settingsFlow.first()

        // 1. CPU Alert Intelligence
        if (cpu != null && settings.cpuAlertEnabled) {
            val highCpu = cpu.overallUsagePercent >= settings.cpuAlertThresholdPercent
            handleThresholdCondition(
                key = "CPU_HIGH",
                isCrossed = highCpu,
                category = "CPU",
                severity = if (cpu.overallUsagePercent >= 95f) "CRITICAL" else "WARNING",
                title = "High CPU Load (${cpu.overallUsagePercent}%)",
                message = "CPU utilization crossed configured threshold (${settings.cpuAlertThresholdPercent.toInt()}%).",
                statusDetail = "${cpu.overallUsagePercent}% via ${cpu.usageSource}",
                recoveryTitle = "CPU Load Normalized",
                recoveryMessage = "CPU utilization returned to normal (${cpu.overallUsagePercent}%).",
                settings = settings
            )
        }

        // 2. Battery & Charging Alerts
        if (battery != null && settings.batteryAlertEnabled) {
            val lowBat = battery.percentage <= settings.lowBatteryThresholdPercent && !battery.isCharging
            handleThresholdCondition(
                key = "BATTERY_LOW",
                isCrossed = lowBat,
                category = "BATTERY",
                severity = if (battery.percentage <= 10) "CRITICAL" else "WARNING",
                title = "Low Battery (${battery.percentage}%)",
                message = "Battery reserve fell below ${settings.lowBatteryThresholdPercent}%. Connect charger soon.",
                statusDetail = "${battery.percentage}% • ${battery.statusLabel}",
                recoveryTitle = null,
                recoveryMessage = null,
                settings = settings
            )

            if (battery.temperatureCelsius != null) {
                val hotBat = battery.temperatureCelsius >= settings.highBatteryTempCelsius
                handleThresholdCondition(
                    key = "BATTERY_TEMP_HIGH",
                    isCrossed = hotBat,
                    category = "BATTERY",
                    severity = if (battery.temperatureCelsius >= 45f) "CRITICAL" else "WARNING",
                    title = "Battery Temperature Warning (${battery.temperatureCelsius}°C)",
                    message = "Battery sensor crossed ${settings.highBatteryTempCelsius}°C threshold.",
                    statusDetail = "${battery.temperatureCelsius}°C • ${battery.chargingSource}",
                    recoveryTitle = "Battery Temperature Normalized",
                    recoveryMessage = "Battery cooled down to ${battery.temperatureCelsius}°C.",
                    settings = settings
                )
            }

            if (settings.chargingAlertsEnabled) {
                val prevCharge = previousChargingState
                if (prevCharge != null && prevCharge != battery.isCharging) {
                    if (battery.isCharging) {
                        dispatchAlert(
                            dedupKey = "CHARGING_CONN",
                            category = "BATTERY",
                            severity = "INFO",
                            title = "Charger Connected",
                            message = "Charging started via ${battery.chargingSource} at ${battery.percentage}%.",
                            statusDetail = "${battery.percentage}% • ${battery.chargingSource}",
                            settings = settings,
                            customCooldownSec = 15
                        )
                    } else {
                        dispatchAlert(
                            dedupKey = "CHARGING_DISC",
                            category = "BATTERY",
                            severity = "INFO",
                            title = "Charger Disconnected",
                            message = "Device switched to battery power at ${battery.percentage}%.",
                            statusDetail = "${battery.percentage}% remaining",
                            settings = settings,
                            customCooldownSec = 15
                        )
                    }
                }
                previousChargingState = battery.isCharging
            }
        }

        // 3. Thermal Alerts (Only using real PowerManager status)
        if (thermal != null && settings.thermalAlertEnabled && thermal.powerManagerStatusSupported) {
            val elevated = thermal.thermalLevel in listOf(
                ThermalLevel.MODERATE,
                ThermalLevel.SEVERE,
                ThermalLevel.CRITICAL,
                ThermalLevel.EMERGENCY,
                ThermalLevel.SHUTDOWN
            )
            val severity = if (thermal.thermalLevel in listOf(
                    ThermalLevel.CRITICAL,
                    ThermalLevel.EMERGENCY,
                    ThermalLevel.SHUTDOWN
                )
            ) "CRITICAL" else "WARNING"

            handleThresholdCondition(
                key = "THERMAL_ELEVATED",
                isCrossed = elevated,
                category = "THERMAL",
                severity = severity,
                title = "Thermal Alert: ${thermal.thermalLevel.displayLabel}",
                message = thermal.thermalLevel.coolingAdvice,
                statusDetail = "Status: ${thermal.thermalLevel.displayLabel}",
                recoveryTitle = "Thermal Status Recovered",
                recoveryMessage = "Device thermal status returned to ${thermal.thermalLevel.displayLabel}.",
                settings = settings
            )
        }

        // 4. RAM Alerts
        if (ram != null && settings.ramAlertEnabled) {
            val highRam = ram.isLowMemory || ram.usagePercent >= settings.lowRamThresholdPercent
            handleThresholdCondition(
                key = "RAM_PRESSURE",
                isCrossed = highRam,
                category = "RAM",
                severity = if (ram.isLowMemory) "CRITICAL" else "WARNING",
                title = "High Memory Pressure (${ram.usagePercent}%)",
                message = "Available RAM is ${SystemUtils.formatBytes(ram.availableBytes)}.",
                statusDetail = "${ram.usagePercent}% used",
                recoveryTitle = null,
                recoveryMessage = null,
                settings = settings
            )
        }

        // 5. Storage Alerts
        if (storage != null && settings.storageAlertEnabled) {
            val lowStorage = storage.usedPercent >= settings.lowStorageThresholdPercent
            handleThresholdCondition(
                key = "STORAGE_LOW",
                isCrossed = lowStorage,
                category = "STORAGE",
                severity = if (storage.usedPercent >= 95f) "CRITICAL" else "WARNING",
                title = "Low Internal Storage (${storage.remainingPercent}% Free)",
                message = "Only ${SystemUtils.formatBytes(storage.availableBytes)} remaining on internal storage.",
                statusDetail = "${storage.usedPercent}% used",
                recoveryTitle = null,
                recoveryMessage = null,
                settings = settings
            )
        }

        // 6. Network Connectivity Transitions
        if (internetConnected != null && settings.networkAlertEnabled) {
            val prevNet = previousInternetState
            if (prevNet != null && prevNet != internetConnected) {
                if (!internetConnected) {
                    dispatchAlert(
                        dedupKey = "NET_OFFLINE",
                        category = "NETWORK",
                        severity = "WARNING",
                        title = "Internet Disconnected",
                        message = "Active network lost internet connectivity.",
                        statusDetail = "Offline",
                        settings = settings,
                        customCooldownSec = 20
                    )
                } else {
                    dispatchAlert(
                        dedupKey = "NET_RESTORED",
                        category = "NETWORK",
                        severity = "NORMAL",
                        title = "Internet Connection Restored",
                        message = "Active network connectivity is back online.",
                        statusDetail = "Online",
                        settings = settings,
                        customCooldownSec = 20
                    )
                }
            }
            previousInternetState = internetConnected
        }
    }

    suspend fun recordSmartActionAlert(
        title: String,
        message: String,
        statusDetail: String,
        severity: String = "INFO"
    ) {
        val settings = preferencesRepository.settingsFlow.first()
        if (!settings.smartActionAlertEnabled) return
        dispatchAlert(
            dedupKey = "SMART_${title.hashCode()}",
            category = "SMART_ACTION",
            severity = severity,
            title = title,
            message = message,
            statusDetail = statusDetail,
            settings = settings,
            customCooldownSec = 3
        )
    }

    suspend fun recordDiagnosticEvent(
        category: String,
        title: String,
        message: String,
        statusDetail: String,
        severity: String = "INFO"
    ) {
        val settings = preferencesRepository.settingsFlow.first()
        dispatchAlert(
            dedupKey = "DIAG_${title}_${System.currentTimeMillis() / 2000}",
            category = category,
            severity = severity,
            title = title,
            message = message,
            statusDetail = statusDetail,
            settings = settings,
            customCooldownSec = 2
        )
    }

    private suspend fun handleThresholdCondition(
        key: String,
        isCrossed: Boolean,
        category: String,
        severity: String,
        title: String,
        message: String,
        statusDetail: String,
        recoveryTitle: String?,
        recoveryMessage: String?,
        settings: PandaSettings
    ) {
        val wasActive = activeThresholdStates[key] ?: false
        if (isCrossed && !wasActive) {
            activeThresholdStates[key] = true
            dispatchAlert(
                dedupKey = key,
                category = category,
                severity = severity,
                title = title,
                message = message,
                statusDetail = statusDetail,
                settings = settings
            )
        } else if (!isCrossed && wasActive) {
            activeThresholdStates[key] = false
            if (recoveryTitle != null && recoveryMessage != null) {
                dispatchAlert(
                    dedupKey = "${key}_RECOVERY",
                    category = category,
                    severity = "NORMAL",
                    title = recoveryTitle,
                    message = recoveryMessage,
                    statusDetail = "Recovered",
                    settings = settings,
                    customCooldownSec = 30
                )
            }
        }
    }

    private suspend fun dispatchAlert(
        dedupKey: String,
        category: String,
        severity: String,
        title: String,
        message: String,
        statusDetail: String,
        settings: PandaSettings,
        customCooldownSec: Int? = null
    ) {
        val now = System.currentTimeMillis()
        val cooldownMs = ((customCooldownSec ?: settings.alertCooldownSeconds) * 1000L).coerceAtLeast(1500L)
        val lastFired = lastAlertTimeByKey[dedupKey] ?: 0L
        if (now - lastFired < cooldownMs) return
        lastAlertTimeByKey[dedupKey] = now

        // Persist in PandaNex Notification Center (Room DB)
        val id = dao.insertNotification(
            AlertNotificationEntity(
                category = category,
                severity = severity,
                title = title,
                message = message,
                statusDetail = statusDetail,
                isRead = false,
                timestamp = now
            )
        )

        // Notify Aqua Dynamics event engine (even if POST_NOTIFICATIONS is not granted)
        onStructuredAlertDispatched?.invoke(dedupKey, category, severity, title, message, statusDetail)

        // When Aqua Dynamics is enabled, Aqua Dynamics presents the dynamic animated interface directly.
        // Do NOT show a standard Android notification or message before displaying Aqua Dynamics.
        // Alert notifications remain saved separately in the PandaNex Notification Center (Room DB) above.
        if (settings.aquaDynamicsEnabled) return

        // Post Android System Notification if permitted and outside quiet hours (unless CRITICAL)
        if (isInQuietHours(settings) && severity != "CRITICAL") return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = when (severity) {
            "CRITICAL" -> NotificationChannels.CHANNEL_CRITICAL
            "WARNING" -> NotificationChannels.CHANNEL_WARNING
            else -> NotificationChannels.CHANNEL_SMART
        }

        val priority = when (severity) {
            "CRITICAL" -> NotificationCompat.PRIORITY_HIGH
            "WARNING" -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pandanex_notify)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("PandaNex • $statusDetail")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setColor(0xFF00C8D7.toInt())
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (!settings.alertSoundEnabled || category == "SMART_ACTION") {
            builder.setSilent(true)
        }
        if (settings.alertVibrationEnabled && severity in listOf("WARNING", "CRITICAL")) {
            SystemUtils.triggerHapticPulse(context, 60L)
        }

        if (settings.edgeGlowEnabled && settings.edgeGlowNotificationsEnabled) {
            onNotificationDispatched?.invoke(title)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify((id % 10000).toInt() + 2000, builder.build())
        }
    }

    private fun isInQuietHours(settings: PandaSettings): Boolean {
        if (!settings.quietHoursEnabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = settings.quietStartHour
        val end = settings.quietEndHour
        return if (start <= end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }
}
