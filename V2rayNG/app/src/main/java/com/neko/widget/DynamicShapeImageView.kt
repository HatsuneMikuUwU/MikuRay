package com.neko.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.getColorAttr

class DynamicShapeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr) {

    private var currentShapeKey: String? = AppConfig.PREF_ICON_SHAPE_DEFAULT

    private var customBgColor: Int? = null
    private var iconDrawable: Drawable? = null
    private var iconTintColor: Int? = null
    private var iconSizeFraction: Float = 0.55f

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val shapeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AppConfig.BROADCAST_ACTION_ICON_SHAPE_CHANGED) {
                val newKey = MmkvManager.decodeSettingsString(AppConfig.PREF_ICON_SHAPE)
                    ?: AppConfig.PREF_ICON_SHAPE_DEFAULT
                applyShape(newKey)
            }
        }
    }

    override fun createImageViewHelper(): ShaderHelper {
        return SvgShader(resolveShapeId())
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.DynamicShapeImageView,
                defStyleAttr,
                0
            )

            if (typedArray.hasValue(R.styleable.DynamicShapeImageView_shapeBackgroundColor)) {
                customBgColor = typedArray.getColor(
                    R.styleable.DynamicShapeImageView_shapeBackgroundColor,
                    0
                )
            }

            if (typedArray.hasValue(R.styleable.DynamicShapeImageView_sectionIcon)) {
                val resId = typedArray.getResourceId(R.styleable.DynamicShapeImageView_sectionIcon, 0)
                if (resId != 0) iconDrawable = ContextCompat.getDrawable(context, resId)?.mutate()
            }

            if (typedArray.hasValue(R.styleable.DynamicShapeImageView_iconTint)) {
                iconTintColor = typedArray.getColor(R.styleable.DynamicShapeImageView_iconTint, Color.WHITE)
            }

            if (typedArray.hasValue(R.styleable.DynamicShapeImageView_iconSizeFraction)) {
                iconSizeFraction = typedArray.getFloat(R.styleable.DynamicShapeImageView_iconSizeFraction, 0.55f)
                    .coerceIn(0.1f, 0.9f)
            }

            typedArray.recycle()
        }

        scaleType = ScaleType.CENTER_CROP
        loadColorBitmap()
    }

    private fun loadColorBitmap() {
        try {
            val color = customBgColor ?: context.getColorAttr(androidx.appcompat.R.attr.colorPrimary)

            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)

            setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawIconOverlay(canvas)
    }

    private fun drawIconOverlay(canvas: Canvas) {
        val drawable = iconDrawable ?: return
        val w = width
        val h = height
        if (w == 0 || h == 0) return

        val iconSize = (minOf(w, h) * iconSizeFraction).toInt()
        val left = (w - iconSize) / 2
        val top = (h - iconSize) / 2

        val tint = iconTintColor ?: try {
            context.getColorAttr(com.google.android.material.R.attr.colorOnPrimary)
        } catch (_: Exception) { Color.WHITE }

        DrawableCompat.setTint(drawable, tint)
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_IN)
        drawable.setBounds(left, top, left + iconSize, top + iconSize)
        drawable.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            val savedKey = MmkvManager.decodeSettingsString(AppConfig.PREF_ICON_SHAPE)
                ?: AppConfig.PREF_ICON_SHAPE_DEFAULT
            applyShape(savedKey)

            val filter = IntentFilter(AppConfig.BROADCAST_ACTION_ICON_SHAPE_CHANGED)
            ContextCompat.registerReceiver(
                context, shapeChangeReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            try { context.unregisterReceiver(shapeChangeReceiver) } catch (_: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)

        if (hasWindowFocus && !isInEditMode) {
            val savedKey = MmkvManager.decodeSettingsString(AppConfig.PREF_ICON_SHAPE)
                ?: AppConfig.PREF_ICON_SHAPE_DEFAULT
            applyShape(savedKey)
        }
    }

    private fun applyShape(shapeKey: String) {
        if (currentShapeKey != shapeKey) {
            currentShapeKey = shapeKey
            reloadShape()
            invalidate()
        }
    }

    private fun resolveShapeId(): Int = when (currentShapeKey ?: AppConfig.PREF_ICON_SHAPE_DEFAULT) {
        "uwu_shape_arch"           -> R.raw.uwu_shape_arch
        "uwu_shape_arrow"          -> R.raw.uwu_shape_arrow
        "uwu_shape_boom"           -> R.raw.uwu_shape_boom
        "uwu_shape_bun"            -> R.raw.uwu_shape_bun
        "uwu_shape_burst"          -> R.raw.uwu_shape_burst
        "uwu_shape_circle"         -> R.raw.uwu_shape_circle
        "uwu_shape_clover_4"       -> R.raw.uwu_shape_clover_4
        "uwu_shape_clover_8"       -> R.raw.uwu_shape_clover_8
        "uwu_shape_cookie_4"       -> R.raw.uwu_shape_cookie_4
        "uwu_shape_cookie_6"       -> R.raw.uwu_shape_cookie_6
        "uwu_shape_cookie_7"       -> R.raw.uwu_shape_cookie_7
        "uwu_shape_cookie_9"       -> R.raw.uwu_shape_cookie_9
        "uwu_shape_cookie_12"      -> R.raw.uwu_shape_cookie_12
        "uwu_shape_diamond"        -> R.raw.uwu_shape_diamond
        "uwu_shape_fan"            -> R.raw.uwu_shape_fan
        "uwu_shape_flower"         -> R.raw.uwu_shape_flower
        "uwu_shape_gem"            -> R.raw.uwu_shape_gem
        "uwu_shape_ghostish"       -> R.raw.uwu_shape_ghostish
        "uwu_shape_heart"          -> R.raw.uwu_shape_heart
        "uwu_shape_hexagon"        -> R.raw.uwu_shape_hexagon
        "uwu_shape_oval"           -> R.raw.uwu_shape_oval
        "uwu_shape_pentagon"       -> R.raw.uwu_shape_pentagon
        "uwu_shape_pill"           -> R.raw.uwu_shape_pill
        "uwu_shape_pixel_circle"   -> R.raw.uwu_shape_pixel_circle
        "uwu_shape_pixel_triangle" -> R.raw.uwu_shape_pixel_triangle
        "uwu_shape_puffy"          -> R.raw.uwu_shape_puffy
        "uwu_shape_puffy_diamond"  -> R.raw.uwu_shape_puffy_diamond
        "uwu_shape_semicircle"     -> R.raw.uwu_shape_semicircle
        "uwu_shape_slanted_square" -> R.raw.uwu_shape_slanted_square
        "uwu_shape_soft_boom"      -> R.raw.uwu_shape_soft_boom
        "uwu_shape_soft_burst"     -> R.raw.uwu_shape_soft_burst
        "uwu_shape_square"         -> R.raw.uwu_shape_square
        "uwu_shape_sunny"          -> R.raw.uwu_shape_sunny
        "uwu_shape_triangle"       -> R.raw.uwu_shape_triangle
        "uwu_shape_very_sunny"     -> R.raw.uwu_shape_very_sunny
        else                       -> R.raw.uwu_shape_cookie_9
    }
}
