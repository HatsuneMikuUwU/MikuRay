package com.miku.ray.particlesdrawable

import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.Keep
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.miku.ray.particlesdrawable.contract.SceneConfiguration
import com.miku.ray.particlesdrawable.contract.SceneController
import com.miku.ray.particlesdrawable.contract.SceneRenderer
import com.miku.ray.particlesdrawable.contract.SceneScheduler
import com.miku.ray.particlesdrawable.engine.Engine
import com.miku.ray.particlesdrawable.engine.SceneConfigurator
import com.miku.ray.particlesdrawable.model.Scene
import com.miku.ray.particlesdrawable.renderer.CanvasSceneRenderer
import com.miku.ray.particlesdrawable.renderer.DefaultSceneRenderer
import com.miku.ray.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

@Keep
class ParticlesDrawable : Drawable(), Animatable, SceneConfiguration, SceneController, SceneScheduler {

    private val canvasRenderer: CanvasSceneRenderer = CanvasSceneRenderer()

    private val scene: Scene = Scene()

    private val sceneConfigurator: SceneConfigurator = SceneConfigurator()

    private val renderer: SceneRenderer = DefaultSceneRenderer(canvasRenderer)

    private val engine: Engine = Engine(scene, this, renderer)

    @Throws(XmlPullParserException::class, IOException::class)
    override fun inflate(
        @NonNull r: Resources,
        @NonNull parser: XmlPullParser,
        @NonNull attrs: AttributeSet,
        @Nullable theme: Resources.Theme?
    ) {
        super.inflate(r, parser, attrs, theme)

        val a: TypedArray = if (theme != null) {
            theme.obtainStyledAttributes(attrs, R.styleable.ParticlesView, 0, 0)
        } else {
            r.obtainAttributes(attrs, R.styleable.ParticlesView)
        }

        try {
            val count = a.indexCount
            var particleRadiusMax = Defaults.PARTICLE_RADIUS_MAX
            var particleRadiusMin = Defaults.PARTICLE_RADIUS_MIN

            for (i in 0 until count) {
                val attr = a.getIndex(i)
                when (attr) {
                    R.styleable.ParticlesView_density -> scene.setDensity(a.getInteger(attr, Defaults.DENSITY))
                    R.styleable.ParticlesView_frameDelayMillis -> scene.setFrameDelay(a.getInteger(attr, Defaults.FRAME_DELAY))
                    R.styleable.ParticlesView_lineColor -> scene.setLineColor(a.getColor(attr, Defaults.LINE_COLOR))
                    R.styleable.ParticlesView_lineLength -> scene.setLineLength(a.getDimension(attr, Defaults.LINE_LENGTH))
                    R.styleable.ParticlesView_lineThickness -> scene.setLineThickness(a.getDimension(attr, Defaults.LINE_THICKNESS))
                    R.styleable.ParticlesView_particleColor -> scene.setParticleColor(a.getColor(attr, Defaults.PARTICLE_COLOR))
                    R.styleable.ParticlesView_particleRadiusMax -> particleRadiusMax = a.getDimension(attr, Defaults.PARTICLE_RADIUS_MAX)
                    R.styleable.ParticlesView_particleRadiusMin -> particleRadiusMin = a.getDimension(attr, Defaults.PARTICLE_RADIUS_MIN)
                    R.styleable.ParticlesView_speedFactor -> scene.setSpeedFactor(a.getFloat(attr, Defaults.SPEED_FACTOR))
                }
            }
            scene.setParticleRadiusRange(particleRadiusMin, particleRadiusMax)
        } finally {
            a.recycle()
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        engine.setDimensions(right - left, bottom - top)
    }

    override fun draw(@NonNull canvas: Canvas) {
        canvasRenderer.setCanvas(canvas)
        engine.draw()
        canvasRenderer.setCanvas(null)
        engine.run()
    }

    override fun scheduleNextFrame(delay: Long) {
        if (delay == 0L) {
            requestRender()
        } else {
            scheduleSelf(invalidateSelfRunnable, SystemClock.uptimeMillis() + delay)
        }
    }

    override fun unscheduleNextFrame() {
        unscheduleSelf(invalidateSelfRunnable)
    }

    override fun requestRender() {
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        engine.setAlpha(alpha)
    }

    override fun getAlpha(): Int = engine.getAlpha()

    override fun setColorFilter(@Nullable colorFilter: ColorFilter?) {
        canvasRenderer.setColorFilter(colorFilter)
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun start() {
        engine.start()
    }

    override fun stop() {
        engine.stop()
    }

    override fun isRunning(): Boolean = engine.isRunning()

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

    override fun getFrameDelay(): Int = scene.getFrameDelay()

    override fun setSpeedFactor(@FloatRange(from = 0.toDouble()) speedFactor: Float) {
        scene.setSpeedFactor(speedFactor)
    }

    override fun getSpeedFactor(): Float = scene.getSpeedFactor()

    override fun setParticleRadiusRange(
        @FloatRange(from = 0.5) minRadius: Float,
        @FloatRange(from = 0.5) maxRadius: Float
    ) {
        scene.setParticleRadiusRange(minRadius, maxRadius)
    }

    override fun getParticleRadiusMin(): Float = scene.getParticleRadiusMin()

    override fun getParticleRadiusMax(): Float = scene.getParticleRadiusMax()

    override fun setLineThickness(@FloatRange(from = 1.toDouble()) lineThickness: Float) {
        scene.setLineThickness(lineThickness)
    }

    override fun getLineThickness(): Float = scene.getLineThickness()

    override fun setLineLength(@FloatRange(from = 0.toDouble()) lineLength: Float) {
        scene.setLineLength(lineLength)
    }

    override fun getLineLength(): Float = scene.getLineLength()

    override fun setDensity(@IntRange(from = 0) newNum: Int) {
        scene.setDensity(newNum)
    }

    override fun getDensity(): Int = scene.getDensity()

    override fun setParticleColor(@ColorInt color: Int) {
        scene.setParticleColor(color)
    }

    override fun getParticleColor(): Int = scene.getParticleColor()

    override fun setLineColor(@ColorInt lineColor: Int) {
        scene.setLineColor(lineColor)
    }

    override fun getLineColor(): Int = scene.getLineColor()

    private val invalidateSelfRunnable: Runnable = Runnable { invalidateSelf() }
}