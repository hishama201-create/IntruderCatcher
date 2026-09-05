package com.sabri.intrudercatcher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // نافذة طلب صلاحية الكاميرا الرسمية من النظام
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(
                this, getString(R.string.camera_permission_denied), android.widget.Toast.LENGTH_LONG
            ).show()
        }
        updateStatusUi()
    }

    // نافذة تفعيل "مدير الجهاز" الرسمية من النظام - ترجع لنا النتيجة هنا
    // بعد ما يضغط المستخدم "تفعيل" أو "إلغاء" بالشاشة اللي يفتحها النظام
    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK && !isDeviceAdminActive()) {
            android.widget.Toast.makeText(
                this, getString(R.string.admin_activation_failed), android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        updateStatusUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.cardGrantCamera)?.setOnClickListener {
            requestCameraPermission()
        }
        findViewById<android.view.View>(R.id.cardGrantAdmin)?.setOnClickListener {
            requestDeviceAdmin()
        }
        findViewById<android.view.View>(R.id.cardViewGallery)?.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAppLock)?.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAutoBackup)?.setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAttemptLog)?.setOnClickListener {
            startActivity(Intent(this, AttemptLogActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUi()
    }

    private fun requestCameraPermission() {
        if (isCameraGranted()) {
            android.widget.Toast.makeText(
                this, getString(R.string.grant_camera_done), android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun requestDeviceAdmin() {
        if (isDeviceAdminActive()) {
            android.widget.Toast.makeText(
                this, getString(R.string.grant_admin_done), android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val adminComponent = ComponentName(this, LockAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_add_explanation))
        }
        deviceAdminLauncher.launch(intent)
    }

    private fun isCameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED


    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, LockAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    private fun updateStatusUi() {
        val cameraOk = isCameraGranted()
        val adminOk = isDeviceAdminActive()

        findViewById<android.widget.TextView>(R.id.tvGrantCamera)?.text =
            getString(if (cameraOk) R.string.grant_camera_done else R.string.grant_camera)
        findViewById<android.widget.TextView>(R.id.tvGrantAdmin)?.text =
            getString(if (adminOk) R.string.grant_admin_done else R.string.grant_admin)

        findViewById<androidx.cardview.widget.CardView>(R.id.cardGrantCamera)
            ?.setCardBackgroundColor(if (cameraOk) 0xFF1B4E2A.toInt() else 0xFF2A1B4E.toInt())
        findViewById<androidx.cardview.widget.CardView>(R.id.cardGrantAdmin)
            ?.setCardBackgroundColor(if (adminOk) 0xFF1B4E2A.toInt() else 0xFF2A1B4E.toInt())

        findViewById<android.widget.TextView>(R.id.tvStatus)?.text =
            getString(if (cameraOk && adminOk) R.string.status_ready else R.string.status_not_ready)
    }
}
