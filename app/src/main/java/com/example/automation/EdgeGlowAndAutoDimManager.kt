package com.example.automation

import android.Manifest
import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.app.Notification
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.PandaNexApplication
import com.example.data.local.datastore.PandaPreferencesRepository
import com.example.data.local.datastore.PandaSettings
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
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

data class EdgeGlowActiveState(
    val isActive: Boolean = false,
    val isPreview: Boolean = false,
    val triggerType: String = "NOTIFICATION", // CALL or NOTIFICATION
    val primaryColorHex: Long = 0xFF00E5FFL,
    val secondaryColorHex: Long = 0xFF1DE9B6L,
    val strokeDp: Float = 8f,
    val style: String = "AQUA_PULSE",
    val sourceLabel: String = "",
    val overlayPermissionGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val phoneStatePermissionGranted: Boolean = false,
    val isSuppressedByFullScreen: Boolean = false,
    val fullScreenReason: String = "",
    val lastEventSummary: String = "Ready for incoming calls & notifications"
)

data class AutoDimStatus(
    val hasUsageAccess: Boolean = false,
    val hasWriteSettings: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isCurrentlyDimmed: Boolean = false,
    val isPreviewActive: Boolean = false,
    val activeForegroundPackage: String = "",
    val dimmedForPackage: String = "",
    val savedBrightnessRaw: Int? = null,
    val lastEventSummary: String = "Select banking or privacy apps to auto-dim when opened"
)

data class SelectableAppItem(
    val appName: String,
    val packageName: String,
    val isSuggestedBankOrSensitive: Boolean,
    val categoryBadge: String
)

