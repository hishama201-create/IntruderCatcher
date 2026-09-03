package com.sabri.intrudercatcher

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetPinActivity : AppCompatActivity() {

    private var firstPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_pin)

        // V18: ألوان ثابتة - المظهر القابل للتغيير بقى خاص بس بشاشة "قائمة التطبيقات"
        val bgColor = 0xFF0B0A1F.toInt()
        val surfaceColor = 0xFF1D1440.toInt()
        val accentDarkColor = 0xFF6C3CE9.toInt()
        val textPrimary = android.graphics.Color.WHITE
        val dividerColor = 0xFF665C99.toInt()

        val tvTitle = findViewById<TextView>(R.id.tvSetPinTitle)
        val etPin = findViewById<EditText>(R.id.etPin)
        val btnConfirm = findViewById<Button>(R.id.btnConfirmPin)

        val rootView = (findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        rootView.setBackgroundColor(bgColor)
        tvTitle.setTextColor(textPrimary)
        etPin.setBackgroundColor(surfaceColor)
        etPin.setTextColor(textPrimary)
        etPin.setHintTextColor(dividerColor)
        btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(accentDarkColor)

        tvTitle.text = getString(R.string.applock_enter_new_pin)

        btnConfirm.setOnClickListener {
            val pin = etPin.text.toString()
            if (pin.length < 4) {
                Toast.makeText(this, getString(R.string.applock_pin_too_short), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (firstPin == null) {
                // أول إدخال - نطلب تأكيد
                firstPin = pin
                etPin.text.clear()
                tvTitle.text = getString(R.string.applock_confirm_pin)
            } else if (firstPin == pin) {
                AppLockPrefs.setPin(this, pin)
                AppLockPrefs.setLockType(this, AppLockPrefs.LOCK_TYPE_PIN)
                Toast.makeText(this, getString(R.string.applock_pin_saved), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, getString(R.string.applock_pin_mismatch), Toast.LENGTH_SHORT).show()
                firstPin = null
                etPin.text.clear()
                tvTitle.text = getString(R.string.applock_enter_new_pin)
            }
        }
    }
}
