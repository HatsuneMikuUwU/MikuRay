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
        "uwu_shape_clover"         -> R.raw.uwu_shape_clover
        "uwu_shape_circle"         -> R.raw.uwu_shape_circle
        "uwu_shape_diamond"        -> R.raw.uwu_shape_diamond
        "uwu_shape_pentagon"       -> R.raw.uwu_shape_pentagon
        "uwu_shape_hexagon"        -> R.raw.uwu_shape_hexagon
        "uwu_shape_octagon"        -> R.raw.uwu_shape_octagon
        "uwu_shape_rounded_square" -> R.raw.uwu_shape_rounded_square
        "uwu_shape_squircle"       -> R.raw.uwu_shape_squircle
        "uwu_shape_heart"       -> R.raw.uwu_shape_heart
        "uwu_shape_hive"       -> R.raw.uwu_shape_hive
        "uwu_shape_pill"       -> R.raw.uwu_shape_pill
        "uwu_shape_scallop"       -> R.raw.uwu_shape_scallop
        else                       -> R.raw.uwu_shape_cookie
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
                        .error(R.drawable.uwu_banner_profile)
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
        setImageResource(R.drawable.uwu_banner_profile)
    }
}
