package com.v2ray.ang.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

class SelectedProfileBannerController(private val context: Context) {

    private var changeReceiver: BroadcastReceiver? = null

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)

    fun hasBanner(): Boolean =
        !MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI).isNullOrEmpty()

    fun applyTo(target: View, cornerRadiusDp: Float = 16f) {
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
        if (uriString.isNullOrEmpty()) {
            clear(target)
            return
        }

        val dimPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.SELECTED_BANNER_DIM_DEFAULT
        ).coerceIn(AppConfig.SELECTED_BANNER_DIM_MIN, AppConfig.SELECTED_BANNER_DIM_MAX)
        val cornerRadiusPx = cornerRadiusDp * context.resources.displayMetrics.density
        val bitmapKey = "selected_banner::$uriString"
        val tagKey = "$bitmapKey::dim=$dimPercent::r=$cornerRadiusPx"
        if (target.getTag(TAG_KEY) == tagKey) return

        bitmapCache[bitmapKey]?.let { cached ->
            target.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            
            target.background = CenterCropDimDrawable(cached, dimColorFor(dimPercent), cornerRadiusPx)
            target.setTag(TAG_KEY, tagKey)
            return
        }

        try {
            val uri = Uri.parse(uriString)
            Glide.with(context)
                .asBitmap()
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        bitmapCache[bitmapKey] = resource
                        
                        target.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        
                        target.background = CenterCropDimDrawable(resource, dimColorFor(dimPercent), cornerRadiusPx)
                        target.setTag(TAG_KEY, tagKey)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        target.setTag(TAG_KEY, null)
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
            target.setTag(TAG_KEY, null)
        }
    }

    fun clear(target: View) {
        if (target.getTag(TAG_KEY) == null) return
        target.setTag(TAG_KEY, null)
        
        target.setLayerType(View.LAYER_TYPE_NONE, null)
        
        Glide.with(context).clear(target)
    }

    private fun dimColorFor(dimPercent: Int): Int {
        val alpha = (dimPercent * 255 / 100).coerceIn(0, 255)
        val baseColor = context.getColorAttr("colorCard")
        return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }

    private class CenterCropDimDrawable(
        private val bitmap: Bitmap,
        private val dimColor: Int,
        private val cornerRadius: Float = 0f
    ) : Drawable() {
        
        private val bitmapPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = android.graphics.PorterDuffColorFilter(dimColor, android.graphics.PorterDuff.Mode.SRC_OVER)
        }
        
        private val matrix = android.graphics.Matrix()
        private val rectF = android.graphics.RectF()

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val vw = bounds.width().toFloat()
            val vh = bounds.height().toFloat()

            val scale = maxOf(vw / bw, vh / bh)
            val scaledW = bw * scale
            val scaledH = bh * scale
            val dx = bounds.left + (vw - scaledW) / 2f
            val dy = bounds.top + (vh - scaledH) / 2f

            matrix.reset()
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)

            val shader = android.graphics.BitmapShader(
                bitmap,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
            shader.setLocalMatrix(matrix)
            bitmapPaint.shader = shader

            rectF.set(bounds)
        }

        override fun draw(canvas: android.graphics.Canvas) {
            if (bounds.width() <= 0 || bounds.height() <= 0) return

            if (cornerRadius > 0f) {
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bitmapPaint)
            } else {
                canvas.drawRect(rectF, bitmapPaint)
            }
        }

        override fun setAlpha(alpha: Int) { bitmapPaint.alpha = alpha }
        
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        }
        
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = -1
        override fun getIntrinsicHeight(): Int = -1
    }

    fun registerChangeListener(onChanged: () -> Unit) {
        if (changeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED) {
                    onChanged()
                }
            }
        }
        changeReceiver = receiver
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterChangeListener() {
        changeReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        changeReceiver = null
    }

    companion object {
        private val TAG_KEY = "selected_profile_banner_tag".hashCode()

        private val bitmapCache = mutableMapOf<String, Bitmap>()

        fun broadcastChanged(context: Context) {
            bitmapCache.clear()
            context.sendBroadcast(Intent(AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED))
        }
    }
}
