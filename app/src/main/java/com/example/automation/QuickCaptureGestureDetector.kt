package com.example.automation

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.common.SystemUtils
import com.example.data.local.database.AutomationEventEntity
import com.example.data.local.database.PandaDao
import com.example.data.local.datastore.PandaPreferencesRepository
import com.example.data.local.datastore.PandaSettings
import com.example.notifications.AlertEngine
import com.example.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

enum class QuickCaptureSensitivity(
    val code: String,
    val displayLabel: String,
    val gyroForwardPeakRadSec: Float,
    val gyroReturnPeakRadSec: Float,
    val accelLateralPeakMps2: Float
) {
    LOW(
        code = "LOW",
        displayLabel = "Low",
        gyroForwardPeakRadSec = 6.2f,
        gyroReturnPeakRadSec = 4.4f,
        accelLateralPeakMps2 = 13.5f
    ),
    MEDIUM(
        code = "MEDIUM",
        displayLabel = "Medium",
        gyroForwardPeakRadSec = 4.5f,
        gyroReturnPeakRadSec = 3.2f,
        accelLateralPeakMps2 = 10.5f
    ),
    HIGH(
        code = "HIGH",
        displayLabel = "High",
        gyroForwardPeakRadSec = 3.2f,
        gyroReturnPeakRadSec = 2.3f,
        accelLateralPeakMps2 = 8.2f
    );

    companion object {
        fun fromCode(code: String): QuickCaptureSensitivity {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MEDIUM
        }
    }
}

enum class QuickCaptureSensorMode(val label: String) {
    GYRO_PLUS_ACCEL("Gyroscope + Accelerometer (Calibrated)"),
    ACCEL_ONLY_FALLBACK("Accelerometer Fallback (No Gyro)"),
    UNSUPPORTED("Motion Sensors Unavailable")
}

data class QuickCaptureState(
    val isEnabled: Boolean = false,
    val sensitivity: QuickCaptureSensitivity = QuickCaptureSensitivity.MEDIUM,
    val vibrationEnabled: Boolean = true,
    val cooldownMs: Long = 3000L,
    val sensorMode: QuickCaptureSensorMode = QuickCaptureSensorMode.UNSUPPORTED,
    val isActivelyListening: Boolean = false,
    val isScreenInteractive: Boolean = true,
    val cameraAppAvailable: Boolean = true,
    val resolvedCameraPackage: String = "Default Camera",
    val lastDetectionStatus: String = "Ready — Enable Shake to Camera to monitor wrist twists",
    val lastTriggerTimestampMs: Long = 0L,
    val triggerCount: Int = 0
)

/**
 * Pure, deterministic Moto-style Quick Capture gesture recognizer.
 *
 * Recognizes two deliberate bidirectional wrist twists within a tight time window while rejecting:
 * - Single shakes or single twists
 * - Normal walking step cadence (~1.5–2.5 Hz vertical/forward acceleration with low twist velocity)
 * - Uncontrolled impact jolts (> 26 m/s^2 linear shock without clean rotational return)
 * - Pocket/bag proximity occlusion
 * - Screen-off state & post-trigger cooldown window
 */
