package com.example.automation

import android.app.KeyguardManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.example.feature.screensaver.SmartScreenSaverActivity

/**
 * Minimal least-privilege DeviceAdminReceiver declaring ONLY <force-lock /> in res/xml/panda_device_admin.xml.
 * Used exclusively when the user explicitly authorizes Put Down to Sleep (or Screen Saver return-to-sleep)
 * to turn off and lock the display safely.
 */
class PandaDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(
            context,
            "PandaNex Screen-Lock Admin enabled for Put Down to Sleep",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(
            context,
            "PandaNex Screen-Lock Admin disabled",
            Toast.LENGTH_SHORT
        ).show()
    }
}

enum class SleepExecutionOutcome(val label: String, val succeeded: Boolean) {
    LOCKED_VIA_DEVICE_ADMIN("Display turned off via Device Admin", true),
    SHORTENED_SCREEN_TIMEOUT("Display timeout shortened to 10s (Device Admin not active)", true),
    REQUIRES_AUTHORIZATION("Requires Screen-Lock Device Admin or Write Settings permission", false)
}

/**
 * Executes Android-supported display wake, screen-off/lock, and motion screen-saver launch actions
 * while strictly respecting Android lock-screen (Keyguard) security.
 */
class DisplayActionManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val adminComponent = ComponentName(context, PandaDeviceAdminReceiver::class.java)

    @Volatile
    private var savedScreenTimeoutBeforeSleepMs: Int? = null

    fun isDisplayInteractive(): Boolean {
        return runCatching { powerManager.isInteractive }.getOrDefault(true)
    }

    fun isKeyguardLocked(): Boolean {
        return runCatching { keyguardManager.isKeyguardLocked }.getOrDefault(false)
    }

    fun isDeviceAdminActive(): Boolean {
        return runCatching { devicePolicyManager.isAdminActive(adminComponent) }.getOrDefault(false)
    }

    fun canWriteSystemSettings(): Boolean {
        return runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
    }

    fun canDrawOverlays(): Boolean {
        return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    }

    fun buildAuthorizeDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "PandaNex uses the Screen-Lock privilege solely for 'Put Down to Sleep' when your phone is placed stably on a flat surface. PandaNex never changes your PIN/password or erases data."
            )
        }
    }

    fun revokeDeviceAdmin() {
        runCatching {
            if (isDeviceAdminActive()) {
                devicePolicyManager.removeActiveAdmin(adminComponent)
            }
        }
    }

    fun openDreamSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_DREAM_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    /**
     * Wakes the display safely using Android's standard short-lived wakeup lock without
     * dismissing or bypassing the system lock screen (PIN, pattern, password, biometric).
     */
    fun wakeDisplayRespectingLockScreen(forceForTest: Boolean = false): Pair<Boolean, String> {
        if (!forceForTest && isDisplayInteractive()) {
            return false to "Skipped: Display is already awake and interactive"
        }

        return try {
            @Suppress("DEPRECATION")
            val wakeFlags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE

            val wakeLock = powerManager.newWakeLock(wakeFlags, "PandaNex:RaiseToWakePulse")
            wakeLock.setReferenceCounted(false)
            wakeLock.acquire(2500L)
            runCatching {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
            val lockNote = if (isKeyguardLocked()) " (Lock screen preserved)" else ""
            true to "Display woken via Raise to Wake$lockNote"
        } catch (e: Exception) {
            false to "Display wake restricted (${e.javaClass.simpleName})"
        }
    }

    /**
     * Turns off / locks the display when Put Down to Sleep detects stable flat placement.
     * Uses authorized DevicePolicyManager.lockNow() when active, or falls back to temporary
     * minimum screen-off timeout when Write System Settings is granted.
     */
    fun turnDisplayOffOrSleep(): SleepExecutionOutcome {
        if (isDeviceAdminActive()) {
            val locked = runCatching {
                devicePolicyManager.lockNow()
                true
            }.getOrDefault(false)
            if (locked) {
                return SleepExecutionOutcome.LOCKED_VIA_DEVICE_ADMIN
            }
        }

        if (canWriteSystemSettings()) {
            val cr = context.contentResolver
            val shortened = runCatching {
                if (savedScreenTimeoutBeforeSleepMs == null) {
                    savedScreenTimeoutBeforeSleepMs = Settings.System.getInt(
                        cr,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        30_000
                    )
                }
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 10_000)
                true
            }.getOrDefault(false)
            if (shortened) {
                return SleepExecutionOutcome.SHORTENED_SCREEN_TIMEOUT
            }
        }

        return SleepExecutionOutcome.REQUIRES_AUTHORIZATION
    }

    /**
     * Restores the user's previous screen timeout if the Write-Settings fallback was used.
     */
    fun onDisplayTurnedOn() {
        val prevTimeout = savedScreenTimeoutBeforeSleepMs ?: return
        savedScreenTimeoutBeforeSleepMs = null
        if (canWriteSystemSettings()) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    prevTimeout.coerceAtLeast(15_000)
                )
            }
        }
    }

    /**
     * Launches the motion-activated PandaNex Smart Screen Saver over the ambient/locked state
     * without bypassing lock-screen security.
     */
    fun launchSmartScreenSaver(
        isPreview: Boolean,
        styleOverride: String? = null
    ): Pair<Boolean, String> {
        if (!isPreview && !canDrawOverlays()) {
            // On Android 10+, starting an Activity from background when screen is off requires
            // SYSTEM_ALERT_WINDOW ("Display over other apps") BAL exemption.
            return false to "Smart Screen Saver requires 'Display over other apps' permission for background motion activation"
        }

        return try {
            val intent = Intent(context, SmartScreenSaverActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
                putExtra(SmartScreenSaverActivity.EXTRA_IS_PREVIEW, isPreview)
                if (!styleOverride.isNullOrBlank()) {
                    putExtra(SmartScreenSaverActivity.EXTRA_STYLE_OVERRIDE, styleOverride)
                }
            }
            context.startActivity(intent)
            true to if (isPreview) {
                "Opened Smart Screen Saver live preview"
            } else {
                "Smart Screen Saver activated by motion"
            }
        } catch (e: Exception) {
            false to "Screen Saver launch restricted (${e.javaClass.simpleName})"
        }
    }
}
