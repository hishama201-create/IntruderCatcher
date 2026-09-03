package com.sabri.intrudercatcher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Activity شفافة بالكامل (بدون واجهة فعلية)، مهمتها الوحيدة إظهار
 * نافذة النظام الرسمية للبصمة/الوجه (BiometricPrompt يتطلب Activity).
 * تُغلق فورًا سواء نجحت المصادقة أو فشلت.
 */
class BiometricUnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        val targetLabel = intent.getStringExtra(EXTRA_TARGET_LABEL) ?: targetPackage
        if (targetPackage == null) {
            finish()
            return
        }

        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // ما فيه بصمة/وجه مسجّل على الجهاز - نرجع لشاشة الـ PIN بالنافذة العائمة
            finish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AttemptLogManager.add(
                        applicationContext, targetPackage, targetLabel ?: targetPackage,
                        AttemptLogManager.METHOD_BIOMETRIC, true
                    )
                    AppLockOverlayManager.onBiometricUnlocked(applicationContext, targetPackage)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // فشل أو إلغاء - نخلي المستخدم يرجع لشاشة الـ PIN العائمة
                    finish()
                }

                override fun onAuthenticationFailed() {
                    // بصمة غير متطابقة - النظام نفسه يعرض رسالة، نستنى محاولة ثانية
                    // أو إلغاء من نفس الـ prompt، ما نسكر الشاشة هنا
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.applock_biometric_title))
            .setNegativeButtonText(getString(R.string.applock_biometric_cancel))
            .build()

        prompt.authenticate(promptInfo)
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_LABEL = "extra_target_label"
    }
}
