package com.example.feature.screensaver

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.datastore.PandaSettings
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ScreenSaverStyle(
    val code: String,
    val title: String,
    val subtitle: String
) {
    AQUA_DIGITAL(
        code = "AQUA_DIGITAL",
        title = "Aqua Digital Clock",
        subtitle = "Large digital clock with elegant aqua halo & date"
    ),
    MINIMAL_ANALOG(
        code = "MINIMAL_ANALOG",
        title = "Minimal Analog Clock",
        subtitle = "Precision analog dial with minimal markers on OLED black"
    ),
    NEON_CLOCK(
        code = "NEON_CLOCK",
        title = "Neon Clock",
        subtitle = "Futuristic glowing cyan-aqua orbital ring & mono numerals"
    ),
    MINIMAL_AOD(
        code = "MINIMAL_AOD",
        title = "Minimal Always-On",
        subtitle = "Ultra low-distraction time, date & battery percentage"
    ),
    AQUA_WAVES(
        code = "AQUA_WAVES",
        title = "Aqua Waves",
        subtitle = "Fluid animated aqua horizon waves with floating clock"
    ),
    DIGITAL_INFO(
        code = "DIGITAL_INFO",
        title = "Digital Information",
        subtitle = "Time, date, battery %, charging state & notification badges"
    ),
    CUSTOM_MINIMAL(
        code = "CUSTOM_MINIMAL",
        title = "Custom Minimal Style",
        subtitle = "User-selectable clock layout, text scale & background darkness"
    );

    companion object {
        fun fromCode(code: String?): ScreenSaverStyle {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AQUA_DIGITAL
        }
    }
}

data class ScreenSaverTelemetryData(
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val chargingLabel: String = "Discharging",
    val activeNotificationsCount: Int = 0
)

