package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.FontSizeController
import com.v2ray.ang.util.WindowBlurUtils
import kotlin.math.roundToInt

class FontSizeSliderDialog @JvmOverloads constructor(
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

    private fun formatPercent(scale: Float) = "${(scale * 100f).roundToInt()}%"

    override fun onClick() {
        val activity = context.findActivity() ?: return

        val savedScale = MmkvManager.decodeSettingsFloat(AppConfig.PREF_APP_FONT_SIZE, AppConfig.FONT_SIZE_DEFAULT)
        val currentScale = if (savedScale > 0f) savedScale else AppConfig.FONT_SIZE_DEFAULT

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_font_size_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_font_size)
        val preview = dialogView.findViewById<TextView>(R.id.text_font_size_preview)

        slider.value = currentScale.coerceIn(AppConfig.FONT_SIZE_MIN, AppConfig.FONT_SIZE_MAX)
        preview.text = context.getString(R.string.pref_font_size_preview_format, formatPercent(slider.value))
        preview.textSize = 17f * slider.value

        slider.addOnChangeListener { _, value, _ ->
            preview.text = context.getString(R.string.pref_font_size_preview_format, formatPercent(value))
            preview.textSize = 17f * value
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_font_size)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clamped = slider.value.coerceIn(AppConfig.FONT_SIZE_MIN, AppConfig.FONT_SIZE_MAX)
                val valueToSave = if (clamped == AppConfig.FONT_SIZE_DEFAULT) AppConfig.FONT_SIZE_DEFAULT else clamped

                MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_SIZE, valueToSave)
                summary = formatPercent(valueToSave)

                FontSizeController.applyFontScale(activity.applicationContext, valueToSave)

                activity.recreate()
            }
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            slider.value = AppConfig.FONT_SIZE_DEFAULT
        }
    }
}
