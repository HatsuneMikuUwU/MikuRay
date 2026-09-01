package com.miku.ray.crashreporter.utils

import android.content.Intent
import com.miku.ray.crashreporter.CrashReporter
import com.miku.ray.ui.crashlog.CrashLogActivity

class CrashReporterExceptionHandler : Thread.UncaughtExceptionHandler {
    private val exceptionHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { CrashUtil.saveCrashReport(throwable) }
        runCatching {
            val intent = Intent(CrashReporter.context, CrashLogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            CrashReporter.context.startActivity(intent)
        }

        // Keep the process alive long enough for the report screen to appear,
        // then preserve Android's normal crash termination behavior.
        try {
            Thread.sleep(1200L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            exceptionHandler?.uncaughtException(thread, throwable)
        }
    }
}
