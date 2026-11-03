package com.example.feature.smartactions

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.EdgesensorHigh
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.navigation.PandaRoute
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch

@Composable
fun SmartGesturesControlScreen(
    viewModel: PandaMainViewModel,
    onNavigate: (PandaRoute) -> Unit,
    onBack: () -> Unit
) {
    val aqua = LocalAquaColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.container.smartGestureManager.refreshStateAndListeners()
                viewModel.container.autoDimController.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Smart Actions & Gestures",
            subtitle = "Motion Wake, Call Controls, Flat Sleep, Dimmed App & Screen Saver",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.testTag("smart_gestures_control_list")
        ) {
            item {
                SmartActionsAndGesturesSection(
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    showDedicatedPageLink = false
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartActionsAndGesturesSection(
    viewModel: PandaMainViewModel,
    onNavigate: (PandaRoute) -> Unit,
    showDedicatedPageLink: Boolean = true
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hub by viewModel.smartGestureState.collectAsStateWithLifecycle()
    val dimStatus by viewModel.autoDimStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var expandedFeatureKey by remember { mutableStateOf<String?>(null) }

    val notifServiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setBackgroundGestureService(true)
    }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.container.smartGestureManager.refreshStateAndListeners()
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.container.smartGestureManager.refreshStateAndListeners()
    }

    fun ensureBackgroundServiceIfNeeded() {
        if (!settings.backgroundMonitoringEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifServiceLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setBackgroundGestureService(true)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("smart_gestures_hub_section")
    ) {
        // Sensor Availability & Arbitration Banner Card
        AquaGlassCard(glowAccent = true) {
            SectionHeader(
                title = "Smart Actions & Gestures",
                icon = Icons.Default.EdgesensorHigh,
                trailingText = if (showDedicatedPageLink) "Full View" else null,
                onTrailingClick = if (showDedicatedPageLink) {
                    { onNavigate(PandaRoute.SmartGestures) }
                } else null
            )
            Text(
                text = "Moto-inspired motion gestures, proximity call intelligence, privacy app dimming & motion screen saver — engineered with conflict-free sensor arbitration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    label = if (hub.hasAccelerometer) "Accel: Ready" else "Accel: Missing",
                    condition = if (hub.hasAccelerometer) HealthCondition.NORMAL else HealthCondition.CRITICAL
                )
                StatusPill(
                    label = if (hub.hasGyroscope) "Gyro: Ready" else "Gyro: Fallback",
                    condition = if (hub.hasGyroscope) HealthCondition.NORMAL else HealthCondition.INFO
                )
                StatusPill(
                    label = if (hub.hasProximitySensor) "Proximity: Ready" else "Proximity: Missing",
                    condition = if (hub.hasProximitySensor) HealthCondition.NORMAL else HealthCondition.WARNING
                )
                StatusPill(
                    label = if (hub.backgroundServiceActive) "BG Service: Active" else "BG Service: Off",
                    condition = if (hub.backgroundServiceActive) HealthCondition.NORMAL else HealthCondition.INFO
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hub.lastTriggeredSummary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 1. Raise to Wake
        GestureFeatureControlCard(
            title = "Raise to Wake",
            explanation = "Automatically wakes the display when you lift your phone from a resting position while respecting lock-screen security.",
            icon = Icons.Default.ScreenLockPortrait,
            enabled = settings.raiseToWakeEnabled,
            sensorAvailable = hub.hasAccelerometer,
            sensorLabel = if (hub.hasAccelerometer) {
                if (hub.hasGyroscope) "Accel + Gyro Ready" else "Accelerometer Ready"
            } else "Accelerometer Unavailable",
            statusText = hub.raiseToWakeStatus,
            statusCondition = if (settings.raiseToWakeEnabled && hub.hasAccelerometer) HealthCondition.NORMAL else HealthCondition.INFO,
            switchTestTag = "toggle_raise_to_wake",
            settingsButtonTag = "settings_btn_raise_to_wake",
            isSettingsExpanded = expandedFeatureKey == "RAISE_TO_WAKE",
            onToggle = { enabled ->
                if (!hub.hasAccelerometer) {
                    Toast.makeText(context, "Accelerometer not available on this device", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        viewModel.prefs.updateSmartMotionAndCallSettings(raiseToWakeEnabled = enabled)
                        if (enabled) ensureBackgroundServiceIfNeeded()
                    }
                }
            },
            onSettingsClick = {
                expandedFeatureKey = if (expandedFeatureKey == "RAISE_TO_WAKE") null else "RAISE_TO_WAKE"
            }
        ) {
            SensitivitySelectorRow(
                selected = settings.raiseToWakeSensitivity,
                onSelect = { code ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(raiseToWakeSensitivity = code) }
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Wake Cooldown: ${String.format("%.1f", settings.raiseToWakeCooldownMs / 1000f)}s",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = settings.raiseToWakeCooldownMs.toFloat(),
                onValueChange = { ms ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(raiseToWakeCooldownMs = ms.toLong()) }
                },
                valueRange = 1500f..10000f
            )
            InlineSubSwitchRow(
                label = "Vibration Feedback on Wake",
                checked = settings.raiseToWakeVibrationEnabled,
                onCheckedChange = { v ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(raiseToWakeVibration = v) }
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    val (_, msg) = viewModel.container.smartGestureManager.triggerRaiseToWakeDiagnosticTest()
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Raise-to-Wake Pulse")
            }
        }

        // 2. Pick Up to Silence Calls
        GestureFeatureControlCard(
            title = "Pick Up to Silence",
            explanation = "Silences an incoming call's ringtone as soon as you lift the ringing phone — without rejecting or disconnecting the call.",
            icon = Icons.Default.VolumeOff,
            enabled = settings.pickUpToSilenceEnabled,
            sensorAvailable = hub.hasAccelerometer,
            sensorLabel = if (hub.hasReadPhoneStatePermission) "Accel + Phone State Ready" else "Phone Permission Needed",
            statusText = hub.pickUpToSilenceStatus,
            statusCondition = when {
                !settings.pickUpToSilenceEnabled -> HealthCondition.INFO
                !hub.hasReadPhoneStatePermission -> HealthCondition.WARNING
                else -> HealthCondition.NORMAL
            },
            switchTestTag = "toggle_pick_up_silence",
            settingsButtonTag = "settings_btn_pick_up_silence",
            isSettingsExpanded = expandedFeatureKey == "PICK_UP_SILENCE",
            onToggle = { enabled ->
                scope.launch {
                    viewModel.prefs.updateSmartMotionAndCallSettings(pickUpToSilenceEnabled = enabled)
                    if (enabled) {
                        ensureBackgroundServiceIfNeeded()
                        if (!hub.hasReadPhoneStatePermission) {
                            callPermissionsLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                        }
                    }
                }
            },
            onSettingsClick = {
                expandedFeatureKey = if (expandedFeatureKey == "PICK_UP_SILENCE") null else "PICK_UP_SILENCE"
            }
        ) {
            if (!hub.hasReadPhoneStatePermission) {
                Button(
                    onClick = { callPermissionsLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Phone State Permission")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            SensitivitySelectorRow(
                selected = settings.pickUpToSilenceSensitivity,
                onSelect = { code ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(pickUpToSilenceSensitivity = code) }
                }
            )
            InlineSubSwitchRow(
                label = "Subtle Vibration Confirmation",
                checked = settings.pickUpToSilenceVibrationEnabled,
                onCheckedChange = { v ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(pickUpToSilenceVibration = v) }
                }
            )
            Text(
                text = "Note: Filters out table vibration buzz by verifying genuine 3D tilt & lift before silencing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3. Proximity Call Answer (Bring Phone Near Ear)
        GestureFeatureControlCard(
            title = "Proximity Call Answer",
            explanation = "Answers an incoming ringing call when you lift the phone upright and hold it near your ear. Automatically pauses on Bluetooth/wired headsets & Android Auto.",
            icon = Icons.Default.PhoneCallback,
            enabled = settings.proximityCallAnswerEnabled,
            sensorAvailable = hub.hasProximitySensor,
            sensorLabel = when {
                !hub.hasProximitySensor -> "No Proximity Sensor"
                !hub.hasReadPhoneStatePermission || !hub.hasAnswerPhoneCallsPermission -> "Call Permissions Needed"
                hub.externalAudioConnected -> "Headset/Car Paused"
                else -> "Proximity + Posture Ready"
            },
            statusText = hub.proximityAnswerStatus,
            statusCondition = when {
                !hub.hasProximitySensor -> HealthCondition.WARNING
                !settings.proximityCallAnswerEnabled -> HealthCondition.INFO
                !hub.hasReadPhoneStatePermission || !hub.hasAnswerPhoneCallsPermission -> HealthCondition.WARNING
                else -> HealthCondition.NORMAL
            },
            switchTestTag = "toggle_proximity_answer",
            settingsButtonTag = "settings_btn_proximity_answer",
            isSettingsExpanded = expandedFeatureKey == "PROXIMITY_ANSWER",
            onToggle = { enabled ->
                if (!hub.hasProximitySensor) {
                    Toast.makeText(context, "Proximity sensor not available on this device", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        viewModel.prefs.updateSmartMotionAndCallSettings(proximityCallAnswerEnabled = enabled)
                        if (enabled) {
                            ensureBackgroundServiceIfNeeded()
                            if (!hub.hasReadPhoneStatePermission || !hub.hasAnswerPhoneCallsPermission) {
                                callPermissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.ANSWER_PHONE_CALLS
                                    )
                                )
                            }
                        }
                    }
                }
            },
            onSettingsClick = {
                expandedFeatureKey = if (expandedFeatureKey == "PROXIMITY_ANSWER") null else "PROXIMITY_ANSWER"
            }
        ) {
            if (!hub.hasReadPhoneStatePermission || !hub.hasAnswerPhoneCallsPermission) {
                Button(
                    onClick = {
                        callPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.ANSWER_PHONE_CALLS
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grant Phone & Answer Calls Permissions")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "Near-Ear Confirmation Delay: ${settings.proximityAnswerDelayMs} ms",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = settings.proximityAnswerDelayMs.toFloat(),
                onValueChange = { ms ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(proximityAnswerDelayMs = ms.toLong()) }
                },
                valueRange = 400f..2500f
            )
            InlineSubSwitchRow(
                label = "Vibration Confirmation on Answer",
                checked = settings.proximityAnswerVibrationEnabled,
                onCheckedChange = { v ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(proximityAnswerVibration = v) }
                }
            )
            Text(
                text = "Android Compatibility: Works on cellular/Telecom-managed calls via TelecomManager.acceptRingingCall(). Third-party VoIP apps that do not integrate with Android Telecom cannot be auto-answered by non-system apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 4. Put Down to Sleep (Flat Surface Display Off)
        GestureFeatureControlCard(
            title = "Put Down to Sleep",
            explanation = "Turns off and locks the display when you place your phone stably onto a flat desk or table after active handheld use.",
            icon = Icons.Default.PhonelinkLock,
            enabled = settings.putDownToSleepEnabled,
            sensorAvailable = hub.hasAccelerometer,
            sensorLabel = when {
                !hub.hasAccelerometer -> "No Accelerometer"
                hub.isDeviceAdminActive -> "Instant Admin Lock Active"
                hub.canWriteSystemSettings -> "10s Timeout Fallback"
                else -> "Setup Screen-Lock Admin"
            },
            statusText = hub.putDownToSleepStatus,
            statusCondition = when {
                !settings.putDownToSleepEnabled -> HealthCondition.INFO
                hub.isDeviceAdminActive || hub.canWriteSystemSettings -> HealthCondition.NORMAL
                else -> HealthCondition.WARNING
            },
            switchTestTag = "toggle_put_down_sleep",
            settingsButtonTag = "settings_btn_put_down_sleep",
            isSettingsExpanded = expandedFeatureKey == "PUT_DOWN_SLEEP",
            onToggle = { enabled ->
                scope.launch {
                    viewModel.prefs.updateSmartMotionAndCallSettings(putDownToSleepEnabled = enabled)
                    if (enabled) {
                        ensureBackgroundServiceIfNeeded()
                        if (!hub.isDeviceAdminActive) {
                            expandedFeatureKey = "PUT_DOWN_SLEEP"
                        }
                    }
                }
            },
            onSettingsClick = {
                expandedFeatureKey = if (expandedFeatureKey == "PUT_DOWN_SLEEP") null else "PUT_DOWN_SLEEP"
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hub.isDeviceAdminActive) {
                    Button(
                        onClick = {
                            val intent = viewModel.container.displayActionManager.buildAuthorizeDeviceAdminIntent()
                            deviceAdminLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_authorize_device_admin")
                    ) {
                        Text("Authorize Instant Lock")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.container.displayActionManager.revokeDeviceAdmin()
                            viewModel.container.smartGestureManager.refreshStateAndListeners()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Revoke Lock Admin")
                    }
                }

                if (!hub.canWriteSystemSettings) {
                    OutlinedButton(
                        onClick = { SystemUtils.openWriteSettingsPermission(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("10s Fallback")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SensitivitySelectorRow(
                selected = settings.putDownToSleepSensitivity,
                onSelect = { code ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(putDownToSleepSensitivity = code) }
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Flat Surface Orientation:", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "BOTH" to "Screen-Up & Down",
                    "FACE_DOWN" to "Screen-Down Only",
                    "FACE_UP" to "Screen-Up Only"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = settings.putDownSurfaceMode == code,
                        onClick = {
                            scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(putDownSurfaceMode = code) }
                        },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Flat Stability Delay: ${String.format("%.1f", settings.putDownToSleepDelayMs / 1000f)}s",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = settings.putDownToSleepDelayMs.toFloat(),
                onValueChange = { ms ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(putDownToSleepDelayMs = ms.toLong()) }
                },
                valueRange = 800f..4500f
            )
            InlineSubSwitchRow(
                label = "Vibration Feedback Before Sleep",
                checked = settings.putDownToSleepVibrationEnabled,
                onCheckedChange = { v ->
                    scope.launch { viewModel.prefs.updateSmartMotionAndCallSettings(putDownToSleepVibration = v) }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    val (_, msg) = viewModel.container.smartGestureManager.triggerPutDownSleepDiagnosticTest()
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Put Down to Sleep")
            }
        }

        // 5. Dimmed App (Privacy & Eye-Comfort App Dimming)
        GestureFeatureControlCard(
            title = "Dimmed App",
            explanation = "Applies a configurable 10%–90% privacy dimming filter to selected banking or sensitive apps, automatically restoring brightness on exit.",
            icon = Icons.Default.BrightnessLow,
            enabled = settings.autoDimAppsEnabled,
            sensorAvailable = hub.hasUsageStatsAccess && (hub.canDrawOverlays || hub.canWriteSystemSettings),
            sensorLabel = if (hub.hasUsageStatsAccess && (hub.canDrawOverlays || hub.canWriteSystemSettings)) {
                "Overlay & Usage Ready"
            } else {
                "Permissions Needed"
            },
            statusText = when {
                !settings.autoDimAppsEnabled -> "Disabled • ${settings.autoDimPackagesSet.size} apps configured"
                dimStatus.isTemporarilyPaused -> "Paused (${dimStatus.pausedRemainingSeconds}s remaining)"
                dimStatus.isCurrentlyDimmed -> "Dimming Active (${settings.autoDimIntensityPercent}%)"
                else -> "Active • ${settings.activeAutoDimPackagesSet.size} app rules armed (${settings.autoDimIntensityPercent}%)"
            },
            statusCondition = when {
                !settings.autoDimAppsEnabled -> HealthCondition.INFO
                dimStatus.isTemporarilyPaused -> HealthCondition.WARNING
                hub.hasUsageStatsAccess && (hub.canDrawOverlays || hub.canWriteSystemSettings) -> HealthCondition.NORMAL
                else -> HealthCondition.WARNING
            },
            switchTestTag = "toggle_dimmed_app_hub",
            settingsButtonTag = "settings_btn_dimmed_app",
            isSettingsExpanded = false,
            onToggle = { enabled ->
                scope.launch {
                    viewModel.prefs.updateAutoDimSettings(enabled = enabled)
                    if (enabled) ensureBackgroundServiceIfNeeded()
                }
            },
            onSettingsClick = { onNavigate(PandaRoute.AutoDimApps) }
        ) {}

        // 6. Smart Screen Saver – Motion Activated
        GestureFeatureControlCard(
            title = "Smart Screen Saver",
            explanation = "Motion-activated ambient clock & status display with 7 Aqua styles when your screen is off, returning to sleep when set flat.",
            icon = Icons.Default.AutoAwesome,
            enabled = settings.screenSaverEnabled,
            sensorAvailable = hub.hasAccelerometer,
            sensorLabel = if (hub.canDrawOverlays) "Motion + Ambient Ready" else "Grant Overlay for Motion",
            statusText = hub.screenSaverStatus,
            statusCondition = when {
                !settings.screenSaverEnabled -> HealthCondition.INFO
                hub.canDrawOverlays && hub.hasAccelerometer -> HealthCondition.NORMAL
                else -> HealthCondition.WARNING
            },
            switchTestTag = "toggle_smart_screen_saver_hub",
            settingsButtonTag = "settings_btn_smart_screen_saver",
            isSettingsExpanded = false,
            onToggle = { enabled ->
                scope.launch {
                    viewModel.prefs.updateScreenSaverSettings(enabled = enabled)
                    if (enabled) ensureBackgroundServiceIfNeeded()
                }
            },
            onSettingsClick = { onNavigate(PandaRoute.SmartScreenSaver) }
        ) {}
    }
}

@Composable
private fun GestureFeatureControlCard(
    title: String,
    explanation: String,
    icon: ImageVector,
    enabled: Boolean,
    sensorAvailable: Boolean,
    sensorLabel: String,
    statusText: String,
    statusCondition: HealthCondition,
    switchTestTag: String,
    settingsButtonTag: String,
    isSettingsExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    AquaGlassCard(glowAccent = enabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag(switchTestTag)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(label = sensorLabel, condition = if (sensorAvailable) HealthCondition.NORMAL else HealthCondition.WARNING)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (statusCondition == HealthCondition.WARNING) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = onSettingsClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag(settingsButtonTag)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settings", style = MaterialTheme.typography.labelMedium)
            }
        }

        AnimatedVisibility(visible = isSettingsExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                expandedContent()
            }
        }
    }
}

@Composable
private fun SensitivitySelectorRow(
    selected: String,
    onSelect: (String) -> Unit
) {
    Text("Gesture Sensitivity:", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("LOW" to "Low", "MEDIUM" to "Medium", "HIGH" to "High").forEach { (code, label) ->
            FilterChip(
                selected = selected.equals(code, ignoreCase = true),
                onClick = { onSelect(code) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun InlineSubSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
