package com.example.monitoring

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.core.ui.HealthCondition
import kotlin.math.roundToInt

data class RamSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val usagePercent: Float,
    val thresholdBytes: Long,
    val isLowMemory: Boolean,
    val condition: HealthCondition,
    val conditionLabel: String,
    val timestamp: Long = System.currentTimeMillis()
)

class RamMonitor(private val context: Context) {

    fun sample(): RamSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val total = memInfo.totalMem.coerceAtLeast(1L)
        val avail = memInfo.availMem.coerceAtLeast(0L)
        val used = (total - avail).coerceAtLeast(0L)
        val pct = ((used.toDouble() / total.toDouble()) * 100.0).toFloat().let {
            (it * 10f).roundToInt() / 10f
        }

        val (condition, label) = when {
            memInfo.lowMemory || pct >= 92f -> HealthCondition.CRITICAL to "High Memory Pressure"
            pct >= 80f -> HealthCondition.WARNING to "Elevated Usage"
            pct >= 60f -> HealthCondition.INFO to "Balanced"
            else -> HealthCondition.NORMAL to "Healthy"
        }

        return RamSnapshot(
            totalBytes = total,
            availableBytes = avail,
            usedBytes = used,
            usagePercent = pct,
            thresholdBytes = memInfo.threshold,
            isLowMemory = memInfo.lowMemory,
            condition = condition,
            conditionLabel = label
        )
    }
}

data class StorageSnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val usedPercent: Float,
    val remainingPercent: Float,
    val blockSizeBytes: Long,
    val totalBlocks: Long,
    val availableBlocks: Long,
    val internalPath: String,
    val condition: HealthCondition,
    val conditionLabel: String
)

/**
 * MODULE 16 — STORAGE MONITOR (STRICTLY READ-ONLY)
 * Displays internal storage telemetry via StatFs without any file deletion, cache cleaning, or modification.
 */
class StorageMonitor {

    fun sample(): StorageSnapshot {
        val dataDir = Environment.getDataDirectory()
        val statFs = StatFs(dataDir.path)

        val total = statFs.totalBytes.coerceAtLeast(1L)
        val available = statFs.availableBytes.coerceAtLeast(0L)
        val used = (total - available).coerceAtLeast(0L)
        val usedPct = ((used.toDouble() / total.toDouble()) * 100.0).toFloat().let {
            (it * 10f).roundToInt() / 10f
        }
        val remPct = (100f - usedPct).coerceAtLeast(0f).let {
            (it * 10f).roundToInt() / 10f
        }

        val (condition, label) = when {
            usedPct >= 93f -> HealthCondition.CRITICAL to "Critically Low Space"
            usedPct >= 84f -> HealthCondition.WARNING to "Storage Filling Up"
            else -> HealthCondition.NORMAL to "Healthy Capacity"
        }

        return StorageSnapshot(
            totalBytes = total,
            usedBytes = used,
            availableBytes = available,
            usedPercent = usedPct,
            remainingPercent = remPct,
            blockSizeBytes = statFs.blockSizeLong,
            totalBlocks = statFs.blockCountLong,
            availableBlocks = statFs.availableBlocksLong,
            internalPath = dataDir.absolutePath,
            condition = condition,
            conditionLabel = label
        )
    }
}
