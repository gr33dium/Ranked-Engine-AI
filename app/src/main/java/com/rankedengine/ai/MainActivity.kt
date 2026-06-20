package com.rankedengine.ai

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SCREEN_CAPTURE_REQ_CODE = 202
    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val tvKeyHelp = findViewById<TextView>(R.id.tvKeyHelp)
        val rgPromptMode = findViewById<RadioGroup>(R.id.rgPromptMode)
        val rbSystemPrompt = findViewById<RadioButton>(R.id.rbSystemPrompt)
        val rbCustomPrompt = findViewById<RadioButton>(R.id.rbCustomPrompt)
        val llSystemPromptFields = findViewById<LinearLayout>(R.id.llSystemPromptFields)
        val etCustomPrompt = findViewById<EditText>(R.id.etCustomPrompt)
        val etSystemPromptPreview = findViewById<EditText>(R.id.etSystemPromptPreview)

        val etPlayer1Nick = findViewById<EditText>(R.id.etPlayer1Nick)
        val etPlayer1Brawlers = findViewById<EditText>(R.id.etPlayer1Brawlers)
        val etPlayer2Nick = findViewById<EditText>(R.id.etPlayer2Nick)
        val etPlayer2Brawlers = findViewById<EditText>(R.id.etPlayer2Brawlers)
        val etPlayer3Nick = findViewById<EditText>(R.id.etPlayer3Nick)
        val etPlayer3Brawlers = findViewById<EditText>(R.id.etPlayer3Brawlers)

        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnStartEngine = findViewById<Button>(R.id.btnStartEngine)
        val btnStopEngine = findViewById<Button>(R.id.btnStopEngine)

        try {
            val rawTemplate = assets.open("system_prompt_template.txt").bufferedReader().use { it.readText() }
            etSystemPromptPreview.setText(rawTemplate)
        } catch (e: Exception) {
            etSystemPromptPreview.setText("Ошибка загрузки файла шаблона промпта.")
        }

        val sharedPrefs = getSharedPreferences("RankedEnginePrefs", Context.MODE_PRIVATE)
        etApiKey.setText(sharedPrefs.getString("api_key", ""))
        etCustomPrompt.setText(sharedPrefs.getString("custom_prompt", ""))
        etPlayer1Nick.setText(sharedPrefs.getString("p1_nick", ""))
        etPlayer1Brawlers.setText(sharedPrefs.getString("p1_brawlers", ""))
        etPlayer2Nick.setText(sharedPrefs.getString("p2_nick", ""))
        etPlayer2Brawlers.setText(sharedPrefs.getString("p2_brawlers", ""))
        etPlayer3Nick.setText(sharedPrefs.getString("p3_nick", ""))
        etPlayer3Brawlers.setText(sharedPrefs.getString("p3_brawlers", ""))

        if (sharedPrefs.getBoolean("is_custom_prompt", false)) {
            rbCustomPrompt.isChecked = true
            etCustomPrompt.visibility = View.VISIBLE
            llSystemPromptFields.visibility = View.GONE
        }

        rgPromptMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbCustomPrompt) {
                etCustomPrompt.visibility = View.VISIBLE
                llSystemPromptFields.visibility = View.GONE
            } else {
                etCustomPrompt.visibility = View.GONE
                llSystemPromptFields.visibility = View.VISIBLE
            }
        }

        tvKeyHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Инфо")
                .setMessage("Ключ необходим для прямой связи с моделью.")
                .setPositiveButton("Понял", null).show()
        }

        val saveAction = {
            sharedPrefs.edit().apply {
                putString("api_key", etApiKey.text.toString().trim())
                putString("custom_prompt", etCustomPrompt.text.toString().trim())
                putBoolean("is_custom_prompt", rbCustomPrompt.isChecked)
                putString("p1_nick", etPlayer1Nick.text.toString().trim())
                putString("p1_brawlers", etPlayer1Brawlers.text.toString().trim())
                putString("p2_nick", etPlayer2Nick.text.toString().trim())
                putString("p2_brawlers", etPlayer2Brawlers.text.toString().trim())
                putString("p3_nick", etPlayer3Nick.text.toString().trim())
                putString("p3_brawlers", etPlayer3Brawlers.text.toString().trim())
                apply()
            }
        }

        btnSave.setOnClickListener {
            saveAction()
            Toast.makeText(this, "Конфигурация пулов сохранена!", Toast.LENGTH_SHORT).show()
        }

        btnStartEngine.setOnClickListener {
            saveAction()
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ_CODE)
        }

        // Кнопка отключения демонстрации и уничтожения сервиса из шторки
        btnStopEngine.setOnClickListener {
            val stopIntent = Intent(this, RankedNotificationService::class.java)
            stopService(stopIntent)
            Toast.makeText(this, "Демонстрация экрана отключена.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQ_CODE && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, RankedNotificationService::class.java).apply {
                action = "ACTION_START"
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA_INTENT", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            moveTaskToBack(true)
        }
    }
}
