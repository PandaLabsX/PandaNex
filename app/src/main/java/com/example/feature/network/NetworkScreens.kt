package com.example.feature.network

import android.Manifest
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaCircularGauge
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaLinearBar
import com.example.core.ui.AquaTopBar
import com.example.core.ui.AquaWaveIndicator
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch

// MODULE 04 — NETWORK DIAGNOSTICS
@Composable
fun NetworkDiagnosticsScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val diag by viewModel.diagProgress.collectAsStateWithLifecycle()
    val history by viewModel.networkTestHistory.collectAsStateWithLifecycle()
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Network Diagnostics",
            subtitle = "Module 04 • Ping, DNS, Packet Loss & Speed Suite",
            onBack = onBack,
            actions = {
                if (diag.progressFraction >= 1f) {
                    IconButton(
                        onClick = {
                            val report = buildString {
                                appendLine("PANDANEX NETWORK DIAGNOSTIC REPORT")
                                appendLine("Connection: ${live.networkInfo?.connectionType}")
                                appendLine("Internet Reachable: ${diag.internetReachable}")
                                appendLine("DNS Latency: ${diag.dnsLatencyMs ?: "N/A"} ms")
                                appendLine("Avg Ping RTT: ${diag.pingAvgMs ?: "N/A"} ms (Min ${diag.pingMinMs ?: "-"} / Max ${diag.pingMaxMs ?: "-"})")
                                appendLine("Packet Loss: ${diag.packetLossPercent ?: 0}%")
                                appendLine("Download Throughput: ${diag.downloadSpeedMbps ?: 0.0} Mbps")
                                appendLine("Upload Throughput: ${diag.uploadSpeedMbps ?: 0.0} Mbps")
                            }
                            SystemUtils.shareTextReport(context, "PandaNex Network Diagnostics", report)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Diagnostic Report")
                    }
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Live Aqua Wave Probe", icon = Icons.Default.NetworkCheck)
                    Spacer(modifier = Modifier.height(6.dp))
                    AquaWaveIndicator(isActive = diag.isRunning)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = diag.stepLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (diag.isRunning || diag.progressFraction > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AquaLinearBar(progress = diag.progressFraction)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.runNetworkDiagnosticsSuite() },
                        enabled = !diag.isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_network_diagnostics_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (diag.isRunning) "Running Live Probes..." else "Start Full Diagnostic Suite")
                    }
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Latest Test Measurements")
                    KeyValueRow(
                        "Internet Connectivity",
                        when (diag.internetReachable) {
                            true -> "Reachable (Verified)"
                            false -> "Unreachable / Offline"
                            null -> "Not Tested Yet"
                        }
                    )
                    KeyValueRow(
                        "Wi-Fi Transport",
                        if (live.networkInfo?.wifiConnected == true) "Connected" else "Not Active"
                    )
                    KeyValueRow(
                        "DNS Resolution",
                        diag.dnsLatencyMs?.let { if (it >= 0) "$it ms" else "Failed" } ?: "—"
                    )
                    KeyValueRow(
                        "TCP Ping Latency (Avg)",
                        diag.pingAvgMs?.let { "$it ms (Min ${diag.pingMinMs}ms / Max ${diag.pingMaxMs}ms)" } ?: "—"
                    )
                    KeyValueRow(
                        "Packet Loss (5 Probes)",
                        diag.packetLossPercent?.let { "$it%" } ?: "—"
                    )
                    KeyValueRow(
                        "Download Speed",
                        diag.downloadSpeedMbps?.let { "$it Mbps" } ?: "—"
                    )
                    KeyValueRow(
                        "Upload Speed",
                        diag.uploadSpeedMbps?.let { "$it Mbps" } ?: "—"
                    )
                    diag.errorNote?.let { note ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (history.isNotEmpty()) {
                item { SectionHeader(title = "Network Diagnostic History", icon = Icons.Default.History) }
                items(history) { item ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.summary, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${SystemUtils.formatTimestamp(item.timestamp)} • Loss ${item.packetLossPercent}% • Up ${item.uploadSpeedMbps} Mbps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = item.connectionType,
                                condition = if (item.internetReachable) HealthCondition.NORMAL else HealthCondition.WARNING
                            )
                        }
                    }
                }
            }
        }
    }
}

// MODULE 09 — WI-FI SIGNAL ANALYZER
@Composable
fun WifiAnalyzerScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current
    val wifi = live.wifiSignal

    val locPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Next telemetry tick updates SSID automatically
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Wi-Fi Signal Analyzer",
            subtitle = "Module 09 • RSSI dBm, Band, Channel & Standard",
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
                            progress = (wifi?.signalQualityPercent ?: 0) / 100f,
                            valueText = wifi?.rssiDbm?.let { "$it" } ?: "--",
                            subtitle = "dBm (${wifi?.signalQualityPercent ?: 0}%)",
                            condition = wifi?.condition ?: HealthCondition.INFO
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                label = wifi?.qualityLabel ?: "Checking",
                                condition = wifi?.condition ?: HealthCondition.INFO
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = wifi?.ssid ?: "Not Connected",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${wifi?.bandLabel ?: "N/A"} • ${wifi?.wifiStandard ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (wifi?.isConnected == true && !wifi.locationPermissionGranted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { locPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Location Permission to Read SSID Name")
                        }
                    }
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Radio & Link Parameters", icon = Icons.Default.Wifi)
                    KeyValueRow("Wi-Fi Radio State", if (wifi?.isWifiEnabled == true) "Enabled" else "Disabled")
                    KeyValueRow("SSID", wifi?.ssid ?: "N/A")
                    KeyValueRow("BSSID (AP MAC)", wifi?.bssid ?: "Protected")
                    KeyValueRow("Signal Strength", wifi?.rssiDbm?.let { "$it dBm (${wifi.signalQualityPercent}%)" } ?: "N/A")
                    KeyValueRow("Link Speed", wifi?.linkSpeedMbps?.let { "$it Mbps" } ?: "N/A")
                    KeyValueRow("Frequency", wifi?.frequencyMhz?.let { "$it MHz (${wifi.bandLabel})" } ?: "N/A")
                    KeyValueRow("Wi-Fi Channel", wifi?.channelNumber?.let { "Channel $it" } ?: "N/A")
                    KeyValueRow("Wi-Fi Standard", wifi?.wifiStandard ?: "N/A")
                    KeyValueRow("IPv4 Address", wifi?.ipv4Address ?: "N/A")
                    KeyValueRow("Gateway", wifi?.gatewayAddress ?: "N/A")
                }
            }

            item {
                OutlinedButton(
                    onClick = { SystemUtils.openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Android Wi-Fi Settings")
                }
            }
        }
    }
}

