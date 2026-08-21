package com.miku.ray.particlesdrawable.engine

import com.miku.ray.particlesdrawable.model.Scene
import androidx.annotation.VisibleForTesting

internal class FrameAdvancer(private val particleGenerator: ParticleGenerator) {

    fun advanceToNextFrame(scene: Scene, step: Float) {
        val particlesCount = scene.getDensity()
        for (i in 0 until particlesCount) {
            var x = scene.getParticleX(i)
            var y = scene.getParticleY(i)

            val speedFactor = scene.getParticleSpeedFactor(i)
            val dCos = scene.getParticleDirectionCos(i)
            val dSin = scene.getParticleDirectionSin(i)

            x += step * scene.getSpeedFactor() * speedFactor * dCos
            y += step * scene.getSpeedFactor() * speedFactor * dSin

            if (particleOutOfBounds(scene, x, y)) {
                particleGenerator.applyFreshParticleOffScreen(scene, i)
            } else {
                scene.setParticleX(i, x)
                scene.setParticleY(i, y)
            }
        }
    }

    @VisibleForTesting
    fun particleOutOfBounds(scene: Scene, x: Float, y: Float): Boolean {
        val offset = scene.getParticleRadiusMin() + scene.getLineLength()
        return x + offset < 0 || x - offset > scene.getWidth() ||
               y + offset < 0 || y - offset > scene.getHeight()
    }
}