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
        val radius = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_RADIUS,
            AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
        ).toFloat()
        val rounds = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_ROUNDS,
            AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS
        )
        binding.blurBottomStatus.setBlurRadius(radius)
        binding.blurBottomStatus.setBlurRounds(rounds)
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
        binding.fabNoBlur.visibility = View.GONE
    }

    private fun applyBlurOff(activity: AppCompatActivity, binding: ActivityMainBinding) {
        binding.blurBottomStatus.visibility = View.GONE
        binding.cardBottomStatus.setCardBackgroundColor(
            activity.getColorAttr("colorPrimary")
        )
        val textColorOnPrimary = activity.getColorAttr("colorOnPrimary")
        binding.tvIpState.setTextColor(textColorOnPrimary)
        binding.tvIpState.alpha = 0.8f 
        binding.tvTestState.setTextColor(textColorOnPrimary)
        binding.fab.visibility = View.GONE
        binding.fabNoBlur.visibility = View.VISIBLE
    }
}
