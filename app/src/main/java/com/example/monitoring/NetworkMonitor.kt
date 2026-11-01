package com.example.monitoring

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.core.ui.HealthCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

data class WifiSignalSnapshot(
    val isWifiEnabled: Boolean,
    val isConnected: Boolean,
    val ssid: String, // Explains if Fine Location permission is needed on Android 8.1+
    val bssid: String,
    val rssiDbm: Int?,
    val signalQualityPercent: Int,
    val qualityLabel: String,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val bandLabel: String,
    val channelNumber: Int?,
    val wifiStandard: String,
    val ipv4Address: String,
    val gatewayAddress: String,
    val locationPermissionGranted: Boolean,
    val condition: HealthCondition
)

data class NetworkInfoSnapshot(
    val connectionType: String, // Wi-Fi, Cellular, Ethernet, VPN, Disconnected
    val isConnected: Boolean,
    val isValidatedInternet: Boolean,
    val isMetered: Boolean,
    val wifiConnected: Boolean,
    val cellularConnected: Boolean,
    val bluetoothStateLabel: String,
    val ipv4Address: String,
    val ipv6Address: String,
    val dnsServers: List<String>,
    val gateway: String,
    val interfaceName: String,
    val downstreamBandwidthKbps: Int?,
    val upstreamBandwidthKbps: Int?,
    val interfacesSummary: List<String>
)

data class DiagnosticProgressState(
    val isRunning: Boolean = false,
    val stepLabel: String = "Ready to run live network diagnostics",
    val progressFraction: Float = 0f,
    val internetReachable: Boolean? = null,
    val wifiConnected: Boolean? = null,
    val dnsLatencyMs: Long? = null,
    val pingAvgMs: Long? = null,
    val pingMinMs: Long? = null,
    val pingMaxMs: Long? = null,
    val packetLossPercent: Int? = null,
    val downloadSpeedMbps: Double? = null,
    val uploadSpeedMbps: Double? = null,
    val errorNote: String? = null
)

class NetworkMonitor(private val context: Context) {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    fun sampleNetworkInfo(): NetworkInfoSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNet?.let { cm.getLinkProperties(it) }

        val wifiConn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellConn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val ethConn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val vpnConn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val connType = when {
            wifiConn && vpnConn -> "Wi-Fi + VPN"
            wifiConn -> "Wi-Fi"
            cellConn -> "Mobile Cellular"
            ethConn -> "Ethernet"
            caps != null -> "Active Network"
            else -> "Disconnected"
        }

        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val linkAddresses = linkProps?.linkAddresses?.map { it.address } ?: emptyList()
        val ipv4 = linkAddresses.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
            ?: findInterfaceAddress(ipv6 = false)
            ?: "Not Assigned"
        val ipv6 = linkAddresses.firstOrNull { it is Inet6Address && !it.isLoopbackAddress }?.hostAddress?.substringBefore("%")
            ?: findInterfaceAddress(ipv6 = true)
            ?: "Not Assigned"

