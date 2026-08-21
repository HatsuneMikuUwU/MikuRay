package com.miku.ray.particlesdrawable.engine

import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.VisibleForTesting
import com.miku.ray.particlesdrawable.contract.SceneConfiguration
import com.miku.ray.particlesdrawable.model.Scene
import java.util.Random

internal class ParticleGenerator @VisibleForTesting constructor(private val random: Random) {

    constructor() : this(Random())

    private val pcc: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 18f, Resources.getSystem().displayMetrics
    )

    fun applyFreshParticleOnScreen(scene: Scene, position: Int) {
        val w = scene.getWidth()
        val h = scene.getHeight()
        if (w == 0 || h == 0) {
            throw IllegalStateException("Cannot generate particles if scene width or height is 0")
        }

        val direction = Math.toRadians(random.nextInt(360).toDouble())
        val dCos = Math.cos(direction).toFloat()
        val dSin = Math.sin(direction).toFloat()
        val x = random.nextInt(w).toFloat()
        val y = random.nextInt(h).toFloat()
        val speedFactor = newRandomIndividualParticleSpeedFactor()
        val radius = newRandomIndividualParticleRadius(scene)

        scene.setParticleData(
            position,
            x,
            y,
            dCos,
            dSin,
            radius,
            speedFactor
        )
    }

    fun applyFreshParticleOffScreen(scene: Scene, position: Int) {
        val w = scene.getWidth()
        val h = scene.getHeight()
        if (w == 0 || h == 0) {
            throw IllegalStateException("Cannot generate particles if scene width or height is 0")
        }

        var x = random.nextInt(w).toFloat()
        var y = random.nextInt(h).toFloat()

        val offset = (scene.getParticleRadiusMin() + scene.getLineLength()).toInt().toShort()

        val startAngle: Float
        var endAngle: Float

        when (random.nextInt(4)) {
            0 -> {
                x = (-offset).toFloat()
                startAngle = angleDeg(pcc, pcc, x, y)
                endAngle = angleDeg(pcc, h - pcc, x, y)
            }
            1 -> {
                y = (-offset).toFloat()
                startAngle = angleDeg(w - pcc, pcc, x, y)
                endAngle = angleDeg(pcc, pcc, x, y)
            }
            2 -> {
                x = (w + offset).toFloat()
                startAngle = angleDeg(w - pcc, h - pcc, x, y)
                endAngle = angleDeg(w - pcc, pcc, x, y)
            }
            3 -> {
                y = (h + offset).toFloat()
                startAngle = angleDeg(pcc, h - pcc, x, y)
                endAngle = angleDeg(w - pcc, h - pcc, x, y)
            }
            else -> throw IllegalArgumentException("Supplied value out of range")
        }

        if (endAngle < startAngle) {
            endAngle += 360f
        }

        val range = Math.abs(endAngle - startAngle).toInt()
        val randomAngleInRange = startAngle + (if (range > 0) random.nextInt(range) else 0)
        val direction = Math.toRadians(randomAngleInRange.toDouble())

        val dCos = Math.cos(direction).toFloat()
        val dSin = Math.sin(direction).toFloat()
        val speedFactor = newRandomIndividualParticleSpeedFactor()
        val radius = newRandomIndividualParticleRadius(scene)

        scene.setParticleData(
            position,
            x,
            y,
            dCos,
            dSin,
            radius,
            speedFactor
        )
    }

    private fun angleDeg(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val angleRad = Math.atan2((ay - by).toDouble(), (ax - bx).toDouble())
        var angle = Math.toDegrees(angleRad)
        if (angleRad < 0) {
            angle += 360.0
        }
        return angle.toFloat()
    }

    private fun newRandomIndividualParticleSpeedFactor(): Float {
        return 1f + 0.1f * (random.nextInt(11) - 5)
    }

    private fun newRandomIndividualParticleRadius(scene: SceneConfiguration): Float {
        val min = scene.getParticleRadiusMin()
        val max = scene.getParticleRadiusMax()
        return if (min == max) {
            min
        } else {
            val bound = ((max - min) * 100f).toInt()
            min + (if (bound > 0) random.nextInt(bound) else 0) / 100f
        }
    }
}