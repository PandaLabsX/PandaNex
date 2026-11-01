package com.example.automation

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

data class FullScreenDetectionState(
    val isFullScreenActive: Boolean = false,
    val systemBarsHidden: Boolean = false,
    val landscapeVideoOrGameActive: Boolean = false,
    val inAppFullScreenActive: Boolean = false,
    val reason: String = "Standard windowed mode"
)

/**
 * Play-Policy-compliant real-time detector that identifies whenever any app, video player,
 * browser video, Reel/Short, game, Picture-in-Picture session, or PandaNex diagnostic screen
 * runs in full screen so Clean Aqua Dynamics overlays, capsules, and animations immediately
 * disappear and stay suppressed until full-screen mode exits.
 */
class FullScreenDetector(
    private val context: Context,
    private val preferencesRepository: PandaPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private var currentSettings = PandaSettings()
    private var probeView: View? = null
    private var probeGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var monitorJob: Job? = null

    private var observedSystemBarsHidden = false
    private var hostWindowSystemBarsHidden = false
    private var inAppFullScreen = false
    private var inPictureInPicture = false
    private var lastKnownForegroundPkg = ""

    private val probeScreenLoc = IntArray(2)

    private val _state = MutableStateFlow(FullScreenDetectionState())
    val state: StateFlow<FullScreenDetectionState> = _state.asStateFlow()

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            inspectProbeLayoutAndInsets()
            evaluateNow()
        }

        override fun onLowMemory() = Unit
    }

    init {
        runCatching { context.registerComponentCallbacks(configCallbacks) }
        scope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                syncProbeAndMonitorLoop()
                evaluateNow()
            }
        }
    }

    fun setInAppFullScreen(active: Boolean) {
        if (inAppFullScreen == active) return
        inAppFullScreen = active
        evaluateNow()
    }

    fun setInPictureInPictureMode(active: Boolean) {
        if (inPictureInPicture == active) return
        inPictureInPicture = active
        evaluateNow()
    }

    fun setHostWindowSystemBarsHidden(hidden: Boolean) {
        if (hostWindowSystemBarsHidden == hidden) return
        hostWindowSystemBarsHidden = hidden
        evaluateNow()
    }

    fun reportSystemOverlayFullScreenSignal(hidden: Boolean) {
        if (observedSystemBarsHidden == hidden) return
        observedSystemBarsHidden = hidden
        evaluateNow()
    }

    fun syncProbeAndMonitorLoop() {
        val shouldMonitor = currentSettings.hideAquaDynamicsInFullScreen

        if (shouldMonitor && Settings.canDrawOverlays(context)) {
            attachInsetsProbeIfNeeded()
        } else {
            detachInsetsProbe()
            if (!Settings.canDrawOverlays(context)) {
                observedSystemBarsHidden = false
            }
        }

        val shouldRunPoll = shouldMonitor &&
            (currentSettings.aquaDynamicsEnabled ||
                currentSettings.edgeGlowEnabled ||
                currentSettings.backgroundMonitoringEnabled ||
                currentSettings.autoDimAppsEnabled ||
                Settings.canDrawOverlays(context))

        if (shouldRunPoll) {
            if (monitorJob?.isActive != true) {
                monitorJob = scope.launch {
                    while (isActive && currentSettings.hideAquaDynamicsInFullScreen) {
                        syncInsetsProbePresence()
                        inspectProbeLayoutAndInsets()
                        evaluateNow()
                        delay(750L)
                    }
                }
            }
        } else {
            monitorJob?.cancel()
            monitorJob = null
            if (!currentSettings.hideAquaDynamicsInFullScreen) {
                observedSystemBarsHidden = false
            }
            evaluateNow()
        }
    }

    private fun syncInsetsProbePresence() {
        if (Settings.canDrawOverlays(context)) {
            attachInsetsProbeIfNeeded()
        } else if (probeView != null) {
            detachInsetsProbe()
            observedSystemBarsHidden = false
        }
    }

    /**
     * Attaches a 1 × MATCH_PARENT invisible (alpha = 0f), non-focusable, non-touchable window
     * WITHOUT FLAG_LAYOUT_IN_SCREEN. Whenever ANY foreground app, video player, browser video,
     * Reel/Short, or game hides the status bar or navigation bar, WindowManager immediately
     * expands/shifts the window frame to y = 0 / full display height and dispatches WindowInsets.
     */
    private fun attachInsetsProbeIfNeeded() {
        if (probeView != null || !Settings.canDrawOverlays(context)) return
        val wm = windowManager ?: return
        try {
            val view = object : View(context) {
                @Suppress("DEPRECATION")
                override fun onWindowSystemUiVisibilityChanged(visible: Int) {
                    super.onWindowSystemUiVisibilityChanged(visible)
                    inspectProbeLayoutAndInsets()
                }
            }.apply {
                alpha = 0f
                setWillNotDraw(true)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    inspectProbeLayoutAndInsets()
                }
                ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                    val statusHidden = !insets.isVisible(WindowInsetsCompat.Type.statusBars())
                    val navMaxBottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
                    val navHidden = navMaxBottom > 0 && !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
                    updateObservedSystemBarsHidden(statusHidden || navHidden)
                    insets
                }
            }

            val globalListener = ViewTreeObserver.OnGlobalLayoutListener {
                inspectProbeLayoutAndInsets()
            }
            view.viewTreeObserver.addOnGlobalLayoutListener(globalListener)
            probeGlobalLayoutListener = globalListener

            // Intentionally omit FLAG_LAYOUT_IN_SCREEN and FLAG_LAYOUT_NO_LIMITS so WindowManager
            // shifts top Y from statusBarHeight -> 0 and expands height -> realHeight whenever
            // ANY app or video enters full screen.
            val lp = WindowManager.LayoutParams(
                1,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            wm.addView(view, lp)
            ViewCompat.requestApplyInsets(view)
            probeView = view
        } catch (_: Exception) {
            probeGlobalLayoutListener = null
            probeView = null
        }
    }

    private fun detachInsetsProbe() {
        val v = probeView ?: return
        probeView = null
        runCatching {
            probeGlobalLayoutListener?.let { listener ->
                if (v.viewTreeObserver.isAlive) {
                    v.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
            }
            probeGlobalLayoutListener = null
            windowManager?.removeViewImmediate(v)
        }
    }

    private fun inspectProbeLayoutAndInsets() {
        val pv = probeView ?: return
        if (!pv.isAttachedToWindow) return

        var detectedHidden = false

        // 1. Check WindowInsets visibility (API 30+ & AndroidX compat)
        ViewCompat.getRootWindowInsets(pv)?.let { insets ->
            val statusHidden = !insets.isVisible(WindowInsetsCompat.Type.statusBars())
            val navMaxBottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            val navHidden = navMaxBottom > 0 && !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            if (statusHidden || navHidden) {
                detectedHidden = true
            }
        }

        // 2. Check WindowManager non-FLAG_LAYOUT_IN_SCREEN frame geometry
        val probeHeight = pv.height
        val realDisplayHeight = queryRealDisplayHeight()
        val expectedStatusBarPx = querySystemStatusBarHeightPx()
        if (probeHeight > 0 && realDisplayHeight > 0) {
            pv.getLocationOnScreen(probeScreenLoc)
            val topOnScreen = probeScreenLoc[1]
            val statusCollapsedToTop = expectedStatusBarPx > 8 && topOnScreen <= 1
            val spansEntirePhysicalScreen = probeHeight >= (realDisplayHeight - 2)
            if (statusCollapsedToTop || spansEntirePhysicalScreen) {
                detectedHidden = true
            }
        }

        // 3. Legacy windowSystemUiVisibility fallback
        @Suppress("DEPRECATION")
        val sysVis = pv.windowSystemUiVisibility
        @Suppress("DEPRECATION")
        if ((sysVis and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0 ||
            (sysVis and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0 ||
            (sysVis and View.SYSTEM_UI_FLAG_IMMERSIVE) != 0 ||
            (sysVis and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0
        ) {
            detectedHidden = true
        }

        updateObservedSystemBarsHidden(detectedHidden)
    }

    private fun queryRealDisplayHeight(): Int {
        val wm = windowManager ?: return 0
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.currentWindowMetrics.bounds.height()
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.getRealMetrics(dm)
                dm.heightPixels
            }
        }.getOrDefault(0)
    }

    private fun querySystemStatusBarHeightPx(): Int {
        return runCatching {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        }.getOrDefault(0)
    }

    private fun updateObservedSystemBarsHidden(hidden: Boolean) {
        if (observedSystemBarsHidden != hidden) {
            observedSystemBarsHidden = hidden
            evaluateNow()
        }
    }

    fun evaluateNow(): FullScreenDetectionState {
        if (!currentSettings.hideAquaDynamicsInFullScreen) {
            val disabledState = FullScreenDetectionState(
                isFullScreenActive = false,
                systemBarsHidden = false,
                landscapeVideoOrGameActive = false,
                inAppFullScreenActive = inAppFullScreen,
                reason = "Full-screen auto-hide disabled"
            )
            _state.value = disabledState
            return disabledState
        }

        inspectProbeLayoutAndInsets()

        val anySystemBarsHidden = observedSystemBarsHidden || hostWindowSystemBarsHidden
        val isLandscape = isDeviceInLandscape()
        val activeMediaLabel = detectActiveMediaPlaybackLabel()
        val fgCategoryReason = detectImmersiveForegroundCategory(isLandscape)

        val landscapeMediaOrGame = (isLandscape && activeMediaLabel != null) || fgCategoryReason != null

        val activeReasons = buildList {
            if (inAppFullScreen) add("In-app full-screen view")
            if (inPictureInPicture) add("Picture-in-Picture video mode")
            if (anySystemBarsHidden) add("Full-screen / immersive window")
            if (isLandscape && activeMediaLabel != null) add("Full-screen landscape media ($activeMediaLabel)")
            if (fgCategoryReason != null) add(fgCategoryReason)
        }

        val next = FullScreenDetectionState(
            isFullScreenActive = activeReasons.isNotEmpty(),
            systemBarsHidden = anySystemBarsHidden,
            landscapeVideoOrGameActive = landscapeMediaOrGame,
            inAppFullScreenActive = inAppFullScreen || inPictureInPicture,
            reason = activeReasons.firstOrNull() ?: "Standard windowed mode"
        )

        if (_state.value != next) {
            _state.value = next
        }
        return next
    }

    private fun isDeviceInLandscape(): Boolean {
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return true
        }
        val rotation = runCatching {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation
        }.getOrNull()
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private fun detectActiveMediaPlaybackLabel(): String? {
        val listenerGranted = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)

        val msm = mediaSessionManager
        if (listenerGranted && msm != null) {
            val activeSessionPkg = runCatching {
                val component = ComponentName(context, EdgeGlowNotificationService::class.java)
                val controllers = msm.getActiveSessions(component)
                controllers.firstOrNull { controller ->
                    val playbackState = controller.playbackState?.state
                    playbackState == PlaybackState.STATE_PLAYING ||
                        playbackState == PlaybackState.STATE_FAST_FORWARDING ||
                        playbackState == PlaybackState.STATE_BUFFERING
                }?.packageName
            }.getOrNull()

            if (!activeSessionPkg.isNullOrEmpty()) {
                return activeSessionPkg.substringAfterLast('.')
            }
        }

        val audioPlaying = runCatching { audioManager?.isMusicActive == true }.getOrDefault(false)
        return if (audioPlaying) "Active video/audio stream" else null
    }

    private fun detectImmersiveForegroundCategory(isLandscape: Boolean): String? {
        if (!AutoDimController.hasUsageStatsPermission(context)) return null
        val usm = usageStatsManager ?: return null
        val now = System.currentTimeMillis()
        var latestPkg = ""
        var latestTs = 0L

        runCatching {
            val events = usm.queryEvents(now - 8_000L, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTs) {
                    latestTs = event.timeStamp
                    latestPkg = event.packageName.orEmpty()
                }
            }
        }

        if (latestPkg.isNotEmpty() && latestPkg != "com.android.systemui") {
            lastKnownForegroundPkg = latestPkg
        }
        val fg = lastKnownForegroundPkg
        if (fg.isEmpty() || fg == context.packageName) return null

        val appInfo = runCatching {
            context.packageManager.getApplicationInfo(fg, 0)
        }.getOrNull() ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return "Full-screen game (${fg.substringAfterLast('.')})"
            }
            if (isLandscape && appInfo.category == ApplicationInfo.CATEGORY_VIDEO) {
                return "Full-screen video (${fg.substringAfterLast('.')})"
            }
        }

        if (isLandscape) {
            val lowerPkg = fg.lowercase()
            val landscapeVideoOrViewerKeywords = listOf(
                "youtube", "netflix", "primevideo", "avod", "disney", "hotstar", "hulu",
                "twitch", "vlc", "mxtech", "mpv", "plex", "jellyfin", "kodi", "tiktok",
                "musically", "reels", "player", "video", "cinema", "gallery", "photos",
                "chrome", "firefox", "brave", "opera", "sbrowser"
            )
            if (landscapeVideoOrViewerKeywords.any { lowerPkg.contains(it) }) {
                return "Full-screen landscape app (${fg.substringAfterLast('.')})"
            }
        }
        return null
    }
}
