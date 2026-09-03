package com.sabri.intrudercatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * خدمة أمامية (Foreground Service) تراقب كل [POLL_INTERVAL_MS] أي تطبيق
 * حاليًا في المقدمة عبر UsageStatsManager (يتطلب صلاحية "Usage Access"
 * تُمنح يدويًا من الإعدادات). لو كان تطبيق مقفول ولم يُفتح بعد في هذه
 * الجلسة، تعرض النافذة العائمة [AppLockOverlayManager] فوقه.
 */
class AppLockMonitorService : Service() {

    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastCheckedPackage: String? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // إطفاء الشاشة = نهاية الجلسة، لازم يعيد التحقق من جديد بمجرد التشغيل
            AppLockSession.clear()
            lastCheckedPackage = null
            AppLockOverlayManager.hide(this@AppLockMonitorService)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        startForegroundWithNotification()
        handler.post(pollRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: IllegalArgumentException) {
        }
        AppLockOverlayManager.hide(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForegroundApp() {
        val lockedApps = AppLockPrefs.getLockedApps(this)
        if (lockedApps.isEmpty()) {
            AppLockOverlayManager.hide(this)
            return
        }

        val currentApp = getCurrentForegroundPackage()

        // ==================== V22: إصلاح أمني مهم ====================
        // getCurrentForegroundPackage() بيرجع null لو مفيش "حدث تبديل تطبيق"
        // جديد خلال آخر ١٠ ثواني بس - وده بيحصل طبيعي جدًا لو المستخدم قاعد
        // واقف على نفس الشاشة (زي شاشة القفل نفسها) من غير ما يعمل أي حاجة.
        // قبل كده، كنا بنفسّر null على إنها "المستخدم خرج من التطبيق" ونقفل
        // نافذة القفل تلقائيًا - وده كان بيسمح بفتح أي تطبيق مقفول لوحده
        // بعد شوية وقوف بدون فعل أي شيء. الإصلاح: null يعني بس "معرفناش
        // حاجة جديدة دلوقتي"، مش "المستخدم غادر" - نتجاهل الدورة دي فقط.
        if (currentApp == null) return

        // لو المستخدم خرج فعليًا من التطبيق المقفول (بدّل لتطبيق تاني فعلاً)، نخفي النافذة
        val shownTarget = AppLockOverlayManager.currentTarget()
        if (shownTarget != null && currentApp != shownTarget) {
            AppLockOverlayManager.hide(this)
        }

        if (currentApp == lastCheckedPackage) return

        // المستخدم غادر آخر تطبيق كان بالمقدمة - نسجّل وقت المغادرة (V12: إعادة القفل حسب الإعداد)
        val previousApp = lastCheckedPackage
        if (previousApp != null && previousApp in lockedApps) {
            AppLockSession.markLeft(previousApp)
        }
        lastCheckedPackage = currentApp

        if (currentApp == packageName) return // ما نقفل تطبيقنا نفسه
        if (currentApp !in lockedApps) return
        if (AppLockSession.isUnlocked(this, currentApp)) {
            // رجع لتطبيق ما زال ضمن فترة السماح - نلغي عداد المغادرة
            AppLockSession.markActive(currentApp)
            return
        }

        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(currentApp, 0)
            ).toString()
        } catch (_: Exception) {
            currentApp
        }

        AppLockOverlayManager.show(this, currentApp, label)
    }

    private fun getCurrentForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.applock_notification_channel),
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.applock_notification_title))
            .setSmallIcon(R.drawable.ic_check_circle)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 400L
        private const val NOTIF_ID = 77
        private const val CHANNEL_ID = "app_lock_monitor_channel"

        fun start(context: Context) {
            val intent = Intent(context, AppLockMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppLockMonitorService::class.java))
        }
    }
}
