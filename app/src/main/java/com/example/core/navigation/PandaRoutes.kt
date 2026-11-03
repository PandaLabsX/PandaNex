package com.example.core.navigation

import kotlinx.serialization.Serializable

sealed interface PandaRoute {
    // Bottom Navigation Top-Level Destinations
    @Serializable data object Home : PandaRoute
    @Serializable data object MonitorHub : PandaRoute
    @Serializable data object ToolsHub : PandaRoute
    @Serializable data object SmartActions : PandaRoute
    @Serializable data object Settings : PandaRoute

    // 16 Core Monitoring & Diagnostic Modules
    @Serializable data object CpuMonitor : PandaRoute            // Module 01
    @Serializable data object BatteryMonitor : PandaRoute        // Module 02
    @Serializable data object HeatGuard : PandaRoute             // Module 03
    @Serializable data object NetworkDiagnostics : PandaRoute    // Module 04
    @Serializable data object DeveloperToolbox : PandaRoute      // Module 05
    @Serializable data object DeviceInfo : PandaRoute            // Module 06
    @Serializable data object RamMonitor : PandaRoute            // Module 07
    @Serializable data object AppPermissions : PandaRoute        // Module 08
    @Serializable data object WifiAnalyzer : PandaRoute          // Module 09
    @Serializable data object DisplayDiagnostics : PandaRoute    // Module 10
    @Serializable data object SensorLab : PandaRoute             // Module 11
    @Serializable data object NotificationCenter : PandaRoute    // Module 12
    @Serializable data object NetworkInfo : PandaRoute           // Module 13
    @Serializable data object QrBarcodeToolkit : PandaRoute      // Module 14
    @Serializable data object SystemHealthReport : PandaRoute    // Module 15
    @Serializable data object StorageMonitor : PandaRoute        // Module 16

    // Smart Automation, Aqua Dynamics, Edge Glow, Auto-Dimmed Apps, Quick Action Center & Device Utilities
    @Serializable data object AquaDynamics : PandaRoute
    @Serializable data object SmartGestures : PandaRoute
    @Serializable data object SmartScreenSaver : PandaRoute
    @Serializable data object AutomationBuilder : PandaRoute
    @Serializable data object EdgeGlow : PandaRoute
    @Serializable data object AutoDimApps : PandaRoute
    @Serializable data object QuickActionCenter : PandaRoute
    @Serializable data object AppLaunchCenter : PandaRoute
    @Serializable data object ConnectionMonitor : PandaRoute
    @Serializable data object DeviceUtilities : PandaRoute       // Uptime, Charging Info, Sound & Vibration, Display Shortcuts
}
