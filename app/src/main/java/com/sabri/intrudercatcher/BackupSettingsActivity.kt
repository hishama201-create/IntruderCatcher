package com.sabri.intrudercatcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sabri.intrudercatcher.databinding.ActivityBackupSettingsBinding

class BackupSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.etBotToken.setText(BackupPrefs.getTelegramBotToken(this))
        binding.etChatId.setText(BackupPrefs.getTelegramChatId(this))
        binding.switchTelegram.isChecked = BackupPrefs.isTelegramEnabled(this)

        binding.btnSave.setOnClickListener { saveAndClose() }

        binding.btnOpenBotFather.setOnClickListener { openTelegramUser("BotFather") }
        binding.btnOpenUserInfoBot.setOnClickListener { openTelegramUser("userinfobot") }
    }

    private fun openTelegramUser(username: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$username")))
        }
    }

    private fun saveAndClose() {
        val token = binding.etBotToken.text.toString().trim()
        val chatId = binding.etChatId.text.toString().trim()

        if (binding.switchTelegram.isChecked && (token.isBlank() || chatId.isBlank())) {
            Toast.makeText(this, R.string.telegram_missing_fields, Toast.LENGTH_LONG).show()
            return
        }

        BackupPrefs.setTelegramBotToken(this, token)
        BackupPrefs.setTelegramChatId(this, chatId)
        BackupPrefs.setTelegramEnabled(this, binding.switchTelegram.isChecked)

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
