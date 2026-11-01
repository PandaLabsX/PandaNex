package com.example.feature.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaSparklineChart
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors
import kotlin.math.sqrt

private data class FeaturedSensorSpec(
    val typeId: Int,
    val title: String,
    val unit: String
)

// MODULE 11 — SENSOR LAB
@Composable
fun SensorLabScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val aqua = LocalAquaColors.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    val featuredSpecs = remember {
        listOf(
            FeaturedSensorSpec(Sensor.TYPE_ACCELEROMETER, "Accelerometer", "m/s²"),
            FeaturedSensorSpec(Sensor.TYPE_GYROSCOPE, "Gyroscope", "rad/s"),
            FeaturedSensorSpec(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", "μT"),
            FeaturedSensorSpec(Sensor.TYPE_PROXIMITY, "Proximity Sensor", "cm"),
            FeaturedSensorSpec(Sensor.TYPE_LIGHT, "Ambient Light", "lx"),
            FeaturedSensorSpec(Sensor.TYPE_PRESSURE, "Barometric Pressure", "hPa"),
            FeaturedSensorSpec(Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector", "vec")
        )
    }

    val allDeviceSensors = remember { sensorManager.getSensorList(Sensor.TYPE_ALL) }
    var selectedType by remember { mutableStateOf(Sensor.TYPE_ACCELEROMETER) }
    var liveValues by remember { mutableStateOf<List<Float>>(emptyList()) }
    var liveAccuracy by remember { mutableStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    val magnitudeHistory = remember { mutableStateListOf<Float>() }

    val activeSensor = remember(selectedType) { sensorManager.getDefaultSensor(selectedType) }

    DisposableEffect(activeSensor) {
        magnitudeHistory.clear()
        liveValues = emptyList()
        if (activeSensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                private var lastUiUpdateMs = 0L
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateMs < 90L) return
                    lastUiUpdateMs = now
                    val copy = event.values.toList()
                    liveValues = copy
                    val mag = if (copy.size >= 3) {
                        sqrt(copy[0] * copy[0] + copy[1] * copy[1] + copy[2] * copy[2])
                    } else {
                        copy.firstOrNull() ?: 0f
                    }
                    magnitudeHistory.add(mag)
                    if (magnitudeHistory.size > 40) {
                        magnitudeHistory.removeAt(0)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    liveAccuracy = accuracy
                }
            }
            sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val accuracyLabel = when (liveAccuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High Accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium Accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low (Needs Calibration)"
        else -> "Unreliable / Uncalibrated"
    }

    val activeSpec = featuredSpecs.firstOrNull { it.typeId == selectedType }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Sensor Lab",
            subtitle = "Module 11 • Live Hardware Sensors (${allDeviceSensors.size} Detected)",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Selected Sensor Card
            item {
                AquaGlassCard(glowAccent = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSpec?.title ?: (activeSensor?.name ?: "Sensor"),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = activeSensor?.let { "${it.name} • ${it.vendor}" }
                                    ?: "Hardware sensor not present on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            label = if (activeSensor != null) accuracyLabel else "Unsupported",
                            condition = if (activeSensor != null) HealthCondition.NORMAL else HealthCondition.WARNING
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (activeSensor == null) {
                        Text(
                            text = "This Android device does not include a hardware ${activeSpec?.title ?: "selected"} sensor. PandaNex never fabricates missing sensor streams.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val unit = activeSpec?.unit ?: ""
                        val formattedValues = if (liveValues.isEmpty()) {
                            "Waiting for hardware event..."
                        } else {
                            liveValues.take(4).mapIndexed { i, v ->
                                val axis = listOf("X", "Y", "Z", "W").getOrElse(i) { "V$i" }
                                "$axis: ${String.format("%.2f", v)} $unit"
                            }.joinToString("   ")
                        }
                        Text(
                            text = formattedValues,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val minV = (magnitudeHistory.minOrNull() ?: 0f) - 1f
                        val maxV = (magnitudeHistory.maxOrNull() ?: 15f) + 1f
                        AquaSparklineChart(
                            points = magnitudeHistory.toList(),
                            minValue = minV,
                            maxValue = maxV
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        KeyValueRow("Max Range", "${activeSensor.maximumRange} $unit")
                        KeyValueRow("Resolution", "${activeSensor.resolution}")
                        KeyValueRow("Power Draw", "${activeSensor.power} mA")
                    }
                }
            }

            // Primary 7 Sensors Selector List
            item {
                SectionHeader(title = "Primary Sensor Suite (Tap to Inspect Live)", icon = Icons.Default.Sensors)
            }
            items(featuredSpecs) { spec ->
                val hw = remember(spec.typeId) { sensorManager.getDefaultSensor(spec.typeId) }
                val isSelected = selectedType == spec.typeId
                AquaGlassCard(
                    onClick = { selectedType = spec.typeId },
                    glowAccent = isSelected,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(spec.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = hw?.let { "${it.name} (${it.vendor})" } ?: "Not Present on Device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            label = if (hw != null) "Available" else "Unsupported",
                            condition = if (hw != null) HealthCondition.NORMAL else HealthCondition.WARNING
                        )
                    }
                }
            }

            // Complete Hardware Sensor Inventory
            item {
                SectionHeader(title = "All Reported Device Sensors (${allDeviceSensors.size})")
            }
            items(allDeviceSensors) { s ->
                AquaGlassCard(
                    onClick = { selectedType = s.type },
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Vendor: ${s.vendor} • Type: ${s.stringType.substringAfterLast('.')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${s.power} mA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
