package com.v2ray.ang.ui.bottomsheet

import androidx.core.view.ViewCompat
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.v2ray.ang.util.WindowBlurUtils
import com.v2ray.ang.R

abstract class BaseBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return

        sheetDialog.window?.let { window ->
            WindowBlurUtils.applyWindowBlur(window)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        
        val bottomSheet = sheetDialog.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.clipToOutline = true

        sheetDialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            
            val screenHeight = view.resources.displayMetrics.heightPixels
            val baseSizePx = (8 * view.resources.displayMetrics.density).toInt() 

            sheetDialog.behavior.maxHeight = screenHeight - statusBarInset - baseSizePx

            view.findViewById<android.view.View>(R.id.bottom_sheet)?.updatePadding(
                bottom = baseSizePx + navBarInset
            )

            insets
        }
    }
}