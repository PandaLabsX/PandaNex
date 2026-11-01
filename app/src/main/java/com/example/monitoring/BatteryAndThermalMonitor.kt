package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.core.ui.HealthCondition
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

data class BatterySnapshot(
    val percentage: Int,
    val temperatureCelsius: Float?,
    val voltageMv: Int?,
    val currentMicroAmps: Int?,
    val isCharging: Boolean,
    val statusLabel: String,     // Charging, Discharging, Full, Not Charging
    val chargingSource: String,  // AC Charger, USB Port, Wireless Pad, Dock, On Battery
    val healthLabel: String,     // Good, Overheat, Over Voltage, Cold, Dead, Unspecified
    val technology: String,
    val chargeTimeRemainingMs: Long?, // API 28+ BatteryManager.computeChargeTimeRemaining()
    val condition: HealthCondition,
    val conditionSummary: String,
    val chargingStartedAtMs: Long?,
    val timestamp: Long = System.currentTimeMillis()
)

class BatteryMonitor(private val context: Context) {

    private var chargingSessionStartMs: Long? = null

    fun sampleNow(): BatterySnapshot {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return parseIntent(intent)
    }

    fun observeBattery(): Flow<BatterySnapshot> = callbackFlow {
        trySend(sampleNow())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) {
                    trySend(parseIntent(intent))
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun parseIntent(intent: Intent?): BatterySnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentage = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
        } else {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (tempTenths in 1..900) tempTenths / 10f else null

        val voltageRaw = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val voltageMv = when {
            voltageRaw > 100 -> voltageRaw
            voltageRaw in 1..15 -> voltageRaw * 1000
            else -> null
        }

        val currentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && abs(it) < 20_000_000 }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            if (chargingSessionStartMs == null) chargingSessionStartMs = System.currentTimeMillis()
        } else {
            chargingSessionStartMs = null
        }

        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Fully Charged"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Connected (Not Charging)"
            else -> "Unknown"
        }

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val sourceLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Data/Power Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charging Pad"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Charging Dock"
            else -> "Discharging on Battery"
        }

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthLabel = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded / Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Hardware Fault"
            else -> "Not Reported"
        }

        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Li-ion"

        val chargeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isCharging) {
            bm?.computeChargeTimeRemaining()?.takeIf { it > 0L }
        } else null

        val (condition, summary) = when {
            (tempC != null && tempC >= 44f) || healthInt == BatteryManager.BATTERY_HEALTH_OVERHEAT ->
                HealthCondition.CRITICAL to "High Battery Temperature"
            percentage <= 15 && !isCharging ->
                HealthCondition.CRITICAL to "Low Battery Reserve"
            (tempC != null && tempC >= 39.5f) || (percentage <= 25 && !isCharging) ->
                HealthCondition.WARNING to "Warm / Low Charge"
            isCharging ->
                HealthCondition.INFO to "Active Charging"
            else ->
                HealthCondition.NORMAL to "Healthy"
        }

        return BatterySnapshot(
            percentage = percentage,
            temperatureCelsius = tempC,
            voltageMv = voltageMv,
            currentMicroAmps = currentNow,
            isCharging = isCharging,
            statusLabel = statusLabel,
            chargingSource = sourceLabel,
            healthLabel = healthLabel,
            technology = tech,
            chargeTimeRemainingMs = chargeRemainingMs,
            condition = condition,
            conditionSummary = summary,
            chargingStartedAtMs = chargingSessionStartMs
        )
    }
}

enum class ThermalLevel(val displayLabel: String, val condition: HealthCondition, val coolingAdvice: String) {
    NONE("Normal", HealthCondition.NORMAL, "Device thermal envelope is completely nominal. No action required."),
    LIGHT("Light", HealthCondition.INFO, "Slight warmth detected under active workload. Airflow is sufficient."),
    MODERATE("Moderate", HealthCondition.WARNING, "Device is warm. Consider reducing screen brightness or closing heavy 3D apps if sustained."),
    SEVERE("Severe", HealthCondition.WARNING, "Thermal throttling active. Pause intensive tasks, remove thick cases, and avoid direct sunlight."),
    CRITICAL("Critical", HealthCondition.CRITICAL, "Critical thermal load! Disconnect fast chargers, stop intensive workloads, and let the device rest."),
    EMERGENCY("Emergency", HealthCondition.CRITICAL, "Emergency thermal threshold reached. Essential system functions may be throttled immediately."),
    SHUTDOWN("Shutdown Imminent", HealthCondition.CRITICAL, "Device has reached thermal protection limit to safeguard hardware."),
    UNAVAILABLE("Thermal Status API Restricted (API < 29)", HealthCondition.INFO, "OS thermal headroom API requires Android 10+. Showing accessible battery sensor reading only.")
}

data class ThermalSnapshot(
    val thermalLevel: ThermalLevel,
    val powerManagerStatusSupported: Boolean,
    val thermalHeadroom: Float?, // API 30+ PowerManager.getThermalHeadroom
    val batteryTempCelsius: Float?, // Real BatteryManager sensor reading
    val cpuSensorTempCelsius: Float?, // Real thermal_zone reading if accessible, otherwise null
    val timestamp: Long = System.currentTimeMillis()
)

class ThermalMonitor(
    private val context: Context,
    private val batteryMonitor: BatteryMonitor,
    private val cpuMonitor: CpuMonitor
) {
    suspend fun sample(): ThermalSnapshot {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batterySnap = batteryMonitor.sampleNow()
        val cpuSnap = cpuMonitor.sample()

        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
                else -> ThermalLevel.NONE
            }
        } else {
            ThermalLevel.UNAVAILABLE
        }

        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { pm.getThermalHeadroom(5) }.getOrNull()?.takeIf { !it.isNaN() && it > 0f }
        } else null

        return ThermalSnapshot(
            thermalLevel = level,
            powerManagerStatusSupported = supported,
            thermalHeadroom = headroom,
            batteryTempCelsius = batterySnap.temperatureCelsius,
            cpuSensorTempCelsius = cpuSnap.cpuTemperatureCelsius
        )
    }
}
