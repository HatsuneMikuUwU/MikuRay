package com.v2ray.ang.util

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogDeleteConfirmBinding
import com.miku.ray.remixicon.R as RemixR

fun showDeleteConfirmDialog(
    context: Context,
    @StringRes messageRes: Int,
    @StringRes titleRes: Int = R.string.del_config_comfirm,
    @DrawableRes iconRes: Int = RemixR.drawable.rmx_system_alert_line,
    @StringRes positiveTextRes: Int = R.string.del_button_dialog_comfirm,
    @StringRes negativeTextRes: Int = android.R.string.cancel,
    onConfirm: () -> Unit
) {
    val binding = DialogDeleteConfirmBinding.inflate(LayoutInflater.from(context))
    binding.dialogIcon.setImageResource(iconRes)
    binding.dialogTitle.setText(titleRes)
    binding.dialogMessage.setText(messageRes)

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setPositiveButton(positiveTextRes) { _, _ ->
            onConfirm()
        }
        .setNegativeButton(negativeTextRes, null)
        .create()

    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()
}
