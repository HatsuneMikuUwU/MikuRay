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
    private var padding = 0
    /** NagramXF-compatible mode: false draws a circle, true draws a rounded rectangle. */
    var nonRound = true
    var radius = 0f
    private var topStrokeWidth = 1f
    private var bottomStrokeWidth = 1f

    fun setBackgroundColor(color: Int) {
        fillPaint.color = color
        invalidateSelf()
    }

    fun setCornerRadius(radius: Float) {
        this.radius = radius.coerceAtLeast(0f)
        cornerRadius = this.radius
        invalidateSelf()
    }

    fun setPadding(padding: Int) {
        this.padding = padding.coerceAtLeast(0)
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

        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        var drawRadius = min(bounds.width(), bounds.height()) / 2f - padding
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        if (nonRound) {
            left = bounds.left.toFloat()
            top = bounds.top.toFloat()
            right = bounds.right.toFloat()
            bottom = bounds.bottom.toFloat()
            drawRadius = min(radius.coerceAtLeast(cornerRadius), min(bounds.width(), bounds.height()) / 2f)
        } else {
            left = cx - drawRadius
            top = cy - drawRadius
            right = cx + drawRadius
            bottom = cy + drawRadius
        }
        if (Color.alpha(fillPaint.color) > 0) {
            canvas.drawRoundRect(RectF(left, top, right, bottom), drawRadius, drawRadius, fillPaint)
        }
        if (Color.alpha(topStrokePaint.color) > 0 && topStrokeWidth > 0f) {
            drawStroke(canvas, left, top, right, bottom, drawRadius, topStrokeWidth, true, topStrokePaint)
        }
        if (Color.alpha(bottomStrokePaint.color) > 0 && bottomStrokeWidth > 0f) {
            drawStroke(canvas, left, top, right, bottom, drawRadius, bottomStrokeWidth, false, bottomStrokePaint)
        }
    }

    /**
     * Draws one rounded edge as a clipped expanded rounded rectangle.
     * This mirrors NagramXF's BlurredBackgroundDrawable.drawStroke implementation
     * and avoids the uneven corner joins produced by a full-border approximation.
     */
    private fun drawStroke(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        strokeWidth: Float,
        isTop: Boolean,
        paint: Paint
    ) {
        val strokeHalf = strokeWidth / 2f
        canvas.save()
        if (isTop) {
            if (canvas.clipRect(
                    left - strokeHalf,
                    top,
                    right + strokeHalf,
                    min(top + radius * 2f, bottom)
                )) {
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
            if (canvas.clipRect(
                    left - strokeHalf,
                    max(bottom - radius * 2f, top),
                    right + strokeHalf,
                    bottom
                )) {
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