@Composable
fun ScreenSaverClockCanvasSurface(
    style: ScreenSaverStyle,
    settings: PandaSettings,
    telemetry: ScreenSaverTelemetryData,
    isThumbnail: Boolean = false,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isThumbnail) {
        val interval = if (isThumbnail) 15_000L else 1_000L
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(interval)
        }
    }

    val animationsActive = settings.screenSaverAnimationsEnabled && settings.animationsEnabled
    val infinite = rememberInfiniteTransition(label = "saver_anim")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "saver_wave_phase"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "saver_glow_pulse"
    )

    val effectivePhase = if (animationsActive) wavePhase else 0.9f
    val effectivePulse = if (animationsActive) glowPulse else 0.8f

    val calendar = remember(nowMillis) {
        Calendar.getInstance().apply { timeInMillis = nowMillis }
    }
    val dateObj = remember(nowMillis) { Date(nowMillis) }

    val timePattern = if (settings.screenSaverUse24Hour) "HH:mm" else "hh:mm"
    val timeText = remember(nowMillis, settings.screenSaverUse24Hour) {
        SimpleDateFormat(timePattern, Locale.getDefault()).format(dateObj)
    }
    val amPmText = remember(nowMillis, settings.screenSaverUse24Hour) {
        if (settings.screenSaverUse24Hour) "" else SimpleDateFormat("a", Locale.getDefault()).format(dateObj)
    }
    val dateText = remember(nowMillis) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(dateObj)
    }
    val hourText = remember(nowMillis, settings.screenSaverUse24Hour) {
        SimpleDateFormat(if (settings.screenSaverUse24Hour) "HH" else "hh", Locale.getDefault()).format(dateObj)
    }
    val minuteText = remember(nowMillis) {
        SimpleDateFormat("mm", Locale.getDefault()).format(dateObj)
    }

    // Subtle OLED pixel-protection drift in full-screen mode
    val driftOffset = remember(nowMillis / 30_000L, isThumbnail) {
        if (isThumbnail) {
            IntOffset.Zero
        } else {
            val minuteBucket = ((nowMillis / 30_000L) % 8L).toInt()
            val dx = ((minuteBucket % 3) - 1) * 4
            val dy = ((minuteBucket / 3) - 1) * 5
            IntOffset(dx, dy)
        }
    }

    val bgBrush = when {
        style == ScreenSaverStyle.CUSTOM_MINIMAL -> when (settings.screenSaverCustomBgTheme) {
            "DEEP_ABYSS_TEAL" -> Brush.verticalGradient(listOf(Color(0xFF02131A), Color(0xFF01060A)))
            "MIDNIGHT_AURORA" -> Brush.verticalGradient(listOf(Color(0xFF041022), Color(0xFF021A1D), Color(0xFF010408)))
            else -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        }
        style == ScreenSaverStyle.AQUA_WAVES -> Brush.verticalGradient(
            listOf(Color(0xFF010912), Color(0xFF021722), Color(0xFF01060B))
        )
        style == ScreenSaverStyle.NEON_CLOCK -> Brush.radialGradient(
            listOf(Color(0xFF041C28), Color(0xFF01050A), Color.Black)
        )
        else -> Brush.verticalGradient(listOf(Color.Black, Color(0xFF010509), Color.Black))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        // Style-specific ambient background canvas
        if (style == ScreenSaverStyle.AQUA_WAVES) {
            AquaWavesBackdrop(phase = effectivePhase, isThumbnail = isThumbnail)
        } else if (style == ScreenSaverStyle.AQUA_DIGITAL) {
            AquaDigitalHaloBackdrop(pulse = effectivePulse, isThumbnail = isThumbnail)
        }

        Box(
            modifier = Modifier
                .offset { driftOffset }
                .padding(if (isThumbnail) 10.dp else 28.dp),
            contentAlignment = Alignment.Center
        ) {
            when (style) {
                ScreenSaverStyle.AQUA_DIGITAL -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = timeText,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = if (isThumbnail) 28.sp else 74.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFFE8FCFF),
                                    letterSpacing = (-1).sp
                                )
                            )
                            if (amPmText.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(if (isThumbnail) 4.dp else 8.dp))
                                Text(
                                    text = amPmText,
                                    style = TextStyle(
                                        fontSize = if (isThumbnail) 10.sp else 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF00E5FF)
                                    ),
                                    modifier = Modifier.padding(bottom = if (isThumbnail) 4.dp else 12.dp)
                                )
                            }
                        }
                        if (settings.screenSaverShowDate) {
                            Spacer(modifier = Modifier.height(if (isThumbnail) 3.dp else 8.dp))
                            Text(
                                text = dateText.uppercase(Locale.getDefault()),
                                style = TextStyle(
                                    fontSize = if (isThumbnail) 9.sp else 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 2.sp,
                                    color = Color(0xFF7ADCF0)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isThumbnail) 6.dp else 16.dp))
                        AmbientStatusRow(
                            settings = settings,
                            telemetry = telemetry,
                            isThumbnail = isThumbnail,
                            accent = Color(0xFF00E5FF)
                        )
                    }
                }

                ScreenSaverStyle.MINIMAL_ANALOG -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val dialSize = if (isThumbnail) 72.dp else 220.dp
                        MinimalAnalogDial(
                            calendar = calendar,
                            animateSeconds = animationsActive && !isThumbnail,
                            modifier = Modifier.size(dialSize)
                        )
                        if (settings.screenSaverShowDate) {
                            Spacer(modifier = Modifier.height(if (isThumbnail) 6.dp else 18.dp))
                            Text(
                                text = dateText,
                                style = TextStyle(
                                    fontSize = if (isThumbnail) 9.sp else 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFFB5CBD8)
                                )
                            )
                        }
                        if (!isThumbnail) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AmbientStatusRow(
                                settings = settings,
                                telemetry = telemetry,
                                isThumbnail = false,
                                accent = Color(0xFF64FFDA)
                            )
                        }
                    }
                }

                ScreenSaverStyle.NEON_CLOCK -> {
                    val ringSize = if (isThumbnail) 92.dp else 258.dp
                    Box(contentAlignment = Alignment.Center) {
                        NeonOrbitalRing(
                            phase = effectivePhase,
                            pulse = effectivePulse,
                            modifier = Modifier.size(ringSize)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeText,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = if (isThumbnail) 21.sp else 54.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00F0FF)
                                )
                            )
                            if (settings.screenSaverShowDate) {
                                Text(
                                    text = dateText,
                                    style = TextStyle(
                                        fontSize = if (isThumbnail) 8.sp else 13.sp,
                                        color = Color(0xFF64FFDA),
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            if (!isThumbnail) {
                                Spacer(modifier = Modifier.height(10.dp))
                                AmbientStatusRow(
                                    settings = settings,
                                    telemetry = telemetry,
                                    isThumbnail = false,
                                    accent = Color(0xFF00F0FF)
                                )
                            }
                        }
                    }
                }

                ScreenSaverStyle.MINIMAL_AOD -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeText,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = if (isThumbnail) 26.sp else 64.sp,
                                fontWeight = FontWeight.ExtraLight,
                                color = Color(0xFFDCE7EE)
                            )
                        )
                        if (settings.screenSaverShowDate) {
                            Spacer(modifier = Modifier.height(if (isThumbnail) 2.dp else 6.dp))
                            Text(
                                text = dateText,
                                style = TextStyle(
                                    fontSize = if (isThumbnail) 9.sp else 14.sp,
                                    color = Color(0xFF8EA4B2)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isThumbnail) 4.dp else 14.dp))
                        AmbientStatusRow(
                            settings = settings,
                            telemetry = telemetry,
                            isThumbnail = isThumbnail,
                            accent = Color(0xFF8EA4B2)
                        )
                    }
                }

                ScreenSaverStyle.AQUA_WAVES -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeText,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = if (isThumbnail) 26.sp else 68.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        )
                        if (settings.screenSaverShowDate) {
                            Text(
                                text = dateText,
                                style = TextStyle(
                                    fontSize = if (isThumbnail) 9.sp else 15.sp,
                                    color = Color(0xFFA7F3FF),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isThumbnail) 5.dp else 14.dp))
                        AmbientStatusRow(
                            settings = settings,
                            telemetry = telemetry,
                            isThumbnail = isThumbnail,
                            accent = Color(0xFF1DE9B6)
                        )
                    }
                }

                ScreenSaverStyle.DIGITAL_INFO -> {
                    Surface(
                        color = Color(0xFF071622).copy(alpha = 0.78f),
                        shape = RoundedCornerShape(if (isThumbnail) 14.dp else 28.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.32f))
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = if (isThumbnail) 10.dp else 28.dp,
                                vertical = if (isThumbnail) 8.dp else 24.dp
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = timeText,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = if (isThumbnail) 22.sp else 58.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            )
                            if (settings.screenSaverShowDate) {
                                Text(
                                    text = dateText,
                                    style = TextStyle(
                                        fontSize = if (isThumbnail) 8.sp else 15.sp,
                                        color = Color(0xFF8AD8EB)
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(if (isThumbnail) 6.dp else 16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isThumbnail) 6.dp else 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (settings.screenSaverShowBattery) {
                                    InfoChipBadge(
                                        icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                        text = if (isThumbnail) {
                                            "${telemetry.batteryPercent}%"
                                        } else {
                                            "${telemetry.batteryPercent}% • ${if (telemetry.isCharging) "Charging" else "Battery"}"
                                        },
                                        accent = if (telemetry.isCharging) Color(0xFF1DE9B6) else Color(0xFF00E5FF),
                                        isThumbnail = isThumbnail
                                    )
                                }
                                if (settings.screenSaverShowNotifications) {
                                    InfoChipBadge(
                                        icon = Icons.Default.Notifications,
                                        text = if (isThumbnail) {
                                            "${telemetry.activeNotificationsCount}"
                                        } else {
                                            "${telemetry.activeNotificationsCount} Alerts"
                                        },
                                        accent = Color(0xFF64FFDA),
                                        isThumbnail = isThumbnail
                                    )
                                }
                            }
                        }
                    }
                }

                ScreenSaverStyle.CUSTOM_MINIMAL -> {
                    val scaleMultiplier = when (settings.screenSaverCustomTextScale) {
                        "COMPACT" -> 0.82f
                        "BOLD_LARGE" -> 1.18f
                        else -> 1.0f
                    }
                    val fontWeight = when (settings.screenSaverCustomTextScale) {
                        "COMPACT" -> FontWeight.Light
                        "BOLD_LARGE" -> FontWeight.Bold
                        else -> FontWeight.Normal
                    }
                    val baseFontSp = (if (isThumbnail) 24f else 66f) * scaleMultiplier

                    val alignment = when (settings.screenSaverCustomLayout) {
                        "LEFT_EDITORIAL" -> Alignment.Start
                        else -> Alignment.CenterHorizontally
                    }

                    Column(horizontalAlignment = alignment) {
                        if (settings.screenSaverCustomLayout == "STACKED") {
                            Text(
                                text = hourText,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = baseFontSp.sp,
                                    fontWeight = fontWeight,
                                    color = Color.White,
                                    lineHeight = baseFontSp.sp
                                )
                            )
                            Text(
                                text = minuteText,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = baseFontSp.sp,
                                    fontWeight = fontWeight,
                                    color = Color(0xFF00E5FF),
                                    lineHeight = baseFontSp.sp
                                )
                            )
                        } else {
                            Text(
                                text = timeText,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = baseFontSp.sp,
                                    fontWeight = fontWeight,
                                    color = Color.White,
                                    textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
                                )
                            )
                        }

                        if (settings.screenSaverShowDate) {
                            Spacer(modifier = Modifier.height(if (isThumbnail) 3.dp else 8.dp))
                            Text(
                                text = dateText,
                                style = TextStyle(
                                    fontSize = if (isThumbnail) 9.sp else 15.sp,
                                    color = Color(0xFF9AC5D6)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isThumbnail) 4.dp else 12.dp))
                        AmbientStatusRow(
                            settings = settings,
                            telemetry = telemetry,
                            isThumbnail = isThumbnail,
                            accent = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }

        if (!isThumbnail) {
            Text(
                text = "Tap anywhere to exit • PandaNex Ambient",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.34f),
                    letterSpacing = 0.6.sp
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }
}

