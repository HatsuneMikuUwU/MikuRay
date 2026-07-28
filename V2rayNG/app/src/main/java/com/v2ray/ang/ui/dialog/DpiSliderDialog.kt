package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.DPIController
import com.v2ray.ang.util.WindowBlurUtils

class DpiSliderDialog @JvmOverloads constructor(
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
        val activity = context.findActivity() ?: return

        val systemDpi = Resources.getSystem().displayMetrics.densityDpi
        
        val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val currentDpi = if (savedDpi > 0) savedDpi else systemDpi

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_dpi_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_dpi)

        slider.value = currentDpi.toFloat().coerceIn(160f, 640f)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_custom_dpi)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clamped = slider.value.toInt()
                val valueToSave = if (clamped == systemDpi) 0 else clamped
                
                MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_DPI, valueToSave)
                summary = if (valueToSave == 0) systemDpi.toString() else clamped.toString()
                
                DPIController.applyDpi(activity.applicationContext, clamped)
                
                activity.recreate()
            }
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            slider.value = systemDpi.toFloat().coerceIn(160f, 640f)

            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_DPI, 0)
            summary = systemDpi.toString()

            DPIController.applyDpi(activity.applicationContext, systemDpi)

            dialog.dismiss()
            activity.recreate()
        }
    }
}
