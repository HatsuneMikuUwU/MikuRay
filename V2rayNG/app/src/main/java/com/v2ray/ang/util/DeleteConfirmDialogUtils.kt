package com.v2ray.ang.util


import com.miku.ray.remixicon.R as RemixR
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogDeleteConfirmBinding

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
    binding.positiveButton.setText(positiveTextRes)
    binding.negativeButton.setText(negativeTextRes)

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .create()
    WindowBlurUtils.applyWindowBlur(dialog.window)

    binding.positiveButton.setOnClickListener {
        dialog.dismiss()
        onConfirm()
    }
    binding.negativeButton.setOnClickListener {
        dialog.dismiss()
    }
    dialog.show()
}
