package com.sabri.intrudercatcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * V16: شاشة اختيار مظهر التطبيق - ألوان رسمية جاهزة فقط (شيّلنا قسم
 * "لون مخصّص" بمنزلقات RGB بالكامل بناءً على طلبك - كان فيها مشكلة
 * وما كانتش محتاجة أصلًا).
 * V26: رجّعنا قسم اللون المخصّص بناءً على طلب جديد - بتصميم أبسط
 * (٣ منزلقات + معاينة حية + زر تطبيق واحد صريح، بدل تطبيق تلقائي أثناء
 * السحب) لتفادي أي مشكلة كانت موجودة بالنسخة القديمة.
 */
class ThemeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)

        // V18: خلفية الشاشة نفسها ثابتة دلوقتي - المظهر القابل للتغيير بقى خاص بس بشاشة "قائمة التطبيقات"
        val palette = ThemeManager.currentPalette(this)
        findViewById<View>(R.id.themeRoot).setBackgroundColor(0xFF0B0A1F.toInt())

        findViewById<View>(R.id.btnThemeBack).setOnClickListener { finish() }

        setupPresets(palette)
        setupCustomColorPicker(palette)
    }

    private fun setupPresets(palette: ThemeManager.Palette) {
        val container = findViewById<GridLayout>(R.id.presetsContainer)
        val presets = listOf(
            ThemeManager.PRESET_PURPLE to getString(R.string.theme_preset_purple),
            ThemeManager.PRESET_BLUE to getString(R.string.theme_preset_blue),
            ThemeManager.PRESET_GREEN to getString(R.string.theme_preset_green),
            ThemeManager.PRESET_RED to getString(R.string.theme_preset_red),
            ThemeManager.PRESET_GRAPHITE to getString(R.string.theme_preset_graphite),
            ThemeManager.PRESET_TEAL to getString(R.string.theme_preset_teal),
            ThemeManager.PRESET_ORANGE to getString(R.string.theme_preset_orange),
            ThemeManager.PRESET_PINK to getString(R.string.theme_preset_pink),
            ThemeManager.PRESET_INDIGO to getString(R.string.theme_preset_indigo),
            ThemeManager.PRESET_YELLOW to getString(R.string.theme_preset_yellow)
        )

        val sizePx = (52 * resources.displayMetrics.density).toInt()
        val marginPx = (10 * resources.displayMetrics.density).toInt()

        presets.forEach { (color, label) ->
            val swatch = TextView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = sizePx
                    height = sizePx
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                }
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == palette.accent) {
                        setStroke(6, Color.WHITE)
                    }
                }
                background = drawable
                contentDescription = label
                isClickable = true
                isFocusable = true
                setOnClickListener { applyColor(color) }
            }
            container.addView(swatch)
        }
    }

    private fun applyColor(color: Int) {
        ThemePrefs.setAccentColor(this, color)
        Toast.makeText(this, getString(R.string.theme_applied_toast), Toast.LENGTH_SHORT).show()
        recreate()
    }

    /**
     * V26: قسم اللون المخصّص - ٣ منزلقات RGB (٠-٢٥٥) مع معاينة حية دائرية،
     * وزر "تطبيق" صريح واحد (بدل ما نطبّق أثناء السحب، تفاديًا لأي بطء أو
     * تكرار غير ضروري). نبدأ المنزلقات من اللون الحالي المطبّق فعليًا.
     */
    private fun setupCustomColorPicker(palette: ThemeManager.Palette) {
        val preview = findViewById<View>(R.id.customColorPreview)
        val seekRed = findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = findViewById<SeekBar>(R.id.seekBlue)
        val btnApply = findViewById<View>(R.id.btnApplyCustomColor)

        seekRed.progress = Color.red(palette.accent)
        seekGreen.progress = Color.green(palette.accent)
        seekBlue.progress = Color.blue(palette.accent)

        fun currentCustomColor(): Int =
            Color.rgb(seekRed.progress, seekGreen.progress, seekBlue.progress)

        fun updatePreview() {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(currentCustomColor())
            }
            preview.background = drawable
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        seekRed.setOnSeekBarChangeListener(listener)
        seekGreen.setOnSeekBarChangeListener(listener)
        seekBlue.setOnSeekBarChangeListener(listener)

        updatePreview()

        btnApply.setOnClickListener { applyColor(currentCustomColor()) }
    }
}
