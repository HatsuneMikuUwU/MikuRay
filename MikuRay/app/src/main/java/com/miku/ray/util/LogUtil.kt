package com.miku.ray.util

import com.miku.ray.AppConfig
import timber.log.Timber

/**
 * MikuRay's app-wide logging facade. Deliberately does not use `android.util.Log` anywhere in
 * this file (or in [InProcessLogBuffer] / [LogEntry]); all calls go through [Timber], and
 * [MikuRayLogTree] is the single place that decides what reaches the in-app buffer vs. the
 * system logcat.
 */
object LogUtil {

    /** Tag used for messages forwarded from the native Xray-core (Go) engine. */
    const val TAG_CORE = "XrayCore"

    /** Call after the user changes the log-level preference so it takes effect immediately. */
    @Suppress("unused")
    fun refreshLogLevel() = MikuRayLogTree.refreshLogLevel()

    fun v(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).v(message)
    fun d(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).d(message)
    fun i(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).i(message)
    fun w(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).w(message)
    fun e(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).e(message)

    fun d(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).d(throwable, message)
    fun i(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).i(throwable, message)
    fun w(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).w(throwable, message)
    fun e(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).e(throwable, message)

    /**
     * Forwards a status/log line emitted by the native Xray-core engine (via
     * `CoreCallbackHandler.onEmitStatus`) into the log pipeline, tagged as [TAG_CORE].
     *
     * The native SDK does not document a stable meaning for [levelHint], so it is used only as
     * a best-effort hint for severity coloring; the message itself is always preserved in full.
     */
    fun core(levelHint: Long, message: String?) {
        if (message.isNullOrEmpty()) return
        when {
            levelHint >= 3L -> e(TAG_CORE, message)
            levelHint == 2L -> w(TAG_CORE, message)
            levelHint == 0L -> d(TAG_CORE, message)
            else -> i(TAG_CORE, message)
        }
    }
}
