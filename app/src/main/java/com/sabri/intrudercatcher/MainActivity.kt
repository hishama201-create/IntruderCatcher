package com.sabri.intrudercatcher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sabri.intrudercatcher.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    /** V24: علامة إننا وسط تفعيل بصمة أول مرة، لازم نفعّلها بعد نجاح إعداد الـ PIN كخط رجوع */
    private var pendingBiometricAfterFirstPin: Boolean = false

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { updateStatus() }

    // أندرويد 13+ يتطلب صلاحية صريحة لعرض الإشعارات (بما فيها إشعار
    // الخدمة الأمامية الإلزامي). طلب اختياري - التطبيق يعمل حتى بدونها.
    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    private val setPinLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            if (pendingBiometricAfterFirstPin) {
                pendingBiometricAfterFirstPin = false
                AppLockPrefs.setUseBiometric(this, true)
            }
            continueAppLockFlowAfterPin()
        } else {
            pendingBiometricAfterFirstPin = false
        }
    }

    // V15: بوابة قفل IntruderCatcher نفسه - لو المستخدم رجع بدون فتح صحيح، نطلع من التطبيق كامل
    private val selfLockLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        binding.btnGrantCamera.setOnClickListener { requestCameraPermission() }
        binding.btnGrantAdmin.setOnClickListener { requestAdminPermission() }
        binding.btnViewGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        binding.btnAppLock.setOnClickListener { openAppLockFlow() }
        binding.btnBackupSettings.setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }

        // فحص السلة عند كل فتح للتطبيق، وليس فقط عند فتح شاشة المعرض
        TrashManager.purgeExpired(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()

        // V15: أول حاجة قبل أي محتوى - لو قفل IntruderCatcher نفسه مفعّل ولسه ما اتفتحش بهذه الجلسة
        if (AppLockPrefs.isSelfLockEnabled(this) && AppLockPrefs.hasLockConfigured(this) &&
            !SelfLockSession.isUnlocked()
        ) {
            selfLockLauncher.launch(Intent(this, SelfLockActivity::class.java))
            return
        }

        checkAdminDisabledNag()
        updateStatus()

        // لو المستخدم رجع من إعدادات الصلاحيات وصار عنده كل شي جاهز
        // وعنده تطبيقات مقفولة من قبل، نتأكد إن خدمة المراقبة شغالة
        val lockedApps = AppLockPrefs.getLockedApps(this)
        if (lockedApps.isNotEmpty() && AppLockPermissions.hasAllPermissions(this)) {
            AppLockMonitorService.start(this)
        }
    }

    /** V18: رجّعنا الشاشة الرئيسية للون الثابت الأصلي بناءً على طلبك - المظهر بقى خاص بس بشاشة "قائمة التطبيقات" */

    /** V15: تنبيه قوي لو حد عطّل صلاحية مدير الجهاز (حماية إزالة التثبيت) من خارج التطبيق */
    private fun checkAdminDisabledNag() {
        if (!AppLockPrefs.wasAdminDisabled(this)) return

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            // اتفعّلت الصلاحية تاني من مكان تاني - نلغي التنبيه بهدوء
            AppLockPrefs.setAdminWasDisabled(this, false)
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_disabled_nag_title))
            .setMessage(getString(R.string.admin_disabled_nag_message))
            .setPositiveButton(getString(R.string.admin_disabled_nag_reenable)) { _, _ ->
                AppLockPrefs.setAdminWasDisabled(this, false)
                requestAdminPermission()
            }
            .setNegativeButton(getString(R.string.applock_biometric_cancel), null)
            .show()
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestAdminPermission() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "يحتاج التطبيق هذه الصلاحية لرصد محاولات فتح القفل الخاطئة فقط"
                )
            }
            startActivity(intent)
        }
    }

    /**
     * تسلسل إعداد ميزة قفل التطبيقات: 1) رمز PIN 2) صلاحية Usage Access
     * 3) صلاحية الظهور فوق التطبيقات، ثم أخيرًا شاشة اختيار التطبيقات.
     * كل خطوة تتحقق من اللي قبلها حتى لو المستخدم رجع من الإعدادات لاحقًا.
     */
    private fun openAppLockFlow() {
        if (!AppLockPrefs.hasLockConfigured(this)) {
            showLockTypeChooser()
            return
        }
        continueAppLockFlowAfterPin()
    }

    /** أول مرة يعدّ فيها المستخدم قفل التطبيقات، نخيّره بين PIN أو نمط أو بصمة */
    private fun showLockTypeChooser() {
        val options = arrayOf(
            getString(R.string.applock_type_pin),
            getString(R.string.applock_type_pattern),
            getString(R.string.applock_type_face),
            getString(R.string.applock_type_fingerprint)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.applock_choose_type_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setPinLauncher.launch(Intent(this, SetPinActivity::class.java))
                    1 -> setPinLauncher.launch(Intent(this, SetPatternActivity::class.java))
                    else -> chooseBiometricFirstTime()
                }
            }
            .setNegativeButton(getString(R.string.applock_biometric_cancel), null)
            .show()
    }

    /**
     * V24: البصمة (وجه/إصبع) دايمًا خيار إضافي فوق رمز أساسي - مش بديل
     * عنه - عشان لو فشلت البصمة يومًا (زي تغيير شكل اللحية أو إصبع
     * مبلول) يفضل عندك خط رجوع آمن تقدر تفتح بيه. فلو اخترتها أول مرة
     * قبل ما يكون عندك أي رمز، نطلب منك تعمل PIN الأول كخط رجوع، وبعدها
     * نفعّل البصمة تلقائيًا فوقه.
     */
    private fun chooseBiometricFirstTime() {
        val canUseBiometric = androidx.biometric.BiometricManager.from(this).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

        if (!canUseBiometric) {
            android.widget.Toast.makeText(
                this, getString(R.string.applock_face_not_supported), android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        pendingBiometricAfterFirstPin = true
        setPinLauncher.launch(Intent(this, SetPinActivity::class.java))
    }

    private fun continueAppLockFlowAfterPin() {
        if (!AppLockPermissions.hasUsageAccess(this)) {
            android.widget.Toast.makeText(
                this, getString(R.string.applock_grant_usage_access_hint), android.widget.Toast.LENGTH_LONG
            ).show()
            AppLockPermissions.requestUsageAccess(this)
            return
        }
        if (!AppLockPermissions.hasOverlayPermission(this)) {
            android.widget.Toast.makeText(
                this, getString(R.string.applock_grant_overlay_hint), android.widget.Toast.LENGTH_LONG
            ).show()
            AppLockPermissions.requestOverlayPermission(this)
            return
        }
        if (!AppLockPermissions.hasIgnoreBatteryOptimizations(this)) {
            android.widget.Toast.makeText(
                this, getString(R.string.applock_grant_battery_hint), android.widget.Toast.LENGTH_LONG
            ).show()
            AppLockPermissions.requestIgnoreBatteryOptimizations(this)
            return
        }
        startActivity(Intent(this, AppSelectionActivity::class.java))
    }

    private fun updateStatus() {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val adminGranted = devicePolicyManager.isAdminActive(adminComponent)

        binding.tvStatus.text = if (cameraGranted && adminGranted) {
            getString(R.string.status_ready)
        } else {
            getString(R.string.status_not_ready)
        }

        binding.btnGrantCamera.isEnabled = !cameraGranted
        binding.btnGrantAdmin.isEnabled = !adminGranted
    }
}
