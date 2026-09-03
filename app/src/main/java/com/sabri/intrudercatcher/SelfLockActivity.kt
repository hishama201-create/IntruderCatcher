package com.sabri.intrudercatcher

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * V15: شاشة قفل IntruderCatcher نفسه - تُعرض قبل أي شاشة تانية من التطبيق
 * لو مفعّلة (@see [AppLockPrefs.isSelfLockEnabled]). تستخدم نفس PIN/نمط
 * قفل التطبيقات الأخرى بالظبط (نفس التخزين المشفّر بـ [AppLockPrefs]) -
 * زر إلغاء واحد بيسكّر التطبيق كامل، مافيش رجوع لمحتوى بدون فتح صحيح.
 */
class SelfLockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_self_lock)

        // V18: ألوان ثابتة (زي باقي شاشات التطبيق) - المظهر القابل للتغيير بقى خاص بس بشاشة "قائمة التطبيقات"
        val bgColor = 0xFF0B0A1F.toInt()
        val surfaceColor = 0xFF1D1440.toInt()
        val accentColor = 0xFF9C6BFF.toInt()
        val textPrimary = Color.WHITE
        val errorColor = 0xFFF44336.toInt()
        val dividerColor = 0xFF665C99.toInt()

        findViewById<View>(R.id.selfLockRoot).setBackgroundColor(bgColor)
        findViewById<TextView>(R.id.tvSelfLockAppName).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvSelfLockError).setTextColor(errorColor)
        findViewById<TextView>(R.id.btnSelfLockBiometric).setTextColor(accentColor)

        val groupPin = findViewById<View>(R.id.selfLockGroupPin)
        val groupPattern = findViewById<View>(R.id.selfLockGroupPattern)
        val pinDotsContainer = findViewById<LinearLayout>(R.id.selfLockPinDotsContainer)
        val errorText = findViewById<TextView>(R.id.tvSelfLockError)
        val keypad = findViewById<GridLayout>(R.id.selfLockPinKeypad)
        val patternView = findViewById<PatternLockView>(R.id.selfLockPatternView)
        val biometricButton = findViewById<TextView>(R.id.btnSelfLockBiometric)

        val lockType = AppLockPrefs.getLockType(this)
        groupPin.visibility = if (lockType == AppLockPrefs.LOCK_TYPE_PATTERN) View.GONE else View.VISIBLE
        groupPattern.visibility = if (lockType == AppLockPrefs.LOCK_TYPE_PATTERN) View.VISIBLE else View.GONE

        val enteredPin = StringBuilder()

        fun unlockAndFinish() {
            AttemptLogManager.add(
                this, packageName, getString(R.string.app_name),
                AttemptLogManager.METHOD_PIN, true
            )
            SelfLockSession.markUnlocked()
            setResult(RESULT_OK)
            finish()
        }

        fun refreshDots() {
            pinDotsContainer.removeAllViews()
            val length = enteredPin.length.coerceAtLeast(4)
            for (i in 0 until length) {
                val dot = TextView(this).apply {
                    text = if (i < enteredPin.length) "●" else "○"
                    textSize = 22f
                    setTextColor(if (i < enteredPin.length) textPrimary else dividerColor)
                    setPadding(10, 0, 10, 0)
                }
                pinDotsContainer.addView(dot)
            }
        }

        fun tryUnlockPin() {
            if (AppLockPrefs.verifyPin(this, enteredPin.toString())) {
                unlockAndFinish()
            } else {
                AttemptLogManager.add(
                    this, packageName, getString(R.string.app_name),
                    AttemptLogManager.METHOD_PIN, false
                )
                errorText.visibility = View.VISIBLE
                enteredPin.clear()
                refreshDots()
            }
        }

        fun addDigit(digit: String) {
            if (enteredPin.length >= 8) return
            errorText.visibility = View.INVISIBLE
            enteredPin.append(digit)
            refreshDots()
        }

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        keys.forEach { key ->
            val button = TextView(this).apply {
                text = key
                textSize = 22f
                setTextColor(textPrimary)
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 80
                    height = 80
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(surfaceColor)
                setOnClickListener {
                    when (key) {
                        "⌫" -> {
                            if (enteredPin.isNotEmpty()) enteredPin.deleteCharAt(enteredPin.length - 1)
                            refreshDots()
                        }
                        "✓" -> tryUnlockPin()
                        else -> addDigit(key)
                    }
                }
            }
            keypad.addView(button)
        }
        refreshDots()

        patternView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                errorText.visibility = View.INVISIBLE
            }
            false
        }
        patternView.onPatternTooShort = { errorText.visibility = View.VISIBLE }
        patternView.onPatternComplete = { pattern ->
            if (AppLockPrefs.verifyPattern(this, pattern)) {
                AttemptLogManager.add(
                    this, packageName, getString(R.string.app_name),
                    AttemptLogManager.METHOD_PATTERN, true
                )
                SelfLockSession.markUnlocked()
                setResult(RESULT_OK)
                finish()
            } else {
                AttemptLogManager.add(
                    this, packageName, getString(R.string.app_name),
                    AttemptLogManager.METHOD_PATTERN, false
                )
                errorText.visibility = View.VISIBLE
                patternView.showError()
            }
        }

        val canUseBiometric = AppLockPrefs.useBiometric(this) && BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        fun showBiometricPrompt() {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        AttemptLogManager.add(
                            this@SelfLockActivity, packageName, getString(R.string.app_name),
                            AttemptLogManager.METHOD_BIOMETRIC, true
                        )
                        unlockAndFinish()
                    }
                    // فشل أو إلغاء - نخلي المستخدم بشاشة الـ PIN/النمط، هو نفسه فاتح أصلًا
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                    override fun onAuthenticationFailed() {}
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.applock_biometric_title))
                    .setNegativeButtonText(getString(R.string.applock_biometric_cancel))
                    .build()
            )
        }

        if (canUseBiometric) {
            biometricButton.visibility = View.VISIBLE
            // الزر يبقى موجود للمحاولة اليدوية (لو المستخدم لغى النافذة التلقائية بالغلط)
            biometricButton.setOnClickListener { showBiometricPrompt() }

            // V23: تشغيل تلقائي لنافذة البصمة/الوجه بمجرد ظهور الشاشة - نفس سلوك
            // شاشة قفل سامسونج، بدون ما ينتظر المستخدم يضغط الزر يدويًا.
            // نأجلها لأول إطار بعد رسم الشاشة حتى لا تظهر النافذة قبل أن تكون
            // الـ Activity في المقدمة فعليًا.
            if (savedInstanceState == null) {
                findViewById<View>(R.id.selfLockRoot).post { showBiometricPrompt() }
            }
        }
    }

    /** لا رجوع بدون فتح صحيح - الضغط على رجوع يخرج التطبيق كامل بدل ما يعرض محتوى غير مفتوح */
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
