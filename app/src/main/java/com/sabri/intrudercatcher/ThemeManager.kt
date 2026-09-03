package com.sabri.intrudercatcher

import android.app.Activity
import android.content.Context
import android.graphics.Color

/**
 * V15: نظام ألوان مبني على لون "مميز" (Accent) واحد فقط يختاره المستخدم -
 * إما من قائمة ألوان رسمية جاهزة أو لون مخصّص بالكامل عبر منزلقات RGB.
 * باقي ألوان الواجهة (الخلفية، شريط العنوان، النص الثانوي، الفواصل...)
 * تُشتق تلقائيًا من نفس اللون عبر HSV، فتبقى دائمًا متناسقة مع بعضها
 * مهما كان اللون المختار - بدل ما المستخدم يضطر يختار ٨ ألوان يدويًا.
 *
 * الأخطاء (أحمر) والنجاح (أخضر) ثابتان دائمًا بغض النظر عن الثيم،
 * حفاظًا على وضوح المعنى (رمز عالمي: أحمر = خطر، أخضر = نجاح).
 */
object ThemeManager {

    data class Palette(
        val background: Int,
        val surface: Int,
        val accent: Int,
        val accentDark: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val error: Int,
        val success: Int,
        val divider: Int,
        val overlayScrim: Int
    )

    // خمسة ألوان رسمية جاهزة - كل واحد بس Accent مختلف، الباقي يُشتق تلقائيًا
    const val PRESET_PURPLE = 0xFF9C6BFF.toInt()   // الافتراضي الأصلي للتطبيق
    const val PRESET_BLUE = 0xFF4FA3FF.toInt()
    const val PRESET_GREEN = 0xFF4CD97B.toInt()
    const val PRESET_RED = 0xFFFF5C6C.toInt()
    const val PRESET_GRAPHITE = 0xFFB0B4C8.toInt()
    // V16: خمسة ألوان إضافية بناءً على طلبك
    const val PRESET_TEAL = 0xFF2DD4BF.toInt()
    const val PRESET_ORANGE = 0xFFFF9142.toInt()
    const val PRESET_PINK = 0xFFFF6FB5.toInt()
    const val PRESET_INDIGO = 0xFF5C6BFF.toInt()
    const val PRESET_YELLOW = 0xFFFFD452.toInt()
    // V26: خمسة ألوان إضافية ثانية بناءً على طلبك
    const val PRESET_BROWN = 0xFFA97C50.toInt()
    const val PRESET_STEEL = 0xFF5A7D9A.toInt()
    const val PRESET_MAGENTA = 0xFFD946EF.toInt()
    const val PRESET_LIME = 0xFFA3E635.toInt()
    const val PRESET_NAVY = 0xFF35407A.toInt()

    val presets = listOf(
        PRESET_PURPLE, PRESET_BLUE, PRESET_GREEN, PRESET_RED, PRESET_GRAPHITE,
        PRESET_TEAL, PRESET_ORANGE, PRESET_PINK, PRESET_INDIGO, PRESET_YELLOW,
        PRESET_BROWN, PRESET_STEEL, PRESET_MAGENTA, PRESET_LIME, PRESET_NAVY
    )

    fun currentPalette(context: Context): Palette = buildPalette(ThemePrefs.getAccentColor(context))

    fun buildPalette(accent: Int): Palette {
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        val hue = hsv[0]
        // حد أدنى من التشبع حتى الألوان شبه الرمادية تفضل متماسكة الشكل
        val sat = hsv[1].coerceIn(0.15f, 1f)
        val value = hsv[2].coerceAtLeast(0.5f)

        fun hsvColor(s: Float, v: Float, alpha: Int = 0xFF): Int =
            Color.HSVToColor(alpha, floatArrayOf(hue, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

        val background = hsvColor(sat * 0.55f, 0.10f)
        val surface = hsvColor(sat * 0.6f, 0.17f)
        val accentDark = hsvColor(sat, value * 0.62f)
        val textSecondary = hsvColor(sat * 0.45f, 0.78f)
        val divider = hsvColor(sat * 0.6f, 0.30f, 0x33)
        val overlayScrim = hsvColor(sat * 0.55f, 0.10f, 0xF2)

        return Palette(
            background = background,
            surface = surface,
            accent = accent,
            accentDark = accentDark,
            textPrimary = Color.WHITE,
            textSecondary = textSecondary,
            error = 0xFFF44336.toInt(),
            success = 0xFF4CAF50.toInt(),
            divider = divider,
            overlayScrim = overlayScrim
        )
    }

    /** يلوّن خلفية النافذة وشريط الحالة العلوي - يُستدعى بعد setContentView في كل شاشة */
    fun applyWindow(activity: Activity, palette: Palette) {
        activity.window.decorView.setBackgroundColor(palette.background)
        activity.window.statusBarColor = palette.background
    }
}
