package com.sabri.intrudercatcher

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * V28: تشفير الملفات قبل رفعها لأي مكان خارجي (Google Drive) باستخدام
 * مفتاح AES-256 محفوظ داخل Android Keystore نفسه - المفتاح لا يخرج من
 * الجهاز أبدًا ولا يمكن استخراجه حتى لو انفتح تخزين التطبيق، وهذا أقوى
 * من تشفير بكلمة مرور عادية يمكن تخمينها.
 */
object CryptoUtil {

    private const val KEYSTORE_ALIAS = "intruder_catcher_backup_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * يشفّر الملف المُدخل ويكتب الناتج (IV بطول 12 بايت + النص المشفّر) بملف جديد.
     * الـ IV يُخزَّن مع الملف نفسه لأنه ليس سريًا - فقط المفتاح بالـ Keystore هو السر.
     */
    fun encryptFile(input: File, output: File) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv

        output.outputStream().use { out ->
            out.write(iv)
            input.inputStream().use { inp ->
                val buffer = ByteArray(8192)
                var encryptedChunk: ByteArray?
                var read: Int
                while (inp.read(buffer).also { read = it } != -1) {
                    encryptedChunk = cipher.update(buffer, 0, read)
                    if (encryptedChunk != null) out.write(encryptedChunk)
                }
                cipher.doFinal()?.let { out.write(it) }
            }
        }
    }

    /** يفك تشفير ملف نُسخ احتياطيًا سابقًا - غير مستخدم حاليًا داخل التطبيق، متاح للاسترجاع اليدوي مستقبلًا */
    fun decryptFile(input: File, output: File) {
        input.inputStream().use { inp ->
            val iv = ByteArray(12)
            inp.read(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            output.outputStream().use { out ->
                val buffer = ByteArray(8192)
                var read: Int
                while (inp.read(buffer).also { read = it } != -1) {
                    cipher.update(buffer, 0, read)?.let { out.write(it) }
                }
                cipher.doFinal()?.let { out.write(it) }
            }
        }
    }
}
