package com.neko.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import androidx.appcompat.R as AppCompatR
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.getColorAttr

class DynamicShapeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr) {

    private var currentShapeKey: String? = AppConfig.PREF_ICON_SHAPE_DEFAULT
    
    private var customBgColor: Int? = null

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
        "material_shape_cookie"          -> R.raw.material_shape_cookie
        "material_shape_cookie_4"        -> R.raw.material_shape_cookie_4
        "material_shape_cookie_6"        -> R.raw.material_shape_cookie_6
        "material_shape_cookie_7"        -> R.raw.material_shape_cookie_7
        "material_shape_cookie_12"       -> R.raw.material_shape_cookie_12
        "material_shape_clover_4"        -> R.raw.material_shape_clover_4
        "material_shape_clover_8"        -> R.raw.material_shape_clover_8
        "material_shape_circle"          -> R.raw.material_shape_circle
        "material_shape_oval"            -> R.raw.material_shape_oval
        "material_shape_pill"            -> R.raw.material_shape_pill
        "material_shape_square"          -> R.raw.material_shape_square
        "material_shape_slanted_square"  -> R.raw.material_shape_slanted_square
        "material_shape_diamond"         -> R.raw.material_shape_diamond
        "material_shape_puffy_diamond"   -> R.raw.material_shape_puffy_diamond
        "material_shape_pentagon"        -> R.raw.material_shape_pentagon
        "material_shape_hexagon"         -> R.raw.material_shape_hexagon
        "material_shape_triangle"        -> R.raw.material_shape_triangle
        "material_shape_arrow"           -> R.raw.material_shape_arrow
        "material_shape_heart"           -> R.raw.material_shape_heart
        "material_shape_gem"             -> R.raw.material_shape_gem
        "material_shape_arch"            -> R.raw.material_shape_arch
        "material_shape_fan"             -> R.raw.material_shape_fan
        "material_shape_semicircle"      -> R.raw.material_shape_semicircle
        "material_shape_bun"             -> R.raw.material_shape_bun
        "material_shape_sunny"           -> R.raw.material_shape_sunny
        "material_shape_very_sunny"      -> R.raw.material_shape_very_sunny
        "material_shape_burst"           -> R.raw.material_shape_burst
        "material_shape_soft_burst"      -> R.raw.material_shape_soft_burst
        "material_shape_boom"            -> R.raw.material_shape_boom
        "material_shape_soft_boom"       -> R.raw.material_shape_soft_boom
        "material_shape_flower"          -> R.raw.material_shape_flower
        "material_shape_puffy"           -> R.raw.material_shape_puffy
        "material_shape_ghostish"        -> R.raw.material_shape_ghostish
        "material_shape_pixel_circle"    -> R.raw.material_shape_pixel_circle
        "material_shape_pixel_triangle"  -> R.raw.material_shape_pixel_triangle
        else                        -> R.raw.material_shape_cookie
    }
}
