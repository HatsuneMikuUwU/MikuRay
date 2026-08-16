package com.neko.crashlog


import com.miku.ray.remixicon.R as RemixR
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.ui.base.BaseActivity
import java.io.File

class CrashDialogActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // This activity is the last line of defense for surfacing a crash - it must not be
        // able to crash itself (e.g. because of a themed/resource dependency that's part of
        // what's broken). Fall back to the plainest possible dialog if anything here throws.
        try {
            super.onCreate(savedInstanceState)
            showStyledDialog()
        } catch (e: Exception) {
            showFallbackDialog()
        }
    }

    private fun showStyledDialog() {
        val file = File(filesDir, "crash_log.txt")
        val crashLog = if (file.exists()) file.readText() else "No crash log found."

        MaterialAlertDialogBuilder(this)
            .setTitle("Crash Log")
            .setIcon(RemixR.drawable.rmx_error_warning_line)
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

    private fun showFallbackDialog() {
        val file = File(filesDir, "crash_log.txt")
        val crashLog = if (file.exists()) file.readText() else "No crash log found."

        AlertDialog.Builder(this)
            .setTitle("Crash Log")
            .setMessage(crashLog)
            .setPositiveButton("Copy") { _, _ -> copyToClipboard(crashLog) }
            .setNegativeButton("Share") { _, _ -> shareCrashLog(crashLog) }
            .setNeutralButton("Close") { _, _ -> file.delete(); finish() }
            .setOnDismissListener { file.delete(); finish() }
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
