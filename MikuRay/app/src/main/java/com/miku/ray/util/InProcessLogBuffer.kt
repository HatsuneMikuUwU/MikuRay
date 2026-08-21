package com.miku.ray.util

import android.os.Process
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object InProcessLogBuffer {
    private const val MAX_ENTRIES = 5000
    private val buffer: LinkedList<String> = LinkedList()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(priority: Int, tag: String, message: String) {
        val level = LogPriority.levelChar(priority)
        val threadName = Thread.currentThread().name
        val line = "${fmt.format(Date())} $level/$tag(${Process.myPid()}/$threadName): $message"
        if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
        buffer.addLast(line)
    }

    @Synchronized
    fun getAll(): List<String> = buffer.toList().reversed()

    @Synchronized
    fun clear() = buffer.clear()

    @Synchronized
    fun trim(keep: Int = 200) {
        while (buffer.size > keep) {
            buffer.removeFirst()
        }
    }
}
