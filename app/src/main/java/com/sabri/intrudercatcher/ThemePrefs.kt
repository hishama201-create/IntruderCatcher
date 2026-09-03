package com.sabri.intrudercatcher

import android.content.Context

/**
 * V15: تخزين بسيط (غير مشفّر - الألوان مو بيانات حساسة) للون المميز
 * (accent) اللي اختاره المستخدم. باقي ألوان الواجهة (الخلفية، شريط
 * العنوان، لون النص الثانوي...) تُشتق تلقائيًا من هذا اللون الواحد
 * عبر [ThemeManager] لضمان تناسق الشكل دائمًا مهما كان اللون المختار.
 */
object ThemePrefs {

    private const val FILE_NAME = "theme_prefs"
    private const val KEY_ACCENT_COLOR = "accent_color"

    /** اللون الافتراضي الأصلي للتطبيق (البنفسجي) - يطابق تمامًا الألوان القديمة المكتوبة يدويًا بالتصميم */
    const val DEFAULT_ACCENT = 0xFF9C6BFF.toInt()

    fun getAccentColor(context: Context): Int =
        prefs(context).getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT)

    fun setAccentColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_ACCENT_COLOR, color).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
