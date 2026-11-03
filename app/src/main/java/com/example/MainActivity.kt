package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.navigation.PandaRoute
import com.example.feature.developer.DeveloperToolboxScreen
import com.example.feature.hardware.AppLaunchCenterScreen
import com.example.feature.hardware.AppPermissionsScreen
import com.example.feature.hardware.DeviceInfoScreen
import com.example.feature.hardware.DisplayDiagnosticsScreen
import com.example.feature.home.HomeDashboardScreen
import com.example.feature.home.MonitorHubScreen
import com.example.feature.home.ToolsHubScreen
import com.example.feature.monitoring.BatteryMonitorScreen
import com.example.feature.monitoring.CpuMonitorScreen
import com.example.feature.monitoring.HeatGuardScreen
import com.example.feature.monitoring.RamMonitorScreen
import com.example.feature.monitoring.StorageMonitorScreen
import com.example.feature.network.ConnectionMonitorScreen
import com.example.feature.network.NetworkDiagnosticsScreen
import com.example.feature.network.NetworkInfoScreen
import com.example.feature.network.WifiAnalyzerScreen
import com.example.feature.qrbarcode.QrBarcodeToolkitScreen
import com.example.feature.quickactions.DeviceUtilitiesScreen
import com.example.feature.quickactions.QuickActionCenterScreen
import com.example.feature.reports.NotificationCenterScreen
import com.example.feature.reports.SystemHealthReportScreen
import com.example.feature.screensaver.SmartScreenSaverScreen
import com.example.feature.sensors.SensorLabScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.smartactions.AquaDynamicsScreen
import com.example.feature.smartactions.AutoDimAppsScreen
import com.example.feature.smartactions.AutomationBuilderScreen
import com.example.feature.smartactions.ComposeAquaDynamicsHost
import com.example.feature.smartactions.ComposeEdgeGlowOverlay
import com.example.feature.smartactions.EdgeGlowScreen
import com.example.feature.smartactions.SmartActionsScreen
import com.example.feature.smartactions.SmartGesturesControlScreen
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.PandaNexTheme

