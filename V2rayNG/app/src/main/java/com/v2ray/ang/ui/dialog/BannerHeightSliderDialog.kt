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

class BannerHeightSliderDialog @JvmOverloads constructor(
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

        val saved = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HOME_BANNER_HEIGHT,
            AppConfig.HOME_BANNER_HEIGHT_DEFAULT
        )
        val current = saved.coerceIn(
            AppConfig.HOME_BANNER_HEIGHT_MIN,
            AppConfig.HOME_BANNER_HEIGHT_MAX
        )

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_banner_height_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_banner_height)
        slider.value = current.toFloat()

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_home_banner_height_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newHeight = slider.value.toInt()
                MmkvManager.encodeSettings(AppConfig.PREF_HOME_BANNER_HEIGHT, newHeight)
                summary = context.getString(
                    R.string.pref_home_banner_height_summary_value, newHeight
                )
                val intent = android.content.Intent(
                    AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED
                )
                activity.sendBroadcast(intent)
            }
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            slider.value = AppConfig.HOME_BANNER_HEIGHT_DEFAULT.toFloat()
        }

        updateSummary()
    }

    private fun updateSummary() {
        val h = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HOME_BANNER_HEIGHT,
            AppConfig.HOME_BANNER_HEIGHT_DEFAULT
        )
        summary = context.getString(R.string.pref_home_banner_height_summary_value, h)
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }
}
