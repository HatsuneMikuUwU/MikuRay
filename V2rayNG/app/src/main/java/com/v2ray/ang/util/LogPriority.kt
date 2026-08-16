package com.v2ray.ang.util

/**
 * Log priority constants used throughout MikuRay's log pipeline (LogUtil, InProcessLogBuffer,
 * LogEntry, MikuRayLogTree). Values intentionally match Timber/android.util.Log's own priority
 * scheme (VERBOSE=2 .. ASSERT=7) so they interoperate directly with [Timber], without any of
 * our own code needing to import `android.util.Log` itself.
 */
object LogPriority {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    fun levelChar(priority: Int): Char = when (priority) {
        VERBOSE -> 'V'
        DEBUG -> 'D'
        INFO -> 'I'
        WARN -> 'W'
        ERROR -> 'E'
        ASSERT -> 'F'
        else -> '?'
    }

    fun fromLevelChar(level: Char): Int = when (level) {
        'V' -> VERBOSE
        'D' -> DEBUG
        'I' -> INFO
        'W' -> WARN
        'E' -> ERROR
        'F' -> ASSERT
        else -> INFO
    }
}
