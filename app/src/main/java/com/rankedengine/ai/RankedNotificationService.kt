package com.rankedengine.ai

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RankedNotificationService : Service() {

    private val CHANNEL_ID = "RankedEngineChannel"
    private val NOTIFICATION_ID = 4004

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var isProjectionInitialized = false
    private val handler = Handler(Looper.getMainLooper())

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            cleanupCaptureResources()
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START" -> {
                val resultCode = intent.getIntExtra("RESULT_CODE", 0)
                val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("DATA_INTENT", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("DATA_INTENT")
                }
                showInitialNotification()
                if (!isProjectionInitialized && dataIntent != null) {
                    handler.postDelayed({
                        try {
                            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, dataIntent)
                            mediaProjection?.registerCallback(mediaProjectionCallback, handler)
                            setupPersistentCaptureStream()
                            isProjectionInitialized = true
                        } catch (e: Exception) {
                            showResultNotification("Ошибка инициализации: ${e.localizedMessage}")
                        }
                    }, 250)
                }
            }
            "ACTION_TAKE_SCREENSHOT" -> startCountdown(3)
        }
        return START_NOT_STICKY
    }

    private fun setupPersistentCaptureStream() {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = (maxOf(metrics.widthPixels, metrics.heightPixels) / 2.5f).toInt()
        val height = (minOf(metrics.widthPixels, metrics.heightPixels) / 2.5f).toInt()

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RankedEngineStream", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
        )
    }

    private fun startCountdown(seconds: Int) {
        if (seconds > 0) {
            updateNotification("Захват через: $seconds...", "Смахните шторку!")
            handler.postDelayed({ startCountdown(seconds - 1) }, 1000)
        } else {
            updateNotification("Анализ ИИ...", "Гуглим мету и строим пики...")
            Thread { captureLatestFrameAndSend() }.start()
        }
    }

    private fun captureLatestFrameAndSend() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowPadding = plane.rowStride - plane.pixelStride * image.width
            val tempBitmap = Bitmap.createBitmap(image.width + rowPadding / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888)
            tempBitmap.copyPixelsFromBuffer(buffer)

            val output = ByteArrayOutputStream()
            val cleanBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, image.width, image.height)
            cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 65, output)
            
            // Безопасно закрываем Image только после полного завершения компрессии в JPEG
            image.close()

            executeGeminiCall(Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
        } catch (e: Exception) {
            try { image.close() } catch (ex: Exception) {}
            Log.e("RankedEngineAI", "Ошибка рендеринга кадра", e)
            handler.post { showResultNotification("Ошибка рендеринга: ${e.localizedMessage}") }
        }
    }

    private fun executeGeminiCall(base64Image: String) {
        val prefs = getSharedPreferences("RankedEnginePrefs", Context.MODE_PRIVATE)
        val key = prefs.getString("api_key", "")?.trim() ?: ""

        if (key.isEmpty()) {
            handler.post { showResultNotification("Ошибка: API ключ пуст!") }
            return
        }

        val p1Nick = prefs.getString("p1_nick", "Игрок 1")?.takeIf { it.isNotEmpty() } ?: "Иగрок 1"
        val p1Brawlers = prefs.getString("p1_brawlers", "Не указаны")?.takeIf { it.isNotEmpty() } ?: "Не указаны"
        val p2Nick = prefs.getString("p2_nick", "Игрок 2")?.takeIf { it.isNotEmpty() } ?: "Игрок 2"
        val p2Brawlers = prefs.getString("p2_brawlers", "Не указаны")?.takeIf { it.isNotEmpty() } ?: "Не указаны"
        val p3Nick = prefs.getString("p3_nick", "Игрок 3")?.takeIf { it.isNotEmpty() } ?: "Игрок 3"
        val p3Brawlers = prefs.getString("p3_brawlers", "Не указаны")?.takeIf { it.isNotEmpty() } ?: "Не указаны"

        val promptText = try {
            val template = assets.open("system_prompt_template.txt").bufferedReader().use { it.readText() }
            template.replace("{nickname_1}", p1Nick).replace("{brawlers_1}", p1Brawlers)
                    .replace("{nickname_2}", p2Nick).replace("{brawlers_2}", p2Brawlers)
                    .replace("{nickname_3}", p3Nick).replace("{brawlers_3}", p3Brawlers)
        } catch (e: Exception) {
            "Анализ драфта: $p1Nick($p1Brawlers), $p2Nick($p2Brawlers), $p3Nick($p3Brawlers). Используй Google Search для меты."
        }
        
        Thread {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$key")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                        .put(JSONObject().put("text", promptText))
                        .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", base64Image)))))
                    )
                    .put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))

                OutputStreamWriter(conn.outputStream).use { it.write(json.toString()); it.flush() }
                
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    handler.post { showResultNotification(text) }
                } else {
                    handler.post { showResultNotification("Ошибка API (${conn.responseCode})") }
                }
                conn.disconnect()
            } catch (e: Exception) {
                handler.post { showResultNotification("Ошибка сети: ${e.localizedMessage}") }
            }
        }.start()
    }

    private fun showInitialNotification() {
        val pIntent = PendingIntent.getService(this, 0, Intent(this, RankedNotificationService::class.java).setAction("ACTION_TAKE_SCREENSHOT"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ranked Engine AI").setContentText("Готов к работе")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_menu_camera, "АНАЛИЗ", pIntent)
        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun showResultNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Аналитика").setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.ic_popup_sync).build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Engine", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun cleanupCaptureResources() {
        virtualDisplay?.release()
        imageReader?.close()
    }

    override fun onDestroy() {
        cleanupCaptureResources()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
