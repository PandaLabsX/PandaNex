package com.example.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaAnimationsEnabled
import com.example.ui.theme.LocalAquaColors
import kotlin.math.PI
import kotlin.math.sin

enum class HealthCondition {
    NORMAL,
    INFO,
    WARNING,
    CRITICAL
}

@Composable
fun conditionColor(condition: HealthCondition): Color {
    val aqua = LocalAquaColors.current
    return when (condition) {
        HealthCondition.NORMAL -> aqua.statusNormal
        HealthCondition.INFO -> aqua.statusInfo
        HealthCondition.WARNING -> aqua.statusWarning
        HealthCondition.CRITICAL -> aqua.statusCritical
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AquaTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        },
        actions = actions,
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    )
}

@Composable
fun AquaGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glowAccent: Boolean = false,
    borderColorOverride: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val aqua = LocalAquaColors.current
    val shape = MaterialTheme.shapes.medium
    val borderCol = borderColorOverride ?: if (glowAccent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        aqua.cardBorder
    }

    val containerCol = if (glowAccent) {
        aqua.cardSurfaceElevated.copy(alpha = if (aqua.isDark) 0.94f else 0.98f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = if (aqua.isDark) 0.92f else 0.98f)
    }

    Card(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerCol),
        border = BorderStroke(1.dp, borderCol),
        elevation = CardDefaults.cardElevation(defaultElevation = if (aqua.isDark) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (glowAccent) {
                        Modifier.background(
                            Brush.linearGradient(
                                colors = listOf(
                                    aqua.glowAqua,
                                    Color.Transparent
                                )
                            )
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    condition: HealthCondition = HealthCondition.INFO,
    modifier: Modifier = Modifier
) {
    val baseColor by animateColorAsState(
        targetValue = conditionColor(condition),
        animationSpec = tween(300),
        label = "status_pill_color"
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = baseColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, baseColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(baseColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = baseColor
            )
        }
    }
}

@Composable
fun AquaCircularGauge(
    progress: Float, // 0f..1f
    valueText: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 11.dp,
    condition: HealthCondition = HealthCondition.INFO
) {
    val animationsEnabled = LocalAquaAnimationsEnabled.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (animationsEnabled) tween(650, easing = FastOutSlowInEasing) else tween(0),
        label = "gauge_progress"
    )
    val activeColor = conditionColor(condition)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val sweepAngle = 260f
            val startAngle = 140f
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.7f),
                        activeColor,
                        activeColor
                    )
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AquaLinearBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    condition: HealthCondition = HealthCondition.INFO
) {
    val animationsEnabled = LocalAquaAnimationsEnabled.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (animationsEnabled) tween(500, easing = FastOutSlowInEasing) else tween(0),
        label = "linear_bar_progress"
    )
    val activeColor = conditionColor(condition)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            activeColor
                        )
                    )
                )
        )
    }
}

@Composable
fun AquaSparklineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 100f,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val fillStart = lineColor.copy(alpha = 0.28f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Subtle horizontal grid lines
        val gridCount = 3
        for (i in 0..gridCount) {
            val y = h * (i.toFloat() / gridCount)
            drawLine(
                color = lineColor.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (points.isEmpty()) return@Canvas

        val safeRange = (maxValue - minValue).coerceAtLeast(1f)
        val displayPoints = if (points.size == 1) listOf(points[0], points[0]) else points
        val stepX = w / (displayPoints.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        displayPoints.forEachIndexed { index, value ->
            val normalized = ((value - minValue) / safeRange).coerceIn(0f, 1f)
            val x = index * stepX
            val y = h - (normalized * (h * 0.88f)) - (h * 0.06f)

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevNorm = ((displayPoints[index - 1] - minValue) / safeRange).coerceIn(0f, 1f)
                val prevY = h - (prevNorm * (h * 0.88f)) - (h * 0.06f)
                val controlX = (prevX + x) / 2f
                linePath.cubicTo(controlX, prevY, controlX, y, x, y)
                fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
            }

            if (index == displayPoints.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillStart, Color.Transparent),
                startY = 0f,
                endY = h
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun AquaWaveIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary
) {
    val animationsEnabled = LocalAquaAnimationsEnabled.current
    val transition = rememberInfiniteTransition(label = "aqua_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        val w = size.width
        val h = size.height
        val midY = h * 0.55f
        val amplitude = if (isActive) h * 0.24f else h * 0.06f
        val activePhase = if (isActive && animationsEnabled) phase else 0.6f

        val wavePath = Path().apply {
            moveTo(0f, h)
            var x = 0f
            val step = 6f
            while (x <= w) {
                val angle = (x / w) * (3.5f * PI).toFloat() + activePhase
                val y = midY + sin(angle) * amplitude
                lineTo(x, y)
                x += step
            }
            lineTo(w, h)
            close()
        }

        drawPath(
            path = wavePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    waveColor.copy(alpha = if (isActive) 0.5f else 0.2f),
                    waveColor.copy(alpha = 0.08f)
                )
            )
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector? = null,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (trailingText != null && onTrailingClick != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTrailingClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    monoValue: Boolean = true,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Row(
            modifier = Modifier.weight(0.58f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = if (monoValue) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
