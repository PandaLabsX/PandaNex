package com.example.automation

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.PandaNexApplication
import com.example.R
import com.example.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * User-visible Foreground Service when background gesture & alert monitoring is explicitly enabled by user.
 * Stops cleanly as soon as disabled to save battery and respect Android background execution limits.
 */
class SensorMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val app = application as PandaNexApplication
        NotificationChannels.createChannels(this)

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            901,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_pandanex_notify)
            .setContentTitle("PandaNex Active Control")
            .setContentText("Monitoring Quick Capture, Flip-to-DND, Auto-Dim & Edge Glow")
            .setOngoing(true)
            .setColor(0xFF00C8D7.toInt())
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        app.container.automationEngine.startForegroundInteractiveMonitoring()
        app.container.quickCaptureController.setBackgroundServiceActive(true)
        app.container.edgeGlowManager.syncTelephonyCallListener()
        app.container.aquaDynamicsManager.syncTelephonyCallListener()
        app.container.fullScreenDetector.syncProbeAndMonitorLoop()

        // Fast lightweight foreground-app & full-screen watcher for Auto-Dimmed Apps & Aqua Dynamics suppression
        serviceScope.launch {
            while (isActive) {
                app.container.fullScreenDetector.evaluateNow()
                app.container.aquaDynamicsManager.refreshEffectiveCallState()
                app.container.autoDimController.pollForegroundAppOnce()
                delay(1000L)
            }
        }

        // Periodic lightweight health alert evaluation every 20 seconds while active
        serviceScope.launch {
            while (isActive) {
                val c = app.container
                val cpu = c.cpuMonitor.sample()
                val ram = c.ramMonitor.sample()
                val bat = c.batteryMonitor.sampleNow()
                val therm = c.thermalMonitor.sample()
                val storage = c.storageMonitor.sample()
                val net = c.networkMonitor.sampleNetworkInfo()
                c.alertEngine.evaluateTelemetry(cpu, ram, bat, therm, storage, net.isConnected)
                c.aquaDynamicsManager.onLiveTelemetrySampled(cpu, bat, therm, net)
                delay(20_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val app = application as PandaNexApplication
        app.container.automationEngine.stopForegroundInteractiveMonitoring()
        app.container.quickCaptureController.setBackgroundServiceActive(false)
        app.container.autoDimController.restoreNormalBrightness("Background service stopped")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1042
        private const val ACTION_STOP = "com.example.pandanex.STOP_MONITORING"

        fun setRunning(context: Context, enabled: Boolean) {
            try {
                if (enabled) {
                    val intent = Intent(context, SensorMonitoringService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    val stopIntent = Intent(context, SensorMonitoringService::class.java).apply {
                        action = ACTION_STOP
                    }
                    context.stopService(stopIntent)
                }
            } catch (_: Exception) {
                // Respect OS background start restrictions gracefully
            }
        }
    }
}
