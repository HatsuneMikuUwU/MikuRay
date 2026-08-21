package com.miku.ray.particlesdrawable.model

import com.miku.ray.particlesdrawable.Defaults
import com.miku.ray.particlesdrawable.KeepAsApi
import com.miku.ray.particlesdrawable.contract.SceneConfiguration
import java.nio.FloatBuffer
import java.util.Locale
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange

@KeepAsApi
class Scene : SceneConfiguration {

    companion object {
        private const val COORDINATES_PER_VERTEX = 2
    }

    private var alpha: Int = 255

    var density: Int = Defaults.DENSITY

    var frameDelay: Int = Defaults.FRAME_DELAY

    @ColorInt
    var lineColor: Int = Defaults.LINE_COLOR

    var lineLength: Float = Defaults.LINE_LENGTH

    var lineThickness: Float = Defaults.LINE_THICKNESS

    @ColorInt
    var particleColor: Int = Defaults.PARTICLE_COLOR

    var particleRadiusMax: Float = Defaults.PARTICLE_RADIUS_MAX

    var particleRadiusMin: Float = Defaults.PARTICLE_RADIUS_MIN

    var speedFactor: Float = Defaults.SPEED_FACTOR

    private var width: Int = 0
    private var height: Int = 0

    private lateinit var coordinates: FloatBuffer

    private lateinit var directions: FloatBuffer
    private lateinit var radiuses: FloatBuffer
    private lateinit var speedFactors: FloatBuffer

    init {
        initBuffers(density)
    }

    fun getCoordinates(): FloatBuffer = coordinates

    fun getRadiuses(): FloatBuffer = radiuses

    fun setWidth(width: Int) {
        this.width = width
    }

    fun setHeight(height: Int) {
        this.height = height
    }

    fun getWidth(): Int = width

    fun getHeight(): Int = height

    fun setParticleData(
        position: Int,
        x: Float,
        y: Float,
        dCos: Float,
        dSin: Float,
        radius: Float,
        speedFactor: Float
    ) {
        setParticleX(position, x)
        setParticleY(position, y)

        setParticleDirectionCos(position, dCos)
        setParticleDirectionSin(position, dSin)

        radiuses.put(position, radius)
        speedFactors.put(position, speedFactor)
    }

    fun getParticleX(position: Int): Float = coordinates.get(position * 2)

    fun getParticleY(position: Int): Float = coordinates.get(position * 2 + 1)

    fun getParticleDirectionCos(position: Int): Float = directions.get(position * 2)

    fun getParticleDirectionSin(position: Int): Float = directions.get(position * 2 + 1)

    fun getParticleSpeedFactor(position: Int): Float = speedFactors.get(position)

    fun setParticleX(position: Int, x: Float) {
        coordinates.put(position * 2, x)
    }

    fun setParticleY(position: Int, y: Float) {
        coordinates.put(position * 2 + 1, y)
    }

    private fun setParticleDirectionCos(position: Int, direction: Float) {
        directions.put(position * 2, direction)
    }

    private fun setParticleDirectionSin(position: Int, direction: Float) {
        directions.put(position * 2 + 1, direction)
    }

    fun setAlpha(alpha: Int) {
        this.alpha = alpha
    }

    @IntRange(from = 0, to = 255)
    fun getAlpha(): Int = alpha

    private fun initBuffers(density: Int) {
        initCoordinates(density)
        initDirections(density)
        initSpeedFactors(density)
        initRadiuses(density)
    }

    private fun initCoordinates(density: Int) {
        val capacity = density * COORDINATES_PER_VERTEX
        if (!::coordinates.isInitialized || coordinates.capacity() != capacity) {
            coordinates = FloatBuffer.allocate(capacity)
        }
    }

    private fun initDirections(density: Int) {
        val capacity = density * 2
        if (!::directions.isInitialized || directions.capacity() != capacity) {
            directions = FloatBuffer.allocate(capacity)
        }
    }

    private fun initSpeedFactors(density: Int) {
        if (!::speedFactors.isInitialized || speedFactors.capacity() != density) {
            speedFactors = FloatBuffer.allocate(density)
        }
    }

    private fun initRadiuses(density: Int) {
        if (!::radiuses.isInitialized || radiuses.capacity() != density) {
            radiuses = FloatBuffer.allocate(density)
        }
    }

    override fun getDensity(): Int = density

    override fun setDensity(@IntRange(from = 0) density: Int) {
        if (density < 0) {
            throw IllegalArgumentException("Density must not be negative")
        }
        if (this.density != density) {
            this.density = density
            initBuffers(density)
        }
    }

    override fun getFrameDelay(): Int = frameDelay

    override fun setFrameDelay(@IntRange(from = 0) delay: Int) {
        if (delay < 0) {
            throw IllegalArgumentException("delay must not be nagative")
        }
        frameDelay = delay
    }

    override fun getLineColor(): Int = lineColor

    override fun setLineColor(@ColorInt lineColor: Int) {
        this.lineColor = lineColor
    }

    override fun getLineLength(): Float = lineLength

    override fun getLineThickness(): Float = lineThickness

    override fun setLineThickness(@FloatRange(from = 1.0) lineThickness: Float) {
        if (lineThickness < 1) {
            throw IllegalArgumentException("Line thickness must not be less than 1")
        }
        if (lineThickness.isNaN()) {
            throw IllegalArgumentException("line thickness must be a valid float")
        }
        this.lineThickness = lineThickness
    }

    override fun setLineLength(@FloatRange(from = 0.0) lineLength: Float) {
        if (lineLength < 0) {
            throw IllegalArgumentException("line length must not be negative")
        }
        if (lineLength.isNaN()) {
            throw IllegalArgumentException("line length must be a valid float")
        }
        this.lineLength = lineLength
    }

    override fun getParticleColor(): Int = particleColor

    override fun setParticleColor(@ColorInt color: Int) {
        particleColor = color
    }

    override fun getParticleRadiusMin(): Float = particleRadiusMin

    override fun getParticleRadiusMax(): Float = particleRadiusMax

    override fun setParticleRadiusRange(
        @FloatRange(from = 0.5) minRadius: Float,
        @FloatRange(from = 0.5) maxRadius: Float
    ) {
        if (minRadius < 0.5f || maxRadius < 0.5f) {
            throw IllegalArgumentException("Particle radius must not be less than 0.5")
        }
        if (minRadius.isNaN() || maxRadius.isNaN()) {
            throw IllegalArgumentException("Particle radius must be a valid float")
        }
        if (minRadius > maxRadius) {
            throw IllegalArgumentException(
                String.format(
                    Locale.US,
                    "Min radius must not be greater than max, but min = %f, max = %f",
                    minRadius,
                    maxRadius
                )
            )
        }
        particleRadiusMin = minRadius
        particleRadiusMax = maxRadius
    }

    override fun getSpeedFactor(): Float = speedFactor

    override fun setSpeedFactor(@FloatRange(from = 0.0) speedFactor: Float) {
        if (speedFactor < 0) {
            throw IllegalArgumentException("speedFactor must not be nagative")
        }
        if (speedFactor.isNaN()) {
            throw IllegalArgumentException("speedFactor must be a valid float")
        }
        this.speedFactor = speedFactor
    }
}