class QuickCaptureRecognizer(
    private val hasGyroscope: Boolean,
    private val hasAccelerometer: Boolean,
    gyroMaxRangeRadSec: Float = 34.9f,
    accelMaxRangeMps2: Float = 39.2f
) {
    private enum class TwistPhase {
        IDLE,
        TWIST_1_OUT,
        TWIST_1_DONE,
        TWIST_2_OUT
    }

    // Calibrate ceiling for devices whose MEMS gyroscope or accelerometer has a narrow dynamic range
    private val gyroRangeCeiling: Float = if (gyroMaxRangeRadSec > 1f) {
        (gyroMaxRangeRadSec * 0.72f).coerceAtLeast(2.6f)
    } else {
        34.9f
    }
    private val accelRangeCeiling: Float = if (accelMaxRangeMps2 > 9.8f) {
        (accelMaxRangeMps2 * 0.75f).coerceAtLeast(8.0f)
    } else {
        39.2f
    }

    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) resetGesturePhase()
        }

    var sensitivity: QuickCaptureSensitivity = QuickCaptureSensitivity.MEDIUM
    var cooldownMs: Long = 3000L
    var isScreenInteractive: Boolean = true
        set(value) {
            field = value
            if (!value) resetGesturePhase()
        }
    var proximityNear: Boolean = false
        set(value) {
            field = value
            if (value) resetGesturePhase()
        }

    private var phase: TwistPhase = TwistPhase.IDLE
    private var activeDirectionSign: Float = 0f
    private var gestureStartMs: Long = 0L
    private var phaseTimestampMs: Long = 0L
    var lastTriggerTimeMs: Long = 0L
        private set

    // Recent linear acceleration shock/walking tracker from accelerometer
    private var latestLinearAccelMps2: Float = 0f
    private var latestVerticalExcessMps2: Float = 0f
    private var recentWalkingStepLikeCount: Int = 0
    private var lastLinearPeakMs: Long = 0L

    fun resetAll() {
        resetGesturePhase()
        lastTriggerTimeMs = 0L
        latestLinearAccelMps2 = 0f
        latestVerticalExcessMps2 = 0f
        recentWalkingStepLikeCount = 0
        lastLinearPeakMs = 0L
    }

    fun resetGesturePhase() {
        phase = TwistPhase.IDLE
        activeDirectionSign = 0f
        gestureStartMs = 0L
        phaseTimestampMs = 0L
    }

    private fun canEvaluate(nowMs: Long): Boolean {
        if (!enabled || !isScreenInteractive || proximityNear) return false
        if (!hasGyroscope && !hasAccelerometer) return false
        if (lastTriggerTimeMs > 0L && nowMs - lastTriggerTimeMs < cooldownMs) {
            resetGesturePhase()
            return false
        }
        return true
    }

    /**
     * Feed accelerometer reading (m/s^2).
     * Used as linear-motion/walking corroboration when a gyroscope is present,
     * or as calibrated double-lateral-reversal fallback when hardware gyroscope is absent.
     *
     * @return true if gesture completed and camera should launch.
     */
    fun onAccelerometerSample(nowMs: Long, ax: Float, ay: Float, az: Float): Boolean {
        val totalMag = sqrt(ax * ax + ay * ay + az * az)
        latestLinearAccelMps2 = abs(totalMag - SensorManager.GRAVITY_EARTH)
        latestVerticalExcessMps2 = abs(abs(ay) - SensorManager.GRAVITY_EARTH)

        // Track repetitive ~1.5-3.0 Hz vertical walking cadence (step interval ~320ms..720ms)
        if (latestLinearAccelMps2 in 2.2f..7.5f && abs(ax) < 6.0f) {
            val dtStep = nowMs - lastLinearPeakMs
            if (dtStep in 300L..750L) {
                recentWalkingStepLikeCount = (recentWalkingStepLikeCount + 1).coerceAtMost(6)
            } else if (dtStep > 1200L) {
                recentWalkingStepLikeCount = 0
            }
            if (dtStep > 260L) {
                lastLinearPeakMs = nowMs
            }
        } else if (nowMs - lastLinearPeakMs > 1200L) {
            recentWalkingStepLikeCount = 0
        }

        // If hardware gyroscope is present, gyroscope drives twist phase recognition
        if (hasGyroscope) return false
        if (!canEvaluate(nowMs)) return false

        // Reject severe drop/impact shock or pure vertical walking bounce
        if (latestLinearAccelMps2 > 28f) {
            resetGesturePhase()
            return false
        }
        if (recentWalkingStepLikeCount >= 3 && abs(ax) < sensitivity.accelLateralPeakMps2 * 1.15f) {
            return false
        }

        val forwardThreshold = min(sensitivity.accelLateralPeakMps2, accelRangeCeiling)
        val returnThreshold = forwardThreshold * 0.78f

        // Require lateral X-axis twist/chop dominance over vertical gravity axis bounce
        if (abs(ax) < returnThreshold || abs(ax) < latestVerticalExcessMps2 * 0.85f) {
            expireStalePhase(nowMs)
            return false
        }

        return advancePhaseMachine(
            nowMs = nowMs,
            signedSignal = ax,
            forwardThreshold = forwardThreshold,
            returnThreshold = returnThreshold,
            minHalfTwistMs = 40L,
            maxHalfTwistMs = 420L,
            minInterTwistMs = 45L,
            maxInterTwistMs = 850L,
            maxTotalDurationMs = 1500L
        )
    }

    /**
     * Feed gyroscope reading (rad/s).
     * Moto-style Quick Capture rotates briskly around the phone's longitudinal Y-axis
     * (forearm pronation/supination) or Z-axis roll, reversing direction twice.
     *
     * @return true if two deliberate wrist twists completed and camera should launch.
     */
    fun onGyroscopeSample(nowMs: Long, gx: Float, gy: Float, gz: Float): Boolean {
        if (!hasGyroscope || !canEvaluate(nowMs)) return false

        // Reject uncontrolled drop/slam shock
        if (latestLinearAccelMps2 > 27.5f) {
            resetGesturePhase()
            return false
        }

        // Dominant wrist-twist angular velocity is longitudinal Y-axis (or Z-axis roll)
        val dominantTwist = if (abs(gy) >= abs(gz) * 0.75f) gy else gz
        val absTwist = abs(dominantTwist)

        // Reject pure pitch/arm-swing motion where X-axis rotation dominates
        if (abs(gx) > absTwist * 1.30f && abs(gx) > 2.5f) {
            expireStalePhase(nowMs)
            return false
        }

        val forwardThreshold = min(sensitivity.gyroForwardPeakRadSec, gyroRangeCeiling)
        val returnThreshold = min(sensitivity.gyroReturnPeakRadSec, gyroRangeCeiling * 0.75f)

        if (absTwist < returnThreshold) {
            expireStalePhase(nowMs)
            return false
        }

        return advancePhaseMachine(
            nowMs = nowMs,
            signedSignal = dominantTwist,
            forwardThreshold = forwardThreshold,
            returnThreshold = returnThreshold,
            minHalfTwistMs = 35L,
            maxHalfTwistMs = 450L,
            minInterTwistMs = 40L,
            maxInterTwistMs = 850L,
            maxTotalDurationMs = 1500L
        )
    }

    private fun expireStalePhase(nowMs: Long) {
        if (phase == TwistPhase.IDLE) return
        val dtPhase = nowMs - phaseTimestampMs
        val maxWait = if (phase == TwistPhase.TWIST_1_DONE) 850L else 450L
        if (dtPhase > maxWait || (nowMs - gestureStartMs) > 1500L) {
            resetGesturePhase()
        }
    }

    private fun advancePhaseMachine(
        nowMs: Long,
        signedSignal: Float,
        forwardThreshold: Float,
        returnThreshold: Float,
        minHalfTwistMs: Long,
        maxHalfTwistMs: Long,
        minInterTwistMs: Long,
        maxInterTwistMs: Long,
        maxTotalDurationMs: Long
    ): Boolean {
        val mag = abs(signedSignal)
        val dir = sign(signedSignal)
        if (dir == 0f) return false

        expireStalePhase(nowMs)

        when (phase) {
            TwistPhase.IDLE -> {
                if (mag >= forwardThreshold) {
                    phase = TwistPhase.TWIST_1_OUT
                    activeDirectionSign = dir
                    gestureStartMs = nowMs
                    phaseTimestampMs = nowMs
                }
            }

            TwistPhase.TWIST_1_OUT -> {
                val dt = nowMs - phaseTimestampMs
                if (dir == -activeDirectionSign && mag >= returnThreshold && dt in minHalfTwistMs..maxHalfTwistMs) {
                    // First deliberate out-and-back twist completed! (Single twist never triggers camera)
                    phase = TwistPhase.TWIST_1_DONE
                    activeDirectionSign = dir
                    phaseTimestampMs = nowMs
                } else if (dt > maxHalfTwistMs) {
                    resetGesturePhase()
                }
            }

            TwistPhase.TWIST_1_DONE -> {
                val dt = nowMs - phaseTimestampMs
                if (mag >= forwardThreshold * 0.90f && dt in minInterTwistMs..maxInterTwistMs) {
                    // Second deliberate twist started
                    phase = TwistPhase.TWIST_2_OUT
                    activeDirectionSign = dir
                    phaseTimestampMs = nowMs
                } else if (dt > maxInterTwistMs) {
                    resetGesturePhase()
                }
            }

            TwistPhase.TWIST_2_OUT -> {
                val dt = nowMs - phaseTimestampMs
                val totalDuration = nowMs - gestureStartMs
                if (
                    dir == -activeDirectionSign &&
                    mag >= returnThreshold &&
                    dt in minHalfTwistMs..maxHalfTwistMs &&
                    totalDuration <= maxTotalDurationMs
                ) {
                    // Second deliberate out-and-back twist completed -> Trigger Quick Capture!
                    lastTriggerTimeMs = nowMs
                    resetGesturePhase()
                    return true
                } else if (dt > maxHalfTwistMs || totalDuration > maxTotalDurationMs) {
                    resetGesturePhase()
                }
            }
        }
        return false
    }
}

