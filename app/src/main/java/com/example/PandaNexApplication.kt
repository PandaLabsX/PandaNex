package com.example

import android.app.Application
import com.example.automation.ActionManager
import com.example.automation.AquaDynamicsManager
import com.example.automation.AquaEventCategory
import com.example.automation.AquaEventPriority
import com.example.automation.AutoDimController
import com.example.automation.AutomationEngine
import com.example.automation.EdgeGlowManager
import com.example.automation.FullScreenDetector
import com.example.automation.QuickCaptureController
import com.example.data.local.database.PandaDao
import com.example.data.local.database.PandaDatabase
import com.example.data.local.datastore.PandaPreferencesRepository
import com.example.monitoring.BatteryMonitor
import com.example.monitoring.CpuMonitor
import com.example.monitoring.NetworkMonitor
import com.example.monitoring.RamMonitor
import com.example.monitoring.StorageMonitor
import com.example.monitoring.ThermalMonitor
import com.example.notifications.AlertEngine
import com.example.notifications.NotificationChannels

class PandaAppContainer(context: Application) {
    val database: PandaDatabase = PandaDatabase.getInstance(context)
    val dao: PandaDao = database.pandaDao()
    val preferencesRepository = PandaPreferencesRepository(context)

    val cpuMonitor = CpuMonitor()
    val ramMonitor = RamMonitor(context)
    val storageMonitor = StorageMonitor()
    val batteryMonitor = BatteryMonitor(context)
    val thermalMonitor = ThermalMonitor(context, batteryMonitor, cpuMonitor)
    val networkMonitor = NetworkMonitor(context)

    val fullScreenDetector = FullScreenDetector(
        context = context,
        preferencesRepository = preferencesRepository
    )

    val edgeGlowManager = EdgeGlowManager(
        context = context,
        preferencesRepository = preferencesRepository,
        fullScreenDetector = fullScreenDetector
    )

    val aquaDynamicsManager = AquaDynamicsManager(
        context = context,
        preferencesRepository = preferencesRepository,
        fullScreenDetector = fullScreenDetector
    )

    val autoDimController = AutoDimController(
        context = context,
        preferencesRepository = preferencesRepository,
        fullScreenDetector = fullScreenDetector
    )

    val alertEngine = AlertEngine(
        context = context,
        dao = dao,
        preferencesRepository = preferencesRepository
    ).apply {
        onNotificationDispatched = { title ->
            edgeGlowManager.triggerGlow(
                isCall = false,
                sourceLabel = "PandaNex: $title",
                continuousUntilStopped = false,
                forcePreview = false
            )
        }
        onStructuredAlertDispatched = { dedupKey, category, severity, title, message, statusDetail ->
            // CPU, BATTERY, THERMAL, and NETWORK transitions are handled directly with rich
            // hardware metrics by aquaDynamicsManager.onLiveTelemetrySampled & networkCallback.
            when (category) {
                "SMART_ACTION" -> {
                    aquaDynamicsManager.onSupportedSensorOrSystemEvent(
                        category = AquaEventCategory.DEVICE_INTERACTIONS,
                        conditionGroupKey = "SMART_ACTION_$dedupKey",
                        signatureKey = "SMART_${dedupKey}_$statusDetail",
                        priority = AquaEventPriority.NORMAL,
                        title = title,
                        subtitle = message,
                        badgeText = statusDetail.take(8).uppercase(),
                        iconType = "SENSOR",
                        accentColorHex = 0xFF00E5FFL
                    )
                }
                "DIAGNOSTIC", "RAM", "STORAGE", "SYSTEM" -> {
                    val prio = when (severity) {
                        "CRITICAL" -> AquaEventPriority.CRITICAL
                        "WARNING" -> AquaEventPriority.IMPORTANT
                        else -> AquaEventPriority.NORMAL
                    }
                    val accent = when (severity) {
                        "CRITICAL" -> 0xFFFF5252L
                        "WARNING" -> 0xFFFFB300L
                        else -> 0xFF00E5FFL
                    }
                    aquaDynamicsManager.onSupportedSensorOrSystemEvent(
                        category = AquaEventCategory.PANDANEX_SYSTEM,
                        conditionGroupKey = "SYS_$category",
                        signatureKey = "SYS_${dedupKey}_$statusDetail",
                        priority = prio,
                        title = title,
                        subtitle = message,
                        badgeText = statusDetail.take(8).uppercase(),
                        iconType = if (category == "DIAGNOSTIC") "NETWORK" else "ALERT",
                        accentColorHex = accent
                    )
                }
            }
        }
    }

    val actionManager = ActionManager(
        context = context,
        alertEngine = alertEngine
    )

    val automationEngine = AutomationEngine(
        context = context,
        dao = dao,
        preferencesRepository = preferencesRepository,
        actionManager = actionManager,
        batteryMonitor = batteryMonitor,
        networkMonitor = networkMonitor,
        alertEngine = alertEngine
    )

    val displayActionManager = com.example.automation.DisplayActionManager(context)
    val dimmedAppManager = autoDimController

    val smartGestureManager = com.example.automation.SmartGestureManager(
        context = context,
        dao = dao,
        preferencesRepository = preferencesRepository,
        displayActionManager = displayActionManager,
        dimmedAppManager = dimmedAppManager,
        fullScreenDetector = fullScreenDetector,
        aquaDynamicsManager = aquaDynamicsManager
    )

    val quickCaptureController = QuickCaptureController(
        context = context,
        dao = dao,
        preferencesRepository = preferencesRepository,
        alertEngine = alertEngine
    ).apply {
        onGestureTriggered = { cameraPkg ->
            aquaDynamicsManager.onSupportedSensorOrSystemEvent(
                category = AquaEventCategory.DEVICE_INTERACTIONS,
                conditionGroupKey = "QUICK_CAPTURE_GESTURE",
                signatureKey = "QUICK_CAPTURE_${System.currentTimeMillis() / 3000L}",
                priority = AquaEventPriority.NORMAL,
                title = "Quick Capture Detected",
                subtitle = "Double-twist opened Camera ($cameraPkg)",
                badgeText = "CAMERA",
                iconType = "SENSOR",
                accentColorHex = 0xFF00E5FFL
            )
        }
    }
}

class PandaNexApplication : Application() {
    lateinit var container: PandaAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)
        container = PandaAppContainer(this)
    }
}
