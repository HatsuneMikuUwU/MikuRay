package com.miku.ray.particlesdrawable.contract

import com.miku.ray.particlesdrawable.KeepAsApi
import com.miku.ray.particlesdrawable.model.Scene
import androidx.annotation.NonNull

@KeepAsApi
interface SceneRenderer {
    fun drawScene(@NonNull scene: Scene)
}