package com.sabri.intrudercatcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * V15: سجل محاولات فتح التطبيقات المقفولة (نجحت أو فشلت) + محاولات فتح
 * IntruderCatcher نفسه + حدث تعطيل صلاحية مدير الجهاز. تخزين محلي بسيط
 * (بدون تشفير - المحتوى هنا أوقات وأسماء تطبيقات فقط، مو بيانات حساسة
 * زي الـ PIN)، بصيغة JSON داخل SharedPreferences عادية.
 *
 * نحتفظ بآخر [MAX_ENTRIES] محاولة فقط لمنع نمو الملف بلا حدود.
 */
object AttemptLogManager {

    private const val FILE_NAME = "attempt_log_prefs"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 200

    const val METHOD_PIN = "pin"
    const val METHOD_PATTERN = "pattern"
    const val METHOD_BIOMETRIC = "biometric"
    const val METHOD_ADMIN_DISABLED = "admin_disabled"

    data class Entry(
        val timestampMillis: Long,
        val packageName: String,
        val label: String,
        val method: String,
        val success: Boolean
    )

    fun add(
        context: Context,
        packageName: String,
        label: String,
        method: String,
        success: Boolean
    ) {
        val list = getAll(context).toMutableList()
        list.add(
            0,
            Entry(
                timestampMillis = System.currentTimeMillis(),
                packageName = packageName,
                label = label,
                method = method,
                success = success
            )
        )
        while (list.size > MAX_ENTRIES) list.removeAt(list.size - 1)
        save(context, list)
    }

    fun getAll(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    timestampMillis = o.getLong("t"),
                    packageName = o.getString("p"),
                    label = o.getString("l"),
                    method = o.getString("m"),
                    success = o.getBoolean("s")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            o.put("t", e.timestampMillis)
            o.put("p", e.packageName)
            o.put("l", e.label)
            o.put("m", e.method)
            o.put("s", e.success)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
