package com.v2ray.ang.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.app.AppCompatActivity
import eightbitlab.com.blurview.BlurView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager
import java.lang.ref.WeakReference

object BlurBottomStatusController {

    private var blurViewReference: WeakReference<BlurView>? = null
    private var glassDrawableReference: WeakReference<GradientDrawable>? = null
    private var strokeDrawableReference: WeakReference<StrokeDrawable>? = null
    private var glassFillBaseColor: Int = 0
    private var glassFillColor: Int = Color.TRANSPARENT
    private var glassRadiusPx: Float = 28f

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        if (isEnabled()) applyBlurOn(activity, binding)
        else applyBlurOff(activity, binding)
    }

    fun updateRadius(radius: Float) {
        val blurRadius = radius.coerceIn(1f, 50f)
        blurViewReference?.get()?.apply {
            setBlurRadius(blurRadius)
            invalidate()
        }
    }

    fun updateAlpha(alphaPercent: Float) {
        glassFillColor = withAlpha(glassFillBaseColor, alphaPercentToInt(alphaPercent))
        glassDrawableReference?.get()?.setColor(glassFillColor)
        blurViewReference?.get()?.invalidate()
    }

    private fun alphaPercentToInt(percent: Float): Int =
        (percent.coerceIn(0f, 100f) / 100f * 255f).toInt().coerceIn(0, 255)

    private fun applyBlurOn(activity: AppCompatActivity, binding: ActivityMainBinding) {
        val blurRadius = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_RADIUS,
            AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
        ).toFloat().coerceIn(1f, 50f)
        val alphaPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_ALPHA,
            AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA
        ).toFloat()

        val density = activity.resources.displayMetrics.density
        glassRadiusPx = 28f * density
        glassFillBaseColor = activity.getColorAttr("colorSurfaceContainerHighest")
        glassFillColor = withAlpha(glassFillBaseColor, alphaPercentToInt(alphaPercent))
        val glassDrawable = GradientDrawable().apply {
            setColor(glassFillColor)
            setCornerRadius(glassRadiusPx)
        }
        val strokeDrawable = StrokeDrawable().apply {
            setCornerRadius(glassRadiusPx)
            setStrokeWidthTop(1f * density)
            setStrokeWidthBottom((2f / 3f) * density)
            setStrokeColorTop(withAlpha(activity.getColorAttr("strokeDrawable"), 0xA8))
            setStrokeColorBottom(withAlpha(activity.getColorAttr("strokeDrawable"), 0x70))
        }

        binding.blurBottomStatus.apply {
            background = glassDrawable
            foreground = strokeDrawable
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setupWith(binding.mainBlurTarget)
                .setBlurRadius(blurRadius)
                .setOverlayColor(Color.TRANSPARENT)
            visibility = View.VISIBLE
        }

        blurViewReference = WeakReference(binding.blurBottomStatus)
        glassDrawableReference = WeakReference(glassDrawable)
        strokeDrawableReference = WeakReference(strokeDrawable)
        binding.cardBottomStatus.setCardBackgroundColor(Color.TRANSPARENT)
        binding.tvIpState.setTextColor(activity.getColorAttr("colorOnSurfaceVariant"))
        binding.tvIpState.alpha = 1f
        binding.tvTestState.setTextColor(activity.getColorAttr("colorOnSurface"))
        binding.fab.visibility = View.VISIBLE
        binding.fabNoBlur.visibility = View.GONE
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun applyBlurOff(activity: AppCompatActivity, binding: ActivityMainBinding) {
        blurViewReference?.clear()
        glassDrawableReference?.clear()
        strokeDrawableReference?.clear()
        binding.blurBottomStatus.apply {
            visibility = View.GONE
            clipToOutline = false
            background = null
            foreground = null
        }
        binding.cardBottomStatus.setCardBackgroundColor(activity.getColorAttr("colorPrimary"))
        val textColorOnPrimary = activity.getColorAttr("colorOnPrimary")
        binding.tvIpState.setTextColor(textColorOnPrimary)
        binding.tvIpState.alpha = 0.8f
        binding.tvTestState.setTextColor(textColorOnPrimary)
        binding.fab.visibility = View.GONE
        binding.fabNoBlur.visibility = View.VISIBLE
    }
}
