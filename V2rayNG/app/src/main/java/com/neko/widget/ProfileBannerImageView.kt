package com.neko.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.Target
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

class ProfileBannerImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr) {

    private val TAG_PROFILE_DEFAULT = "DEFAULT_BANNER_PROFILE"

    private var currentShapeKey: String = AppConfig.PREF_PROFILE_BANNER_SHAPE_DEFAULT

    private val shapeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AppConfig.BROADCAST_ACTION_PROFILE_BANNER_CHANGED) {
                post {
                    checkAndUpdateShape()
                    loadImage()
                }
            }
        }
    }

    override fun createImageViewHelper(): ShaderHelper {
        currentShapeKey = resolveShapeKey()
        return SvgShader(resolveShapeId(currentShapeKey))
    }

    init {
        scaleType = ScaleType.CENTER_CROP
        setLayerType(View.LAYER_TYPE_NONE, null)
        elevation = 0f
        outlineProvider = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            val filter = IntentFilter(AppConfig.BROADCAST_ACTION_PROFILE_BANNER_CHANGED)
            ContextCompat.registerReceiver(
                context, shapeChangeReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            checkAndUpdateShape()
            loadImage()
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
            checkAndUpdateShape()
            loadImage()
        }
    }

    private fun resolveShapeKey(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_SHAPE)
            ?: AppConfig.PREF_PROFILE_BANNER_SHAPE_DEFAULT

    private fun resolveShapeId(key: String): Int = when (key) {
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

    private fun checkAndUpdateShape() {
        val newKey = resolveShapeKey()
        if (currentShapeKey != newKey) {
            currentShapeKey = newKey
            reloadShape()
            invalidate()
        }
    }

    private fun loadImage() {
        try {
            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
            val targetTag = if (uriString.isNullOrEmpty()) TAG_PROFILE_DEFAULT else uriString

            if (this.tag != targetTag) {
                if (!uriString.isNullOrEmpty()) {
                    val savedUri = Uri.parse(uriString)
                    Glide.with(this)
                        .asBitmap()
                        .load(savedUri)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontAnimate()
                        .error(R.drawable.material_banner_profile)
                        .into(this)
                } else {
                    loadDefault()
                }
                this.tag = targetTag
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (this.tag != TAG_PROFILE_DEFAULT) {
                loadDefault()
                this.tag = TAG_PROFILE_DEFAULT
            }
        }
    }

    private fun loadDefault() {
        Glide.with(this).clear(this)
        setImageResource(R.drawable.material_banner_profile)
    }
}
