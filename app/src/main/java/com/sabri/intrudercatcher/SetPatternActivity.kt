package com.sabri.intrudercatcher

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetPatternActivity : AppCompatActivity() {

    private var firstPattern: List<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_pattern)

        // V18: ألوان ثابتة - المظهر القابل للتغيير بقى خاص بس بشاشة "قائمة التطبيقات"
        val bgColor = 0xFF0B0A1F.toInt()
        val textPrimary = android.graphics.Color.WHITE
        val errorColor = 0xFFF44336.toInt()

        val tvTitle = findViewById<TextView>(R.id.tvSetPatternTitle)
        val tvError = findViewById<TextView>(R.id.tvSetPatternError)
        val patternView = findViewById<PatternLockView>(R.id.patternLockView)

        val rootView = (findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        rootView.setBackgroundColor(bgColor)
        tvTitle.setTextColor(textPrimary)
        tvError.setTextColor(errorColor)

        tvTitle.text = getString(R.string.applock_draw_new_pattern)

        patternView.onPatternTooShort = {
            Toast.makeText(this, getString(R.string.applock_pattern_too_short), Toast.LENGTH_SHORT).show()
        }

        patternView.onPatternComplete = { pattern ->
            if (firstPattern == null) {
                // أول رسمة - نطلب تأكيد
                firstPattern = pattern
                tvError.visibility = View.INVISIBLE
                tvTitle.text = getString(R.string.applock_confirm_pattern)
                patternView.postDelayed({ patternView.reset() }, 250)
            } else if (firstPattern == pattern) {
                AppLockPrefs.setPattern(this, pattern)
                AppLockPrefs.setLockType(this, AppLockPrefs.LOCK_TYPE_PATTERN)
                Toast.makeText(this, getString(R.string.applock_pattern_saved), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                tvError.visibility = View.VISIBLE
                patternView.showError()
                firstPattern = null
                tvTitle.text = getString(R.string.applock_draw_new_pattern)
            }
        }
    }
}
