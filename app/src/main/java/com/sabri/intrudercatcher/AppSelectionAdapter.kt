package com.sabri.intrudercatcher

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * V13: استخدمنا ImageView بدل ImageButton للأيقونتين - بعض أجهزة سامسونج
 * (One UI) بتطبّق تخصيصات على ImageButton ممكن تخفي المحتوى بالكامل على
 * أجهزة معينة. ImageView + clickable=true أكثر استقرارًا واختبارًا.
 *
 * V14: أضفنا فلترة بالاسم لدعم شريط البحث - القائمة الكاملة تبقى محفوظة
 * دائمًا، وfilter() بس بتغيّر القائمة المعروضة بدون فقدان أي بيانات.
 */
class AppSelectionAdapter(
    private val fullApps: List<LockableApp>,
    private val onLockIconClick: (LockableApp) -> Unit,
    private val onSettingsClick: (LockableApp) -> Unit
) : RecyclerView.Adapter<AppSelectionAdapter.ViewHolder>() {

    private var displayedApps: List<LockableApp> = fullApps

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val label: TextView = view.findViewById(R.id.tvAppLabel)
        val lockToggle: ImageView = view.findViewById(R.id.ivLockToggle)
        val settingsButton: ImageView = view.findViewById(R.id.ivAppSettings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = displayedApps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.lockToggle.setImageResource(
            if (app.locked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open
        )
        holder.lockToggle.setOnClickListener { onLockIconClick(app) }
        holder.settingsButton.setOnClickListener { onSettingsClick(app) }

        // V18: خلفية الصف بتتلوّن حسب المظهر المختار - النص نفسه فاضل أبيض
        // ثابت دايمًا (مش متأثر باللون) عشان يفضل واضح ومقروء مهما كان اللون
        val palette = ThemeManager.currentPalette(holder.itemView.context)
        holder.itemView.setBackgroundColor(palette.surface)
    }

    override fun getItemCount(): Int = displayedApps.size

    /** يحدّث أيقونة صف واحد فقط بدون إعادة رسم القائمة كلها */
    fun notifyAppChanged(packageName: String) {
        val index = displayedApps.indexOfFirst { it.packageName == packageName }
        if (index != -1) notifyItemChanged(index)
    }

    /** V14: فلترة القائمة المعروضة بالاسم - القائمة الأصلية الكاملة تفضل محفوظة دائمًا */
    fun filter(query: String) {
        displayedApps = if (query.isBlank()) {
            fullApps
        } else {
            fullApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}
