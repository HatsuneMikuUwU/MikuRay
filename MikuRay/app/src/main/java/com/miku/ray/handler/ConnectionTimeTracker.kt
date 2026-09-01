package com.miku.ray.handler

/**
 * Tracks when the current proxy/VPN connection started.
 *
 * This mirrors the structure of NotificationManager's own connectStartTime
 * (a plain timestamp field on a singleton object) but is fully independent
 * of it, so the FAB timer no longer depends on the notification system.
 *
 * As a singleton object it lives for as long as this process is alive, i.e.
 * as long as the foreground service is running - not tied to any Activity
 * or ViewModel. That means it survives the Activity/ViewModel being
 * destroyed and recreated (app backgrounded or fully closed and reopened),
 * so callers can always recover the true elapsed connection time instead of
 * it resetting to zero.
 */
object ConnectionTimeTracker {
    @Volatile
    private var connectStartTime = 0L

    fun start() {
        connectStartTime = System.currentTimeMillis()
    }

    fun stop() {
        connectStartTime = 0L
    }

    fun getConnectStartTime() = connectStartTime
}
