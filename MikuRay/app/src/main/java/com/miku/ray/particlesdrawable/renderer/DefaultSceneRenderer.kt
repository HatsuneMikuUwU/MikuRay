package com.miku.ray.particlesdrawable.renderer

import com.miku.ray.particlesdrawable.KeepAsApi
import com.miku.ray.particlesdrawable.model.Scene
import com.miku.ray.particlesdrawable.contract.LowLevelRenderer
import com.miku.ray.particlesdrawable.contract.SceneRenderer
import com.miku.ray.particlesdrawable.util.DistanceResolver
import com.miku.ray.particlesdrawable.util.LineColorResolver
import com.miku.ray.particlesdrawable.util.ParticleColorResolver
import java.nio.FloatBuffer

@KeepAsApi
class DefaultSceneRenderer(private val renderer: LowLevelRenderer) : SceneRenderer {

    override fun drawScene(scene: Scene) {
        if (scene.getDensity() > 0) {
            val particleColor = ParticleColorResolver.resolveParticleColorWithSceneAlpha(
                scene.getParticleColor(),
                scene.getAlpha()
            )

            val radiuses: FloatBuffer = scene.getRadiuses()
            val particlesCount = scene.getDensity()
            for (i in 0 until particlesCount) {
                val x1 = scene.getParticleX(i)
                val y1 = scene.getParticleY(i)

                for (j in i + 1 until particlesCount) {
                    val x2 = scene.getParticleX(j)
                    val y2 = scene.getParticleY(j)

                    val distance = DistanceResolver.distance(x1, y1, x2, y2)
                    if (distance < scene.getLineLength()) {
                        val lineColor = LineColorResolver.resolveLineColorWithAlpha(
                            scene.getAlpha(),
                            scene.getLineColor(),
                            scene.getLineLength(),
                            distance
                        )

                        renderer.drawLine(
                            x1,
                            y1,
                            x2,
                            y2,
                            scene.getLineThickness(),
                            lineColor
                        )
                    }
                }

                val radius = radiuses.get(i)
                renderer.fillCircle(x1, y1, radius, particleColor)
            }
        }
    }
}