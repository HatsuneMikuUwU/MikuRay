package com.miku.ray.particlesdrawable.engine

import android.graphics.drawable.Animatable
import com.miku.ray.particlesdrawable.KeepAsApi
import com.miku.ray.particlesdrawable.contract.SceneController
import com.miku.ray.particlesdrawable.contract.SceneRenderer
import com.miku.ray.particlesdrawable.contract.SceneScheduler
import com.miku.ray.particlesdrawable.model.Scene
import androidx.annotation.VisibleForTesting

@KeepAsApi
class Engine @VisibleForTesting internal constructor(
    private val frameAdvancer: FrameAdvancer,
    internal val particleGenerator: ParticleGenerator,
    internal val scene: Scene,
    private val scheduler: SceneScheduler,
    private val renderer: SceneRenderer,
    private val timeProvider: TimeProvider
) : Animatable, Runnable, SceneController {

    companion object {
        private const val STEP_PER_MS = 0.05f
    }

    constructor(scene: Scene, scheduler: SceneScheduler, renderer: SceneRenderer) :
            this(FrameAdvancer(ParticleGenerator()), ParticleGenerator(), scene, scheduler, renderer, TimeProvider())

    private var particlesInited: Boolean = false

    private var lastFrameTime: Long = 0L
    private var lastDrawDuration: Long = 0L

    @Volatile
    private var animating: Boolean = false

    private fun resetLastFrameTime() {
        lastFrameTime = 0L
    }

    private fun gotoNextFrameAndSchedule() {
        nextFrame()
        scheduler.scheduleNextFrame(kotlin.math.max(scene.getFrameDelay() - lastDrawDuration, 0L))
    }

    fun setAlpha(alpha: Int) {
        scene.setAlpha(alpha)
    }

    fun getAlpha(): Int = scene.getAlpha()

    override fun start() {
        if (!animating) {
            animating = true
            resetLastFrameTime()
            gotoNextFrameAndSchedule()
        }
    }

    override fun stop() {
        if (animating) {
            animating = false
            resetLastFrameTime()
            scheduler.unscheduleNextFrame()
        }
    }

    override fun isRunning(): Boolean = animating

    override fun run() {
        if (animating) {
            gotoNextFrameAndSchedule()
        } else {
            resetLastFrameTime()
        }
    }

    fun draw() {
        val startTime = timeProvider.uptimeMillis()
        renderer.drawScene(scene)
        lastDrawDuration = timeProvider.uptimeMillis() - startTime
    }

    override fun makeFreshFrame() {
        val scene = this.scene
        if (scene.getWidth() != 0 && scene.getHeight() != 0) {
            resetLastFrameTime()
            initParticles()
        }
    }

    override fun makeFreshFrameWithParticlesOffscreen() {
        val scene = this.scene
        if (scene.getWidth() != 0 && scene.getHeight() != 0) {
            resetLastFrameTime()
            initParticlesOffScreen()
        }
    }

    fun setDimensions(width: Int, height: Int) {
        val scene = this.scene
        scene.setWidth(width)
        scene.setHeight(height)
        if (width > 0 && height > 0) {
            if (!particlesInited) {
                particlesInited = true
                initParticles()
            }
        } else {
            if (particlesInited) {
                particlesInited = false
            }
        }
    }

    private fun initParticles() {
        initParticles(ParticleCreationStrategy { position ->
            if (position % 2 == 0) {
                particleGenerator.applyFreshParticleOnScreen(scene, position)
            } else {
                particleGenerator.applyFreshParticleOffScreen(scene, position)
            }
        })
    }

    private fun initParticlesOffScreen() {
        initParticles(ParticleCreationStrategy { position ->
            particleGenerator.applyFreshParticleOffScreen(scene, position)
        })
    }

    private fun initParticles(strategy: ParticleCreationStrategy) {
        val scene = this.scene
        if (scene.getWidth() == 0 || scene.getHeight() == 0) {
            throw IllegalStateException("Cannot init particles if width or height is 0")
        }
        for (i in 0 until scene.getDensity()) {
            strategy.addNewParticle(i)
        }
    }

    override fun nextFrame() {
        val step = if (lastFrameTime == 0L) {
            1f
        } else {
            (timeProvider.uptimeMillis() - lastFrameTime) * STEP_PER_MS
        }
        frameAdvancer.advanceToNextFrame(scene, step)
        lastFrameTime = timeProvider.uptimeMillis()
    }

    private fun interface ParticleCreationStrategy {
        fun addNewParticle(position: Int)
    }
}