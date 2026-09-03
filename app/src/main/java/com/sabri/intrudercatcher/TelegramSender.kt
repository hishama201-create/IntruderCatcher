package com.sabri.intrudercatcher

import android.content.Context
import android.util.Log
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/**
 * V28: يرسل فيديو المتطفل فورًا لبوت تيليجرام خاص بالمستخدم، بحيث تصل
 * الصورة/الفيديو خارج الجهاز مباشرة حتى لو حذف المتطفل التطبيق أو
 * عمل Factory Reset للجهاز بعدها. يستخدم HttpURLConnection مباشرة
 * (بدون مكتبات خارجية) عبر واجهة Telegram Bot API الرسمية والموثقة.
 */
object TelegramSender {

    private const val TAG = "TelegramSender"
    private val executor = Executors.newSingleThreadExecutor()

    /** يرسل الملف بالخلفية دون تعطيل الخدمة المستدعية - فشل الإرسال (لا إنترنت مثلًا) لا يُسقط أي شيء آخر */
    fun sendVideoAsync(context: Context, videoFile: File, caption: String) {
        if (!BackupPrefs.isTelegramEnabled(context)) return
        val token = BackupPrefs.getTelegramBotToken(context)
        val chatId = BackupPrefs.getTelegramChatId(context)
        executor.execute {
            try {
                sendMultipart(token, chatId, videoFile, caption)
            } catch (e: Exception) {
                Log.e(TAG, "فشل إرسال تيليجرام: ${e.message}")
            }
        }
    }

    private fun sendMultipart(token: String, chatId: String, file: File, caption: String) {
        val boundary = "----IntruderCatcher${UUID.randomUUID()}"
        val url = URL("https://api.telegram.org/bot$token/sendVideo")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.connectTimeout = 15000
        connection.readTimeout = 30000

        connection.outputStream.use { out ->
            writeFormField(out, boundary, "chat_id", chatId)
            writeFormField(out, boundary, "caption", caption)
            writeFileField(out, boundary, "video", file)
            out.write("--$boundary--\r\n".toByteArray())
        }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText()
            Log.e(TAG, "تيليجرام رفض الطلب ($responseCode): $errorBody")
        } else {
            Log.d(TAG, "تم إرسال الفيديو لتيليجرام بنجاح")
        }
        connection.disconnect()
    }

    private fun writeFormField(out: OutputStream, boundary: String, name: String, value: String) {
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        out.write("$value\r\n".toByteArray())
    }

    private fun writeFileField(out: OutputStream, boundary: String, name: String, file: File) {
        out.write("--$boundary\r\n".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n".toByteArray()
        )
        out.write("Content-Type: video/mp4\r\n\r\n".toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.write("\r\n".toByteArray())
    }
}
