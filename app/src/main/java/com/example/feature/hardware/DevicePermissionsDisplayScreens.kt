package com.example.feature.hardware

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// MODULE 06 — DEVICE INFORMATION
@Composable
fun DeviceInfoScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val aqua = LocalAquaColors.current

    val metrics = context.resources.displayMetrics
    val refreshRate = remember {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
        display?.refreshRate?.roundToInt() ?: 60
    }

    val pm = context.packageManager
    val hwFeatures = remember {
        buildList {
            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) add("Camera")
            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) add("LED Flash")
            if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) add("Bluetooth LE")
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) add("NFC")
            if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) add("Biometrics")
            if (pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) add("GPS")
            if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) add("Wi-Fi Direct")
            if (pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) add("Gyroscope")
            if (pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)) add("Barometer")
        }.joinToString(", ")
    }

    val specGroups = listOf(
        "Identity & OS" to listOf(
            "Manufacturer" to Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            "Brand" to Build.BRAND.replaceFirstChar { it.uppercase() },
            "Model" to Build.MODEL,
            "Device Codename" to Build.DEVICE,
            "Product" to Build.PRODUCT,
            "Android Version" to Build.VERSION.RELEASE,
            "SDK / API Level" to Build.VERSION.SDK_INT.toString(),
            "Security Patch" to (Build.VERSION.SECURITY_PATCH.ifBlank { "Not Reported" }),
            "Build ID" to Build.DISPLAY,
            "Build Fingerprint" to Build.FINGERPRINT,
            "Kernel Version" to (System.getProperty("os.version") ?: "Unknown"),
            "System Uptime" to SystemUtils.formatDurationMillis(SystemClock.elapsedRealtime())
        ),
        "Processor & Display" to listOf(
            "SoC / Processor" to (live.cpu?.socModel ?: Build.HARDWARE),
            "Hardware Board" to Build.BOARD,
            "CPU Architecture" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"),
            "Supported ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
            "CPU Core Count" to "${Runtime.getRuntime().availableProcessors()} Cores",
            "Screen Resolution" to "${metrics.widthPixels} × ${metrics.heightPixels} px",
            "Screen Density" to "${metrics.densityDpi} dpi (${String.format("%.2f", metrics.density)}x)",
            "Refresh Rate" to "$refreshRate Hz",
            "Hardware Capabilities" to hwFeatures
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Device Information",
            subtitle = "Module 06 • Complete Hardware & OS Specifications",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        val fullText = buildString {
                            appendLine("PANDANEX DEVICE SPECIFICATIONS")
                            specGroups.forEach { (section, rows) ->
                                appendLine("\n[$section]")
                                rows.forEach { (k, v) -> appendLine("$k: $v") }
                            }
                        }
                        SystemUtils.copyToClipboard(context, "All Device Specs", fullText)
                    },
                    modifier = Modifier.testTag("copy_all_device_specs")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy All Specs")
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            specGroups.forEach { (sectionTitle, rows) ->
                item {
                    AquaGlassCard {
                        SectionHeader(title = sectionTitle)
                        rows.forEach { (label, valStr) ->
                            KeyValueRow(
                                label = label,
                                value = valStr,
                                onCopy = { SystemUtils.copyToClipboard(context, label, valStr) }
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val fullText = specGroups.flatMap { it.second }
                            .joinToString("\n") { "${it.first}: ${it.second}" }
                        SystemUtils.copyToClipboard(context, "All Device Info", fullText)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy All Device Information")
                }
            }
        }
    }
}

// Shared data model for App Permissions & App Launch Center
data class InstalledAppRecord(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>,
    val hasCamera: Boolean,
    val hasMic: Boolean,
    val hasLocation: Boolean,
    val hasContacts: Boolean,
    val hasPhone: Boolean,
    val hasStorageMedia: Boolean,
    val hasNotifications: Boolean
)

private suspend fun loadVisibleLauncherApps(context: Context): List<InstalledAppRecord> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(mainIntent, 0)
        val uniquePackages = resolved.mapNotNull { it.activityInfo?.packageName }.toSet() + setOf(context.packageName)

        uniquePackages.mapNotNull { pkg ->
            try {
                @Suppress("DEPRECATION")
                val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                }
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val label = pm.getApplicationLabel(appInfo).toString()
                val reqPerms = pkgInfo.requestedPermissions ?: emptyArray()
                val reqFlags = pkgInfo.requestedPermissionsFlags ?: IntArray(0)

                val granted = mutableListOf<String>()
                val denied = mutableListOf<String>()

                reqPerms.forEachIndexed { idx, perm ->
                    val isGranted = idx < reqFlags.size && (reqFlags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (isGranted) granted.add(perm) else denied.add(perm)
                }

                val allRequested = reqPerms.toList()
                InstalledAppRecord(
                    appName = label,
                    packageName = pkg,
                    versionName = pkgInfo.versionName ?: "1.0",
                    isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    grantedPermissions = granted,
                    deniedPermissions = denied,
                    hasCamera = allRequested.any { it.contains("CAMERA") },
                    hasMic = allRequested.any { it.contains("RECORD_AUDIO") },
                    hasLocation = allRequested.any { it.contains("ACCESS_FINE_LOCATION") || it.contains("ACCESS_COARSE_LOCATION") },
                    hasContacts = allRequested.any { it.contains("CONTACTS") },
                    hasPhone = allRequested.any { it.contains("CALL_PHONE") || it.contains("READ_PHONE_STATE") },
                    hasStorageMedia = allRequested.any { it.contains("EXTERNAL_STORAGE") || it.contains("READ_MEDIA_") },
                    hasNotifications = allRequested.any { it.contains("POST_NOTIFICATIONS") }
                )
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }

// MODULE 08 — APP PERMISSIONS INSPECTOR
@Composable
fun AppPermissionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val aqua = LocalAquaColors.current
    val apps by produceState(initialValue = emptyList(), context) {
        value = loadVisibleLauncherApps(context)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    val filters = listOf(
        "ALL" to "All Apps",
        "CAMERA" to "Camera",
        "MIC" to "Microphone",
        "LOCATION" to "Location",
        "CONTACTS" to "Contacts",
        "PHONE" to "Phone",
        "STORAGE" to "Media/Storage",
        "NOTIFY" to "Notifications"
    )

    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        apps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedFilter) {
                "CAMERA" -> app.hasCamera
                "MIC" -> app.hasMic
                "LOCATION" -> app.hasLocation
                "CONTACTS" -> app.hasContacts
                "PHONE" -> app.hasPhone
                "STORAGE" -> app.hasStorageMedia
                "NOTIFY" -> app.hasNotifications
                else -> true
            }
            matchesQuery && matchesCategory
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "App Permissions Inspector",
            subtitle = "Module 08 • Audit Visible Launcher Apps (${filteredApps.size})",
            onBack = onBack
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search app name or package...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isExpanded = expandedPkg == app.packageName
                AquaGlassCard(
                    onClick = { expandedPkg = if (isExpanded) null else app.packageName }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            label = "${app.grantedPermissions.size} Granted / ${app.deniedPermissions.size} Denied",
                            condition = if (app.hasCamera || app.hasMic || app.hasLocation) HealthCondition.INFO else HealthCondition.NORMAL
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val badges = buildList {
                        if (app.hasCamera) add("Camera")
                        if (app.hasMic) add("Mic")
                        if (app.hasLocation) add("Location")
                        if (app.hasContacts) add("Contacts")
                        if (app.hasPhone) add("Phone")
                        if (app.hasStorageMedia) add("Media")
                        if (app.hasNotifications) add("Notify")
                    }
                    if (badges.isNotEmpty()) {
                        Text(
                            text = "Sensitive Scope: ${badges.joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (app.grantedPermissions.isNotEmpty()) {
                            Text("Granted Permissions:", style = MaterialTheme.typography.labelLarge, color = aqua.statusNormal)
                            app.grantedPermissions.take(15).forEach { p ->
                                Text("✓ ${p.substringAfterLast('.')}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (app.deniedPermissions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Denied / Not Granted:", style = MaterialTheme.typography.labelLarge, color = aqua.statusWarning)
                            app.deniedPermissions.take(10).forEach { p ->
                                Text("✕ ${p.substringAfterLast('.')}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { SystemUtils.openAppDetailsSettings(context, app.packageName) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Android App Permission Settings")
                        }
                    }
                }
            }
        }
    }
}