/**
 * Lifecycle-aware Android hardware controller for Shake to Camera (Moto-style Quick Capture).
 * Manages SensorManager registration, screen-on/off battery optimization, camera intent verification,
 * tactile confirmation, and foreground/background camera launching.
 */
class QuickCaptureController(
    private val context: Context,
    private val dao: PandaDao,
    private val preferencesRepository: PandaPreferencesRepository,
    private val alertEngine: AlertEngine
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    val sensorMode: QuickCaptureSensorMode = when {
        gyroscope != null && accelerometer != null -> QuickCaptureSensorMode.GYRO_PLUS_ACCEL
        gyroscope != null -> QuickCaptureSensorMode.GYRO_PLUS_ACCEL
        accelerometer != null -> QuickCaptureSensorMode.ACCEL_ONLY_FALLBACK
        else -> QuickCaptureSensorMode.UNSUPPORTED
    }

    val recognizer = QuickCaptureRecognizer(
        hasGyroscope = gyroscope != null,
        hasAccelerometer = accelerometer != null,
        gyroMaxRangeRadSec = gyroscope?.maximumRange ?: 34.9f,
        accelMaxRangeMps2 = accelerometer?.maximumRange ?: 39.2f
    )

    private var currentSettings = PandaSettings()
    private var isActivityForeground = false
    private var isBackgroundServiceRunning = false
    private var isSensorRegistered = false
    private var screenReceiverRegistered = false

    private val _state = MutableStateFlow(
        QuickCaptureState(
            sensorMode = sensorMode,
            isScreenInteractive = isDeviceInteractiveSafe()
        )
    )
    val state: StateFlow<QuickCaptureState> = _state.asStateFlow()
    var onGestureTriggered: ((String) -> Unit)? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenInteractiveChanged(false)
                Intent.ACTION_SCREEN_ON -> handleScreenInteractiveChanged(true)
            }
        }
    }

    init {
        refreshCameraAvailability()
        registerScreenStateReceiver()
        scope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                val sens = QuickCaptureSensitivity.fromCode(settings.shakeToCameraSensitivity)
                recognizer.sensitivity = sens
                recognizer.cooldownMs = settings.shakeToCameraCooldownMs
                recognizer.enabled = settings.shakeToCameraEnabled

                syncSensorRegistration()
                _state.value = _state.value.copy(
                    isEnabled = settings.shakeToCameraEnabled,
                    sensitivity = sens,
                    vibrationEnabled = settings.shakeToCameraVibrationEnabled,
                    cooldownMs = settings.shakeToCameraCooldownMs,
                    lastDetectionStatus = when {
                        sensorMode == QuickCaptureSensorMode.UNSUPPORTED ->
                            "Unsupported: No gyroscope or accelerometer found on this device"
                        !settings.shakeToCameraEnabled ->
                            "Disabled — Motion sensors stopped (0% battery impact)"
                        !_state.value.isScreenInteractive ->
                            "Paused while screen is off (Android sensor & lock-screen policy)"
                        _state.value.triggerCount == 0 ->
                            "Armed (${sens.displayLabel} sensitivity) — Double-twist wrist to open Camera"
                        else -> _state.value.lastDetectionStatus
                    }
                )
            }
        }
    }

    private fun isDeviceInteractiveSafe(): Boolean {
        return runCatching { powerManager.isInteractive }.getOrDefault(true)
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(screenStateReceiver, filter)
            }
            screenReceiverRegistered = true
        }
    }

    fun setActivityForeground(inForeground: Boolean) {
        isActivityForeground = inForeground
        if (inForeground) {
            refreshCameraAvailability()
            recognizer.isScreenInteractive = isDeviceInteractiveSafe()
        }
        syncSensorRegistration()
    }

    fun setBackgroundServiceActive(active: Boolean) {
        isBackgroundServiceRunning = active
        syncSensorRegistration()
    }

    /**
     * Exposed for deterministic testing and OS screen-state transitions.
     */
    fun handleScreenInteractiveChanged(interactive: Boolean) {
        recognizer.isScreenInteractive = interactive
        syncSensorRegistration()
        _state.value = _state.value.copy(
            isScreenInteractive = interactive,
            lastDetectionStatus = if (!interactive && currentSettings.shakeToCameraEnabled) {
                "Paused while screen is off (Android sensor & lock-screen security)"
            } else if (interactive && currentSettings.shakeToCameraEnabled) {
                "Armed (${recognizer.sensitivity.displayLabel} sensitivity) — Screen active"
            } else {
                _state.value.lastDetectionStatus
            }
        )
    }

    @Synchronized
    fun syncSensorRegistration() {
        val shouldListen = sensorMode != QuickCaptureSensorMode.UNSUPPORTED &&
            currentSettings.shakeToCameraEnabled &&
            recognizer.isScreenInteractive &&
            (isActivityForeground || isBackgroundServiceRunning || currentSettings.backgroundMonitoringEnabled)

        if (shouldListen && !isSensorRegistered) {
            // SENSOR_DELAY_GAME (~20ms) provides responsive wrist-twist peak capture without excessive battery drain
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            accelerometer?.let {
                val rate = if (gyroscope != null) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME
                sensorManager.registerListener(this, it, rate)
            }
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            isSensorRegistered = true
            _state.value = _state.value.copy(isActivelyListening = true)
        } else if (!shouldListen && isSensorRegistered) {
            sensorManager.unregisterListener(this)
            recognizer.resetGesturePhase()
            isSensorRegistered = false
            _state.value = _state.value.copy(isActivelyListening = false)
        }
    }

    /**
     * Resolves a safe, standard Android Camera intent that ONLY opens the camera viewfinder
     * and never automatically captures photos or bypasses lock-screen security.
     */
    fun resolveCameraLaunchIntent(): Pair<Intent?, String> {
        val pm = context.packageManager
        val candidates = listOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )

        for (candidate in candidates) {
            val resolvedList = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        candidate,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(candidate, PackageManager.MATCH_DEFAULT_ONLY)
                }
            }.getOrDefault(emptyList())

            if (resolvedList.isNotEmpty()) {
                val pkgName = resolvedList.first().activityInfo?.packageName ?: "System Camera"
                val launchIntent = Intent(candidate).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                return launchIntent to pkgName
            }

            val fallbackComponent = runCatching { candidate.resolveActivity(pm) }.getOrNull()
            if (fallbackComponent != null) {
                val launchIntent = Intent(candidate).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                return launchIntent to fallbackComponent.packageName
            }
        }
        return null to "No Camera App Installed"
    }

    fun refreshCameraAvailability(): Boolean {
        val (intent, label) = resolveCameraLaunchIntent()
        val available = intent != null
        _state.value = _state.value.copy(
            cameraAppAvailable = available,
            resolvedCameraPackage = label
        )
        return available
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val nowMs = if (event.timestamp > 0L) {
            event.timestamp / 1_000_000L
        } else {
            System.currentTimeMillis()
        }

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
                val isNear = event.values[0] < maxRange && event.values[0] < 5f
                if (currentSettings.proximityPocketGuardEnabled || isNear) {
                    recognizer.proximityNear = isNear
                } else {
                    recognizer.proximityNear = false
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val triggered = recognizer.onAccelerometerSample(
                    nowMs = nowMs,
                    ax = event.values[0],
                    ay = event.values[1],
                    az = event.values[2]
                )
                if (triggered) {
                    onQuickCaptureRecognized(isManualTest = false)
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val triggered = recognizer.onGyroscopeSample(
                    nowMs = nowMs,
                    gx = event.values[0],
                    gy = event.values[1],
                    gz = event.values[2]
                )
                if (triggered) {
                    onQuickCaptureRecognized(isManualTest = false)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Triggered when either two deliberate wrist twists are detected or the user taps "Test Gesture".
     */
    fun onQuickCaptureRecognized(isManualTest: Boolean): Pair<Boolean, String> {
        val nowWallMs = System.currentTimeMillis()
        val (cameraIntent, cameraPkg) = resolveCameraLaunchIntent()

        if (cameraIntent == null) {
            val errMsg = "Camera unavailable: No compatible Camera app found on this device"
            _state.value = _state.value.copy(
                cameraAppAvailable = false,
                resolvedCameraPackage = cameraPkg,
                lastDetectionStatus = errMsg
            )
            logQuickCaptureEvent(
                triggerDesc = if (isManualTest) "Manual Test Gesture" else "Double-Twist Gesture",
                actionDesc = errMsg,
                succeeded = false
            )
            return false to errMsg
        }

        if (currentSettings.shakeToCameraVibrationEnabled) {
            SystemUtils.triggerQuickCaptureVibration(context)
        }

        val appVisibleInForeground = isActivityForeground || isAppProcessInForeground()

        // On Android 10+ (API 29+), background processes cannot directly start Activities without user interaction.
        if (!appVisibleInForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isManualTest) {
            postCameraQuickLaunchNotification(cameraIntent)
            val bgMsg = "Double-twist detected in background • Tap notification to open Camera ($cameraPkg)"
            _state.value = _state.value.copy(
                cameraAppAvailable = true,
                resolvedCameraPackage = cameraPkg,
                lastDetectionStatus = bgMsg,
                lastTriggerTimestampMs = nowWallMs,
                triggerCount = _state.value.triggerCount + 1
            )
            logQuickCaptureEvent(
                triggerDesc = "Background Double-Twist",
                actionDesc = "Posted Quick Capture Camera prompt (Android 10+ background restriction)",
                succeeded = true
            )
            return true to bgMsg
        }

        return try {
            context.startActivity(cameraIntent)
            val prefix = if (isManualTest) "Test succeeded" else "Double-twist detected"
            val okMsg = "$prefix • Launched Camera ($cameraPkg)"
            _state.value = _state.value.copy(
                cameraAppAvailable = true,
                resolvedCameraPackage = cameraPkg,
                lastDetectionStatus = okMsg,
                lastTriggerTimestampMs = nowWallMs,
                triggerCount = _state.value.triggerCount + 1
            )
            logQuickCaptureEvent(
                triggerDesc = if (isManualTest) "Test Shake to Camera" else "Moto Quick Capture (2× Twist)",
                actionDesc = "Opened Camera UI ($cameraPkg)",
                succeeded = true
            )
            if (!isManualTest) {
                onGestureTriggered?.invoke(cameraPkg)
            }
            true to okMsg
        } catch (e: Exception) {
            postCameraQuickLaunchNotification(cameraIntent)
            val fallbackMsg = "Camera launch restricted (${e.javaClass.simpleName}) • Notification posted"
            _state.value = _state.value.copy(
                lastDetectionStatus = fallbackMsg,
                lastTriggerTimestampMs = nowWallMs
            )
            logQuickCaptureEvent(
                triggerDesc = if (isManualTest) "Test Shake to Camera" else "Moto Quick Capture (2× Twist)",
                actionDesc = fallbackMsg,
                succeeded = false
            )
            false to fallbackMsg
        }
    }

    private fun isAppProcessInForeground(): Boolean {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val proc = am?.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }
            proc?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }.getOrDefault(false)
    }

    private fun postCameraQuickLaunchNotification(cameraIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        runCatching {
            val pendingIntent = PendingIntent.getActivity(
                context,
                9402,
                cameraIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_CRITICAL)
                .setSmallIcon(R.drawable.ic_pandanex_notify)
                .setContentTitle("Quick Capture Detected")
                .setContentText("Tap to open Camera immediately")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setColor(0xFF00E5FF.toInt())
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(9402, notification)
        }
    }

    private fun logQuickCaptureEvent(triggerDesc: String, actionDesc: String, succeeded: Boolean) {
        scope.launch {
            runCatching {
                dao.insertAutomationEvent(
                    AutomationEventEntity(
                        ruleName = "Shake to Camera",
                        triggerDescription = triggerDesc,
                        actionDescription = actionDesc,
                        succeeded = succeeded
                    )
                )
                if (!succeeded) {
                    alertEngine.recordSmartActionAlert(
                        title = "Shake to Camera Notice",
                        message = actionDesc,
                        statusDetail = triggerDesc,
                        severity = "WARNING"
                    )
                }
            }
        }
    }
}
