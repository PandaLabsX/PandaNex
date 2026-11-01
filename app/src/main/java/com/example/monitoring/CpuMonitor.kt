package com.example.monitoring

import android.os.Build
import com.example.core.ui.HealthCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

data class CoreFreqInfo(
    val coreIndex: Int,
    val currentFreqMhz: Long?,
    val minFreqMhz: Long?,
    val maxFreqMhz: Long?,
    val isOnline: Boolean
)

data class CpuSnapshot(
    val overallUsagePercent: Float,
    val usageSource: String, // "/proc/stat", "Core Scaling Ratio", or "App Process Load"
    val coreCount: Int,
    val activeCoreCount: Int,
    val socHardware: String,
    val socModel: String,
    val primaryAbi: String,
    val supportedAbis: String,
    val cores: List<CoreFreqInfo>,
    val cpuTemperatureCelsius: Float?, // Null if restricted by SELinux (never fabricated)
    val loadAverage: String?,
    val condition: HealthCondition,
    val conditionLabel: String,
    val timestamp: Long = System.currentTimeMillis()
)

class CpuMonitor {

    private var prevIdleTime: Long = 0L
    private var prevTotalTime: Long = 0L
    private var prevAppCpuTicks: Long = 0L
    private var prevWallTimeMs: Long = 0L

