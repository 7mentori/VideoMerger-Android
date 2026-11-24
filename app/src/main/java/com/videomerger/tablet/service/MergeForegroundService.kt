package com.videomerger.tablet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.videomerger.tablet.MainActivity
import com.videomerger.tablet.R
import com.videomerger.tablet.app.AppPreferences
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MergeForegroundService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val workerStarted = AtomicBoolean(false)
    private var currentNotificationText = "影片合併中"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MergeRuntime.requestStop()
            return START_NOT_STICKY
        }

        val notification = buildNotification(currentNotificationText)
        when {
            Build.VERSION.SDK_INT >= 35 -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
            Build.VERSION.SDK_INT >= 34 -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            else -> startForeground(NOTIFICATION_ID, notification)
        }

        val request = MergeRuntime.takeRequest()
        if (request == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (workerStarted.compareAndSet(false, true)) {
            executor.execute {
                try {
                    MergeCoordinator(applicationContext).execute(request) { text ->
                        currentNotificationText = text
                        getSystemService(NotificationManager::class.java).notify(
                            NOTIFICATION_ID,
                            buildNotification(text)
                        )
                    }
                    playCompletionFeedbackIfEnabled()
                } catch (error: Exception) {
                    MergeRuntime.resetAfterServiceFailure(error.message ?: error.javaClass.simpleName)
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        MergeRuntime.requestStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, MergeForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("影片合併中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .build()
    }

    private fun playCompletionFeedbackIfEnabled() {
        val state = MergeRuntime.state.value
        if (!state.finished || state.wasStopped) return
        val prefs = AppPreferences(this)
        if (prefs.soundEnabled) {
            runCatching {
                val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 70)
                tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 450)
                android.os.Handler(mainLooper).postDelayed({ tone.release() }, 600L)
            }
        }
        if (prefs.vibrationEnabled) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                    getSystemService(android.os.VibratorManager::class.java).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(350L, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                ) else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(350L)
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.videomerger.tablet.action.STOP"
        private const val CHANNEL_ID = "video_merger_work"
        private const val NOTIFICATION_ID = 4201
    }
}
