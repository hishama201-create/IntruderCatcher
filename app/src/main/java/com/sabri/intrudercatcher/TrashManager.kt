package com.sabri.intrudercatcher

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * يدير سلة المحذوفات: كل صورة تُحذف تُنقل فعليًا لمجلد ".trash" الخاص
 * بالتطبيق (مو حذف فوري)، مع تشفير وقت الحذف داخل اسم الملف نفسه.
 * أي صورة تعدّت 30 يوم داخل السلة تُحذف نهائيًا تلقائيًا.
 */
object TrashManager {

    private const val TRASH_DIR_NAME = ".trash"
    private const val RETENTION_DAYS = 30L

    fun photosDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "Detector")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun trashDir(context: Context): File {
        val dir = File(photosDir(context), TRASH_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** ينقل صورة من مجلد الصور الرئيسي إلى السلة، ويرجع true لو نجح */
    fun moveToTrash(context: Context, photo: File): Boolean {
        val deletedAt = System.currentTimeMillis()
        val target = File(trashDir(context), "${deletedAt}__${photo.name}")
        return photo.renameTo(target)
    }

    /** يرجع صورة من السلة لمجلد الصور الأصلي */
    fun restore(context: Context, trashedFile: File): Boolean {
        val originalName = trashedFile.name.substringAfter("__")
        val target = File(photosDir(context), originalName)
        return trashedFile.renameTo(target)
    }

    /** حذف نهائي فوري لعنصر واحد من السلة (بدون رجعة) */
    fun deletePermanently(trashedFile: File): Boolean = trashedFile.delete()

    fun listTrashed(context: Context): List<File> =
        trashDir(context).listFiles()?.sortedByDescending { extractDeletedAt(it) } ?: emptyList()

    fun extractDeletedAt(file: File): Long =
        file.name.substringBefore("__").toLongOrNull() ?: file.lastModified()

    fun daysRemaining(file: File): Long {
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - extractDeletedAt(file))
        return (RETENTION_DAYS - elapsedDays).coerceAtLeast(0)
    }

    /**
     * يفحص كل عناصر السلة ويحذف نهائيًا أي عنصر تعدّى 30 يوم.
     * يُستدعى عند فتح التطبيق (MainActivity) وعند فتح شاشة السلة.
     */
    fun purgeExpired(context: Context) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        trashDir(context).listFiles()?.forEach { file ->
            if (extractDeletedAt(file) < cutoff) file.delete()
        }
    }
}
