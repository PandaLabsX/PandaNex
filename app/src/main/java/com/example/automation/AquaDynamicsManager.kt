package com.example.automation

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.core.common.SystemUtils
import com.example.data.local.datastore.PandaPreferencesRepository
import com.example.data.local.datastore.PandaSettings
import com.example.monitoring.BatterySnapshot
import com.example.monitoring.CpuSnapshot
import com.example.monitoring.NetworkInfoSnapshot
import com.example.monitoring.ThermalLevel
import com.example.monitoring.ThermalSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Central hardware-backed controller & system overlay manager for PandaNex Aqua Dynamics.
 *
 * - Strictly baseline-first: records initial battery, charging, network, rotation, and audio
 *   device states silently on startup so Aqua Dynamics NEVER opens merely because PandaNex starts.
 * - Suppresses Aqua Dynamics immediately during any incoming, outgoing, or ongoing call using
 *   TelephonyCallback/PhoneStateListener + TelecomManager + AudioManager call mode + NotificationListener.
 * - Renders a constrained (WRAP_CONTENT, non-focusable, non-full-screen) interactive floating pill
 *   when overlay permission is granted, synchronized with the in-app Compose Aqua Dynamics capsule.
 */
class AquaDynamicsManager(
    private val context: Context,
    private val preferencesRepository: PandaPreferencesRepository,
    private val fullScreenDetector: FullScreenDetector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    val gatekeeper = AquaDynamicsGatekeeper()

    private var currentSettings = PandaSettings()
    private var autoMinimizeJob: Job? = null
    private var callModeWatchJob: Job? = null

    private var systemCapsuleView: AquaSystemOverlayCapsuleView? = null
    private var systemCapsuleParams: WindowManager.LayoutParams? = null

    // Call tracking signals
    private var telephonyCallState: Int = TelephonyManager.CALL_STATE_IDLE
    private var notificationCallActive: Boolean = false
    private var callListenerRegistered = false
    private var modernCallCallback: Any? = null

    // Baseline trackers (prevent false startup triggers!)
    private var powerReceiverRegistered = false
    private var audioCallbackRegistered = false
    private var audioBaselineCaptured = false
    private val connectedAudioDeviceIds = HashSet<Int>()

    private var networkCallbackRegistered = false
    private var networkBaselineCaptured = false
    private var lastWifiConnected: Boolean? = null
    private var lastInternetConnected: Boolean? = null
    private var lastConnectionType: String? = null

    private var configCallbackRegistered = false
    private var lastOrientation: Int = context.resources.configuration.orientation

    private var telemetryBaselineCaptured = false
    private var wasCpuHigh = false
    private var wasThermalElevated = false
    private var wasBatteryHot = false
    private var wasBatteryLow = false
    private var lastBatteryCharging: Boolean? = null
    private var lastBatteryFull: Boolean? = null

    private val _state = MutableStateFlow(gatekeeper.currentSnapshot())
    val state: StateFlow<AquaDynamicsEngineSnapshot> = _state.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class AquaTelephonyCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            telephonyCallState = state
            refreshEffectiveCallState()
        }
    }

    @Suppress("DEPRECATION")
    private val legacyPhoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            telephonyCallState = state
            refreshEffectiveCallState()
        }
    }

    private val powerConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt().coerceIn(0, 100) else -1
            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val source = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast/Wall Power"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Pad"
                else -> "Power Cable"
            }

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    lastBatteryCharging = true
                    emitRealEvent(
                        AquaDynamicsEvent(
                            id = "pwr_conn_${System.currentTimeMillis()}",
                            signatureKey = "CHARGING_CONNECTED_${System.currentTimeMillis() / 4000L}",
                            conditionGroupKey = "POWER_CABLE",
                            category = AquaEventCategory.DEVICE_INTERACTIONS,
                            priority = AquaEventPriority.NORMAL,
                            title = "Charging Started",
                            subtitle = if (pct >= 0) "$source connected • $pct%" else "$source connected",
                            badgeText = if (pct >= 0) "$pct%" else "CHG",
                            iconType = "CHARGING",
                            accentColorHex = 0xFF00E676L
                        )
                    )
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    lastBatteryCharging = false
                    emitRealEvent(
                        AquaDynamicsEvent(
                            id = "pwr_disc_${System.currentTimeMillis()}",
                            signatureKey = "CHARGING_DISCONNECTED_${System.currentTimeMillis() / 4000L}",
                            conditionGroupKey = "POWER_CABLE",
                            category = AquaEventCategory.DEVICE_INTERACTIONS,
                            priority = AquaEventPriority.NORMAL,
                            title = "Charger Disconnected",
                            subtitle = if (pct >= 0) "On battery power • $pct% remaining" else "Switched to battery power",
                            badgeText = if (pct >= 0) "$pct%" else "BAT",
                            iconType = "BATTERY",
                            accentColorHex = 0xFF00E5FFL
                        )
                    )
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val relevant = addedDevices?.filter { isExternalHeadphoneOrBtDevice(it) }.orEmpty()
            if (!audioBaselineCaptured) {
                relevant.forEach { connectedAudioDeviceIds.add(it.id) }
                audioBaselineCaptured = true
                return
            }
            for (dev in relevant) {
                if (connectedAudioDeviceIds.add(dev.id)) {
                    val kind = describeAudioDevice(dev)
                    val prodName = dev.productName?.toString()?.trim()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL }
                    val detail = if (prodName != null) "$prodName ($kind)" else "$kind audio routed"
                    emitRealEvent(
                        AquaDynamicsEvent(
                            id = "audio_add_${dev.id}_${System.currentTimeMillis()}",
                            signatureKey = "AUDIO_CONN_${dev.type}_${dev.id}_${System.currentTimeMillis() / 3000L}",
                            conditionGroupKey = "AUDIO_ROUTE",
                            category = AquaEventCategory.DEVICE_INTERACTIONS,
                            priority = AquaEventPriority.NORMAL,
                            title = "$kind Connected",
                            subtitle = detail,
                            badgeText = if (isBluetoothAudio(dev)) "BT" else "AUDIO",
                            iconType = "AUDIO",
                            accentColorHex = 0xFF1DE9B6L
                        )
                    )
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (!audioBaselineCaptured) return
            val relevant = removedDevices?.filter { isExternalHeadphoneOrBtDevice(it) }.orEmpty()
            for (dev in relevant) {
                if (connectedAudioDeviceIds.remove(dev.id)) {
                    val kind = describeAudioDevice(dev)
                    emitRealEvent(
                        AquaDynamicsEvent(
                            id = "audio_rem_${dev.id}_${System.currentTimeMillis()}",
                            signatureKey = "AUDIO_DISC_${dev.type}_${dev.id}_${System.currentTimeMillis() / 3000L}",
                            conditionGroupKey = "AUDIO_ROUTE",
                            category = AquaEventCategory.DEVICE_INTERACTIONS,
                            priority = AquaEventPriority.NORMAL,
                            title = "$kind Disconnected",
                            subtitle = "Audio output returned to device speaker",
                            badgeText = "SPK",
                            iconType = "AUDIO",
                            accentColorHex = 0xFF80D8FFL
                        )
                    )
                }
            }
        }
    }

    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val newOri = newConfig.orientation
            if (newOri != lastOrientation &&
                (newOri == Configuration.ORIENTATION_LANDSCAPE || newOri == Configuration.ORIENTATION_PORTRAIT)
            ) {
                lastOrientation = newOri
                val label = if (newOri == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "rot_${System.currentTimeMillis()}",
                        signatureKey = "SCREEN_ROTATION_${label}_${System.currentTimeMillis() / 2500L}",
                        conditionGroupKey = "SCREEN_ROTATION",
                        category = AquaEventCategory.DEVICE_INTERACTIONS,
                        priority = AquaEventPriority.NORMAL,
                        title = "Screen Rotated",
                        subtitle = "Display switched to $label orientation",
                        badgeText = if (newOri == Configuration.ORIENTATION_LANDSCAPE) "LAND" else "PORT",
                        iconType = "ROTATION",
                        accentColorHex = 0xFF00E5FFL
                    )
                )
                updateSystemOverlayPositionIfAttached()
            }
        }

        override fun onLowMemory() = Unit
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluateNetworkDelta()
        }

        override fun onLost(network: Network) {
            mainHandler.postDelayed({ evaluateNetworkDelta() }, 250L)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            evaluateNetworkDelta()
        }
    }

    init {
        captureInitialBaselinesSilently()
        registerHardwareDetectors()

        scope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                syncTelephonyCallListener()
                syncCallModeWatchLoop()
                val snap = gatekeeper.updateSettings(settings)
                publishSnapshot(snap)
            }
        }

        scope.launch {
            fullScreenDetector.state.collectLatest { fsState ->
                val snap = gatekeeper.onFullScreenChanged(fsState.isFullScreenActive)
                publishSnapshot(snap)
            }
        }
    }

    private fun captureInitialBaselinesSilently() {
        runCatching {
            val cm = connectivityManager
            val active = cm?.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            lastWifiConnected = wifi
            lastInternetConnected = connected
            lastConnectionType = when {
                wifi -> "Wi-Fi"
                cell -> "Mobile Data"
                connected -> "Connected"
                else -> "Offline"
            }
            networkBaselineCaptured = true
        }
        runCatching {
            val bIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            lastBatteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            lastBatteryFull = status == BatteryManager.BATTERY_STATUS_FULL
        }
        lastOrientation = context.resources.configuration.orientation
    }

    private fun registerHardwareDetectors() {
        // 1. Power cable connection/disconnection receiver (non-sticky: never fires on registration!)
        if (!powerReceiverRegistered) {
            runCatching {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(powerConnectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(powerConnectionReceiver, filter)
                }
                powerReceiverRegistered = true
            }
        }

        // 2. Headphone & Bluetooth audio callback (first callback captured as baseline silently)
        if (!audioCallbackRegistered) {
            runCatching {
                audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
                audioCallbackRegistered = true
            }
        }

        // 3. Screen rotation / configuration callback
        if (!configCallbackRegistered) {
            runCatching {
                context.registerComponentCallbacks(componentCallbacks)
                configCallbackRegistered = true
            }
        }

        // 4. Default network callback
        if (!networkCallbackRegistered) {
            runCatching {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback, mainHandler)
                networkCallbackRegistered = true
            }
        }

        // 5. Call state listener & AudioManager call-mode fallback
        syncTelephonyCallListener()
        syncCallModeWatchLoop()
    }

    fun syncTelephonyCallListener() {
        val hasPhonePerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPhonePerm && !callListenerRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cb = AquaTelephonyCallback()
                    modernCallCallback = cb
                    telephonyManager?.registerTelephonyCallback(context.mainExecutor, cb)
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                }
                callListenerRegistered = true
            }
        } else if (!hasPhonePerm && callListenerRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (modernCallCallback as? TelephonyCallback)?.let {
                        telephonyManager?.unregisterTelephonyCallback(it)
                    }
                    modernCallCallback = null
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
            }
            callListenerRegistered = false
            telephonyCallState = TelephonyManager.CALL_STATE_IDLE
        }
        refreshEffectiveCallState()
    }

    private fun syncCallModeWatchLoop() {
        if (callModeWatchJob?.isActive == true) return
        callModeWatchJob = scope.launch {
            while (isActive) {
                refreshEffectiveCallState()
                delay(900L)
            }
        }
    }

    /**
     * Combines TelephonyManager call state, TelecomManager.isInCall, AudioManager mode
     * (MODE_RINGTONE / MODE_IN_CALL / MODE_IN_COMMUNICATION — works even without READ_PHONE_STATE!),
     * and NotificationListenerService call notifications so Aqua Dynamics NEVER covers any call screen.
     */
    fun refreshEffectiveCallState() {
        val audioMode = runCatching { audioManager?.mode ?: AudioManager.MODE_NORMAL }
            .getOrDefault(AudioManager.MODE_NORMAL)

        val telecomInCall = if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { telecomManager?.isInCall == true }.getOrDefault(false)
        } else false

        val detectedPhase = when {
            telephonyCallState == TelephonyManager.CALL_STATE_RINGING ||
                audioMode == AudioManager.MODE_RINGTONE -> AquaActiveCallPhase.INCOMING_RINGING

            telephonyCallState == TelephonyManager.CALL_STATE_OFFHOOK ||
                telecomInCall ||
                audioMode == AudioManager.MODE_IN_CALL ||
                audioMode == AudioManager.MODE_IN_COMMUNICATION ||
                notificationCallActive -> AquaActiveCallPhase.OUTGOING_OR_ONGOING

            else -> AquaActiveCallPhase.IDLE
        }

        if (detectedPhase != gatekeeper.currentSnapshot().callPhase) {
            val snap = gatekeeper.onCallPhaseChanged(detectedPhase)
            publishSnapshot(snap)
        }
    }

    /**
     * Called by EdgeGlowNotificationService when call or missed-call notifications arrive/clear.
     */
    fun onSystemCallNotificationSignal(category: String?, isActiveCall: Boolean, appLabel: String = "") {
        if (category == Notification.CATEGORY_CALL) {
            notificationCallActive = isActiveCall
            refreshEffectiveCallState()
        } else if (category == Notification.CATEGORY_MISSED_CALL && isActiveCall) {
            emitRealEvent(
                AquaDynamicsEvent(
                    id = "missed_sbn_${System.currentTimeMillis()}",
                    signatureKey = "CALL_MISSED_${System.currentTimeMillis() / 4000L}",
                    conditionGroupKey = "CALL_MISSED",
                    category = AquaEventCategory.PHONE_COMMUNICATION,
                    priority = AquaEventPriority.IMPORTANT,
                    title = "Missed Call",
                    subtitle = if (appLabel.isNotBlank()) "Unanswered call via $appLabel" else "Unanswered incoming call",
                    badgeText = "MISSED",
                    iconType = "CALL_MISSED",
                    accentColorHex = 0xFFFFB300L
                )
            )
        }
    }

    /**
     * Deterministic testing & explicit call state update hook.
     */
    fun reportCallPhaseForState(phase: AquaActiveCallPhase, nowMs: Long = System.currentTimeMillis()) {
        val snap = gatekeeper.onCallPhaseChanged(phase, nowMs)
        publishSnapshot(snap)
    }

    private fun evaluateNetworkDelta() {
        val cm = connectivityManager ?: return
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val eth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val typeLabel = when {
            wifi -> "Wi-Fi"
            cell -> "Mobile Data"
            eth -> "Ethernet"
            connected -> "Network"
            else -> "Offline"
        }

        if (!networkBaselineCaptured) {
            lastWifiConnected = wifi
            lastInternetConnected = connected
            lastConnectionType = typeLabel
            networkBaselineCaptured = true
            return
        }

        val prevWifi = lastWifiConnected
        val prevConn = lastInternetConnected
        val prevType = lastConnectionType

        lastWifiConnected = wifi
        lastInternetConnected = connected
        lastConnectionType = typeLabel

        if (prevConn == connected && prevWifi == wifi && prevType == typeLabel) return

        val now = System.currentTimeMillis()
        when {
            !connected && prevConn == true -> {
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "net_lost_$now",
                        signatureKey = "NET_OFFLINE",
                        conditionGroupKey = "NETWORK_STATE",
                        category = AquaEventCategory.NETWORK,
                        priority = AquaEventPriority.IMPORTANT,
                        title = "Internet Disconnected",
                        subtitle = "Device is currently offline",
                        badgeText = "OFFLINE",
                        iconType = "NETWORK",
                        accentColorHex = 0xFFFFB300L,
                        timestampMs = now
                    )
                )
            }

            wifi && prevWifi != true -> {
                gatekeeper.markConditionCleared("NETWORK_STATE")
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "wifi_conn_$now",
                        signatureKey = "NET_WIFI_CONNECTED",
                        conditionGroupKey = "NETWORK_STATE",
                        category = AquaEventCategory.NETWORK,
                        priority = AquaEventPriority.NORMAL,
                        title = "Wi-Fi Connected",
                        subtitle = if (prevType == "Mobile Data") "Switched from Mobile Data to Wi-Fi" else "Wireless internet active",
                        badgeText = "WI-FI",
                        iconType = "WIFI",
                        accentColorHex = 0xFF00E5FFL,
                        timestampMs = now
                    )
                )
            }

            !wifi && prevWifi == true && connected -> {
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "net_switch_$now",
                        signatureKey = "NET_SWITCH_$typeLabel",
                        conditionGroupKey = "NETWORK_STATE",
                        category = AquaEventCategory.NETWORK,
                        priority = AquaEventPriority.NORMAL,
                        title = "Switched to $typeLabel",
                        subtitle = "Wi-Fi disconnected • Active on $typeLabel",
                        badgeText = "DATA",
                        iconType = "NETWORK",
                        accentColorHex = 0xFF80D8FFL,
                        timestampMs = now
                    )
                )
            }

            connected && prevConn == false -> {
                gatekeeper.markConditionCleared("NETWORK_STATE")
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "net_online_$now",
                        signatureKey = "NET_ONLINE_$typeLabel",
                        conditionGroupKey = "NETWORK_STATE",
                        category = AquaEventCategory.NETWORK,
                        priority = AquaEventPriority.NORMAL,
                        title = "Back Online",
                        subtitle = "Connected via $typeLabel",
                        badgeText = "ONLINE",
                        iconType = "NETWORK",
                        accentColorHex = 0xFF00E676L,
                        timestampMs = now
                    )
                )
            }
        }
    }

    /**
     * Evaluates live hardware telemetry deltas (CPU overload, overheating, battery temperature,
     * low battery, charging completion). First sample is recorded silently as baseline!
     */
    fun onLiveTelemetrySampled(
        cpu: CpuSnapshot?,
        battery: BatterySnapshot?,
        thermal: ThermalSnapshot?,
        network: NetworkInfoSnapshot?
    ) {
        val s = currentSettings
        if (!telemetryBaselineCaptured) {
            if (cpu != null) wasCpuHigh = cpu.overallUsagePercent >= s.cpuAlertThresholdPercent
            if (thermal != null && thermal.powerManagerStatusSupported) {
                wasThermalElevated = thermal.thermalLevel.ordinal >= ThermalLevel.MODERATE.ordinal &&
                    thermal.thermalLevel != ThermalLevel.UNAVAILABLE
            }
            if (battery != null) {
                wasBatteryHot = (battery.temperatureCelsius ?: 0f) >= s.highBatteryTempCelsius
                wasBatteryLow = battery.percentage <= s.lowBatteryThresholdPercent && !battery.isCharging
                lastBatteryCharging = battery.isCharging
                lastBatteryFull = battery.percentage >= 100 && battery.isCharging
            }
            if (network != null && !networkBaselineCaptured) {
                lastWifiConnected = network.wifiConnected
                lastInternetConnected = network.isConnected
                lastConnectionType = network.connectionType
                networkBaselineCaptured = true
            }
            telemetryBaselineCaptured = true
            return
        }

        // 1. CPU Overload Transition
        if (cpu != null) {
            val highNow = cpu.overallUsagePercent >= s.cpuAlertThresholdPercent
            if (highNow && !wasCpuHigh) {
                wasCpuHigh = true
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "cpu_high_${System.currentTimeMillis()}",
                        signatureKey = "CPU_OVERLOAD_ACTIVE",
                        conditionGroupKey = "CPU_LOAD",
                        category = AquaEventCategory.DEVICE_PERFORMANCE,
                        priority = if (cpu.overallUsagePercent >= 95f) AquaEventPriority.CRITICAL else AquaEventPriority.IMPORTANT,
                        title = "High CPU Usage (${cpu.overallUsagePercent.roundToInt()}%)",
                        subtitle = "${cpu.activeCoreCount}/${cpu.coreCount} cores under sustained load",
                        badgeText = "${cpu.overallUsagePercent.roundToInt()}%",
                        iconType = "CPU",
                        accentColorHex = if (cpu.overallUsagePercent >= 95f) 0xFFFF5252L else 0xFFFFB300L
                    )
                )
            } else if (!highNow && wasCpuHigh && cpu.overallUsagePercent < (s.cpuAlertThresholdPercent - 8f)) {
                wasCpuHigh = false
                gatekeeper.markConditionCleared("CPU_LOAD")
            }
        }

        // 2. Device Overheating (PowerManager Thermal Status)
        if (thermal != null && thermal.powerManagerStatusSupported && thermal.thermalLevel != ThermalLevel.UNAVAILABLE) {
            val hotNow = thermal.thermalLevel.ordinal >= ThermalLevel.MODERATE.ordinal
            if (hotNow && !wasThermalElevated) {
                wasThermalElevated = true
                val crit = thermal.thermalLevel.ordinal >= ThermalLevel.CRITICAL.ordinal
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "therm_${System.currentTimeMillis()}",
                        signatureKey = "THERMAL_${thermal.thermalLevel.name}",
                        conditionGroupKey = "DEVICE_THERMAL",
                        category = AquaEventCategory.DEVICE_PERFORMANCE,
                        priority = if (crit) AquaEventPriority.CRITICAL else AquaEventPriority.IMPORTANT,
                        title = "Heat Guard: ${thermal.thermalLevel.displayLabel}",
                        subtitle = thermal.thermalLevel.coolingAdvice,
                        badgeText = thermal.thermalLevel.displayLabel.uppercase(),
                        iconType = "THERMAL",
                        accentColorHex = if (crit) 0xFFFF5252L else 0xFFFFB300L
                    )
                )
            } else if (!hotNow && wasThermalElevated) {
                wasThermalElevated = false
                gatekeeper.markConditionCleared("DEVICE_THERMAL")
            }
        }

        // 3. Battery Temperature, Low Battery & Charging Completed
        if (battery != null) {
            val tempC = battery.temperatureCelsius
            val batHotNow = tempC != null && tempC >= s.highBatteryTempCelsius
            if (batHotNow && !wasBatteryHot) {
                wasBatteryHot = true
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "bat_temp_${System.currentTimeMillis()}",
                        signatureKey = "BATTERY_TEMP_HIGH",
                        conditionGroupKey = "BATTERY_TEMP",
                        category = AquaEventCategory.DEVICE_PERFORMANCE,
                        priority = if ((tempC ?: 0f) >= 44.5f) AquaEventPriority.CRITICAL else AquaEventPriority.IMPORTANT,
                        title = "Battery Warm (${tempC}°C)",
                        subtitle = "Crossed ${s.highBatteryTempCelsius}°C thermal threshold",
                        badgeText = "${tempC}°C",
                        iconType = "THERMAL",
                        accentColorHex = 0xFFFF5252L
                    )
                )
            } else if (!batHotNow && wasBatteryHot && (tempC == null || tempC <= s.highBatteryTempCelsius - 1.5f)) {
                wasBatteryHot = false
                gatekeeper.markConditionCleared("BATTERY_TEMP")
            }

            val lowNow = battery.percentage <= s.lowBatteryThresholdPercent && !battery.isCharging
            if (lowNow && !wasBatteryLow) {
                wasBatteryLow = true
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "bat_low_${System.currentTimeMillis()}",
                        signatureKey = "BATTERY_LOW_${if (battery.percentage <= 10) "10" else "20"}",
                        conditionGroupKey = "BATTERY_LOW",
                        category = AquaEventCategory.DEVICE_PERFORMANCE,
                        priority = if (battery.percentage <= 10) AquaEventPriority.CRITICAL else AquaEventPriority.IMPORTANT,
                        title = "Low Battery (${battery.percentage}%)",
                        subtitle = "Connect charger soon to preserve device readiness",
                        badgeText = "${battery.percentage}%",
                        iconType = "BATTERY",
                        accentColorHex = if (battery.percentage <= 10) 0xFFFF5252L else 0xFFFFB300L
                    )
                )
            } else if (!lowNow && wasBatteryLow) {
                wasBatteryLow = false
                gatekeeper.markConditionCleared("BATTERY_LOW")
            }

            val fullNow = (battery.statusLabel == "Fully Charged" || battery.percentage >= 100) && battery.isCharging
            if (fullNow && lastBatteryFull == false) {
                lastBatteryFull = true
                emitRealEvent(
                    AquaDynamicsEvent(
                        id = "bat_full_${System.currentTimeMillis()}",
                        signatureKey = "CHARGING_COMPLETED_100",
                        conditionGroupKey = "CHARGING_FULL",
                        category = AquaEventCategory.DEVICE_PERFORMANCE,
                        priority = AquaEventPriority.NORMAL,
                        title = "Charging Completed",
                        subtitle = "Battery reached 100% via ${battery.chargingSource}",
                        badgeText = "100%",
                        iconType = "CHARGING",
                        accentColorHex = 0xFF00E676L
                    )
                )
            } else if (!fullNow && lastBatteryFull == true) {
                lastBatteryFull = false
                gatekeeper.markConditionCleared("CHARGING_FULL")
            }
        }
    }

    /**
     * Publishes supported sensor & PandaNex smart events (Flip-to-DND, Shake-to-Camera, Torch, Diagnostics, Heat Guard).
     */
    fun onSupportedSensorOrSystemEvent(
        category: AquaEventCategory,
        conditionGroupKey: String,
        signatureKey: String,
        priority: AquaEventPriority,
        title: String,
        subtitle: String,
        badgeText: String,
        iconType: String = "SENSOR",
        accentColorHex: Long = 0xFF00E5FFL
    ) {
        emitRealEvent(
            AquaDynamicsEvent(
                id = "${conditionGroupKey}_${System.currentTimeMillis()}",
                signatureKey = signatureKey,
                conditionGroupKey = conditionGroupKey,
                category = category,
                priority = priority,
                title = title,
                subtitle = subtitle,
                badgeText = badgeText,
                iconType = iconType,
                accentColorHex = accentColorHex
            )
        )
    }

    fun triggerInteractivePreview() {
        val now = System.currentTimeMillis()
        val previewEvent = AquaDynamicsEvent(
            id = "preview_$now",
            signatureKey = "PREVIEW_$now",
            conditionGroupKey = "USER_PREVIEW",
            category = AquaEventCategory.PANDANEX_SYSTEM,
            priority = AquaEventPriority.IMPORTANT,
            title = "Aqua Dynamics Ready",
            subtitle = "Drag up toward camera to minimize • Tap black pill to expand",
            badgeText = "LIVE",
            iconType = "ALERT",
            accentColorHex = 0xFF00E5FFL,
            timestampMs = now
        )
        scope.launch {
            refreshEffectiveCallState()
            val accepted = gatekeeper.submitEvent(previewEvent, nowMs = now, isUserPreview = true)
            if (accepted) {
                publishSnapshot(gatekeeper.currentSnapshot())
                scheduleAutoMinimizeIfConfigured()
            }
        }
    }

    fun emitRealEvent(event: AquaDynamicsEvent) {
        scope.launch {
            refreshEffectiveCallState()
            val accepted = gatekeeper.submitEvent(event)
            if (accepted) {
                publishSnapshot(gatekeeper.currentSnapshot())
                scheduleAutoMinimizeIfConfigured()
            }
        }
    }

    fun onUserDragVertical(totalDyPx: Float, fullTravelPx: Float = 130f, touchSlopPx: Float = 14f) {
        autoMinimizeJob?.cancel()
        val snap = gatekeeper.onInteractiveVerticalDrag(totalDyPx, fullTravelPx, touchSlopPx)
        publishSnapshot(snap)
    }

    fun onUserDragSettle(velocityY: Float = 0f) {
        val prevMode = _state.value.displayMode
        val snap = gatekeeper.settleInteractiveDrag(velocityY)
        if (snap.displayMode != prevMode && currentSettings.hapticFeedbackEnabled) {
            SystemUtils.triggerHapticPulse(context, 18L)
        }
        publishSnapshot(snap)
        if (snap.displayMode == AquaDisplayMode.EXPANDED) {
            scheduleAutoMinimizeIfConfigured()
        }
    }

    fun minimizeToCameraPill() {
        autoMinimizeJob?.cancel()
        val snap = gatekeeper.minimizeToCameraArea()
        if (currentSettings.hapticFeedbackEnabled) {
            SystemUtils.triggerHapticPulse(context, 16L)
        }
        publishSnapshot(snap)
    }

    fun expandCapsule() {
        autoMinimizeJob?.cancel()
        val snap = gatekeeper.expandFromMinimized()
        if (currentSettings.hapticFeedbackEnabled) {
            SystemUtils.triggerHapticPulse(context, 18L)
        }
        publishSnapshot(snap)
        scheduleAutoMinimizeIfConfigured()
    }

    fun dismissActiveCapsule() {
        autoMinimizeJob?.cancel()
        val (snap, dismissedSet) = gatekeeper.dismissActiveEvent()
        publishSnapshot(snap)
        scope.launch {
            preferencesRepository.updateAquaDynamicsSettings(dismissedSignatures = dismissedSet)
        }
    }

    private fun scheduleAutoMinimizeIfConfigured() {
        autoMinimizeJob?.cancel()
        if (!currentSettings.aquaAutoMinimizeEnabled) return
        val delayMs = currentSettings.aquaAutoMinimizeDelayMs.coerceIn(2500L, 15000L)
        autoMinimizeJob = scope.launch {
            delay(delayMs)
            val current = gatekeeper.currentSnapshot()
            if (current.displayMode == AquaDisplayMode.EXPANDED) {
                val minimized = gatekeeper.minimizeToCameraArea()
                publishSnapshot(minimized)
            }
        }
    }

    private fun publishSnapshot(snapshot: AquaDynamicsEngineSnapshot) {
        _state.value = snapshot
        syncSystemOverlayCapsule(snapshot)
    }

    private fun syncSystemOverlayCapsule(snapshot: AquaDynamicsEngineSnapshot) {
        val shouldShowSystemOverlay = Settings.canDrawOverlays(context) &&
            snapshot.displayMode != AquaDisplayMode.HIDDEN &&
            snapshot.activeEvent != null &&
            !snapshot.isSuppressedByCall &&
            !snapshot.isSuppressedByFullScreen

        if (!shouldShowSystemOverlay) {
            removeSystemOverlayCapsule()
            return
        }

        val wm = windowManager ?: return
        val event = snapshot.activeEvent ?: return
        val anchor = computeCurrentCameraAnchor()

        try {
            val existing = systemCapsuleView
            val existingLp = systemCapsuleParams
            if (existing != null && existingLp != null) {
                existing.bindSnapshot(snapshot, currentSettings.aquaAnimationStyle)
                val targetW = existing.desiredWindowWidthPx()
                val targetH = existing.desiredWindowHeightPx()
                if (existingLp.width != targetW || existingLp.height != targetH || existingLp.y != anchor.topMarginPx) {
                    existingLp.width = targetW
                    existingLp.height = targetH
                    existingLp.y = anchor.topMarginPx
                    wm.updateViewLayout(existing, existingLp)
                }
                return
            }

            val view = AquaSystemOverlayCapsuleView(
                context = context,
                onTapMinimized = { expandCapsule() },
                onTapMinimizeAction = { minimizeToCameraPill() },
                onDismiss = { dismissActiveCapsule() },
                onVerticalDrag = { dy, slop -> onUserDragVertical(dy, 130f, slop) },
                onVerticalSettle = { vy -> onUserDragSettle(vy) },
                onSizeStepChanged = { v, wPx, hPx ->
                    val lp = systemCapsuleParams
                    if (lp != null && v.isAttachedToWindow && (lp.width != wPx || lp.height != hPx)) {
                        lp.width = wPx
                        lp.height = hPx
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
            ).apply {
                bindSnapshot(snapshot, currentSettings.aquaAnimationStyle)
            }

            // Strictly compact, non-focusable, non-touch-modal window — NEVER full-screen!
            val lp = WindowManager.LayoutParams(
                view.desiredWindowWidthPx(),
                view.desiredWindowHeightPx(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = anchor.horizontalOffsetPx
                y = anchor.topMarginPx
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            // Re-verify event validity before adding
            if (event.title.isNotBlank()) {
                wm.addView(view, lp)
                systemCapsuleView = view
                systemCapsuleParams = lp
            }
        } catch (_: Exception) {
            systemCapsuleView = null
            systemCapsuleParams = null
        }
    }

    private fun updateSystemOverlayPositionIfAttached() {
        val view = systemCapsuleView ?: return
        val lp = systemCapsuleParams ?: return
        if (!view.isAttachedToWindow) return
        val anchor = computeCurrentCameraAnchor()
        lp.y = anchor.topMarginPx
        runCatching { windowManager?.updateViewLayout(view, lp) }
    }

    private fun removeSystemOverlayCapsule() {
        val view = systemCapsuleView ?: return
        systemCapsuleView = null
        systemCapsuleParams = null
        runCatching {
            view.cleanup()
            windowManager?.removeViewImmediate(view)
        }
    }

    fun computeCurrentCameraAnchor(): CameraSafeAnchorOffset {
        val density = context.resources.displayMetrics.density
        val widthPx = context.resources.displayMetrics.widthPixels
        var cutoutRects: List<Rect> = emptyList()
        var safeTop = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val metrics = windowManager?.currentWindowMetrics
                val cutout = metrics?.windowInsets?.displayCutout
                if (cutout != null) {
                    cutoutRects = cutout.boundingRects
                    safeTop = cutout.safeInsetTop
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                @Suppress("DEPRECATION")
                val cutout = windowManager?.defaultDisplay?.cutout
                if (cutout != null) {
                    cutoutRects = cutout.boundingRects
                    safeTop = cutout.safeInsetTop
                }
            }
        }

        if (safeTop <= 0) {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) {
                safeTop = (context.resources.getDimensionPixelSize(resId) * 0.25f).toInt()
            }
        }

        return AquaDynamicsGatekeeper.computeCameraSafeAnchor(
            screenWidthPx = widthPx,
            cutoutRects = cutoutRects,
            safeInsetTopPx = safeTop,
            density = density
        )
    }

    private fun isExternalHeadphoneOrBtDevice(info: AudioDeviceInfo): Boolean {
        if (!info.isSink) return false
        return when (info.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            else -> {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        info.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
            }
        }
    }

    private fun isBluetoothAudio(info: AudioDeviceInfo): Boolean {
        return info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }

    private fun describeAudioDevice(info: AudioDeviceInfo): String {
        return when (info.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Audio"
            else -> if (isBluetoothAudio(info)) "Bluetooth LE Audio" else "Audio Device"
        }
    }
}

/**
 * Compact, hardware-accelerated custom View for system-wide WindowManager Aqua Dynamics pill.
 * Uses a pure black (#05070B / #000000) rounded background in minimized mode around the camera area
 * and smoothly morphs into the Clean Aqua Dynamics expanded capsule with drag-to-minimize,
 * tap-to-restore, and horizontal-swipe-to-dismiss gestures.
 */
private class AquaSystemOverlayCapsuleView(
    context: Context,
    private val onTapMinimized: () -> Unit,
    private val onTapMinimizeAction: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onVerticalDrag: (totalDyPx: Float, touchSlopPx: Float) -> Unit,
    private val onVerticalSettle: (velocityY: Float) -> Unit,
    private val onSizeStepChanged: (View, Int, Int) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat().coerceAtLeast(12f)

    private var titleText: String = ""
    private var subtitleText: String = ""
    private var badgeText: String = ""
    private var accentColor: Int = 0xFF00E5FF.toInt()
    private var targetFraction: Float = 1f
    private var animatedFraction: Float = 1f
    private var animStyle: String = "FLUID_SPRING"

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x6696B3CC
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13.5f * scaledDensity
        isFakeBoldText = true
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB4CFE6.toInt()
        textSize = 11.5f * scaledDensity
    }
    private val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        textSize = 10.5f * scaledDensity
        isFakeBoldText = true
    }

    private var currentEventSignature: String = ""
    private var lastDisplayMode: AquaDisplayMode = AquaDisplayMode.HIDDEN

    private val pillRect = RectF()
    private val closeHitRect = RectF()
    private var morphAnimator: ValueAnimator? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var draggingVertical = false
    private var draggingHorizontal = false
    private var horizontalTranslationPx = 0f
    private var velocityTracker: VelocityTracker? = null

    private val minWidthPx: Int
        get() = (176f * density).roundToInt()
    private val maxWidthPx: Int
        get() = (resources.displayMetrics.widthPixels - (32f * density).roundToInt())
            .coerceIn((240f * density).roundToInt(), (372f * density).roundToInt())
    private val minHeightPx: Int
        get() = (38f * density).roundToInt()
    private val maxHeightPx: Int
        get() = (76f * density).roundToInt()

    fun desiredWindowWidthPx(): Int {
        val f = animatedFraction.coerceIn(0f, 1f)
        return (minWidthPx + (maxWidthPx - minWidthPx) * f).roundToInt()
    }

    fun desiredWindowHeightPx(): Int {
        val f = animatedFraction.coerceIn(0f, 1f)
        return (minHeightPx + (maxHeightPx - minHeightPx) * f).roundToInt()
    }

    fun bindSnapshot(snapshot: AquaDynamicsEngineSnapshot, style: String) {
        val ev = snapshot.activeEvent ?: return
        val isNewEvent = currentEventSignature != ev.signatureKey || lastDisplayMode == AquaDisplayMode.HIDDEN
        currentEventSignature = ev.signatureKey
        lastDisplayMode = snapshot.displayMode

        titleText = ev.title
        subtitleText = ev.subtitle
        badgeText = ev.badgeText
        accentColor = ev.accentColorHex.toInt()
        badgePaint.color = accentColor
        animStyle = style

        val desired = when (snapshot.displayMode) {
            AquaDisplayMode.EXPANDED -> snapshot.interactiveExpandFraction.coerceIn(0f, 1f)
            AquaDisplayMode.MINIMIZED -> snapshot.interactiveExpandFraction.coerceIn(0f, 1f)
            AquaDisplayMode.HIDDEN -> 0f
        }

        // When a new event arrives in EXPANDED mode, present the animated dynamic interface
        // directly from the first frame. Do not play an intermediate message-to-dynamics transition!
        if (isNewEvent && snapshot.displayMode == AquaDisplayMode.EXPANDED) {
            morphAnimator?.cancel()
            animatedFraction = 1f
            targetFraction = 1f
            onSizeStepChanged(this, desiredWindowWidthPx(), desiredWindowHeightPx())
            invalidate()
            return
        }

        if (draggingVertical || style == "REDUCED" || abs(desired - animatedFraction) < 0.02f) {
            morphAnimator?.cancel()
            animatedFraction = desired
            targetFraction = desired
            onSizeStepChanged(this, desiredWindowWidthPx(), desiredWindowHeightPx())
            invalidate()
        } else if (abs(desired - targetFraction) >= 0.02f) {
            targetFraction = desired
            morphAnimator?.cancel()
            val start = animatedFraction
            morphAnimator = ValueAnimator.ofFloat(start, desired).apply {
                duration = if (style == "CRISP") 190L else 320L
                interpolator = if (style == "FLUID_SPRING") OvershootInterpolator(0.75f) else null
                addUpdateListener { anim ->
                    this@AquaSystemOverlayCapsuleView.animatedFraction =
                        (anim.animatedValue as Float).coerceIn(0f, 1f)
                    onSizeStepChanged(this@AquaSystemOverlayCapsuleView, desiredWindowWidthPx(), desiredWindowHeightPx())
                    postInvalidateOnAnimation()
                }
                start()
            }
        } else {
            invalidate()
        }
    }

    fun cleanup() {
        morphAnimator?.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 4f || h <= 4f) return

        val f = animatedFraction.coerceIn(0f, 1f)
        val inset = 1.5f * density
        pillRect.set(
            inset + horizontalTranslationPx * 0.35f,
            inset,
            w - inset + horizontalTranslationPx * 0.35f,
            h - inset
        )
        val corner = (h / 2f).coerceAtMost(26f * density)

        // Minimized mode = sleek pure black (#020408) rounded capsule; Expanded = deep midnight aqua glass
        val bgColorStart = blendArgb(0xF6020408.toInt(), 0xF4061224.toInt(), f)
        val bgColorEnd = blendArgb(0xF605080F.toInt(), 0xF40A1E38.toInt(), f)
        bgPaint.shader = LinearGradient(
            pillRect.left,
            pillRect.top,
            pillRect.right,
            pillRect.bottom,
            bgColorStart,
            bgColorEnd,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillRect, corner, corner, bgPaint)

        strokePaint.strokeWidth = (1.2f + 0.4f * f) * density
        strokePaint.color = (accentColor and 0x00FFFFFF) or ((if (f < 0.35f) 0x88 else 0xCC) shl 24)
        canvas.drawRoundRect(pillRect, corner, corner, strokePaint)

        // Leading status pulse dot
        val dotRadius = (4.2f + 1.4f * f) * density
        val dotCx = pillRect.left + (14f + 4f * f) * density
        val dotCy = if (f < 0.45f) pillRect.centerY() else pillRect.top + 24f * density
        dotPaint.color = accentColor
        canvas.drawCircle(dotCx, dotCy, dotRadius, dotPaint)

        if (f < 0.45f) {
            // Compact minimized black capsule around camera area
            val avail = (pillRect.width() - 72f * density).coerceAtLeast(36f * density)
            val compactTitle = TextUtils.ellipsize(titleText, titlePaint, avail, TextUtils.TruncateAt.END).toString()
            val textY = pillRect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f
            canvas.drawText(compactTitle, dotCx + 11f * density, textY, titlePaint)

            if (badgeText.isNotBlank()) {
                val bw = badgePaint.measureText(badgeText)
                val bx = pillRect.right - bw - 12f * density
                val by = pillRect.centerY() - (badgePaint.descent() + badgePaint.ascent()) / 2f
                canvas.drawText(badgeText, bx, by, badgePaint)
            }
            closeHitRect.setEmpty()
        } else {
            // Expanded Clean Aqua Dynamics capsule
            val closeSize = 28f * density
            closeHitRect.set(
                pillRect.right - closeSize - 8f * density,
                pillRect.top + 8f * density,
                pillRect.right - 8f * density,
                pillRect.top + 8f * density + closeSize
            )

            val textStartX = dotCx + 12f * density
            val maxTextW = (closeHitRect.left - textStartX - 8f * density).coerceAtLeast(60f * density)
            val ellipsizedTitle = TextUtils.ellipsize(titleText, titlePaint, maxTextW, TextUtils.TruncateAt.END).toString()
            val ellipsizedSub = TextUtils.ellipsize(subtitleText, subPaint, maxTextW, TextUtils.TruncateAt.END).toString()

            canvas.drawText(ellipsizedTitle, textStartX, pillRect.top + 27f * density, titlePaint)
            canvas.drawText(ellipsizedSub, textStartX, pillRect.top + 46f * density, subPaint)

            // Close "×" glyph
            val cx = closeHitRect.centerX()
            val cy = closeHitRect.centerY()
            val arm = 4.5f * density
            strokePaint.strokeWidth = 1.6f * density
            strokePaint.color = 0xCCBFD8EE.toInt()
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, strokePaint)
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, strokePaint)

            // Bottom interactive drag handle bar
            val hw = 34f * density
            val hh = 3f * density
            canvas.drawRoundRect(
                pillRect.centerX() - hw / 2f,
                pillRect.bottom - 8f * density,
                pillRect.centerX() + hw / 2f,
                pillRect.bottom - 8f * density + hh,
                hh,
                hh,
                handlePaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                draggingVertical = false
                draggingHorizontal = false
                horizontalTranslationPx = 0f
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()?.also { it.addMovement(event) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY

                if (!draggingVertical && !draggingHorizontal) {
                    if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                        draggingVertical = true
                    } else if (abs(dx) > touchSlop * 1.3f && abs(dx) > abs(dy)) {
                        draggingHorizontal = true
                    }
                }

                if (draggingVertical) {
                    onVerticalDrag(dy, touchSlop)
                } else if (draggingHorizontal) {
                    horizontalTranslationPx = dx
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val totalDx = event.rawX - downRawX
                val totalDy = event.rawY - downRawY

                when {
                    draggingVertical -> {
                        draggingVertical = false
                        onVerticalSettle(vy)
                    }

                    draggingHorizontal -> {
                        draggingHorizontal = false
                        val shouldDismiss = abs(totalDx) > 72f * density || abs(vx) > 650f
                        horizontalTranslationPx = 0f
                        if (shouldDismiss) {
                            onDismiss()
                        } else {
                            invalidate()
                        }
                    }

                    abs(totalDx) < touchSlop && abs(totalDy) < touchSlop && event.actionMasked == MotionEvent.ACTION_UP -> {
                        // Intentional tap
                        if (animatedFraction < 0.45f) {
                            onTapMinimized()
                        } else if (closeHitRect.contains(event.x, event.y)) {
                            onDismiss()
                        } else {
                            onTapMinimizeAction()
                        }
                    }
                }
                ViewCompat.postInvalidateOnAnimation(this)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun blendArgb(c1: Int, c2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val inv = 1f - r
        val a = (((c1 ushr 24) and 0xFF) * inv + ((c2 ushr 24) and 0xFF) * r).roundToInt()
        val red = (((c1 ushr 16) and 0xFF) * inv + ((c2 ushr 16) and 0xFF) * r).roundToInt()
        val green = (((c1 ushr 8) and 0xFF) * inv + ((c2 ushr 8) and 0xFF) * r).roundToInt()
        val blue = ((c1 and 0xFF) * inv + (c2 and 0xFF) * r).roundToInt()
        return (a shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
