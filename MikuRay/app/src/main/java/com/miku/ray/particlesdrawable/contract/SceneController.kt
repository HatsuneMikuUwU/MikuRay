package com.miku.ray.particlesdrawable.contract

import androidx.annotation.Keep

@Keep
interface SceneController {
    fun nextFrame()
    fun makeFreshFrame()
    fun makeFreshFrameWithParticlesOffscreen()
}