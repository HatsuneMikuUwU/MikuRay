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
    private var glassFillBaseColor: Int = 0

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        if (isEnabled()) applyBlurOn(activity, binding)
        else applyBlurOff(activity, binding)
    }

    fun updateRadius(radius: Float) {
        blurViewReference?.get()?.apply {
            setBlurRadius(radius.coerceIn(1f, 50f))
            invalidate()
        }
    }

    fun updateAlpha(alphaPercent: Float) {
        val alpha = alphaPercentToInt(alphaPercent)
        glassDrawableReference?.get()?.apply {
            setColor(withAlpha(glassFillBaseColor, alpha))
        }
        blurViewReference?.get()?.invalidate()
    }

    private fun alphaPercentToInt(percent: Float): Int =
        (percent.coerceIn(0f, 100f) / 100f * 255f).toInt().coerceIn(0, 255)

    private fun applyBlurOn(activity: AppCompatActivity, binding: ActivityMainBinding) {
        val radius = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_RADIUS,
            AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
        ).toFloat().coerceIn(1f, 50f)
        val alphaPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_ALPHA,
            AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA
        ).toFloat()

        glassFillBaseColor = activity.getColorAttr("colorSurfaceContainerHigh")
        val glassFillColor = withAlpha(glassFillBaseColor, alphaPercentToInt(alphaPercent))
        val glassStrokeColor = withAlpha(activity.getColorAttr("colorOutline"), 0x90)
        val glassDrawable = GradientDrawable().apply {
            setColor(glassFillColor)
            setCornerRadius(28f * activity.resources.displayMetrics.density)
            setStroke(3, glassStrokeColor)
        }
        binding.blurBottomStatus.apply {
            background = glassDrawable
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setupWith(binding.mainBlurTarget)
                .setFrameClearDrawable(activity.window.decorView.background)
                .setBlurRadius(radius)
                .setOverlayColor(Color.TRANSPARENT)
        }
        binding.blurBottomStatus.visibility = View.VISIBLE
        blurViewReference = WeakReference(binding.blurBottomStatus)
        glassDrawableReference = WeakReference(glassDrawable)
        binding.cardBottomStatus.setCardBackgroundColor(Color.TRANSPARENT)
        binding.tvIpState.setTextColor(activity.getColorAttr("colorOnSurfaceVariant"))
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
        binding.blurBottomStatus.apply {
            visibility = View.GONE
            clipToOutline = false
            background = null
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
