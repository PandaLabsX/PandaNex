package com.example.automation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

enum class GestureSensitivity(
    val code: String,
    val displayLabel: String,
    val raisePitchDeltaDeg: Float,
    val raiseMinTiltAxisMps2: Float,
    val raiseGyroBoostRadSec: Float,
    val pickupAngleDeltaDeg: Float,
    val pickupLinearImpulseMps2: Float,
    val putDownFlatMaxXYMps2: Float,
    val putDownStillAccelVarMps2: Float,
    val putDownStillGyroRadSec: Float,
    val screenSaverAccelDeltaMps2: Float,
    val screenSaverGyroRadSec: Float
) {
    LOW(
        code = "LOW",
        displayLabel = "Low",
        raisePitchDeltaDeg = 32f,
        raiseMinTiltAxisMps2 = 5.6f,
        raiseGyroBoostRadSec = 0.85f,
        pickupAngleDeltaDeg = 27f,
        pickupLinearImpulseMps2 = 2.4f,
        putDownFlatMaxXYMps2 = 1.25f,
        putDownStillAccelVarMps2 = 0.24f,
        putDownStillGyroRadSec = 0.08f,
        screenSaverAccelDeltaMps2 = 2.9f,
        screenSaverGyroRadSec = 1.05f
    ),
    MEDIUM(
        code = "MEDIUM",
        displayLabel = "Medium",
        raisePitchDeltaDeg = 24f,
        raiseMinTiltAxisMps2 = 4.6f,
        raiseGyroBoostRadSec = 0.60f,
        pickupAngleDeltaDeg = 20f,
        pickupLinearImpulseMps2 = 1.8f,
        putDownFlatMaxXYMps2 = 1.65f,
        putDownStillAccelVarMps2 = 0.35f,
        putDownStillGyroRadSec = 0.12f,
        screenSaverAccelDeltaMps2 = 2.0f,
        screenSaverGyroRadSec = 0.72f
    ),
    HIGH(
        code = "HIGH",
        displayLabel = "High",
        raisePitchDeltaDeg = 18f,
        raiseMinTiltAxisMps2 = 3.8f,
        raiseGyroBoostRadSec = 0.42f,
        pickupAngleDeltaDeg = 15f,
        pickupLinearImpulseMps2 = 1.3f,
        putDownFlatMaxXYMps2 = 2.05f,
        putDownStillAccelVarMps2 = 0.48f,
        putDownStillGyroRadSec = 0.18f,
        screenSaverAccelDeltaMps2 = 1.35f,
        screenSaverGyroRadSec = 0.48f
    );

    companion object {
        fun fromCode(code: String): GestureSensitivity {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MEDIUM
        }
    }
}

/**
 * Pure recognizer for Raise to Wake:
 * Detects transition from a resting surface state (flat or stable angle) into an angled
 * viewing posture while the display is off and unobstructed.
 */
