package com.example.automation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure, deterministic evaluator for Bring Phone Near Ear to Answer Calls.
 *
 * Prevents accidental answering by requiring ALL of:
 * 1. An active incoming ringing call (`isCallRinging == true`).
 * 2. No conflicting Bluetooth headset, wired headset, or Android Auto audio route.
 * 3. Pocket-at-start guard: if proximity was ALREADY near when ringing began, user must
 *    remove the phone from pocket (`isNear == false`) before near-ear detection arms.
 * 4. Genuine lifting/handheld motion during the incoming call AND upright ear-level posture
 *    (`abs(az) < 7.2` and `abs(ay) >= 4.0 || abs(ax) >= 4.0`) — covering a phone lying flat
 *    on a table (`az ≈ 9.8`) is strictly rejected.
 * 5. Continuous hold near the ear throughout `confirmationDelayMs` (400ms–2500ms).
 */
class NearEarAnswerEvaluator {
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) resetCallSession()
        }
    var confirmationDelayMs: Long = 800L
    var isCallRinging: Boolean = false
        private set
    var externalAudioRouteActive: Boolean = false

    private var proximityNear: Boolean = false
    private var mustClearPocketFirst: Boolean = false
    private var hadLiftMotionDuringCall: Boolean = false
    private var isUprightEarPosture: Boolean = false
    private var initialAccelMag: Float? = null
    private var nearEarEnteredAtMs: Long = 0L
    var answeredForCurrentCall: Boolean = false
        private set

    fun onCallRingingChanged(ringing: Boolean, currentProximityNear: Boolean) {
        if (isCallRinging == ringing) return
        isCallRinging = ringing
        if (ringing) {
            proximityNear = currentProximityNear
            // If already covered/in-pocket when ringing begins, wait for removal first
            mustClearPocketFirst = currentProximityNear
            hadLiftMotionDuringCall = false
            isUprightEarPosture = false
            initialAccelMag = null
            nearEarEnteredAtMs = 0L
            answeredForCurrentCall = false
        } else {
            resetCallSession()
        }
    }

    fun resetCallSession() {
        mustClearPocketFirst = false
        hadLiftMotionDuringCall = false
        isUprightEarPosture = false
        initialAccelMag = null
        nearEarEnteredAtMs = 0L
        answeredForCurrentCall = false
    }

    fun onAccelerometerSample(nowMs: Long, ax: Float, ay: Float, az: Float) {
        if (!isCallRinging) return
        val mag = sqrt(ax * ax + ay * ay + az * az)
        if (initialAccelMag == null) {
            initialAccelMag = mag
        }
        val linearDelta = abs(mag - SensorManager.GRAVITY_EARTH)

        // Ear posture: phone held upright/angled against ear (NOT lying flat face-up or face-down)
        val uprightNow = abs(az) < 7.2f && (abs(ay) >= 4.0f || abs(ax) >= 4.0f)
        if (linearDelta >= 1.15f || uprightNow) {
            hadLiftMotionDuringCall = true
        }
        isUprightEarPosture = uprightNow

        if (!uprightNow) {
            nearEarEnteredAtMs = 0L
        } else if (nearEarEnteredAtMs == 0L && isReadyToHoldNearEar()) {
            nearEarEnteredAtMs = nowMs
        }
    }

    fun onProximityChanged(nowMs: Long, isNear: Boolean): Boolean {
        proximityNear = isNear
        if (!isNear) {
            mustClearPocketFirst = false
            nearEarEnteredAtMs = 0L
            return false
        }
        if (isReadyToHoldNearEar()) {
            if (nearEarEnteredAtMs == 0L) {
                nearEarEnteredAtMs = nowMs
            }
            return checkConfirmationElapsed(nowMs)
        }
        return false
    }

    fun checkConfirmationElapsed(nowMs: Long): Boolean {
        if (!isReadyToHoldNearEar() || nearEarEnteredAtMs == 0L) return false
        if (nowMs - nearEarEnteredAtMs >= confirmationDelayMs) {
            answeredForCurrentCall = true
            nearEarEnteredAtMs = 0L
            return true
        }
        return false
    }

    fun isCurrentlyCountingDown(): Boolean {
        return isReadyToHoldNearEar() && nearEarEnteredAtMs > 0L && !answeredForCurrentCall
    }

    private fun isReadyToHoldNearEar(): Boolean {
        return enabled &&
            isCallRinging &&
            !externalAudioRouteActive &&
            !answeredForCurrentCall &&
            !mustClearPocketFirst &&
            proximityNear &&
            hadLiftMotionDuringCall &&
            isUprightEarPosture
    }
}

/**
 * Lifecycle-aware Proximity Sensor Manager that registers Sensor.TYPE_PROXIMITY only when
 * required by Proximity Call Answer or active Pocket-Guard motion gestures.
 */
class ProximityDetectionManager(
    context: Context,
    private val onProximityStateChanged: (Boolean) -> Unit,
    private val onNearEarAnswerConfirmed: () -> Unit
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    val hasProximitySensor: Boolean = proximitySensor != null
    val evaluator = NearEarAnswerEvaluator()

    @Volatile
    var isNear: Boolean = false
        private set

    private var registered = false
    private var confirmationTimerJob: Job? = null

    @Synchronized
    fun syncRegistration(shouldListen: Boolean) {
        if (!hasProximitySensor) return
        if (shouldListen && !registered) {
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            registered = true
        } else if (!shouldListen && registered) {
            confirmationTimerJob?.cancel()
            confirmationTimerJob = null
            sensorManager.unregisterListener(this)
            registered = false
            isNear = false
        }
    }

    fun onAccelerometerForPosture(ax: Float, ay: Float, az: Float) {
        val now = System.currentTimeMillis()
        evaluator.onAccelerometerSample(now, ax, ay, az)
        scheduleConfirmationCheckIfNeeded()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
        val nearNow = event.values[0] < maxRange && event.values[0] < 5f
        val now = System.currentTimeMillis()

        if (nearNow != isNear) {
            isNear = nearNow
            onProximityStateChanged(nearNow)
        }

        val immediate = evaluator.onProximityChanged(now, nearNow)
        if (immediate) {
            confirmationTimerJob?.cancel()
            onNearEarAnswerConfirmed()
        } else {
            scheduleConfirmationCheckIfNeeded()
        }
    }

    private fun scheduleConfirmationCheckIfNeeded() {
        if (!evaluator.isCurrentlyCountingDown()) {
            confirmationTimerJob?.cancel()
            confirmationTimerJob = null
            return
        }
        if (confirmationTimerJob?.isActive == true) return
        val delayMs = evaluator.confirmationDelayMs.coerceIn(300L, 3000L)
        confirmationTimerJob = scope.launch {
            delay(delayMs + 30L)
            if (evaluator.checkConfirmationElapsed(System.currentTimeMillis())) {
                onNearEarAnswerConfirmed()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
