package com.example.feature.smartactions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.automation.EdgeGlowActiveState
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private data class GlowColorPreset(
    val name: String,
    val hex: Long
)

private val GLOW_COLOR_PRESETS = listOf(
    GlowColorPreset("Electric Aqua", 0xFF00E5FFL),
    GlowColorPreset("Cyber Mint", 0xFF1DE9B6L),
    GlowColorPreset("Neon Emerald", 0xFF00E676L),
    GlowColorPreset("Ice Cyan", 0xFF40C4FFL),
    GlowColorPreset("Solar Amber", 0xFFFFD740L),
    GlowColorPreset("Crimson Pulse", 0xFFFF5252L),
    GlowColorPreset("Plasma Violet", 0xFF7C4DFFL)
)

private val GLOW_STYLES = listOf(
    "AQUA_PULSE" to "Aqua Pulse",
    "CYBER_SWEEP" to "360° Cyber Sweep",
    "DUAL_WAVE" to "Liquid Dual Wave",
    "HORIZON_BEAM" to "Horizon Beam"
)

/**
 * Full-screen non-blocking Compose Edge Glow overlay rendered at the root of PandaNexAppShell.
 * Automatically and immediately disappears whenever any app, video player, game, or diagnostic
 * view runs in full screen.
 */
@Composable
fun ComposeEdgeGlowOverlay(
    state: EdgeGlowActiveState,
    hideInFullScreen: Boolean = true,
    fullScreenActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hostView = LocalView.current
    var windowStatusBarHidden by remember { mutableStateOf(false) }

    DisposableEffect(hostView) {
        val currentInsets = ViewCompat.getRootWindowInsets(hostView)
        if (currentInsets != null) {
            windowStatusBarHidden = !currentInsets.isVisible(WindowInsetsCompat.Type.statusBars())
        }
        ViewCompat.setOnApplyWindowInsetsListener(hostView) { v, insets ->
            windowStatusBarHidden = !insets.isVisible(WindowInsetsCompat.Type.statusBars())
            ViewCompat.onApplyWindowInsets(v, insets)
        }
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(hostView, null)
        }
    }

    val suppressedNow = hideInFullScreen &&
        (fullScreenActive || state.isSuppressedByFullScreen || windowStatusBarHidden)

    AnimatedVisibility(
        visible = state.isActive && !suppressedNow,
        enter = fadeIn(tween(220)),
        exit = if (suppressedNow) ExitTransition.None else fadeOut(tween(320)),
        modifier = modifier.fillMaxSize()
    ) {
        val infinite = rememberInfiniteTransition(label = "edge_glow_anim")
        val phase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "edge_phase"
        )

        val primary = Color(state.primaryColorHex)
        val secondary = Color(state.secondaryColorHex)

        Box(modifier = Modifier.fillMaxSize()) {
            // Render Compose edge glow if system WindowManager overlay isn't already drawing it
            if (!state.overlayPermissionGranted) {
                EdgeGlowCanvasEffect(
                    primary = primary,
                    secondary = secondary,
                    strokeDp = state.strokeDp,
                    style = state.style,
                    phase = phase,
                    cornerRadiusDp = 34f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Sleek non-intrusive status capsule at top center during active notification/preview glow
            if (state.isPreview || state.triggerType != "CALL") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xDD04101F),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (state.triggerType == "CALL") Icons.Default.Call else Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${if (state.triggerType == "CALL") "Call Glow" else "Notification Glow"} • ${state.sourceLabel}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgeGlowCanvasEffect(
    primary: Color,
    secondary: Color,
    strokeDp: Float,
    style: String,
    phase: Float,
    cornerRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val strokePx = strokeDp.dp.toPx()
        val cornerPx = cornerRadiusDp.dp.toPx()
        val pulse = (0.55f + 0.45f * sin(phase * 2.0 * PI)).toFloat()
        val alphaFactor = if (style == "HORIZON_BEAM") 1f else pulse

        val linearBrush = when (style) {
            "DUAL_WAVE" -> {
                val yOffset = h * phase
                Brush.verticalGradient(
                    colors = listOf(primary, secondary, primary),
                    startY = yOffset - h * 0.5f,
                    endY = yOffset + h * 0.5f
                )
            }
            else -> Brush.linearGradient(
                colors = listOf(primary, secondary, primary),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        }

        if (style == "CYBER_SWEEP") {
            val sweep = Brush.sweepGradient(
                colors = listOf(primary, secondary, primary.copy(alpha = 0.15f), primary),
                center = Offset(w / 2f, h / 2f)
            )
            rotate(degrees = phase * 360f, pivot = Offset(w / 2f, h / 2f)) {
                // Draw only via perimeter clipping effect using strokes on unrotated rect
            }
            // Perimeter sweep using phased four-corner gradient
            val angleRad = phase * 2.0 * PI
            val cx = (w / 2f) + (w * 0.5f * kotlin.math.cos(angleRad)).toFloat()
            val cy = (h / 2f) + (h * 0.5f * sin(angleRad)).toFloat()
            val dynamicBrush = Brush.radialGradient(
                colors = listOf(primary, secondary, primary.copy(alpha = 0.25f)),
                center = Offset(cx, cy),
                radius = maxOf(w, h) * 0.85f
            )
            drawGlowLayers(w, h, strokePx, cornerPx, dynamicBrush, 0.92f)
            // Keep sweep variable referenced cleanly
            @Suppress("UNUSED_EXPRESSION")
            sweep
        } else {
            drawGlowLayers(w, h, strokePx, cornerPx, linearBrush, alphaFactor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlowLayers(
    w: Float,
    h: Float,
    strokePx: Float,
    cornerPx: Float,
    brush: Brush,
    alphaFactor: Float
) {
    // Layer 1: Wide soft outer halo
    val inset1 = strokePx * 0.6f
    drawRoundRect(
        brush = brush,
        topLeft = Offset(inset1, inset1),
        size = Size((w - inset1 * 2).coerceAtLeast(1f), (h - inset1 * 2).coerceAtLeast(1f)),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        alpha = (0.34f * alphaFactor).coerceIn(0.12f, 0.48f),
        style = Stroke(width = strokePx * 3.1f)
    )
    // Layer 2: Mid aqua glow
    val inset2 = strokePx * 0.45f
    drawRoundRect(
        brush = brush,
        topLeft = Offset(inset2, inset2),
        size = Size((w - inset2 * 2).coerceAtLeast(1f), (h - inset2 * 2).coerceAtLeast(1f)),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        alpha = (0.62f * alphaFactor).coerceIn(0.25f, 0.8f),
        style = Stroke(width = strokePx * 1.75f)
    )
    // Layer 3: Crisp core neon border
    val inset3 = strokePx * 0.35f
    drawRoundRect(
        brush = brush,
        topLeft = Offset(inset3, inset3),
        size = Size((w - inset3 * 2).coerceAtLeast(1f), (h - inset3 * 2).coerceAtLeast(1f)),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        alpha = (0.78f + 0.22f * alphaFactor).coerceIn(0.65f, 1f),
        style = Stroke(width = strokePx)
    )
}

@Composable
fun EdgeGlowScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val glowState by viewModel.edgeGlowState.collectAsStateWithLifecycle()
    val fullScreenState by viewModel.fullScreenState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.container.edgeGlowManager.refreshPermissions()
        viewModel.container.edgeGlowManager.syncTelephonyCallListener()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.container.edgeGlowManager.refreshPermissions()
                viewModel.container.edgeGlowManager.syncTelephonyCallListener()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val infinite = rememberInfiniteTransition(label = "mini_preview_glow")
    val previewPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_phase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Edge Glow Lighting",
            subtitle = "Illuminated Bezel Effects for Calls & Notifications",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Interactive Mini-Bezel Visualizer + Instant Test Buttons
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Live Edge Glow Visualizer", icon = Icons.Default.AutoAwesome)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF040B16)),
                        contentAlignment = Alignment.Center
                    ) {
                        EdgeGlowCanvasEffect(
                            primary = Color(settings.edgeGlowCallColorHex),
                            secondary = Color(settings.edgeGlowNotifColorHex),
                            strokeDp = settings.edgeGlowStrokeDp.coerceIn(4f, 14f),
                            style = settings.edgeGlowStyle,
                            phase = previewPhase,
                            cornerRadiusDp = 22f,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = GLOW_STYLES.firstOrNull { it.first == settings.edgeGlowStyle }?.second ?: "Aqua Pulse",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = glowState.lastEventSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA8C7DF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.container.edgeGlowManager.triggerGlow(
                                    isCall = true,
                                    sourceLabel = "Preview Incoming Call",
                                    continuousUntilStopped = false,
                                    forcePreview = true
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_call_glow_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Call Glow")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.container.edgeGlowManager.triggerGlow(
                                    isCall = false,
                                    sourceLabel = "Preview Notification",
                                    continuousUntilStopped = false,
                                    forcePreview = true
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_notif_glow_button")
                        ) {
                            Text("Test Alert Glow")
                        }
                    }
                    if (glowState.isActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.container.edgeGlowManager.stopGlow() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Active Glow")
                        }
                    }
                }
            }

            // 2. Master Switch & Call / Notification Triggers
            item {
                AquaGlassCard {
                    SectionHeader(title = "Edge Glow Triggers", icon = Icons.Default.NotificationsActive)
                    EdgeToggleItem(
                        title = "Enable Edge Glow Lighting",
                        subtitle = "Master switch for glowing screen edges on calls and notifications",
                        checked = settings.edgeGlowEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(enabled = enabled) }
                        },
                        tag = "toggle_edge_glow_master"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    EdgeToggleItem(
                        title = "Glow Edges for Incoming Calls",
                        subtitle = "Continuous perimeter glow while phone is ringing",
                        checked = settings.edgeGlowCallsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(callsEnabled = enabled) }
                        },
                        tag = "toggle_edge_glow_calls"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    EdgeToggleItem(
                        title = "Glow Edges for Notifications",
                        subtitle = "Illuminates screen edges when status bar or PandaNex alerts arrive",
                        checked = settings.edgeGlowNotificationsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(notificationsEnabled = enabled) }
                        },
                        tag = "toggle_edge_glow_notifs"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    EdgeToggleItem(
                        title = stringResource(R.string.aqua_fullscreen_hide_title),
                        subtitle = stringResource(R.string.aqua_fullscreen_hide_subtitle),
                        checked = settings.hideAquaDynamicsInFullScreen,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(hideInFullScreen = enabled) }
                        },
                        tag = "toggle_hide_aqua_fullscreen"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Full-Screen Guard: ${fullScreenState.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        StatusPill(
                            label = when {
                                !settings.hideAquaDynamicsInFullScreen -> "Off"
                                fullScreenState.isFullScreenActive -> "Suppressed"
                                else -> "Armed"
                            },
                            condition = if (fullScreenState.isFullScreenActive) HealthCondition.INFO else HealthCondition.NORMAL
                        )
                    }
                }
            }

            // 3. System Permissions for System-Wide Call & Notification Detection
            item {
                AquaGlassCard {
                    SectionHeader(title = "System Integration Permissions", icon = Icons.Default.Layers)

                    PermissionAccessRow(
                        title = "Draw Over Other Apps (System Overlay)",
                        subtitle = "Allows Edge Glow to illuminate over lockscreen, call screen & all apps",
                        granted = glowState.overlayPermissionGranted,
                        buttonLabel = "Grant Overlay",
                        onGrantClick = { SystemUtils.openOverlayPermissionSettings(context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionAccessRow(
                        title = "Notification Listener Access",
                        subtitle = "Detects incoming notifications & call banners from messaging/phone apps",
                        granted = glowState.notificationListenerGranted,
                        buttonLabel = "Grant Access",
                        onGrantClick = { SystemUtils.openNotificationListenerSettings(context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionAccessRow(
                        title = "Phone Ringing State",
                        subtitle = "Detects cellular CALL_STATE_RINGING for instant Call Edge Glow",
                        granted = glowState.phoneStatePermissionGranted,
                        buttonLabel = "Allow",
                        onGrantClick = { phonePermLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
                    )
                }
            }

            // 4. Style, Colors, Thickness & Duration Customization
            item {
                AquaGlassCard {
                    SectionHeader(title = "Style, Colors & Geometry", icon = Icons.Default.Palette)
                    Text("Animation Pattern:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GLOW_STYLES) { (code, label) ->
                            FilterChip(
                                selected = settings.edgeGlowStyle == code,
                                onClick = {
                                    scope.launch { viewModel.prefs.updateEdgeGlowSettings(style = code) }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Incoming Call Glow Color:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorSwatchRow(
                        selectedHex = settings.edgeGlowCallColorHex,
                        onSelect = { hex ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(callColorHex = hex) }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Notification Glow Color:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorSwatchRow(
                        selectedHex = settings.edgeGlowNotifColorHex,
                        onSelect = { hex ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(notifColorHex = hex) }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Edge Thickness: ${String.format("%.1f", settings.edgeGlowStrokeDp)} dp",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.edgeGlowStrokeDp,
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(strokeDp = v) }
                        },
                        valueRange = 4f..16f
                    )

                    Text(
                        text = "Notification Glow Duration: ${String.format("%.1f", settings.edgeGlowDurationMs / 1000f)} sec",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.edgeGlowDurationMs.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateEdgeGlowSettings(durationMs = v.toLong()) }
                        },
                        valueRange = 1500f..8000f
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    selectedHex: Long,
    onSelect: (Long) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(GLOW_COLOR_PRESETS) { preset ->
            val isSelected = selectedHex == preset.hex
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(preset.hex))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(preset.hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = preset.name,
                        tint = Color(0xFF04101E),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PermissionAccessRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            StatusPill(label = "Granted", condition = HealthCondition.NORMAL)
        } else {
            OutlinedButton(onClick = onGrantClick) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun EdgeToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
