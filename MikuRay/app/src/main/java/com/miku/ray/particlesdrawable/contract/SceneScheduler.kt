package com.miku.ray.particlesdrawable.contract

import androidx.annotation.Keep

@Keep
interface SceneScheduler {
    fun scheduleNextFrame(delay: Long)
    fun unscheduleNextFrame()
    fun requestRender()
}