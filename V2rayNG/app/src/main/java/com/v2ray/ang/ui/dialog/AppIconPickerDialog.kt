package com.v2ray.ang.ui.dialog

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.Preference
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.ui.AppIconPickerAdapter
import com.v2ray.ang.util.IconSwitcher
import com.v2ray.ang.util.WindowBlurUtils

class AppIconPickerDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    fun refreshSummary() {
        val current = IconSwitcher.currentVariant()
        val icons = AppIconPickerAdapter.icons(context)
        summary = icons.firstOrNull { it.first == current }?.third ?: icons.first().third
    }

    override fun onClick() {
        val current = IconSwitcher.currentVariant()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_icon_picker, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rv_app_icons)

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val adapter = AppIconPickerAdapter(
            context = context,
            selectedValue = current,
            onSelect = { value ->
                IconSwitcher.applyVariant(context.applicationContext, value)
                summary = AppIconPickerAdapter.icons(context).firstOrNull { it.first == value }?.third ?: value
                callChangeListener(value)
                dialog.dismiss()
            }
        )
        rv.layoutManager = GridLayoutManager(context, 3)
        rv.adapter = adapter

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.title_pref_app_icon)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()
    }
}
