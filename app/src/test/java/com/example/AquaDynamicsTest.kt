package com.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.automation.AquaActiveCallPhase
import com.example.automation.AquaDisplayMode
import com.example.automation.AquaDynamicsEvent
import com.example.automation.AquaDynamicsGatekeeper
import com.example.automation.AquaEventCategory
import com.example.automation.AquaEventPriority
import com.example.core.ui.HealthCondition
import com.example.data.local.datastore.PandaSettings
import com.example.monitoring.BatterySnapshot
import com.example.monitoring.CpuSnapshot
import com.example.monitoring.NetworkInfoSnapshot
import com.example.monitoring.ThermalLevel
import com.example.monitoring.ThermalSnapshot
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
class AquaDynamicsTest {

    private fun sampleCpu(usage: Float) = CpuSnapshot(
        overallUsagePercent = usage,
        usageSource = "/proc/stat",
        coreCount = 8,
        activeCoreCount = 8,
        socHardware = "Tensor",
        socModel = "SoC",
        primaryAbi = "arm64-v8a",
        supportedAbis = "arm64-v8a",
        cores = emptyList(),
        cpuTemperatureCelsius = 38f,
        loadAverage = "1.2",
        condition = if (usage >= 85f) HealthCondition.WARNING else HealthCondition.NORMAL,
        conditionLabel = if (usage >= 85f) "High Load" else "Normal"
    )

    private fun sampleBattery(
        pct: Int,
        charging: Boolean = false,
        tempC: Float = 31.5f,
        statusLabel: String = if (charging) "Charging" else "Discharging"
    ) = BatterySnapshot(
        percentage = pct,
        temperatureCelsius = tempC,
        voltageMv = 4100,
        currentMicroAmps = 500_000,
        isCharging = charging,
        statusLabel = statusLabel,
        chargingSource = if (charging) "AC Charger" else "On Battery",
        healthLabel = "Good",
        technology = "Li-ion",
        chargeTimeRemainingMs = null,
        condition = HealthCondition.NORMAL,
        conditionSummary = "Nominal",
        chargingStartedAtMs = null
    )

    private fun sampleThermal(level: ThermalLevel) = ThermalSnapshot(
        thermalLevel = level,
        powerManagerStatusSupported = true,
        thermalHeadroom = 0.5f,
        batteryTempCelsius = 34f,
        cpuSensorTempCelsius = 40f
    )

    private fun sampleNet(
        connected: Boolean = true,
        wifi: Boolean = true,
        type: String = "Wi-Fi"
    ) = NetworkInfoSnapshot(
        connectionType = type,
        isConnected = connected,
        isValidatedInternet = connected,
        isMetered = !wifi,
        wifiConnected = wifi,
        cellularConnected = !wifi && connected,
        bluetoothStateLabel = "ON",
        ipv4Address = "192.168.1.24",
        ipv6Address = "::1",
        dnsServers = listOf("8.8.8.8"),
        gateway = "192.168.1.1",
        interfaceName = "wlan0",
        downstreamBandwidthKbps = 150_000,
        upstreamBandwidthKbps = 45_000,
        interfacesSummary = emptyList()
    )

    @Test
    fun `verify Aqua Dynamics never activates on startup or initial baseline telemetry`() {
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()
        val manager = app.container.aquaDynamicsManager
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // 1. Simply starting PandaNex leaves Aqua Dynamics completely hidden
        assertEquals(AquaDisplayMode.HIDDEN, manager.state.value.displayMode)
        assertNull(manager.state.value.activeEvent)

        // 2. First telemetry sample (even if CPU or battery already happens to be low/warm at startup)
        // is captured silently as baseline — NEVER triggers unexpected startup popup!
        manager.onLiveTelemetrySampled(
            cpu = sampleCpu(usage = 92f),
            battery = sampleBattery(pct = 15, charging = false, tempC = 44f),
            thermal = sampleThermal(ThermalLevel.MODERATE),
            network = sampleNet(connected = true, wifi = true)
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AquaDisplayMode.HIDDEN, manager.state.value.displayMode)
        assertNull(manager.state.value.activeEvent)

        // 3. Once conditions normalize and then a GENUINE new transition occurs, Aqua Dynamics activates
        manager.onLiveTelemetrySampled(
            cpu = sampleCpu(usage = 24f),
            battery = sampleBattery(pct = 55, charging = false, tempC = 32f),
            thermal = sampleThermal(ThermalLevel.NONE),
            network = sampleNet(connected = true, wifi = true)
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AquaDisplayMode.HIDDEN, manager.state.value.displayMode)

        // Now CPU genuinely spikes above threshold -> Aqua Dynamics appears!
        manager.onLiveTelemetrySampled(
            cpu = sampleCpu(usage = 96f),
            battery = sampleBattery(pct = 55, charging = false, tempC = 32f),
            thermal = sampleThermal(ThermalLevel.NONE),
            network = sampleNet(connected = true, wifi = true)
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AquaDisplayMode.EXPANDED, manager.state.value.displayMode)
        assertNotNull(manager.state.value.activeEvent)
        assertTrue(manager.state.value.activeEvent!!.title.contains("High CPU Usage"))

        // Clean up for subsequent Robolectric tests sharing process
        manager.dismissActiveCapsule()
    }