class EdgeGlowManager(
    private val context: Context,
    private val preferencesRepository: PandaPreferencesRepository,
    private val fullScreenDetector: FullScreenDetector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private var currentSettings = PandaSettings()
    private var autoStopJob: Job? = null
    private var overlayView: EdgeGlowOverlayView? = null
    private var callListenerRegistered = false
    private var activePhoneCallInProgress = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class CallGlowCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallStateChange(state)
        }
    }

    private var modernCallCallback: Any? = null

    @Suppress("DEPRECATION")
    private val legacyPhoneListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            handleCallStateChange(state)
        }
    }

    private val _state = MutableStateFlow(EdgeGlowActiveState())
    val state: StateFlow<EdgeGlowActiveState> = _state.asStateFlow()

    init {
        refreshPermissions()
        scope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                refreshPermissions()
                syncTelephonyCallListener()
                if (!settings.edgeGlowEnabled && !_state.value.isPreview) {
                    stopGlow()
                }
                if (settings.hideAquaDynamicsInFullScreen && fullScreenDetector.evaluateNow().isFullScreenActive) {
                    suppressForFullScreen(fullScreenDetector.state.value.reason)
                } else if (!settings.hideAquaDynamicsInFullScreen && _state.value.isSuppressedByFullScreen) {
                    _state.value = _state.value.copy(
                        isSuppressedByFullScreen = false,
                        fullScreenReason = ""
                    )
                }
            }
        }
        scope.launch {
            fullScreenDetector.state.collectLatest { fsState ->
                if (currentSettings.hideAquaDynamicsInFullScreen && fsState.isFullScreenActive) {
                    suppressForFullScreen(fsState.reason)
                } else if (_state.value.isSuppressedByFullScreen) {
                    _state.value = _state.value.copy(
                        isSuppressedByFullScreen = false,
                        fullScreenReason = "",
                        lastEventSummary = "Ready for incoming calls & notifications"
                    )
                }
            }
        }
    }

    private fun suppressForFullScreen(reason: String) {
        autoStopJob?.cancel()
        autoStopJob = null
        removeSystemOverlay()
        _state.value = _state.value.copy(
            isActive = false,
            isPreview = false,
            isSuppressedByFullScreen = true,
            fullScreenReason = reason,
            lastEventSummary = "Aqua Dynamics hidden • $reason"
        )
    }

    fun refreshPermissions() {
        val overlayOk = Settings.canDrawOverlays(context)
        val notifOk = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)
        val phoneOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        _state.value = _state.value.copy(
            overlayPermissionGranted = overlayOk,
            notificationListenerGranted = notifOk,
            phoneStatePermissionGranted = phoneOk
        )
    }

    fun syncTelephonyCallListener() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val shouldListen = currentSettings.edgeGlowEnabled && currentSettings.edgeGlowCallsEnabled && hasPerm
        if (shouldListen && !callListenerRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cb = CallGlowCallback()
                    modernCallCallback = cb
                    telephonyManager?.registerTelephonyCallback(context.mainExecutor, cb)
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(legacyPhoneListener, PhoneStateListener.LISTEN_CALL_STATE)
                }
                callListenerRegistered = true
            }
        } else if (!shouldListen && callListenerRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (modernCallCallback as? TelephonyCallback)?.let {
                        telephonyManager?.unregisterTelephonyCallback(it)
                    }
                    modernCallCallback = null
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(legacyPhoneListener, PhoneStateListener.LISTEN_NONE)
                }
            }
            callListenerRegistered = false
        }
    }

    private fun handleCallStateChange(callState: Int) {
        activePhoneCallInProgress = callState != TelephonyManager.CALL_STATE_IDLE
        if (activePhoneCallInProgress) {
            // Never cover or interfere with the system calling interface during calls
            if (_state.value.isActive && !_state.value.isPreview) {
                stopGlow()
            }
            return
        }
    }

    private fun isAnyCallCurrentlyActive(): Boolean {
        if (activePhoneCallInProgress) return true
        val mode = runCatching { audioManager?.mode ?: android.media.AudioManager.MODE_NORMAL }
            .getOrDefault(android.media.AudioManager.MODE_NORMAL)
        return mode == android.media.AudioManager.MODE_RINGTONE ||
            mode == android.media.AudioManager.MODE_IN_CALL ||
            mode == android.media.AudioManager.MODE_IN_COMMUNICATION
    }

    fun triggerGlow(
        isCall: Boolean,
        sourceLabel: String,
        continuousUntilStopped: Boolean = false,
        forcePreview: Boolean = false
    ) {
        scope.launch {
            refreshPermissions()
            val s = currentSettings
            val fs = fullScreenDetector.evaluateNow()
            if (s.hideAquaDynamicsInFullScreen && fs.isFullScreenActive) {
                suppressForFullScreen(fs.reason)
                return@launch
            }
            if (!forcePreview && isAnyCallCurrentlyActive()) {
                stopGlow()
                return@launch
            }
            if (!forcePreview) {
                if (!s.edgeGlowEnabled) return@launch
                if (isCall && !s.edgeGlowCallsEnabled) return@launch
                if (!isCall && !s.edgeGlowNotificationsEnabled) return@launch
            }

            val primaryHex = if (isCall) s.edgeGlowCallColorHex else s.edgeGlowNotifColorHex
            val secondaryHex = if (isCall) s.edgeGlowNotifColorHex else s.edgeGlowCallColorHex
            val triggerKind = if (isCall) "CALL" else "NOTIFICATION"

            _state.value = _state.value.copy(
                isActive = true,
                isPreview = forcePreview,
                triggerType = triggerKind,
                primaryColorHex = primaryHex,
                secondaryColorHex = secondaryHex,
                strokeDp = s.edgeGlowStrokeDp,
                style = s.edgeGlowStyle,
                sourceLabel = sourceLabel,
                isSuppressedByFullScreen = false,
                fullScreenReason = "",
                lastEventSummary = "${if (isCall) "Call" else "Notification"} Edge Glow • $sourceLabel"
            )

            showOrUpdateSystemOverlay(
                primaryColor = primaryHex.toInt(),
                secondaryColor = secondaryHex.toInt(),
                strokeDp = s.edgeGlowStrokeDp,
                style = s.edgeGlowStyle
            )

            autoStopJob?.cancel()
            if (!continuousUntilStopped) {
                val duration = if (isCall && forcePreview) {
                    (s.edgeGlowDurationMs + 1500L).coerceAtMost(8000L)
                } else {
                    s.edgeGlowDurationMs
                }
                autoStopJob = scope.launch {
                    delay(duration)
                    stopGlow()
                }
            }
        }
    }

    fun stopGlow() {
        autoStopJob?.cancel()
        autoStopJob = null
        removeSystemOverlay()
        _state.value = _state.value.copy(isActive = false, isPreview = false)
    }

    private fun showOrUpdateSystemOverlay(
        primaryColor: Int,
        secondaryColor: Int,
        strokeDp: Float,
        style: String
    ) {
        if (!Settings.canDrawOverlays(context)) return
        val wm = windowManager ?: return
        try {
            val existing = overlayView
            if (existing != null) {
                existing.updateConfig(primaryColor, secondaryColor, strokeDp, style)
                return
            }

            val view = EdgeGlowOverlayView(
                context = context,
                shouldSuppressDraw = {
                    currentSettings.hideAquaDynamicsInFullScreen &&
                        fullScreenDetector.state.value.isFullScreenActive
                },
                onFullScreenDetected = {
                    if (currentSettings.hideAquaDynamicsInFullScreen) {
                        fullScreenDetector.reportSystemOverlayFullScreenSignal(true)
                        suppressForFullScreen("Full-screen / immersive window")
                    }
                }
            ).apply {
                updateConfig(primaryColor, secondaryColor, strokeDp, style)
            }
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            wm.addView(view, layoutParams)
            view.startPulse()
            overlayView = view
        } catch (_: Exception) {
            overlayView = null
        }
    }

    private fun removeSystemOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching {
            view.stopPulse()
            windowManager?.removeViewImmediate(view)
        }
    }
}

