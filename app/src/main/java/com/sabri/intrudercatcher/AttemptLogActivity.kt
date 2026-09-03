package com.sabri.intrudercatcher

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AttemptLogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attempt_log)

        // V18: رجّعنا الألوان الثابتة (XML الأصلي) - المظهر القابل للتغيير بقى خاص بس بشاشة "قائمة التطبيقات"
        findViewById<View>(R.id.btnLogBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnClearLog).setOnClickListener { confirmClear() }

        loadEntries()
    }

    override fun onResume() {
        super.onResume()
        loadEntries()
    }

    private fun loadEntries() {
        val entries = AttemptLogManager.getAll(this)
        val recycler = findViewById<RecyclerView>(R.id.rvAttemptLog)
        val emptyText = findViewById<TextView>(R.id.tvLogEmpty)

        if (entries.isEmpty()) {
            recycler.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = AttemptLogAdapter(entries, packageManager, packageName)
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.attempt_log_clear))
            .setMessage(getString(R.string.attempt_log_clear_confirm))
            .setPositiveButton(getString(R.string.applock_ok)) { _, _ ->
                AttemptLogManager.clear(this)
                loadEntries()
            }
            .setNegativeButton(getString(R.string.applock_cancel), null)
            .show()
    }
}
