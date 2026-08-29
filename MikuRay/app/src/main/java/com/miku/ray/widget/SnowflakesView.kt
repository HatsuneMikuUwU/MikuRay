package com.miku.ray.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.miku.ray.util.getColorAttr
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class SnowflakesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Flake(
        var x: Float = 0f,
        var y: Float = 0f,
        var fallSpeed: Float = 0f,
        var drift: Float = 0f,
        var size: Float = 0f,
        var alpha: Float = 0f,
        var phase: Float = 0f,
        var life: Float = 0f,
        var age: Float = 0f
    )

    private val random = Random.Default
    private val flakes = ArrayList<Flake>(MAX_FLAKES_LIMIT)
    private val freeFlakes = ArrayList<Flake>(MAX_FLAKES_LIMIT)
    private var maxFlakes = DEFAULT_MAX_FLAKES
    private var speedMultiplier = 1f
    private var sizeMultiplier = 1f
    private var opacityMultiplier = 0.60f
    private var windMultiplier = 1f
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var lastFrameTime = 0L
    private var running = false
    private var lastColor = Color.WHITE

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
        repeat(MAX_FLAKES_LIMIT) { freeFlakes += Flake() }
        refreshColor()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshColor()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) {
            if (visibility == VISIBLE) startAnimation() else stopAnimation()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0 && flakes.isEmpty()) {
            repeat(INITIAL_FLAKES.coerceAtMost(maxFlakes)) { spawnFlake(initial = true) }
        }
    }

    fun configure(
        speed: Float,
        count: Int,
        size: Float = 1f,
        opacity: Float = 0.60f,
        wind: Float = 1f
    ) {
        speedMultiplier = speed.coerceIn(0.25f, 3f)
        maxFlakes = count.coerceIn(10, MAX_FLAKES_LIMIT)
        sizeMultiplier = size.coerceIn(0.5f, 2f)
        opacityMultiplier = opacity.coerceIn(0.1f, 1f)
        windMultiplier = wind.coerceIn(0f, 3f)
        while (flakes.size > maxFlakes) {
            recycle(flakes.removeAt(flakes.lastIndex))
        }
        if (width > 0 && height > 0) {
            while (flakes.size < min(INITIAL_FLAKES, maxFlakes)) spawnFlake(initial = true)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameTime == 0L) 16L else (now - lastFrameTime).coerceIn(1L, FRAME_TIME_LIMIT_MS)
        lastFrameTime = now

        updateFlakes(dt.toFloat())
        flakes.forEach { drawFlake(canvas, it) }
        if (running) postInvalidateOnAnimation()
    }

    private fun startAnimation() {
        if (running) return
        running = true
        lastFrameTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun stopAnimation() {
        running = false
        lastFrameTime = 0L
    }

    private fun refreshColor() {
        lastColor = try {
            context.getColorAttr("colorOnSurface")
        } catch (_: Exception) {
            Color.WHITE
        }
        flakePaint.color = lastColor
    }

    private fun updateFlakes(dtMs: Float) {
        val dt = dtMs / 16f
        val density = resources.displayMetrics.density
        val bottom = height.toFloat() + 12f * density
        val iterator = flakes.iterator()
        while (iterator.hasNext()) {
            val flake = iterator.next()
            flake.age += dtMs
            flake.x += flake.drift * dt * windMultiplier
            flake.y += flake.fallSpeed * dt * speedMultiplier
            flake.phase += 0.025f * dt
            val fadeOutStart = bottom - FADE_OUT_DISTANCE_DP * density
            flake.alpha = when {
                flake.age < FADE_IN_MS -> flake.age / FADE_IN_MS
                flake.y > fadeOutStart ->
                    ((bottom - flake.y) / (bottom - fadeOutStart)).coerceIn(0f, 1f)
                else -> 1f
            }
            if (flake.y > bottom) {
                iterator.remove()
                recycle(flake)
            }
        }

        while (flakes.size < maxFlakes && random.nextFloat() > 0.72f) {
            spawnFlake(initial = false)
        }
    }

    private fun spawnFlake(initial: Boolean) {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val flake = if (freeFlakes.isNotEmpty()) freeFlakes.removeAt(freeFlakes.lastIndex) else Flake()
        flake.x = random.nextFloat() * width
        flake.y = if (initial) random.nextFloat() * height else -8f * density
        flake.fallSpeed = (0.7f + random.nextFloat() * 1.1f) * density
        flake.drift = (-0.25f + random.nextFloat() * 0.5f) * density
        flake.size = (1.4f + random.nextFloat() * 3.2f) * density * sizeMultiplier
        flake.phase = random.nextFloat() * 6.28f
        flake.age = if (initial) random.nextFloat() * 1400f else 0f
        flake.life = 2600f + random.nextFloat() * 2200f
        flake.alpha = 0f
        flakes += flake
    }

    private fun recycle(flake: Flake) {
        if (freeFlakes.size < MAX_FLAKES_LIMIT) freeFlakes += flake
    }

    private fun drawFlake(canvas: Canvas, flake: Flake) {
        flakePaint.alpha = (flake.alpha * 255f * opacityMultiplier).toInt().coerceIn(0, 255)
        flakePaint.strokeWidth = (flake.size * 0.22f).coerceAtLeast(0.7f)
        val sway = sin(flake.phase) * flake.size * 0.35f
        canvas.save()
        canvas.translate(flake.x + sway, flake.y)
        for (arm in 0 until 6) {
            val angle = arm * Math.PI.toFloat() / 3f
            val endX = cos(angle) * flake.size
            val endY = sin(angle) * flake.size
            canvas.drawLine(0f, 0f, endX, endY, flakePaint)
            val branchX = endX * 0.58f
            val branchY = endY * 0.58f
            val branchLength = flake.size * 0.28f
            val branchAngle = angle + Math.PI.toFloat() / 3f
            canvas.drawLine(branchX, branchY, branchX + cos(branchAngle) * branchLength, branchY + sin(branchAngle) * branchLength, flakePaint)
            val otherAngle = angle - Math.PI.toFloat() / 3f
            canvas.drawLine(branchX, branchY, branchX + cos(otherAngle) * branchLength, branchY + sin(otherAngle) * branchLength, flakePaint)
        }
        canvas.restore()
    }

    companion object {
        private const val MAX_FLAKES_LIMIT = 120
        private const val DEFAULT_MAX_FLAKES = 55
        private const val INITIAL_FLAKES = 28
        private const val FADE_IN_MS = 260f
        private const val FADE_OUT_DISTANCE_DP = 72f
        private const val FRAME_TIME_LIMIT_MS = 32L
    }
}
