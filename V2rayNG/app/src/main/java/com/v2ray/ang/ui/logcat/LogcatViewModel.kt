package com.v2ray.ang.ui.logcat

import android.os.Process
import androidx.lifecycle.ViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.util.InProcessLogBuffer
import com.v2ray.ang.util.LogUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

class LogcatViewModel : ViewModel() {
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var filteredLogs: List<String> = emptyList()
    private var currentFilter: String = ""

    var usedFallback: Boolean = false
        private set

    fun getAll(): List<String> = filteredLogs

    fun loadLogcat() {
        val lines = tryLogcatProcessBuilder()
            ?: tryLogcatPidOnly()
            ?: useInProcessBuffer()

        logsetsAll.clear()
        logsetsAll.addAll(lines)
        applyFilter()
    }

    private fun tryLogcatProcessBuilder(): List<String>? {
        return try {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "time",
                "-s", "GoLog,$ANG_PACKAGE,AndroidRuntime,System.err"
            )
                .redirectErrorStream(true)
                .start()

            val exited = process.waitFor(5, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                return null
            }

            val lines = process.inputStream.bufferedReader().readLines()
            if (lines.isEmpty()) null
            else {
                usedFallback = false
                lines.reversed()
            }
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "logcat ProcessBuilder failed: ${e.message}")
            null
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "logcat ProcessBuilder blocked: ${e.message}")
            null
        }
    }

    private fun tryLogcatPidOnly(): List<String>? {
        return try {
            val pid = Process.myPid().toString()
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid")
                .redirectErrorStream(true)
                .start()

            val exited = process.waitFor(5, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                return null
            }

            val lines = process.inputStream.bufferedReader().readLines()
            if (lines.isEmpty()) null
            else {
                usedFallback = false
                lines.reversed()
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "logcat --pid fallback failed: ${e.message}")
            null
        }
    }

    private fun useInProcessBuffer(): List<String> {
        usedFallback = true
        return InProcessLogBuffer.getAll()
    }

    fun clearLogcat() {
        try {
            val process = ProcessBuilder("logcat", "-c")
                .redirectErrorStream(true)
                .start()
            process.waitFor(3, TimeUnit.SECONDS)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "logcat clear failed: ${e.message}")
        }
        InProcessLogBuffer.clear()
        logsetsAll.clear()
        filteredLogs = emptyList()
    }

    fun filter(content: String?) {
        currentFilter = content?.trim() ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        filteredLogs = if (currentFilter.isEmpty()) {
            logsetsAll.toList()
        } else {
            logsetsAll.filter { it.contains(currentFilter, ignoreCase = true) }
        }
    }
}
