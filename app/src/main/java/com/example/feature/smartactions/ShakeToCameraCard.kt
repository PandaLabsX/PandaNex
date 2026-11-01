package com.example.feature.smartactions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.QuickCaptureSensitivity
import com.example.automation.QuickCaptureSensorMode
import com.example.automation.QuickCaptureState
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.ui.theme.LocalAquaColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dedicated Smart Actions card for Shake to Camera (Moto-style Quick Capture).
 * Includes Enable/Disable toggle, Low/Medium/High sensitivity chips, vibration toggle,
 * animated wrist-twist gesture illustration, real-time detection status, Test Gesture button,
 * and transparent Android background & lock-screen security documentation.
 */
@Composable
fun ShakeToCameraCard(
    state: QuickCaptureState,
    animationsEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectSensitivity: (QuickCaptureSensitivity) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onTestGesture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aqua = LocalAquaColors.current
    val sensorsSupported = state.sensorMode != QuickCaptureSensorMode.UNSUPPORTED

    AquaGlassCard(
        glowAccent = state.isEnabled && sensorsSupported,
        modifier = modifier.testTag("shake_to_camera_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SectionHeader(
                    title = stringResource(R.string.shake_camera_title),
                    icon = Icons.Default.CameraAlt
                )
            }
            StatusPill(
                label = when {
                    !sensorsSupported -> "Unsupported"
                    !state.cameraAppAvailable -> "No Camera"
                    state.isEnabled && state.isActivelyListening -> "Armed"
                    state.isEnabled -> "Standby"
                    else -> "Off"
                },
                condition = when {
                    !sensorsSupported || !state.cameraAppAvailable -> HealthCondition.WARNING
                    state.isEnabled -> HealthCondition.NORMAL
                    else -> HealthCondition.INFO
                }
            )
        }

        // Enable / Disable Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.shake_camera_enable_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.shake_camera_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.isEnabled && sensorsSupported,
                enabled = sensorsSupported,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_shake_to_camera")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Animated Moto-Style Double Wrist Twist Illustration + Instructions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = aqua.cardBorder,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
                .testTag("shake_camera_illustration_box")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickCaptureTwistIllustration(
                    animate = animationsEnabled,
                    accentColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(84.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Double Wrist-Twist Gesture (1 → 2)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.shake_camera_instructions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Gesture Sensitivity: Low / Medium / High
        Text(
            text = stringResource(R.string.shake_camera_sensitivity_label),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickCaptureSensitivity.entries.forEach { option ->
                val chipTag = "chip_shake_camera_sens_${option.code.lowercase()}"
                FilterChip(
                    selected = state.sensitivity == option,
                    enabled = sensorsSupported,
                    onClick = { onSelectSensitivity(option) },
                    label = { Text(option.displayLabel) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(chipTag)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Camera Launch Vibration Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(R.string.shake_camera_vibration_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.shake_camera_vibration_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = state.vibrationEnabled,
                onCheckedChange = onToggleVibration,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_camera_launch_vibration")
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Live Sensor & Last Gesture Detection Status
        KeyValueRow("Sensor Calibration", state.sensorMode.label, monoValue = false)
        KeyValueRow(
            label = "Default Camera App",
            value = if (state.cameraAppAvailable) state.resolvedCameraPackage else "Unavailable",
            monoValue = false
        )
        KeyValueRow("Trigger Cooldown", "${state.cooldownMs / 1000.0}s")
        if (state.lastTriggerTimestampMs > 0L) {
            KeyValueRow(
                label = "Last Triggered",
                value = "${SystemUtils.formatTimestamp(state.lastTriggerTimestampMs)} (#${state.triggerCount})"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("shake_camera_last_status")
        ) {
            Text(
                text = "Status: ${state.lastDetectionStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Test Gesture Button
        Button(
            onClick = onTestGesture,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("test_shake_to_camera_button")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.shake_camera_test_btn)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.shake_camera_test_btn))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Transparent Android Background Execution & Lock-Screen Security Notice
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Android Background Restrictions Info",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.shake_camera_bg_notice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Clean Aqua Dynamics Canvas illustration demonstrating two quick wrist twists (1 -> 2)
 * followed by a brief pause, mirroring the Motorola Quick Capture motion signature.
 */
@Composable
private fun QuickCaptureTwistIllustration(
    animate: Boolean,
    accentColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "quick_capture_twist")
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "twist_cycle"
    )

    // First 62% of cycle performs 2 crisp sine twists; remaining 38% rests upright (cooldown visual)
    val phase = if (animate) cycleProgress else 0.16f
    val twistAngleDeg = if (phase < 0.62f) {
        val normalized = phase / 0.62f
        (sin(normalized * 4.0 * PI) * 24.0).toFloat()
    } else {
        0f
    }
    val activePulseAlpha = if (phase in 0.56f..0.78f) 0.95f else 0.35f

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ringRadius = size.minDimension * 0.44f

        // Outer rotational motion arcs (left & right wrist twist guides)
        drawArc(
            color = secondaryColor.copy(alpha = 0.55f),
            startAngle = -145f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(cx - ringRadius, cy - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = accentColor.copy(alpha = activePulseAlpha),
            startAngle = 35f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(cx - ringRadius, cy - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Rotated smartphone silhouette with glowing camera lens
        rotate(degrees = twistAngleDeg, pivot = Offset(cx, cy)) {
            val phoneW = size.width * 0.36f
            val phoneH = size.height * 0.64f
            val left = cx - phoneW / 2f
            val top = cy - phoneH / 2f

            drawRoundRect(
                color = accentColor.copy(alpha = 0.18f),
                topLeft = Offset(left, top),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(left, top),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Camera viewfinder ring inside phone
            drawCircle(
                color = secondaryColor,
                radius = phoneW * 0.22f,
                center = Offset(cx, top + phoneH * 0.34f),
                style = Stroke(width = 1.8.dp.toPx())
            )
            drawCircle(
                color = accentColor.copy(alpha = activePulseAlpha),
                radius = phoneW * 0.10f,
                center = Offset(cx, top + phoneH * 0.34f)
            )
        }
    }
}
