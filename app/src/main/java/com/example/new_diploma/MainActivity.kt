package com.example.new_diploma

import android.annotation.SuppressLint
import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var durationInput: EditText
    private lateinit var permissionButton: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        durationInput = findViewById(R.id.durationInput)
        permissionButton = findViewById(R.id.permissionButton)

        startButton.setOnClickListener {
            val minutes = durationInput.text.toString().toIntOrNull()
            if (minutes != null) {
                val intent = Intent(this, BlockerService::class.java)
                intent.putExtra("duration", minutes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "Фокус начат на $minutes мин.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Введите корректное число минут", Toast.LENGTH_SHORT).show()
            }
        }

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Необходимо разрешение на доступ к использованию", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}

class BlockerService : Service() {

    private var timer: Timer? = null
    private val blockedApps = listOf("com.instagram.android", "com.facebook.katana")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val duration = intent?.getIntExtra("duration", 0) ?: 0
        startForeground(1, createNotification())

        val endTime = System.currentTimeMillis() + duration * 60 * 1000
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val end = System.currentTimeMillis()
                val begin = end - 5000
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
                val recentApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName

                Handler(mainLooper).post {
                    Toast.makeText(applicationContext, "Активное: $recentApp", Toast.LENGTH_SHORT).show()
                }

                if (recentApp != null && blockedApps.contains(recentApp)) {
                    Handler(mainLooper).post {
                        Toast.makeText(applicationContext, "Блокировка: $recentApp", Toast.LENGTH_LONG).show()
                    }
                    val blockIntent = Intent(this@BlockerService, BlockScreenActivity::class.java)
                    blockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(blockIntent)
                }

                if (System.currentTimeMillis() > endTime) stopSelf()
            }
        }, 0, 3000)

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "focus_blocker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Focus Blocker", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Фокус режим активен")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class BlockScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "\uD83D\uDD12 Приложение заблокировано!"
            textSize = 24f
            setPadding(40, 200, 40, 40)
            gravity = android.view.Gravity.CENTER
        }
        setContentView(textView)
    }

    override fun onBackPressed() {
        // Блокируем кнопку "Назад"
    }
}
