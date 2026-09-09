package dev.timmoc.wifiwarn

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/** 前台应用 + 当前 WiFi 多规则匹配 → 提示音 / 通知 / 悬浮窗 / Toast 警告 */
class WarnService : AccessibilityService() {

    private var lastWarnTs = 0L
    private val cooldownMs = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!Config.isMasterEnabled(this)) return

        val pkg = event.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty()) return

        val ssid = WifiHelper.currentSsid(this) ?: return
        val rule = Config.findRulesForSsid(this, ssid) ?: return
        if (rule.packages.none { it.equals(pkg, ignoreCase = true) }) return

        val now = System.currentTimeMillis()
        if (now - lastWarnTs < cooldownMs) return
        lastWarnTs = now

        warn(rule.ssid, pkg)
    }

    private fun warn(ssid: String, pkg: String) {
        val appName = appLabel(pkg)
        val message = "⚠️ 你在 WiFi「$ssid」下打开了「$appName」"
        val toastText = "⚠️ 已连接 WiFi「$ssid」，打开了「$appName」——请注意使用"

        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show()
        playSound()

        if (Config.isNotifEnabled(this) && hasNotificationPermission()) {
            sendNotification(message)
        }

        if (Config.isOverlayEnabled(this) && Settings.canDrawOverlays(this)) {
            showOverlay(ssid, appName)
        }
    }

    private fun appLabel(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun playSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(this, uri)?.play()
        } catch (_: Exception) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            } catch (_: Exception) {
                Log.w("WifiWarn", "playSound failed")
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun sendNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "wifi_warn"
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val notification = android.app.Notification.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
        } catch (_: Exception) {
            Log.w("WifiWarn", "sendNotification failed")
        }
    }

    private fun showOverlay(ssid: String, appName: String) {
        handler.post {
            try {
                val view = ensureOverlayView()
                view.findViewById<TextView>(R.id.tvOverlayText).text =
                    "⚠️ 你在 WiFi「$ssid」下打开了「$appName」"
                if (view.parent == null) {
                    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.addView(view, overlayParams())
                }
                overlayRunnable?.let { handler.removeCallbacks(it) }
                overlayRunnable = Runnable { removeOverlay() }
                handler.postDelayed(overlayRunnable!!, 3000L)
            } catch (e: Exception) {
                Log.w("WifiWarn", "showOverlay failed: $e")
            }
        }
    }

    private fun ensureOverlayView(): View {
        return overlayView ?: LayoutInflater.from(this).inflate(R.layout.overlay_warning, null).also { view ->
            view.findViewById<TextView>(R.id.btnCloseOverlay).setOnClickListener { removeOverlay() }
            overlayView = view
        }
    }

    private fun overlayParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = 120
        }

    private fun removeOverlay() {
        overlayRunnable?.let { handler.removeCallbacks(it) }
        overlayRunnable = null
        val view = overlayView ?: return
        try {
            if (view.parent != null) {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
            }
        } catch (_: Exception) {
            Log.w("WifiWarn", "removeOverlay failed")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