private class EdgeGlowOverlayView(
    context: Context,
    private val shouldSuppressDraw: () -> Boolean = { false },
    private val onFullScreenDetected: () -> Unit = {}
) : View(context) {
    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusHidden = !insets.isVisible(WindowInsetsCompat.Type.statusBars())
            val navMax = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            val navHidden = navMax > 0 && !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            if (statusHidden || navHidden) {
                onFullScreenDetected()
            }
            insets
        }
    }

    @Suppress("DEPRECATION")
    override fun onWindowSystemUiVisibilityChanged(visible: Int) {
        super.onWindowSystemUiVisibilityChanged(visible)
        if ((visible and SYSTEM_UI_FLAG_FULLSCREEN) != 0 ||
            (visible and SYSTEM_UI_FLAG_IMMERSIVE) != 0 ||
            (visible and SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0
        ) {
            onFullScreenDetected()
        }
    }
    private var primaryColor: Int = 0xFF00E5FF.toInt()
    private var secondaryColor: Int = 0xFF1DE9B6.toInt()
    private var strokePx: Float = 24f
    private var glowStyle: String = "AQUA_PULSE"
    private var phase: Float = 0f

    private val rect = RectF()
    private val sweepMatrix = Matrix()
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    fun updateConfig(primary: Int, secondary: Int, strokeDp: Float, style: String) {
        primaryColor = primary
        secondaryColor = secondary
        strokePx = strokeDp * resources.displayMetrics.density
        glowStyle = style
        invalidate()
    }

    fun startPulse() {
        if (!animator.isStarted) animator.start()
    }

    fun stopPulse() {
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shouldSuppressDraw()) {
            post { onFullScreenDetected() }
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cornerRadius = 38f * resources.displayMetrics.density
        val pulseFactor = (0.55f + 0.45f * sin(phase * Math.PI * 2.0)).toFloat()

        val shader: Shader = when (glowStyle) {
            "CYBER_SWEEP" -> {
                val sg = SweepGradient(
                    w / 2f,
                    h / 2f,
                    intArrayOf(primaryColor, secondaryColor, 0x2200E5FF, primaryColor),
                    floatArrayOf(0f, 0.35f, 0.72f, 1f)
                )
                sweepMatrix.setRotate(phase * 360f, w / 2f, h / 2f)
                sg.setLocalMatrix(sweepMatrix)
                sg
            }
            "DUAL_WAVE" -> {
                val shift = h * phase
                LinearGradient(
                    0f,
                    shift,
                    0f,
                    shift + h * 0.6f,
                    intArrayOf(primaryColor, secondaryColor, primaryColor),
                    null,
                    Shader.TileMode.MIRROR
                )
            }
            else -> {
                LinearGradient(
                    0f,
                    0f,
                    w,
                    h,
                    intArrayOf(primaryColor, secondaryColor, primaryColor),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
        }

        val alphaBoost = if (glowStyle == "HORIZON_BEAM") 1f else pulseFactor

        // Outer soft halo
        outerPaint.shader = shader
        outerPaint.strokeWidth = strokePx * 3.1f
        outerPaint.alpha = (85 * alphaBoost).roundToInt().coerceIn(25, 130)
        val insetOuter = strokePx * 0.6f
        rect.set(insetOuter, insetOuter, w - insetOuter, h - insetOuter)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outerPaint)

        // Mid aqua aura
        midPaint.shader = shader
        midPaint.strokeWidth = strokePx * 1.8f
        midPaint.alpha = (155 * alphaBoost).roundToInt().coerceIn(60, 210)
        val insetMid = strokePx * 0.45f
        rect.set(insetMid, insetMid, w - insetMid, h - insetMid)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, midPaint)

        // Crisp neon edge core
        corePaint.shader = shader
        corePaint.strokeWidth = strokePx
        corePaint.alpha = (235 * (0.75f + 0.25f * alphaBoost)).roundToInt().coerceIn(160, 255)
        val insetCore = strokePx * 0.35f
        rect.set(insetCore, insetCore, w - insetCore, h - insetCore)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, corePaint)
    }
}

