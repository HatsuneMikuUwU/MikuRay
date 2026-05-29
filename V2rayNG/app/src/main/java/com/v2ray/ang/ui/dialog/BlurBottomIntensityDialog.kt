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
        val origCorner     = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_CORNER,            AppConfig.DEFAULT_BLUR_BOTTOM_CORNER)
        val origRefrHeight = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_HEIGHT, AppConfig.DEFAULT_BLUR_BOTTOM_REFRACTION_HEIGHT)
        val origRefrOffset = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_OFFSET, AppConfig.DEFAULT_BLUR_BOTTOM_REFRACTION_OFFSET)
        val origBlurRadius = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_BLUR_RADIUS,       AppConfig.DEFAULT_BLUR_BOTTOM_BLUR_RADIUS)
        val origDispersion = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_DISPERSION,        AppConfig.DEFAULT_BLUR_BOTTOM_DISPERSION)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_liquid_glass_intensity, null)

        val sliderCorner     = dialogView.findViewById<Slider>(R.id.slider_lg_corner)
        val sliderRefrHeight = dialogView.findViewById<Slider>(R.id.slider_lg_refraction_height)
        val sliderRefrOffset = dialogView.findViewById<Slider>(R.id.slider_lg_refraction_offset)
        val sliderBlurRadius = dialogView.findViewById<Slider>(R.id.slider_lg_blur_radius)
        val sliderDispersion = dialogView.findViewById<Slider>(R.id.slider_lg_dispersion)

        sliderCorner.value     = origCorner.coerceIn(0f, 99f)
        sliderRefrHeight.value = origRefrHeight.coerceIn(12f, 50f)
        sliderRefrOffset.value = origRefrOffset.coerceIn(20f, 120f)
        sliderBlurRadius.value = origBlurRadius.coerceIn(0f, 50f)
        sliderDispersion.value = origDispersion.coerceIn(0f, 1f)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_blur_bottom_intensity)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val corner     = sliderCorner.value
            val refrHeight = sliderRefrHeight.value
            val refrOffset = sliderRefrOffset.value
            val blurRadius = sliderBlurRadius.value
            val dispersion = sliderDispersion.value
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_CORNER,            corner)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_HEIGHT, refrHeight)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_OFFSET, refrOffset)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_BLUR_RADIUS,       blurRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_DISPERSION,        dispersion)
            updateSummary(corner, refrHeight, refrOffset, blurRadius, dispersion)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_CORNER,            origCorner)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_HEIGHT, origRefrHeight)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_REFRACTION_OFFSET, origRefrOffset)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_BLUR_RADIUS,       origBlurRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_DISPERSION,        origDispersion)
            updateSummary(origCorner, origRefrHeight, origRefrOffset, origBlurRadius, origDispersion)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            sliderCorner.value     = AppConfig.DEFAULT_BLUR_BOTTOM_CORNER
            sliderRefrHeight.value = AppConfig.DEFAULT_BLUR_BOTTOM_REFRACTION_HEIGHT
            sliderRefrOffset.value = AppConfig.DEFAULT_BLUR_BOTTOM_REFRACTION_OFFSET
            sliderBlurRadius.value = AppConfig.DEFAULT_BLUR_BOTTOM_BLUR_RADIUS
            sliderDispersion.value = AppConfig.DEFAULT_BLUR_BOTTOM_DISPERSION
        }
    }

    fun updateSummary(corner: Float, refrHeight: Float, refrOffset: Float, blurRadius: Float, dispersion: Float) {
        summary = context.getString(
            R.string.summary_lg_intensity_value,
            corner.toInt(), refrHeight.toInt(), refrOffset.toInt(), blurRadius.toInt(), dispersion
        )
    }
}
