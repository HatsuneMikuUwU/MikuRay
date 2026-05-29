package com.v2ray.ang.util

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager

object BlurBottomStatusController {

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        if (isEnabled()) applyBlurOn(activity, binding)
        else applyBlurOff(activity, binding)
    }

    private fun applyBlurOn(activity: AppCompatActivity, binding: ActivityMainBinding) {
        val corner     = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_CORNER,            AppConfig.DEFAULT_BLUR_BOTTOM_CORNER)
        val refrHeight = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_HEIGHT, AppConfig.DEFAULT_BLUR_BOTTOM_REFRACTION_HEIGHT)
        val refrOffset = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_OFFSET, AppConfig.DEFAULT_BLUR_BOTTOM_REFRACTION_OFFSET)
        val blurRadius = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_BLUR_RADIUS,       AppConfig.DEFAULT_BLUR_BOTTOM_BLUR_RADIUS)
        val dispersion = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_DISPERSION,        AppConfig.DEFAULT_BLUR_BOTTOM_DISPERSION)

        val density = activity.resources.displayMetrics.density
        binding.blurBottomStatus.setCornerRadius(corner * density)
        binding.blurBottomStatus.setRefractionHeight(refrHeight * density)
        binding.blurBottomStatus.setRefractionOffset(refrOffset * density)
        binding.blurBottomStatus.setBlurRadius(blurRadius)
        binding.blurBottomStatus.setDispersion(dispersion)
        binding.blurBottomStatus.invalidate()

        binding.blurBottomStatus.visibility = View.VISIBLE
        binding.cardBottomStatus.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.tvIpState.setTextColor(
            activity.getColorAttr("colorOnSurfaceVariant")
        )
        binding.tvTestState.setTextColor(
            activity.getColorAttr("colorOnSurface")
        )
        binding.fab.visibility = View.VISIBLE
    }

    private fun applyBlurOff(activity: AppCompatActivity, binding: ActivityMainBinding) {
        binding.blurBottomStatus.visibility = View.GONE
        binding.cardBottomStatus.setCardBackgroundColor(
            activity.getColorAttr("colorPrimary")
        )
        val textColorInverse = activity.getColorAttr("android:textColorPrimaryInverse")
        binding.tvIpState.setTextColor(textColorInverse)
        binding.tvTestState.setTextColor(textColorInverse)
        binding.fab.visibility = View.VISIBLE
    }
}
