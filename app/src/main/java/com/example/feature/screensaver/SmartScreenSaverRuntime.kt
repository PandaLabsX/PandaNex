package com.example.feature.screensaver

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.PandaNexApplication
import com.example.data.local.datastore.PandaSettings
import com.example.ui.theme.PandaNexTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Full-screen motion-activated ambient Screen Saver Activity.
 * - Uses Android-supported setShowWhenLocked(true) + setTurnScreenOn(true) without bypassing
 *   or dismissing Keyguard (PIN, pattern, password, biometric remain intact).
 * - Exits cleanly on single tap anywhere, configurable timeout, active phone call, or when placed
 *   stably back on a flat surface after the configured stationary delay.
 */
class SmartScreenSaverActivity : ComponentActivity() {

    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var stationaryFlatSinceMs: Long = 0L
    private var sawUserMovementInSession: Boolean = false
    private var activityCreatedAtMs: Long = 0L
    private var latestStationaryOffSec: Int = 4
    private var isPreviewLaunch: Boolean = false

    private val flatStabilityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val mag = sqrt(ax * ax + ay * ay + az * az)
            val now = SystemClock.elapsedRealtime()

            val isTiltedOrMoving = abs(mag - 9.81f) > 1.25f || abs(ax) > 2.4f || abs(ay) > 2.4f
            if (isTiltedOrMoving) {
                sawUserMovementInSession = true
                stationaryFlatSinceMs = 0L
                return
            }

