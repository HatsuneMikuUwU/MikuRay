package com.v2ray.ang.ui.bottomsheet

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.v2ray.ang.R
import com.v2ray.ang.ui.FontPickerAdapter
import com.v2ray.ang.util.WindowBlurUtils
import com.v2ray.ang.util.getColorAttr

class FontPickerBottomSheet(
    private val context: Context,
    private val selectedValue: String,
    private val onSelected: (value: String, label: String) -> Unit
) {
    fun show() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.uwu_layout_bottom_sheet_font_picker, null)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerFont)

        val values = context.resources.getStringArray(R.array.app_font_values)
        val labels = context.resources.getStringArray(R.array.app_font_entries)

        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = FontPickerAdapter(context, values, labels, selectedValue) { value, label ->
            onSelected(value, label)
            dialog.dismiss()
        }

        dialog.setContentView(view)

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val bgColor = context.getColorAttr(R.attr.colorBg)

        val bottomSheet = dialog.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        if (bottomSheet != null) {
            bottomSheet.backgroundTintList = ColorStateList.valueOf(bgColor)

            bottomSheet.clipToOutline = true

            ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { v, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val screenHeight = v.resources.displayMetrics.heightPixels
                val margin = (8 * v.resources.displayMetrics.density).toInt()

                dialog.behavior.maxHeight = screenHeight - statusBarInset - margin

                insets
            }
        }

        dialog.window?.let { window ->
            WindowBlurUtils.applyWindowBlur(window)

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            window.navigationBarColor = bgColor
        }

        dialog.show()
    }
}
