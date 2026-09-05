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
        setupGridUI()
    }

    private fun setupGridUI() {
        val btnCamera = findViewById<View>(R.id.btnGrantCamera)
        btnCamera?.findViewById<ImageView>(R.id.imgIcon)?.setImageResource(R.drawable.ic_grid_camera)
        btnCamera?.findViewById<TextView>(R.id.txtTitle)?.setText(R.string.grant_camera)
        btnCamera?.setOnClickListener { }

        val btnAdmin = findViewById<View>(R.id.btnGrantAdmin)
        btnAdmin?.findViewById<ImageView>(R.id.imgIcon)?.setImageResource(R.drawable.ic_grid_shield)
        btnAdmin?.findViewById<TextView>(R.id.txtTitle)?.setText(R.string.grant_admin)
        btnAdmin?.setOnClickListener { }

        val btnGallery = findViewById<View>(R.id.btnViewGallery)
        btnGallery?.findViewById<ImageView>(R.id.imgIcon)?.setImageResource(R.drawable.ic_grid_gallery)
        btnGallery?.findViewById<TextView>(R.id.txtTitle)?.setText(R.string.view_gallery)
        btnGallery?.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        val btnAppLock = findViewById<View>(R.id.btnAppLock)
        btnAppLock?.findViewById<ImageView>(R.id.imgIcon)?.setImageResource(R.drawable.ic_grid_lock)
        btnAppLock?.findViewById<TextView>(R.id.txtTitle)?.setText(R.string.applock_open_feature)
        btnAppLock?.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        val btnBackup = findViewById<View>(R.id.btnAutoBackup)
        btnBackup?.findViewById<ImageView>(R.id.imgIcon)?.setImageResource(R.drawable.ic_grid_backup)
        btnBackup?.findViewById<TextView>(R.id.txtTitle)?.setText(R.string.backup_settings_title)
        btnBackup?.setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }

        val btnAttemptLog = findViewById<View>(R.id.btnAttemptLog)
        btnAttemptLog?.findViewById<ImageView>(R.id.imgIcon)?.setImageResource(R.drawable.ic_grid_history)
        btnAttemptLog?.findViewById<TextView>(R.id.txtTitle)?.setText(R.string.attempt_log_title)
        btnAttemptLog?.setOnClickListener {
            startActivity(Intent(this, AttemptLogActivity::class.java))
        }
    }
}
