package com.sabri.intrudercatcher

import android.content.Context

/**
 * حالة داخل الذاكرة فقط (تُمسح عند إطفاء الشاشة أو إغلاق التطبيق):
 * تتبّع أي تطبيقات مقفولة تم فتحها بنجاح في الجلسة الحالية، وتحدد هل
 * لسه "مفتوحة" حسب إعداد إعادة القفل الخاص بكل تطبيق (V12):
 * - على الفور: تُقفل فور مغادرة التطبيق مباشرة.
 * - بعد غلق الشاشة: تبقى مفتوحة لحد ما تُطفأ الشاشة (السلوك الأصلي).
 * - بعد N دقيقة: تبقى مفتوحة لمدة محددة بعد آخر مغادرة، ثم تُقفل تلقائيًا.
 */
object AppLockSession {
    private val unlockedApps = mutableSetOf<String>()
    private val leftAtMillis = mutableMapOf<String, Long>()

    fun isUnlocked(context: Context, packageName: String): Boolean {
        if (!unlockedApps.contains(packageName)) return false
        val leftAt = leftAtMillis[packageName] ?: return true // ما زال بالمقدمة، ما غادر بعد

        return when (AppLockPrefs.getRelockMode(context, packageName)) {
            AppLockPrefs.RELOCK_SCREEN_OFF -> true
            AppLockPrefs.RELOCK_AFTER_MINUTES -> {
                val minutes = AppLockPrefs.getRelockMinutes(context, packageName)
                (System.currentTimeMillis() - leftAt) < minutes * 60_000L
            }
            else -> false // RELOCK_IMMEDIATE
        }
    }

    fun markUnlocked(packageName: String) {
        unlockedApps.add(packageName)
        leftAtMillis.remove(packageName)
    }

    /** يُستدعى عندما يغادر المستخدم تطبيقًا كان مفتوحًا في هذه الجلسة */
    fun markLeft(packageName: String) {
        if (unlockedApps.contains(packageName) && !leftAtMillis.containsKey(packageName)) {
            leftAtMillis[packageName] = System.currentTimeMillis()
        }
    }

    /** يُستدعى عند رجوع المستخدم لتطبيق ما زال ضمن فترة السماح - يلغي عداد المغادرة */
    fun markActive(packageName: String) {
        leftAtMillis.remove(packageName)
    }

    fun clear() {
        unlockedApps.clear()
        leftAtMillis.clear()
    }
}
