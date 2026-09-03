package com.sabri.intrudercatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * V12: يعيد تشغيل خدمة مراقبة قفل التطبيقات تلقائيًا بعد إعادة تشغيل
 * الجهاز، بشرط وجود تطبيقات مقفولة أصلًا وكل الصلاحيات ممنوحة مسبقًا.
 * بدون هذا، كان المستخدم مضطرًا يفتح IntruderCatcher يدويًا بعد كل ريستارت.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val lockedApps = AppLockPrefs.getLockedApps(context)
        if (lockedApps.isNotEmpty() && AppLockPermissions.hasAllPermissions(context)) {
            AppLockMonitorService.start(context)
        }
    }
}