private data class TopLevelDestination(
    val route: PandaRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val detector = (application as PandaNexApplication).container.fullScreenDetector
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val statusHidden = !insets.isVisible(WindowInsetsCompat.Type.statusBars())
            val navMax = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            val navHidden = navMax > 0 && !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            detector.setHostWindowSystemBarsHidden(statusHidden || navHidden)
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        setContent {
            val mainViewModel: PandaMainViewModel = viewModel(factory = PandaMainViewModel.Factory)
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val fullScreenState by mainViewModel.fullScreenState.collectAsStateWithLifecycle()

            val effectiveAnimations = settings.animationsEnabled &&
                !(settings.hideAquaDynamicsInFullScreen && fullScreenState.isFullScreenActive)

            PandaNexTheme(
                themeMode = settings.themeMode,
                animationsEnabled = effectiveAnimations
            ) {
                PandaNexAppShell(viewModel = mainViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as PandaNexApplication).container
        container.quickCaptureController.setActivityForeground(true)
        container.smartGestureManager.setActivityForeground(true)
    }

    override fun onResume() {
        super.onResume()
        val container = (application as PandaNexApplication).container
        container.fullScreenDetector.syncProbeAndMonitorLoop()
        container.fullScreenDetector.evaluateNow()
        container.quickCaptureController.setActivityForeground(true)
        container.smartGestureManager.setActivityForeground(true)
    }

    override fun onStop() {
        val container = (application as PandaNexApplication).container
        container.quickCaptureController.setActivityForeground(false)
        container.smartGestureManager.setActivityForeground(false)
        super.onStop()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        (application as PandaNexApplication)
            .container
            .fullScreenDetector
            .setInPictureInPictureMode(isInPictureInPictureMode)
    }
}

@Composable
fun PandaNexAppShell(viewModel: PandaMainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val edgeGlowState by viewModel.edgeGlowState.collectAsStateWithLifecycle()
    val aquaDynamicsState by viewModel.aquaDynamicsState.collectAsStateWithLifecycle()
    val fullScreenState by viewModel.fullScreenState.collectAsStateWithLifecycle()
    val hideAquaChrome = settings.hideAquaDynamicsInFullScreen && fullScreenState.inAppFullScreenActive

    val topDestinations = listOf(
        TopLevelDestination(
            route = PandaRoute.Home,
            labelRes = R.string.nav_home,
            selectedIcon = Icons.Filled.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard,
            testTag = "nav_tab_home"
        ),
        TopLevelDestination(
            route = PandaRoute.MonitorHub,
            labelRes = R.string.nav_monitor,
            selectedIcon = Icons.Filled.Speed,
            unselectedIcon = Icons.Outlined.Speed,
            testTag = "nav_tab_monitor"
        ),
        TopLevelDestination(
            route = PandaRoute.ToolsHub,
            labelRes = R.string.nav_tools,
            selectedIcon = Icons.Filled.Build,
            unselectedIcon = Icons.Outlined.Build,
            testTag = "nav_tab_tools"
        ),
        TopLevelDestination(
            route = PandaRoute.SmartActions,
            labelRes = R.string.nav_smart_actions,
            selectedIcon = Icons.Filled.Bolt,
            unselectedIcon = Icons.Outlined.Bolt,
            testTag = "nav_tab_smart"
        ),
        TopLevelDestination(
            route = PandaRoute.Settings,
            labelRes = R.string.nav_settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_tab_settings"
        )
    )

    val isTopLevel = topDestinations.any { dest ->
        currentDest?.hasRoute(dest.route::class) == true
    }

    fun navigateTopLevel(route: PandaRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 700.dp

        Box(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isTopLevel && !hideAquaChrome) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            topDestinations.forEach { item ->
                                val selected = currentDest?.hasRoute(item.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = if (hideAquaChrome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing
                    ) { innerPadding ->
                        PandaNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            } else {
                Scaffold(
                    contentWindowInsets = if (hideAquaChrome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                    bottomBar = {
                        if (isTopLevel && !hideAquaChrome) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                tonalElevation = 4.dp
                            ) {
                                topDestinations.forEach { item ->
                                    val selected = currentDest?.hasRoute(item.route::class) == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTopLevel(item.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = stringResource(item.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(item.labelRes)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag(item.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    PandaNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }

            // Full-bleed Edge Glow overlay for Notifications (auto-disappears in full screen & suppressed on calls)
            ComposeEdgeGlowOverlay(
                state = edgeGlowState,
                hideInFullScreen = settings.hideAquaDynamicsInFullScreen,
                fullScreenActive = fullScreenState.isFullScreenActive
            )

            // Interactive Aqua Dynamics Capsule around camera cutout (suppressed during calls & full screen)
            ComposeAquaDynamicsHost(
                snapshot = aquaDynamicsState,
                animationStyle = settings.aquaAnimationStyle,
                hideInFullScreen = settings.hideAquaDynamicsInFullScreen,
                fullScreenActive = fullScreenState.isFullScreenActive,
                onVerticalDrag = { dy, slop -> viewModel.dragAquaDynamicsVertical(dy, slop) },
                onVerticalSettle = { vy -> viewModel.settleAquaDynamicsDrag(vy) },
                onTapExpand = { viewModel.expandAquaDynamicsCapsule() },
                onTapMinimize = { viewModel.minimizeAquaDynamicsCapsule() },
                onDismiss = { viewModel.dismissAquaDynamicsCapsule() }
            )
        }
    }
}

@Composable
private fun PandaNavHost(
    navController: NavHostController,
    viewModel: PandaMainViewModel,
    modifier: Modifier = Modifier
) {
    val goBack: () -> Unit = { navController.navigateUp() }
    val navigateTo: (PandaRoute) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = PandaRoute.Home,
        modifier = modifier.fillMaxSize()
    ) {
        // 5 Bottom Navigation Destinations
        composable<PandaRoute.Home> {
            HomeDashboardScreen(viewModel = viewModel, onNavigate = navigateTo)
        }
        composable<PandaRoute.MonitorHub> {
            MonitorHubScreen(viewModel = viewModel, onNavigate = navigateTo)
        }
        composable<PandaRoute.ToolsHub> {
            ToolsHubScreen(viewModel = viewModel, onNavigate = navigateTo)
        }
        composable<PandaRoute.SmartActions> {
            SmartActionsScreen(viewModel = viewModel, onNavigate = navigateTo)
        }
        composable<PandaRoute.Settings> {
            SettingsScreen(viewModel = viewModel, onNavigate = navigateTo)
        }

        // 16 Core Monitoring & Diagnostic Modules
        composable<PandaRoute.CpuMonitor> {
            CpuMonitorScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.BatteryMonitor> {
            BatteryMonitorScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.HeatGuard> {
            HeatGuardScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.NetworkDiagnostics> {
            NetworkDiagnosticsScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.DeveloperToolbox> {
            DeveloperToolboxScreen(onBack = goBack)
        }
        composable<PandaRoute.DeviceInfo> {
            DeviceInfoScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.RamMonitor> {
            RamMonitorScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.AppPermissions> {
            AppPermissionsScreen(onBack = goBack)
        }
        composable<PandaRoute.WifiAnalyzer> {
            WifiAnalyzerScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.DisplayDiagnostics> {
            DisplayDiagnosticsScreen(
                onBack = goBack,
                onFullScreenModeChanged = { isFullScreen ->
                    viewModel.setInAppFullScreen(isFullScreen)
                }
            )
        }
        composable<PandaRoute.SensorLab> {
            SensorLabScreen(onBack = goBack)
        }
        composable<PandaRoute.NotificationCenter> {
            NotificationCenterScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.NetworkInfo> {
            NetworkInfoScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.QrBarcodeToolkit> {
            QrBarcodeToolkitScreen(onBack = goBack)
        }
        composable<PandaRoute.SystemHealthReport> {
            SystemHealthReportScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.StorageMonitor> {
            StorageMonitorScreen(viewModel = viewModel, onBack = goBack)
        }

        // Smart Automation, Edge Glow, Auto-Dimmed Apps, Quick Action Center & Additional Utilities
        composable<PandaRoute.AutomationBuilder> {
            AutomationBuilderScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.AquaDynamics> {
            AquaDynamicsScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.EdgeGlow> {
            EdgeGlowScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.AutoDimApps> {
            AutoDimAppsScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.SmartGestures> {
            SmartGesturesControlScreen(viewModel = viewModel, onNavigate = navigateTo, onBack = goBack)
        }
        composable<PandaRoute.SmartScreenSaver> {
            SmartScreenSaverScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.QuickActionCenter> {
            QuickActionCenterScreen(viewModel = viewModel, onNavigate = navigateTo, onBack = goBack)
        }
        composable<PandaRoute.AppLaunchCenter> {
            AppLaunchCenterScreen(onBack = goBack)
        }
        composable<PandaRoute.ConnectionMonitor> {
            ConnectionMonitorScreen(viewModel = viewModel, onBack = goBack)
        }
        composable<PandaRoute.DeviceUtilities> {
            DeviceUtilitiesScreen(viewModel = viewModel, onBack = goBack)
        }
    }
}