// UTILITY — APP LAUNCH CENTER
@Composable
fun AppLaunchCenterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val aqua = LocalAquaColors.current
    val apps by produceState(initialValue = emptyList(), context) {
        value = loadVisibleLauncherApps(context)
    }
    var search by remember { mutableStateOf("") }
    val filtered = remember(apps, search) {
        apps.filter {
            search.isBlank() ||
                it.appName.contains(search, ignoreCase = true) ||
                it.packageName.contains(search, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "App Launch Center",
            subtitle = "Quick Launch & Package Inspector (${filtered.size} Apps)",
            onBack = onBack
        )
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search installed launcher apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AquaGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${app.packageName} • v${app.versionName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { SystemUtils.openAppDetailsSettings(context, app.packageName) }) {
                                Icon(Icons.Default.Settings, contentDescription = "App Settings")
                            }
                            IconButton(
                                onClick = {
                                    val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launch != null) {
                                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launch)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Open ${app.appName}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// MODULE 10 — DISPLAY DIAGNOSTICS
private enum class DisplayTestMode {
    NONE,
    SOLID_COLOR,
    MULTITOUCH
}

@Composable
fun DisplayDiagnosticsScreen(
    onBack: () -> Unit,
    onFullScreenModeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val config = LocalConfiguration.current
    val aqua = LocalAquaColors.current
    val metrics = context.resources.displayMetrics

    var activeMode by remember { mutableStateOf(DisplayTestMode.NONE) }
    var colorIndex by remember { mutableStateOf(0) }
    val testColors = listOf(
        "Pure Red" to Color.Red,
        "Pure Green" to Color.Green,
        "Pure Blue" to Color.Blue,
        "Pure White" to Color.White,
        "Pure Black (OLED Check)" to Color.Black
    )

    val refreshRate = remember {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
        display?.refreshRate?.roundToInt() ?: 60
    }

    val brightnessPercent = remember {
        val raw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        ((raw / 255f) * 100f).roundToInt().coerceIn(0, 100)
    }

    val orientationName = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"

    DisposableEffect(activeMode, view) {
        val isFullScreenTest = activeMode != DisplayTestMode.NONE
        onFullScreenModeChanged(isFullScreenTest)

        val window = (context as? Activity)?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else null

        if (isFullScreenTest) {
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            onFullScreenModeChanged(false)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (activeMode != DisplayTestMode.NONE) {
        BackHandler { activeMode = DisplayTestMode.NONE }
    }

    when (activeMode) {
        DisplayTestMode.SOLID_COLOR -> {
            val (label, bgColor) = testColors[colorIndex % testColors.size]
            val fgColor = if (bgColor == Color.White || bgColor == Color.Green) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .clickable {
                        if (colorIndex < testColors.lastIndex) {
                            colorIndex++
                        } else {
                            activeMode = DisplayTestMode.NONE
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { activeMode = DisplayTestMode.NONE }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Test", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exit ($label — Tap screen for next)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        DisplayTestMode.MULTITOUCH -> {
            val touchPointers = remember { mutableStateMapOf<Long, Offset>() }
            var maxDetectedPointers by remember { mutableStateOf(0) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF040A14))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                touchPointers.clear()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        touchPointers[change.id.value] = change.position
                                    }
                                }
                                if (touchPointers.size > maxDetectedPointers) {
                                    maxDetectedPointers = touchPointers.size
                                }
                            } while (event.changes.any { it.pressed })
                            touchPointers.clear()
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    touchPointers.entries.forEachIndexed { idx, (_, pos) ->
                        val c = Color.hsv((idx * 55f) % 360f, 0.85f, 1f)
                        // Crosshair lines across screen
                        drawLine(c.copy(alpha = 0.35f), Offset(0f, pos.y), Offset(size.width, pos.y), strokeWidth = 2f)
                        drawLine(c.copy(alpha = 0.35f), Offset(pos.x, 0f), Offset(pos.x, size.height), strokeWidth = 2f)
                        drawCircle(color = c.copy(alpha = 0.25f), radius = 56.dp.toPx(), center = pos)
                        drawCircle(color = c, radius = 38.dp.toPx(), center = pos, style = Stroke(width = 4.dp.toPx()))
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Active Fingers: ${touchPointers.size} • Peak Multi-Touch: $maxDetectedPointers",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "Touch anywhere with multiple fingers simultaneously to verify digitizer response.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Button(
                    onClick = { activeMode = DisplayTestMode.NONE },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Touch Test")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exit")
                }
            }
        }

        DisplayTestMode.NONE -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
            ) {
                AquaTopBar(
                    title = "Display Diagnostics",
                    subtitle = "Module 10 • Dead Pixel RGB Check & Multi-Touch Digitizer",
                    onBack = onBack
                )
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        AquaGlassCard(glowAccent = true) {
                            SectionHeader(title = "Panel & Window Telemetry", icon = Icons.Default.PhoneAndroid)
                            KeyValueRow("Resolution", "${metrics.widthPixels} × ${metrics.heightPixels} px")
                            KeyValueRow("Pixel Density", "${metrics.densityDpi} dpi (${String.format("%.2f", metrics.density)}x)")
                            KeyValueRow("Refresh Rate", "$refreshRate Hz")
                            KeyValueRow("System Brightness", "$brightnessPercent%")
                            KeyValueRow("Current Orientation", orientationName)
                        }
                    }

                    item {
                        AquaGlassCard {
                            SectionHeader(title = "Full-Screen Dead Pixel Color Tests")
                            Text(
                                "Tap any color swatch to enter full-screen inspection. Tap screen to cycle colors or use top-right Exit button.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                testColors.forEachIndexed { index, (name, col) ->
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(col)
                                            .clickable {
                                                colorIndex = index
                                                activeMode = DisplayTestMode.SOLID_COLOR
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = name.substringAfter("Pure ").take(3).uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (col == Color.White || col == Color.Green) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        AquaGlassCard {
                            SectionHeader(title = "Touchscreen & Multi-Touch Digitizer", icon = Icons.Default.TouchApp)
                            Text(
                                "Verify touch accuracy, edge tracking, and simultaneous multi-finger pointer count.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { activeMode = DisplayTestMode.MULTITOUCH },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("launch_multitouch_test")
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Launch Multi-Touch Digitizer Test")
                            }
                        }
                    }
                }
            }
        }
    }
}
