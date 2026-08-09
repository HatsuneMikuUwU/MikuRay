package com.v2ray.ang.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qmdeve.blurview.widget.BlurView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

object WindowBlurUtils {

    private const val BLUR_OVERLAY_ID = 2100000000

    fun applyWindowBlur(window: Window?) {
        if (window == null) return

        val isBlurEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)
        if (!isBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
            return
        }

        try {
            val context = window.context
            val activity = context.getActivity() ?: return
            val decorView = activity.window?.decorView as? ViewGroup ?: return

            decorView.findViewById<View>(BLUR_OVERLAY_ID)?.let {
                decorView.removeView(it)
            }

            val blurView = BlurView(context, null).apply {
                id = BLUR_OVERLAY_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val blurRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS).toFloat()
                val blurRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
                setBlurRadius(blurRadius)
                setBlurRounds(blurRounds)
                setOverlayColor(Color.argb(120, 0, 0, 0))

                isClickable = false
                isFocusable = false
                elevation = 0f
                outlineProvider = null
            }

            decorView.addView(blurView)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    decorView.removeView(blurView)
                    window.decorView.removeOnAttachStateChangeListener(this)
                }
            })

        } catch (e: Exception) {
            e.printStackTrace()
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
        }
    }

    fun updateWindowBlur(window: Window?, radius: Float, rounds: Int) {
        if (window == null) return
        try {
            val activity = window.context.getActivity() ?: return
            val decorView = activity.window?.decorView as? ViewGroup ?: return
            val blurView = decorView.findViewById<BlurView>(BLUR_OVERLAY_ID) ?: return

            blurView.setBlurRadius(radius)
            blurView.setBlurRounds(rounds)

            blurView.invalidate()
            decorView.invalidate()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

tailrec fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

fun MaterialAlertDialogBuilder.showBlur(): androidx.appcompat.app.AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()
    return dialog
}

fun AlertDialog.Builder.showBlur(): AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()
    return dialog
}