            val isFlatSurface = abs(ax) <= 1.85f && abs(ay) <= 1.85f && abs(az) >= 8.5f && abs(mag - 9.81f) <= 0.75f
            if (isFlatSurface && !isPreviewLaunch && (sawUserMovementInSession || now - activityCreatedAtMs > 2500L)) {
                if (stationaryFlatSinceMs == 0L) {
                    stationaryFlatSinceMs = now
                } else if (now - stationaryFlatSinceMs >= latestStationaryOffSec.coerceIn(2, 15) * 1000L) {
                    stationaryFlatSinceMs = 0L
                    returnToDisplayOffAndExit()
                }
            } else {
                stationaryFlatSinceMs = 0L
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCreatedAtMs = SystemClock.elapsedRealtime()
        isPreviewLaunch = intent?.getBooleanExtra(EXTRA_IS_PREVIEW, false) ?: false
        val styleOverrideCode = intent?.getStringExtra(EXTRA_STYLE_OVERRIDE)

        configureLockScreenSafeWindow()
        hideSystemBarsImmersive()

        val app = application as PandaNexApplication
        val container = app.container
        container.smartGestureManager.setScreenSaverActive(true)
        container.smartGestureManager.onCloseActiveScreenSaverRequested = {
            if (!isFinishing) finish()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            val settings by container.preferencesRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = PandaSettings()
            )
            val unreadCount by container.dao.observeUnreadCount().collectAsStateWithLifecycle(initialValue = 0)
            val batterySnap = container.batteryMonitor.sampleNow()

            latestStationaryOffSec = settings.screenSaverStationaryOffSec

            LaunchedEffect(settings.screenSaverBrightnessPercent) {
                val lp = window.attributes
                lp.screenBrightness = (settings.screenSaverBrightnessPercent / 100f).coerceIn(0.05f, 1.0f)
                window.attributes = lp
            }

            LaunchedEffect(settings.screenSaverTimeoutSec) {
                val timeoutMs = settings.screenSaverTimeoutSec.coerceIn(5, 60) * 1000L
                delay(timeoutMs)
                if (!isPreviewLaunch) {
                    returnToDisplayOffAndExit()
                } else {
                    finish()
                }
            }

            val resolvedStyle = if (!styleOverrideCode.isNullOrBlank()) {
                ScreenSaverStyle.fromCode(styleOverrideCode)
            } else {
                ScreenSaverStyle.fromCode(settings.screenSaverStyle)
            }

            val telemetry = ScreenSaverTelemetryData(
                batteryPercent = batterySnap.percentage,
                isCharging = batterySnap.isCharging,
                chargingLabel = batterySnap.statusLabel,
                activeNotificationsCount = unreadCount
            )

            PandaNexTheme(
                themeMode = settings.themeMode,
                animationsEnabled = settings.animationsEnabled && settings.screenSaverAnimationsEnabled
            ) {
                TapToExitAmbientContainer(onExit = { finish() }) {
                    ScreenSaverClockCanvasSurface(
                        style = resolvedStyle,
                        settings = settings,
                        telemetry = telemetry,
                        isThumbnail = false
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBarsImmersive()
        accelSensor?.let { sensor ->
            runCatching {
                sensorManager?.registerListener(flatStabilityListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onPause() {
        runCatching { sensorManager?.unregisterListener(flatStabilityListener) }
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { sensorManager?.unregisterListener(flatStabilityListener) }
        val container = (application as PandaNexApplication).container
        container.smartGestureManager.onCloseActiveScreenSaverRequested = null
        container.smartGestureManager.setScreenSaverActive(false)
        super.onDestroy()
    }

    private fun returnToDisplayOffAndExit() {
        val container = (application as PandaNexApplication).container
        container.smartGestureManager.motionDetectionManager.applyPostSleepGuard(4500L)
        if (container.displayActionManager.isDeviceAdminActive()) {
            container.displayActionManager.turnDisplayOffOrSleep()
        }
        if (!isFinishing) {
            finish()
        }
    }

    private fun configureLockScreenSafeWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBarsImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_IS_PREVIEW = "extra_is_preview"
        const val EXTRA_STYLE_OVERRIDE = "extra_style_override"
    }
}

@Composable
private fun TapToExitAmbientContainer(
    onExit: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_saver_ambient_root")
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onExit() })
            }
    ) {
        content()
    }
}

/**
 * Native Android System Screen Saver (Daydream / Dock / Charging DreamService) integration
 * rendering the user's selected PandaNex Screen Saver style.
 */
class PandaSmartDreamService : DreamService() {

    private var composeLifecycleOwner: DreamComposeLifecycleOwner? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = false

        val app = application as? PandaNexApplication ?: return
        val container = app.container

        val owner = DreamComposeLifecycleOwner().also {
            it.onCreate()
            composeLifecycleOwner = it
        }

        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(owner)
            decor.setViewTreeSavedStateRegistryOwner(owner)
            decor.setViewTreeViewModelStoreOwner(owner)
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                val settings by container.preferencesRepository.settingsFlow.collectAsStateWithLifecycle(
                    initialValue = PandaSettings()
                )
                val unreadCount by container.dao.observeUnreadCount().collectAsStateWithLifecycle(initialValue = 0)
                val batterySnap = container.batteryMonitor.sampleNow()
                val telemetry = ScreenSaverTelemetryData(
                    batteryPercent = batterySnap.percentage,
                    isCharging = batterySnap.isCharging,
                    chargingLabel = batterySnap.statusLabel,
                    activeNotificationsCount = unreadCount
                )

                PandaNexTheme(
                    themeMode = settings.themeMode,
                    animationsEnabled = settings.animationsEnabled && settings.screenSaverAnimationsEnabled
                ) {
                    TapToExitAmbientContainer(onExit = { finish() }) {
                        ScreenSaverClockCanvasSurface(
                            style = ScreenSaverStyle.fromCode(settings.screenSaverStyle),
                            settings = settings,
                            telemetry = telemetry,
                            isThumbnail = false
                        )
                    }
                }
            }
        }

        setContentView(composeView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        composeLifecycleOwner?.onResume()
        (application as? PandaNexApplication)?.container?.smartGestureManager?.setScreenSaverActive(true)
    }

    override fun onDreamingStopped() {
        (application as? PandaNexApplication)?.container?.smartGestureManager?.setScreenSaverActive(false)
        composeLifecycleOwner?.onStop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        composeLifecycleOwner?.onDestroy()
        composeLifecycleOwner = null
        super.onDetachedFromWindow()
    }
}

private class DreamComposeLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
