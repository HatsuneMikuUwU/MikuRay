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
        "uwu_shape_clover"         -> R.raw.uwu_shape_clover
        "uwu_shape_circle"         -> R.raw.uwu_shape_circle
        "uwu_shape_diamond"        -> R.raw.uwu_shape_diamond
        "uwu_shape_pentagon"       -> R.raw.uwu_shape_pentagon
        "uwu_shape_hexagon"        -> R.raw.uwu_shape_hexagon
        "uwu_shape_octagon"        -> R.raw.uwu_shape_octagon
        "uwu_shape_rounded_square" -> R.raw.uwu_shape_rounded_square
        "uwu_shape_squircle"       -> R.raw.uwu_shape_squircle
        "uwu_shape_heart"          -> R.raw.uwu_shape_heart
        "uwu_shape_hive"       -> R.raw.uwu_shape_hive
        "uwu_shape_pill"       -> R.raw.uwu_shape_pill
        "uwu_shape_scallop"       -> R.raw.uwu_shape_scallop
        else                       -> R.raw.uwu_shape_cookie
    }
}