class RaiseToWakeRecognizer(private val hasGyroscope: Boolean) {
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) reset()
        }
    var sensitivity: GestureSensitivity = GestureSensitivity.MEDIUM
    var cooldownMs: Long = 3500L
    var isDisplayInteractive: Boolean = true
    var proximityNear: Boolean = false
    var suppressedUntilMs: Long = 0L

    private var restingVerified = false
    private var restingSinceMs: Long = 0L
    private var restingTiltDeg: Float = 0f
    private var recentGyroPeakRadSec: Float = 0f
    private var recentGyroPeakMs: Long = 0L
    private var recentLiftImpulse: Boolean = false
    private var recentLiftImpulseMs: Long = 0L
    private var confirmRaiseStartMs: Long = 0L
    var lastTriggerMs: Long = 0L
        private set

    fun reset() {
        restingVerified = false
        restingSinceMs = 0L
        confirmRaiseStartMs = 0L
        recentGyroPeakRadSec = 0f
        recentLiftImpulse = false
    }

    fun onGyroscope(nowMs: Long, gx: Float, gy: Float, gz: Float) {
        val rotMag = sqrt(gx * gx + gy * gy + gz * gz)
        if (rotMag >= recentGyroPeakRadSec || nowMs - recentGyroPeakMs > 700L) {
            recentGyroPeakRadSec = rotMag
            recentGyroPeakMs = nowMs
        }
    }

    fun onAccelerometer(nowMs: Long, ax: Float, ay: Float, az: Float): Boolean {
        if (!enabled || isDisplayInteractive || proximityNear || nowMs < suppressedUntilMs) {
            confirmRaiseStartMs = 0L
            return false
        }
        if (lastTriggerMs > 0L && nowMs - lastTriggerMs < cooldownMs) {
            return false
        }

        val mag = sqrt(ax * ax + ay * ay + az * zSafe(az))
        val linearExcess = abs(mag - SensorManager.GRAVITY_EARTH)
        val horizontalTiltMag = sqrt(ax * ax + ay * ay)
        val tiltFromFlatDeg = Math.toDegrees(atan2(horizontalTiltMag.toDouble(), abs(az).toDouble())).toFloat()

        // 1. Detect stable resting baseline (e.g., lying on desk/nightstand)
        val gyroQuiet = !hasGyroscope || (nowMs - recentGyroPeakMs > 300L) || recentGyroPeakRadSec < 0.35f
        if (linearExcess < 0.85f && tiltFromFlatDeg < 26f && gyroQuiet) {
            if (restingSinceMs == 0L) {
                restingSinceMs = nowMs
            } else if (nowMs - restingSinceMs >= 260L) {
                restingVerified = true
                restingTiltDeg = tiltFromFlatDeg
            }
            confirmRaiseStartMs = 0L
            return false
        } else if (linearExcess >= 0.95f && restingVerified) {
            recentLiftImpulse = true
            recentLiftImpulseMs = nowMs
        }

        if (!restingVerified) return false

        // Expire stale impulse after 2.2 seconds
        if (recentLiftImpulse && nowMs - recentLiftImpulseMs > 2200L) {
            recentLiftImpulse = false
        }

        val gyroCorroborated = !hasGyroscope ||
            (nowMs - recentGyroPeakMs <= 1200L && recentGyroPeakRadSec >= sensitivity.raiseGyroBoostRadSec)
        val pitchGain = tiltFromFlatDeg - restingTiltDeg
        val viewingAngleReached = az > -2.5f &&
            pitchGain >= sensitivity.raisePitchDeltaDeg &&
            (ay >= sensitivity.raiseMinTiltAxisMps2 || abs(ax) >= sensitivity.raiseMinTiltAxisMps2 * 1.08f)

        if (viewingAngleReached && (gyroCorroborated || recentLiftImpulse)) {
            if (confirmRaiseStartMs == 0L) {
                confirmRaiseStartMs = nowMs
            } else if (nowMs - confirmRaiseStartMs >= 110L) {
                lastTriggerMs = nowMs
                reset()
                return true
            }
        } else {
            confirmRaiseStartMs = 0L
        }
        return false
    }

    private fun zSafe(az: Float): Float = az
}

/**
 * Pure recognizer for Pick Up to Silence Calls:
 * Active ONLY while an incoming call is ringing. Measures true 3D gravity orientation change
 * relative to the stationary ringing baseline so phone call vibration on a desk never self-triggers.
 */
