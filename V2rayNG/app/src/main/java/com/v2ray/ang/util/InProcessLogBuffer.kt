package com.v2ray.ang.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object InProcessLogBuffer {
    private const val MAX_ENTRIES = 2000
    private val buffer: LinkedList<String> = LinkedList()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(priority: Int, tag: String, message: String) {
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
        val line = "${fmt.format(Date())} $level/$tag: $message"
        if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
        buffer.addLast(line)
    }

    @Synchronized
    fun getAll(): List<String> = buffer.toList().reversed()

    @Synchronized
    fun clear() = buffer.clear()
}
