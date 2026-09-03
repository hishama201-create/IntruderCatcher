package com.sabri.intrudercatcher

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/**
 * V28: نسخ احتياطي مشفّر تلقائي لفيديوهات المتطفلين على Google Drive
 * الخاص بالمستخدم (مجلد خاص بالتطبيق فقط عبر صلاحية DRIVE_FILE - لا
 * يرى ولا يصل لأي ملف آخر بحساب Drive). كل ملف يُشفَّر محليًا أولًا
 * (CryptoUtil) قبل الرفع، فحتى Google نفسها لا تقدر تفتح محتواه.
 *
 * يحتاج ربط حساب مرة واحدة فقط من شاشة الإعدادات (تسجيل دخول تفاعلي)،
 * وبعدها الرفع يصير صامتًا بالكامل بالخلفية مع كل عملية التقاط.
 */
object DriveBackupManager {

    private const val TAG = "DriveBackupManager"
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
    private val executor = Executors.newSingleThreadExecutor()

    fun getSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /** يُستدعى من شاشة الإعدادات بعد رجوع نتيجة تسجيل الدخول */
    fun handleSignInResult(data: Intent?, onResult: (email: String?, error: String?) -> Unit) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            onResult(account?.email, null)
        } catch (e: ApiException) {
            onResult(null, "خطأ (${e.statusCode})")
        }
    }

    fun signOut(context: Context) {
        getSignInClient(context).signOut()
        BackupPrefs.setDriveAccountEmail(context, null)
        BackupPrefs.setDriveEnabled(context, false)
    }

    /** يشفّر الملف ويرفعه بالخلفية - لا يوقف ولا يبطئ الخدمة المستدعية عند أي فشل (لا إنترنت، توكن منتهي...) */
    fun backupAsync(context: Context, sourceFile: File) {
        if (!BackupPrefs.isDriveEnabled(context)) return
        val account = getSignedInAccount(context) ?: return
        executor.execute {
            var encryptedFile: File? = null
            try {
                val token = fetchAccessToken(context, account.account) ?: return@execute
                encryptedFile = File(sourceFile.parentFile, "${sourceFile.name}.enc")
                CryptoUtil.encryptFile(sourceFile, encryptedFile)
                uploadToDrive(token, encryptedFile, "${sourceFile.name}.enc")
            } catch (e: Exception) {
                Log.e(TAG, "فشل النسخ الاحتياطي على Drive: ${e.message}")
            } finally {
                encryptedFile?.delete()
            }
        }
    }

    private fun fetchAccessToken(context: Context, account: Account?): String? {
        if (account == null) return null
        return try {
            GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
        } catch (e: Exception) {
            Log.e(TAG, "تعذّر الحصول على توكن Drive: ${e.message}")
            null
        }
    }

    /** رفع بسيط (Simple Upload) عبر Drive REST API v3 - مناسب للملفات الصغيرة (فيديو قصير 4 ثواني) */
    private fun uploadToDrive(accessToken: String, file: File, remoteName: String) {
        val boundary = "----IntruderCatcherDrive${UUID.randomUUID()}"
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.connectTimeout = 15000
        connection.readTimeout = 30000

        val metadata = """{"name":"$remoteName","parents":[],"appProperties":{"source":"IntruderCatcher"}}"""

        connection.outputStream.use { out ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            out.write(metadata.toByteArray())
            out.write("\r\n--$boundary\r\n".toByteArray())
            out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText()
            Log.e(TAG, "Drive رفض الرفع ($responseCode): $errorBody")
        } else {
            Log.d(TAG, "تم رفع النسخة المشفّرة لـ Drive بنجاح")
        }
        connection.disconnect()
    }
}