class PickUpToSilenceRecognizer {
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) reset()
        }
    var sensitivity: GestureSensitivity = GestureSensitivity.MEDIUM
    var isCallRinging: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                reset()
            }
        }

    private var baselineCaptured = false
    private var baseX = 0f
    private var baseY = 0f
    private var baseZ = 0f
    private var baselineCandidateStartMs = 0L
    private var liftConfirmStartMs = 0L
    private var peakImpulseSeen = 0f
    var triggeredForCurrentCall = false
        private set

    fun reset() {
        baselineCaptured = false
        baselineCandidateStartMs = 0L
        liftConfirmStartMs = 0L
        peakImpulseSeen = 0f
        triggeredForCurrentCall = false
    }

    fun onAccelerometer(nowMs: Long, ax: Float, ay: Float, az: Float): Boolean {
        if (!enabled || !isCallRinging || triggeredForCurrentCall) return false

        val mag = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(0.1f)
        val linearExcess = abs(mag - SensorManager.GRAVITY_EARTH)

        if (!baselineCaptured) {
            // Capture initial resting gravity orientation (tolerates call vibration micro-buzz < 3.2 m/s^2)
            if (linearExcess < 3.2f) {
                if (baselineCandidateStartMs == 0L) {
                    baselineCandidateStartMs = nowMs
                    baseX = ax
                    baseY = ay
                    baseZ = az
                } else {
                    // Exponential low-pass filter to average out symmetric vibrator buzz
                    baseX = baseX * 0.7f + ax * 0.3f
                    baseY = baseY * 0.7f + ay * 0.3f
                    baseZ = baseZ * 0.7f + az * 0.3f
                    if (nowMs - baselineCandidateStartMs >= 160L) {
                        baselineCaptured = true
                    }
                }
            }
            return false
        }

        if (linearExcess > peakImpulseSeen) {
            peakImpulseSeen = linearExcess
        }

        val baseMag = sqrt(baseX * baseX + baseY * baseY + baseZ * baseZ).coerceAtLeast(0.1f)
        val cosAngle = ((ax * baseX + ay * baseY + az * baseZ) / (mag * baseMag)).coerceIn(-1f, 1f)
        val angleDeltaDeg = Math.toDegrees(acos(cosAngle.toDouble())).toFloat()

        // Require deliberate orientation tilt change + lift motion (call vibrator buzz has < 5 deg tilt change)
        val deliberatePickup = angleDeltaDeg >= sensitivity.pickupAngleDeltaDeg &&
            (peakImpulseSeen >= sensitivity.pickupLinearImpulseMps2 || ay >= 4.8f)

        if (deliberatePickup) {
            if (liftConfirmStartMs == 0L) {
                liftConfirmStartMs = nowMs
            } else if (nowMs - liftConfirmStartMs >= 140L) {
                triggeredForCurrentCall = true
                return true
            }
        } else {
            liftConfirmStartMs = 0L
        }
        return false
    }
}

enum class FlatSurfacePlacement {
    NONE,
    SCREEN_UP,
    SCREEN_DOWN
}

/**
 * Pure recognizer for Put Down to Sleep:
 * Requires genuine handheld movement/posture BEFORE recognizing a transition onto a stable,
 * flat horizontal surface (screen-up or screen-down), preventing pocket triggers and repeated locks.
 */
