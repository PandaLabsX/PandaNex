package com.example.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_notifications")
data class AlertNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,      // CPU, BATTERY, THERMAL, RAM, STORAGE, NETWORK, SMART_ACTION, SYSTEM, DIAGNOSTIC
    val severity: String,      // INFO, NORMAL, WARNING, CRITICAL
    val title: String,
    val message: String,
    val statusDetail: String,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "metric_history")
data class MetricHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricType: String,    // CPU, RAM, BATTERY, THERMAL
    val primaryValue: Float,   // e.g. CPU %, RAM %, Battery %
    val secondaryValue: Float, // e.g. Battery Temp C or Thermal Status ordinal
    val statusLabel: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "network_test_history")
data class NetworkTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectionType: String,
    val internetReachable: Boolean,
    val dnsLatencyMs: Long,
    val pingLatencyMs: Long,
    val packetLossPercent: Int,
    val downloadSpeedMbps: Double,
    val uploadSpeedMbps: Double,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String,       // FACE_DOWN, FACE_UP, SHAKE, PICKUP, CHARGING_CONNECTED, CHARGING_DISCONNECTED, BATTERY_BELOW, WIFI_CONNECTED, WIFI_DISCONNECTED, PROXIMITY_NEAR, TIME_WINDOW
    val triggerParam: String = "", // e.g. "25" for battery < 25%, or "22:00"
    val conditionType: String = "ALWAYS", // ALWAYS, WHEN_CHARGING, WHEN_NOT_CHARGING, WHEN_BATTERY_BELOW_50, WHEN_WIFI_CONNECTED
    val actionType: String,        // ENABLE_DND, DISABLE_DND, RINGER_SILENT, RINGER_VIBRATE, RINGER_NORMAL, TOGGLE_FLASHLIGHT, SHOW_NOTIFICATION, SHOW_REMINDER, OPEN_APP
    val actionParam: String = "",  // e.g. reminder text or target package name
    val isEnabled: Boolean = true,
    val lastTriggeredAt: Long? = null,
    val triggerCount: Int = 0
)

@Entity(tableName = "automation_events")
data class AutomationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleName: String,
    val triggerDescription: String,
    val actionDescription: String,
    val succeeded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "connection_events")
data class ConnectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // WIFI, MOBILE, INTERNET, BLUETOOTH
    val stateLabel: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
