package com.miku.ray.particlesdrawable.contract

import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.Keep

@Keep
interface SceneConfiguration {

    fun setDensity(@IntRange(from = 0) density: Int)

    fun getDensity(): Int

    fun setFrameDelay(@IntRange(from = 0) delay: Int)

    fun getFrameDelay(): Int

    fun setLineColor(@ColorInt lineColor: Int)

    fun getLineColor(): @ColorInt Int

    fun setLineLength(@FloatRange(from = 0.0) lineLength: Float)

    fun getLineLength(): Float

    fun setLineThickness(@FloatRange(from = 1.0) lineThickness: Float)

    fun getLineThickness(): Float

    fun setParticleColor(@ColorInt color: Int)

    fun getParticleColor(): @ColorInt Int

    fun setParticleRadiusRange(
        @FloatRange(from = 0.5) minRadius: Float,
        @FloatRange(from = 0.5) maxRadius: Float
    )

    fun getParticleRadiusMax(): Float

    fun getParticleRadiusMin(): Float

    fun setSpeedFactor(@FloatRange(from = 0.0) speedFactor: Float)

    fun getSpeedFactor(): Float
}