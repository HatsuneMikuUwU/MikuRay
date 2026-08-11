package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.WindowBlurUtils

class BlurBottomIntensityDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun onClick() {
        val originalRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS)
        val originalRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_radius)
        val sliderRounds = dialogView.findViewById<Slider>(R.id.slider_blur_rounds)

        sliderRadius.value = originalRadius.toFloat().coerceIn(2f, 100f)
        sliderRounds.value = originalRounds.toFloat().coerceIn(1f, 15f)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_blur_bottom_intensity)
            .setIcon(R.drawable.ic_baseline_blur_on_24)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val radius = sliderRadius.value.toInt()
            val rounds = sliderRounds.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, radius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, rounds)
            updateSummary(radius, rounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, originalRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, originalRounds)
            updateSummary(originalRadius, originalRounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val defaultRadius = AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
            val defaultRounds = AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS

            sliderRadius.value = defaultRadius.toFloat()
            sliderRounds.value = defaultRounds.toFloat()
            WindowBlurUtils.updateWindowBlur(dialog.window, defaultRadius.toFloat(), defaultRounds)

            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, defaultRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, defaultRounds)
            updateSummary(defaultRadius, defaultRounds)

            dialog.dismiss()
        }
    }

    fun updateSummary(radius: Int, rounds: Int) {
        summary = context.getString(R.string.summary_blur_intensity_value, radius, rounds)
    }
}
