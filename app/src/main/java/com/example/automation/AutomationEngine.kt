package com.example.automation

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import com.example.core.common.SystemUtils
import com.example.data.local.database.AutomationEventEntity
import com.example.data.local.database.AutomationRuleEntity
import com.example.data.local.database.PandaDao
import com.example.data.local.datastore.PandaPreferencesRepository
import com.example.data.local.datastore.PandaSettings
import com.example.monitoring.BatteryMonitor
import com.example.monitoring.NetworkMonitor
import com.example.notifications.AlertEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

enum class DeviceOrientationState(val label: String) {
    FACE_UP("Face Up"),
    FACE_DOWN("Face Down"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    TILTED("In Motion / Tilted")
}

data class LiveGestureTelemetry(
    val orientation: DeviceOrientationState = DeviceOrientationState.FACE_UP,
    val proximityNear: Boolean = false,
    val proximitySupported: Boolean = false,
    val accelerometerSupported: Boolean = false,
    val flashlightOn: Boolean = false,
    val flashlightSupported: Boolean = false,
    val dndPolicyGranted: Boolean = false,
    val dndActive: Boolean = false,
    val ringerModeLabel: String = "Normal",
    val activeFlipApplied: Boolean = false,
    val lastGestureLabel: String = "Monitoring ready"
)

class ActionManager(
    private val context: Context,
    private val alertEngine: AlertEngine
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var torchCameraId: String? = null
    @Volatile var isTorchEnabled: Boolean = false
        private set

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                isTorchEnabled = enabled
            }
        }
    }

    init {
        runCatching {
            torchCameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (torchCameraId != null) {
                cameraManager?.registerTorchCallback(torchCallback, null)
            }
        }
    }

    fun hasFlashlight(): Boolean = torchCameraId != null

    fun isDndPolicyGranted(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    fun isDndActive(): Boolean {
        val filter = notificationManager.currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun getCurrentInterruptionFilter(): Int = notificationManager.currentInterruptionFilter

    fun getRingerMode(): Int = audioManager.ringerMode

    fun getRingerModeLabel(): String = when (audioManager.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "Silent"
        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
        else -> "Normal (Sound)"
    }

    fun openDndPolicySettings() {
        SystemUtils.openSystemSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    fun toggleFlashlight(targetState: Boolean? = null): Boolean {
        val id = torchCameraId ?: return false
        val desired = targetState ?: !isTorchEnabled
        return try {
            cameraManager?.setTorchMode(id, desired)
            isTorchEnabled = desired
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setDndEnabled(enable: Boolean): Boolean {
        if (!isDndPolicyGranted()) return false
        return try {
            notificationManager.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restoreInterruptionFilter(previousFilter: Int): Boolean {
        if (!isDndPolicyGranted()) return false
        return try {
            notificationManager.setInterruptionFilter(previousFilter)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setRingerModeSafe(targetMode: Int): Boolean {
        return try {
            if (targetMode == AudioManager.RINGER_MODE_SILENT && !isDndPolicyGranted()) {
                return false
            }
            audioManager.ringerMode = targetMode
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return false
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun executeRuleAction(actionType: String, actionParam: String, ruleName: String): Pair<Boolean, String> {
        return when (actionType) {
            "ENABLE_DND" -> {
                val ok = setDndEnabled(true)
                ok to if (ok) "Do Not Disturb enabled" else "Failed: DND Policy permission required"
            }
            "DISABLE_DND" -> {
                val ok = setDndEnabled(false)
                ok to if (ok) "Do Not Disturb disabled" else "Failed: DND Policy permission required"
            }
            "RINGER_SILENT" -> {
                val ok = setRingerModeSafe(AudioManager.RINGER_MODE_SILENT)
                ok to if (ok) "Ringer set to Silent" else "Failed: DND Policy permission required"
            }
            "RINGER_VIBRATE" -> {
                val ok = setRingerModeSafe(AudioManager.RINGER_MODE_VIBRATE)
                ok to if (ok) "Ringer set to Vibrate" else "Unable to switch ringer mode"
            }
            "RINGER_NORMAL" -> {
                val ok = setRingerModeSafe(AudioManager.RINGER_MODE_NORMAL)
                ok to if (ok) "Ringer set to Normal" else "Unable to switch ringer mode"
            }
            "TOGGLE_FLASHLIGHT" -> {
                val ok = toggleFlashlight()
                ok to if (ok) "Flashlight toggled (${if (isTorchEnabled) "ON" else "OFF"})" else "Flashlight hardware unavailable"
            }
            "SHOW_NOTIFICATION" -> {
                val msg = actionParam.ifBlank { "Automation '$ruleName' triggered." }
                alertEngine.recordSmartActionAlert(
                    title = "Automation: $ruleName",
                    message = msg,
                    statusDetail = "Rule Triggered"
                )
                true to "Notification posted"
            }
            "SHOW_REMINDER" -> {
                val msg = actionParam.ifBlank { "Smart Reminder triggered by $ruleName" }
                alertEngine.recordSmartActionAlert(
                    title = "Smart Reminder",
                    message = msg,
                    statusDetail = ruleName
                )
                true to "Reminder displayed"
            }
            "OPEN_APP" -> {
                val ok = launchPackage(actionParam.trim())
                ok to if (ok) "Opened $actionParam" else "Cannot launch $actionParam (restricted or not installed)"
            }
            else -> false to "Unsupported action"
        }
    }
}

/**
 * PandaNex Automation & Gesture Engine
 * Processes real hardware Accelerometer + Proximity events for exclusive Flip to DND
 * (2x vibration ON, 1x vibration OFF), Shake to Flashlight, Double Shake shortcuts,
 * Pickup Detection, and WHEN -> IF -> THEN Automation Rules.
 */
class AutomationEngine(
    private val context: Context,
    private val dao: PandaDao,
    private val preferencesRepository: PandaPreferencesRepository,
    val actionManager: ActionManager,
    private val batteryMonitor: BatteryMonitor,
    private val networkMonitor: NetworkMonitor,
    private val alertEngine: AlertEngine
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var currentSettings = PandaSettings()
    private var isListening = false

    private val _telemetry = MutableStateFlow(
        LiveGestureTelemetry(
            proximitySupported = proximitySensor != null,
            accelerometerSupported = accelerometer != null,
            flashlightSupported = actionManager.hasFlashlight(),
            dndPolicyGranted = actionManager.isDndPolicyGranted(),
            dndActive = actionManager.isDndActive(),
            ringerModeLabel = actionManager.getRingerModeLabel()
        )
    )
    val telemetry: StateFlow<LiveGestureTelemetry> = _telemetry.asStateFlow()

    // Gesture Detection State
    private var faceDownEnteredAtMs: Long? = null
    private var flipStateApplied = false
    private var savedInterruptionFilter: Int? = null
    private var lastFlipActionMs: Long = 0L

    private var lastShakeTimestampMs: Long = 0L
    private var pendingFirstShakeMs: Long = 0L
    private var wasStationaryFlat: Boolean = false
    private var lastPickupMs: Long = 0L

    init {
        scope.launch {
            seedDefaultRulesIfEmpty()
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                syncSensorListeners()
                refreshSystemStatus()
            }
        }
    }

    private suspend fun seedDefaultRulesIfEmpty() {
        if (dao.getAutomationRuleCount() == 0) {
            dao.upsertAutomationRule(
                AutomationRuleEntity(
                    name = "Desk Focus Flip DND",
                    triggerType = "FACE_DOWN",
                    triggerParam = "",
                    conditionType = "WHEN_NOT_CHARGING",
                    actionType = "ENABLE_DND",
                    actionParam = "",
                    isEnabled = false
                )
            )
            dao.upsertAutomationRule(
                AutomationRuleEntity(
                    name = "Charger Connected Notice",
                    triggerType = "CHARGING_CONNECTED",
                    triggerParam = "",
                    conditionType = "ALWAYS",
                    actionType = "SHOW_REMINDER",
                    actionParam = "Power connected. Check thermal headroom if fast-charging.",
                    isEnabled = true
                )
            )
        }
    }

    fun startForegroundInteractiveMonitoring() {
        registerListenersIfNeeded(forceInteractive = true)
        refreshSystemStatus()
    }

    fun stopForegroundInteractiveMonitoring() {
        if (!anyGestureEnabled(currentSettings) && !currentSettings.backgroundMonitoringEnabled) {
            unregisterListeners()
        }
    }

    fun refreshSystemStatus() {
        _telemetry.value = _telemetry.value.copy(
            flashlightOn = actionManager.isTorchEnabled,
            flashlightSupported = actionManager.hasFlashlight(),
            dndPolicyGranted = actionManager.isDndPolicyGranted(),
            dndActive = actionManager.isDndActive(),
            ringerModeLabel = actionManager.getRingerModeLabel(),
            activeFlipApplied = flipStateApplied
        )
    }

    private fun anyGestureEnabled(s: PandaSettings): Boolean {
        return s.flipToDndEnabled ||
            s.shakeFlashlightEnabled || s.doubleShakeAction != "NONE" ||
            s.pickupDetectionEnabled || s.proximityPocketGuardEnabled
    }

    private fun syncSensorListeners() {
        if (anyGestureEnabled(currentSettings) || currentSettings.backgroundMonitoringEnabled) {
            registerListenersIfNeeded(forceInteractive = false)
        }
    }

    private fun registerListenersIfNeeded(forceInteractive: Boolean) {
        if (isListening && !forceInteractive) return
        if (!isListening) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            isListening = true
        }
    }

    private fun unregisterListeners() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
                val isNear = event.values[0] < maxRange.coerceAtMost(5f)
                val prevNear = _telemetry.value.proximityNear
                if (isNear != prevNear) {
                    _telemetry.value = _telemetry.value.copy(proximityNear = isNear)
                    if (isNear) {
                        scope.launch { evaluateRulesForTrigger("PROXIMITY_NEAR") }
                    }
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                processAccelerometer(x, y, z)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun processAccelerometer(x: Float, y: Float, z: Float) {
        val now = System.currentTimeMillis()
        val magnitude = sqrt(x * x + y * y + z * z)

        // 1. Determine Orientation
        val orientation = when {
            z <= -8.2f && abs(x) < 3.5f && abs(y) < 3.5f -> DeviceOrientationState.FACE_DOWN
            z >= 8.2f && abs(x) < 3.5f && abs(y) < 3.5f -> DeviceOrientationState.FACE_UP
            abs(y) >= 7.2f && abs(x) < 4.5f -> DeviceOrientationState.PORTRAIT
            abs(x) >= 7.2f && abs(y) < 4.5f -> DeviceOrientationState.LANDSCAPE
            else -> DeviceOrientationState.TILTED
        }

        val prevOrientation = _telemetry.value.orientation
        if (orientation != prevOrientation) {
            _telemetry.value = _telemetry.value.copy(orientation = orientation)
            if (orientation == DeviceOrientationState.FACE_DOWN) {
                scope.launch { evaluateRulesForTrigger("FACE_DOWN") }
            } else if (orientation == DeviceOrientationState.FACE_UP) {
                scope.launch { evaluateRulesForTrigger("FACE_UP") }
            }
        }

        // 2. Exclusive Flip to DND (Face Down -> ON with 2x vibrate; Face Up -> OFF with 1x vibrate)
        if (orientation == DeviceOrientationState.FACE_DOWN) {
            if (faceDownEnteredAtMs == null) {
                faceDownEnteredAtMs = now
            } else if (!flipStateApplied &&
                (now - (faceDownEnteredAtMs ?: now)) >= currentSettings.flipDelayMs &&
                (now - lastFlipActionMs) >= currentSettings.gestureCooldownMs
            ) {
                applyFlipDownActions(now)
            }
        } else if (orientation == DeviceOrientationState.FACE_UP || orientation == DeviceOrientationState.PORTRAIT) {
            faceDownEnteredAtMs = null
            if (flipStateApplied && currentSettings.flipRestorePrevious) {
                restoreFlipUpState(now)
            } else {
                flipStateApplied = false
            }
        }

        // 3. Pickup Detection (Stationary flat -> lifted into Portrait/Tilted)
        if (orientation == DeviceOrientationState.FACE_UP && abs(magnitude - 9.81f) < 0.8f) {
            wasStationaryFlat = true
        } else if (wasStationaryFlat &&
            (orientation == DeviceOrientationState.PORTRAIT || y > 5.2f) &&
            (now - lastPickupMs) > 5000L
        ) {
            wasStationaryFlat = false
            lastPickupMs = now
            if (currentSettings.pickupDetectionEnabled) {
                _telemetry.value = _telemetry.value.copy(lastGestureLabel = "Pickup movement detected")
                scope.launch {
                    recordEvent("Pickup Detection", "Phone Lifted", "Pickup Gesture Recognized", true)
                    alertEngine.recordSmartActionAlert(
                        title = "Pickup Detected",
                        message = "PandaNex detected device pickup motion.",
                        statusDetail = "Motion Sensor"
                    )
                    evaluateRulesForTrigger("PICKUP")
                }
            }
        }

        // 4. Shake & Double-Shake Detection
        val netAccel = abs(magnitude - SensorManager.GRAVITY_EARTH)
        if (netAccel >= currentSettings.shakeSensitivity) {
            // Avoid pocket accidental shakes if Pocket Guard is active and proximity is Near
            if (currentSettings.proximityPocketGuardEnabled && _telemetry.value.proximityNear) {
                return
            }

            if (now - pendingFirstShakeMs in 260L..950L && currentSettings.doubleShakeAction != "NONE") {
                pendingFirstShakeMs = 0L
                lastShakeTimestampMs = now
                handleDoubleShake()
            } else if (now - lastShakeTimestampMs >= currentSettings.gestureCooldownMs) {
                pendingFirstShakeMs = now
                lastShakeTimestampMs = now
                handleSingleShake()
            }
        }
    }

    private fun applyFlipDownActions(now: Long) {
        if (!currentSettings.flipToDndEnabled) return

        lastFlipActionMs = now
        flipStateApplied = true
        savedInterruptionFilter = actionManager.getCurrentInterruptionFilter()

        scope.launch {
            val ok = actionManager.setDndEnabled(true)
            val appliedDesc = if (ok) {
                "Flip to DND ON (Vibrated 2×)"
            } else {
                "Flip to DND Failed (Grant Policy Access)"
            }

            if (ok) {
                // Strictly vibrate 2 times when Flip to DND turns ON
                SystemUtils.triggerFlipDndVibration(context, isTurningOn = true)
            }

            _telemetry.value = _telemetry.value.copy(
                lastGestureLabel = appliedDesc,
                activeFlipApplied = ok,
                dndActive = actionManager.isDndActive(),
                ringerModeLabel = actionManager.getRingerModeLabel()
            )

            recordEvent("Flip to DND", "Face Down (${currentSettings.flipDelayMs}ms)", appliedDesc, ok)
            alertEngine.recordSmartActionAlert(
                title = appliedDesc,
                message = if (ok) "Device placed face-down. Do Not Disturb enabled (2× haptic confirmation)."
                else "DND policy permission is required to enable Flip to DND.",
                statusDetail = "DND ON • 2× Vibrate"
            )
        }
    }

    private fun restoreFlipUpState(now: Long) {
        flipStateApplied = false
        lastFlipActionMs = now
        val prevFilter = savedInterruptionFilter
        savedInterruptionFilter = null

        scope.launch {
            var ok = false
            if (currentSettings.flipToDndEnabled) {
                ok = if (prevFilter != null && prevFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    actionManager.restoreInterruptionFilter(prevFilter)
                } else {
                    actionManager.setDndEnabled(false)
                }
            }

            if (ok) {
                // Strictly vibrate 1 time alone when Flip to DND turns OFF
                SystemUtils.triggerFlipDndVibration(context, isTurningOn = false)
            }

            val restoreLabel = if (ok) {
                "Flip to DND OFF (Vibrated 1×)"
            } else {
                "Flip to DND State Restored"
            }

            _telemetry.value = _telemetry.value.copy(
                lastGestureLabel = restoreLabel,
                activeFlipApplied = false,
                dndActive = actionManager.isDndActive(),
                ringerModeLabel = actionManager.getRingerModeLabel()
            )
            recordEvent("Flip to DND", "Phone Flipped Up", restoreLabel, ok)
            alertEngine.recordSmartActionAlert(
                title = restoreLabel,
                message = "Device flipped face-up — Do Not Disturb turned OFF (1× haptic confirmation).",
                statusDetail = "DND OFF • 1× Vibrate"
            )
        }
    }

    private fun handleSingleShake() {
        scope.launch {
            evaluateRulesForTrigger("SHAKE")
            if (!currentSettings.shakeFlashlightEnabled) return@launch
            val ok = actionManager.toggleFlashlight()
            if (ok && currentSettings.hapticFeedbackEnabled) {
                SystemUtils.triggerHapticPulse(context, 55L)
            }
            val stateStr = if (actionManager.isTorchEnabled) "ON" else "OFF"
            _telemetry.value = _telemetry.value.copy(
                flashlightOn = actionManager.isTorchEnabled,
                lastGestureLabel = if (ok) "Shake -> Flashlight $stateStr" else "Flashlight unavailable"
            )
            recordEvent("Shake to Flashlight", "Motion Shake", "Flashlight turned $stateStr", ok)
            alertEngine.recordSmartActionAlert(
                title = "Shake Gesture: Flashlight $stateStr",
                message = "Camera torch toggled $stateStr via shake gesture.",
                statusDetail = "Torch $stateStr"
            )
        }
    }

    private fun handleDoubleShake() {
        scope.launch {
            val action = currentSettings.doubleShakeAction
            val (ok, label) = when (action) {
                "FLASHLIGHT" -> {
                    val res = actionManager.toggleFlashlight()
                    res to "Double Shake: Flashlight Toggled"
                }
                "DND_TOGGLE" -> {
                    val next = !actionManager.isDndActive()
                    val res = actionManager.setDndEnabled(next)
                    res to if (res) "Double Shake: DND ${if (next) "Enabled" else "Disabled"}"
                    else "Double Shake: DND Permission Needed"
                }
                "VIBRATE_MODE" -> {
                    val res = actionManager.setRingerModeSafe(AudioManager.RINGER_MODE_VIBRATE)
                    res to "Double Shake: Vibrate Mode"
                }
                else -> false to "No Double Shake Action"
            }
            if (ok && currentSettings.hapticFeedbackEnabled) {
                SystemUtils.triggerHapticPulse(context, 70L)
            }
            refreshSystemStatus()
            _telemetry.value = _telemetry.value.copy(lastGestureLabel = label)
            recordEvent("Double Shake Shortcut", "Double Shake", label, ok)
        }
    }

    suspend fun simulateTestGesture(gestureType: String): String {
        return when (gestureType) {
            "FLIP_DND" -> {
                val turningOn = !actionManager.isDndActive()
                val ok = actionManager.setDndEnabled(turningOn)
                if (ok) {
                    // Vibrate 2 times for ON, 1 time alone for OFF
                    SystemUtils.triggerFlipDndVibration(context, isTurningOn = turningOn)
                }
                refreshSystemStatus()
                val pulseNote = if (turningOn) "ON • Vibrated 2×" else "OFF • Vibrated 1× alone"
                val msg = if (ok) "Flip to DND $pulseNote"
                else "DND Policy Access Required — tap 'Grant DND Policy Permission' first"
                _telemetry.value = _telemetry.value.copy(lastGestureLabel = msg)
                recordEvent("Manual Gesture Test", "Test Flip to DND", msg, ok)
                msg
            }
            "SHAKE_TORCH" -> {
                val ok = actionManager.toggleFlashlight()
                refreshSystemStatus()
                val msg = if (ok) "Test Succeeded: Flashlight is now ${if (actionManager.isTorchEnabled) "ON" else "OFF"}"
                else "Flashlight hardware not available on this device/emulator"
                recordEvent("Manual Gesture Test", "Test Shake Flashlight", msg, ok)
                msg
            }
            else -> "Test completed"
        }
    }

    suspend fun evaluateRulesForTrigger(triggerType: String) {
        val rules = dao.getEnabledAutomationRules().filter { it.triggerType == triggerType }
        if (rules.isEmpty()) return

        val battery = batteryMonitor.sampleNow()
        val net = networkMonitor.sampleNetworkInfo()

        for (rule in rules) {
            // Check Trigger Parameter if applicable
            if (triggerType == "BATTERY_BELOW") {
                val threshold = rule.triggerParam.toIntOrNull() ?: 25
                if (battery.percentage > threshold) continue
            }

            // Evaluate IF Condition
            val conditionMet = when (rule.conditionType) {
                "ALWAYS" -> true
                "WHEN_CHARGING" -> battery.isCharging
                "WHEN_NOT_CHARGING" -> !battery.isCharging
                "WHEN_BATTERY_BELOW_50" -> battery.percentage < 50
                "WHEN_WIFI_CONNECTED" -> net.wifiConnected
                else -> true
            }
            if (!conditionMet) continue

            // Cooldown per rule (10 seconds minimum to prevent loops)
            val now = System.currentTimeMillis()
            if (rule.lastTriggeredAt != null && (now - rule.lastTriggeredAt) < 10_000L) continue

            val (ok, resultDesc) = actionManager.executeRuleAction(rule.actionType, rule.actionParam, rule.name)
            dao.updateAutomationRule(
                rule.copy(
                    lastTriggeredAt = now,
                    triggerCount = rule.triggerCount + 1
                )
            )
            recordEvent(rule.name, triggerType, resultDesc, ok)
            refreshSystemStatus()
        }
    }

    private suspend fun recordEvent(ruleName: String, trigger: String, action: String, succeeded: Boolean) {
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
