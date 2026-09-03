package com.sabri.intrudercatcher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.sabri.intrudercatcher.databinding.ActivityPhotoViewerBinding
import java.io.File

/**
 * تعرض صورة واحدة بحجم كامل، مع إمكانية التمرير يمين/يسار للتنقل بين
 * كل الصور الملتقطة في نفس الجلسة (مو بس الصورة اللي ضُغط عليها).
 */
class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding

    companion object {
        const val EXTRA_PHOTO_PATHS = "extra_photo_paths"
        const val EXTRA_START_INDEX = "extra_start_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val paths = intent.getStringArrayListExtra(EXTRA_PHOTO_PATHS) ?: arrayListOf()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val photos = paths.map { File(it) }

        if (photos.isEmpty()) {
            finish()
            return
        }

        binding.photoViewPager.adapter = PhotoViewerAdapter(photos)
        binding.photoViewPager.setCurrentItem(startIndex, false)
        updateHeader(startIndex, photos)

        binding.photoViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateHeader(position, photos)
            }
        })

        binding.btnClose.setOnClickListener { finish() }
    }

    private fun updateHeader(position: Int, photos: List<File>) {
        binding.tvPageCounter.text = "${position + 1} / ${photos.size}"
        binding.tvPhotoTime.text = photos[position].nameWithoutExtension.removePrefix("intruder_")
    }
}
