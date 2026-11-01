package com.example.feature.reports

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaCircularGauge
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.LocalAquaColors
import kotlin.math.roundToInt

// MODULE 12 — NOTIFICATION CENTER
@Composable
fun NotificationCenterScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = listOf(
        "ALL" to "All",
        "CPU" to "CPU",
        "BATTERY" to "Battery",
        "THERMAL" to "Thermal",
        "RAM" to "RAM",
        "STORAGE" to "Storage",
        "NETWORK" to "Network",
        "SMART_ACTION" to "Smart Actions",
        "DIAGNOSTIC" to "Diagnostics"
    )

    val filtered = remember(notifications, selectedCategory) {
        if (selectedCategory == "ALL") notifications
        else notifications.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Notification Center",
            subtitle = "Module 12 • PandaNex Alert Engine History (${filtered.size})",
            onBack = onBack,
            actions = {
                if (notifications.any { !it.isRead }) {
                    IconButton(onClick = { viewModel.markAllNotificationsRead() }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Mark All Read")
                    }
                }
                if (notifications.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearAllNotifications() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History")
                    }
                }
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { (key, label) ->
                FilterChip(
                    selected = selectedCategory == key,
                    onClick = { selectedCategory = key },
                    label = { Text(label) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    AquaGlassCard {
                        SectionHeader(title = "Clean Slate", icon = Icons.Default.Notifications)
                        Text(
                            "No PandaNex alerts recorded in this category yet. Threshold crossings, recovery alerts, and smart gesture events appear here automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { alert ->
                    AquaGlassCard(
                        glowAccent = !alert.isRead,
                        onClick = { if (!alert.isRead) viewModel.markNotificationRead(alert.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!alert.isRead) "● ${alert.title}" else alert.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (!alert.isRead) FontWeight.Bold else FontWeight.Medium
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(
                                label = alert.category,
                                condition = when (alert.severity) {
                                    "CRITICAL" -> HealthCondition.CRITICAL
                                    "WARNING" -> HealthCondition.WARNING
                                    "NORMAL" -> HealthCondition.NORMAL
                                    else -> HealthCondition.INFO
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = alert.statusDetail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = SystemUtils.formatTimestamp(alert.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// MODULE 15 — SYSTEM HEALTH REPORT
@Composable
fun SystemHealthReportScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    val sensorCount = remember {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getSensorList(Sensor.TYPE_ALL).size
    }

    val displaySummary = remember {
        val dm = context.resources.displayMetrics
        val hz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.refreshRate?.roundToInt() ?: 60
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(WindowManager::class.java))?.defaultDisplay?.refreshRate?.roundToInt() ?: 60
        }
        "${dm.widthPixels}×${dm.heightPixels} @ ${hz}Hz (${dm.densityDpi} dpi)"
    }

    fun buildExportText(): String = buildString {
        appendLine("==========================================")
        appendLine("PANDANEX — COMPLETE SYSTEM HEALTH REPORT")
        appendLine("==========================================")
        appendLine("Generated: ${SystemUtils.formatTimestamp(System.currentTimeMillis())}")
        appendLine("Device: ${live.deviceModel} (${live.androidVersion})")
        appendLine("Overall Condition: ${live.overallHealth.statusLabel} (${live.overallHealth.scorePercent}/100)")
        appendLine()
        appendLine("--- SUBSYSTEM BREAKDOWN ---")
        appendLine("• CPU: ${live.cpu?.overallUsagePercent ?: 0f}% (${live.cpu?.conditionLabel}) [${live.cpu?.socModel}]")
        appendLine("• RAM: ${live.ram?.usagePercent ?: 0f}% used (${SystemUtils.formatBytes(live.ram?.availableBytes ?: 0L)} free)")
        appendLine("• Battery: ${live.battery?.percentage ?: 0}% (${live.battery?.statusLabel}, Health: ${live.battery?.healthLabel}, Temp: ${live.battery?.temperatureCelsius?.let { "${it}°C" } ?: "N/A"})")
        appendLine("• Thermal: ${live.thermal?.thermalLevel?.displayLabel ?: "Nominal"}")
        appendLine("• Storage (Read-Only): ${live.storage?.usedPercent ?: 0f}% used (${SystemUtils.formatBytes(live.storage?.availableBytes ?: 0L)} free)")
        appendLine("• Network: ${live.networkInfo?.connectionType ?: "N/A"} (IPv4: ${live.networkInfo?.ipv4Address ?: "N/A"})")
        appendLine("• Wi-Fi: ${live.wifiSignal?.qualityLabel ?: "N/A"} (${live.wifiSignal?.rssiDbm?.let { "$it dBm" } ?: "N/A"})")
        appendLine("• Hardware Sensors: $sensorCount verified hardware sensors")
        appendLine("• Display: $displaySummary")
        appendLine()
        appendLine("--- DIAGNOSTIC NOTES ---")
        live.overallHealth.explanationBullets.forEach { appendLine("- $it") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "System Health Report",
            subtitle = "Module 15 • Unified Hardware & Subsystem Audit",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        SystemUtils.shareTextReport(
                            context = context,
                            title = "PandaNex System Health Report",
                            content = buildExportText(),
                            fileNamePrefix = "pandanex_health_report"
                        )
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Health Report")
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true, modifier = Modifier.testTag("health_report_score_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AquaCircularGauge(
                            progress = live.overallHealth.scorePercent / 100f,
                            valueText = "${live.overallHealth.scorePercent}",
                            subtitle = "SCORE / 100",
                            condition = live.overallHealth.condition
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                label = live.overallHealth.statusLabel,
                                condition = live.overallHealth.condition
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(live.deviceModel, style = MaterialTheme.typography.titleMedium)
                            Text(
                                live.androidVersion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Verified Diagnostic Findings", icon = Icons.Default.Assessment)
                    live.overallHealth.explanationBullets.forEach { bullet ->
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "9-Subsystem Telemetry Matrix")
                    KeyValueRow("1. CPU Condition", "${live.cpu?.overallUsagePercent ?: 0f}% (${live.cpu?.conditionLabel ?: "OK"})")
                    KeyValueRow("2. RAM Condition", "${live.ram?.usagePercent ?: 0f}% (${live.ram?.conditionLabel ?: "OK"})")
                    KeyValueRow("3. Battery Health", "${live.battery?.percentage ?: 0}% • ${live.battery?.healthLabel ?: "Good"}")
                    KeyValueRow("4. Thermal State", live.thermal?.thermalLevel?.displayLabel ?: "Normal")
                    KeyValueRow("5. Network State", live.networkInfo?.connectionType ?: "Offline")
                    KeyValueRow("6. Wi-Fi Signal", live.wifiSignal?.rssiDbm?.let { "$it dBm (${live.wifiSignal?.qualityLabel})" } ?: "Inactive")
                    KeyValueRow("7. Internal Storage", "${live.storage?.remainingPercent ?: 0f}% Free")
                    KeyValueRow("8. Hardware Sensors", "$sensorCount Active Sensors")
                    KeyValueRow("9. Display Panel", displaySummary)
                }
            }

            item {
                Button(
                    onClick = {
                        SystemUtils.shareTextReport(
                            context = context,
                            title = "PandaNex System Health Report",
                            content = buildExportText(),
                            fileNamePrefix = "pandanex_health_report"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_health_report_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate & Share Full Health Report")
                }
            }
        }
    }
}
