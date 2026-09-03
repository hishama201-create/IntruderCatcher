package com.sabri.intrudercatcher

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * محول لعرض قائمة صور/فيديوهات بحجم كامل داخل ViewPager2، مع دعم
 * التمرير يمين/يسار للتنقل. V28: يشغّل مقاطع الفيديو تلقائيًا بحلقة
 * متكررة (loop) صامتة عند فتح الصفحة، والضغط عليها يوقف/يشغّل يدويًا.
 */
class PhotoViewerAdapter(
    private val photos: List<File>
) : RecyclerView.Adapter<PhotoViewerAdapter.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFullPhoto: ImageView = view.findViewById(R.id.ivFullPhoto)
        val videoFull: VideoView = view.findViewById(R.id.videoFull)
        val ivPlayOverlay: ImageView = view.findViewById(R.id.ivPlayOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_viewer_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val file = photos[position]
        val isVideo = file.extension.equals("mp4", ignoreCase = true)

        if (isVideo) {
            holder.ivFullPhoto.visibility = View.GONE
            holder.videoFull.visibility = View.VISIBLE
            holder.videoFull.setVideoPath(file.absolutePath)
            holder.videoFull.setOnPreparedListener { mp ->
                mp.isLooping = true
                holder.videoFull.start()
                holder.ivPlayOverlay.visibility = View.GONE
            }
            holder.videoFull.setOnClickListener {
                if (holder.videoFull.isPlaying) {
                    holder.videoFull.pause()
                    holder.ivPlayOverlay.visibility = View.VISIBLE
                } else {
                    holder.videoFull.start()
                    holder.ivPlayOverlay.visibility = View.GONE
                }
            }
        } else {
            holder.videoFull.visibility = View.GONE
            holder.ivPlayOverlay.visibility = View.GONE
            holder.ivFullPhoto.visibility = View.VISIBLE
            // decode بالحجم الكامل هنا مقبول لأن صورة واحدة تُعرض بكل مرة
            // (على عكس الشبكة اللي تعرض عشرات الصور دفعة وحدة)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            holder.ivFullPhoto.setImageBitmap(bitmap)
        }
    }

    override fun getItemCount(): Int = photos.size
}
