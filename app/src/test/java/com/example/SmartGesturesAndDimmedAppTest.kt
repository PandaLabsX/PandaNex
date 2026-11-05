package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.automation.FlatSurfacePlacement
import com.example.automation.GestureSensitivity
import com.example.automation.NearEarAnswerEvaluator
import com.example.automation.PickUpToSilenceRecognizer
import com.example.automation.PutDownToSleepRecognizer
import com.example.automation.RaiseToWakeRecognizer
import com.example.automation.ScreenSaverMotionRecognizer
import com.example.data.local.datastore.PandaSettings
import com.example.feature.screensaver.ScreenSaverStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartGesturesAndDimmedAppTest {

    @Test
    fun raiseToWake_triggersOnRestingLiftWhenScreenOff_andIgnoresActiveOrPocket() {
        val recognizer = RaiseToWakeRecognizer(hasGyroscope = true).apply {
            enabled = true
            sensitivity = GestureSensitivity.MEDIUM
            cooldownMs = 3500L
            isDisplayInteractive = false
            proximityNear = false
        }

        // 1. Establish flat resting baseline for > 260ms
        assertFalse(recognizer.onAccelerometer(1000L, 0.1f, 0.2f, 9.80f))
        assertFalse(recognizer.onAccelerometer(1300L, 0.1f, 0.2f, 9.80f))

        // 2. Lift impulse + gyroscope rotation into viewing posture
        recognizer.onGyroscope(1450L, 1.15f, 0.2f, 0.1f)
        assertFalse(recognizer.onAccelerometer(1460L, 0.3f, 6.8f, 8.2f))
        assertTrue(
            "Expected Raise to Wake after sustained viewing posture",
            recognizer.onAccelerometer(1600L, 0.3f, 6.8f, 7.2f)
        )

        // 3. Cooldown prevents immediate re-trigger
        assertFalse(recognizer.onAccelerometer(2000L, 0.1f, 0.2f, 9.80f))
        assertFalse(recognizer.onAccelerometer(2350L, 0.3f, 6.8f, 7.2f))

        // 4. Pocket guard blocks trigger even after cooldown
        recognizer.proximityNear = true
        assertFalse(recognizer.onAccelerometer(6000L, 0.1f, 0.2f, 9.80f))
        recognizer.onGyroscope(6400L, 1.2f, 0.1f, 0.1f)
        assertFalse(recognizer.onAccelerometer(6550L, 0.3f, 6.8f, 7.2f))
    }

    @Test
    fun pickUpToSilence_ignoresDeskVibrationBuzz_andTriggersOnlyOnGenuineCallPickup() {
        val recognizer = PickUpToSilenceRecognizer().apply {
            enabled = true
            sensitivity = GestureSensitivity.MEDIUM
            isCallRinging = false
        }

        // Should never trigger when no incoming call is ringing
        assertFalse(recognizer.onAccelerometer(500L, 0f, 7.5f, 6.5f))

        // Start incoming call on flat table
        recognizer.isCallRinging = true
        assertFalse(recognizer.onAccelerometer(1000L, 0.1f, 0.1f, 9.81f))
        assertFalse(recognizer.onAccelerometer(1200L, -0.1f, 0.15f, 9.79f))

        // Simulate strong symmetric call vibrator buzz on flat desk (< 5 deg tilt delta)
        assertFalse(recognizer.onAccelerometer(1350L, 0.35f, -0.30f, 11.2f))
        assertFalse(recognizer.onAccelerometer(1520L, -0.30f, 0.25f, 10.9f))

        // Genuine pickup lifting phone to 45-degree angle
        assertFalse(recognizer.onAccelerometer(1700L, 0.4f, 6.9f, 6.9f))
        assertTrue(
            "Expected Pick Up to Silence to fire once lifted during ringing",
            recognizer.onAccelerometer(1880L, 0.4f, 7.1f, 6.7f)
        )

        // Should not re-trigger repeatedly during the same call
        assertFalse(recognizer.onAccelerometer(2100L, 0.4f, 7.4f, 6.4f))
    }

    @Test
    fun proximityCallAnswer_enforcesRingingUprightPosturePocketClearAndHeadsetPause() {
        val evaluator = NearEarAnswerEvaluator().apply {
            enabled = true
            confirmationDelayMs = 800L
        }

        // 1. Unrelated proximity cover when not ringing does nothing
        assertFalse(evaluator.onProximityChanged(500L, isNear = true))

        // 2. Call starts while phone is ALREADY in pocket (proximityNear = true)
        evaluator.onCallRingingChanged(ringing = true, currentProximityNear = true)
        evaluator.onAccelerometerSample(1000L, 0.5f, 8.8f, 3.0f)
        assertFalse("Must not auto-answer while still inside pocket", evaluator.checkConfirmationElapsed(2500L))

        // 3. User pulls phone out of pocket (isNear = false) -> pocket lock clears
        assertFalse(evaluator.onProximityChanged(2600L, isNear = false))

        // 4. Phone placed flat on table & hand waves over proximity sensor -> rejected!
        evaluator.onAccelerometerSample(2800L, 0.1f, 0.2f, 9.80f)
        assertFalse(evaluator.onProximityChanged(2850L, isNear = true))
        assertFalse("Must reject hand covering flat phone on table", evaluator.checkConfirmationElapsed(4000L))
        evaluator.onProximityChanged(4100L, isNear = false)

        // 5. External Bluetooth / Android Auto route pauses auto-answer
        evaluator.externalAudioRouteActive = true
        evaluator.onAccelerometerSample(4300L, 0.8f, 8.7f, 3.2f)
        assertFalse(evaluator.onProximityChanged(4350L, isNear = true))
        assertFalse("Must pause when Bluetooth/Car audio is active", evaluator.checkConfirmationElapsed(5500L))

        // 6. Disconnect headset, hold upright near ear for >= 800ms -> answers!
        evaluator.externalAudioRouteActive = false
        evaluator.onProximityChanged(5600L, isNear = false)
        evaluator.onAccelerometerSample(5700L, 0.8f, 8.7f, 3.2f)
        assertFalse(evaluator.onProximityChanged(5750L, isNear = true))
        assertFalse("Should wait full confirmation delay", evaluator.checkConfirmationElapsed(6300L))
        assertTrue("Expected call answer after 800ms upright near-ear hold", evaluator.checkConfirmationElapsed(6600L))
    }

    @Test
    fun putDownToSleep_requiresHandheldFirst_detectsFlatSurface_andBlocksPocket() {
        val recognizer = PutDownToSleepRecognizer(hasGyroscope = true).apply {
            enabled = true
            sensitivity = GestureSensitivity.MEDIUM
            stabilityDelayMs = 1200L
            cooldownMs = 5000L
            isDisplayInteractive = true
        }

        // 1. Already lying flat without prior handheld activity -> never triggers
        assertEquals(FlatSurfacePlacement.NONE, recognizer.onAccelerometer(1000L, 0.0f, 0.1f, 9.81f))
        assertEquals(FlatSurfacePlacement.NONE, recognizer.onAccelerometer(2500L, 0.0f, 0.1f, 9.81f))

        // 2. Active handheld tilt
        recognizer.onGyroscope(0.7f, 0.2f, 0.1f)
        recognizer.onAccelerometer(3000L, 1.2f, 6.5f, 7.0f)
        recognizer.onAccelerometer(3200L, 1.0f, 6.2f, 7.3f)
        assertTrue(recognizer.wasHandheldActive)

        // 3. Screen-up inside pocket (proximityNear = true) -> blocked!
        recognizer.onGyroscope(0.01f, 0.01f, 0.01f)
        recognizer.proximityNear = true
        assertEquals(FlatSurfacePlacement.NONE, recognizer.onAccelerometer(3500L, 0.1f, 0.1f, 9.80f))
        assertEquals(FlatSurfacePlacement.NONE, recognizer.onAccelerometer(5000L, 0.1f, 0.1f, 9.80f))

        // 4. Uncovered flat desk placement -> triggers after 1200ms stability delay
        recognizer.proximityNear = false
        assertEquals(FlatSurfacePlacement.NONE, recognizer.onAccelerometer(5200L, 0.05f, -0.05f, 9.81f))
        assertEquals(FlatSurfacePlacement.NONE, recognizer.onAccelerometer(6000L, 0.05f, -0.05f, 9.81f))
        assertEquals(
            FlatSurfacePlacement.SCREEN_UP,
            recognizer.onAccelerometer(6450L, 0.05f, -0.05f, 9.81f)
        )
    }

    @Test
    fun screenSaverMotion_andAllSevenStyles_behaveDeterministically() {
        assertEquals(7, ScreenSaverStyle.entries.size)
        ScreenSaverStyle.entries.forEach { style ->
            assertEquals(style, ScreenSaverStyle.fromCode(style.code))
        }

        val recognizer = ScreenSaverMotionRecognizer(hasGyroscope = true).apply {
            enabled = true
            sensitivity = GestureSensitivity.MEDIUM
            isDisplayInteractive = false
            proximityNear = false
        }

        // Prime stationary screen-off state
        assertFalse(recognizer.onAccelerometer(1000L, 0f, 0f, 9.81f))
        assertFalse(recognizer.onAccelerometer(1300L, 0f, 0f, 9.81f))

        // Tiny table micro-vibration does not trigger
        recognizer.onGyroscope(0.05f, 0.02f, 0.01f)
        assertFalse(recognizer.onAccelerometer(1500L, 0.1f, 0.1f, 10.1f))

        // Deliberate movement/pickup triggers ambient screen saver
        recognizer.onGyroscope(0.95f, 0.2f, 0.1f)
        assertFalse(recognizer.onAccelerometer(1700L, 1.5f, 3.2f, 11.9f))
        assertTrue(recognizer.onAccelerometer(1850L, 1.4f, 4.0f, 11.8f))
    }

    @Test
    fun dimmedAppManager_bypassesSystemSecurityDialogs_andRespectsPerAppRules() {
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()
        val dimmedManager = app.container.dimmedAppManager

        assertTrue(dimmedManager.isSystemDialogOrProtectedPackage("com.google.android.permissioncontroller"))
        assertTrue(dimmedManager.isSystemDialogOrProtectedPackage("com.android.packageinstaller"))
        assertTrue(dimmedManager.isSystemDialogOrProtectedPackage("com.android.settings"))
        assertFalse(dimmedManager.isSystemDialogOrProtectedPackage("com.example.mobilebanking"))

        val settings = PandaSettings(
            autoDimAppsEnabled = true,
            autoDimIntensityPercent = 65,
            autoDimPackagesCsv = "com.bank.one,com.bank.two,com.wallet.three",
            autoDimDisabledRulesCsv = "com.bank.two"
        )
        assertEquals(setOf("com.bank.one", "com.bank.two", "com.wallet.three"), settings.autoDimPackagesSet)
        assertEquals(setOf("com.bank.one", "com.wallet.three"), settings.activeAutoDimPackagesSet)
    }

    @Test
    fun autoDimController_previewAndPauseResume_behaveProperly() {
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()
        val dimmedManager = app.container.dimmedAppManager

        // Initial state is not dimmed
        assertFalse(dimmedManager.status.value.isCurrentlyDimmed)

        // Trigger in-app preview
        dimmedManager.triggerPreviewDim(durationMs = 2000L)
        assertTrue(dimmedManager.status.value.isPreviewActive)
        assertTrue(dimmedManager.status.value.isCurrentlyDimmed)

        // Restore normal brightness
        dimmedManager.restoreNormalBrightness("Test restore")
        assertFalse(dimmedManager.status.value.isCurrentlyDimmed)
        assertFalse(dimmedManager.status.value.isPreviewActive)

        // Temporary pause
        dimmedManager.pauseDimmingTemporarily(durationMinutes = 5)
        // Resume
        dimmedManager.resumeDimmingNow()
        assertFalse(dimmedManager.status.value.isTemporarilyPaused)
    }
}
