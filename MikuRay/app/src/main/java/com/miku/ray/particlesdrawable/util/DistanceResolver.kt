package com.miku.ray.particlesdrawable.util

import com.miku.ray.particlesdrawable.KeepAsApi
import kotlin.math.sqrt

@KeepAsApi
object DistanceResolver {
    @JvmStatic
    fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float =
        sqrt(((ax - bx) * (ax - bx) + (ay - by) * (ay - by)).toDouble()).toFloat()
}