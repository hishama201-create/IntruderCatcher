package com.sabri.intrudercatcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupGridButtons()
    }

    private fun setupGridButtons() {
        // 1. زر منح صلاحية الكاميرا
        val btnCamera = findViewById<View>(R.id.btnCamera)
        btnCamera.findViewById<ImageView>(R.id.imgIcon).setImageResource(R.drawable.ic_grid_camera)
        btnCamera.findViewById<TextView>(R.id.txtTitle).setText(R.string.grant_camera)
        btnCamera.setOnClickListener {
            // أضف إجرائية الكاميرا هنا أو استدعاء الأذونات
        }

        // 2. زر تفعيل مدير الجهاز
        val btnAdmin = findViewById<View>(R.id.btnAdmin)
        btnAdmin.findViewById<ImageView>(R.id.imgIcon).setImageResource(R.drawable.ic_grid_shield)
        btnAdmin.findViewById<TextView>(R.id.txtTitle).setText(R.string.grant_admin)
        btnAdmin.setOnClickListener {
            // أضف إجرائية مدير الجهاز
        }

        // 3. زر عرض الصور الملتقطة
        val btnGallery = findViewById<View>(R.id.btnGallery)
        btnGallery.findViewById<ImageView>(R.id.imgIcon).setImageResource(R.drawable.ic_grid_gallery)
        btnGallery.findViewById<TextView>(R.id.txtTitle).setText(R.string.view_gallery)
        btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        // 4. زر الانتقال إلى قفل التطبيقات
        val btnAppLock = findViewById<View>(R.id.btnAppLock)
        btnAppLock.findViewById<ImageView>(R.id.imgIcon).setImageResource(R.drawable.ic_grid_lock)
        btnAppLock.findViewById<TextView>(R.id.txtTitle).setText(R.string.applock_open_feature)
        btnAppLock.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        // 5. زر النسخ الاحتياطي
        val btnBackup = findViewById<View>(R.id.btnBackup)
        btnBackup.findViewById<ImageView>(R.id.imgIcon).setImageResource(R.drawable.ic_grid_backup)
        btnBackup.findViewById<TextView>(R.id.txtTitle).setText(R.string.auto_backup_title)
        btnBackup.setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }
    }
}
