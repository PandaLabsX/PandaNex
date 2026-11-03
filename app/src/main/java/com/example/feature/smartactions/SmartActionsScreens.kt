package com.example.feature.smartactions

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.data.local.database.AutomationRuleEntity
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch

@Composable
fun SmartActionsScreen(
    viewModel: PandaMainViewModel,
    onNavigate: (PandaRoute) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val gesture by viewModel.gestureTelemetry.collectAsStateWithLifecycle()
    val quickCapture by viewModel.quickCaptureState.collectAsStateWithLifecycle()
    val aquaDynamics by viewModel.aquaDynamicsState.collectAsStateWithLifecycle()
    val rules by viewModel.automationRules.collectAsStateWithLifecycle()
    val events by viewModel.automationEvents.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setBackgroundGestureService(true)
    }

    // Keep interactive sensor monitoring responsive while user is on Smart Actions screen
    DisposableEffect(Unit) {
        viewModel.container.automationEngine.startForegroundInteractiveMonitoring()
        viewModel.container.quickCaptureController.setActivityForeground(true)
        onDispose {
            viewModel.container.automationEngine.stopForegroundInteractiveMonitoring()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "PandaNex Smart Actions",
            subtitle = "Motion Gestures, Call Controls, Screen Saver, Aqua Dynamics & Dimmed App"
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Orientation & Proximity Telemetry Card
            item {
                AquaGlassCard(glowAccent = true, modifier = Modifier.testTag("smart_live_sensor_card")) {
                    SectionHeader(title = "Live Motion & Orientation Telemetry", icon = Icons.Default.Sensors)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusPill(
                            label = "Orientation: ${gesture.orientation.label}",
                            condition = HealthCondition.INFO
                        )
                        StatusPill(
                            label = if (gesture.proximitySupported) {
                                if (gesture.proximityNear) "Proximity: NEAR" else "Proximity: FAR"
                            } else "Proximity: N/A",
                            condition = if (gesture.proximityNear) HealthCondition.WARNING else HealthCondition.NORMAL
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    KeyValueRow("Ringer State", gesture.ringerModeLabel)
                    KeyValueRow("Do Not Disturb", if (gesture.dndActive) "Active" else "Off")
                    KeyValueRow("Camera Flashlight", if (gesture.flashlightOn) "ON" else "OFF")
                    KeyValueRow("Last Gesture Event", gesture.lastGestureLabel, monoValue = false)
                }
            }

            // Aqua Dynamics Interactive Capsule & Event System Card
            item {
                AquaGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_open_aqua_dynamics_card"),
                    glowAccent = settings.aquaDynamicsEnabled,
                    onClick = { onNavigate(PandaRoute.AquaDynamics) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Aqua Dynamics",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Aqua Dynamics Capsule", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Drag-to-Camera Minimize • Call Guard • Smart Event System",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.aquaDynamicsEnabled,
                            onCheckedChange = { viewModel.setAquaDynamicsMasterEnabled(it) },
                            modifier = Modifier.testTag("smart_aqua_dynamics_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = when {
                                !settings.aquaDynamicsEnabled -> "Disabled"
                                aquaDynamics.isSuppressedByCall -> "Suppressed (Active Call)"
                                else -> "Mode: ${aquaDynamics.displayMode.name}"
                            },
                            condition = if (settings.aquaDynamicsEnabled) HealthCondition.NORMAL else HealthCondition.INFO
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Configure Events & Gesture",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Edge Glow (Calls & Notifications) & Auto-Dimmed Apps (Banking & Privacy) Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AquaGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("smart_open_edge_glow_card"),
                        glowAccent = settings.edgeGlowEnabled,
                        onClick = { onNavigate(PandaRoute.EdgeGlow) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Edge Glow",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            StatusPill(
                                label = if (settings.edgeGlowEnabled) "ON" else "Setup",
                                condition = if (settings.edgeGlowEnabled) HealthCondition.NORMAL else HealthCondition.INFO
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Glow Edges", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Calls & Notification Bezel Lighting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Customize",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    AquaGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("smart_open_auto_dim_card"),
                        glowAccent = settings.autoDimAppsEnabled,
                        onClick = { onNavigate(PandaRoute.AutoDimApps) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = "Auto-Dimmed Apps",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            StatusPill(
                                label = if (settings.autoDimAppsEnabled) "${settings.autoDimIntensityPercent}%" else "Setup",
                                condition = if (settings.autoDimAppsEnabled) HealthCondition.NORMAL else HealthCondition.INFO
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Dimmed App", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "10%–90% Privacy Brightness Shield",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${settings.autoDimPackagesSet.size} Apps",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Smart Actions & Gestures Control Centre (Raise to Wake, Pick Up to Silence, Proximity Answer, Put Down to Sleep, Dimmed App, Smart Screen Saver)
            item {
                SmartActionsAndGesturesSection(
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    showDedicatedPageLink = true
                )
            }

            // Moto-Style Quick Capture: Shake to Camera Card
            item {
                ShakeToCameraCard(
                    state = quickCapture,
                    animationsEnabled = settings.animationsEnabled,
                    onToggleEnabled = { enabled ->
                        viewModel.setShakeToCameraEnabled(enabled)
                    },
                    onSelectSensitivity = { sens ->
                        viewModel.setShakeToCameraSensitivity(sens)
                    },
                    onToggleVibration = { vibEnabled ->
                        viewModel.setShakeToCameraVibration(vibEnabled)
                    },
                    onTestGesture = {
                        val (_, message) = viewModel.testShakeToCameraGesture()
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // DND Policy Access Banner if needed
            if (!gesture.dndPolicyGranted) {
                item {
                    AquaGlassCard {
                        SectionHeader(title = "Do Not Disturb Policy Access", icon = Icons.Default.DoNotDisturbOn)
                        Text(
                            "Android requires Notification Policy Access for Flip to DND.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.container.actionManager.openDndPolicySettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant DND Policy Permission")
                        }
                    }
                }
            }

            // Exclusive Flip Phone Action: ONLY Flip to DND (2x vibrate ON, 1x vibrate OFF)
            item {
                AquaGlassCard {
                    SectionHeader(title = "Flip Phone — Exclusive Flip to DND", icon = Icons.Default.DoNotDisturbOn)
                    SmartToggleRow(
                        title = "Flip to DND (Face Down)",
                        subtitle = "Only DND is triggered when flipping face-down • Vibrates 2× for ON, 1× alone for OFF",
                        checked = settings.flipToDndEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(flipToDnd = enabled) }
                        },
                        tag = "toggle_flip_dnd"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SmartToggleRow(
                        title = "Turn DND OFF on Flip Up",
                        subtitle = "Automatically turn Flip-to-DND OFF (with 1 single vibration) when lifted face-up",
                        checked = settings.flipRestorePrevious,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(flipRestore = enabled) }
                        },
                        tag = "toggle_flip_restore"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Haptic Signature: DND ON = 2× Vibrate  •  DND OFF = 1× Vibrate Alone",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Face-Down Detection Delay: ${settings.flipDelayMs} ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.flipDelayMs.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(flipDelayMs = v.toLong()) }
                        },
                        valueRange = 500f..5000f
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = viewModel.container.automationEngine.simulateTestGesture("FLIP_DND")
                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_flip_dnd_button")
                    ) {
                        Text("Test Flip to DND (2× ON / 1× OFF)")
                    }
                }
            }

            // D & E: Shake to Flashlight & Double Shake Shortcut
            item {
                AquaGlassCard {
                    SectionHeader(title = "Shake & Double-Shake Gestures", icon = Icons.Default.FlashlightOn)
                    SmartToggleRow(
                        title = "Shake to Flashlight",
                        subtitle = "Toggle rear LED camera torch with a brisk motion shake",
                        checked = settings.shakeFlashlightEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(shakeFlashlight = enabled) }
                        },
                        tag = "toggle_shake_flashlight"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Shake Sensitivity Threshold: ${String.format("%.1f", settings.shakeSensitivity)} m/s²",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.shakeSensitivity,
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(shakeSensitivity = v) }
                        },
                        valueRange = 8.5f..22f
                    )

                    Text(
                        "Gesture Cooldown: ${settings.gestureCooldownMs} ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.gestureCooldownMs.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(gestureCooldownMs = v.toLong()) }
                        },
                        valueRange = 1000f..8000f
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Double-Shake Shortcut Assignment:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf(
                            "NONE" to "Disabled",
                            "FLASHLIGHT" to "Toggle Torch",
                            "DND_TOGGLE" to "Toggle DND",
                            "VIBRATE_MODE" to "Vibrate Mode"
                        )
                        items(options) { (code, label) ->
                            FilterChip(
                                selected = settings.doubleShakeAction == code,
                                onClick = {
                                    scope.launch { viewModel.prefs.updateSmartActionSettings(doubleShakeAction = code) }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = viewModel.container.automationEngine.simulateTestGesture("SHAKE_TORCH")
                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Flashlight Toggle")
                    }
                }
            }

            // F & G: Pickup Detection & Proximity Pocket Guard + Foreground Service
            item {
                AquaGlassCard {
                    SectionHeader(title = "Pickup, Proximity & Background Service", icon = Icons.Default.Bolt)
                    SmartToggleRow(
                        title = "Pickup Movement Detection",
                        subtitle = "Detect lifting phone from flat surface & trigger Pickup rules",
                        checked = settings.pickupDetectionEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(pickupDetection = enabled) }
                        },
                        tag = "toggle_pickup"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SmartToggleRow(
                        title = "Proximity Pocket Guard",
                        subtitle = "Prevent accidental Shake gestures when proximity sensor detects pocket/bag",
                        checked = settings.proximityPocketGuardEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateSmartActionSettings(proximityPocketGuard = enabled) }
                        },
                        tag = "toggle_pocket_guard"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SmartToggleRow(
                        title = "Active Background Gesture Service",
                        subtitle = "Runs a visible foreground notification so gestures work while app is minimized",
                        checked = settings.backgroundMonitoringEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setBackgroundGestureService(enabled)
                            }
                        },
                        tag = "toggle_bg_gesture_service"
                    )
                }
            }

            // J: Automation Builder (WHEN -> IF -> THEN) Entry & Active Rules
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "WHEN → IF → THEN Automation Rules (${rules.size})", icon = Icons.Default.AutoAwesome)
                    Text(
                        "Build custom multi-condition smart rules combining motion, charging, battery, and network triggers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onNavigate(PandaRoute.AutomationBuilder) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_automation_builder_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open WHEN → IF → THEN Rule Builder")
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "WHEN ${rule.triggerType} • IF ${rule.conditionType} → THEN ${rule.actionType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Triggered ${rule.triggerCount} times",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { viewModel.toggleAutomationRule(rule, it) }
                        )
                        IconButton(onClick = { viewModel.deleteAutomationRule(rule.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Rule")
                        }
                    }
                }
            }

            // Automation Execution History
            if (events.isNotEmpty()) {
                item {
                    SectionHeader(title = "Automation Execution History", icon = Icons.Default.History)
                }
                items(events.take(10)) { ev ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ev.ruleName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${ev.triggerDescription} → ${ev.actionDescription}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(SystemUtils.formatTimestamp(ev.timestamp), style = MaterialTheme.typography.labelSmall)
                            }
                            StatusPill(
                                label = if (ev.succeeded) "OK" else "Blocked",
                                condition = if (ev.succeeded) HealthCondition.NORMAL else HealthCondition.WARNING
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            modifier = Modifier.testTag(tag)
        )
    }
}

