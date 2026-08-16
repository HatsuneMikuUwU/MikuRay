package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import timber.log.Timber
import java.util.Locale

/**
 * MikuRay's single [Timber.Tree] implementation. This is the only place in the app that touches
 * a "real" Android logging primitive at all (via [Timber.DebugTree]'s own internals) — everywhere
 * else in the codebase logs through [LogUtil], which just calls [Timber].
 *
 * Two responsibilities are combined here so the two destinations (in-app buffer, system logcat)
 * can never drift out of sync:
 *  - Every message, at every priority, is captured into [InProcessLogBuffer] with full detail.
 *  - Only messages meeting the user-configured [AppConfig.PREF_LOGLEVEL] threshold are also
 *    written out to the system logcat (via the DebugTree superclass), so device logcat isn't
 *    spammed unless the user actually wants that level of verbosity.
 */
class MikuRayLogTree : Timber.DebugTree() {

    // isLoggable is normally where a Tree would gate output; we deliberately let everything
    // through here so log() below always runs and the buffer always gets full detail.
    override fun isLoggable(tag: String?, priority: Int): Boolean = true

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val effectiveTag = tag ?: AppConfig.TAG
        val fullMessage = if (t != null) "$message\n${t.stackTraceToString()}" else message

        InProcessLogBuffer.append(priority, effectiveTag, fullMessage)

        if (priority >= minPriority()) {
            super.log(priority, effectiveTag, message, t)
        }
    }

    companion object {
        private const val DEFAULT_LEVEL = "warning"
        private const val CACHE_UNSET = Int.MIN_VALUE

        @Volatile
        private var cachedMinPriority: Int = CACHE_UNSET

        private fun parsePriority(level: String?): Int {
            return when ((level ?: DEFAULT_LEVEL).lowercase(Locale.US)) {
                "verbose" -> LogPriority.VERBOSE
                "debug" -> LogPriority.DEBUG
                "info" -> LogPriority.INFO
                "warn", "warning" -> LogPriority.WARN
                "error" -> LogPriority.ERROR
                "none", "off" -> Int.MAX_VALUE
                else -> LogPriority.WARN
            }
        }

        /** Call after the user changes the log-level preference so it takes effect immediately. */
        fun refreshLogLevel() {
            cachedMinPriority = parsePriority(MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL, DEFAULT_LEVEL))
        }

        private fun minPriority(): Int {
            val cached = cachedMinPriority
            if (cached != CACHE_UNSET) return cached

            return synchronized(this) {
                val current = cachedMinPriority
                if (current != CACHE_UNSET) {
                    current
                } else {
                    parsePriority(MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL, DEFAULT_LEVEL)).also {
                        cachedMinPriority = it
                    }
                }
            }
        }
    }
}
