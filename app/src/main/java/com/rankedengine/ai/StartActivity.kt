package com.rankedengine.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class StartActivity : AppCompatActivity() {

    private val PUSH_REQ_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val tvStatusTitle = findViewById<TextView>(R.id.tvStatusTitle)
        val tvInstruction = findViewById<TextView>(R.id.tvInstruction)
        val btnAction = findViewById<Button>(R.id.btnAction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            
            tvStatusTitle.text = "Нужно разрешение"
            tvInstruction.text = "Для вывода интерактивной кнопки создания скриншотов в шторку приложению требуется доступ к отправке уведомлений.\n\nЕсли возникнут проблемы, загляните в наш Telegram-канал."
            btnAction.text = "Предоставить доступ"
            btnAction.setOnClickListener {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PUSH_REQ_CODE)
            }
        } else {
            tvStatusTitle.text = "Спасибо за установку!"
            tvInstruction.text = "Приложение успешно настроено и готово к интеграции драфтов.\n\nПолный разбор механики работы и гайд по веб-поиску ИИ смотрите в закрепленных сообщениях канала."
            btnAction.text = "Запустить конфигуратор"
            btnAction.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PUSH_REQ_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recreate()
        }
    }
}