// AUTOMATION BUILDER SCREEN (WHEN -> IF -> THEN)
@Composable
fun AutomationBuilderScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val rules by viewModel.automationRules.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    var ruleName by remember { mutableStateOf("") }
    var selectedTrigger by remember { mutableStateOf("FACE_DOWN") }
    var triggerParam by remember { mutableStateOf("25") }
    var selectedCondition by remember { mutableStateOf("ALWAYS") }
    var selectedAction by remember { mutableStateOf("ENABLE_DND") }
    var actionParam by remember { mutableStateOf("") }

    val triggers = listOf(
        "FACE_DOWN" to "Face Down",
        "FACE_UP" to "Face Up",
        "SHAKE" to "Shake Motion",
        "PICKUP" to "Phone Pickup",
        "CHARGING_CONNECTED" to "Charger Connected",
        "CHARGING_DISCONNECTED" to "Charger Disconnected",
        "BATTERY_BELOW" to "Battery Below %",
        "WIFI_CONNECTED" to "Wi-Fi Connected",
        "WIFI_DISCONNECTED" to "Wi-Fi Disconnected",
        "PROXIMITY_NEAR" to "Proximity Near"
    )

    val conditions = listOf(
        "ALWAYS" to "Always",
        "WHEN_CHARGING" to "Only While Charging",
        "WHEN_NOT_CHARGING" to "Only On Battery",
        "WHEN_BATTERY_BELOW_50" to "If Battery < 50%",
        "WHEN_WIFI_CONNECTED" to "If Connected to Wi-Fi"
    )

    val allActions = listOf(
        "ENABLE_DND" to "Enable DND",
        "DISABLE_DND" to "Disable DND",
        "RINGER_SILENT" to "Silent Mode",
        "RINGER_VIBRATE" to "Vibrate Mode",
        "RINGER_NORMAL" to "Normal Sound",
        "TOGGLE_FLASHLIGHT" to "Toggle Flashlight",
        "SHOW_NOTIFICATION" to "Show Notification",
        "SHOW_REMINDER" to "Display Reminder",
        "OPEN_APP" to "Open App (Package)"
    )

    // For phone flipping (FACE_DOWN / FACE_UP), strictly allow DND actions only
    val actions = if (selectedTrigger == "FACE_DOWN" || selectedTrigger == "FACE_UP") {
        allActions.filter { it.first == "ENABLE_DND" || it.first == "DISABLE_DND" }
    } else {
        allActions
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Automation Builder",
            subtitle = "WHEN → IF → THEN Rule Composer",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Create New Smart Rule", icon = Icons.Default.AutoAwesome)
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Rule Name (e.g. Meeting Flip DND)") }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("1. WHEN (Select Trigger):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(triggers) { (code, label) ->
                            FilterChip(
                                selected = selectedTrigger == code,
                                onClick = {
                                    selectedTrigger = code
                                    if (code == "FACE_DOWN") {
                                        selectedAction = "ENABLE_DND"
                                    } else if (code == "FACE_UP") {
                                        selectedAction = "DISABLE_DND"
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (selectedTrigger == "BATTERY_BELOW") {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = triggerParam,
                            onValueChange = { triggerParam = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Battery Percentage Threshold (e.g. 25)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("2. IF (Guard Condition):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(conditions) { (code, label) ->
                            FilterChip(
                                selected = selectedCondition == code,
                                onClick = { selectedCondition = code },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("3. THEN (Execute Action):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(actions) { (code, label) ->
                            FilterChip(
                                selected = selectedAction == code,
                                onClick = { selectedAction = code },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (selectedAction in listOf("SHOW_NOTIFICATION", "SHOW_REMINDER", "OPEN_APP")) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = actionParam,
                            onValueChange = { actionParam = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = {
                                Text(
                                    if (selectedAction == "OPEN_APP") "Target Package Name (e.g. com.android.settings)"
                                    else "Custom Notification / Reminder Message"
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val finalName = ruleName.trim().ifBlank { "$selectedTrigger → $selectedAction" }
                            viewModel.saveAutomationRule(
                                AutomationRuleEntity(
                                    name = finalName,
                                    triggerType = selectedTrigger,
                                    triggerParam = triggerParam,
                                    conditionType = selectedCondition,
                                    actionType = selectedAction,
                                    actionParam = actionParam,
                                    isEnabled = true
                                )
                            )
                            ruleName = ""
                            actionParam = ""
                            Toast.makeText(context, "Automation rule saved & activated", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_automation_rule_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Enable Rule")
                    }
                }
            }

            item { SectionHeader(title = "Configured Rules (${rules.size})") }
            items(rules, key = { it.id }) { rule ->
                AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "WHEN ${rule.triggerType} • IF ${rule.conditionType} → ${rule.actionType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { viewModel.toggleAutomationRule(rule, it) }
                        )
                        IconButton(onClick = { viewModel.deleteAutomationRule(rule.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Rule")
                        }
                    }
                }
            }
        }
    }
}