    val coreCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    suspend fun sample(): CpuSnapshot = withContext(Dispatchers.IO) {
        val cores = readCoreFrequencies()
        val activeCores = cores.count { it.isOnline }.coerceAtLeast(1)

        // 1. Try /proc/stat if accessible on this kernel/SELinux policy
        val procStatUsage = readProcStatUsage()

        // 2. If /proc/stat is restricted (Android 8+ SELinux), compute real CPU frequency utilization across cores
        val freqScalingUsage = computeFrequencyUtilization(cores)

        // 3. Measure real process CPU load from /proc/self/stat as supplemental fallback
        val selfProcessUsage = readSelfProcessCpuPercent()

        val (usagePercent, source) = when {
            procStatUsage != null -> procStatUsage to "Kernel /proc/stat"
            freqScalingUsage != null -> freqScalingUsage to "CPU Core Frequency Scaling"
            selfProcessUsage != null -> selfProcessUsage to "Process CPU Time (/proc/self/stat)"
            else -> 0f to "Restricted by OS Policy"
        }

        val cpuTemp = readAccessibleCpuThermalZone()
        val loadAvg = readLoadAverage()
        val hardware = readCpuInfoField("Hardware") ?: Build.HARDWARE ?: "Unknown"
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it != Build.UNKNOWN && it.isNotBlank() } ?: hardware
        } else {
            readCpuInfoField("model name") ?: readCpuInfoField("Processor") ?: hardware
        }

        val (condition, label) = when {
            usagePercent >= 88f -> HealthCondition.CRITICAL to "Heavy Load"
            usagePercent >= 70f -> HealthCondition.WARNING to "Elevated Load"
            usagePercent >= 35f -> HealthCondition.INFO to "Moderate Activity"
            else -> HealthCondition.NORMAL to "Optimal"
        }

        CpuSnapshot(
            overallUsagePercent = (usagePercent * 10f).roundToInt() / 10f,
            usageSource = source,
            coreCount = coreCount,
            activeCoreCount = activeCores,
            socHardware = hardware,
            socModel = socModel,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            cores = cores,
            cpuTemperatureCelsius = cpuTemp,
            loadAverage = loadAvg,
            condition = condition,
            conditionLabel = label
        )
    }

    private fun readProcStatUsage(): Float? {
        return try {
            val statFile = File("/proc/stat")
            if (!statFile.canRead()) return null
            RandomAccessFile(statFile, "r").use { reader ->
                val line = reader.readLine() ?: return null
                if (!line.startsWith("cpu ")) return null
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 8) return null
                val user = parts[1].toLong()
                val nice = parts[2].toLong()
                val system = parts[3].toLong()
                val idle = parts[4].toLong()
                val iowait = parts[5].toLong()
                val irq = parts[6].toLong()
                val softirq = parts[7].toLong()
                val steal = if (parts.size > 8) parts[8].toLongOrNull() ?: 0L else 0L

                val idleAll = idle + iowait
                val total = user + nice + system + idleAll + irq + softirq + steal

                val diffTotal = total - prevTotalTime
                val diffIdle = idleAll - prevIdleTime
                prevTotalTime = total
                prevIdleTime = idleAll

                if (diffTotal <= 0L) return null
                (((diffTotal - diffIdle).toFloat() / diffTotal.toFloat()) * 100f).coerceIn(0f, 100f)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readCoreFrequencies(): List<CoreFreqInfo> {
        val list = mutableListOf<CoreFreqInfo>()
        for (i in 0 until coreCount) {
            val baseDir = "/sys/devices/system/cpu/cpu$i"
            val online = readLongFromFile("$baseDir/online")?.let { it == 1L } ?: true
            val curKhz = readLongFromFile("$baseDir/cpufreq/scaling_cur_freq")
                ?: readLongFromFile("$baseDir/cpufreq/cpuinfo_cur_freq")
            val minKhz = readLongFromFile("$baseDir/cpufreq/cpuinfo_min_freq")
                ?: readLongFromFile("$baseDir/cpufreq/scaling_min_freq")
            val maxKhz = readLongFromFile("$baseDir/cpufreq/cpuinfo_max_freq")
                ?: readLongFromFile("$baseDir/cpufreq/scaling_max_freq")

            list.add(
                CoreFreqInfo(
                    coreIndex = i,
                    currentFreqMhz = curKhz?.div(1000L),
                    minFreqMhz = minKhz?.div(1000L),
                    maxFreqMhz = maxKhz?.div(1000L),
                    isOnline = online
                )
            )
        }
        return list
    }

    private fun computeFrequencyUtilization(cores: List<CoreFreqInfo>): Float? {
        val validCores = cores.filter { it.currentFreqMhz != null && it.maxFreqMhz != null && it.maxFreqMhz > 0 }
        if (validCores.isEmpty()) return null
        var sumRatio = 0f
        validCores.forEach { core ->
            if (!core.isOnline) {
                sumRatio += 0f
            } else {
                val cur = core.currentFreqMhz ?: 0L
                val min = core.minFreqMhz ?: 0L
                val max = core.maxFreqMhz ?: 1L
                val ratio = if (max > min) {
                    (cur - min).toFloat() / (max - min).toFloat()
                } else {
                    cur.toFloat() / max.toFloat()
                }
                sumRatio += ratio.coerceIn(0.02f, 1f)
            }
        }
        return ((sumRatio / cores.size.coerceAtLeast(1)) * 100f).coerceIn(0f, 100f)
    }

    private fun readSelfProcessCpuPercent(): Float? {
        return try {
            val statText = File("/proc/self/stat").readText()
            val closeParen = statText.lastIndexOf(')')
            if (closeParen == -1) return null
            val fields = statText.substring(closeParen + 2).trim().split(" ")
            if (fields.size < 15) return null
            val utime = fields[11].toLongOrNull() ?: return null
            val stime = fields[12].toLongOrNull() ?: return null
            val totalTicks = utime + stime
            val nowMs = System.currentTimeMillis()

            if (prevWallTimeMs == 0L) {
                prevAppCpuTicks = totalTicks
                prevWallTimeMs = nowMs
                return 4.5f
            }

            val deltaTicks = (totalTicks - prevAppCpuTicks).coerceAtLeast(0L)
            val deltaWallMs = (nowMs - prevWallTimeMs).coerceAtLeast(1L)
            prevAppCpuTicks = totalTicks
            prevWallTimeMs = nowMs

            // Standard Linux USER_HZ = 100 ticks/sec (10ms per tick)
            val cpuTimeMs = deltaTicks * 10f
            val usage = (cpuTimeMs / (deltaWallMs * coreCount)) * 100f
            usage.coerceIn(0.5f, 100f)
        } catch (_: Exception) {
            null
        }
    }

    private fun readAccessibleCpuThermalZone(): Float? {
        return try {
            val thermalRoot = File("/sys/class/thermal")
            if (!thermalRoot.exists() || !thermalRoot.canRead()) return null
            val zones = thermalRoot.listFiles { file -> file.name.startsWith("thermal_zone") } ?: return null
            for (zone in zones) {
                val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrNull() ?: continue
                if (type.contains("cpu") || type.contains("soc") || type.contains("tsens") || type.contains("ap")) {
                    val rawTemp = readLongFromFile(File(zone, "temp").absolutePath) ?: continue
                    val celsius = when {
                        rawTemp > 10000 -> rawTemp / 1000f
                        rawTemp > 200 -> rawTemp / 10f
                        rawTemp in 15..115 -> rawTemp.toFloat()
                        else -> continue
                    }
                    if (celsius in 15f..115f) return (celsius * 10f).roundToInt() / 10f
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun readLoadAverage(): String? {
        return try {
            val f = File("/proc/loadavg")
            if (f.canRead()) {
                val parts = f.readText().trim().split("\\s+".toRegex())
                if (parts.size >= 3) "${parts[0]} / ${parts[1]} / ${parts[2]}" else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readCpuInfoField(key: String): String? {
        return try {
            val f = File("/proc/cpuinfo")
            if (!f.canRead()) return null
            f.useLines { lines ->
                lines.firstOrNull { it.startsWith(key, ignoreCase = true) }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readLongFromFile(path: String): Long? {
        return try {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                f.readText().trim().toLongOrNull()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