/**
 * Listens for incoming system notifications & call notifications when the user enables Notification Access.
 */
class EdgeGlowNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        val app = applicationContext as? PandaNexApplication ?: return
        val glowManager = app.container.edgeGlowManager
        val aquaManager = app.container.aquaDynamicsManager

        val isCallCategory = notification.category == Notification.CATEGORY_CALL
        val isMissedCallCategory = notification.category == Notification.CATEGORY_MISSED_CALL
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))

        if (isCallCategory) {
            // Suppress Aqua Dynamics & Edge Glow immediately so system calling interface is untouched
            aquaManager.onSystemCallNotificationSignal(Notification.CATEGORY_CALL, isActiveCall = true, appLabel = appLabel)
            glowManager.stopGlow()
            return
        }

        if (isMissedCallCategory) {
            aquaManager.onSystemCallNotificationSignal(Notification.CATEGORY_MISSED_CALL, isActiveCall = true, appLabel = appLabel)
        }

        if (isOngoing || isGroupSummary) return

        // Avoid recursive triggering on PandaNex's own persistent foreground service notification
        if (pkg == packageName && notification.channelId == "pandanex_foreground_service") return

        glowManager.triggerGlow(
            isCall = false,
            sourceLabel = appLabel,
            continuousUntilStopped = false,
            forcePreview = false
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val notification = sbn?.notification ?: return
        if (notification.category == Notification.CATEGORY_CALL) {
            val app = applicationContext as? PandaNexApplication ?: return
            app.container.aquaDynamicsManager.onSystemCallNotificationSignal(
                Notification.CATEGORY_CALL,
                isActiveCall = false
            )
            app.container.edgeGlowManager.stopGlow()
        }
    }
}

/**
 * Monitors foreground app transitions via UsageStatsManager and automatically dims screen brightness
 * (plus optional privacy shade overlay) when banking or user-selected sensitive apps are opened,
 * restoring the exact prior system brightness when exiting.
 */
