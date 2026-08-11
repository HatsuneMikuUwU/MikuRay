package com.neko.crashlog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.ui.base.BaseActivity
import java.io.File

class CrashDialogActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = File(filesDir, "crash_log.txt")
        val crashLog = if (file.exists()) file.readText() else "No crash log found."

        MaterialAlertDialogBuilder(this)
            .setTitle("Crash Log")
            .setIcon(com.v2ray.ang.R.drawable.ic_warning)
            .setMessage(crashLog)
            .setPositiveButton("Copy") { _, _ ->
                copyToClipboard(crashLog)
            }
            .setNegativeButton("Share") { _, _ ->
                shareCrashLog(crashLog)
            }
            .setNeutralButton("Close") { _, _ ->
                file.delete()
                finish()
            }
            .setOnDismissListener {
                file.delete()
                finish()
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", text))
    }

    private fun shareCrashLog(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share Crash Log"))
    }
}
