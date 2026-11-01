package com.example.feature.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.PandaNexApplication
import com.example.automation.AquaDynamicsEngineSnapshot
import com.example.automation.AutoDimStatus
import com.example.automation.EdgeGlowActiveState
import com.example.automation.FullScreenDetectionState
import com.example.automation.LiveGestureTelemetry
import com.example.automation.QuickCaptureSensitivity
import com.example.automation.QuickCaptureState
import com.example.automation.SensorMonitoringService
import com.example.core.ui.HealthCondition
import com.example.data.local.database.AlertNotificationEntity
import com.example.data.local.database.AutomationEventEntity
import com.example.data.local.database.AutomationRuleEntity
import com.example.data.local.database.ConnectionEventEntity
import com.example.data.local.database.MetricHistoryEntity
import com.example.data.local.database.NetworkTestEntity
import com.example.data.local.datastore.PandaSettings
import com.example.monitoring.BatterySnapshot
import com.example.monitoring.CpuSnapshot
import com.example.monitoring.DiagnosticProgressState
import com.example.monitoring.NetworkInfoSnapshot
import com.example.monitoring.RamSnapshot
import com.example.monitoring.StorageSnapshot
import com.example.monitoring.ThermalLevel
import com.example.monitoring.ThermalSnapshot
import com.example.monitoring.WifiSignalSnapshot
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OverallHealthSummary(
    val statusLabel: String, // Excellent, Good, Attention Required, Warning, Critical
    val condition: HealthCondition,
    val scorePercent: Int,
    val explanationBullets: List<String>
)

data class LiveSystemState(
    val deviceModel: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val uptimeMillis: Long = SystemClock.elapsedRealtime(),
    val cpu: CpuSnapshot? = null,
    val cpuGraphPoints: List<Float> = emptyList(),
    val ram: RamSnapshot? = null,
    val ramGraphPoints: List<Float> = emptyList(),
    val battery: BatterySnapshot? = null,
    val thermal: ThermalSnapshot? = null,
    val storage: StorageSnapshot? = null,
    val networkInfo: NetworkInfoSnapshot? = null,
    val wifiSignal: WifiSignalSnapshot? = null,
    val overallHealth: OverallHealthSummary = OverallHealthSummary(
        statusLabel = "Analyzing...",
        condition = HealthCondition.INFO,
        scorePercent = 100,
        explanationBullets = listOf("Sampling hardware telemetry...")
    )
)

class PandaMainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PandaNexApplication
    val container = app.container
    private val dao = container.dao
    val prefs = container.preferencesRepository

    val settings: StateFlow<PandaSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PandaSettings()
    )

    val gestureTelemetry: StateFlow<LiveGestureTelemetry> = container.automationEngine.telemetry
    val quickCaptureState: StateFlow<QuickCaptureState> = container.quickCaptureController.state
    val edgeGlowState: StateFlow<EdgeGlowActiveState> = container.edgeGlowManager.state
    val aquaDynamicsState: StateFlow<AquaDynamicsEngineSnapshot> = container.aquaDynamicsManager.state
    val autoDimStatus: StateFlow<AutoDimStatus> = container.autoDimController.status
    val fullScreenState: StateFlow<FullScreenDetectionState> = container.fullScreenDetector.state

    val notifications: StateFlow<List<AlertNotificationEntity>> = dao.observeNotifications().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unreadAlertCount: StateFlow<Int> = dao.observeUnreadCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val cpuHistoryDb: StateFlow<List<MetricHistoryEntity>> = dao.observeMetricHistory("CPU", 40).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val ramHistoryDb: StateFlow<List<MetricHistoryEntity>> = dao.observeMetricHistory("RAM", 40).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val batteryHistoryDb: StateFlow<List<MetricHistoryEntity>> = dao.observeMetricHistory("BATTERY", 40).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val thermalHistoryDb: StateFlow<List<MetricHistoryEntity>> = dao.observeMetricHistory("THERMAL", 40).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val networkTestHistory: StateFlow<List<NetworkTestEntity>> = dao.observeNetworkTests().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val automationRules: StateFlow<List<AutomationRuleEntity>> = dao.observeAutomationRules().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val automationEvents: StateFlow<List<AutomationEventEntity>> = dao.observeAutomationEvents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val connectionEvents: StateFlow<List<ConnectionEventEntity>> = dao.observeConnectionEvents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _liveState = MutableStateFlow(LiveSystemState())
    val liveState: StateFlow<LiveSystemState> = _liveState.asStateFlow()

    private val _diagProgress = MutableStateFlow(DiagnosticProgressState())
    val diagProgress: StateFlow<DiagnosticProgressState> = _diagProgress.asStateFlow()

    private var samplingJob: Job? = null
    private var dbSnapshotTick = 0
    private var networkCallbackRegistered = false

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                val net = container.networkMonitor.sampleNetworkInfo()
                dao.insertConnectionEvent(
                    ConnectionEventEntity(
                        eventType = net.connectionType.uppercase(),
                        stateLabel = "Connected",
                        details = "Interface ${net.interfaceName} • IPv4 ${net.ipv4Address}"
                    )
                )
                container.automationEngine.evaluateRulesForTrigger(
                    if (net.wifiConnected) "WIFI_CONNECTED" else "NETWORK_CHANGED"
                )
            }
        }

        override fun onLost(network: Network) {
            viewModelScope.launch {
                dao.insertConnectionEvent(
                    ConnectionEventEntity(
                        eventType = "NETWORK",
                        stateLabel = "Disconnected",
                        details = "Active network transport lost"
                    )
                )
                container.automationEngine.evaluateRulesForTrigger("WIFI_DISCONNECTED")
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            viewModelScope.launch {
                val net = container.networkMonitor.sampleNetworkInfo()
                val wifi = container.networkMonitor.sampleWifiSignal()
                _liveState.value = _liveState.value.copy(networkInfo = net, wifiSignal = wifi)
            }
        }
    }

    init {
        startTelemetryLoops()
        observeBatteryStream()
        registerNetworkMonitorCallback()
    }

    private fun startTelemetryLoops() {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            while (isActive) {
                val s = settings.value
                val cpu = container.cpuMonitor.sample()
                val ram = container.ramMonitor.sample()
                val storage = container.storageMonitor.sample()
                val battery = if (s.batteryMonitoringEnabled) container.batteryMonitor.sampleNow() else _liveState.value.battery
                val thermal = if (s.thermalMonitoringEnabled) container.thermalMonitor.sample() else _liveState.value.thermal
                val netInfo = container.networkMonitor.sampleNetworkInfo()
                val wifiSig = container.networkMonitor.sampleWifiSignal()

                val updatedCpuPoints = (_liveState.value.cpuGraphPoints + cpu.overallUsagePercent).takeLast(32)
                val updatedRamPoints = (_liveState.value.ramGraphPoints + ram.usagePercent).takeLast(32)

                val health = computeOverallHealth(cpu, ram, battery, thermal, storage, netInfo)

                _liveState.value = _liveState.value.copy(
                    uptimeMillis = SystemClock.elapsedRealtime(),
                    cpu = cpu,
                    cpuGraphPoints = updatedCpuPoints,
                    ram = ram,
                    ramGraphPoints = updatedRamPoints,
                    battery = battery,
                    thermal = thermal,
                    storage = storage,
                    networkInfo = netInfo,
                    wifiSignal = wifiSig,
                    overallHealth = health
                )

                // Evaluate alert thresholds & smart battery/charging triggers
                container.alertEngine.evaluateTelemetry(
                    cpu = cpu,
                    ram = ram,
                    battery = battery,
                    thermal = thermal,
                    storage = storage,
                    internetConnected = netInfo.isConnected
                )
                container.aquaDynamicsManager.onLiveTelemetrySampled(
                    cpu = cpu,
                    battery = battery,
                    thermal = thermal,
                    network = netInfo
                )

                if (battery != null && battery.percentage <= 25 && !battery.isCharging) {
                    container.automationEngine.evaluateRulesForTrigger("BATTERY_BELOW")
                }

                // Persist historical snapshot every ~6 ticks so Room DB stays fast and compact
                dbSnapshotTick++
                if (dbSnapshotTick % 6 == 0) {
                    dao.insertMetricSnapshot(
                        MetricHistoryEntity(
                            metricType = "CPU",
                            primaryValue = cpu.overallUsagePercent,
                            secondaryValue = cpu.cpuTemperatureCelsius ?: -1f,
                            statusLabel = cpu.conditionLabel
                        )
                    )
                    dao.insertMetricSnapshot(
                        MetricHistoryEntity(
                            metricType = "RAM",
                            primaryValue = ram.usagePercent,
                            secondaryValue = (ram.availableBytes / (1024f * 1024f)),
                            statusLabel = ram.conditionLabel
                        )
                    )
                    if (battery != null) {
                        dao.insertMetricSnapshot(
                            MetricHistoryEntity(
                                metricType = "BATTERY",
                                primaryValue = battery.percentage.toFloat(),
                                secondaryValue = battery.temperatureCelsius ?: -1f,
                                statusLabel = battery.statusLabel
                            )
                        )
                    }
                    if (thermal != null) {
                        dao.insertMetricSnapshot(
                            MetricHistoryEntity(
                                metricType = "THERMAL",
                                primaryValue = thermal.thermalLevel.ordinal.toFloat(),
                                secondaryValue = thermal.batteryTempCelsius ?: -1f,
                                statusLabel = thermal.thermalLevel.displayLabel
                            )
                        )
                    }
                }

                container.automationEngine.refreshSystemStatus()
                container.edgeGlowManager.refreshPermissions()
                container.aquaDynamicsManager.refreshEffectiveCallState()
                container.autoDimController.refreshPermissions()
                container.fullScreenDetector.evaluateNow()
                delay(s.cpuRefreshMs.coerceIn(750L, 10000L))
            }
        }
    }

    private fun observeBatteryStream() {
        var lastCharging: Boolean? = null
        viewModelScope.launch {
            container.batteryMonitor.observeBattery().collect { snap ->
                _liveState.value = _liveState.value.copy(battery = snap)
                if (lastCharging != null && lastCharging != snap.isCharging) {
                    container.automationEngine.evaluateRulesForTrigger(
                        if (snap.isCharging) "CHARGING_CONNECTED" else "CHARGING_DISCONNECTED"
                    )
                }
                lastCharging = snap.isCharging
            }
        }
    }

    private fun registerNetworkMonitorCallback() {
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(connectivityCallback)
            networkCallbackRegistered = true
        } catch (_: Exception) {
            networkCallbackRegistered = false
        }
    }

    override fun onCleared() {
        if (networkCallbackRegistered) {
            runCatching {
                val cm = app.getSystemService(ConnectivityManager::class.java)
                cm?.unregisterNetworkCallback(connectivityCallback)
            }
        }
        super.onCleared()
    }

    private fun computeOverallHealth(
        cpu: CpuSnapshot,
        ram: RamSnapshot,
        battery: BatterySnapshot?,
        thermal: ThermalSnapshot?,
        storage: StorageSnapshot,
        net: NetworkInfoSnapshot
    ): OverallHealthSummary {
        var score = 100
        val notes = mutableListOf<String>()

        if (cpu.overallUsagePercent >= 88f) {
            score -= 22
            notes.add("CPU load is high (${cpu.overallUsagePercent}% via ${cpu.usageSource}).")
        } else {
            notes.add("CPU operating smoothly (${cpu.overallUsagePercent}% load across ${cpu.activeCoreCount}/${cpu.coreCount} cores).")
        }

        if (ram.isLowMemory || ram.usagePercent >= 90f) {
            score -= 20
            notes.add("Memory pressure detected (${ram.usagePercent}% RAM used).")
        } else {
            notes.add("RAM headroom healthy (${100f - ram.usagePercent}% available).")
        }

        if (battery != null) {
            if (battery.temperatureCelsius != null && battery.temperatureCelsius >= 42f) {
                score -= 25
                notes.add("Battery temperature is elevated (${battery.temperatureCelsius}°C).")
            } else if (battery.percentage <= 15 && !battery.isCharging) {
                score -= 15
                notes.add("Battery charge is low (${battery.percentage}%).")
            } else {
                val tempTxt = battery.temperatureCelsius?.let { " at ${it}°C" } ?: ""
                notes.add("Battery health '${battery.healthLabel}' (${battery.percentage}%$tempTxt).")
            }
        }

        if (thermal != null && thermal.powerManagerStatusSupported) {
            when (thermal.thermalLevel) {
                ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN -> {
                    score -= 35
                    notes.add("System thermal status is ${thermal.thermalLevel.displayLabel}.")
                }
                ThermalLevel.MODERATE, ThermalLevel.SEVERE -> {
                    score -= 18
                    notes.add("Thermal status is ${thermal.thermalLevel.displayLabel}.")
                }
                else -> notes.add("System thermal state is ${thermal.thermalLevel.displayLabel}.")
            }
        }

        if (storage.usedPercent >= 92f) {
            score -= 15
            notes.add("Internal storage is nearly full (${storage.usedPercent}% used).")
        } else {
            notes.add("Storage capacity nominal (${storage.remainingPercent}% free).")
        }

        if (!net.isConnected) {
            score -= 8
            notes.add("Network is currently offline.")
        } else {
            notes.add("Connected via ${net.connectionType} (${if (net.isValidatedInternet) "Validated Internet" else "Captive/Local"}).")
        }

        val finalScore = score.coerceIn(10, 100)
        val (label, cond) = when {
            finalScore >= 90 -> "Excellent" to HealthCondition.NORMAL
            finalScore >= 78 -> "Good" to HealthCondition.NORMAL
            finalScore >= 62 -> "Attention Required" to HealthCondition.INFO
            finalScore >= 45 -> "Warning" to HealthCondition.WARNING
            else -> "Critical" to HealthCondition.CRITICAL
        }

        return OverallHealthSummary(
            statusLabel = label,
            condition = cond,
            scorePercent = finalScore,
            explanationBullets = notes
        )
    }

    // Network Diagnostics Runner
    fun runNetworkDiagnosticsSuite() {
        if (_diagProgress.value.isRunning) return
        viewModelScope.launch {
            val result = container.networkMonitor.runFullDiagnostics { progress ->
                _diagProgress.value = progress
            }
            val net = container.networkMonitor.sampleNetworkInfo()
            val summary = if (result.internetReachable == true) {
                "Online • Ping ${result.pingAvgMs ?: 0}ms • Down ${result.downloadSpeedMbps ?: 0.0} Mbps"
            } else {
                "Connectivity Check Failed"
            }
            dao.insertNetworkTest(
                NetworkTestEntity(
                    connectionType = net.connectionType,
                    internetReachable = result.internetReachable == true,
                    dnsLatencyMs = result.dnsLatencyMs ?: -1L,
                    pingLatencyMs = result.pingAvgMs ?: -1L,
                    packetLossPercent = result.packetLossPercent ?: 100,
                    downloadSpeedMbps = result.downloadSpeedMbps ?: 0.0,
                    uploadSpeedMbps = result.uploadSpeedMbps ?: 0.0,
                    summary = summary
                )
            )
            container.alertEngine.recordDiagnosticEvent(
                category = "DIAGNOSTIC",
                title = "Network Diagnostics Completed",
                message = summary,
                statusDetail = net.connectionType,
                severity = if (result.internetReachable == true) "INFO" else "WARNING"
            )
        }
    }

    // Settings & Theme Actions
    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setAnimationsEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setHapticFeedbackEnabled(enabled) }
    }

    fun setHideAquaDynamicsInFullScreen(enabled: Boolean) {
        viewModelScope.launch { prefs.setHideAquaDynamicsInFullScreen(enabled) }
    }

    fun setInAppFullScreen(active: Boolean) {
        container.fullScreenDetector.setInAppFullScreen(active)
    }

    fun setCpuRefreshInterval(ms: Long) {
        viewModelScope.launch { prefs.setCpuRefreshMs(ms) }
    }

    fun setRamRefreshInterval(ms: Long) {
        viewModelScope.launch { prefs.setRamRefreshMs(ms) }
    }

    fun setBatteryMonitoring(enabled: Boolean) {
        viewModelScope.launch { prefs.setBatteryMonitoringEnabled(enabled) }
    }

    fun setThermalMonitoring(enabled: Boolean) {
        viewModelScope.launch { prefs.setThermalMonitoringEnabled(enabled) }
    }

    fun setBackgroundGestureService(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateSmartActionSettings(backgroundMonitoring = enabled)
            SensorMonitoringService.setRunning(app, enabled)
        }
    }

    // Shake to Camera (Moto-style Quick Capture) Actions
    fun setShakeToCameraEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateShakeToCameraSettings(enabled = enabled)
        }
    }

    fun setShakeToCameraSensitivity(sensitivity: QuickCaptureSensitivity) {
        viewModelScope.launch {
            prefs.updateShakeToCameraSettings(sensitivity = sensitivity.code)
        }
    }

    fun setShakeToCameraVibration(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateShakeToCameraSettings(vibrationEnabled = enabled)
        }
    }

    fun testShakeToCameraGesture(): Pair<Boolean, String> {
        return container.quickCaptureController.onQuickCaptureRecognized(isManualTest = true)
    }

    // Aqua Dynamics Interactive Capsule Actions
    fun setAquaDynamicsMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateAquaDynamicsSettings(enabled = enabled)
        }
    }

    fun previewAquaDynamicsCapsule() {
        container.aquaDynamicsManager.triggerInteractivePreview()
    }

    fun minimizeAquaDynamicsCapsule() {
        container.aquaDynamicsManager.minimizeToCameraPill()
    }

    fun expandAquaDynamicsCapsule() {
        container.aquaDynamicsManager.expandCapsule()
    }

    fun dismissAquaDynamicsCapsule() {
        container.aquaDynamicsManager.dismissActiveCapsule()
    }

    fun dragAquaDynamicsVertical(totalDyPx: Float, touchSlopPx: Float) {
        container.aquaDynamicsManager.onUserDragVertical(totalDyPx, 130f, touchSlopPx)
    }

    fun settleAquaDynamicsDrag(velocityY: Float) {
        container.aquaDynamicsManager.onUserDragSettle(velocityY)
    }

    // Notifications Actions
    fun markNotificationRead(id: Long) {
        viewModelScope.launch { dao.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { dao.markAllNotificationsRead() }
    }

    fun clearAllNotifications() {
        viewModelScope.launch { dao.clearAllNotifications() }
    }

    fun clearAllDiagnosticHistory() {
        viewModelScope.launch {
            dao.clearAllMetrics()
            dao.clearNetworkTests()
            dao.clearAutomationEvents()
            dao.clearConnectionEvents()
        }
    }

    // Automation Rules CRUD
    fun saveAutomationRule(rule: AutomationRuleEntity) {
        viewModelScope.launch { dao.upsertAutomationRule(rule) }
    }

    fun toggleAutomationRule(rule: AutomationRuleEntity, enabled: Boolean) {
        viewModelScope.launch { dao.updateAutomationRule(rule.copy(isEnabled = enabled)) }
    }

    fun deleteAutomationRule(id: Long) {
        viewModelScope.launch { dao.deleteAutomationRule(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
                PandaMainViewModel(application)
            }
        }
    }
}
