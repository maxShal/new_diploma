package com.example.new_diploma

import android.Manifest
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.api.Usage
import java.util.*

class BlockerService : Service() {

    companion object {
        private const val TAG = "BlockerService"

        // канал для foreground-сервиса
        private const val SERVICE_CHANNEL_ID   = "focus_blocker_service_channel"
        private const val SERVICE_CHANNEL_NAME = "Focus Blocker Service"
        private const val SERVICE_NOTIF_ID     = 1

        // канал для экрана блокировки
        private const val BLOCK_CHANNEL_ID     = "focus_blocker_block_channel"
        private const val BLOCK_CHANNEL_NAME   = "Focus Blocker Alerts"
        private const val BLOCK_NOTIF_ID       = 2
    }

    private var timer: Timer? = null
    private val blockedApps = setOf("org.telegram.messenger")
    private var previousApp: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMin = intent?.getIntExtra("duration", 0) ?: 0
        val finishAt = System.currentTimeMillis() + durationMin * 60_000L

        // уведомление для foreground-сервиса (LOW)
        val svcNotif = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Фокус-режим активен")
            .setContentText("Осталось $durationMin мин")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SERVICE_NOTIF_ID,
                svcNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(SERVICE_NOTIF_ID, svcNotif)
        }

        timer?.cancel()
        timer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
                override fun run() {
                    if (System.currentTimeMillis() > finishAt) {
                        stopSelf()
                        timer?.cancel()
                        return
                    }
                    val current = getCurrentForegroundApp()
                    Log.d(TAG, "Проверка: recentApp = $current")

                    // пропускаем своё приложение
                    if (current == applicationContext.packageName) {
                        return
                    }

                    // блокируем только при первом входе Telegram в foreground
                    if (current != null && blockedApps.contains(current)) {
                        Log.d(TAG, "Блокировка: $current")
                        showBlockScreenNotification()
                    }
                }
            }, 0, 1_000L)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Возвращает packageName последнего приложения, перешедшего в foreground
     * за последние 10 секунд
     */
    private fun getCurrentForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000L
        val events = usm.queryEvents(begin, end)
        val ev = UsageEvents.Event()
        //val ev = Usage,既
        var lastPkg: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPkg = ev.packageName
            }
        }
        Log.d(TAG, "getCurrentForegroundApp: lastPkg=$lastPkg")
        return lastPkg
    }

    /**
     * Полноэкранное уведомление, запускающее BlockScreenActivity даже из фонового состояния
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showBlockScreenNotification() {
        Log.d(TAG, "showBlockScreenNotification()")
        val intent = Intent(this, BlockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, BLOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Фокус-режим: запрещено")
            .setContentText("Нельзя открыть Telegram")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(BLOCK_NOTIF_ID, notif)
    }

    /** Два канала: LOW для сервиса и HIGH для блокировки */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val svcChan = NotificationChannel(
            SERVICE_CHANNEL_ID,
            SERVICE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Сервис фокус-режима"
            setSound(null, null);
            enableVibration(false)
        }
        val blkChan = NotificationChannel(
            BLOCK_CHANNEL_ID,
            BLOCK_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о блокировке"
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
        }

        nm.createNotificationChannel(svcChan)
        nm.createNotificationChannel(blkChan)
    }
}
