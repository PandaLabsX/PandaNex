package com.example.feature.smartactions

import android.Manifest
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.automation.AquaDisplayMode
import com.example.automation.AquaDynamicsEngineSnapshot
import com.example.automation.AquaDynamicsEvent
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive floating Aqua Dynamics capsule rendered in-app when system WindowManager overlay
 * permission is not active (preventing double rendering when system overlay is already active).
 *
 * - Keeps the camera cutout unobstructed using WindowInsets.displayCutout + statusBars safe top inset.
 * - Supports smooth edge dragging upward toward the camera area to minimize into a compact black
 *   rounded floating pill, and tapping or dragging downward away from the camera to restore.
 * - Immediately hides without flash whenever an incoming, outgoing, or ongoing call is active,
 *   or when any app/video runs in full screen.
 */
@Composable
fun ComposeAquaDynamicsHost(
    snapshot: AquaDynamicsEngineSnapshot,
    animationStyle: String,
    hideInFullScreen: Boolean,
    fullScreenActive: Boolean,
    onVerticalDrag: (totalDyPx: Float, touchSlopPx: Float) -> Unit,
    onVerticalSettle: (velocityY: Float) -> Unit,
    onTapExpand: () -> Unit,
    onTapMinimize: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current

    // If system WindowManager overlay is granted & active, it renders system-wide; avoid duplicate stacking
    val systemOverlayActive = remember(snapshot.displayMode, snapshot.activeEvent?.id) {
        Settings.canDrawOverlays(context)
    }

    val suppressedNow = snapshot.isSuppressedByCall ||
        (hideInFullScreen && (fullScreenActive || snapshot.isSuppressedByFullScreen))

    val shouldRenderComposeCapsule = !systemOverlayActive &&
        snapshot.isEnabled &&
        !suppressedNow &&
        snapshot.displayMode != AquaDisplayMode.HIDDEN &&
        snapshot.activeEvent != null

    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeCameraTopPadding = maxOf(cutoutTop, statusTop) + 6.dp

    val enterAnim = when (animationStyle) {
        "REDUCED" -> fadeIn(tween(80))
        "CRISP" -> fadeIn(tween(170)) + scaleIn(tween(170), initialScale = 0.92f)
        else -> fadeIn(tween(220)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialScale = 0.86f
        )
    }
    val exitAnim = if (suppressedNow || animationStyle == "REDUCED") {
        ExitTransition.None
    } else {
        fadeOut(tween(190)) + scaleOut(tween(190), targetScale = 0.9f)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = shouldRenderComposeCapsule,
            enter = enterAnim,
            exit = exitAnim,
            modifier = Modifier.padding(top = safeCameraTopPadding, start = 16.dp, end = 16.dp)
        ) {
            val event = snapshot.activeEvent
            if (event != null) {
                InteractiveAquaCapsulePill(
                    event = event,
                    displayMode = snapshot.displayMode,
                    expandFraction = snapshot.interactiveExpandFraction,
                    animationStyle = animationStyle,
                    touchSlopPx = viewConfig.touchSlop,
                    densityScale = density.density,
                    onVerticalDrag = onVerticalDrag,
                    onVerticalSettle = onVerticalSettle,
                    onTapExpand = onTapExpand,
                    onTapMinimize = onTapMinimize,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun InteractiveAquaCapsulePill(
    event: AquaDynamicsEvent,
    displayMode: AquaDisplayMode,
    expandFraction: Float,
    animationStyle: String,
    touchSlopPx: Float,
    densityScale: Float,
    onVerticalDrag: (totalDyPx: Float, touchSlopPx: Float) -> Unit,
    onVerticalSettle: (velocityY: Float) -> Unit,
    onTapExpand: () -> Unit,
    onTapMinimize: () -> Unit,
    onDismiss: () -> Unit
) {
    // When an event is triggered in EXPANDED mode, it appears directly as an animated dynamic interface
    // from the very beginning. We do NOT show an intermediate notification/message pill.
    val isExplicitlyMinimized = (displayMode == AquaDisplayMode.MINIMIZED)
    val floatSpec = when (animationStyle) {
        "REDUCED" -> tween<Float>(0)
        "CRISP" -> tween(180, easing = FastOutSlowInEasing)
        else -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    // Direct fraction: in EXPANDED mode on a new event, start at 1f directly without message-to-dynamics transition
    val animatedFraction by animateFloatAsState(
        targetValue = if (isExplicitlyMinimized) 0f else expandFraction.coerceIn(0f, 1f),
        animationSpec = floatSpec,
        label = "aqua_expand_fraction"
    )

    // Fluid aqua-inspired breathing aura
    val infiniteTransition = rememberInfiniteTransition(label = "aqua_fluid_pulse_${event.id}")
    val fluidAuraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fluid_aura"
    )
    val iconHaloPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_halo"
    )

    val targetWidth = (188f + (176f * animatedFraction)).dp
    val targetHeight = (40f + (42f * animatedFraction)).dp
    val cornerRadius by animateDpAsState(
        targetValue = if (isExplicitlyMinimized && animatedFraction < 0.35f) 24.dp else 26.dp,
        label = "aqua_corner_radius"
    )

    val accentColor = Color(event.accentColorHex)
    // Minimized state uses pure black rounded floating background (#020408); Expanded uses deep Clean Aqua midnight glass
    val containerColor = lerp(Color(0xF8020408), Color(0xF5071529), animatedFraction)
    val borderColor = accentColor.copy(alpha = (0.50f + 0.35f * animatedFraction) * (if (animationStyle == "REDUCED") 1f else fluidAuraAlpha))

    var swipeOffsetX by remember(event.id) { mutableFloatStateOf(0f) }
    var dragAccumY by remember(event.id, expandFraction > 0.5f) { mutableFloatStateOf(0f) }
    var lastDeltaY by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .offset { IntOffset(swipeOffsetX.roundToInt(), 0) }
            .widthIn(min = 176.dp, max = 380.dp)
            .width(targetWidth)
            .height(targetHeight)
            .shadow(
                elevation = (6f + 8f * animatedFraction).dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = accentColor.copy(alpha = 0.35f * fluidAuraAlpha),
                spotColor = accentColor.copy(alpha = 0.45f * fluidAuraAlpha)
            )
            .pointerInput(event.id) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumY = 0f
                        lastDeltaY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumY += dragAmount
                        lastDeltaY = dragAmount
                        onVerticalDrag(dragAccumY, touchSlopPx)
                    },
                    onDragEnd = {
                        val approxVel = lastDeltaY * 32f
                        dragAccumY = 0f
                        onVerticalSettle(approxVel)
                    },
                    onDragCancel = {
                        dragAccumY = 0f
                        onVerticalSettle(0f)
                    }
                )
            }
            .pointerInput(event.id, "horiz_dismiss") {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeOffsetX += dragAmount
                    },
                    onDragEnd = {
                        if (abs(swipeOffsetX) > 74f * densityScale) {
                            swipeOffsetX = 0f
                            onDismiss()
                        } else {
                            swipeOffsetX = 0f
                        }
                    },
                    onDragCancel = {
                        swipeOffsetX = 0f
                    }
                )
            }
            .clickable {
                if (isExplicitlyMinimized || animatedFraction < 0.45f) {
                    onTapExpand()
                } else {
                    onTapMinimize()
                }
            }
            .testTag(if (isExplicitlyMinimized && animatedFraction < 0.45f) "aqua_capsule_minimized" else "aqua_capsule_expanded"),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        if (isExplicitlyMinimized && animatedFraction < 0.40f) {
            // Compact Minimized Black Rounded Pill around Camera Area (shown ONLY when deliberately minimized)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = resolveAquaEventIcon(event.iconType),
                        contentDescription = stringResource(R.string.aqua_dynamics_expand_cd),
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = event.badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accentColor,
                    maxLines = 1
                )
            }
        } else {
            // Direct Animated Dynamic Aqua Interface from the very first frame
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Dynamic glowing halo around icon
                        Box(
                            modifier = Modifier
                                .size((34f * iconHaloPulse).dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.08f * fluidAuraAlpha))
                        )
                        Icon(
                            imageVector = resolveAquaEventIcon(event.iconType),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = CircleShape,
                                color = accentColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = event.badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = event.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB6D2EA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("aqua_capsule_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.aqua_dynamics_dismiss_cd),
                            tint = Color(0xFFA8C7DF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom tactile edge drag bar (drag upward toward camera to minimize)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .width(36.dp)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.55f))
                )
            }
        }
    }
}

