package com.miku.ray.particlesdrawable.engine

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Keep
import com.miku.ray.R
import com.miku.ray.particlesdrawable.Defaults
import com.miku.ray.particlesdrawable.contract.SceneConfiguration

@Keep
class SceneConfigurator {

    fun configureSceneFromAttributes(
        scene: SceneConfiguration,
        context: Context,
        attrs: AttributeSet
    ) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ParticlesView)
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
}