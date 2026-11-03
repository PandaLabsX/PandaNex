package com.example.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.core.common.SystemUtils
import com.example.data.local.database.AutomationEventEntity
import com.example.data.local.database.PandaDao
import com.example.data.local.datastore.PandaPreferencesRepository
import com.example.data.local.datastore.PandaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SmartGestureHubState(
    // Hardware Sensor Availability
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasProximitySensor: Boolean = false,
    val hasSignificantMotion: Boolean = false,

    // System Permissions & Privileges
    val hasReadPhoneStatePermission: Boolean = false,
    val hasAnswerPhoneCallsPermission: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val canWriteSystemSettings: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val hasUsageStatsAccess: Boolean = false,

    // Runtime & Conflict Arbitration Status
    val isDisplayInteractive: Boolean = true,
    val isCallRinging: Boolean = false,
    val isCallActive: Boolean = false,
    val externalAudioConnected: Boolean = false,
    val externalAudioLabel: String = "",
    val isScreenSaverCurrentlyVisible: Boolean = false,
    val backgroundServiceActive: Boolean = false,

    // Per-Feature Live Status Messages
    val raiseToWakeStatus: String = "Disabled",
    val pickUpToSilenceStatus: String = "Disabled",
    val proximityAnswerStatus: String = "Disabled",
    val putDownToSleepStatus: String = "Disabled",
    val screenSaverStatus: String = "Disabled",
    val lastTriggeredSummary: String = "Smart Actions & Gestures ready"
)

/**
 * Central PandaNex coordinator for:
 * 1. Raise to Wake
 * 2. Pick Up to Silence Calls
 * 3. Proximity Call Answer (Bring Near Ear)
 * 4. Put Down to Sleep (Flat Surface Display Off)
 * 5. Dimmed App (coordinated via DimmedAppManager)
 * 6. Smart Screen Saver (Motion Activated & DreamService)
 *
 * Enforces battery-aware dynamic sensor registration and conflict-free arbitration across all features.
 */