private fun resolveAquaEventIcon(iconType: String): ImageVector {
    return when (iconType) {
        "CALL_MISSED" -> Icons.AutoMirrored.Filled.CallMissed
        "CALL_DONE" -> Icons.Default.CallEnd
        "CPU" -> Icons.Default.Memory
        "THERMAL" -> Icons.Default.Thermostat
        "BATTERY" -> Icons.Default.BatteryAlert
        "CHARGING" -> Icons.Default.Bolt
        "WIFI" -> Icons.Default.Wifi
        "NETWORK" -> Icons.Default.NetworkCheck
        "ROTATION" -> Icons.Default.ScreenRotation
        "AUDIO" -> Icons.Default.Headphones
        "SENSOR" -> Icons.Default.Sensors
        else -> Icons.Default.NotificationsActive
    }
}

/**
 * Dedicated Aqua Dynamics Settings & Interactive Control Screen.
 */
@Composable
fun AquaDynamicsScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aquaState by viewModel.aquaDynamicsState.collectAsStateWithLifecycle()
    val glowState by viewModel.edgeGlowState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.container.edgeGlowManager.refreshPermissions()
        viewModel.container.aquaDynamicsManager.syncTelephonyCallListener()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.container.edgeGlowManager.refreshPermissions()
                viewModel.container.aquaDynamicsManager.syncTelephonyCallListener()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val priorityOptions = listOf(
        "ALL" to "All Events",
        "IMPORTANT_ONLY" to "Important & Critical",
        "CRITICAL_ONLY" to "Critical Only"
    )
    val animationOptions = listOf(
        "FLUID_SPRING" to "Fluid Spring",
        "CRISP" to "Crisp Precision",
        "REDUCED" to "Reduced Motion"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = stringResource(R.string.aqua_dynamics_title),
            subtitle = stringResource(R.string.aqua_dynamics_subtitle),
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Interactive Status & Minimize/Expand Test Controls
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Live Capsule Status & Gesture Preview", icon = Icons.Default.AutoAwesome)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = when {
                                !settings.aquaDynamicsEnabled -> "Disabled"
                                aquaState.isSuppressedByCall -> "Call Guard Active"
                                aquaState.displayMode == AquaDisplayMode.EXPANDED -> "Expanded"
                                aquaState.displayMode == AquaDisplayMode.MINIMIZED -> "Minimized Pill"
                                else -> "Standby"
                            },
                            condition = when {
                                aquaState.isSuppressedByCall -> HealthCondition.WARNING
                                settings.aquaDynamicsEnabled -> HealthCondition.NORMAL
                                else -> HealthCondition.INFO
                            }
                        )
                        StatusPill(
                            label = aquaState.callPhase.label,
                            condition = if (aquaState.callPhase.isCallActive) HealthCondition.WARNING else HealthCondition.INFO
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = aquaState.statusSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.aqua_dynamics_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.previewAquaDynamicsCapsule() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("aqua_preview_capsule_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Capsule")
                        }

                        if (aquaState.displayMode == AquaDisplayMode.EXPANDED) {
                            OutlinedButton(
                                onClick = { viewModel.minimizeAquaDynamicsCapsule() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("aqua_minimize_now_button")
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Minimize")
                            }
                        } else if (aquaState.displayMode == AquaDisplayMode.MINIMIZED) {
                            OutlinedButton(
                                onClick = { viewModel.expandAquaDynamicsCapsule() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("aqua_expand_now_button")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Expand")
                            }
                        }
                    }

                    if (aquaState.displayMode != AquaDisplayMode.HIDDEN) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.dismissAquaDynamicsCapsule() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("aqua_dismiss_now_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dismiss Active Event")
                        }
                    }
                }
            }

            // 2. Master Toggle & Call / Full-Screen Suppression Rules
            item {
                AquaGlassCard {
                    SectionHeader(title = "Core Behaviour & Call Protection", icon = Icons.Default.PhoneDisabled)
                    AquaSwitchRow(
                        title = stringResource(R.string.aqua_dynamics_master_enable),
                        subtitle = stringResource(R.string.aqua_dynamics_master_desc),
                        checked = settings.aquaDynamicsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(enabled = enabled) }
                        },
                        tag = "toggle_aqua_dynamics_master"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = stringResource(R.string.aqua_dynamics_call_guard_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.aqua_dynamics_call_guard_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(label = "Always Enforced", condition = HealthCondition.NORMAL)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AquaSwitchRow(
                        title = "Lock Dismissed Events Until New Event",
                        subtitle = "Dismissed alerts never reopen on repeated telemetry until a genuinely new event occurs",
                        checked = settings.aquaStrictDismissLock,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(strictDismissLock = enabled) }
                        },
                        tag = "toggle_aqua_dismiss_lock"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AquaSwitchRow(
                        title = stringResource(R.string.aqua_fullscreen_hide_title),
                        subtitle = stringResource(R.string.aqua_fullscreen_hide_subtitle),
                        checked = settings.hideAquaDynamicsInFullScreen,
                        onCheckedChange = { enabled ->
                            viewModel.setHideAquaDynamicsInFullScreen(enabled)
                        },
                        tag = "toggle_aqua_hide_fullscreen"
                    )
                }
            }

            // 3. Individual Event Categories
            item {
                AquaGlassCard {
                    SectionHeader(title = "Event Categories", icon = Icons.Default.NotificationsActive)
                    AquaSwitchRow(
                        title = "Phone & Communication",
                        subtitle = "Missed calls & post-call status summaries (suppressed while call is active)",
                        checked = settings.aquaCatPhoneEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(phoneEnabled = enabled) }
                        },
                        tag = "toggle_aqua_cat_phone"
                    )
                    if (settings.aquaCatPhoneEnabled) {
                        AquaSwitchRow(
                            title = "Ongoing Call Post-Call Summary",
                            subtitle = "Show call duration capsule only after an active call finishes",
                            checked = settings.aquaPostCallSummaryEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { viewModel.prefs.updateAquaDynamicsSettings(postCallSummaryEnabled = enabled) }
                            },
                            tag = "toggle_aqua_post_call_summary"
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AquaSwitchRow(
                        title = "Device Performance & Battery",
                        subtitle = "CPU overload, device overheating, battery temperature, low battery & full charge",
                        checked = settings.aquaCatPerformanceEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(performanceEnabled = enabled) }
                        },
                        tag = "toggle_aqua_cat_performance"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AquaSwitchRow(
                        title = "Network & Connectivity",
                        subtitle = "Wi-Fi connect/disconnect, internet state & cellular/Wi-Fi handoff",
                        checked = settings.aquaCatNetworkEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(networkEnabled = enabled) }
                        },
                        tag = "toggle_aqua_cat_network"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AquaSwitchRow(
                        title = "Device & Audio Interactions",
                        subtitle = "Screen rotation, charging cable plug/unplug, headphones/Bluetooth audio & smart gestures",
                        checked = settings.aquaCatInteractionsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(interactionsEnabled = enabled) }
                        },
                        tag = "toggle_aqua_cat_interactions"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AquaSwitchRow(
                        title = "PandaNex System Alerts",
                        subtitle = "Heat Guard warnings, diagnostic completions & smart automation alerts",
                        checked = settings.aquaCatSystemAlertsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(systemAlertsEnabled = enabled) }
                        },
                        tag = "toggle_aqua_cat_system"
                    )
                }
            }

            // 4. Priority, Auto-Minimization, Animation & Cooldown Tuning
            item {
                AquaGlassCard {
                    SectionHeader(title = "Minimization, Priority & Cooldown", icon = Icons.Default.Tune)

                    Text("Event Priority Filter:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(priorityOptions) { (code, label) ->
                            FilterChip(
                                selected = settings.aquaPriorityFilter == code,
                                onClick = {
                                    scope.launch { viewModel.prefs.updateAquaDynamicsSettings(priorityFilter = code) }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("aqua_priority_$code")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    AquaSwitchRow(
                        title = "Automatic Minimization",
                        subtitle = "Smoothly collapse expanded capsule into the compact camera-area black pill",
                        checked = settings.aquaAutoMinimizeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(autoMinimizeEnabled = enabled) }
                        },
                        tag = "toggle_aqua_auto_minimize"
                    )
                    if (settings.aquaAutoMinimizeEnabled) {
                        Text(
                            text = "Auto-Minimize Delay: ${String.format("%.1f", settings.aquaAutoMinimizeDelayMs / 1000f)} sec",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = settings.aquaAutoMinimizeDelayMs.toFloat(),
                            onValueChange = { v ->
                                scope.launch { viewModel.prefs.updateAquaDynamicsSettings(autoMinimizeDelayMs = v.toLong()) }
                            },
                            valueRange = 2500f..15000f
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Animation Behaviour:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(animationOptions) { (code, label) ->
                            FilterChip(
                                selected = settings.aquaAnimationStyle == code,
                                onClick = {
                                    scope.launch { viewModel.prefs.updateAquaDynamicsSettings(animationStyle = code) }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("aqua_anim_$code")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Event Cooldown Duration: ${settings.aquaEventCooldownMs / 1000L} sec",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.aquaEventCooldownMs.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.updateAquaDynamicsSettings(eventCooldownMs = v.toLong()) }
                        },
                        valueRange = 2000f..60000f
                    )
                }
            }

            // 5. System Permissions for Floating Overlay & Call State Detection
            item {
                AquaGlassCard {
                    SectionHeader(title = "Optional System Permissions", icon = Icons.Default.Layers)
                    PermissionAccessRow(
                        title = "System Floating Capsule (Draw Over Apps)",
                        subtitle = "Displays the compact Aqua Dynamics pill near the camera across other apps (never during calls)",
                        granted = glowState.overlayPermissionGranted,
                        buttonLabel = "Grant Overlay",
                        onGrantClick = { SystemUtils.openOverlayPermissionSettings(context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionAccessRow(
                        title = "Phone Call State Detection",
                        subtitle = "Detects cellular ringing/off-hook for instant Call Guard & Missed Call summaries",
                        granted = glowState.phoneStatePermissionGranted,
                        buttonLabel = "Allow",
                        onGrantClick = { phonePermLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
                    )
                }
            }

            // 6. Recent Genuine Aqua Dynamics Events Log
            if (aquaState.recentEvents.isNotEmpty()) {
                item {
                    SectionHeader(title = "Recent Aqua Dynamics Events", icon = Icons.Default.History)
                }
                items(aquaState.recentEvents.take(8), key = { it.id }) { ev ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        KeyValueRow(ev.title, ev.badgeText)
                        Text(
                            text = "${ev.category.displayLabel} • ${ev.subtitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AquaSwitchRow(
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
            Text(
                text = subtitle,
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