        val dnsList = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
        val gateway = linkProps?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null && it.gateway?.hostAddress != "0.0.0.0" }
            ?.gateway?.hostAddress ?: "Not Reported"

        val ifaceName = linkProps?.interfaceName ?: "None"
        val interfaces = readAllNetworkInterfaces()

        val btLabel = getBluetoothStatusLabel()

        return NetworkInfoSnapshot(
            connectionType = connType,
            isConnected = isConnected,
            isValidatedInternet = validated,
            isMetered = cm.isActiveNetworkMetered,
            wifiConnected = wifiConn,
            cellularConnected = cellConn,
            bluetoothStateLabel = btLabel,
            ipv4Address = ipv4,
            ipv6Address = ipv6,
            dnsServers = dnsList,
            gateway = gateway,
            interfaceName = ifaceName,
            downstreamBandwidthKbps = caps?.linkDownstreamBandwidthKbps,
            upstreamBandwidthKbps = caps?.linkUpstreamBandwidthKbps,
            interfacesSummary = interfaces
        )
    }

    fun sampleWifiSignal(): WifiSignalSnapshot {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNet?.let { cm.getLinkProperties(it) }

        val hasFineLoc = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val wifiEnabled = wm?.isWifiEnabled == true
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        @Suppress("DEPRECATION")
        val wifiInfo: WifiInfo? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                (caps?.transportInfo as? WifiInfo) ?: wm?.connectionInfo
            }
            else -> wm?.connectionInfo
        }

        val rawSsid = wifiInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")
        val ssid = when {
            !wifiConnected -> "Not Connected"
            rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>" -> {
                if (hasFineLoc) "Hidden / OS Restricted SSID" else "Grant Location Permission to View SSID"
            }
            else -> rawSsid
        }

        val bssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" && wifiConnected } ?: "Protected / Unavailable"
        val rssi = wifiInfo?.rssi?.takeIf { wifiConnected && it in -126..-1 }

        val signalPct = if (rssi != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
                val maxLevel = wm.maxSignalLevel.coerceAtLeast(1)
                ((wm.calculateSignalLevel(rssi).toFloat() / maxLevel.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            } else {
                @Suppress("DEPRECATION")
                WifiManager.calculateSignalLevel(rssi, 101).coerceIn(0, 100)
            }
        } else 0

        val (qualityLabel, condition) = when {
            !wifiConnected -> "Disconnected" to HealthCondition.WARNING
            rssi == null -> "Connected" to HealthCondition.INFO
            rssi >= -55 -> "Excellent" to HealthCondition.NORMAL
            rssi >= -67 -> "Good" to HealthCondition.NORMAL
            rssi >= -77 -> "Fair" to HealthCondition.INFO
            else -> "Weak Signal" to HealthCondition.WARNING
        }

        val linkSpeed = wifiInfo?.linkSpeed?.takeIf { wifiConnected && it > 0 }
        val freq = wifiInfo?.frequency?.takeIf { wifiConnected && it > 0 }
        val bandLabel = when {
            freq == null -> "N/A"
            freq in 2400..2500 -> "2.4 GHz"
            freq in 4900..5900 -> "5 GHz"
            freq in 5925..7125 -> "6 GHz (Wi-Fi 6E/7)"
            else -> "$freq MHz"
        }
        val channel = freq?.let { frequencyToChannel(it) }

        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiInfo != null && wifiConnected) {
            when (wifiInfo.wifiStandard) {
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 / 6E (802.11ax)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_LEGACY -> "Legacy 802.11a/b/g"
                else -> "Standard Wi-Fi"
            }
        } else if (wifiConnected) {
            "Supported (API 30+ for exact standard)"
        } else {
            "N/A"
        }

        val ipv4 = linkProps?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress ?: "N/A"

        val gateway = linkProps?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress ?: "N/A"

        return WifiSignalSnapshot(
            isWifiEnabled = wifiEnabled,
            isConnected = wifiConnected,
            ssid = ssid,
            bssid = bssid,
            rssiDbm = rssi,
            signalQualityPercent = signalPct,
            qualityLabel = qualityLabel,
            linkSpeedMbps = linkSpeed,
            frequencyMhz = freq,
            bandLabel = bandLabel,
            channelNumber = channel,
            wifiStandard = standard,
            ipv4Address = ipv4,
            gatewayAddress = gateway,
            locationPermissionGranted = hasFineLoc,
            condition = condition
        )
    }

    suspend fun runFullDiagnostics(onProgress: (DiagnosticProgressState) -> Unit): DiagnosticProgressState =
        withContext(Dispatchers.IO) {
            val netInfo = sampleNetworkInfo()
            var state = DiagnosticProgressState(
                isRunning = true,
                stepLabel = "Checking Wi-Fi & active network interface...",
                progressFraction = 0.12f,
                wifiConnected = netInfo.wifiConnected
            )
            onProgress(state)

            // Step 1: DNS Resolution Latency
            state = state.copy(stepLabel = "Testing DNS resolution (cloudflare / google)...", progressFraction = 0.28f)
            onProgress(state)
            var dnsOk = false
            val dnsMs = measureTimeMillis {
                dnsOk = runCatching {
                    InetAddress.getAllByName("one.one.one.one").isNotEmpty()
                }.getOrDefault(false)
            }
            state = state.copy(dnsLatencyMs = if (dnsOk) dnsMs else -1L)
            onProgress(state)

            // Step 2: Multi-Probe TCP/ICMP Latency & Packet Loss (5 probes)
            state = state.copy(stepLabel = "Measuring latency & packet loss (5 probes)...", progressFraction = 0.50f)
            onProgress(state)
            val probeTimes = mutableListOf<Long>()
            val totalProbes = 5
            repeat(totalProbes) {
                val rtt = tcpPingMs("1.1.1.1", 443, 2500) ?: tcpPingMs("8.8.8.8", 53, 2500)
                if (rtt != null) probeTimes.add(rtt)
            }
            val packetLoss = (((totalProbes - probeTimes.size).toFloat() / totalProbes.toFloat()) * 100f).roundToInt()
            val avgPing = if (probeTimes.isNotEmpty()) probeTimes.average().toLong() else null
            val minPing = probeTimes.minOrNull()
            val maxPing = probeTimes.maxOrNull()
            val reachable = probeTimes.isNotEmpty() || checkHttp204()

            state = state.copy(
                internetReachable = reachable,
                pingAvgMs = avgPing,
                pingMinMs = minPing,
                pingMaxMs = maxPing,
                packetLossPercent = packetLoss,
                stepLabel = "Running live HTTP download speed test...",
                progressFraction = 0.72f
            )
            onProgress(state)

            // Step 3: Download Bandwidth Measurement (Real HTTP payload stream)
            val dlMbps = if (reachable) measureDownloadSpeedMbps() else 0.0
            state = state.copy(
                downloadSpeedMbps = dlMbps,
                stepLabel = "Running live HTTP upload throughput test...",
                progressFraction = 0.90f
            )
            onProgress(state)

            // Step 4: Upload Bandwidth Measurement (Real HTTP POST payload)
            val ulMbps = if (reachable) measureUploadSpeedMbps() else 0.0

            val finalState = state.copy(
                isRunning = false,
                stepLabel = if (reachable) "Diagnostics complete" else "Offline or restricted network detected",
                progressFraction = 1.0f,
                uploadSpeedMbps = ulMbps,
                errorNote = if (!reachable) "Target endpoints could not be reached. Verify internet connectivity." else null
            )
            onProgress(finalState)
            finalState
        }

    private fun tcpPingMs(host: String, port: Int, timeoutMs: Int): Long? {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        }
    }

    private fun checkHttp204(): Boolean {
        return try {
            val req = Request.Builder()
                .url("https://connectivitycheck.gstatic.com/generate_204")
                .get()
                .build()
            okHttp.newCall(req).execute().use { it.isSuccessful || it.code == 204 }
        } catch (_: Exception) {
            false
        }
    }

    private fun measureDownloadSpeedMbps(): Double {
        val urls = listOf(
            "https://speed.cloudflare.com/__down?bytes=1500000",
            "https://www.gstatic.com/webp/gallery/1.jpg"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                val startNs = System.nanoTime()
                var totalBytes = 0L
                okHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val stream = resp.body?.byteStream() ?: return@use
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        totalBytes += read
                    }
                }
                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                if (totalBytes > 4096) {
                    val mbps = ((totalBytes * 8.0) / 1_000_000.0) / elapsedSec
                    return (mbps * 100.0).roundToInt() / 100.0
                }
            } catch (_: Exception) {
                continue
            }
        }
        return 0.0
    }

    private fun measureUploadSpeedMbps(): Double {
        return try {
            val payloadSize = 256 * 1024 // 256 KB binary payload
            val payload = ByteArray(payloadSize) { (it % 127).toByte() }
            val body = payload.toRequestBody("application/octet-stream".toMediaType())
            val req = Request.Builder()
                .url("https://speed.cloudflare.com/__up")
                .post(body)
                .build()
            val startNs = System.nanoTime()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code !in 200..299) return 0.0
            }
            val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
            val mbps = ((payloadSize * 8.0) / 1_000_000.0) / elapsedSec
            (mbps * 100.0).roundToInt() / 100.0
        } catch (_: Exception) {
            0.0
        }
    }

    private fun frequencyToChannel(freqMhz: Int): Int? {
        return when {
            freqMhz == 2484 -> 14
            freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
            freqMhz in 5170..5825 -> (freqMhz - 5000) / 5
            freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
            else -> null
        }
    }

    private fun findInterfaceAddress(ipv6: Boolean): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr.isLoopbackAddress) continue
                    if (!ipv6 && addr is Inet4Address) return addr.hostAddress
                    if (ipv6 && addr is Inet6Address) return addr.hostAddress?.substringBefore("%")
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun readAllNetworkInterfaces(): List<String> {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .map { intf ->
                    val ips = Collections.list(intf.inetAddresses)
                        .filter { !it.isLoopbackAddress }
                        .joinToString(", ") { it.hostAddress?.substringBefore("%") ?: "" }
                    "${intf.name} (MTU ${intf.mtu}): ${ips.ifBlank { "No IP" }}"
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getBluetoothStatusLabel(): String {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: return "Unsupported"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasBtPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasBtPerm) {
                    return if (adapter.isEnabled) "Enabled" else "Disabled"
                }
            }
            if (adapter.isEnabled) "Enabled (Ready)" else "Disabled"
        } catch (_: Exception) {
            "Restricted"
        }
    }
}
