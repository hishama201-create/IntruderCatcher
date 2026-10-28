package com.sabri.intrudercatcher

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * تخزين مشفّر (AES-256) لقائمة التطبيقات المقفولة ورمز PIN.
 * الـ PIN لا يُخزَّن أبدًا كنص صريح - فقط بصمة SHA-256 منه.
 */
object AppLockPrefs {

    private const val FILE_NAME = "app_lock_secure_prefs"
    private const val KEY_LOCKED_APPS = "locked_apps"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PATTERN_HASH = "pattern_hash"
    private const val KEY_LOCK_TYPE = "lock_type"
    private const val KEY_USE_BIOMETRIC = "use_biometric"

    const val LOCK_TYPE_PIN = "pin"
    const val LOCK_TYPE_PATTERN = "pattern"

    // V12: إعدادات إعادة القفل - قابلة للتخصيص لكل تطبيق على حدة
    private const val KEY_RELOCK_MODE_PREFIX = "relock_mode_"
    private const val KEY_RELOCK_MINUTES_PREFIX = "relock_minutes_"
    const val RELOCK_IMMEDIATE = "immediate"
    const val RELOCK_SCREEN_OFF = "screen_off"
    const val RELOCK_AFTER_MINUTES = "after_minutes"

    // V13: إعداد عام افتراضي (تبويب الإعدادات) يُطبَّق تلقائيًا على أي
    // تطبيق جديد يتقفل ما لم يكن له إعداد خاص محفوظ مسبقًا
    private const val KEY_DEFAULT_RELOCK_MODE = "default_relock_mode"
    private const val KEY_DEFAULT_RELOCK_MINUTES = "default_relock_minutes"

    // V15: قفل IntruderCatcher نفسه عند فتحه (نفس PIN/نمط قفل التطبيقات الأخرى)
    private const val KEY_SELF_LOCK_ENABLED = "self_lock_enabled"

    // V15: علامة تُرفع لحظة تعطيل صلاحية مدير الجهاز من خارج التطبيق،
    // حتى نعرض تنبيه قوي لإعادة التفعيل في أول فرصة يفتح فيها المستخدم التطبيق
    private const val KEY_ADMIN_WAS_DISABLED = "admin_was_disabled"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getLockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet()

    fun setLockedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_LOCKED_APPS, packages).apply()
    }

    fun isAppLocked(context: Context, packageName: String): Boolean =
        getLockedApps(context).contains(packageName)

    fun hasPin(context: Context): Boolean =
        prefs(context).contains(KEY_PIN_HASH)

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) == hash(pin)

    /** تخزين منفصل ومشفّر خاص بالنمط - لا علاقة له بتخزين الـ PIN */
    fun hasPattern(context: Context): Boolean =
        prefs(context).contains(KEY_PATTERN_HASH)

    fun setPattern(context: Context, pattern: List<Int>) {
        prefs(context).edit().putString(KEY_PATTERN_HASH, hash(pattern.joinToString(","))).apply()
    }

    fun verifyPattern(context: Context, pattern: List<Int>): Boolean =
        prefs(context).getString(KEY_PATTERN_HASH, null) == hash(pattern.joinToString(","))

    /** أي طريقة قفل فعّالة حاليًا: PIN أو نمط (يُحدَّد وقت آخر إعداد ناجح) */
    fun setLockType(context: Context, type: String) {
        prefs(context).edit().putString(KEY_LOCK_TYPE, type).apply()
    }

    fun getLockType(context: Context): String =
        prefs(context).getString(KEY_LOCK_TYPE, LOCK_TYPE_PIN) ?: LOCK_TYPE_PIN

    /** فيه أي طريقة قفل مُعدّة أصلًا (PIN أو نمط)، بغض النظر عن أيهما فعّال حاليًا */
    fun hasLockConfigured(context: Context): Boolean = hasPin(context) || hasPattern(context)

    fun setUseBiometric(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_BIOMETRIC, enabled).apply()
    }

    fun useBiometric(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_BIOMETRIC, false)

    /** طريقة إعادة القفل لتطبيق معيّن - لو ما فيه إعداد خاص، نرجع للإعداد العام الافتراضي */
    fun getRelockMode(context: Context, packageName: String): String =
        prefs(context).getString(KEY_RELOCK_MODE_PREFIX + packageName, null)
            ?: getDefaultRelockMode(context)

    fun setRelockMode(context: Context, packageName: String, mode: String) {
        prefs(context).edit().putString(KEY_RELOCK_MODE_PREFIX + packageName, mode).apply()
    }

    fun getRelockMinutes(context: Context, packageName: String): Int =
        if (prefs(context).contains(KEY_RELOCK_MODE_PREFIX + packageName)) {
            prefs(context).getInt(KEY_RELOCK_MINUTES_PREFIX + packageName, 1)
        } else {
            getDefaultRelockMinutes(context)
        }

    fun setRelockMinutes(context: Context, packageName: String, minutes: Int) {
        prefs(context).edit().putInt(KEY_RELOCK_MINUTES_PREFIX + packageName, minutes).apply()
    }

    /** V50: مسح الإعدادات الخاصة بكل تطبيق ليطبَّق الإعداد الافتراضي على الجميع */
    fun clearAllAppRelockOverrides(context: Context) {
        val p = prefs(context)
        val editor = p.edit()
        p.all.keys
            .filter { it.startsWith(KEY_RELOCK_MODE_PREFIX) || it.startsWith(KEY_RELOCK_MINUTES_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /** V13: الإعداد العام الافتراضي (تبويب الإعدادات) - على الفور ما لم يُغيَّر */
    fun getDefaultRelockMode(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_RELOCK_MODE, RELOCK_IMMEDIATE) ?: RELOCK_IMMEDIATE

    fun setDefaultRelockMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_DEFAULT_RELOCK_MODE, mode).apply()
    }

    fun getDefaultRelockMinutes(context: Context): Int =
        prefs(context).getInt(KEY_DEFAULT_RELOCK_MINUTES, 1)

    fun setDefaultRelockMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_DEFAULT_RELOCK_MINUTES, minutes).apply()
    }

    /** V15: قفل تطبيق IntruderCatcher نفسه عند فتحه - يستخدم نفس PIN/نمط قفل التطبيقات الأخرى */
    fun isSelfLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SELF_LOCK_ENABLED, false)

    fun setSelfLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SELF_LOCK_ENABLED, enabled).apply()
    }

    fun wasAdminDisabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADMIN_WAS_DISABLED, false)

    fun setAdminWasDisabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADMIN_WAS_DISABLED, value).apply()
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
