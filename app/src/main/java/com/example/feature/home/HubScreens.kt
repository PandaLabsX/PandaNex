package com.example.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.navigation.PandaRoute
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.LocalAquaColors

private data class HubEntry(
    val moduleBadge: String,
    val title: String,
    val subtitle: String,
    val liveMetric: String,
    val condition: HealthCondition,
    val icon: ImageVector,
    val route: PandaRoute,
    val tag: String
)

@Composable
fun MonitorHubScreen(
    viewModel: PandaMainViewModel,
    onNavigate: (PandaRoute) -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    val entries = listOf(
        HubEntry(
            moduleBadge = "MOD 01",
            title = "CPU & Performance Monitor",
            subtitle = "Real-time load graph, per-core frequencies, SoC & thermal zones",
            liveMetric = "${live.cpu?.overallUsagePercent ?: 0f}%",
            condition = live.cpu?.condition ?: HealthCondition.NORMAL,
            icon = Icons.Default.Speed,
            route = PandaRoute.CpuMonitor,
            tag = "hub_cpu"
        ),
        HubEntry(
            moduleBadge = "MOD 07",
            title = "RAM Memory Monitor",
            subtitle = "Live ActivityManager memory usage, thresholds & history",
            liveMetric = "${live.ram?.usagePercent ?: 0f}%",
            condition = live.ram?.condition ?: HealthCondition.NORMAL,
            icon = Icons.Default.Memory,
            route = PandaRoute.RamMonitor,
            tag = "hub_ram"
        ),
        HubEntry(
            moduleBadge = "MOD 02",
            title = "Battery Monitor",
            subtitle = "Charge state, source, temperature, voltage, health & history",
            liveMetric = "${live.battery?.percentage ?: 0}%",
            condition = live.battery?.condition ?: HealthCondition.NORMAL,
            icon = Icons.Default.BatteryChargingFull,
            route = PandaRoute.BatteryMonitor,
            tag = "hub_battery"
        ),
        HubEntry(
            moduleBadge = "MOD 03",
            title = "Device Heat Guard",
            subtitle = "PowerManager thermal levels, accessible sensors & cooling advice",
            liveMetric = live.thermal?.thermalLevel?.displayLabel ?: "Normal",
            condition = live.thermal?.thermalLevel?.condition ?: HealthCondition.NORMAL,
            icon = Icons.Default.Thermostat,
            route = PandaRoute.HeatGuard,
            tag = "hub_heat_guard"
        ),
        HubEntry(
            moduleBadge = "MOD 16",
            title = "Storage Monitor (Read-Only)",
            subtitle = "Internal partition capacity, free space & block telemetry",
            liveMetric = "${SystemUtils.formatBytes(live.storage?.availableBytes ?: 0L)} Free",
            condition = live.storage?.condition ?: HealthCondition.NORMAL,
            icon = Icons.Default.Storage,
            route = PandaRoute.StorageMonitor,
            tag = "hub_storage"
        ),
        HubEntry(
            moduleBadge = "MOD 09",
            title = "Wi-Fi Signal Analyzer",
            subtitle = "Signal dBm, quality, band, channel, link speed & Wi-Fi standard",
            liveMetric = live.wifiSignal?.rssiDbm?.let { "$it dBm" } ?: (live.wifiSignal?.qualityLabel ?: "Idle"),
            condition = live.wifiSignal?.condition ?: HealthCondition.INFO,
            icon = Icons.Default.Wifi,
            route = PandaRoute.WifiAnalyzer,
            tag = "hub_wifi_analyzer"
        ),
        HubEntry(
            moduleBadge = "MOD 13",
            title = "Network Information",
            subtitle = "IPv4, IPv6, DNS servers, default gateway & active interfaces",
            liveMetric = live.networkInfo?.connectionType ?: "Unknown",
            condition = if (live.networkInfo?.isConnected == true) HealthCondition.NORMAL else HealthCondition.WARNING,
            icon = Icons.Default.Router,
            route = PandaRoute.NetworkInfo,
            tag = "hub_network_info"
        ),
        HubEntry(
            moduleBadge = "MOD 15",
            title = "System Health Report",
            subtitle = "Unified diagnostic score across all hardware & exportable report",
            liveMetric = "${live.overallHealth.scorePercent}% • ${live.overallHealth.statusLabel}",
            condition = live.overallHealth.condition,
            icon = Icons.Default.Assessment,
            route = PandaRoute.SystemHealthReport,
            tag = "hub_health_report"
        ),
        HubEntry(
            moduleBadge = "UTIL",
            title = "Connection Event Monitor",
            subtitle = "Live Wi-Fi, cellular, Bluetooth & internet event timeline",
            liveMetric = live.networkInfo?.bluetoothStateLabel ?: "Active",
            condition = HealthCondition.INFO,
            icon = Icons.Default.Hub,
            route = PandaRoute.ConnectionMonitor,
            tag = "hub_connection_monitor"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Hardware & System Monitor",
            subtitle = "Live non-fabricated Android hardware telemetry"
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries) { entry ->
                HubModuleCard(entry = entry, onClick = { onNavigate(entry.route) })
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun ToolsHubScreen(
    viewModel: PandaMainViewModel,
    onNavigate: (PandaRoute) -> Unit
) {
    val unread by viewModel.unreadAlertCount.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val tools = listOf(
        HubEntry(
            moduleBadge = "CENTER",
            title = "Quick Action Center",
            subtitle = "Rearrangeable control tiles: Flashlight, DND, Edge Glow, Auto-Dim & shortcuts",
            liveMetric = "15 Tiles",
            condition = HealthCondition.INFO,
            icon = Icons.Default.GridView,
            route = PandaRoute.QuickActionCenter,
            tag = "tools_quick_center"
        ),
        HubEntry(
            moduleBadge = "SMART",
            title = "Edge Glow Lighting",
            subtitle = "Animated glowing bezel edges for incoming calls and notifications",
            liveMetric = if (settings.edgeGlowEnabled) "Active" else "Configure",
            condition = if (settings.edgeGlowEnabled) HealthCondition.NORMAL else HealthCondition.INFO,
            icon = Icons.Default.AutoAwesome,
            route = PandaRoute.EdgeGlow,
            tag = "tools_edge_glow"
        ),
        HubEntry(
            moduleBadge = "PRIVACY",
            title = "Auto-Dimmed Apps",
            subtitle = "Automatically reduce screen brightness when banking or sensitive apps open",
            liveMetric = if (settings.autoDimAppsEnabled) "${settings.autoDimTargetBrightnessPercent}% Dim" else "${settings.autoDimPackagesSet.size} Apps",
            condition = if (settings.autoDimAppsEnabled) HealthCondition.NORMAL else HealthCondition.INFO,
            icon = Icons.Default.AccountBalance,
            route = PandaRoute.AutoDimApps,
            tag = "tools_auto_dim"
        ),
        HubEntry(
            moduleBadge = "MOD 04",
            title = "Network Diagnostics",
            subtitle = "Internet test, DNS latency, TCP ping, packet loss & HTTP speed test",
            liveMetric = "Live Probe",
            condition = HealthCondition.NORMAL,
            icon = Icons.Default.NetworkCheck,
            route = PandaRoute.NetworkDiagnostics,
            tag = "tools_net_diag"
        ),
        HubEntry(
            moduleBadge = "MOD 05",
            title = "Developer Toolbox",
            subtitle = "JSON formatter/validator, Base64, MD5/SHA hashes, URL & Text diff tools",
            liveMetric = "5 Suites",
            condition = HealthCondition.INFO,
            icon = Icons.Default.Code,
            route = PandaRoute.DeveloperToolbox,
            tag = "tools_dev_toolbox"
        ),
        HubEntry(
            moduleBadge = "MOD 14",
            title = "QR & Barcode Toolkit",
            subtitle = "CameraX + ML Kit scanner & QR generator (Text, URL, Wi-Fi, Contact)",
            liveMetric = "Scan & Gen",
            condition = HealthCondition.NORMAL,
            icon = Icons.Default.QrCodeScanner,
            route = PandaRoute.QrBarcodeToolkit,
            tag = "tools_qr_barcode"
        ),
        HubEntry(
            moduleBadge = "MOD 11",
            title = "Sensor Lab",
            subtitle = "Real-time readings & graphs for Accelerometer, Gyro, Light, Pressure & more",
            liveMetric = "Hardware",
            condition = HealthCondition.INFO,
            icon = Icons.Default.Sensors,
            route = PandaRoute.SensorLab,
            tag = "tools_sensor_lab"
        ),
        HubEntry(
            moduleBadge = "MOD 10",
            title = "Display Diagnostics",
            subtitle = "RGB/White/Black pixel check, multi-touch tester, refresh rate & DPI",
            liveMetric = "Interactive",
            condition = HealthCondition.INFO,
            icon = Icons.Default.PhoneAndroid,
            route = PandaRoute.DisplayDiagnostics,
            tag = "tools_display_diag"
        ),
        HubEntry(
            moduleBadge = "MOD 06",
            title = "Device Information",
            subtitle = "Model, Android SDK, security patch, build fingerprint, SoC & copy all",
            liveMetric = "Specs",
            condition = HealthCondition.NORMAL,
            icon = Icons.Default.Info,
            route = PandaRoute.DeviceInfo,
            tag = "tools_device_info"
        ),
        HubEntry(
            moduleBadge = "MOD 08",
            title = "App Permissions Inspector",
            subtitle = "Audit Camera, Mic, Location, Contacts & Storage permissions by app",
            liveMetric = "Security",
            condition = HealthCondition.INFO,
            icon = Icons.Default.Security,
            route = PandaRoute.AppPermissions,
            tag = "tools_app_permissions"
        ),
        HubEntry(
            moduleBadge = "UTIL",
            title = "App Launch Center",
            subtitle = "Browse accessible launcher apps, open directly or inspect app settings",
            liveMetric = "Launcher",
            condition = HealthCondition.INFO,
            icon = Icons.Default.Apps,
            route = PandaRoute.AppLaunchCenter,
            tag = "tools_app_launcher"
        ),
        HubEntry(
            moduleBadge = "UTIL",
            title = "Sound, Charging & System Utilities",
            subtitle = "Ringer modes, vibration test, uptime, charging telemetry & display shortcuts",
            liveMetric = "Controls",
            condition = HealthCondition.NORMAL,
            icon = Icons.Default.Tune,
            route = PandaRoute.DeviceUtilities,
            tag = "tools_device_utilities"
        ),
        HubEntry(
            moduleBadge = "MOD 12",
            title = "PandaNex Notification Center",
            subtitle = "Persistent alert history with category filters & read status",
            liveMetric = if (unread > 0) "$unread Unread" else "Up to date",
            condition = if (unread > 0) HealthCondition.WARNING else HealthCondition.NORMAL,
            icon = Icons.Default.NotificationsActive,
            route = PandaRoute.NotificationCenter,
            tag = "tools_notifications"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Diagnostics & Utilities",
            subtitle = "Hardware labs, developer utilities & smart controls"
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tools) { entry ->
                HubModuleCard(entry = entry, onClick = { onNavigate(entry.route) })
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HubModuleCard(
    entry: HubEntry,
    onClick: () -> Unit
) {
    AquaGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(entry.tag),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.moduleBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatusPill(label = entry.liveMetric, condition = entry.condition)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
