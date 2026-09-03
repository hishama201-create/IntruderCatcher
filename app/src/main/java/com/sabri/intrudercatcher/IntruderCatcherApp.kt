package com.sabri.intrudercatcher

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * V15: نحتاج نعرف تحديدًا متى غادر المستخدم IntruderCatcher بالكامل
 * (رجع للهوم، فتح تطبيق تاني...) لا فرق بين شاشتين داخليتين بنفس
 * التطبيق (مثلًا MainActivity -> GalleryActivity) - عشان نقفل قفل
 * IntruderCatcher نفسه فقط لما يرجع من برّه، مو في كل تنقل داخلي.
 * عدّاد الأنشطة "المبدوءة" هو الطريقة القياسية لفعل هذا بدون أي
 * صلاحية أو مكتبة إضافية.
 */
class IntruderCatcherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppForegroundTracker)
    }
}

object AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    private var startedCount = 0

    val isAppInForeground: Boolean
        get() = startedCount > 0

    override fun onActivityStarted(activity: Activity) {
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
        if (startedCount <= 0) {
            startedCount = 0
            // التطبيق كامل غادر المقدمة - نلغي جلسة قفل IntruderCatcher نفسه
            SelfLockSession.clear()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
