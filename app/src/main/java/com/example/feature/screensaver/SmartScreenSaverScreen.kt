package com.example.feature.screensaver

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaLinearBar
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch

@Composable
fun SmartScreenSaverScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hubState by viewModel.smartGestureState.collectAsStateWithLifecycle()
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadAlertCount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setBackgroundGestureService(true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.container.smartGestureManager.refreshStateAndListeners()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedStyle = ScreenSaverStyle.fromCode(settings.screenSaverStyle)
    val previewTelemetry = ScreenSaverTelemetryData(
        batteryPercent = liveState.battery?.percentage ?: 86,
        isCharging = liveState.battery?.isCharging ?: false,
        chargingLabel = liveState.battery?.statusLabel ?: "Battery",
        activeNotificationsCount = unreadCount
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Smart Screen Saver",
            subtitle = "Motion-Activated Ambient Display & 7 Aqua Clock Styles",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Master Enable, Status & Full-Screen Live Preview Card
            item {
                AquaGlassCard(glowAccent = settings.screenSaverEnabled) {
                    SectionHeader(title = "Motion-Activated Ambient Display", icon = Icons.Default.AutoAwesome)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Enable Smart Screen Saver", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Activates when your phone is moved or lifted while the screen is off, and returns to sleep when placed stably on a flat surface",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.screenSaverEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    viewModel.prefs.updateScreenSaverSettings(enabled = enabled)
                                    if (enabled && !settings.backgroundMonitoringEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            viewModel.setBackgroundGestureService(true)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("toggle_screen_saver_master")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = if (settings.screenSaverEnabled) "Active: ${selectedStyle.title}" else "Standby",
                            condition = if (settings.screenSaverEnabled) HealthCondition.NORMAL else HealthCondition.INFO
                        )
                        StatusPill(
                            label = if (hubState.hasAccelerometer) "Motion Sensor Ready" else "No Motion Sensor",
                            condition = if (hubState.hasAccelerometer) HealthCondition.NORMAL else HealthCondition.WARNING
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = hubState.screenSaverStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val (_, msg) = viewModel.launchScreenSaverPreview(selectedStyle.code)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_preview_screen_saver")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Live Preview")
                        }
                        OutlinedButton(
                            onClick = { viewModel.container.displayActionManager.openDreamSettings() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_system_dream_settings")
                        ) {
                            Text("System Dock Saver")
                        }
                    }
                }
            }

            // 2. Permissions & Lock-Screen Safety Card
            if (!hubState.canDrawOverlays || !hubState.backgroundServiceActive) {
                item {
                    AquaGlassCard {
                        SectionHeader(title = "Screen-Off Motion Setup", icon = Icons.Default.Security)
                        if (!hubState.canDrawOverlays) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text("Display Over Other Apps", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Required by Android 10+ so PandaNex can open the ambient screen saver when motion is detected while the display is off",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = { SystemUtils.openOverlayPermissionSettings(context) }) {
                                    Text("Grant")
                                }
                            }
                        }
                        if (!hubState.backgroundServiceActive) {
                            if (!hubState.canDrawOverlays) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text("Background Gesture Service", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Keeps low-power motion sensing active when the phone screen turns off",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.setBackgroundGestureService(true) }) {
                                    Text("Enable")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Dedicated Screen Saver Style Gallery (7 Selectable Visual Designs)
            item {
                AquaGlassCard {
                    SectionHeader(title = "Screen Saver Style Gallery (7 Designs)", icon = Icons.Default.Palette)
                    Text(
                        text = "Tap any style card to select it, or tap 'Preview' to test full-screen ambient display:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("screen_saver_style_gallery")
                    ) {
                        items(ScreenSaverStyle.entries, key = { it.code }) { styleItem ->
                            val isSelected = styleItem == selectedStyle
                            Card(
                                modifier = Modifier
                                    .width(196.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        scope.launch {
                                            viewModel.prefs.updateScreenSaverSettings(style = styleItem.code)
                                        }
                                    }
                                    .testTag("style_card_${styleItem.code.lowercase()}"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else aqua.cardBorder
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(126.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    ) {
                                        ScreenSaverClockCanvasSurface(
                                            style = styleItem,
                                            settings = settings,
                                            telemetry = previewTelemetry,
                                            isThumbnail = true
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected Style",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = styleItem.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = styleItem.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        minLines = 2,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.launchScreenSaverPreview(styleItem.code) },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 6.dp)
                                    ) {
                                        Text("Preview Style", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Custom Minimal Style Studio (Shown when Custom Minimal Style is selected)
            if (selectedStyle == ScreenSaverStyle.CUSTOM_MINIMAL) {
                item {
                    AquaGlassCard(glowAccent = true) {
                        SectionHeader(title = "Custom Minimal Style Studio", icon = Icons.Default.Tune)
                        Text("Clock Layout:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "STACKED" to "Stacked",
                                "INLINE_CLASSIC" to "Inline",
                                "LEFT_EDITORIAL" to "Editorial Left"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = settings.screenSaverCustomLayout == code,
                                    onClick = {
                                        scope.launch { viewModel.prefs.updateScreenSaverSettings(customLayout = code) }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Text Scale & Weight:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "COMPACT" to "Compact Light",
                                "BALANCED" to "Balanced",
                                "BOLD_LARGE" to "Bold Large"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = settings.screenSaverCustomTextScale == code,
                                    onClick = {
                                        scope.launch { viewModel.prefs.updateScreenSaverSettings(customTextScale = code) }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Background Darkness & Tone:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "PURE_OLED_BLACK" to "Pure OLED",
                                "DEEP_ABYSS_TEAL" to "Abyss Teal",
                                "MIDNIGHT_AURORA" to "Aurora"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = settings.screenSaverCustomBgTheme == code,
                                    onClick = {
                                        scope.launch { viewModel.prefs.updateScreenSaverSettings(customBgTheme = code) }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Clock Format & Information Display Controls
            item {
                AquaGlassCard {
                    SectionHeader(title = "Clock Format & Displayed Information", icon = Icons.Default.Tune)
                    Text("Clock Time Format:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !settings.screenSaverUse24Hour,
                            onClick = { scope.launch { viewModel.prefs.updateScreenSaverSettings(use24Hour = false) } },
                            label = { Text("12-Hour (AM/PM)") },
                            modifier = Modifier.testTag("chip_saver_12h")
                        )
                        FilterChip(
                            selected = settings.screenSaverUse24Hour,
                            onClick = { scope.launch { viewModel.prefs.updateScreenSaverSettings(use24Hour = true) } },
                            label = { Text("24-Hour") },
                            modifier = Modifier.testTag("chip_saver_24h")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SaverToggleRow(
                        title = "Show Date & Day",
                        subtitle = "Display weekday and calendar date below clock",
                        checked = settings.screenSaverShowDate,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateScreenSaverSettings(showDate = it) } }
                    )
                    SaverToggleRow(
                        title = "Show Battery Percentage",
                        subtitle = "Display live battery % and charging bolt indicator",
                        checked = settings.screenSaverShowBattery,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateScreenSaverSettings(showBattery = it) } }
                    )
                    SaverToggleRow(
                        title = "Show Notification Indicators",
                        subtitle = "Show subtle unread alert count without exposing private message text",
                        checked = settings.screenSaverShowNotifications,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateScreenSaverSettings(showNotifications = it) } }
                    )
                }
            }

            // 6. Brightness, Timeout, Return-to-Sleep & Motion Sensitivity
            item {
                AquaGlassCard {
                    SectionHeader(title = "Brightness, Timeout & Motion Sensitivity", icon = Icons.Default.BrightnessLow)

                    Text(
                        text = "Screen Saver Brightness: ${settings.screenSaverBrightnessPercent}%",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AquaLinearBar(progress = settings.screenSaverBrightnessPercent / 100f)
                    Slider(
                        value = settings.screenSaverBrightnessPercent.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateScreenSaverSettings(brightnessPercent = v.toInt()) }
                        },
                        valueRange = 5f..100f,
                        modifier = Modifier.testTag("slider_saver_brightness")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Auto-Off Timeout: ${settings.screenSaverTimeoutSec} seconds",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.screenSaverTimeoutSec.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateScreenSaverSettings(timeoutSec = v.toInt()) }
                        },
                        valueRange = 5f..60f,
                        modifier = Modifier.testTag("slider_saver_timeout")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Flat Surface Return-to-Sleep Delay: ${settings.screenSaverStationaryOffSec} seconds",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "When placed stably back on a table, automatically closes the screen saver after ${settings.screenSaverStationaryOffSec}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.screenSaverStationaryOffSec.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateScreenSaverSettings(stationaryOffSec = v.toInt()) }
                        },
                        valueRange = 2f..15f
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Motion Activation Sensitivity:", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOW" to "Low", "MEDIUM" to "Medium", "HIGH" to "High").forEach { (code, label) ->
                            FilterChip(
                                selected = settings.screenSaverMotionSensitivity == code,
                                onClick = {
                                    scope.launch { viewModel.prefs.updateScreenSaverSettings(motionSensitivity = code) }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SaverToggleRow(
                        title = "Enable Ambient Animations",
                        subtitle = "Smooth aqua waves, neon orbital pulse & analog second hand",
                        checked = settings.screenSaverAnimationsEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateScreenSaverSettings(animationsEnabled = it) } }
                    )
                    SaverToggleRow(
                        title = "Subtle Vibration on Motion Peek",
                        subtitle = "Gentle haptic pulse when motion activates the screen saver",
                        checked = settings.screenSaverVibrationEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefs.updateScreenSaverSettings(vibrationEnabled = it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SaverToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
