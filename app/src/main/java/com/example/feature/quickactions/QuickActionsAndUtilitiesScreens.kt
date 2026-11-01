package com.example.feature.quickactions

import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.navigation.PandaRoute
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class QuickActionSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isActive: Boolean = false,
    val onTrigger: () -> Unit
)

// SECTION 7 — QUICK ACTION CENTER
@Composable
fun QuickActionCenterScreen(
    viewModel: PandaMainViewModel,
    onNavigate: (PandaRoute) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val gesture by viewModel.gestureTelemetry.collectAsStateWithLifecycle()
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current
    var isReorderMode by remember { mutableStateOf(false) }

    val catalog: Map<String, QuickActionSpec> = mapOf(
        "FLASHLIGHT" to QuickActionSpec(
            id = "FLASHLIGHT",
            title = "Flashlight",
            subtitle = if (gesture.flashlightOn) "Torch ON" else "Tap to toggle",
            icon = Icons.Default.FlashlightOn,
            isActive = gesture.flashlightOn,
            onTrigger = {
                val ok = viewModel.container.actionManager.toggleFlashlight()
                viewModel.container.automationEngine.refreshSystemStatus()
                if (!ok) Toast.makeText(context, "Flashlight unavailable on this hardware", Toast.LENGTH_SHORT).show()
            }
        ),
        "DND" to QuickActionSpec(
            id = "DND",
            title = "Do Not Disturb",
            subtitle = if (gesture.dndActive) "DND Active" else "Priority Mode",
            icon = Icons.Default.DoNotDisturbOn,
            isActive = gesture.dndActive,
            onTrigger = {
                if (!viewModel.container.actionManager.isDndPolicyGranted()) {
                    viewModel.container.actionManager.openDndPolicySettings()
                } else {
                    val turningOn = !gesture.dndActive
                    val ok = viewModel.container.actionManager.setDndEnabled(turningOn)
                    if (ok) {
                        SystemUtils.triggerFlipDndVibration(context, isTurningOn = turningOn)
                    }
                    viewModel.container.automationEngine.refreshSystemStatus()
                }
            }
        ),
        "AQUA_DYNAMICS" to QuickActionSpec(
            id = "AQUA_DYNAMICS",
            title = "Aqua Dynamics",
            subtitle = if (settings.aquaDynamicsEnabled) "Capsule & Call Guard ON" else "Disabled",
            icon = Icons.Default.AutoAwesome,
            isActive = settings.aquaDynamicsEnabled,
            onTrigger = { onNavigate(PandaRoute.AquaDynamics) }
        ),
        "EDGE_GLOW" to QuickActionSpec(
            id = "EDGE_GLOW",
            title = "Edge Glow",
            subtitle = if (settings.edgeGlowEnabled) "Calls & Alerts ON" else "Configure Glow",
            icon = Icons.Default.AutoAwesome,
            isActive = settings.edgeGlowEnabled,
            onTrigger = { onNavigate(PandaRoute.EdgeGlow) }
        ),
        "AUTO_DIM" to QuickActionSpec(
            id = "AUTO_DIM",
            title = "Auto-Dim Apps",
            subtitle = if (settings.autoDimAppsEnabled) "Dim ${settings.autoDimTargetBrightnessPercent}% Active" else "Bank Privacy Dim",
            icon = Icons.Default.AccountBalance,
            isActive = settings.autoDimAppsEnabled,
            onTrigger = { onNavigate(PandaRoute.AutoDimApps) }
        ),
        "WIFI" to QuickActionSpec(
            id = "WIFI",
            title = "Wi-Fi Settings",
            subtitle = if (live.networkInfo?.wifiConnected == true) "Connected" else "Open Panel",
            icon = Icons.Default.Wifi,
            isActive = live.networkInfo?.wifiConnected == true,
            onTrigger = {
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_WIFI
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                SystemUtils.openSystemSettings(context, action)
            }
        ),
        "BLUETOOTH" to QuickActionSpec(
            id = "BLUETOOTH",
            title = "Bluetooth",
            subtitle = live.networkInfo?.bluetoothStateLabel ?: "Settings",
            icon = Icons.Default.Bluetooth,
            isActive = live.networkInfo?.bluetoothStateLabel?.startsWith("Enabled") == true,
            onTrigger = { SystemUtils.openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
        ),
        "HOTSPOT" to QuickActionSpec(
            id = "HOTSPOT",
            title = "Hotspot & Tethering",
            subtitle = "System Wireless",
            icon = Icons.Default.WifiTethering,
            onTrigger = { SystemUtils.openSystemSettings(context, Settings.ACTION_WIRELESS_SETTINGS) }
        ),
        "AIRPLANE" to QuickActionSpec(
            id = "AIRPLANE",
            title = "Airplane Mode",
            subtitle = "System Radio Control",
            icon = Icons.Default.AirplanemodeActive,
            onTrigger = { SystemUtils.openSystemSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
        ),
        "BATTERY" to QuickActionSpec(
            id = "BATTERY",
            title = "Battery Monitor",
            subtitle = "${live.battery?.percentage ?: 0}% • ${live.battery?.statusLabel ?: ""}",
            icon = Icons.Default.BatteryChargingFull,
            onTrigger = { onNavigate(PandaRoute.BatteryMonitor) }
        ),
        "HEALTH" to QuickActionSpec(
            id = "HEALTH",
            title = "Device Health",
            subtitle = "${live.overallHealth.scorePercent}% ${live.overallHealth.statusLabel}",
            icon = Icons.Default.Assessment,
            onTrigger = { onNavigate(PandaRoute.SystemHealthReport) }
        ),
        "NETWORK_DIAG" to QuickActionSpec(
            id = "NETWORK_DIAG",
            title = "Network Diagnostics",
            subtitle = "Ping & Speed Test",
            icon = Icons.Default.NetworkCheck,
            onTrigger = { onNavigate(PandaRoute.NetworkDiagnostics) }
        ),
        "QR_SCANNER" to QuickActionSpec(
            id = "QR_SCANNER",
            title = "QR & Barcode",
            subtitle = "Scan & Generate",
            icon = Icons.Default.QrCodeScanner,
            onTrigger = { onNavigate(PandaRoute.QrBarcodeToolkit) }
        ),
        "SENSOR_LAB" to QuickActionSpec(
            id = "SENSOR_LAB",
            title = "Sensor Lab",
            subtitle = "Live Hardware",
            icon = Icons.Default.Sensors,
            onTrigger = { onNavigate(PandaRoute.SensorLab) }
        ),
        "DEV_TOOLS" to QuickActionSpec(
            id = "DEV_TOOLS",
            title = "Developer Toolbox",
            subtitle = "JSON, Hash, Base64",
            icon = Icons.Default.Code,
            onTrigger = { onNavigate(PandaRoute.DeveloperToolbox) }
        ),
        "SMART_ACTIONS" to QuickActionSpec(
            id = "SMART_ACTIONS",
            title = "Smart Actions",
            subtitle = "Gestures & Rules",
            icon = Icons.Default.Vibration,
            onTrigger = { onNavigate(PandaRoute.SmartActions) }
        )
    )

    val orderedIds = remember(settings.quickActionsOrder) {
        val saved = settings.quickActionsOrder.split(",").filter { catalog.containsKey(it) }
        val missing = catalog.keys.filter { it !in saved }
        saved + missing
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Quick Action Center",
            subtitle = if (isReorderMode) "Tap arrows to rearrange shortcuts" else "Customizable Device Control Tiles",
            onBack = onBack,
            actions = {
                IconButton(onClick = { isReorderMode = !isReorderMode }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rearrange Shortcuts",
                        tint = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        if (isReorderMode) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(orderedIds, key = { _, id -> id }) { idx, id ->
                    val spec = catalog[id] ?: return@itemsIndexed
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(spec.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(spec.title, style = MaterialTheme.typography.titleSmall)
                            }
                            Row {
                                IconButton(
                                    enabled = idx > 0,
                                    onClick = {
                                        val mutable = orderedIds.toMutableList()
                                        val tmp = mutable[idx - 1]
                                        mutable[idx - 1] = mutable[idx]
                                        mutable[idx] = tmp
                                        scope.launch { viewModel.prefs.setQuickActionsOrder(mutable) }
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                }
                                IconButton(
                                    enabled = idx < orderedIds.lastIndex,
                                    onClick = {
                                        val mutable = orderedIds.toMutableList()
                                        val tmp = mutable[idx + 1]
                                        mutable[idx + 1] = mutable[idx]
                                        mutable[idx] = tmp
                                        scope.launch { viewModel.prefs.setQuickActionsOrder(mutable) }
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val pairs = orderedIds.mapNotNull { catalog[it] }.chunked(2)
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(pairs) { _, rowSpecs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowSpecs.forEach { spec ->
                            AquaGlassCard(
                                modifier = Modifier.weight(1f),
                                glowAccent = spec.isActive,
                                onClick = spec.onTrigger
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            spec.icon,
                                            contentDescription = spec.title,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (spec.isActive) {
                                        StatusPill("ON", HealthCondition.NORMAL)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(spec.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    spec.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        if (rowSpecs.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// SECTION 8 — ADDITIONAL DEVICE UTILITIES (Uptime, Charging Info, Sound & Vibration, Display Shortcuts)
@Composable
fun DeviceUtilitiesScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val gesture by viewModel.gestureTelemetry.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    val elapsedRealtimeMs = SystemClock.elapsedRealtime()
    val deepSleepActiveMs = elapsedRealtimeMs - SystemClock.uptimeMillis()
    val bootTimeEpochMs = System.currentTimeMillis() - elapsedRealtimeMs

    val refreshRate = remember {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(WindowManager::class.java))?.defaultDisplay
        }
        display?.refreshRate?.roundToInt() ?: 60
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "System & Audio Utilities",
            subtitle = "Sound, Vibration, Uptime, Charging & Display Shortcuts",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sound & Vibration Controls
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Sound, Ringer & Vibration", icon = Icons.Default.VolumeUp)
                    KeyValueRow("Current Ringer Mode", gesture.ringerModeLabel)
                    KeyValueRow("Do Not Disturb", if (gesture.dndActive) "Enabled" else "Off")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val ok = viewModel.container.actionManager.setRingerModeSafe(AudioManager.RINGER_MODE_NORMAL)
                                viewModel.container.automationEngine.refreshSystemStatus()
                                if (!ok) viewModel.container.actionManager.openDndPolicySettings()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sound")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.container.actionManager.setRingerModeSafe(AudioManager.RINGER_MODE_VIBRATE)
                                viewModel.container.automationEngine.refreshSystemStatus()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Vibrate")
                        }
                        OutlinedButton(
                            onClick = {
                                val ok = viewModel.container.actionManager.setRingerModeSafe(AudioManager.RINGER_MODE_SILENT)
                                viewModel.container.automationEngine.refreshSystemStatus()
                                if (!ok) viewModel.container.actionManager.openDndPolicySettings()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Silent")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { SystemUtils.triggerHapticPulse(context, 180L) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Motor")
                        }
                        OutlinedButton(
                            onClick = { SystemUtils.openSystemSettings(context, Settings.ACTION_SOUND_SETTINGS) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Volume Panel")
                        }
                    }
                }
            }

            // Charging Information
            item {
                val bat = live.battery
                val sessionDuration = bat?.chargingStartedAtMs?.let {
                    SystemUtils.formatDurationMillis(System.currentTimeMillis() - it)
                } ?: "Not Currently Charging"

                AquaGlassCard {
                    SectionHeader(title = "Charging Telemetry", icon = Icons.Default.BatteryChargingFull)
                    KeyValueRow("Charging State", bat?.statusLabel ?: "N/A")
                    KeyValueRow("Battery Level", "${bat?.percentage ?: 0}%")
                    KeyValueRow("Power Source", bat?.chargingSource ?: "N/A")
                    KeyValueRow("Battery Temperature", bat?.temperatureCelsius?.let { "${it}°C" } ?: "N/A")
                    KeyValueRow("Observed Session Time", sessionDuration)
                    KeyValueRow(
                        "OS Charge Remaining Est.",
                        bat?.chargeTimeRemainingMs?.let { SystemUtils.formatDurationMillis(it) } ?: "Not Available"
                    )
                }
            }

            // Device Uptime & Boot Information
            item {
                AquaGlassCard {
                    SectionHeader(title = "Device Uptime & Sleep Telemetry", icon = Icons.Default.Timer)
                    KeyValueRow("Total System Uptime", SystemUtils.formatDurationMillis(elapsedRealtimeMs))
                    KeyValueRow("Active Awake Time", SystemUtils.formatDurationMillis(SystemClock.uptimeMillis()))
                    KeyValueRow("Deep Sleep Duration", SystemUtils.formatDurationMillis(deepSleepActiveMs))
                    KeyValueRow("Last System Boot", SystemUtils.formatTimestamp(bootTimeEpochMs))
                }
            }

            // Display Shortcuts
            item {
                AquaGlassCard {
                    SectionHeader(title = "Display & System Shortcuts", icon = Icons.Default.BrightnessMedium)
                    KeyValueRow("Active Refresh Rate", "$refreshRate Hz")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { SystemUtils.openSystemSettings(context, Settings.ACTION_DISPLAY_SETTINGS) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Brightness & Timeout")
                        }
                        OutlinedButton(
                            onClick = {
                                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    Settings.ACTION_NIGHT_DISPLAY_SETTINGS
                                } else {
                                    Settings.ACTION_DISPLAY_SETTINGS
                                }
                                SystemUtils.openSystemSettings(context, action)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dark / Eye Care")
                        }
                    }
                }
            }
        }
    }
}
