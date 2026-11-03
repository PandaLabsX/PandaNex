package com.example.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.SystemUtils
import com.example.core.navigation.PandaRoute
import com.example.core.ui.AquaCircularGauge
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaLinearBar
import com.example.core.ui.AquaSparklineChart
import com.example.core.ui.HealthCondition
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors

private data class QuickToolChip(
    val title: String,
    val icon: ImageVector,
    val route: PandaRoute,
    val tag: String
)

@Composable
fun HomeDashboardScreen(
    viewModel: PandaMainViewModel,
    onNavigate: (PandaRoute) -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadAlertCount.collectAsStateWithLifecycle()
    val recentAlerts by viewModel.notifications.collectAsStateWithLifecycle()
    val rules by viewModel.automationRules.collectAsStateWithLifecycle()
    val events by viewModel.automationEvents.collectAsStateWithLifecycle()
    val gesture by viewModel.gestureTelemetry.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    val quickTools = listOf(
        QuickToolChip("Smart Gestures", Icons.Default.Bolt, PandaRoute.SmartGestures, "quick_smart_gestures"),
        QuickToolChip("Screen Saver", Icons.Default.AutoAwesome, PandaRoute.SmartScreenSaver, "quick_screen_saver"),
        QuickToolChip("Aqua Capsule", Icons.Default.AutoAwesome, PandaRoute.AquaDynamics, "quick_aqua_dynamics"),
        QuickToolChip("Edge Glow", Icons.Default.AutoAwesome, PandaRoute.EdgeGlow, "quick_edge_glow"),
        QuickToolChip("Dimmed App", Icons.Default.AccountBalance, PandaRoute.AutoDimApps, "quick_auto_dim"),
        QuickToolChip("Quick Center", Icons.Default.GridView, PandaRoute.QuickActionCenter, "quick_center_chip"),
        QuickToolChip("Net Diag", Icons.Default.NetworkCheck, PandaRoute.NetworkDiagnostics, "quick_net_diag"),
        QuickToolChip("Wi-Fi Signal", Icons.Default.Wifi, PandaRoute.WifiAnalyzer, "quick_wifi"),
        QuickToolChip("Sensor Lab", Icons.Default.Sensors, PandaRoute.SensorLab, "quick_sensors"),
        QuickToolChip("Dev Toolbox", Icons.Default.Code, PandaRoute.DeveloperToolbox, "quick_dev_tools"),
        QuickToolChip("QR Toolkit", Icons.Default.QrCodeScanner, PandaRoute.QrBarcodeToolkit, "quick_qr"),
        QuickToolChip("Display Test", Icons.Default.PhoneAndroid, PandaRoute.DisplayDiagnostics, "quick_display"),
        QuickToolChip("Health Report", Icons.Default.Assessment, PandaRoute.SystemHealthReport, "quick_health")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)
                )
            )
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Premium PandaNex Brand Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF010101))
                            .padding(2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_panda_launcher_icon),
                            contentDescription = "PandaNex Official Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PandaNex",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.app_tagline_secondary),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onNavigate(PandaRoute.NotificationCenter) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(text = if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (unreadCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = stringResource(R.string.action_notifications),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    IconButton(
                        onClick = { onNavigate(PandaRoute.Settings) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // 2. Hero Device Overview Banner Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onNavigate(PandaRoute.SystemHealthReport) }
                    .testTag("hero_overview_card")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_aqua_hero_banner),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xE6050D1C),
                                    Color(0xB3071933)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_tagline_primary),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64E9FF)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = live.deviceModel,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${live.androidVersion} • Up ${SystemUtils.formatDurationMillis(live.uptimeMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB4CFE6)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                label = live.overallHealth.statusLabel,
                                condition = live.overallHealth.condition
                            )
                            StatusPill(
                                label = "Thermal: ${live.thermal?.thermalLevel?.displayLabel ?: "Nominal"}",
                                condition = live.thermal?.thermalLevel?.condition ?: HealthCondition.NORMAL
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    AquaCircularGauge(
                        progress = live.overallHealth.scorePercent / 100f,
                        valueText = "${live.overallHealth.scorePercent}%",
                        subtitle = "HEALTH",
                        size = 96.dp,
                        strokeWidth = 8.dp,
                        condition = live.overallHealth.condition
                    )
                }
            }
        }

        // 3. Quick Tools Ribbon
        item {
            SectionHeader(
                title = "Quick Tools",
                icon = Icons.Default.Bolt,
                trailingText = "All Actions",
                onTrailingClick = { onNavigate(PandaRoute.QuickActionCenter) }
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(quickTools) { tool ->
                    AquaGlassCard(
                        modifier = Modifier
                            .width(114.dp)
                            .testTag(tool.tag),
                        onClick = { onNavigate(tool.route) },
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = tool.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 4. Live Telemetry 2x3 Grid (CPU, RAM, Battery, Thermal, Storage, Network)
        item {
            SectionHeader(
                title = "Live Device Telemetry",
                icon = Icons.Default.Speed,
                trailingText = "Monitor Hub",
                onTrailingClick = { onNavigate(PandaRoute.MonitorHub) }
            )
            Spacer(modifier = Modifier.height(4.dp))

            // CPU Full-Width Sparkline Card
            AquaGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_cpu_card"),
                onClick = { onNavigate(PandaRoute.CpuMonitor) },
                glowAccent = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("CPU & Performance", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${live.cpu?.socModel ?: "SoC"} • ${live.cpu?.activeCoreCount ?: 0}/${live.cpu?.coreCount ?: 0} Cores",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "${live.cpu?.overallUsagePercent ?: 0f}%",
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                AquaSparklineChart(
                    points = live.cpuGraphPoints,
                    modifier = Modifier.height(74.dp)
                )
            }
        }

        // Row: RAM + Battery
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AquaGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_ram_card"),
                    onClick = { onNavigate(PandaRoute.RamMonitor) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "${live.ram?.usagePercent ?: 0f}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("RAM Memory", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${SystemUtils.formatBytes(live.ram?.usedBytes ?: 0L)} / ${SystemUtils.formatBytes(live.ram?.totalBytes ?: 0L)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AquaLinearBar(
                        progress = (live.ram?.usagePercent ?: 0f) / 100f,
                        condition = live.ram?.condition ?: HealthCondition.NORMAL
                    )
                }

                AquaGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_battery_card"),
                    onClick = { onNavigate(PandaRoute.BatteryMonitor) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "${live.battery?.percentage ?: 0}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Battery", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${live.battery?.statusLabel ?: "Nominal"} • ${live.battery?.temperatureCelsius?.let { "${it}°C" } ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AquaLinearBar(
                        progress = (live.battery?.percentage ?: 0) / 100f,
                        condition = live.battery?.condition ?: HealthCondition.NORMAL
                    )
                }
            }
        }

        // Row: Heat Guard + Storage
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AquaGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_thermal_card"),
                    onClick = { onNavigate(PandaRoute.HeatGuard) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Default.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        StatusPill(
                            label = live.thermal?.thermalLevel?.displayLabel ?: "Normal",
                            condition = live.thermal?.thermalLevel?.condition ?: HealthCondition.NORMAL
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Heat Guard", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = live.thermal?.batteryTempCelsius?.let { "Battery Sensor: ${it}°C" }
                            ?: "OS Thermal Monitor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                AquaGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_storage_card"),
                    onClick = { onNavigate(PandaRoute.StorageMonitor) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "${live.storage?.usedPercent ?: 0f}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Storage (Read-Only)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${SystemUtils.formatBytes(live.storage?.availableBytes ?: 0L)} free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AquaLinearBar(
                        progress = (live.storage?.usedPercent ?: 0f) / 100f,
                        condition = live.storage?.condition ?: HealthCondition.NORMAL
                    )
                }
            }
        }

        // Network Card
        item {
            AquaGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_network_card"),
                onClick = { onNavigate(PandaRoute.NetworkInfo) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Network: ${live.networkInfo?.connectionType ?: "Checking..."}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "IPv4: ${live.networkInfo?.ipv4Address ?: "N/A"} • ${live.wifiSignal?.rssiDbm?.let { "$it dBm" } ?: live.networkInfo?.interfaceName ?: ""}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusPill(
                        label = if (live.networkInfo?.isConnected == true) "Online" else "Offline",
                        condition = if (live.networkInfo?.isConnected == true) HealthCondition.NORMAL else HealthCondition.WARNING
                    )
                }
            }
        }

        // 5. Smart Actions & Gesture Automation Panel
        item {
            val activeRulesCount = rules.count { it.isEnabled }
            val latestEvent = events.firstOrNull()
            SectionHeader(
                title = "Smart Actions & Gestures",
                icon = Icons.Default.Bolt,
                trailingText = "Configure",
                onTrailingClick = { onNavigate(PandaRoute.SmartActions) }
            )
            AquaGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_smart_actions_panel"),
                onClick = { onNavigate(PandaRoute.SmartActions) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aqua Capsule: ${if (settings.aquaDynamicsEnabled) "ON" else "Off"} • Flip DND: ${if (settings.flipToDndEnabled) "ON (2×/1×)" else "Off"} • Auto-Dim: ${if (settings.autoDimAppsEnabled) "${settings.autoDimPackagesSet.size} Apps" else "Off"}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Orientation: ${gesture.orientation.label} • Shake Torch: ${if (settings.shakeFlashlightEnabled) "ON" else "Off"} • Rules: $activeRulesCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (latestEvent != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Latest: ${latestEvent.ruleName} → ${latestEvent.actionDescription}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Intelligent Alert Engine Panel
        item {
            SectionHeader(
                title = "Alert Engine & Notifications",
                icon = Icons.Default.Notifications,
                trailingText = "History",
                onTrailingClick = { onNavigate(PandaRoute.NotificationCenter) }
            )
            AquaGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_alerts_panel"),
                onClick = { onNavigate(PandaRoute.NotificationCenter) }
            ) {
                if (recentAlerts.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(aqua.statusNormal)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "No active warnings. PandaNex Alert Engine is monitoring thresholds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    recentAlerts.take(2).forEachIndexed { idx, alert ->
                        if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = alert.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = alert.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
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
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
