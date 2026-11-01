package com.example.core.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object SystemUtils {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun shareTextReport(context: Context, title: String, content: String, fileNamePrefix: String = "pandanex_report") {
        try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, "${fileNamePrefix}_${System.currentTimeMillis()}.txt")
            file.writeText(content)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share $title").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
            }
            context.startActivity(Intent.createChooser(fallback, "Share $title").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openAppDetailsSettings(context: Context, packageName: String = context.packageName) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open application settings", Toast.LENGTH_SHORT).show()
        }
    }

    fun openSystemSettings(context: Context, action: String) {
        try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(context, "Settings screen unavailable on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openSystemSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }
    }

    fun openWriteSettingsPermission(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openSystemSettings(context, Settings.ACTION_MANAGE_WRITE_SETTINGS)
        }
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openSystemSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        openSystemSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerHapticPulse(context: Context, durationMs: Long = 35L) {
        try {
            val vibrator = resolveVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
            // Ignore if vibrator unavailable
        }
    }

    /**
     * Exclusive Flip-to-DND tactile feedback:
     * - When Flip to DND turns ON  -> Vibrates strictly 2 times.
     * - When Flip to DND turns OFF -> Vibrates strictly 1 time alone.
     * Uses USAGE_ALARM attributes so Android does not suppress the vibration while DND is active.
     */
    fun triggerFlipDndVibration(context: Context, isTurningOn: Boolean) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.cancel()

            val effect: VibrationEffect = if (isTurningOn) {
                // Exactly 2 crisp pulses: 100ms ON, 130ms pause, 100ms ON
                if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 100L, 130L, 100L),
                        intArrayOf(0, 255, 0, 255),
                        -1
                    )
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0L, 100L, 130L, 100L), -1)
                }
            } else {
                // Strictly 1 pulse alone: 115ms ON
                VibrationEffect.createOneShot(115L, VibrationEffect.DEFAULT_AMPLITUDE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttrs)
            }
        } catch (_: Exception) {
            // Ignore if vibrator unavailable on device/emulator
        }
    }

    /**
     * Moto-style Quick Capture tactile confirmation:
     * Crisp double-tap mechanical shutter-style vibration when Shake to Camera triggers.
     */
    fun triggerQuickCaptureVibration(context: Context) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 45L, 55L, 65L),
                    intArrayOf(0, 210, 0, 255),
                    -1
                )
            } else {
                VibrationEffect.createWaveform(longArrayOf(0L, 45L, 55L, 65L), -1)
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {
            // Ignore if vibrator unavailable
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val unit = 1024.0
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(0, 4)
        val prefix = arrayOf("B", "KB", "MB", "GB", "TB")[exp]
        val value = bytes / unit.pow(exp.toDouble())
        return String.format(Locale.US, "%.2f %s", value, prefix)
    }

    fun formatDurationMillis(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${minutes}m ${seconds}s")
        }
    }

    fun formatTimestamp(timestampMs: Long): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}
