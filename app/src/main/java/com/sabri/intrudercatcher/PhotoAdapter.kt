package com.sabri.intrudercatcher

import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * محول لعرض الصور بشبكة، مع دعم وضع "تحديد متعدد" يُفعّل بالضغط
 * المطول (Long Press) على أي صورة، بعدها الضغط العادي يبدّل التحديد.
 * V28: يدعم أيضًا عرض صورة مصغّرة (thumbnail) لملفات الفيديو القصيرة.
 */
class PhotoAdapter(
    private val photos: MutableList<File>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onPhotoTapped: (File) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private val selected = mutableSetOf<File>()
    var selectionMode = false
        private set

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val vSelectedOverlay: View = view.findViewById(R.id.vSelectedOverlay)
        val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
        val ivPlayBadge: ImageView? = view.findViewById(R.id.ivPlayBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val file = photos[position]
        val isVideo = file.extension.equals("mp4", ignoreCase = true)
        if (isVideo) {
            val bitmap = ThumbnailUtils.createVideoThumbnail(
                file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND
            )
            holder.ivPhoto.setImageBitmap(bitmap)
            holder.ivPlayBadge?.visibility = View.VISIBLE
        } else {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            holder.ivPhoto.setImageBitmap(bitmap)
            holder.ivPlayBadge?.visibility = View.GONE
        }
        holder.tvTime.text = file.nameWithoutExtension.removePrefix("intruder_")

        val isSelected = selected.contains(file)
        holder.vSelectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                toggleSelection(file)
                notifyDataSetChanged()
            }
            true
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(file)
                notifyItemChanged(position)
            } else {
                onPhotoTapped(file)
            }
        }
    }

    private fun toggleSelection(file: File) {
        if (selected.contains(file)) selected.remove(file) else selected.add(file)
        if (selected.isEmpty()) selectionMode = false
        onSelectionChanged(selected.size)
    }

    fun getSelectedFiles(): List<File> = selected.toList()

    /** ترجع كل الصور المعروضة حاليًا بنفس ترتيبها، لتمريرها لعارض الصور */
    fun getAllFiles(): List<File> = photos.toList()

    fun clearSelection() {
        selected.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    /** يزيل عناصر من القائمة بعد حذفها/نقلها بنجاح ويحدّث العرض */
    fun removeFiles(files: List<File>) {
        photos.removeAll(files)
        selected.removeAll(files)
        selectionMode = selected.isNotEmpty()
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    override fun getItemCount(): Int = photos.size
}
