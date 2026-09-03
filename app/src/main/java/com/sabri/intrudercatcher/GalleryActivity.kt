package com.sabri.intrudercatcher

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.sabri.intrudercatcher.databinding.ActivityGalleryBinding
import java.io.File
import java.io.FileInputStream

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: PhotoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // فحص وحذف تلقائي لأي عنصر بالسلة تعدّى 30 يوم، بكل مرة تُفتح الشاشة
        TrashManager.purgeExpired(this)

        loadPhotos()

        binding.btnOpenTrash.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }

        binding.btnShare.setOnClickListener { shareSelected() }
        binding.btnCopy.setOnClickListener { copySelectedToGallery(deleteOriginal = false) }
        binding.btnDelete.setOnClickListener { confirmDeleteSelected() }
    }

    override fun onResume() {
        super.onResume()
        // لو رجع المستخدم من شاشة السلة بعد استرجاع صورة، حدّث القائمة
        loadPhotos()
    }

    private fun loadPhotos() {
        val photos = TrashManager.photosDir(this)
            .listFiles { f -> f.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.toMutableList() ?: mutableListOf()

        if (photos.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvPhotos.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvPhotos.visibility = View.VISIBLE
            binding.rvPhotos.layoutManager = GridLayoutManager(this, 2)
            adapter = PhotoAdapter(
                photos = photos,
                onSelectionChanged = ::updateSelectionUi,
                onPhotoTapped = { tappedFile -> openPhotoViewer(tappedFile) }
            )
            binding.rvPhotos.adapter = adapter
        }
    }

    /**
     * يفتح شاشة عرض الصورة بحجم كامل، مع تمرير كل الصور الحالية بنفس
     * ترتيب القائمة، حتى يقدر المستخدم يتنقل بينها يمين/يسار من نفس
     * الصورة اللي ضغط عليها.
     */
    private fun openPhotoViewer(tappedFile: File) {
        if (!::adapter.isInitialized) return
        val currentPhotos = adapter.getAllFiles()
        val startIndex = currentPhotos.indexOf(tappedFile).coerceAtLeast(0)
        val intent = Intent(this, PhotoViewerActivity::class.java).apply {
            putStringArrayListExtra(
                PhotoViewerActivity.EXTRA_PHOTO_PATHS,
                ArrayList(currentPhotos.map { it.absolutePath })
            )
            putExtra(PhotoViewerActivity.EXTRA_START_INDEX, startIndex)
        }
        startActivity(intent)
    }

    private fun updateSelectionUi(count: Int) {
        val inSelection = count > 0
        binding.toolbarSelection.visibility = if (inSelection) View.VISIBLE else View.GONE
        binding.toolbarNormal.visibility = if (inSelection) View.GONE else View.VISIBLE
        binding.tvSelectedCount.text = "$count محدد"
    }

    private fun shareSelected() {
        if (!::adapter.isInitialized) return
        val files = adapter.getSelectedFiles()
        if (files.isEmpty()) return
        val uris = files.map { file ->
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }
        // V28: قد تكون العناصر المحددة خليط صور وفيديوهات - نوع عام يناسب الاثنين معًا
        val allVideos = files.all { it.extension.equals("mp4", ignoreCase = true) }
        val allImages = files.all { !it.extension.equals("mp4", ignoreCase = true) }
        val mimeType = when {
            allVideos -> "video/mp4"
            allImages -> "image/jpeg"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "مشاركة الصور"))
    }

    /**
     * ينسخ الصور/الفيديوهات المحددة لمعرض الجهاز العام (MediaStore) - يعمل
     * بدون أي صلاحية تخزين إضافية على أندرويد 10+ بفضل Scoped Storage.
     * لو deleteOriginal=true، يحذف الأصل بعد نجاح النسخ (يعني "نقل").
     */
    private fun copySelectedToGallery(deleteOriginal: Boolean) {
        if (!::adapter.isInitialized) return
        val files = adapter.getSelectedFiles()
        if (files.isEmpty()) return
        var successCount = 0
        val movedFiles = mutableListOf<File>()

        files.forEach { file ->
            val isVideo = file.extension.equals("mp4", ignoreCase = true)
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                if (isVideo) {
                    put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Detector")
                    }
                } else {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Detector")
                    }
                }
            }
            val uri = contentResolver.insert(collection, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input -> input.copyTo(out) }
                }
                successCount++
                if (deleteOriginal) {
                    TrashManager.moveToTrash(this, file)
                    movedFiles.add(file)
                }
            }
        }

        if (movedFiles.isNotEmpty()) adapter.removeFiles(movedFiles)
        if (adapter.itemCount == 0) loadPhotos()

        val action = if (deleteOriginal) "نُقلت" else "نُسخت"
        Toast.makeText(this, "$action $successCount صورة إلى معرض الجهاز", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteSelected() {
        if (!::adapter.isInitialized) return
        val files = adapter.getSelectedFiles()
        if (files.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("حذف ${files.size} صورة")
            .setMessage("ستُنقل لسلة المحذوفات وتُحذف نهائيًا تلقائيًا بعد 30 يومًا، إلا لو استرجعتها قبل ذلك.")
            .setPositiveButton("حذف") { _, _ ->
                files.forEach { TrashManager.moveToTrash(this, it) }
                adapter.removeFiles(files)
                if (adapter.itemCount == 0) loadPhotos()
                Toast.makeText(this, "نُقلت ${files.size} صورة لسلة المحذوفات", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
