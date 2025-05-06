package com.example.new_diploma

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

class BlockScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    override fun onPause() {
        super.onPause()
        // Возвращаем экран блокировки, если пользователь пытается свернуть
        val intent = Intent(this, BlockScreenActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
}