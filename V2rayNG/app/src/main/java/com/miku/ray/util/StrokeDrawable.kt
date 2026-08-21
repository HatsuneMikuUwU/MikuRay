package com.miku.ray.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

class StrokeDrawable : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val topStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bottomStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var topColor = Color.TRANSPARENT
    private var bottomColor = Color.TRANSPARENT
    private var drawableAlpha = 255
    private var cornerRadius = 0f
    private var topStrokeWidth = 1f
    private var bottomStrokeWidth = 1f

    fun setBackgroundColor(color: Int) {
        fillPaint.color = color
        invalidateSelf()
    }

    fun setCornerRadius(radius: Float) {
        cornerRadius = radius.coerceAtLeast(0f)
        invalidateSelf()
    }

    fun setStrokeColorTop(color: Int) {
        topColor = color
        updatePaintColors()
    }

    fun setStrokeColorBottom(color: Int) {
        bottomColor = color
        updatePaintColors()
    }

    fun setStrokeWidthTop(width: Float) {
        topStrokeWidth = width.coerceAtLeast(0f)
        topStrokePaint.strokeWidth = topStrokeWidth
        invalidateSelf()
    }

    fun setStrokeWidthBottom(width: Float) {
        bottomStrokeWidth = width.coerceAtLeast(0f)
        bottomStrokePaint.strokeWidth = bottomStrokeWidth
        invalidateSelf()
    }

    private fun updatePaintColors() {
        topStrokePaint.color = withAlpha(topColor, drawableAlpha)
        bottomStrokePaint.color = withAlpha(bottomColor, drawableAlpha)
        invalidateSelf()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        (Color.alpha(color) * alpha / 255f).toInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val radius = min(cornerRadius, min(bounds.width(), bounds.height()) / 2f)
        val strokeHalfTop = topStrokeWidth / 2f
        val strokeHalfBottom = bottomStrokeWidth / 2f

        if (Color.alpha(fillPaint.color) > 0) {
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, fillPaint)
        }
        if (Color.alpha(topStrokePaint.color) > 0 && topStrokeWidth > 0f) {
            drawEdgeStroke(canvas, left, top, right, bottom, radius, strokeHalfTop, true, topStrokePaint)
        }
        if (Color.alpha(bottomStrokePaint.color) > 0 && bottomStrokeWidth > 0f) {
            drawEdgeStroke(canvas, left, top, right, bottom, radius, strokeHalfBottom, false, bottomStrokePaint)
        }
    }

    private fun drawEdgeStroke(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        strokeHalf: Float,
        isTop: Boolean,
        paint: Paint
    ) {
        canvas.save()
        if (isTop) {
            val clipBottom = min(top + radius * 2f, bottom)
            if (canvas.clipRect(left - strokeHalf, top, right + strokeHalf, clipBottom)) {
                canvas.drawRoundRect(
                    left - strokeHalf,
                    top + strokeHalf,
                    right + strokeHalf,
                    bottom + strokeHalf,
                    radius,
                    radius,
                    paint
                )
            }
        } else {
            val clipTop = max(bottom - radius * 2f, top)
            if (canvas.clipRect(left - strokeHalf, clipTop, right + strokeHalf, bottom)) {
                canvas.drawRoundRect(
                    left - strokeHalf,
                    top - strokeHalf,
                    right + strokeHalf,
                    bottom - strokeHalf,
                    radius,
                    radius,
                    paint
                )
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        updatePaintColors()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        topStrokePaint.colorFilter = colorFilter
        bottomStrokePaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }
     
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
