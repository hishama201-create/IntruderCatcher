package com.sabri.intrudercatcher

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sabri.intrudercatcher.databinding.ActivityTrashBinding
import java.io.File

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private var adapter: TrashAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TrashManager.purgeExpired(this)
        binding.toolbarTitle.text = "سلة المحذوفات"
        loadTrash()

        binding.btnSelectAll.setOnClickListener { adapter?.toggleSelectAll() }
        binding.btnCancelSelection.setOnClickListener { adapter?.clearSelection() }
        binding.btnRestoreSelected.setOnClickListener { restoreSelected() }
        binding.btnDeleteSelectedForever.setOnClickListener { confirmDeleteSelectedForever() }
    }

    private fun loadTrash() {
        val items = TrashManager.listTrashed(this)
        if (items.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvTrash.visibility = View.GONE
            adapter = null
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvTrash.visibility = View.VISIBLE
            binding.rvTrash.layoutManager = GridLayoutManager(this, 2)
            adapter = TrashAdapter(
                items.toMutableList(),
                onSelectionChanged = ::updateSelectionUi,
                onRestore = { file ->
                    TrashManager.restore(this, file)
                    Toast.makeText(this, "تم الاسترجاع", Toast.LENGTH_SHORT).show()
                    loadTrash()
                },
                onDeleteForever = { file ->
                    AlertDialog.Builder(this)
                        .setTitle("حذف نهائي")
                        .setMessage("هذا الإجراء لا يمكن التراجع عنه. متأكد؟")
                        .setPositiveButton("حذف نهائيًا") { _, _ ->
                            TrashManager.deletePermanently(file)
                            loadTrash()
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            )
            binding.rvTrash.adapter = adapter
        }
        updateSelectionUi(0)
    }

    private fun updateSelectionUi(count: Int) {
        val inSelection = count > 0
        binding.toolbarSelection.visibility = if (inSelection) View.VISIBLE else View.GONE
        binding.toolbarNormal.visibility = if (inSelection) View.GONE else View.VISIBLE
        binding.tvSelectedCount.text = "$count محدد"
    }

    private fun restoreSelected() {
        val current = adapter ?: return
        val files = current.getSelectedFiles()
        if (files.isEmpty()) return
        files.forEach { TrashManager.restore(this, it) }
        Toast.makeText(this, "تم استرجاع ${files.size} صورة", Toast.LENGTH_SHORT).show()
        loadTrash()
    }

    private fun confirmDeleteSelectedForever() {
        val current = adapter ?: return
        val files = current.getSelectedFiles()
        if (files.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("حذف ${files.size} صورة نهائيًا")
            .setMessage("هذا الإجراء لا يمكن التراجع عنه. متأكد؟")
            .setPositiveButton("حذف نهائيًا") { _, _ ->
                files.forEach { TrashManager.deletePermanently(it) }
                Toast.makeText(this, "تم حذف ${files.size} صورة نهائيًا", Toast.LENGTH_SHORT).show()
                loadTrash()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}

/**
 * محول شبكة السلة، بدعم وضع "تحديد متعدد" يُفعّل بالضغط المطوّل على
 * صورة السلة نفسها (زي معرض الصور تمامًا)، مع بقاء زري "استرجاع" و
 * "حذف نهائي" الفرديين شغالين لحالهم في كل الأحوال بدون علاقة بوضع التحديد.
 */
private class TrashAdapter(
    private val items: MutableList<File>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onRestore: (File) -> Unit,
    private val onDeleteForever: (File) -> Unit
) : RecyclerView.Adapter<TrashAdapter.VH>() {

    private val selected = mutableSetOf<File>()
    private var selectionMode = false

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        val vSelectedOverlay: View = view.findViewById(R.id.vSelectedOverlay)
        val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
        val tvDaysLeft: TextView = view.findViewById(R.id.tvDaysLeft)
        val btnRestore: TextView = view.findViewById(R.id.btnRestore)
        val btnDelete: TextView = view.findViewById(R.id.btnDeleteForever)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trash_photo, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        val isVideo = file.extension.equals("mp4", ignoreCase = true)
        val bitmap = if (isVideo) {
            android.media.ThumbnailUtils.createVideoThumbnail(
                file.absolutePath, android.provider.MediaStore.Video.Thumbnails.MINI_KIND
            )
        } else {
            BitmapFactory.decodeFile(file.absolutePath)
        }
        holder.ivPhoto.setImageBitmap(bitmap)
        val daysLeft = TrashManager.daysRemaining(file)
        holder.tvDaysLeft.text = "يُحذف نهائيًا خلال $daysLeft يوم"

        val isSelected = selected.contains(file)
        holder.vSelectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.ivPhoto.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                toggleSelection(file)
                notifyDataSetChanged()
            }
            true
        }

        holder.ivPhoto.setOnClickListener {
            if (selectionMode) {
                toggleSelection(file)
                notifyItemChanged(position)
            }
        }

        // الأزرار الفردية تفضل شغالة دائمًا، بوضع التحديد أو بدونه
        holder.btnRestore.setOnClickListener { onRestore(file) }
        holder.btnDelete.setOnClickListener { onDeleteForever(file) }
    }

    private fun toggleSelection(file: File) {
        if (selected.contains(file)) selected.remove(file) else selected.add(file)
        if (selected.isEmpty()) selectionMode = false
        onSelectionChanged(selected.size)
    }

    fun toggleSelectAll() {
        if (selected.size == items.size) {
            selected.clear()
            selectionMode = false
        } else {
            selected.clear()
            selected.addAll(items)
            selectionMode = true
        }
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun clearSelection() {
        selected.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedFiles(): List<File> = selected.toList()

    override fun getItemCount(): Int = items.size
}
