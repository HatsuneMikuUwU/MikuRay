package com.miku.ray.particlesdrawable.contract

import androidx.annotation.ColorInt

interface LowLevelRenderer {
    fun drawLine(
        startX: Float,
        startY: Float,
        stopX: Float,
        stopY: Float,
        strokeWidth: Float,
        @ColorInt color: Int
    )

    fun fillCircle(
        cx: Float,
        cy: Float,
        radius: Float,
        @ColorInt color: Int
    )
}