@Composable
private fun AmbientStatusRow(
    settings: PandaSettings,
    telemetry: ScreenSaverTelemetryData,
    isThumbnail: Boolean,
    accent: Color
) {
    if (!settings.screenSaverShowBattery && !settings.screenSaverShowNotifications) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isThumbnail) 6.dp else 14.dp)
    ) {
        if (settings.screenSaverShowBattery) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (telemetry.isCharging) Icons.Default.Bolt else Icons.Default.BatteryStd,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(if (isThumbnail) 11.dp else 16.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${telemetry.batteryPercent}%",
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = if (isThumbnail) 9.sp else 13.sp,
                        color = Color(0xFFD0E6F0)
                    )
                )
            }
        }
        if (settings.screenSaverShowNotifications && (telemetry.activeNotificationsCount > 0 || isThumbnail)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (isThumbnail) 5.dp else 7.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isThumbnail) "${telemetry.activeNotificationsCount}" else "${telemetry.activeNotificationsCount} notifications",
                    style = TextStyle(
                        fontSize = if (isThumbnail) 8.sp else 13.sp,
                        color = Color(0xFFB5D2DF)
                    )
                )
            }
        }
    }
}

@Composable
private fun InfoChipBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accent: Color,
    isThumbnail: Boolean
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isThumbnail) 6.dp else 12.dp,
                vertical = if (isThumbnail) 2.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (isThumbnail) 10.dp else 15.dp)
            )
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = if (isThumbnail) 8.sp else 12.sp,
                    color = Color.White
                )
            )
        }
    }
}

