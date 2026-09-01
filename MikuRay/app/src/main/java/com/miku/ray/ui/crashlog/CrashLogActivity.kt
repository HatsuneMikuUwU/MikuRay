package com.miku.ray.ui.crashlog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.crashreporter.CrashReporter
import com.miku.ray.crashreporter.utils.CrashUtil
import com.miku.ray.extension.toastSuccess
import com.miku.ray.ui.base.BaseActivity

class CrashLogActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_log)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(
            toolbar,
            showHomeAsUp = false,
            title = getString(R.string.crash_log_title),
            subtitle = getString(R.string.subtitle_crash_log)
        )
        toolbar.setNavigationOnClickListener { finishAndRemoveTask() }

        val logText = findViewById<android.widget.TextView>(R.id.crash_log_text)
        val clearButton = findViewById<MaterialButton>(R.id.clear_all_button)
        val copyButton = findViewById<MaterialButton>(R.id.copy_button)
        val shareButton = findViewById<MaterialButton>(R.id.share_button)

        fun refreshLog() {
            val content = CrashReporter.getAllCrashInfo()
            logText.text = content.ifEmpty { getString(R.string.crash_log_empty) }
            val hasLogs = content.isNotEmpty()
            copyButton.isEnabled = hasLogs
            shareButton.isEnabled = hasLogs
        }

        clearButton.setOnClickListener {
            CrashUtil.clearAllCrashLogs()
            refreshLog()
            toastSuccess(getString(R.string.crash_log_cleared))
        }
        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_log_title), logText.text))
            toastSuccess(getString(R.string.crash_log_copied))
        }
        shareButton.setOnClickListener { shareToTelegram(logText.text) }
        refreshLog()
    }

    private fun shareToTelegram(logContent: CharSequence) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_log_title), logContent))

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.TG_CRASH_REPORT_URL)))
            toastSuccess(getString(R.string.crash_log_share_telegram_hint))
        } catch (e: Exception) {
            // No app can handle the link (e.g. no browser) - fall back to the generic
            // share sheet instead of crashing.
            CrashReporter.shareCrash(this)
        }
    }
}
