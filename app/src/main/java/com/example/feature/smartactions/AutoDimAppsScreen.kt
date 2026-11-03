package com.example.feature.smartactions

import android.Manifest
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.automation.SelectableAppItem
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaLinearBar
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.feature.viewmodel.PandaMainViewModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors
import kotlinx.coroutines.launch

@Composable
fun AutoDimAppsScreen(
    viewModel: PandaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dimStatus by viewModel.autoDimStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val aqua = LocalAquaColors.current

    var installedApps by remember { mutableStateOf<List<SelectableAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("ALL") } // ALL, BANKING, SELECTED

    val bgServicePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setBackgroundGestureService(true)
    }

    LaunchedEffect(Unit) {
        installedApps = viewModel.container.autoDimController.loadInstalledSelectableApps()
    }

    // Apply in-window brightness preview when user taps "Preview Dimming Intensity"
    LaunchedEffect(dimStatus.isPreviewActive, settings.autoDimIntensityPercent) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val lp = window.attributes
            val remainingPct = (100 - settings.autoDimIntensityPercent.coerceIn(10, 90)).coerceIn(10, 90)
            lp.screenBrightness = if (dimStatus.isPreviewActive) {
                (remainingPct / 100f).coerceIn(0.08f, 1f)
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = lp
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.container.autoDimController.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val window = (context as? Activity)?.window
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    val selectedPackages = settings.autoDimPackagesSet
    val disabledRules = settings.autoDimDisabledRulesSet
    val activePackages = settings.activeAutoDimPackagesSet

    val filteredApps = remember(installedApps, searchQuery, activeFilter, selectedPackages) {
        installedApps.filter { app ->
            val matchesFilter = when (activeFilter) {
                "BANKING" -> app.isSuggestedBankOrSensitive
                "SELECTED" -> selectedPackages.contains(app.packageName)
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Dimmed App Shield",
            subtitle = "10%–90% Privacy & Eye-Comfort Dimming for Selected Apps",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Master Control, Temporary Pause & Live Status Card
            item {
                AquaGlassCard(glowAccent = settings.autoDimAppsEnabled) {
                    SectionHeader(title = "Automatic Dimmed App Control", icon = Icons.Default.BrightnessLow)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Enable Dimmed App", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Automatically dims the screen when selected apps open and restores normal brightness when you switch away or close them",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoDimAppsEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    viewModel.prefs.updateAutoDimSettings(enabled = enabled)
                                    if (enabled && !settings.backgroundMonitoringEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            bgServicePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            viewModel.setBackgroundGestureService(true)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("toggle_auto_dim_master")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = when {
                                dimStatus.isTemporarilyPaused -> "PAUSED (${dimStatus.pausedRemainingSeconds}s)"
                                dimStatus.isCurrentlyDimmed -> "DIMMING ACTIVE"
                                else -> "${activePackages.size}/${selectedPackages.size} Rules Active"
                            },
                            condition = when {
                                dimStatus.isTemporarilyPaused -> HealthCondition.WARNING
                                dimStatus.isCurrentlyDimmed -> HealthCondition.NORMAL
                                else -> HealthCondition.INFO
                            }
                        )
                        Text(
                            text = "Intensity: ${settings.autoDimIntensityPercent}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = dimStatus.lastEventSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Temporary Pause / Resume Quick Controls
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text("Temporary Pause (Keep Rules Saved):", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (dimStatus.isTemporarilyPaused) {
                            Button(
                                onClick = { viewModel.container.autoDimController.resumeDimmingNow() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_resume_dimming_now")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume Dimmed App Now")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.container.autoDimController.pauseDimmingTemporarily(5)
                                    Toast.makeText(context, "Dimmed App paused for 5 minutes", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_pause_dimming_5m")
                            ) {
                                Icon(Icons.Default.PauseCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause 5m")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.container.autoDimController.pauseDimmingTemporarily(15)
                                    Toast.makeText(context, "Dimmed App paused for 15 minutes", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_pause_dimming_15m")
                            ) {
                                Icon(Icons.Default.PauseCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause 15m")
                            }
                        }
                    }
                }
            }

            // 2. Required Permissions & Security Dialog Auto-Bypass Explanation
            item {
                AquaGlassCard {
                    SectionHeader(title = "Permissions & System Security Safety", icon = Icons.Default.Security)
                    PermissionAccessRow(
                        title = "App Usage Access (Foreground App Detection)",
                        subtitle = "Required so PandaNex knows when a selected app opens or closes",
                        granted = dimStatus.hasUsageAccess,
                        buttonLabel = "Grant Usage",
                        onGrantClick = { SystemUtils.openUsageAccessSettings(context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionAccessRow(
                        title = "Display Over Other Apps (Non-Blocking Dimmer Filter)",
                        subtitle = "Draws a pass-through 10%–90% dark privacy filter without intercepting touches",
                        granted = dimStatus.hasOverlayPermission,
                        buttonLabel = "Grant Overlay",
                        onGrantClick = { SystemUtils.openOverlayPermissionSettings(context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionAccessRow(
                        title = "Modify System Settings (Hardware Backlight Control)",
                        subtitle = "Optional: lowers hardware screen brightness and restores exact prior level on exit",
                        granted = dimStatus.hasWriteSettings,
                        buttonLabel = "Grant Write",
                        onGrantClick = { SystemUtils.openWriteSettingsPermission(context) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Safety Guarantee: Dimming overlays never intercept touch events and automatically suspend whenever Android Permission Controller, Package Installer, Biometric, or Accessibility dialogs appear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Dimming Intensity (10%–90%), Preview & Reset Settings
            item {
                AquaGlassCard {
                    SectionHeader(title = "Dimming Intensity (10% – 90%)", icon = Icons.Default.BrightnessLow)
                    Text(
                        text = "Dimming Filter Intensity: ${settings.autoDimIntensityPercent}%",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AquaLinearBar(
                        progress = settings.autoDimIntensityPercent / 100f,
                        condition = HealthCondition.INFO
                    )
                    Slider(
                        value = settings.autoDimIntensityPercent.coerceIn(10, 90).toFloat(),
                        onValueChange = { v ->
                            val pct = v.toInt().coerceIn(10, 90)
                            scope.launch {
                                viewModel.prefs.updateAutoDimSettings(
                                    intensityPercent = pct,
                                    targetBrightnessPercent = (100 - pct).coerceIn(10, 90)
                                )
                            }
                        },
                        valueRange = 10f..90f,
                        modifier = Modifier.testTag("auto_dim_brightness_slider")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Anti-Peek Privacy Shade Filter", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Applies non-blocking dark filter over selected apps for public privacy & night comfort",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoDimUseExtraPrivacyShade,
                            onCheckedChange = { enabled ->
                                scope.launch { viewModel.prefs.updateAutoDimSettings(useExtraPrivacyShade = enabled) }
                            },
                            modifier = Modifier.testTag("toggle_auto_dim_shade")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.container.autoDimController.triggerPreviewDim(3500L) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_auto_dim_button")
                        ) {
                            Text("Preview Intensity")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.container.autoDimController.restoreNormalBrightness("Brightness manually restored")
                                Toast.makeText(context, "Brightness restored", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.container.autoDimController.resetSettingsToDefaults()
                            Toast.makeText(context, "Dimmed App settings reset to defaults", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_auto_dim_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Dimmed App Settings")
                    }
                }
            }

            // 4. App Selection & Per-App Rule Toggle Header
            item {
                AquaGlassCard {
                    SectionHeader(title = "Select Apps & Individual Rules", icon = Icons.Default.AccountBalance)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_dim_search_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Search banking, wallet, or installed apps") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    val suggestedCount = installedApps.count { it.isSuggestedBankOrSensitive }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = activeFilter == "ALL",
                                onClick = { activeFilter = "ALL" },
                                label = { Text("All Apps (${installedApps.size})") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeFilter == "BANKING",
                                onClick = { activeFilter = "BANKING" },
                                label = { Text("Banking & Finance ($suggestedCount)") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeFilter == "SELECTED",
                                onClick = { activeFilter = "SELECTED" },
                                label = { Text("Selected (${selectedPackages.size})") }
                            )
                        }
                    }

                    if (suggestedCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val bankPkgs = installedApps
                                    .filter { it.isSuggestedBankOrSensitive }
                                    .map { it.packageName }
                                    .toSet()
                                scope.launch {
                                    viewModel.prefs.updateAutoDimSettings(packagesSet = selectedPackages + bankPkgs)
                                    Toast.makeText(
                                        context,
                                        "Added ${bankPkgs.size} banking/finance apps to Dimmed App",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_suggested_banks_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select All Suggested Banking & Payment Apps")
                        }
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                item {
                    AquaGlassCard {
                        Text(
                            text = if (activeFilter == "BANKING") {
                                "No installed apps matched common banking keywords on this device — switch to 'All Apps' to pick any app."
                            } else {
                                "No matching apps found."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected = selectedPackages.contains(app.packageName)
                    val isRuleEnabled = isSelected && !disabledRules.contains(app.packageName)
                    AquaGlassCard(
                        contentPadding = PaddingValues(12.dp),
                        glowAccent = isRuleEnabled,
                        modifier = Modifier.clickable {
                            scope.launch { viewModel.prefs.toggleAutoDimPackage(app.packageName) }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (app.isSuggestedBankOrSensitive) {
                                        Icons.Default.AccountBalance
                                    } else {
                                        Icons.Default.BrightnessLow
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (app.isSuggestedBankOrSensitive) {
                                        StatusPill(label = app.categoryBadge, condition = HealthCondition.INFO)
                                    }
                                }
                                Text(
                                    text = if (isSelected) {
                                        if (isRuleEnabled) "${app.packageName} • Rule Active" else "${app.packageName} • Rule Paused"
                                    } else {
                                        app.packageName
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Switch(
                                    checked = isRuleEnabled,
                                    onCheckedChange = { ruleActive ->
                                        scope.launch {
                                            viewModel.prefs.setIndividualAppDimRuleEnabled(app.packageName, ruleActive)
                                        }
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    scope.launch { viewModel.prefs.toggleAutoDimPackage(app.packageName) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