class PutDownToSleepRecognizer(private val hasGyroscope: Boolean) {
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) reset()
        }
    var sensitivity: GestureSensitivity = GestureSensitivity.MEDIUM
    var stabilityDelayMs: Long = 1500L
    var cooldownMs: Long = 5000L
    var surfaceMode: String = "BOTH" // BOTH, FACE_DOWN, FACE_UP
    var isDisplayInteractive: Boolean = true
        set(value) {
            field = value
            if (!value) {
                // Once screen turns off, require fresh handheld use after next wake
                wasHandheldActive = false
                flatEnteredMs = 0L
            }
        }
    var proximityNear: Boolean = false
    var isSuppressedExternal: Boolean = false // e.g., active phone call or full-screen video

    var wasHandheldActive: Boolean = false
        private set
    private var handheldSamplesCount: Int = 0
    private var flatEnteredMs: Long = 0L
    private var currentPlacement: FlatSurfacePlacement = FlatSurfacePlacement.NONE
    private var latestGyroMagRadSec: Float = 0f
    var lastTriggerMs: Long = 0L
        private set

    fun reset() {
        wasHandheldActive = false
        handheldSamplesCount = 0
        flatEnteredMs = 0L
        currentPlacement = FlatSurfacePlacement.NONE
        latestGyroMagRadSec = 0f
    }

    fun onGyroscope(gx: Float, gy: Float, gz: Float) {
        latestGyroMagRadSec = sqrt(gx * gx + gy * gy + gz * gz)
    }

    fun onAccelerometer(nowMs: Long, ax: Float, ay: Float, az: Float): FlatSurfacePlacement {
        if (!enabled || !isDisplayInteractive || isSuppressedExternal) {
            flatEnteredMs = 0L
            return FlatSurfacePlacement.NONE
        }
        if (lastTriggerMs > 0L && nowMs - lastTriggerMs < cooldownMs) {
            flatEnteredMs = 0L
            return FlatSurfacePlacement.NONE
        }

        val mag = sqrt(ax * ax + ay * ay + az * az)
        val accelExcess = abs(mag - SensorManager.GRAVITY_EARTH)

        // 1. Detect handheld posture / active movement (tilted in hand or moving)
        val isTiltedInHand = abs(az) < 8.1f && (abs(ay) > 2.8f || abs(ax) > 2.8f)
        val isMovingInHand = accelExcess > 1.25f || (hasGyroscope && latestGyroMagRadSec > 0.45f)
        if (isTiltedInHand || isMovingInHand) {
            handheldSamplesCount++
            if (handheldSamplesCount >= 2) {
                wasHandheldActive = true
            }
            flatEnteredMs = 0L
            currentPlacement = FlatSurfacePlacement.NONE
            return FlatSurfacePlacement.NONE
        }

        // Never trigger if phone was already resting flat without prior handheld activity
        if (!wasHandheldActive) return FlatSurfacePlacement.NONE

        // 2. Check flat horizontal placement (Screen-Up or Screen-Down)
        val flatXY = abs(ax) <= sensitivity.putDownFlatMaxXYMps2 &&
            abs(ay) <= sensitivity.putDownFlatMaxXYMps2
        val rawPlacement = when {
            flatXY && az >= 9.0f -> FlatSurfacePlacement.SCREEN_UP
            flatXY && az <= -9.0f -> FlatSurfacePlacement.SCREEN_DOWN
            else -> FlatSurfacePlacement.NONE
        }

        val modeAllowed = when (surfaceMode) {
            "FACE_DOWN" -> rawPlacement == FlatSurfacePlacement.SCREEN_DOWN
            "FACE_UP" -> rawPlacement == FlatSurfacePlacement.SCREEN_UP
            else -> rawPlacement != FlatSurfacePlacement.NONE
        }

        // Pocket / Bag Guard: if screen-up yet proximity is covered (NEAR), phone is inside a pocket/bag!
        val pocketBlocked = rawPlacement == FlatSurfacePlacement.SCREEN_UP && proximityNear

        val isStationaryStill = accelExcess <= sensitivity.putDownStillAccelVarMps2 &&
            (!hasGyroscope || latestGyroMagRadSec <= sensitivity.putDownStillGyroRadSec)

        if (!modeAllowed || pocketBlocked || !isStationaryStill) {
            flatEnteredMs = 0L
            currentPlacement = FlatSurfacePlacement.NONE
            return FlatSurfacePlacement.NONE
        }

        if (currentPlacement != rawPlacement || flatEnteredMs == 0L) {
            currentPlacement = rawPlacement
            flatEnteredMs = nowMs
            return FlatSurfacePlacement.NONE
        }

        if (nowMs - flatEnteredMs >= stabilityDelayMs) {
            val triggeredPlacement = currentPlacement
            lastTriggerMs = nowMs
            wasHandheldActive = false
            handheldSamplesCount = 0
            flatEnteredMs = 0L
            currentPlacement = FlatSurfacePlacement.NONE
            return triggeredPlacement
        }

        return FlatSurfacePlacement.NONE
    }
}

/**
 * Pure recognizer for Motion-Activated Smart Screen Saver when display is OFF:
 * Requires brief stillness followed by meaningful movement/pickup, filtering out tiny vibrations.
 */
class ScreenSaverMotionRecognizer(private val hasGyroscope: Boolean) {
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) reset()
        }
    var sensitivity: GestureSensitivity = GestureSensitivity.MEDIUM
    var cooldownMs: Long = 4500L
    var isDisplayInteractive: Boolean = true
    var proximityNear: Boolean = false
    var suppressedUntilMs: Long = 0L

    private var stationaryPrimed = false
    private var stationarySinceMs = 0L
    private var motionPulseCount = 0
    private var lastMotionPulseMs = 0L
    private var latestGyroRadSec = 0f
    var lastTriggerMs: Long = 0L
        private set

    fun reset() {
        stationaryPrimed = false
        stationarySinceMs = 0L
        motionPulseCount = 0
        latestGyroRadSec = 0f
    }

    fun onGyroscope(gx: Float, gy: Float, gz: Float) {
        latestGyroRadSec = sqrt(gx * gx + gy * gy + gz * gz)
    }

    fun onAccelerometer(nowMs: Long, ax: Float, ay: Float, az: Float): Boolean {
        if (!enabled || isDisplayInteractive || proximityNear || nowMs < suppressedUntilMs) {
            motionPulseCount = 0
            return false
        }
        if (lastTriggerMs > 0L && nowMs - lastTriggerMs < cooldownMs) {
            return false
        }

        val mag = sqrt(ax * ax + ay * ay + az * az)
        val linearExcess = abs(mag - SensorManager.GRAVITY_EARTH)

        if (!stationaryPrimed) {
            if (linearExcess < 0.9f) {
                if (stationarySinceMs == 0L) stationarySinceMs = nowMs
                if (nowMs - stationarySinceMs >= 220L) {
                    stationaryPrimed = true
                }
            } else {
                stationarySinceMs = 0L
            }
            return false
        }

        val meaningfulMotion = linearExcess >= sensitivity.screenSaverAccelDeltaMps2 ||
            (hasGyroscope && latestGyroRadSec >= sensitivity.screenSaverGyroRadSec)

        if (meaningfulMotion) {
            if (nowMs - lastMotionPulseMs > 900L) {
                motionPulseCount = 1
            } else {
                motionPulseCount++
            }
            lastMotionPulseMs = nowMs

            if (motionPulseCount >= 2) {
                lastTriggerMs = nowMs
                reset()
                return true
            }
        } else if (nowMs - lastMotionPulseMs > 900L) {
            motionPulseCount = 0
        }
        return false
    }
}

