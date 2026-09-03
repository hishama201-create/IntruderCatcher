package com.sabri.intrudercatcher

/**
 * V15: حالة داخل الذاكرة فقط (تُمسح فور مغادرة كل شاشات IntruderCatcher
 * لأي تطبيق آخر - يُدار عبر [AppForegroundTracker]) تتبّع هل المستخدم
 * فتح قفل IntruderCatcher نفسه في هذه الجلسة الحالية أم لا.
 */
object SelfLockSession {
    @Volatile
    private var unlocked = false

    fun isUnlocked(): Boolean = unlocked

    fun markUnlocked() {
        unlocked = true
    }

    fun clear() {
        unlocked = false
    }
}
