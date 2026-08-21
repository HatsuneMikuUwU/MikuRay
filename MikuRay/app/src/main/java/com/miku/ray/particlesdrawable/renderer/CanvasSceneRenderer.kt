package com.miku.ray.particlesdrawable.renderer

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import com.miku.ray.particlesdrawable.KeepAsApi
import com.miku.ray.particlesdrawable.contract.LowLevelRenderer
import androidx.annotation.ColorInt
import androidx.annotation.NonNull
import androidx.annotation.Nullable

@KeepAsApi
class CanvasSceneRenderer : LowLevelRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Nullable
    private var canvas: Canvas? = null

    fun setCanvas(@Nullable canvas: Canvas?) {
        this.canvas = canvas
    }

    @NonNull
    fun getPaint(): Paint = paint

    fun setColorFilter(@Nullable colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun drawLine(
        startX: Float,
        startY: Float,
        stopX: Float,
        stopY: Float,
        strokeWidth: Float,
        @ColorInt color: Int
    ) {
        val c = canvas ?: throw IllegalStateException("Called in wrong state")
        paint.strokeWidth = strokeWidth
        paint.color = color
        c.drawLine(startX, startY, stopX, stopY, paint)
    }

    override fun fillCircle(
        cx: Float,
        cy: Float,
        radius: Float,
        @ColorInt color: Int
    ) {
        val c = canvas ?: throw IllegalStateException("Called in wrong state")
        paint.color = color
        c.drawCircle(cx, cy, radius, paint)
    }
}