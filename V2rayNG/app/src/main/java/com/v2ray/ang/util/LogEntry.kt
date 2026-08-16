package com.v2ray.ang.util

/**
 * A parsed, structured representation of a single log line, regardless of whether it originated
 * from [InProcessLogBuffer] (format: `MM-dd HH:mm:ss.SSS L/tag(pid/thread): message`) or from a
 * live `logcat -v time` dump (format: `MM-dd HH:mm:ss.SSS L/tag(  pid): message`).
 *
 * Keeping a single parser shared by the ViewModel (for merging/sorting) and the adapter (for
 * rendering/coloring) avoids the two views of a log line ever disagreeing with each other.
 */
data class LogEntry(
    val timestamp: String,
    val level: Char,
    val tag: String,
    val meta: String,
    val message: String,
    val raw: String,
) {
    val priority: Int
        get() = LogPriority.fromLevelChar(level)

    companion object {
        // "MM-dd HH:mm:ss.SSS L/tag(meta): message" — meta may be a bare pid ("  1234") or
        // "pid/threadName" as written by InProcessLogBuffer.
        private val PATTERN = Regex(
            """^(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\(([^)]*)\):\s?(.*)$"""
        )

        fun parse(line: String): LogEntry {
            val match = PATTERN.matchEntire(line)
            return if (match != null) {
                val (ts, level, tag, meta, message) = match.destructured
                LogEntry(ts, level[0], tag.trim(), meta.trim(), message, line)
            } else {
                // Unparsed lines (e.g. multi-line stack traces continuing a previous entry, or
                // odd output from ProcessBuilder) are still shown, just without structure.
                LogEntry(timestamp = "", level = 'I', tag = "", meta = "", message = line, raw = line)
            }
        }

        fun parseAll(lines: List<String>): List<LogEntry> = lines.map(::parse)
    }
}
