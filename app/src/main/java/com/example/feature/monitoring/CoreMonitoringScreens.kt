package com.example.feature.monitoring

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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaCircularGauge
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaLinearBar
import com.example.core.ui.AquaSparklineChart
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.monitoring.ThermalLevel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch
import kotlin.math.abs

// MODULE 01 — CPU & PERFORMANCE MONITOR
@Composable
fun CpuMonitorScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.cpuHistoryDb.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current
    val cpu = live.cpu

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "CPU & Performance",
            subtitle = "Module 01 • Real-time SoC & Core Telemetry",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true, modifier = Modifier.testTag("cpu_overview_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AquaCircularGauge(
                            progress = (cpu?.overallUsagePercent ?: 0f) / 100f,
                            valueText = "${cpu?.overallUsagePercent ?: 0f}%",
                            subtitle = "CPU LOAD",
                            condition = cpu?.condition ?: HealthCondition.NORMAL
                        )
                        Spacer(modifier = Modifier.padding(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                label = cpu?.conditionLabel ?: "Optimal",
                                condition = cpu?.condition ?: HealthCondition.NORMAL
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = cpu?.socModel ?: "Detecting Processor...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Source: ${cpu?.usageSource ?: "Sampling..."}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cpu?.cpuTemperatureCelsius?.let { "CPU Thermal Zone: ${it}°C" }
                                    ?: "CPU Thermal Zone: Restricted by OS SELinux",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AquaSparklineChart(points = live.cpuGraphPoints)
                }
            }

            // Refresh Interval & High Load Alert Threshold
            item {
                AquaGlassCard {
                    SectionHeader(title = "Refresh & Alert Threshold", icon = Icons.Default.Speed)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1000L to "1.0s", 1500L to "1.5s", 3000L to "3.0s", 5000L to "5.0s").forEach { (ms, label) ->
                            FilterChip(
                                selected = settings.cpuRefreshMs == ms,
                                onClick = { viewModel.setCpuRefreshInterval(ms) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "High CPU Alert Threshold: ${settings.cpuAlertThresholdPercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.cpuAlertThresholdPercent,
                        onValueChange = { pct ->
                            scope.launch { viewModel.prefs.setThresholds(cpuPercent = pct) }
                        },
                        valueRange = 55f..98f
                    )
                }
            }

            // Processor Architecture Details
            item {
                AquaGlassCard {
                    SectionHeader(title = "Processor Details")
                    KeyValueRow("SoC / Model", cpu?.socModel ?: "N/A")
                    KeyValueRow("Hardware Board", cpu?.socHardware ?: "N/A")
                    KeyValueRow("Primary ABI", cpu?.primaryAbi ?: "N/A")
                    KeyValueRow("Supported ABIs", cpu?.supportedAbis ?: "N/A")
                    KeyValueRow("Active / Total Cores", "${cpu?.activeCoreCount ?: 0} / ${cpu?.coreCount ?: 0}")
                    KeyValueRow("Kernel Load Avg", cpu?.loadAverage ?: "Restricted by OS")
                }
            }

            // Per-Core Frequencies
            item {
                AquaGlassCard {
                    SectionHeader(title = "Per-Core Frequency Scaling")
                    cpu?.cores?.forEach { core ->
                        val cur = core.currentFreqMhz
                        val max = core.maxFreqMhz
                        val min = core.minFreqMhz
                        val freqText = when {
                            !core.isOnline -> "Offline (Parked)"
                            cur != null && max != null -> "$cur MHz / $max MHz"
                            cur != null -> "$cur MHz"
                            else -> "Online (Freq Restricted)"
                        }
                        val ratio = if (core.isOnline && cur != null && max != null && max > 0) {
                            (cur.toFloat() / max.toFloat()).coerceIn(0.05f, 1f)
                        } else 0f

                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Core #${core.coreIndex}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = if (min != null && max != null) "$freqText (Min $min)" else freqText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (cur != null && max != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                AquaLinearBar(progress = ratio, height = 6.dp)
                            }
                        }
                    }
                }
            }

            // CPU History
            if (history.isNotEmpty()) {
                item {
                    SectionHeader(title = "Recent CPU Snapshots", icon = Icons.Default.History)
                }
                items(history.take(10)) { item ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(SystemUtils.formatTimestamp(item.timestamp), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${item.primaryValue}% • ${item.statusLabel}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// MODULE 07 — RAM MONITOR
@Composable
fun RamMonitorScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val history by viewModel.ramHistoryDb.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current
    val ram = live.ram

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "RAM Memory Monitor",
            subtitle = "Module 07 • ActivityManager Live Memory Telemetry",
            onBack = onBack
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AquaCircularGauge(
                            progress = (ram?.usagePercent ?: 0f) / 100f,
                            valueText = "${ram?.usagePercent ?: 0f}%",
                            subtitle = "RAM USED",
                            condition = ram?.condition ?: HealthCondition.NORMAL
                        )
                        Spacer(modifier = Modifier.padding(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                label = ram?.conditionLabel ?: "Healthy",
                                condition = ram?.condition ?: HealthCondition.NORMAL
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            KeyValueRow("Used RAM", SystemUtils.formatBytes(ram?.usedBytes ?: 0L))
                            KeyValueRow("Available", SystemUtils.formatBytes(ram?.availableBytes ?: 0L))
                            KeyValueRow("Total RAM", SystemUtils.formatBytes(ram?.totalBytes ?: 0L))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AquaSparklineChart(points = live.ramGraphPoints)
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "System Memory Thresholds", icon = Icons.Default.Memory)
                    KeyValueRow("OS Low-Memory Trigger", SystemUtils.formatBytes(ram?.thresholdBytes ?: 0L))
                    KeyValueRow("Low-Memory Flag Active", if (ram?.isLowMemory == true) "YES (High Pressure)" else "No (Normal)")
                }
            }

            if (history.isNotEmpty()) {
                item { SectionHeader(title = "RAM Monitoring History", icon = Icons.Default.History) }
                items(history.take(10)) { entry ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(SystemUtils.formatTimestamp(entry.timestamp), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${entry.primaryValue}% Used (${entry.secondaryValue.toInt()} MB Free)",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// MODULE 02 — BATTERY MONITOR
@Composable
fun BatteryMonitorScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.batteryHistoryDb.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current
    val bat = live.battery

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Battery Monitor",
            subtitle = "Module 02 • Charge, Health & Thermal Telemetry",
            onBack = onBack
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AquaCircularGauge(
                            progress = (bat?.percentage ?: 0) / 100f,
                            valueText = "${bat?.percentage ?: 0}%",
                            subtitle = bat?.statusLabel?.uppercase() ?: "BATTERY",
                            condition = bat?.condition ?: HealthCondition.NORMAL
                        )
                        Spacer(modifier = Modifier.padding(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                label = bat?.conditionSummary ?: "Nominal",
                                condition = bat?.condition ?: HealthCondition.NORMAL
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = bat?.chargingSource ?: "On Battery",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Temperature: ${bat?.temperatureCelsius?.let { "${it}°C" } ?: "Not Reported"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            bat?.chargeTimeRemainingMs?.let { rem ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Est. Full In: ${SystemUtils.formatDurationMillis(rem)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Hardware Battery Properties", icon = Icons.Default.BatteryChargingFull)
                    KeyValueRow("Charging Status", bat?.statusLabel ?: "N/A")
                    KeyValueRow("Power Source", bat?.chargingSource ?: "N/A")
                    KeyValueRow("Reported Health", bat?.healthLabel ?: "N/A")
                    KeyValueRow("Chemistry / Tech", bat?.technology ?: "N/A")
                    KeyValueRow("Voltage", bat?.voltageMv?.let { "$it mV" } ?: "Unavailable")
                    KeyValueRow(
                        "Instant Current",
                        bat?.currentMicroAmps?.let { "${abs(it / 1000)} mA" } ?: "Not Exposed by Driver"
                    )
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Configurable Battery Alert Thresholds")
                    Text(
                        text = "Low Battery Notification: ≤ ${settings.lowBatteryThresholdPercent}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.lowBatteryThresholdPercent.toFloat(),
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.setThresholds(lowBatteryPercent = v.toInt()) }
                        },
                        valueRange = 10f..45f
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "High Battery Temperature Warning: ≥ ${String.format("%.1f", settings.highBatteryTempCelsius)}°C",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.highBatteryTempCelsius,
                        onValueChange = { v ->
                            scope.launch { viewModel.prefs.setThresholds(highBatteryTempC = v) }
                        },
                        valueRange = 36f..48f
                    )
                }
            }

            if (history.isNotEmpty()) {
                item { SectionHeader(title = "Battery History", icon = Icons.Default.History) }
                items(history.take(10)) { item ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(SystemUtils.formatTimestamp(item.timestamp), style = MaterialTheme.typography.bodySmall)
                            val tempStr = if (item.secondaryValue > 0f) " • ${item.secondaryValue}°C" else ""
                            Text(
                                "${item.primaryValue.toInt()}%$tempStr (${item.statusLabel})",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// MODULE 03 — DEVICE HEAT GUARD
@Composable
fun HeatGuardScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val history by viewModel.thermalHistoryDb.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current
    val thermal = live.thermal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Device Heat Guard",
            subtitle = "Module 03 • Android PowerManager Thermal Protection",
            onBack = onBack
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Thermal Status", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = thermal?.thermalLevel?.displayLabel ?: "Checking...",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        StatusPill(
                            label = thermal?.thermalLevel?.displayLabel ?: "Normal",
                            condition = thermal?.thermalLevel?.condition ?: HealthCondition.NORMAL
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = thermal?.thermalLevel?.coolingAdvice
                            ?: "Monitoring device thermal headroom using official PowerManager APIs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Verified Sensor Readings", icon = Icons.Default.Thermostat)
                    KeyValueRow(
                        "OS Thermal API",
                        if (thermal?.powerManagerStatusSupported == true) "PowerManager Supported" else "Requires Android 10+"
                    )
                    KeyValueRow(
                        "Battery Thermistor",
                        thermal?.batteryTempCelsius?.let { "${it}°C" } ?: "Not Reported"
                    )
                    KeyValueRow(
                        "CPU Thermal Zone",
                        thermal?.cpuSensorTempCelsius?.let { "${it}°C" }
                            ?: "Not Exposed (Protected by OS SELinux)"
                    )
                    KeyValueRow(
                        "Thermal Headroom (5s)",
                        thermal?.thermalHeadroom?.let { String.format("%.2f (1.0 = Throttling)", it) }
                            ?: "Not Supported by HAL"
                    )
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Android Thermal Scale Reference")
                    listOf(
                        ThermalLevel.NONE,
                        ThermalLevel.LIGHT,
                        ThermalLevel.MODERATE,
                        ThermalLevel.SEVERE,
                        ThermalLevel.CRITICAL,
                        ThermalLevel.EMERGENCY,
                        ThermalLevel.SHUTDOWN
                    ).forEach { lvl ->
                        val isCurrent = thermal?.thermalLevel == lvl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCurrent) "▶ ${lvl.displayLabel} (Current)" else lvl.displayLabel,
                                style = if (isCurrent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusPill(label = lvl.displayLabel, condition = lvl.condition)
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item { SectionHeader(title = "Thermal Status History", icon = Icons.Default.History) }
                items(history.take(8)) { h ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(SystemUtils.formatTimestamp(h.timestamp), style = MaterialTheme.typography.bodySmall)
                            val batTmp = if (h.secondaryValue > 0f) " • Bat ${h.secondaryValue}°C" else ""
                            Text(
                                "${h.statusLabel}$batTmp",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// MODULE 16 — STORAGE MONITOR (STRICTLY READ-ONLY)
@Composable
fun StorageMonitorScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current
    val st = live.storage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Storage Monitor",
            subtitle = "Module 16 • Strictly Read-Only Partition Analysis",
            onBack = onBack
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AquaCircularGauge(
                            progress = (st?.usedPercent ?: 0f) / 100f,
                            valueText = "${st?.usedPercent ?: 0f}%",
                            subtitle = "USED",
                            condition = st?.condition ?: HealthCondition.NORMAL
                        )
                        Spacer(modifier = Modifier.padding(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                label = st?.conditionLabel ?: "Nominal",
                                condition = st?.condition ?: HealthCondition.NORMAL
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            KeyValueRow("Used Space", SystemUtils.formatBytes(st?.usedBytes ?: 0L))
                            KeyValueRow("Remaining Free", SystemUtils.formatBytes(st?.availableBytes ?: 0L))
                            KeyValueRow("Total Capacity", SystemUtils.formatBytes(st?.totalBytes ?: 0L))
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AquaLinearBar(
                        progress = (st?.usedPercent ?: 0f) / 100f,
                        height = 12.dp,
                        condition = st?.condition ?: HealthCondition.NORMAL
                    )
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Internal Partition Details", icon = Icons.Default.Storage)
                    KeyValueRow("Mount Path", st?.internalPath ?: "/data")
                    KeyValueRow("Used Percentage", "${st?.usedPercent ?: 0f}%")
                    KeyValueRow("Remaining Percentage", "${st?.remainingPercent ?: 0f}%")
                    KeyValueRow("Filesystem Block Size", "${st?.blockSizeBytes ?: 4096} bytes")
                    KeyValueRow("Total Blocks", "${st?.totalBlocks ?: 0L}")
                    KeyValueRow("Available Blocks", "${st?.availableBlocks ?: 0L}")
                }
            }

            item {
                AquaGlassCard {
                    Text(
                        text = "Read-Only Integrity Guarantee",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PandaNex Storage Monitor operates in strictly read-only telemetry mode via Android StatFs. It never modifies, deletes, or touches user files or app caches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
