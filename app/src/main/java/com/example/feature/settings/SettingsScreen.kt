package com.example.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.core.common.SystemUtils
import com.example.core.navigation.PandaRoute
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: PandaMainViewModel,
    onNavigate: ((PandaRoute) -> Unit)? = null
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val gesture by viewModel.gestureTelemetry.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current

    val notifPermGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "Notification permission granted" else "Notification permission declined",
            Toast.LENGTH_SHORT
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "PandaNex Settings",
            subtitle = "Clean Aqua Dynamics, Monitoring, Alerts & Privacy"
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. General & Clean Aqua Dynamics Theme
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "General & Clean Aqua Dynamics", icon = Icons.Default.Palette)
                    Text("Application Theme (Persisted via DataStore):", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.DARK_AQUA,
                            onClick = { viewModel.setThemeMode(AppThemeMode.DARK_AQUA) },
                            label = { Text("Dark Aqua") },
                            modifier = Modifier.testTag("theme_dark_aqua")
                        )
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.LIGHT_AQUA,
                            onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT_AQUA) },
                            label = { Text("Light Aqua") },
                            modifier = Modifier.testTag("theme_light_aqua")
                        )
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.SYSTEM_DEFAULT,
                            onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM_DEFAULT) },
                            label = { Text("System") },
                            modifier = Modifier.testTag("theme_system")
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "Fluid Aqua Animations",
                        subtitle = "Enable smooth gauge transitions and wave animations",
                        checked = settings.animationsEnabled,
                        onCheckedChange = { viewModel.setAnimationsEnabled(it) }
                    )
                    SettingSwitchRow(
                        title = "Tactile Haptic Feedback",
                        subtitle = "Subtle vibration confirmation on smart gestures & QR scans",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) }
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.aqua_dynamics_master_enable),
                        subtitle = "Interactive camera-area capsule for performance, network & device events (suppressed during calls)",
                        checked = settings.aquaDynamicsEnabled,
                        onCheckedChange = { viewModel.setAquaDynamicsMasterEnabled(it) },
                        switchTestTag = "toggle_settings_aqua_dynamics"
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.aqua_fullscreen_hide_title),
                        subtitle = stringResource(R.string.aqua_fullscreen_hide_subtitle),
                        checked = settings.hideAquaDynamicsInFullScreen,
                        onCheckedChange = { viewModel.setHideAquaDynamicsInFullScreen(it) },
                        switchTestTag = "toggle_settings_hide_aqua_fullscreen"
                    )
                    if (onNavigate != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onNavigate(PandaRoute.AquaDynamics) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_settings_open_aqua_dynamics")
                        ) {
                            Text("Configure Aqua Dynamics Categories & Gestures")
                        }
                    }
                }
            }

            // 2. Monitoring Preferences
            item {
                AquaGlassCard {
                    SectionHeader(title = "Monitoring Intervals & Subsystems", icon = Icons.Default.Speed)
                    Text("CPU Telemetry Refresh Interval:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1000L to "1s", 1500L to "1.5s", 3000L to "3s", 5000L to "5s").forEach { (ms, lbl) ->
                            FilterChip(
                                selected = settings.cpuRefreshMs == ms,
                                onClick = { viewModel.setCpuRefreshInterval(ms) },
                                label = { Text(lbl) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "Continuous Battery Telemetry",
                        subtitle = "Track charge level, thermistor temperature & voltage",
                        checked = settings.batteryMonitoringEnabled,
                        onCheckedChange = { viewModel.setBatteryMonitoring(it) }
                    )
                    SettingSwitchRow(
                        title = "PowerManager Thermal Monitoring",
                        subtitle = "Monitor Android thermal throttling states",
                        checked = settings.thermalMonitoringEnabled,
                        onCheckedChange = { viewModel.setThermalMonitoring(it) }
                    )
                }
            }

            // 3. Intelligent Alert Engine Preferences
            item {
                AquaGlassCard {
                    SectionHeader(title = "PandaNex Alert Engine", icon = Icons.Default.NotificationsActive)
                    if (!notifPermGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Notification Permission")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    SettingSwitchRow(
                        title = "CPU High-Load Alerts",
                        subtitle = "Trigger when CPU ≥ ${settings.cpuAlertThresholdPercent.toInt()}%",
                        checked = settings.cpuAlertEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateAlertCategory("CPU", it) } }
                    )
                    SettingSwitchRow(
                        title = "Battery & Temperature Alerts",
                        subtitle = "Low charge ≤ ${settings.lowBatteryThresholdPercent}% or Temp ≥ ${settings.highBatteryTempCelsius}°C",
                        checked = settings.batteryAlertEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateAlertCategory("BATTERY", it) } }
                    )
                    SettingSwitchRow(
                        title = "Charger Connect / Disconnect Notices",
                        subtitle = "Log power transitions",
                        checked = settings.chargingAlertsEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateAlertCategory("CHARGING", it) } }
                    )
                    SettingSwitchRow(
                        title = "Thermal Overheat Warnings",
                        subtitle = "Notify on Moderate, Severe, or Critical thermal status",
                        checked = settings.thermalAlertEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateAlertCategory("THERMAL", it) } }
                    )
                    SettingSwitchRow(
                        title = "RAM Pressure & Storage Alerts",
                        subtitle = "RAM ≥ ${settings.lowRamThresholdPercent.toInt()}% or Storage ≥ ${settings.lowStorageThresholdPercent.toInt()}%",
                        checked = settings.ramAlertEnabled,
                        onCheckedChange = {
                            scope.launch {
                                viewModel.prefs.updateAlertCategory("RAM", it)
                                viewModel.prefs.updateAlertCategory("STORAGE", it)
                            }
                        }
                    )
                    SettingSwitchRow(
                        title = "Network Connectivity Alerts",
                        subtitle = "Offline / Online recovery notifications",
                        checked = settings.networkAlertEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateAlertCategory("NETWORK", it) } }
                    )
                    SettingSwitchRow(
                        title = "Smart Action Execution Alerts",
                        subtitle = "Notify when gestures or rules run",
                        checked = settings.smartActionAlertEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateAlertCategory("SMART_ACTION", it) } }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "Alert Sound",
                        subtitle = "Play system notification sound for non-silent alerts",
                        checked = settings.alertSoundEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.setAlertBehavior(sound = it) } }
                    )
                    SettingSwitchRow(
                        title = "Quiet Hours (23:00 – 07:00)",
                        subtitle = "Suppress non-critical notifications overnight",
                        checked = settings.quietHoursEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.setAlertBehavior(quietHours = it) } }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Alert Cooldown Deduplication: ${settings.alertCooldownSeconds} seconds",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.alertCooldownSeconds.toFloat(),
                        onValueChange = { sec ->
                            scope.launch { viewModel.prefs.setAlertBehavior(cooldownSeconds = sec.toInt()) }
                        },
                        valueRange = 30f..600f
                    )
                }
            }

            // 4. Smart Actions Quick Summary
            item {
                AquaGlassCard {
                    SectionHeader(title = "Smart Actions & Background Engine", icon = Icons.Default.Bolt)
                    SettingSwitchRow(
                        title = "Foreground Gesture Service",
                        subtitle = "Keep Raise to Wake, Call Controls, Put Down to Sleep, Dimmed App & Screen Saver active in background",
                        checked = settings.backgroundMonitoringEnabled,
                        onCheckedChange = { viewModel.setBackgroundGestureService(it) }
                    )
                    KeyValueRow("DND Policy Access", if (gesture.dndPolicyGranted) "Granted" else "Not Granted")
                    KeyValueRow("Shake Sensitivity", "${String.format("%.1f", settings.shakeSensitivity)} m/s²")
                    KeyValueRow("Flip Detection Delay", "${settings.flipDelayMs} ms")
                    if (onNavigate != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onNavigate(PandaRoute.SmartGestures) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_settings_open_smart_gestures")
                            ) {
                                Text("Smart Gestures")
                            }
                            OutlinedButton(
                                onClick = { onNavigate(PandaRoute.SmartScreenSaver) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_settings_open_screen_saver")
                            ) {
                                Text("Screen Saver")
                            }
                        }
                    }
                }
            }

            // 5. Privacy, Permissions & Local Data Management
            item {
                AquaGlassCard {
                    SectionHeader(title = "Privacy, Security & Local Data", icon = Icons.Default.PrivacyTip)
                    Text(
                        "100% Local-First Architecture: All diagnostic histories, sensor logs, and alert records remain strictly on your device in Room SQLite storage. No root access, hidden APIs, or cloud telemetry uploads are used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearAllDiagnosticHistory()
                                Toast.makeText(context, "Diagnostic & metric histories cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Metrics")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearAllNotifications()
                                Toast.makeText(context, "Alert history cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Alerts")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { SystemUtils.openAppDetailsSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage Android Permissions for PandaNex")
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF010101))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_pandanex_brand_full),
                            contentDescription = "PandaNex — Unlock More From Your Phone",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    KeyValueRow("Application", "PandaNex v${BuildConfig.VERSION_NAME}")
                    KeyValueRow("Design System", "Clean Aqua Dynamics")
                    KeyValueRow("Storage Policy", "Strictly Read-Only")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier
        )
    }
}
