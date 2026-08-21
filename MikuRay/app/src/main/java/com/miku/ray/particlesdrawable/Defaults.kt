package com.miku.ray.particlesdrawable

import android.content.res.Resources
import android.graphics.Color
import androidx.annotation.ColorInt
import android.util.TypedValue

object Defaults {

    @JvmField
    val DENSITY: Int = 60

    @JvmField
    val FRAME_DELAY: Int = 10

    @JvmField
    @ColorInt
    val LINE_COLOR: Int = Color.WHITE

    @JvmField
    val LINE_LENGTH: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 86f, Resources.getSystem().displayMetrics
    )

    @JvmField
    val LINE_THICKNESS: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 1f, Resources.getSystem().displayMetrics
    )

    @JvmField
    @ColorInt
    val PARTICLE_COLOR: Int = Color.WHITE

    @JvmField
    val PARTICLE_RADIUS_MAX: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 3f, Resources.getSystem().displayMetrics
    )

    @JvmField
    val PARTICLE_RADIUS_MIN: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 1f, Resources.getSystem().displayMetrics
    )

    @JvmField
    val SPEED_FACTOR: Float = 1f
}