@Composable
private fun MinimalAnalogDial(
    calendar: Calendar,
    animateSeconds: Boolean,
    modifier: Modifier = Modifier
) {
    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 12 minimalist hour ticks
        for (i in 0 until 12) {
            val angleRad = ((i * 30.0 - 90.0) * PI / 180.0)
            val isMajor = i % 3 == 0
            val outer = radius * 0.92f
            val inner = radius * if (isMajor) 0.78f else 0.85f
            drawLine(
                color = if (isMajor) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.42f),
                start = Offset(
                    center.x + (cos(angleRad) * inner).toFloat(),
                    center.y + (sin(angleRad) * inner).toFloat()
                ),
                end = Offset(
                    center.x + (cos(angleRad) * outer).toFloat(),
                    center.y + (sin(angleRad) * outer).toFloat()
                ),
                strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Hour hand
        val hourAngle = (((hour + minute / 60.0) * 30.0 - 90.0) * PI / 180.0)
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(
                center.x + (cos(hourAngle) * radius * 0.50f).toFloat(),
                center.y + (sin(hourAngle) * radius * 0.50f).toFloat()
            ),
            strokeWidth = 3.2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Minute hand
        val minAngle = (((minute + second / 60.0) * 6.0 - 90.0) * PI / 180.0)
        drawLine(
            color = Color(0xFF8CF4FF),
            start = center,
            end = Offset(
                center.x + (cos(minAngle) * radius * 0.72f).toFloat(),
                center.y + (sin(minAngle) * radius * 0.72f).toFloat()
            ),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )

        if (animateSeconds) {
            val secAngle = ((second * 6.0 - 90.0) * PI / 180.0)
            drawLine(
                color = Color(0xFF1DE9B6),
                start = center,
                end = Offset(
                    center.x + (cos(secAngle) * radius * 0.80f).toFloat(),
                    center.y + (sin(secAngle) * radius * 0.80f).toFloat()
                ),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawCircle(color = Color(0xFF00E5FF), radius = 3.5.dp.toPx(), center = center)
    }
}

@Composable
private fun NeonOrbitalRing(
    phase: Float,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.14f * pulse),
            radius = r * 0.96f,
            center = center,
            style = Stroke(width = 8.dp.toPx())
        )
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF1DE9B6),
                    Color(0xFF0077AA),
                    Color(0xFF00E5FF)
                ),
                center = center
            ),
            radius = r * 0.90f,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )

        val dotX = center.x + cos(phase) * (r * 0.90f)
        val dotY = center.y + sin(phase) * (r * 0.90f)
        drawCircle(
            color = Color(0xFF64FFDA),
            radius = 4.dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}

