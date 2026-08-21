package com.miku.ray.ui.logcat

import android.os.Process
import androidx.lifecycle.ViewModel
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.ANG_PACKAGE
import com.miku.ray.util.InProcessLogBuffer
import com.miku.ray.util.LogEntry
import com.miku.ray.util.LogUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

class LogcatViewModel : ViewModel() {
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var filteredLogs: List<String> = emptyList()
    private var currentFilter: String = ""

    var usedFallback: Boolean = false
        private set

    fun getAll(): List<String> = filteredLogs

    private val ownTags = setOf(ANG_PACKAGE, LogUtil.TAG_CORE)

    fun loadLogcat() {
        val bufferLines = InProcessLogBuffer.getAll()

        val systemLines = (tryLogcatProcessBuilder() ?: tryLogcatPidOnly())
            ?.filter { line ->
                val tag = LogEntry.parse(line).tag
                tag.isEmpty() || tag !in ownTags
            }
            .orEmpty()

        usedFallback = systemLines.isEmpty() && bufferLines.isNotEmpty()

        logsetsAll.clear()
        logsetsAll.addAll(mergeByTimestamp(bufferLines, systemLines))
        applyFilter()
    }

    private fun mergeByTimestamp(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return (a + b).sortedByDescending { LogEntry.parse(it).timestamp }
    }

    private fun tryLogcatProcessBuilder(): List<String>? {
        return try {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "time",
                "-s", "GoLog,${LogUtil.TAG_CORE},$ANG_PACKAGE,AndroidRuntime,System.err,VpnService"
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
            else lines.reversed()
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
            else lines.reversed()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "logcat --pid fallback failed: ${e.message}")
            null
        }
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
