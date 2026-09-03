package com.sabri.intrudercatcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * يعرض شاشة قفل (PIN + خيار بصمة) كنافذة عائمة (TYPE_APPLICATION_OVERLAY)
 * فوق أي تطبيق آخر، بدل تشغيل Activity جديدة - هذا يتجنّب تمامًا قيود
 * "بدء Activity من الخلفية" الموجودة من أندرويد 10 فما فوق، لأن النافذة
 * العائمة مو Activity أصلًا.
 *
 * يتطلب صلاحية "الظهور فوق التطبيقات الأخرى" (SYSTEM_ALERT_WINDOW) ممنوحة مسبقًا.
 */
object AppLockOverlayManager {

    private var overlayView: View? = null
    private var currentTargetPackage: String? = null

    fun isShowing(): Boolean = overlayView != null

    fun currentTarget(): String? = currentTargetPackage

    fun show(context: Context, targetPackage: String, targetLabel: String) {
        if (overlayView != null) return // نافذة معروضة أصلًا، ما نكرر

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_lock_screen, null)

        // V18: ألوان ثابتة - المظهر القابل للتغيير بقى خاص بس بشاشة "قائمة التطبيقات"
        val bgColor = 0xFF0B0A1F.toInt()
        val surfaceColor = 0xFF1D1440.toInt()
        val accentColor = 0xFF9C6BFF.toInt()
        val textPrimary = Color.WHITE
        val errorColor = 0xFFF44336.toInt()
        val dividerColor = 0xFF665C99.toInt()
        // V20: تعتيم كامل ١٠٠٪ - كان فيه شفافية خفيفة (٩٥٪) بتسيب جزء من التطبيق اللي وراه يبان
        val overlayScrimColor = 0xFF0B0A1F.toInt()

        view.setBackgroundColor(overlayScrimColor)

        view.findViewById<TextView>(R.id.tvOverlayAppName).apply {
            text = context.getString(R.string.applock_locked_app_label, targetLabel)
            setTextColor(textPrimary)
        }

        val groupPin = view.findViewById<View>(R.id.groupPin)
        val groupPattern = view.findViewById<View>(R.id.groupPattern)
        val pinDotsContainer = view.findViewById<LinearLayout>(R.id.pinDotsContainer)
        val errorText = view.findViewById<TextView>(R.id.tvOverlayError)
        val keypad = view.findViewById<GridLayout>(R.id.pinKeypad)
        val patternView = view.findViewById<PatternLockView>(R.id.patternLockView)
        val biometricButton = view.findViewById<TextView>(R.id.btnOverlayBiometric)
        errorText.setTextColor(errorColor)
        biometricButton.setTextColor(accentColor)

        val lockType = AppLockPrefs.getLockType(context)
        groupPin.visibility = if (lockType == AppLockPrefs.LOCK_TYPE_PATTERN) View.GONE else View.VISIBLE
        groupPattern.visibility = if (lockType == AppLockPrefs.LOCK_TYPE_PATTERN) View.VISIBLE else View.GONE

        val enteredPin = StringBuilder()

        fun refreshDots() {
            pinDotsContainer.removeAllViews()
            val length = enteredPin.length.coerceAtLeast(4)
            for (i in 0 until length) {
                val dot = TextView(context).apply {
                    text = if (i < enteredPin.length) "●" else "○"
                    textSize = 22f
                    setTextColor(if (i < enteredPin.length) textPrimary else dividerColor)
                    setPadding(10, 0, 10, 0)
                }
                pinDotsContainer.addView(dot)
            }
        }

        fun tryUnlock() {
            if (AppLockPrefs.verifyPin(context, enteredPin.toString())) {
                AttemptLogManager.add(context, targetPackage, targetLabel, AttemptLogManager.METHOD_PIN, true)
                AppLockSession.markUnlocked(targetPackage)
                hide(context)
            } else {
                AttemptLogManager.add(context, targetPackage, targetLabel, AttemptLogManager.METHOD_PIN, false)
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
            if (enteredPin.length >= 4) {
                // نحاول الفتح تلقائيًا بمجرد الوصول لـ 4 أرقام (الحد الأدنى)
                // لو غلط، المستخدم يقدر يكمل يضيف أو يمسح
            }
        }

        // بناء لوحة الأرقام 1-9 + مسح + 0 + تأكيد
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        keys.forEach { key ->
            val button = TextView(context).apply {
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
                        "✓" -> tryUnlock()
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
            false // نخلي PatternLockView نفسه يكمل معالجة اللمسة بشكل طبيعي
        }
        patternView.onPatternTooShort = {
            errorText.visibility = View.VISIBLE
        }
        patternView.onPatternComplete = { pattern ->
            if (AppLockPrefs.verifyPattern(context, pattern)) {
                AttemptLogManager.add(context, targetPackage, targetLabel, AttemptLogManager.METHOD_PATTERN, true)
                AppLockSession.markUnlocked(targetPackage)
                hide(context)
            } else {
                AttemptLogManager.add(context, targetPackage, targetLabel, AttemptLogManager.METHOD_PATTERN, false)
                errorText.visibility = View.VISIBLE
                patternView.showError()
            }
        }

        fun launchBiometricUnlock() {
            // نافذة عائمة ما تقدر تستضيف BiometricPrompt مباشرة (يحتاج Activity)،
            // فنفتح Activity شفافة صغيرة مخصصة لهذا الغرض فقط.
            val intent = Intent(context, BiometricUnlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(BiometricUnlockActivity.EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(BiometricUnlockActivity.EXTRA_TARGET_LABEL, targetLabel)
            }
            context.startActivity(intent)
        }

        if (AppLockPrefs.useBiometric(context)) {
            biometricButton.visibility = View.VISIBLE
            // الزر يبقى موجود للمحاولة اليدوية (مثلاً لو المستخدم لغى النافذة التلقائية بالغلط)
            biometricButton.setOnClickListener { launchBiometricUnlock() }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // بدون FLAG_NOT_FOCUSABLE أو FLAG_NOT_TOUCHABLE، حتى تستقبل النافذة
        // لمسات المستخدم على لوحة الأرقام وزر البصمة بشكل طبيعي
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            0,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager.addView(view, params)
        overlayView = view
        currentTargetPackage = targetPackage

        // V23: تشغيل تلقائي لنافذة البصمة/الوجه بمجرد ظهور شاشة القفل - نفس سلوك
        // شاشة قفل سامسونج، بدون انتظار ضغطة المستخدم على الزر. نؤجلها لأول
        // إطار بعد إضافة النافذة حتى تكون معروضة فعليًا فوق التطبيق المستهدف.
        if (AppLockPrefs.useBiometric(context)) {
            view.post { launchBiometricUnlock() }
        }
    }

    /** يُستدعى بعد نجاح فتح البصمة من [BiometricUnlockActivity] */
    fun onBiometricUnlocked(context: Context, targetPackage: String) {
        AppLockSession.markUnlocked(targetPackage)
        hide(context)
    }

    fun hide(context: Context) {
        val view = overlayView ?: return
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // النافذة أصلًا مو مضافة (سباق نادر) - تجاهل
        }
        overlayView = null
        currentTargetPackage = null
    }
}
