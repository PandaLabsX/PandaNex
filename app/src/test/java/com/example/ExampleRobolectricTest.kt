package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.automation.QuickCaptureRecognizer
import com.example.automation.QuickCaptureSensitivity
import com.example.core.common.SystemUtils
import com.example.data.local.datastore.PandaSettings
import com.example.monitoring.RamMonitor
import com.example.monitoring.StorageMonitor
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `verify PandaNex app name and read-only storage and RAM monitors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PandaNex", appName)

        val ramSnapshot = RamMonitor(context).sample()
        assertTrue(ramSnapshot.totalBytes > 0L)
        assertTrue(ramSnapshot.usagePercent in 0f..100f)

        val storageSnapshot = StorageMonitor().sample()
        assertTrue(storageSnapshot.totalBytes > 0L)
        assertTrue(storageSnapshot.usedPercent in 0f..100f)
    }

    @Test
    fun `verify Auto-Dim package parsing, Edge Glow preview, and Flip-to-DND 2x ON and 1x OFF vibrations`() {
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()

        // 1. Auto-Dimmed banking package CSV parsing
        val settings = PandaSettings(
            autoDimAppsEnabled = true,
            autoDimTargetBrightnessPercent = 15,
            autoDimPackagesCsv = "com.chase.sig.android, com.google.android.apps.nbu.paisa.user "
        )
        assertEquals(2, settings.autoDimPackagesSet.size)
        assertTrue(settings.autoDimPackagesSet.contains("com.chase.sig.android"))

        // 2. Edge Glow preview trigger & stop
        val edgeGlow = app.container.edgeGlowManager
        edgeGlow.triggerGlow(
            isCall = true,
            sourceLabel = "Preview Incoming Call",
            continuousUntilStopped = true,
            forcePreview = true
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(edgeGlow.state.value.isActive)
        assertEquals("CALL", edgeGlow.state.value.triggerType)

        edgeGlow.stopGlow()
        assertFalse(edgeGlow.state.value.isActive)

        // 3. Exclusive Flip-to-DND vibrations (ON = 2x waveform, OFF = 1x one-shot) execute safely
        SystemUtils.triggerFlipDndVibration(app, isTurningOn = true)
        SystemUtils.triggerFlipDndVibration(app, isTurningOn = false)
    }

    @Test
    fun `verify Aqua Dynamics immediately disappears when app or video runs in full screen`() {
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()
        val edgeGlow = app.container.edgeGlowManager
        val fullScreenDetector = app.container.fullScreenDetector

        // Default setting has full-screen auto-hide enabled
        assertTrue(PandaSettings().hideAquaDynamicsInFullScreen)

        // Start active continuous Call Edge Glow
        edgeGlow.triggerGlow(
            isCall = true,
            sourceLabel = "Incoming Call",
            continuousUntilStopped = true,
            forcePreview = true
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(edgeGlow.state.value.isActive)
        assertFalse(edgeGlow.state.value.isSuppressedByFullScreen)

        // Simulate system full-screen / video player hiding status bar -> Aqua Dynamics disappears immediately
        fullScreenDetector.reportSystemOverlayFullScreenSignal(true)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(fullScreenDetector.state.value.isFullScreenActive)
        assertFalse(edgeGlow.state.value.isActive)
        assertTrue(edgeGlow.state.value.isSuppressedByFullScreen)

        // Any incoming notification/call glow while in full screen stays suppressed
        edgeGlow.triggerGlow(
            isCall = false,
            sourceLabel = "Messaging App",
            continuousUntilStopped = false,
            forcePreview = true
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(edgeGlow.state.value.isActive)
        assertTrue(edgeGlow.state.value.isSuppressedByFullScreen)

        // Exiting full screen restores normal Aqua Dynamics readiness
        fullScreenDetector.reportSystemOverlayFullScreenSignal(false)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(fullScreenDetector.state.value.isFullScreenActive)
        assertFalse(edgeGlow.state.value.isSuppressedByFullScreen)

        // In-app full-screen mode also immediately hides Aqua Dynamics
        edgeGlow.triggerGlow(
            isCall = false,
            sourceLabel = "Preview Alert",
            continuousUntilStopped = true,
            forcePreview = true
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(edgeGlow.state.value.isActive)

        fullScreenDetector.setInAppFullScreen(true)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(edgeGlow.state.value.isActive)
        assertTrue(edgeGlow.state.value.isSuppressedByFullScreen)

        fullScreenDetector.setInAppFullScreen(false)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(edgeGlow.state.value.isSuppressedByFullScreen)

        // Picture-in-Picture video mode & host window hidden system bars also immediately hide Aqua Dynamics
        edgeGlow.triggerGlow(
            isCall = true,
            sourceLabel = "Second Call",
            continuousUntilStopped = true,
            forcePreview = true
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(edgeGlow.state.value.isActive)

        fullScreenDetector.setInPictureInPictureMode(true)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(edgeGlow.state.value.isActive)
        assertTrue(edgeGlow.state.value.isSuppressedByFullScreen)

        fullScreenDetector.setInPictureInPictureMode(false)
        fullScreenDetector.setHostWindowSystemBarsHidden(true)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(fullScreenDetector.state.value.isFullScreenActive)
        assertFalse(edgeGlow.state.value.isActive)

        fullScreenDetector.setHostWindowSystemBarsHidden(false)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(fullScreenDetector.state.value.isFullScreenActive)
        assertFalse(edgeGlow.state.value.isSuppressedByFullScreen)
    }

    @Test
    fun `verify Shake to Camera rejects single shake and walking, triggers on double twist, respects cooldown, disable, and screen off`() {
        val recognizer = QuickCaptureRecognizer(
            hasGyroscope = true,
            hasAccelerometer = true
        ).apply {
            enabled = true
            sensitivity = QuickCaptureSensitivity.MEDIUM
            cooldownMs = 3000L
            isScreenInteractive = true
        }

        // 1. Single shake or single wrist twist MUST NOT launch camera
        var t = 1_000L
        assertFalse(recognizer.onAccelerometerSample(t, 16.0f, 9.8f, 1.2f))
        assertFalse(recognizer.onGyroscopeSample(t, 0.2f, 5.4f, 0.3f))       // Twist 1 out
        t += 120L
        assertFalse(recognizer.onGyroscopeSample(t, -0.1f, -4.2f, -0.2f))   // Twist 1 return ONLY (single twist)
        t += 1_200L // Let single twist expire without a second twist
        assertFalse(recognizer.onGyroscopeSample(t, 0.0f, 0.2f, 0.0f))

        // 2. Normal walking (~2 Hz step cadence + gentle arm swing) MUST NOT trigger camera
        for (stepSample in 0 until 200) {
            t += 50L // 10 seconds at 20 Hz
            val phase = (stepSample * 0.05) * 2.0 * PI * 2.0 // 2 Hz step cadence
            val walkAx = (1.8 * sin(phase)).toFloat()
            val walkAy = (9.81 + 3.6 * sin(phase)).toFloat()
            val walkAz = (1.5 * sin(phase * 0.5)).toFloat()
            val armSwingGx = (1.4 * sin(phase * 0.5)).toFloat()
            val armSwingGy = (1.1 * sin(phase * 0.5)).toFloat()
            assertFalse(recognizer.onAccelerometerSample(t, walkAx, walkAy, walkAz))
            assertFalse(recognizer.onGyroscopeSample(t, armSwingGx, armSwingGy, 0.2f))
        }

        // 3. Two deliberate wrist twists MUST trigger Quick Capture
        fun performDeliberateDoubleTwist(startMs: Long): Pair<Boolean, Long> {
            var ts = startMs
            recognizer.onAccelerometerSample(ts, 3.0f, 9.5f, 1.0f)
            assertFalse(recognizer.onGyroscopeSample(ts, 0.3f, 5.6f, 0.4f))    // Twist 1 forward
            ts += 110L
            assertFalse(recognizer.onGyroscopeSample(ts, -0.2f, -4.1f, -0.3f)) // Twist 1 back
            ts += 140L
            assertFalse(recognizer.onGyroscopeSample(ts, 0.3f, 5.5f, 0.4f))    // Twist 2 forward
            ts += 110L
            val fired = recognizer.onGyroscopeSample(ts, -0.2f, -4.3f, -0.3f)  // Twist 2 back -> TRIGGER!
            return fired to ts
        }

        t += 1_500L
        val (firstTriggered, triggerTime) = performDeliberateDoubleTwist(t)
        assertTrue("Expected two deliberate twists to trigger Quick Capture", firstTriggered)

        // 4. Repeated gestures within 3000ms cooldown MUST NOT trigger
        val (duringCooldownTriggered, afterCooldownAttemptTime) = performDeliberateDoubleTwist(triggerTime + 700L)
        assertFalse("Expected gesture inside cooldown window to be ignored", duringCooldownTriggered)

        // Gesture AFTER 3000ms cooldown MUST trigger again
        val (afterCooldownTriggered, secondTriggerTime) = performDeliberateDoubleTwist(triggerTime + 3_200L)
        assertTrue("Expected gesture after cooldown expiry to trigger", afterCooldownTriggered)
        assertTrue(afterCooldownAttemptTime < secondTriggerTime)

        // 5. Disabling the feature MUST immediately stop detection
        recognizer.enabled = false
        val (whileDisabledTriggered, _) = performDeliberateDoubleTwist(secondTriggerTime + 4_000L)
        assertFalse("Expected disabled recognizer to stop detection", whileDisabledTriggered)

        // 6. Screen OFF vs Screen ON behavior
        recognizer.enabled = true
        recognizer.isScreenInteractive = false
        val (whileScreenOffTriggered, _) = performDeliberateDoubleTwist(secondTriggerTime + 8_000L)
        assertFalse("Expected screen-off state to block detection", whileScreenOffTriggered)

        recognizer.isScreenInteractive = true
        val (whenScreenBackOnTriggered, _) = performDeliberateDoubleTwist(secondTriggerTime + 12_000L)
        assertTrue("Expected screen-on state to resume detection", whenScreenBackOnTriggered)

        // Verify Accelerometer-only fallback recognizer also rejects single shake and triggers on 2 lateral twists
        val fallbackRecognizer = QuickCaptureRecognizer(
            hasGyroscope = false,
            hasAccelerometer = true
        ).apply {
            enabled = true
            sensitivity = QuickCaptureSensitivity.MEDIUM
        }
        assertFalse(fallbackRecognizer.onAccelerometerSample(20_000L, 12.5f, 9.8f, 0.5f))
        assertFalse(fallbackRecognizer.onAccelerometerSample(20_110L, -11.5f, 9.8f, -0.4f)) // Single shake stops here
        assertFalse(fallbackRecognizer.onAccelerometerSample(21_500L, 0.2f, 9.8f, 0.1f))

        // Deliberate double lateral twist on fallback
        assertFalse(fallbackRecognizer.onAccelerometerSample(25_000L, 12.2f, 9.6f, 0.5f))
        assertFalse(fallbackRecognizer.onAccelerometerSample(25_110L, -11.2f, 9.6f, -0.5f))
        assertFalse(fallbackRecognizer.onAccelerometerSample(25_250L, 12.0f, 9.6f, 0.5f))
        assertTrue(fallbackRecognizer.onAccelerometerSample(25_360L, -11.4f, 9.6f, -0.5f))
    }

    @Test
    fun `verify QuickCaptureController resolves camera availability and launches standard STILL_IMAGE_CAMERA intent`() {
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()
        val shadowApp = Shadows.shadowOf(app)
        val shadowPm = Shadows.shadowOf(app.packageManager)
        val controller = app.container.quickCaptureController

        // Clear any prior started intents
        while (shadowApp.nextStartedActivity != null) { /* drain */ }

        // When no camera app is installed in ShadowPackageManager, graceful error is reported without crash
        val (noCamOk, noCamMsg) = controller.onQuickCaptureRecognized(isManualTest = true)
        assertFalse(noCamOk)
        assertTrue(noCamMsg.contains("Camera unavailable"))
        assertNull(shadowApp.nextStartedActivity)

        // Register a standard Android Camera Activity handling MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        val cameraComponent = ComponentName("com.android.camera2", "com.android.camera.CameraActivity")
        shadowPm.addActivityIfNotPresent(cameraComponent)
        shadowPm.addIntentFilterForActivity(
            cameraComponent,
            IntentFilter(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        )

        controller.setActivityForeground(true)
        assertTrue(controller.refreshCameraAvailability())

        val (launchedOk, launchMsg) = controller.onQuickCaptureRecognized(isManualTest = false)
        assertTrue(launchedOk)
        assertTrue(launchMsg.contains("com.android.camera2"))

        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("Expected STILL_IMAGE_CAMERA intent to be started", startedIntent)
        assertEquals(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, startedIntent.action)
    }
}

