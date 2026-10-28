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
    private var fullApps: List<LockableApp>,
    private val onLockIconClick: (LockableApp) -> Unit,
    private val onSettingsClick: (LockableApp) -> Unit
) : RecyclerView.Adapter<AppSelectionAdapter.ViewHolder>() {

    private var displayedApps: List<LockableApp> = fullApps
    private var currentQuery: String = ""

    // V48: تحميل الأيقونات كسولًا مع ذاكرة مؤقتة
    private val iconCache = android.util.LruCache<String, android.graphics.drawable.Drawable>(300)
    private val iconExecutor = java.util.concurrent.ThreadPoolExecutor(
        2, 2, 3, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.LinkedBlockingQueue<Runnable>()
    ).apply { allowCoreThreadTimeOut(true) }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** V48: يستبدل القائمة بعد انتهاء التحميل الخلفي مع الحفاظ على نص البحث الحالي */
    fun setApps(apps: List<LockableApp>) {
        fullApps = apps
        filter(currentQuery)
    }

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
        val pkg = app.packageName
        holder.icon.tag = pkg
        val cachedIcon = iconCache.get(pkg)
        if (cachedIcon != null) {
            holder.icon.setImageDrawable(cachedIcon)
        } else {
            holder.icon.setImageDrawable(null)
            val pm = holder.itemView.context.packageManager
            iconExecutor.execute {
                val d = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
                if (d != null) {
                    iconCache.put(pkg, d)
                    mainHandler.post {
                        if (holder.icon.tag == pkg) holder.icon.setImageDrawable(d)
                    }
                }
            }
        }
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
        currentQuery = query
        displayedApps = if (query.isBlank()) {
            fullApps
        } else {
            fullApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}