class AutoDimController(
    private val context: Context,
    private val preferencesRepository: PandaPreferencesRepository,
    private val fullScreenDetector: FullScreenDetector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private var currentSettings = PandaSettings()
    private var monitorJob: Job? = null
    private var previewJob: Job? = null
    private var shadeOverlayView: View? = null

    private var savedBrightnessValue: Int? = null
    private var savedBrightnessMode: Int? = null
    private var lastObservedForegroundPkg: String = ""

    private val _status = MutableStateFlow(AutoDimStatus())
    val status: StateFlow<AutoDimStatus> = _status.asStateFlow()

    init {
        refreshPermissions()
        scope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                refreshPermissions()
                if (settings.autoDimAppsEnabled) {
                    startMonitoringLoop()
                    // Re-evaluate immediately if brightness slider changed while dimmed
                    if (_status.value.isCurrentlyDimmed && _status.value.dimmedForPackage.isNotEmpty()) {
                        if (_status.value.dimmedForPackage !in settings.autoDimPackagesSet) {
                            restoreNormalBrightness("Removed from Auto-Dim list")
                        } else {
                            applyDimmingForPackage(_status.value.dimmedForPackage, settings)
                        }
                    }
                } else {
                    stopMonitoringLoop()
                    if (_status.value.isCurrentlyDimmed) {
                        restoreNormalBrightness("Auto-Dim disabled")
                    }
                }
            }
        }
    }

    fun refreshPermissions() {
        val usageOk = hasUsageStatsPermission(context)
        val writeOk = Settings.System.canWrite(context)
        val overlayOk = Settings.canDrawOverlays(context)
        _status.value = _status.value.copy(
            hasUsageAccess = usageOk,
            hasWriteSettings = writeOk,
            hasOverlayPermission = overlayOk
        )
    }

    private fun startMonitoringLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive && currentSettings.autoDimAppsEnabled) {
                pollForegroundAppOnce()
                delay(1100L)
            }
        }
    }

    private fun stopMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    suspend fun pollForegroundAppOnce() {
        refreshPermissions()
        val s = currentSettings
        if (!s.autoDimAppsEnabled || !_status.value.hasUsageAccess) return
        if (_status.value.isPreviewActive) return

        val fgPkg = withContext(Dispatchers.Default) {
            queryCurrentForegroundPackage()
        }
        if (fgPkg.isEmpty() || fgPkg == "com.android.systemui") return

        lastObservedForegroundPkg = fgPkg
        val shouldDim = s.autoDimPackagesSet.contains(fgPkg)

        if (shouldDim && (!_status.value.isCurrentlyDimmed || _status.value.dimmedForPackage != fgPkg)) {
            applyDimmingForPackage(fgPkg, s)
        } else if (!shouldDim && _status.value.isCurrentlyDimmed) {
            restoreNormalBrightness("Exited dimmed app → restored brightness")
        } else {
            if (!shouldDim && shadeOverlayView != null && fullScreenDetector.state.value.isFullScreenActive) {
                removeShadeOverlay()
            }
            _status.value = _status.value.copy(activeForegroundPackage = fgPkg)
        }
    }

    private fun queryCurrentForegroundPackage(): String {
        val usm = usageStatsManager ?: return lastObservedForegroundPkg
        val now = System.currentTimeMillis()
        var latestPkg = ""
        var latestTs = 0L

        runCatching {
            val events = usm.queryEvents(now - 10_000L, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTs) {
                    latestTs = event.timeStamp
                    latestPkg = event.packageName ?: ""
                }
            }
        }

        if (latestPkg.isNotEmpty()) return latestPkg
        if (lastObservedForegroundPkg.isNotEmpty()) return lastObservedForegroundPkg

        // Initial seed fallback from recent usage stats
        return runCatching {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName.orEmpty()
        }.getOrDefault("")
    }

    private fun applyDimmingForPackage(pkg: String, settings: PandaSettings) {
        val cr = context.contentResolver
        if (savedBrightnessValue == null) {
            savedBrightnessValue = runCatching {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            }.getOrDefault(140)
            savedBrightnessMode = runCatching {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE)
            }.getOrDefault(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        }

        val targetRaw = ((settings.autoDimTargetBrightnessPercent / 100f) * 255f)
            .roundToInt()
            .coerceIn(4, 255)

        var appliedSystemWrite = false
        if (Settings.System.canWrite(context)) {
            appliedSystemWrite = runCatching {
                Settings.System.putInt(
                    cr,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, targetRaw)
                true
            }.getOrDefault(false)
        }

        val shouldApplyShade = Settings.canDrawOverlays(context) &&
            (settings.autoDimUseExtraPrivacyShade || !appliedSystemWrite)

        if (shouldApplyShade) {
            val shadeAlpha = ((1f - (settings.autoDimTargetBrightnessPercent / 100f)) * 0.62f)
                .coerceIn(0.22f, 0.72f)
            showOrUpdateShadeOverlay(shadeAlpha)
        } else {
            removeShadeOverlay()
        }

        _status.value = _status.value.copy(
            isCurrentlyDimmed = true,
            activeForegroundPackage = pkg,
            dimmedForPackage = pkg,
            savedBrightnessRaw = savedBrightnessValue,
            lastEventSummary = "Dimmed to ${settings.autoDimTargetBrightnessPercent}% for ${pkg.substringAfterLast('.')}"
        )
    }

    fun restoreNormalBrightness(reason: String = "Brightness restored") {
        val cr = context.contentResolver
        if (Settings.System.canWrite(context)) {
            savedBrightnessValue?.let { prevVal ->
                runCatching {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, prevVal.coerceIn(1, 255))
                }
            }
            savedBrightnessMode?.let { prevMode ->
                runCatching {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, prevMode)
                }
            }
        }
        savedBrightnessValue = null
        savedBrightnessMode = null
        removeShadeOverlay()

        _status.value = _status.value.copy(
            isCurrentlyDimmed = false,
            isPreviewActive = false,
            dimmedForPackage = "",
            savedBrightnessRaw = null,
            lastEventSummary = reason
        )
    }

    fun triggerPreviewDim(durationMs: Long = 3500L) {
        previewJob?.cancel()
        previewJob = scope.launch {
            _status.value = _status.value.copy(isPreviewActive = true)
            applyDimmingForPackage(context.packageName, currentSettings)
            _status.value = _status.value.copy(
                lastEventSummary = "Testing Auto-Dim (${currentSettings.autoDimTargetBrightnessPercent}%) for ${durationMs / 1000.0}s..."
            )
            delay(durationMs)
            restoreNormalBrightness("Preview finished — original brightness restored")
        }
    }

    private fun showOrUpdateShadeOverlay(alphaFraction: Float) {
        if (!Settings.canDrawOverlays(context)) return
        val wm = windowManager ?: return
        val argb = (((alphaFraction.coerceIn(0.15f, 0.78f)) * 255f).roundToInt() shl 24)
        try {
            val existing = shadeOverlayView
            if (existing != null) {
                existing.setBackgroundColor(argb)
                return
            }
            val shade = View(context).apply {
                setBackgroundColor(argb)
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            wm.addView(shade, lp)
            shadeOverlayView = shade
        } catch (_: Exception) {
            shadeOverlayView = null
        }
    }

    private fun removeShadeOverlay() {
        val view = shadeOverlayView ?: return
        shadeOverlayView = null
        runCatching {
            windowManager?.removeViewImmediate(view)
        }
    }

    suspend fun loadInstalledSelectableApps(): List<SelectableAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val bankAndPrivacyKeywords = listOf(
            "bank", "pay", "upi", "wallet", "finance", "credit", "card", "chase", "wells",
            "citi", "bofa", "barclays", "hsbc", "revolut", "wise", "paypal", "venmo",
            "cashapp", "paisa", "gpay", "phonepe", "paytm", "bhim", "sbi", "hdfc",
            "icici", "axis", "kotak", "crypto", "binance", "coinbase", "auth", "vault", "pass"
        )

        resolveInfos
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching { ri.loadLabel(pm).toString().trim() }
                    .getOrDefault(pkg)
                    .ifBlank { pkg }
                val combined = "${label.lowercase()} ${pkg.lowercase()}"
                val isSuggested = bankAndPrivacyKeywords.any { kw -> combined.contains(kw) }
                val badge = when {
                    combined.contains("bank") || combined.contains("chase") || combined.contains("sbi") ||
                        combined.contains("hdfc") || combined.contains("icici") || combined.contains("citi") -> "Banking"
                    combined.contains("pay") || combined.contains("upi") || combined.contains("wallet") ||
                        combined.contains("venmo") || combined.contains("revolut") -> "Payments & Wallet"
                    isSuggested -> "Finance / Sensitive"
                    else -> "Installed App"
                }
                SelectableAppItem(
                    appName = label,
                    packageName = pkg,
                    isSuggestedBankOrSensitive = isSuggested,
                    categoryBadge = badge
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<SelectableAppItem> { it.isSuggestedBankOrSensitive }
                    .thenBy { it.appName.lowercase() }
            )
    }

    companion object {
        fun hasUsageStatsPermission(context: Context): Boolean {
            return try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) {
                false
            }
        }
    }
}