    @Test
    fun `verify active calls immediately hide Aqua Dynamics, block events during call, and handle Missed Call and Post-Call Summary after call ends`() {
        val gatekeeper = AquaDynamicsGatekeeper(
            PandaSettings(
                aquaDynamicsEnabled = true,
                aquaCatPhoneEnabled = true,
                aquaPostCallSummaryEnabled = true
            )
        )

        var now = 10_000L
        // 1. Show an active Aqua Dynamics capsule first
        val netEvent = AquaDynamicsEvent(
            id = "wifi_1",
            signatureKey = "WIFI_CONNECTED_HOME",
            conditionGroupKey = "WIFI",
            category = AquaEventCategory.NETWORK,
            priority = AquaEventPriority.NORMAL,
            title = "Wi-Fi Connected",
            subtitle = "Validated Internet",
            badgeText = "WI-FI",
            iconType = "WIFI",
            timestampMs = now
        )
        assertTrue(gatekeeper.submitEvent(netEvent, nowMs = now))
        assertEquals(AquaDisplayMode.EXPANDED, gatekeeper.currentSnapshot().displayMode)

        // 2. Incoming call begins -> Aqua Dynamics immediately hides so system dialer is untouched
        now += 1_000L
        val ringingSnap = gatekeeper.onCallPhaseChanged(AquaActiveCallPhase.INCOMING_RINGING, nowMs = now)
        assertTrue(ringingSnap.isSuppressedByCall)
        assertEquals(AquaDisplayMode.HIDDEN, ringingSnap.displayMode)
        assertNull(ringingSnap.activeEvent)

        // 3. Even a CRITICAL event during an active call MUST NOT appear over the call screen
        now += 500L
        val criticalDuringCall = AquaDynamicsEvent(
            id = "crit_call",
            signatureKey = "THERM_CRIT_DURING_CALL",
            conditionGroupKey = "THERM",
            category = AquaEventCategory.DEVICE_PERFORMANCE,
            priority = AquaEventPriority.CRITICAL,
            title = "Heat Guard Critical",
            subtitle = "Device hot",
            badgeText = "HOT",
            iconType = "THERMAL",
            timestampMs = now
        )
        assertFalse(gatekeeper.submitEvent(criticalDuringCall, nowMs = now))
        assertEquals(AquaDisplayMode.HIDDEN, gatekeeper.currentSnapshot().displayMode)

        // 4. Ringing ends WITHOUT answering -> Call returns to IDLE -> Normal functionality restored & Missed Call surfaced
        now += 4_000L
        val afterMissedSnap = gatekeeper.onCallPhaseChanged(AquaActiveCallPhase.IDLE, nowMs = now)
        assertFalse(afterMissedSnap.isSuppressedByCall)
        assertEquals(AquaDisplayMode.EXPANDED, afterMissedSnap.displayMode)
        assertEquals("Missed Call", afterMissedSnap.activeEvent?.title)

        // 5. Outgoing / Answered Ongoing Call -> Hides immediately while active, shows Post-Call Summary ONLY after call ends
        now += 5_000L
        val ongoingSnap = gatekeeper.onCallPhaseChanged(AquaActiveCallPhase.OUTGOING_OR_ONGOING, nowMs = now)
        assertTrue(ongoingSnap.isSuppressedByCall)
        assertEquals(AquaDisplayMode.HIDDEN, ongoingSnap.displayMode)
        assertNull(ongoingSnap.activeEvent)

        now += 65_000L // 1m 5s call duration
        val postCallSnap = gatekeeper.onCallPhaseChanged(AquaActiveCallPhase.IDLE, nowMs = now)
        assertFalse(postCallSnap.isSuppressedByCall)
        assertEquals(AquaDisplayMode.EXPANDED, postCallSnap.displayMode)
        assertEquals("Call Ended", postCallSnap.activeEvent?.title)
        assertEquals("1m 5s", postCallSnap.activeEvent?.badgeText)

        // 6. Verify AudioManager IN_CALL mode on AquaDynamicsManager also suppresses system & in-app overlays
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val aquaManager = app.container.aquaDynamicsManager

        aquaManager.triggerInteractivePreview()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AquaDisplayMode.EXPANDED, aquaManager.state.value.displayMode)