class SmartGestureManager(
    private val context: Context,
    private val dao: PandaDao,
    private val preferencesRepository: PandaPreferencesRepository,
    val displayActionManager: DisplayActionManager,
    val dimmedAppManager: DimmedAppManager,
    private val fullScreenDetector: FullScreenDetector,
    private val aquaDynamicsManager: AquaDynamicsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val proximityDetectionManager: ProximityDetectionManager = ProximityDetectionManager(
        context = context,
        onProximityStateChanged = { isNear ->
            motionDetectionManager.updateProximityState(isNear)
        },
        onNearEarAnswerConfirmed = {
            handleProximityNearEarAnswer()
        }
    )

    val motionDetectionManager: MotionDetectionManager = MotionDetectionManager(
        context = context,
        onRaiseToWakeTriggered = { handleRaiseToWake() },
        onPickUpToSilenceTriggered = { handlePickUpToSilence() },
        onPutDownToSleepTriggered = { placement -> handlePutDownToSleep(placement) },
        onScreenSaverMotionTriggered = { handleScreenSaverMotion() },
        onOrientationSampled = { ax, ay, az ->
            if (currentSettings.proximityCallAnswerEnabled && callGestureManager.runtimeState.isCallRinging) {
                proximityDetectionManager.onAccelerometerForPosture(ax, ay, az)
            }
        }
    )

    val callGestureManager: CallGestureManager = CallGestureManager(
        context = context,
        onRingingStateChanged = { isRinging, isAnyCallActive ->
            onCallStateUpdated(isRinging, isAnyCallActive)
        }
    )

    private var currentSettings = PandaSettings()
    private var isActivityForeground = false
    private var isBackgroundServiceRunning = false
    private var isScreenSaverVisible = false
    private var screenReceiverRegistered = false

    private val _state = MutableStateFlow(
        SmartGestureHubState(
            hasAccelerometer = motionDetectionManager.hasAccelerometer,
            hasGyroscope = motionDetectionManager.hasGyroscope,
            hasProximitySensor = proximityDetectionManager.hasProximitySensor,
            hasSignificantMotion = motionDetectionManager.hasSignificantMotion,
            isDisplayInteractive = displayActionManager.isDisplayInteractive()
        )
    )
    val state: StateFlow<SmartGestureHubState> = _state.asStateFlow()

    var onCloseActiveScreenSaverRequested: (() -> Unit)? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    displayActionManager.onDisplayTurnedOn()
                    motionDetectionManager.updateDisplayInteractive(true)
                    refreshStateAndListeners()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    motionDetectionManager.updateDisplayInteractive(false)
                    refreshStateAndListeners()
                }
                Intent.ACTION_USER_PRESENT -> {
                    displayActionManager.onDisplayTurnedOn()
                    refreshStateAndListeners()
                }
            }
        }
    }

    init {
        registerScreenBroadcasts()
        scope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                applySettingsToRecognizers(settings)
                refreshStateAndListeners()
            }
        }
        scope.launch {
            fullScreenDetector.state.collectLatest { fs ->
                updateExternalPutDownSuppression(fs.isFullScreenActive)
            }
        }
    }

    private fun registerScreenBroadcasts() {
        if (screenReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(screenReceiver, filter)
            }
            screenReceiverRegistered = true
        }
    }

    private fun applySettingsToRecognizers(s: PandaSettings) {
        val rtw = motionDetectionManager.raiseToWakeRecognizer
        rtw.enabled = s.raiseToWakeEnabled && motionDetectionManager.hasAccelerometer
        rtw.sensitivity = GestureSensitivity.fromCode(s.raiseToWakeSensitivity)
        rtw.cooldownMs = s.raiseToWakeCooldownMs

        val pts = motionDetectionManager.pickUpToSilenceRecognizer
        pts.enabled = s.pickUpToSilenceEnabled && motionDetectionManager.hasAccelerometer
        pts.sensitivity = GestureSensitivity.fromCode(s.pickUpToSilenceSensitivity)

        val pds = motionDetectionManager.putDownToSleepRecognizer
        pds.enabled = s.putDownToSleepEnabled && motionDetectionManager.hasAccelerometer
        pds.sensitivity = GestureSensitivity.fromCode(s.putDownToSleepSensitivity)
        pds.stabilityDelayMs = s.putDownToSleepDelayMs
        pds.cooldownMs = s.putDownToSleepCooldownMs
        pds.surfaceMode = s.putDownSurfaceMode

        val ssm = motionDetectionManager.screenSaverRecognizer
        ssm.enabled = s.screenSaverEnabled && motionDetectionManager.hasAccelerometer
        ssm.sensitivity = GestureSensitivity.fromCode(s.screenSaverMotionSensitivity)
        ssm.cooldownMs = s.screenSaverCooldownMs

        val proxEval = proximityDetectionManager.evaluator
        proxEval.enabled = s.proximityCallAnswerEnabled && proximityDetectionManager.hasProximitySensor
        proxEval.confirmationDelayMs = s.proximityAnswerDelayMs
    }

    fun setActivityForeground(foreground: Boolean) {
        isActivityForeground = foreground
        if (foreground) {
            motionDetectionManager.updateDisplayInteractive(displayActionManager.isDisplayInteractive())
        }
        refreshStateAndListeners()
    }

    fun setBackgroundServiceActive(active: Boolean) {
        isBackgroundServiceRunning = active
        refreshStateAndListeners()
    }

    fun setScreenSaverActive(active: Boolean) {
        isScreenSaverVisible = active
        refreshStateAndListeners()
    }

    private fun updateExternalPutDownSuppression(fullScreenActive: Boolean = fullScreenDetector.state.value.isFullScreenActive) {
        val callBusy = callGestureManager.runtimeState.isCallActiveOrOffhook
        motionDetectionManager.putDownToSleepRecognizer.isSuppressedExternal =
            callBusy || fullScreenActive || isScreenSaverVisible
    }

    private fun onCallStateUpdated(isRinging: Boolean, isAnyCallActive: Boolean) {
        // Conflict Prevention: Immediately close Smart Screen Saver if an incoming/outgoing call begins
        if (isAnyCallActive && isScreenSaverVisible) {
            onCloseActiveScreenSaverRequested?.invoke()
        }

        val (extConnected, _) = callGestureManager.inspectExternalCallAudioRoute()
        motionDetectionManager.pickUpToSilenceRecognizer.isCallRinging = isRinging
        proximityDetectionManager.evaluator.externalAudioRouteActive = extConnected
        proximityDetectionManager.evaluator.onCallRingingChanged(
            ringing = isRinging,
            currentProximityNear = proximityDetectionManager.isNear
        )
        motionDetectionManager.setExternalEarPostureSamplingNeeded(
            isRinging && currentSettings.proximityCallAnswerEnabled && !extConnected
        )
        updateExternalPutDownSuppression()
        refreshStateAndListeners()
    }

    fun refreshStateAndListeners() {
        val s = currentSettings
        val allowSensorExecution = isActivityForeground || isBackgroundServiceRunning || s.backgroundMonitoringEnabled

        // 1. Call state listener only needed if Pick Up to Silence or Proximity Answer is enabled
        val needCallMonitoring = (s.pickUpToSilenceEnabled && motionDetectionManager.hasAccelerometer) ||
            (s.proximityCallAnswerEnabled && proximityDetectionManager.hasProximitySensor)
        callGestureManager.syncCallStateRegistration(needCallMonitoring && allowSensorExecution)

        val callRuntime = callGestureManager.runtimeState
        val isInteractive = displayActionManager.isDisplayInteractive()

        // 2. Proximity sensor only needed during ringing call (Proximity Answer) or when pocket-guarded gestures are armed
        val needProximityForCall = s.proximityCallAnswerEnabled && callRuntime.isCallRinging && !callRuntime.externalHeadsetOrCarConnected
        val needProximityForPocketGuard = (!isInteractive && (s.raiseToWakeEnabled || s.screenSaverEnabled)) ||
            (isInteractive && s.putDownToSleepEnabled)
        proximityDetectionManager.syncRegistration(
            allowSensorExecution && (needProximityForCall || needProximityForPocketGuard)
        )

        // 3. Motion sensor registration (only registers when an enabled gesture applies to current screen/call state)
        updateExternalPutDownSuppression()
        motionDetectionManager.syncRegistration(allowListening = allowSensorExecution)

        val adminOk = displayActionManager.isDeviceAdminActive()
        val writeSettingsOk = displayActionManager.canWriteSystemSettings()
        val overlayOk = displayActionManager.canDrawOverlays()
        val usageOk = AutoDimController.hasUsageStatsPermission(context)

        _state.value = _state.value.copy(
            hasAccelerometer = motionDetectionManager.hasAccelerometer,
            hasGyroscope = motionDetectionManager.hasGyroscope,
            hasProximitySensor = proximityDetectionManager.hasProximitySensor,
            hasSignificantMotion = motionDetectionManager.hasSignificantMotion,
            hasReadPhoneStatePermission = callRuntime.hasReadPhoneStatePermission,
            hasAnswerPhoneCallsPermission = callRuntime.hasAnswerPhoneCallsPermission,
            isDeviceAdminActive = adminOk,
            canWriteSystemSettings = writeSettingsOk,
            canDrawOverlays = overlayOk,
            hasUsageStatsAccess = usageOk,
            isDisplayInteractive = isInteractive,
            isCallRinging = callRuntime.isCallRinging,
            isCallActive = callRuntime.isCallActiveOrOffhook,
            externalAudioConnected = callRuntime.externalHeadsetOrCarConnected,
            externalAudioLabel = callRuntime.externalRouteDescription,
            isScreenSaverCurrentlyVisible = isScreenSaverVisible,
            backgroundServiceActive = isBackgroundServiceRunning || s.backgroundMonitoringEnabled,
            raiseToWakeStatus = computeRaiseToWakeStatus(s, isInteractive, allowSensorExecution),
            pickUpToSilenceStatus = computePickUpToSilenceStatus(s, callRuntime),
            proximityAnswerStatus = computeProximityAnswerStatus(s, callRuntime),
            putDownToSleepStatus = computePutDownStatus(s, adminOk, writeSettingsOk, allowSensorExecution),
            screenSaverStatus = computeScreenSaverStatus(s, overlayOk, allowSensorExecution)
        )
    }

    private fun computeRaiseToWakeStatus(s: PandaSettings, interactive: Boolean, canRun: Boolean): String = when {
        !motionDetectionManager.hasAccelerometer -> "Unavailable (No Accelerometer)"
        !s.raiseToWakeEnabled -> "Disabled"
        s.screenSaverEnabled -> "Cooperating with Smart Screen Saver (Ambient Peek priority)"
        !canRun -> "Enable Background Gesture Service for screen-off detection"
        interactive -> "Armed • Standby while screen is active"
        else -> "Actively monitoring lift motion (${s.raiseToWakeSensitivity})"
    }

    private fun computePickUpToSilenceStatus(s: PandaSettings, call: CallGestureRuntimeState): String = when {
        !motionDetectionManager.hasAccelerometer -> "Unavailable (No Accelerometer)"
        !s.pickUpToSilenceEnabled -> "Disabled"
        !call.hasReadPhoneStatePermission -> "Needs Phone State permission"
        call.isCallRinging -> if (call.silencedCurrentCall) "Ringtone silenced" else "Ringing — Lift phone to silence"
        else -> "Standby — Activates only on incoming calls"
    }

    private fun computeProximityAnswerStatus(s: PandaSettings, call: CallGestureRuntimeState): String = when {
        !proximityDetectionManager.hasProximitySensor -> "Unavailable (No Proximity Sensor)"
        !s.proximityCallAnswerEnabled -> "Disabled"
        !call.hasReadPhoneStatePermission || !call.hasAnswerPhoneCallsPermission -> "Needs Phone & Answer Calls permissions"
        call.externalHeadsetOrCarConnected -> "Paused (${call.externalRouteDescription} active)"
        call.isCallRinging -> "Ringing — Bring upright near ear (${s.proximityAnswerDelayMs}ms hold)"
        else -> "Standby — Activates only on incoming calls"
    }

    private fun computePutDownStatus(
        s: PandaSettings,
        adminOk: Boolean,
        writeOk: Boolean,
        canRun: Boolean
    ): String = when {
        !motionDetectionManager.hasAccelerometer -> "Unavailable (No Accelerometer)"
        !s.putDownToSleepEnabled -> "Disabled"
        !adminOk && !writeOk -> "Authorize Screen-Lock Admin (or Write Settings)"
        !canRun -> "Active in-app • Enable Background Service for all apps"
        adminOk -> "Armed • Instant Lock (${s.putDownToSleepDelayMs}ms flat stability)"
        else -> "Armed • 10s Timeout Fallback (Authorize Admin for instant lock)"
    }

    private fun computeScreenSaverStatus(s: PandaSettings, overlayOk: Boolean, canRun: Boolean): String = when {
        !s.screenSaverEnabled -> "Disabled (System DreamService still available)"
        !motionDetectionManager.hasAccelerometer -> "System DreamService Ready (No motion sensor)"
        !overlayOk -> "Grant Overlay permission for screen-off motion launch"
        !canRun -> "Enable Background Gesture Service for screen-off motion"
        isScreenSaverVisible -> "Displaying ${s.screenSaverStyle}"
        else -> "Armed • Style: ${s.screenSaverStyle.replace('_', ' ')}"
    }

    private fun handleRaiseToWake() {
        val (ok, msg) = displayActionManager.wakeDisplayRespectingLockScreen(forceForTest = false)
        if (ok && currentSettings.raiseToWakeVibrationEnabled) {
            SystemUtils.triggerHapticPulse(context, 40L)
        }
        updateLastEvent("Raise to Wake", "Phone lifted from resting position", msg, ok, notifyAqua = ok)
    }

    private fun handlePickUpToSilence() {
        val (ok, msg) = callGestureManager.silenceIncomingCallRinger()
        if (ok && currentSettings.pickUpToSilenceVibrationEnabled) {
            SystemUtils.triggerHapticPulse(context, 50L)
        }
        // Never show Aqua Dynamics during an active/incoming call!
        updateLastEvent("Pick Up to Silence", "Ringing phone lifted", msg, ok, notifyAqua = false)
    }

    private fun handleProximityNearEarAnswer() {
        val (ok, msg) = callGestureManager.answerIncomingRingingCall()
        if (ok && currentSettings.proximityAnswerVibrationEnabled) {
            SystemUtils.triggerHapticPulse(context, 55L)
        }
        // Never show Aqua Dynamics during an active/incoming call!
        updateLastEvent("Proximity Call Answer", "Brought upright near ear", msg, ok, notifyAqua = false)
    }

    private fun handlePutDownToSleep(placement: FlatSurfacePlacement) {
        if (callGestureManager.runtimeState.isCallActiveOrOffhook) return
        val outcome = displayActionManager.turnDisplayOffOrSleep()
        if (outcome.succeeded) {
            // Apply 4s post-sleep settling guard so Smart Screen Saver / Raise to Wake never bounce-trigger
            motionDetectionManager.applyPostSleepGuard(guardDurationMs = 4500L)
            if (currentSettings.putDownToSleepVibrationEnabled) {
                SystemUtils.triggerHapticPulse(context, 45L)
            }
        }
        val surfaceLabel = if (placement == FlatSurfacePlacement.SCREEN_DOWN) "Screen-Down" else "Screen-Up"
        updateLastEvent(
            ruleName = "Put Down to Sleep",
            trigger = "Stable $surfaceLabel surface placement",
            action = outcome.label,
            succeeded = outcome.succeeded,
            notifyAqua = !outcome.succeeded
        )
    }

    private fun handleScreenSaverMotion() {
        if (callGestureManager.runtimeState.isCallActiveOrOffhook || isScreenSaverVisible) return
        val (ok, msg) = displayActionManager.launchSmartScreenSaver(isPreview = false)
        if (ok && currentSettings.screenSaverVibrationEnabled) {
            SystemUtils.triggerHapticPulse(context, 35L)
        }
        updateLastEvent("Smart Screen Saver", "Screen-off motion detected", msg, ok, notifyAqua = false)
    }

    fun triggerRaiseToWakeDiagnosticTest(): Pair<Boolean, String> {
        val (ok, msg) = displayActionManager.wakeDisplayRespectingLockScreen(forceForTest = true)
        if (ok && currentSettings.raiseToWakeVibrationEnabled) {
            SystemUtils.triggerHapticPulse(context, 40L)
        }
        updateLastEvent("Raise to Wake (Test)", "Manual Diagnostic Wake Pulse", msg, ok, notifyAqua = true)
        return ok to msg
    }

    fun triggerPutDownSleepDiagnosticTest(): Pair<Boolean, String> {
        val outcome = displayActionManager.turnDisplayOffOrSleep()
        if (outcome.succeeded) {
            motionDetectionManager.applyPostSleepGuard(guardDurationMs = 4500L)
            if (currentSettings.putDownToSleepVibrationEnabled) {
                SystemUtils.triggerHapticPulse(context, 45L)
            }
        }
        updateLastEvent("Put Down to Sleep (Test)", "Manual Sleep Verification", outcome.label, outcome.succeeded, notifyAqua = false)
        return outcome.succeeded to outcome.label
    }

    fun launchScreenSaverPreview(styleOverride: String? = null): Pair<Boolean, String> {
        return displayActionManager.launchSmartScreenSaver(isPreview = true, styleOverride = styleOverride)
    }

    private fun updateLastEvent(
        ruleName: String,
        trigger: String,
        action: String,
        succeeded: Boolean,
        notifyAqua: Boolean
    ) {
        _state.value = _state.value.copy(lastTriggeredSummary = "$ruleName: $action")
        refreshStateAndListeners()

        if (notifyAqua && !callGestureManager.runtimeState.isCallActiveOrOffhook) {
            aquaDynamicsManager.onSupportedSensorOrSystemEvent(
                category = AquaEventCategory.DEVICE_INTERACTIONS,
                conditionGroupKey = "SMART_GESTURE_${ruleName.uppercase().replace(' ', '_')}",
                signatureKey = "${ruleName}_${System.currentTimeMillis() / 3000L}",
                priority = AquaEventPriority.NORMAL,
                title = ruleName,
                subtitle = action,
                badgeText = if (succeeded) "ACTIVE" else "SETUP",
                iconType = "SENSOR",
                accentColorHex = if (succeeded) 0xFF00E5FFL else 0xFFFFB300L
            )
        }

        scope.launch {
            runCatching {
                dao.insertAutomationEvent(
                    AutomationEventEntity(
                        ruleName = ruleName,
                        triggerDescription = trigger,
                        actionDescription = action,
                        succeeded = succeeded
                    )
                )
            }
        }
    }
}