@Composable
private fun AquaDigitalHaloBackdrop(
    pulse: Float,
    isThumbnail: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.minDimension * if (isThumbnail) 0.45f else 0.42f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.16f * pulse),
                    Color(0xFF1DE9B6).copy(alpha = 0.05f * pulse),
                    Color.Transparent
                ),
                center = center,
                radius = radius.coerceAtLeast(12f)
            ),
            radius = radius,
            center = center
        )
    }
}

@Composable
private fun AquaWavesBackdrop(
    phase: Float,
    isThumbnail: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val step = if (isThumbnail) 6f else 10f
        for (layer in 0..2) {
            val baseHeight = h * (0.64f + layer * 0.10f)
            val amp = h * (0.045f - layer * 0.01f)
            val layerPhase = phase + layer * 1.15f
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w + step) {
                    val y = baseHeight + sin((x / w) * (2.5f * PI).toFloat() + layerPhase) * amp
                    lineTo(x.coerceAtMost(w), y)
                    x += step
                }
                lineTo(w, h)
                close()
            }
            val color = when (layer) {
                0 -> Color(0xFF00E5FF).copy(alpha = 0.20f)
                1 -> Color(0xFF1DE9B6).copy(alpha = 0.16f)
                else -> Color(0xFF0097A7).copy(alpha = 0.22f)
            }
            drawPath(path = path, color = color)
        }
    }
}