        audioManager.mode = AudioManager.MODE_IN_CALL
        aquaManager.refreshEffectiveCallState()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(aquaManager.state.value.isSuppressedByCall)
        assertEquals(AquaDisplayMode.HIDDEN, aquaManager.state.value.displayMode)

        audioManager.mode = AudioManager.MODE_NORMAL
        aquaManager.refreshEffectiveCallState()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(aquaManager.state.value.isSuppressedByCall)
        aquaManager.dismissActiveCapsule()
    }

    @Test
    fun `verify dismissed events never reopen until a genuinely new event or cleared condition occurs`() {
        val gatekeeper = AquaDynamicsGatekeeper(
            PandaSettings(
                aquaDynamicsEnabled = true,
                aquaStrictDismissLock = true,
                aquaEventCooldownMs = 3_000L
            )
        )

        var now = 20_000L
        val lowBatEvent = AquaDynamicsEvent(
            id = "bat_1",
            signatureKey = "BATTERY_LOW_20",
            conditionGroupKey = "BATTERY_LOW",
            category = AquaEventCategory.DEVICE_PERFORMANCE,
            priority = AquaEventPriority.IMPORTANT,
            title = "Low Battery (18%)",
            subtitle = "Connect charger",
            badgeText = "18%",
            iconType = "BATTERY",
            timestampMs = now
        )
        assertTrue(gatekeeper.submitEvent(lowBatEvent, nowMs = now))
        assertEquals(AquaDisplayMode.EXPANDED, gatekeeper.currentSnapshot().displayMode)

        // User dismisses the capsule
        val (afterDismiss, dismissedSet) = gatekeeper.dismissActiveEvent()
        assertEquals(AquaDisplayMode.HIDDEN, afterDismiss.displayMode)
        assertTrue(dismissedSet.contains("BATTERY_LOW_20"))

        // Even well past cooldown (60 seconds later), the dismissed event signature MUST NOT reopen!
        now += 60_000L
        assertFalse(gatekeeper.submitEvent(lowBatEvent.copy(id = "bat_repeat", timestampMs = now), nowMs = now))
        assertEquals(AquaDisplayMode.HIDDEN, gatekeeper.currentSnapshot().displayMode)

        // A genuinely NEW event (e.g., Headphones connected or Critical 10% battery) DOES open Aqua Dynamics
        now += 5_000L
        val audioEvent = AquaDynamicsEvent(
            id = "aud_1",
            signatureKey = "AUDIO_ATTACHED_PIXEL_BUDS",
            conditionGroupKey = "AUDIO_ACCESSORY",
            category = AquaEventCategory.DEVICE_INTERACTIONS,
            priority = AquaEventPriority.NORMAL,
            title = "Audio Connected",
            subtitle = "Bluetooth Audio ready",
            badgeText = "AUDIO",
            iconType = "AUDIO",
            timestampMs = now
        )
        assertTrue(gatekeeper.submitEvent(audioEvent, nowMs = now))
        assertEquals(AquaDisplayMode.EXPANDED, gatekeeper.currentSnapshot().displayMode)
        assertEquals("Audio Connected", gatekeeper.currentSnapshot().activeEvent?.title)

        // If user plugs in charger (clearing BATTERY_LOW) and later unplugs & drops low again, it can trigger anew
        gatekeeper.markConditionCleared("BATTERY_LOW")
        now += 15_000L
        assertTrue(gatekeeper.submitEvent(lowBatEvent.copy(id = "bat_new_cycle", timestampMs = now), nowMs = now))
    }

    @Test
    fun `verify interactive drag-to-camera minimize, tap or drag restore, scroll touch-slop guard, and camera cutout clearance`() {
        val gatekeeper = AquaDynamicsGatekeeper(PandaSettings(aquaDynamicsEnabled = true))

        val event = AquaDynamicsEvent(
            id = "chg_1",
            signatureKey = "POWER_CONNECTED_AC",
            conditionGroupKey = "POWER_CABLE",
            category = AquaEventCategory.DEVICE_INTERACTIONS,
            priority = AquaEventPriority.NORMAL,
            title = "Charging Started (64%)",
            subtitle = "AC Fast Charger connected",
            badgeText = "64%",
            iconType = "CHARGING",
            timestampMs = 1_000L
        )
        assertTrue(gatekeeper.submitEvent(event, nowMs = 1_000L))
        assertEquals(AquaDisplayMode.EXPANDED, gatekeeper.currentSnapshot().displayMode)
        assertEquals(1f, gatekeeper.currentSnapshot().interactiveExpandFraction, 0.001f)

        // 1. Small accidental finger jitter (< touchSlopPx = 14f) does NOT move or minimize capsule
        val jitterSnap = gatekeeper.onInteractiveVerticalDrag(totalDragDyPx = -9f, fullTravelPx = 120f, touchSlopPx = 14f)
        assertEquals(AquaDisplayMode.EXPANDED, jitterSnap.displayMode)
        assertEquals(1f, jitterSnap.interactiveExpandFraction, 0.001f)

        // 2. Deliberate upward drag toward camera area smoothly interpolates fraction and settles into MINIMIZED black pill
        val midDragUp = gatekeeper.onInteractiveVerticalDrag(totalDragDyPx = -85f, fullTravelPx = 120f, touchSlopPx = 14f)
        assertTrue(midDragUp.interactiveExpandFraction in 0.1f..0.6f)

        val settledMin = gatekeeper.settleInteractiveDrag(velocityY = -100f)
        assertEquals(AquaDisplayMode.MINIMIZED, settledMin.displayMode)
        assertEquals(0f, settledMin.interactiveExpandFraction, 0.001f)
        assertNotNull(settledMin.activeEvent) // Still retains active event inside compact camera pill

        // 3. Tapping minimized black capsule restores EXPANDED view
        val tappedRestore = gatekeeper.expandFromMinimized()
        assertEquals(AquaDisplayMode.EXPANDED, tappedRestore.displayMode)
        assertEquals(1f, tappedRestore.interactiveExpandFraction, 0.001f)

        // 4. Fast upward fling velocity minimizes immediately; downward drag away from camera restores EXPANDED
        gatekeeper.onInteractiveVerticalDrag(totalDragDyPx = -25f, fullTravelPx = 120f, touchSlopPx = 14f)
        val flungMin = gatekeeper.settleInteractiveDrag(velocityY = -650f)
        assertEquals(AquaDisplayMode.MINIMIZED, flungMin.displayMode)

        val dragDown = gatekeeper.onInteractiveVerticalDrag(totalDragDyPx = 95f, fullTravelPx = 120f, touchSlopPx = 14f)
        assertTrue(dragDown.interactiveExpandFraction > 0.5f)
        val settledExp = gatekeeper.settleInteractiveDrag(velocityY = 0f)
        assertEquals(AquaDisplayMode.EXPANDED, settledExp.displayMode)
        assertEquals(1f, settledExp.interactiveExpandFraction, 0.001f)

        // 5. Verify camera cutout anchor strictly clears physical center punch-hole lens
        val punchHoleRect = Rect(490, 0, 590, 88) // 1080px wide screen with 88px tall top-center camera cutout
        val anchor = AquaDynamicsGatekeeper.computeCameraSafeAnchor(
            screenWidthPx = 1080,
            cutoutRects = listOf(punchHoleRect),
            safeInsetTopPx = 88,
            density = 2.75f
        )
        assertTrue("Anchor must recognize center camera cutout", anchor.avoidsCenterCutout)
        assertTrue(
            "Capsule top (${anchor.topMarginPx}px) must sit below camera cutout bottom (${punchHoleRect.bottom}px)",
            anchor.topMarginPx > punchHoleRect.bottom
        )
    }

    @Test
    fun `verify category toggles, priority filter, cooldown deduplication, critical preemption, and manual disable`() {
        val gatekeeper = AquaDynamicsGatekeeper(
            PandaSettings(
                aquaDynamicsEnabled = true,
                aquaCatNetworkEnabled = false, // Network category turned OFF by user
                aquaCatPerformanceEnabled = true,
                aquaPriorityFilter = "IMPORTANT_ONLY",
                aquaEventCooldownMs = 6_000L
            )
        )

        var now = 50_000L
        // 1. Disabled category (Network) is rejected
        val wifiEvt = AquaDynamicsEvent(
            id = "n1",
            signatureKey = "WIFI_OFF",
            conditionGroupKey = "NET",
            category = AquaEventCategory.NETWORK,
            priority = AquaEventPriority.IMPORTANT,
            title = "Offline",
            subtitle = "Lost Wi-Fi",
            badgeText = "OFFLINE",
            iconType = "NETWORK",
            timestampMs = now
        )
        assertFalse(gatekeeper.submitEvent(wifiEvt, nowMs = now))

        // 2. Normal priority is filtered out when user selected IMPORTANT_ONLY
        val normalPerfEvt = AquaDynamicsEvent(
            id = "p_norm",
            signatureKey = "CHG_FULL",
            conditionGroupKey = "CHG",
            category = AquaEventCategory.DEVICE_PERFORMANCE,
            priority = AquaEventPriority.NORMAL,
            title = "Charging Completed",
            subtitle = "100%",
            badgeText = "100%",
            iconType = "CHARGING",
            timestampMs = now
        )
        assertFalse(gatekeeper.submitEvent(normalPerfEvt, nowMs = now))

        // 3. Important event in enabled category is accepted
        val impPerfEvt = AquaDynamicsEvent(
            id = "p_imp",
            signatureKey = "CPU_HIGH_88",
            conditionGroupKey = "CPU_GROUP",
            category = AquaEventCategory.DEVICE_PERFORMANCE,
            priority = AquaEventPriority.IMPORTANT,
            title = "High CPU Usage (88%)",
            subtitle = "Sustained load",
            badgeText = "88%",
            iconType = "CPU",
            timestampMs = now
        )
        assertTrue(gatekeeper.submitEvent(impPerfEvt, nowMs = now))

        // 4. Another IMPORTANT event in the same condition group inside 6000ms cooldown is blocked (anti-spam)
        now += 1_500L
        assertFalse(
            gatekeeper.submitEvent(
                impPerfEvt.copy(id = "p_imp_2", signatureKey = "CPU_HIGH_91", timestampMs = now),
                nowMs = now
            )
        )

        // 5. Escalation to CRITICAL priority preempts cooldown immediately!
        now += 500L
        val critPerfEvt = impPerfEvt.copy(
            id = "p_crit",
            signatureKey = "CPU_CRITICAL_99",
            priority = AquaEventPriority.CRITICAL,
            title = "Critical CPU Overload (99%)",
            timestampMs = now
        )
        assertTrue(gatekeeper.submitEvent(critPerfEvt, nowMs = now))
        assertEquals("Critical CPU Overload (99%)", gatekeeper.currentSnapshot().activeEvent?.title)

        // 6. Manually disabling Aqua Dynamics immediately closes visible capsule and blocks future triggers
        val disabledSnap = gatekeeper.updateSettings(PandaSettings(aquaDynamicsEnabled = false))
        assertFalse(disabledSnap.isEnabled)
        assertEquals(AquaDisplayMode.HIDDEN, disabledSnap.displayMode)
        assertNull(disabledSnap.activeEvent)

        now += 20_000L
        assertFalse(
            gatekeeper.submitEvent(
                critPerfEvt.copy(id = "p_while_off", signatureKey = "THERM_SHUTDOWN", timestampMs = now),
                nowMs = now
            )
        )
    }

    @Test
    fun `verify power cable broadcast and screen rotation events trigger Aqua Dynamics via real Android callbacks`() {
        val app = ApplicationProvider.getApplicationContext<PandaNexApplication>()
        val manager = app.container.aquaDynamicsManager
        manager.dismissActiveCapsule()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // 1. Broadcast ACTION_POWER_CONNECTED triggers Charging Started event
        app.sendBroadcast(Intent(Intent.ACTION_POWER_CONNECTED))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AquaDisplayMode.EXPANDED, manager.state.value.displayMode)
        assertTrue(manager.state.value.activeEvent?.title?.contains("Charging") == true)

        manager.dismissActiveCapsule()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // 2. Configuration change to Landscape triggers Screen Rotation event
        val landscapeConfig = Configuration(app.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        app.onConfigurationChanged(landscapeConfig)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AquaDisplayMode.EXPANDED, manager.state.value.displayMode)
        assertEquals("Screen Rotated", manager.state.value.activeEvent?.title)
        assertTrue(manager.state.value.activeEvent?.subtitle?.contains("Landscape") == true)
        assertEquals("LAND", manager.state.value.activeEvent?.badgeText)

        // Restore Portrait and clean up
        val portraitConfig = Configuration(app.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        app.onConfigurationChanged(portraitConfig)
        manager.dismissActiveCapsule()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}
