package com.sabri.intrudercatcher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.cardGrantCamera)?.setOnClickListener {
            // إضافة الإجراء الخاص بالكاميرا
        }
        findViewById<android.view.View>(R.id.cardGrantAdmin)?.setOnClickListener {
            // إضافة الإجراء الخاص بمدير الجهاز
        }
        findViewById<android.view.View>(R.id.cardViewGallery)?.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAppLock)?.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAutoBackup)?.setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardAttemptLog)?.setOnClickListener {
            startActivity(Intent(this, AttemptLogActivity::class.java))
        }
    }
}
