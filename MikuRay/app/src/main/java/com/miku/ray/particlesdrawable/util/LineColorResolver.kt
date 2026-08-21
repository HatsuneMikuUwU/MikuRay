package com.miku.ray.particlesdrawable.util

import com.miku.ray.particlesdrawable.KeepAsApi
import androidx.annotation.ColorInt
import androidx.annotation.IntRange

@KeepAsApi
object LineColorResolver {
    private const val OPAQUE = 255

    private fun resolveLineAlpha(
        @IntRange(from = 0, to = 255) sceneAlpha: Int,
        maxDistance: Float,
        distance: Float
    ): @IntRange(from = 0, to = 255) Int {
        val alphaPercent = 1f - distance / maxDistance
        val alpha = (OPAQUE * alphaPercent).toInt()
        return alpha * sceneAlpha / OPAQUE
    }

    fun resolveLineColorWithAlpha(
        @IntRange(from = 0, to = 255) sceneAlpha: Int,
         lineColor: Int,
        maxDistance: Float,
        distance: Float
    ):  Int {
        val alpha = resolveLineAlpha(sceneAlpha, maxDistance, distance)
        return (lineColor and 0x00FFFFFF) or (alpha shl 24)
    }
}