/**
 * Hardware SensorEventListener managing Accelerometer, Gyroscope, and Significant Motion
 * with battery-aware dynamic registration (zero sensor polling when no gesture requires it).
 */
class MotionDetectionManager(
    context: Context,
    private val onRaiseToWakeTriggered: () -> Unit,
    private val onPickUpToSilenceTriggered: () -> Unit,
    private val onPutDownToSleepTriggered: (FlatSurfacePlacement) -> Unit,
    private val onScreenSaverMotionTriggered: () -> Unit,
    private val onOrientationSampled: (Float, Float, Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val standardAccel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val wakeUpAccel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val significantMotionSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    val hasAccelerometer: Boolean = standardAccel != null || wakeUpAccel != null
    val hasGyroscope: Boolean = gyroscope != null
    val hasSignificantMotion: Boolean = significantMotionSensor != null

    val raiseToWakeRecognizer = RaiseToWakeRecognizer(hasGyroscope = hasGyroscope)
    val pickUpToSilenceRecognizer = PickUpToSilenceRecognizer()
    val putDownToSleepRecognizer = PutDownToSleepRecognizer(hasGyroscope = hasGyroscope)
    val screenSaverRecognizer = ScreenSaverMotionRecognizer(hasGyroscope = hasGyroscope)

    private var registered = false
    private var usingWakeUpAccel = false
    private var sigMotionRequested = false
    private var externalProximityConsumerNeeded = false

    private val significantMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            sigMotionRequested = false
            // Significant hardware motion occurred while display was off
            if (!raiseToWakeRecognizer.isDisplayInteractive) {
                if (screenSaverRecognizer.enabled && !screenSaverRecognizer.proximityNear) {
                    onScreenSaverMotionTriggered()
                } else if (raiseToWakeRecognizer.enabled && !raiseToWakeRecognizer.proximityNear) {
                    onRaiseToWakeTriggered()
                }
            }
            requestSignificantMotionIfAppropriate()
        }
    }

    fun setExternalEarPostureSamplingNeeded(needed: Boolean) {
        if (externalProximityConsumerNeeded != needed) {
            externalProximityConsumerNeeded = needed
            syncRegistration()
        }
    }

    fun updateProximityState(isNear: Boolean) {
        raiseToWakeRecognizer.proximityNear = isNear
        putDownToSleepRecognizer.proximityNear = isNear
        screenSaverRecognizer.proximityNear = isNear
    }

    fun updateDisplayInteractive(interactive: Boolean, nowMs: Long = System.currentTimeMillis()) {
        raiseToWakeRecognizer.isDisplayInteractive = interactive
        putDownToSleepRecognizer.isDisplayInteractive = interactive
        screenSaverRecognizer.isDisplayInteractive = interactive
        if (!interactive) {
            // Brief 1.5s guard after screen turns off so power-button press motion doesn't re-wake
            val guardUntil = maxOf(raiseToWakeRecognizer.suppressedUntilMs, nowMs + 1500L)
            raiseToWakeRecognizer.suppressedUntilMs = guardUntil
            screenSaverRecognizer.suppressedUntilMs = guardUntil
        }
        syncRegistration()
    }

    fun applyPostSleepGuard(nowMs: Long = System.currentTimeMillis(), guardDurationMs: Long = 4000L) {
        val until = nowMs + guardDurationMs
        raiseToWakeRecognizer.suppressedUntilMs = until
        screenSaverRecognizer.suppressedUntilMs = until
    }

    @Synchronized
    fun syncRegistration(allowListening: Boolean = true) {
        val screenInteractive = raiseToWakeRecognizer.isDisplayInteractive
        val needCallMotion = pickUpToSilenceRecognizer.enabled && pickUpToSilenceRecognizer.isCallRinging
        val needScreenOnMotion = screenInteractive && putDownToSleepRecognizer.enabled
        val needScreenOffMotion = !screenInteractive &&
            (raiseToWakeRecognizer.enabled || screenSaverRecognizer.enabled)
        val shouldListen = allowListening && hasAccelerometer &&
            (needCallMotion || needScreenOnMotion || needScreenOffMotion || externalProximityConsumerNeeded)

        val preferWakeAccel = needScreenOffMotion && wakeUpAccel != null

        if (shouldListen && (!registered || preferWakeUpMismatch(preferWakeAccel))) {
            if (registered) {
                sensorManager.unregisterListener(this)
                registered = false
            }
            val accelToUse = if (preferWakeAccel) wakeUpAccel else (standardAccel ?: wakeUpAccel)
            val samplingDelay = if (needCallMotion) {
                SensorManager.SENSOR_DELAY_UI
            } else {
                SensorManager.SENSOR_DELAY_NORMAL
            }
            accelToUse?.let {
                sensorManager.registerListener(this, it, samplingDelay)
            }
            gyroscope?.let {
                sensorManager.registerListener(this, it, samplingDelay)
            }
            registered = true
            usingWakeUpAccel = preferWakeAccel
        } else if (!shouldListen && registered) {
            sensorManager.unregisterListener(this)
            registered = false
            usingWakeUpAccel = false
        }

        if (allowListening && needScreenOffMotion) {
            requestSignificantMotionIfAppropriate()
        } else {
            cancelSignificantMotionIfNeeded()
        }
    }

    private fun preferWakeUpMismatch(preferWake: Boolean): Boolean {
        return wakeUpAccel != null && usingWakeUpAccel != preferWake
    }

    private fun requestSignificantMotionIfAppropriate() {
        val sensor = significantMotionSensor ?: return
        if (sigMotionRequested) return
        runCatching {
            sigMotionRequested = sensorManager.requestTriggerSensor(significantMotionListener, sensor)
        }
    }

    private fun cancelSignificantMotionIfNeeded() {
        val sensor = significantMotionSensor ?: return
        if (!sigMotionRequested) return
        runCatching {
            sensorManager.cancelTriggerSensor(significantMotionListener, sensor)
        }
        sigMotionRequested = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val nowMs = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                raiseToWakeRecognizer.onGyroscope(nowMs, gx, gy, gz)
                putDownToSleepRecognizer.onGyroscope(gx, gy, gz)
                screenSaverRecognizer.onGyroscope(gx, gy, gz)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                onOrientationSampled(ax, ay, az)

                // 1. Incoming Call Pick Up to Silence has top priority during ringing
                if (pickUpToSilenceRecognizer.onAccelerometer(nowMs, ax, ay, az)) {
                    onPickUpToSilenceTriggered()
                    return
                }

                // 2. Screen-OFF gestures: Smart Screen Saver vs Raise to Wake conflict-free arbitration
                if (!raiseToWakeRecognizer.isDisplayInteractive) {
                    if (screenSaverRecognizer.enabled) {
                        if (screenSaverRecognizer.onAccelerometer(nowMs, ax, ay, az)) {
                            onScreenSaverMotionTriggered()
                        }
                    } else if (raiseToWakeRecognizer.enabled) {
                        if (raiseToWakeRecognizer.onAccelerometer(nowMs, ax, ay, az)) {
                            onRaiseToWakeTriggered()
                        }
                    }
                    return
                }

                // 3. Screen-ON Put Down to Sleep
                val placement = putDownToSleepRecognizer.onAccelerometer(nowMs, ax, ay, az)
                if (placement != FlatSurfacePlacement.NONE) {
                    onPutDownToSleepTriggered(placement)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
