package com.miku.ray.particlesdrawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.Keep
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import com.miku.ray.particlesdrawable.contract.SceneConfiguration
import com.miku.ray.particlesdrawable.contract.SceneController
import com.miku.ray.particlesdrawable.contract.SceneRenderer
import com.miku.ray.particlesdrawable.contract.SceneScheduler
import com.miku.ray.particlesdrawable.engine.Engine
import com.miku.ray.particlesdrawable.engine.SceneConfigurator
import com.miku.ray.particlesdrawable.model.Scene
import com.miku.ray.particlesdrawable.renderer.CanvasSceneRenderer
import com.miku.ray.particlesdrawable.renderer.DefaultSceneRenderer

@Keep
class ParticlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes),
    Animatable,
    SceneConfiguration,
    SceneController,
    SceneScheduler {

    private val canvasSceneRenderer = CanvasSceneRenderer()
    private val scene = Scene()
    private val sceneConfigurator = SceneConfigurator()
    private val renderer: SceneRenderer = DefaultSceneRenderer(canvasSceneRenderer)
    private val engine: Engine

    private var mExplicitlyStopped: Boolean = false

    private var mAttachedToWindow: Boolean = false
    private var mEmulateOnAttachToWindow: Boolean = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, canvasSceneRenderer.getPaint())
        if (attrs != null) {
            sceneConfigurator.configureSceneFromAttributes(scene, context, attrs)
        }
        engine = Engine(scene, this, renderer)
    }

    override fun nextFrame() {
        engine.nextFrame()
    }

    override fun makeFreshFrame() {
        engine.makeFreshFrame()
    }

    override fun makeFreshFrameWithParticlesOffscreen() {
        engine.makeFreshFrameWithParticlesOffscreen()
    }

    override fun setFrameDelay(@IntRange(from = 0) delay: Int) {
        scene.setFrameDelay(delay)
    }

    override fun getFrameDelay(): Int {
        return scene.frameDelay
    }

    override fun setSpeedFactor(@FloatRange(from = 0.0) speedFactor: Float) {
        scene.setSpeedFactor(speedFactor)
    }

    override fun getSpeedFactor(): Float {
        return scene.speedFactor
    }

    fun setParticleRadiusRange(
        @FloatRange(from = 0.5) minRadius: Float,
        @FloatRange(from = 0.5) maxRadius: Float
    ) {
        scene.setParticleRadiusRange(minRadius, maxRadius)
    }

    override fun getParticleRadiusMin(): Float {
        return scene.particleRadiusMin
    }

    override fun getParticleRadiusMax(): Float {
        return scene.particleRadiusMax
    }

    fun setLineThickness(@FloatRange(from = 1.0) lineThickness: Float) {
        scene.setLineThickness(lineThickness)
    }

    override fun getLineThickness(): Float {
        return scene.lineThickness
    }

    fun setLineLength(@FloatRange(from = 0.0) lineLength: Float) {
        scene.setLineLength(lineLength)
    }

    override fun getLineLength(): Float {
        return scene.lineLength
    }

    fun setDensity(@IntRange(from = 0) newNum: Int) {
        scene.setDensity(newNum)
    }

    override fun getDensity(): Int {
        return scene.density
    }

    fun setParticleColor(@ColorInt color: Int) {
        scene.setParticleColor(color)
    }

    override fun getParticleColor(): Int {
        return scene.particleColor
    }

    fun setLineColor(@ColorInt lineColor: Int) {
        scene.setLineColor(lineColor)
    }

    override fun getLineColor(): Int {
        return scene.lineColor
    }

    override fun requestRender() {
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        engine.setDimensions(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasSceneRenderer.setCanvas(canvas)
        engine.draw()
        canvasSceneRenderer.setCanvas(null)
        engine.run()
    }

    override fun scheduleNextFrame(delay: Long) {
        if (delay == 0L) {
            requestRender()
        } else {
            postInvalidateDelayed(delay)
        }
    }

    override fun unscheduleNextFrame() {
        // no-op
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) {
            stopInternal()
        } else {
            startInternal()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mAttachedToWindow = true
        startInternal()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mAttachedToWindow = false
        stopInternal()
    }

    override fun start() {
        mExplicitlyStopped = false
        startInternal()
    }

    override fun stop() {
        mExplicitlyStopped = true
        stopInternal()
    }

    override fun isRunning(): Boolean {
        return engine.isRunning()
    }

    private fun startInternal() {
        if (!mExplicitlyStopped && isVisibleWithAllParents(this) && isAttachedToWindowCompat()) {
            engine.start()
        }
    }

    private fun stopInternal() {
        engine.stop()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun setEmulateOnAttachToWindow(emulateOnAttachToWindow: Boolean) {
        mEmulateOnAttachToWindow = emulateOnAttachToWindow
    }

    private fun isAttachedToWindowCompat(): Boolean {
        return if (mEmulateOnAttachToWindow) {
            mAttachedToWindow
        } else {
            isAttachedToWindow
        }
    }

    private fun isVisibleWithAllParents(view: View): Boolean {
        if (view.visibility != VISIBLE) {
            return false
        }
        val parent: ViewParent? = view.parent
        return if (parent is View) {
            isVisibleWithAllParents(parent)
        } else {
            true
        }
    }
}