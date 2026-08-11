package com.v2ray.ang.util

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager

object SearchChipGradientController {

    data class ChipViews(
        val layoutWeatherChip: View,
        val ivWeatherIcon: ImageView,
        val tvWeatherTemp: TextView,
        val ivTotalTrafficIcon: ImageView,
        val tvTotalTraffic: TextView
    )

    fun isEnabled(): Boolean {
        val gradientSwitchOn = MmkvManager.decodeSettingsBool(AppConfig.PREF_SEARCH_CHIP_GRADIENT, false)
        val weatherEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)
        val totalTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP, false)
        return gradientSwitchOn && (weatherEnabled || totalTrafficEnabled)
    }

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        applyState(
            activity,
            ChipViews(
                layoutWeatherChip = binding.layoutWeatherChip,
                ivWeatherIcon = binding.ivWeatherIcon,
                tvWeatherTemp = binding.tvWeatherTemp,
                ivTotalTrafficIcon = binding.ivTotalTrafficIcon,
                tvTotalTraffic = binding.tvTotalTraffic
            )
        )
    }

    fun applyState(activity: AppCompatActivity, chip: ChipViews) {
        if (isEnabled()) applyGradientOn(activity, chip) else applyGradientOff(activity, chip)
    }

    private fun applyGradientOn(activity: AppCompatActivity, chip: ChipViews) {
        val colorStart = activity.getColorAttr("colorPrimary")
        val colorEnd = activity.getColorAttr("colorTertiary")
        val cornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16f, activity.resources.displayMetrics
        )
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colorStart, colorEnd)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
        }
        chip.layoutWeatherChip.background = RippleDrawable(
            ColorStateList.valueOf(activity.getColorAttr("android:attr/colorControlHighlight")),
            gradient,
            null
        )

        val tintList = ColorStateList.valueOf(activity.getColorAttr("colorOnPrimary"))
        ImageViewCompat.setImageTintList(chip.ivWeatherIcon, tintList)
        ImageViewCompat.setImageTintList(chip.ivTotalTrafficIcon, tintList)
        chip.tvWeatherTemp.setTextColor(tintList.defaultColor)
        chip.tvTotalTraffic.setTextColor(tintList.defaultColor)
    }

    private fun applyGradientOff(activity: AppCompatActivity, chip: ChipViews) {
        chip.layoutWeatherChip.setBackgroundResource(R.drawable.bg_weather_chip)

        val tintList = ColorStateList.valueOf(activity.getColorAttr("colorOnSurfaceVariant"))
        ImageViewCompat.setImageTintList(chip.ivWeatherIcon, tintList)
        ImageViewCompat.setImageTintList(chip.ivTotalTrafficIcon, tintList)
        chip.tvWeatherTemp.setTextColor(tintList.defaultColor)
        chip.tvTotalTraffic.setTextColor(tintList.defaultColor)
    }
}
