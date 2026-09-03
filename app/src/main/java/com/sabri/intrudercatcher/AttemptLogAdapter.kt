package com.sabri.intrudercatcher

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttemptLogAdapter(
    private val entries: List<AttemptLogManager.Entry>,
    private val packageManager: PackageManager,
    private val ownPackageName: String
) : RecyclerView.Adapter<AttemptLogAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("d MMM yyyy — h:mm a", Locale("ar"))

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivLogAppIcon)
        val label: TextView = view.findViewById(R.id.tvLogAppLabel)
        val details: TextView = view.findViewById(R.id.tvLogDetails)
        val status: TextView = view.findViewById(R.id.tvLogStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attempt_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context

        holder.label.text = entry.label

        val methodLabel = when (entry.method) {
            AttemptLogManager.METHOD_PIN -> context.getString(R.string.applock_type_pin)
            AttemptLogManager.METHOD_PATTERN -> context.getString(R.string.applock_type_pattern)
            AttemptLogManager.METHOD_BIOMETRIC -> context.getString(R.string.applock_method_face)
            AttemptLogManager.METHOD_ADMIN_DISABLED -> context.getString(R.string.attempt_log_admin_disabled)
            else -> entry.method
        }
        holder.details.text = "$methodLabel — ${dateFormat.format(Date(entry.timestampMillis))}"

        if (entry.success) {
            holder.status.text = context.getString(R.string.attempt_log_success)
            holder.status.setTextColor(0xFF4CAF50.toInt())
        } else {
            holder.status.text = context.getString(R.string.attempt_log_failed)
            holder.status.setTextColor(0xFFF44336.toInt())
        }

        val icon = try {
            if (entry.packageName == ownPackageName) {
                context.packageManager.getApplicationIcon(ownPackageName)
            } else {
                packageManager.getApplicationIcon(entry.packageName)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // التطبيق أُزيل بعد تسجيل المحاولة - نستخدم أيقونة IntruderCatcher كبديل
            try {
                context.packageManager.getApplicationIcon(ownPackageName)
            } catch (_: Exception) {
                null
            }
        }
        holder.icon.setImageDrawable(icon)
    }

    override fun getItemCount(): Int = entries.size
}
