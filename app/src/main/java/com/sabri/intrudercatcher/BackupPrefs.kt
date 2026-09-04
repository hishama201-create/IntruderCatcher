package com.sabri.intrudercatcher

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object BackupPrefs {

    private const val FILE_NAME = "backup_secure_prefs"
    private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
    private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
    private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun setTelegramBotToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TELEGRAM_BOT_TOKEN, token).apply()
    }

    fun getTelegramBotToken(context: Context): String =
        prefs(context).getString(KEY_TELEGRAM_BOT_TOKEN, "") ?: ""

    fun setTelegramChatId(context: Context, chatId: String) {
        prefs(context).edit().putString(KEY_TELEGRAM_CHAT_ID, chatId).apply()
    }

    fun getTelegramChatId(context: Context): String =
        prefs(context).getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""

    fun setTelegramEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TELEGRAM_ENABLED, enabled).apply()
    }

    fun isTelegramEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TELEGRAM_ENABLED, false) &&
            getTelegramBotToken(context).isNotBlank() && getTelegramChatId(context).isNotBlank()
}
