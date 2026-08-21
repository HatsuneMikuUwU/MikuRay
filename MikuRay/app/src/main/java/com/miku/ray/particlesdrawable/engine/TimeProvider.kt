package com.miku.ray.particlesdrawable.engine

import android.os.SystemClock

internal class TimeProvider {
    fun uptimeMillis(): Long = SystemClock.uptimeMillis()
}