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
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.getColorAttr

/**
 * Shape-aware ImageView used both for the app's icon-shape background badges
 * and, via [R.styleable.DynamicShapeImageView_shapeTarget], for the small
 * "arrow" background badges shown at the end of preference/menu cards.
 *
 * Both targets share the exact same set of SVG shapes (see [resolveShapeId]),
 * but each target has its own MMKV pref key / broadcast action so the icon
 * shape and arrow shape can be customized independently from UI Settings.
 */
class DynamicShapeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr) {

    private enum class ShapeTarget { ICON, ARROW }

    private var shapeTarget: ShapeTarget = ShapeTarget.ICON

    private val prefKey: String
        get() = if (shapeTarget == ShapeTarget.ARROW) AppConfig.PREF_ARROW_SHAPE else AppConfig.PREF_ICON_SHAPE

    private val defaultShapeKey: String
        get() = if (shapeTarget == ShapeTarget.ARROW) AppConfig.PREF_ARROW_SHAPE_DEFAULT else AppConfig.PREF_ICON_SHAPE_DEFAULT

    private val broadcastAction: String
        get() = if (shapeTarget == ShapeTarget.ARROW) AppConfig.BROADCAST_ACTION_ARROW_SHAPE_CHANGED else AppConfig.BROADCAST_ACTION_ICON_SHAPE_CHANGED

    private var currentShapeKey: String? = null

    private var customBgColor: Int? = null

    private val shapeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == broadcastAction) {
                val newKey = MmkvManager.decodeSettingsString(prefKey) ?: defaultShapeKey
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

            shapeTarget = if (typedArray.getInt(R.styleable.DynamicShapeImageView_shapeTarget, 0) == 1) {
                ShapeTarget.ARROW
            } else {
                ShapeTarget.ICON
            }

            typedArray.recycle()
        }

        currentShapeKey = defaultShapeKey

        scaleType = ScaleType.CENTER_CROP

        // ShaderImageView's own init block already called createImageViewHelper()
        // during the super() constructor call above, i.e. before shapeTarget and
        // customBgColor (parsed just above) even existed yet — so that first
        // helper was always built with the ICON defaults. Force a rebuild now
        // that this view's real target/color are known, so arrow badges (and any
        // non-default color) render correctly from the very first frame instead
        // of only after the user actively changes the shape preference.
        reloadShape()

        loadColorBitmap()
    }

    private fun loadColorBitmap() {
        try {
            val color = customBgColor ?: context.getColorAttr("colorPrimary")

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
            val savedKey = MmkvManager.decodeSettingsString(prefKey) ?: defaultShapeKey
            applyShape(savedKey)

            val filter = IntentFilter(broadcastAction)
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
            val savedKey = MmkvManager.decodeSettingsString(prefKey) ?: defaultShapeKey
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

    private fun resolveShapeId(): Int = when (currentShapeKey ?: defaultShapeKey) {
        "uwu_shape_cookie"         -> R.raw.uwu_shape_cookie
        "uwu_shape_clover"         -> R.raw.uwu_shape_clover
        "uwu_shape_circle"         -> R.raw.uwu_shape_circle
        "uwu_shape_diamond"        -> R.raw.uwu_shape_diamond
        "uwu_shape_pentagon"       -> R.raw.uwu_shape_pentagon
        "uwu_shape_hexagon"        -> R.raw.uwu_shape_hexagon
        "uwu_shape_octagon"        -> R.raw.uwu_shape_octagon
        "uwu_shape_rounded_square" -> R.raw.uwu_shape_rounded_square
        "uwu_shape_squircle"       -> R.raw.uwu_shape_squircle
        "uwu_shape_heart"          -> R.raw.uwu_shape_heart
        "uwu_shape_hive"           -> R.raw.uwu_shape_hive
        "uwu_shape_pill"           -> R.raw.uwu_shape_pill
        "uwu_shape_scallop"        -> R.raw.uwu_shape_scallop
        else                       -> if (shapeTarget == ShapeTarget.ARROW) R.raw.uwu_shape_circle else R.raw.uwu_shape_cookie
    }
}
