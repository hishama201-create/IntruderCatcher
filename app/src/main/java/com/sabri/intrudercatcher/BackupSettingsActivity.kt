package com.sabri.intrudercatcher

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sabri.intrudercatcher.databinding.ActivityBackupSettingsBinding

/**
 * V28: شاشة إعدادات النسخ الاحتياطي التلقائي - ربط بوت تيليجرام
 * (إرسال فوري خارج الجهاز) وربط حساب Google Drive (نسخة احتياطية
 * مشفّرة). كلاهما اختياري ومستقل عن الآخر تمامًا.
 */
class BackupSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupSettingsBinding

    private val driveSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        DriveBackupManager.handleSignInResult(result.data) { email, error ->
            if (email != null) {
                BackupPrefs.setDriveAccountEmail(this, email)
                updateDriveUi()
                Toast.makeText(this, getString(R.string.drive_linked_success, email), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.drive_link_failed, error ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.etBotToken.setText(BackupPrefs.getTelegramBotToken(this))
        binding.etChatId.setText(BackupPrefs.getTelegramChatId(this))
        binding.switchTelegram.isChecked = BackupPrefs.isTelegramEnabled(this)
        binding.switchDrive.isChecked = BackupPrefs.isDriveEnabled(this)
        updateDriveUi()

        binding.btnLinkDrive.setOnClickListener {
            val existing = DriveBackupManager.getSignedInAccount(this)
            if (existing != null) {
                // مربوط فعلًا - الضغط يفصل الحساب حتى يقدر يربط حساب آخر لو حاب
                DriveBackupManager.signOut(this)
                binding.switchDrive.isChecked = false
                updateDriveUi()
                Toast.makeText(this, R.string.drive_unlinked, Toast.LENGTH_SHORT).show()
            } else {
                driveSignInLauncher.launch(DriveBackupManager.getSignInClient(this).signInIntent)
            }
        }

        binding.btnSave.setOnClickListener { saveAndClose() }
    }

    private fun updateDriveUi() {
        val email = BackupPrefs.getDriveAccountEmail(this)
        if (email.isNotBlank()) {
            binding.tvDriveAccount.text = getString(R.string.drive_linked_as, email)
            binding.btnLinkDrive.text = getString(R.string.drive_unlink_button)
        } else {
            binding.tvDriveAccount.text = getString(R.string.drive_not_linked)
            binding.btnLinkDrive.text = getString(R.string.drive_link_button)
        }
    }

    private fun saveAndClose() {
        val token = binding.etBotToken.text.toString().trim()
        val chatId = binding.etChatId.text.toString().trim()
        BackupPrefs.setTelegramBotToken(this, token)
        BackupPrefs.setTelegramChatId(this, chatId)
        BackupPrefs.setTelegramEnabled(this, binding.switchTelegram.isChecked)

        val driveReady = BackupPrefs.getDriveAccountEmail(this).isNotBlank()
        BackupPrefs.setDriveEnabled(this, binding.switchDrive.isChecked && driveReady)
        if (binding.switchDrive.isChecked && !driveReady) {
            Toast.makeText(this, R.string.drive_must_link_first, Toast.LENGTH_LONG).show()
        }

        if (binding.switchTelegram.isChecked && (token.isBlank() || chatId.isBlank())) {
            Toast.makeText(this, R.string.telegram_missing_fields, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