// MODULE 13 — NETWORK INFORMATION
@Composable
fun NetworkInfoScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current
    val net = live.networkInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Network Information",
            subtitle = "Module 13 • IPv4/IPv6, DNS, Gateway & Interfaces",
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
                        Column {
                            Text("Active Transport", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = net?.connectionType ?: "Disconnected",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        StatusPill(
                            label = if (net?.isValidatedInternet == true) "Validated Internet"
                            else if (net?.isConnected == true) "Local / Captive"
                            else "Offline",
                            condition = if (net?.isValidatedInternet == true) HealthCondition.NORMAL else HealthCondition.WARNING
                        )
                    }
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Addressing & Routing", icon = Icons.Default.Router)
                    KeyValueRow("IPv4 Address", net?.ipv4Address ?: "N/A") {
                        SystemUtils.copyToClipboard(context, "IPv4", net?.ipv4Address ?: "")
                    }
                    KeyValueRow("IPv6 Address", net?.ipv6Address ?: "N/A") {
                        SystemUtils.copyToClipboard(context, "IPv6", net?.ipv6Address ?: "")
                    }
                    KeyValueRow("Default Gateway", net?.gateway ?: "N/A")
                    KeyValueRow("DNS Servers", net?.dnsServers?.joinToString(", ")?.ifBlank { "None" } ?: "N/A")
                    KeyValueRow("Active Interface", net?.interfaceName ?: "None")
                    KeyValueRow("Metered Connection", if (net?.isMetered == true) "Yes (Metered)" else "Unmetered")
                    KeyValueRow("Wi-Fi Transport", if (net?.wifiConnected == true) "Connected" else "Inactive")
                    KeyValueRow("Mobile Data Transport", if (net?.cellularConnected == true) "Connected" else "Inactive")
                    KeyValueRow(
                        "Link Bandwidth Est.",
                        if (net?.downstreamBandwidthKbps != null)
                            "↓ ${net.downstreamBandwidthKbps / 1000} Mbps / ↑ ${(net.upstreamBandwidthKbps ?: 0) / 1000} Mbps"
                        else "Not Reported"
                    )
                }
            }

            item {
                AquaGlassCard {
                    SectionHeader(title = "Active Network Interfaces")
                    if (net?.interfacesSummary.isNullOrEmpty()) {
                        Text(
                            "No active non-loopback interfaces reported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        net?.interfacesSummary?.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// UTILITY — CONNECTION MONITOR
@Composable
fun ConnectionMonitorScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val events by viewModel.connectionEvents.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current
    val net = live.networkInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Connection Monitor",
            subtitle = "Live Wi-Fi, Cellular, Bluetooth & Event Log",
            onBack = onBack,
            actions = {
                if (events.isNotEmpty()) {
                    IconButton(onClick = { scope.launch { viewModel.container.dao.clearConnectionEvents() } }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Connection Events")
                    }
                }
            }
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Current Connectivity Matrix", icon = Icons.Default.Hub)
                    KeyValueRow("Primary Network", net?.connectionType ?: "Disconnected")
                    KeyValueRow("Wi-Fi State", if (net?.wifiConnected == true) "Connected" else "Disconnected")
                    KeyValueRow("Cellular Data", if (net?.cellularConnected == true) "Connected" else "Inactive")
                    KeyValueRow("Bluetooth Adapter", net?.bluetoothStateLabel ?: "Unknown")
                    KeyValueRow("Internet Validation", if (net?.isValidatedInternet == true) "Online & Validated" else "Unvalidated / Offline")
                }
            }

            item { SectionHeader(title = "Connection Event Timeline", icon = Icons.Default.History) }

            if (events.isEmpty()) {
                item {
                    AquaGlassCard {
                        Text(
                            "Listening for Wi-Fi, Mobile, and Internet transitions...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(events) { ev ->
                    AquaGlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${ev.eventType} — ${ev.stateLabel}", style = MaterialTheme.typography.titleSmall)
                                Text(ev.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(SystemUtils.formatTimestamp(ev.timestamp), style = MaterialTheme.typography.labelSmall)
                            }
                            StatusPill(
                                label = ev.stateLabel,
                                condition = if (ev.stateLabel == "Connected") HealthCondition.NORMAL else HealthCondition.WARNING
                            )
                        }
                    }
                }
            }
        }
    }
}
