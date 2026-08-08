package com.v2ray.ang.util

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.databinding.DialogTotalTrafficDetailBinding
import com.v2ray.ang.handler.MmkvManager

/**
 * Shows a Material 3 styled dialog breaking down the combined upload and
 * download traffic totals across all profiles.
 *
 * @param context The context used to inflate and show the dialog.
 */
fun showTotalTrafficDetailDialog(context: Context) {
    val (uploadBytes, downloadBytes) = MmkvManager.getTotalTrafficDetail() ?: return

    val binding = DialogTotalTrafficDetailBinding.inflate(LayoutInflater.from(context))
    binding.tvUploadValue.text = MmkvManager.formatTrafficBytesPublic(uploadBytes)
    binding.tvDownloadValue.text = MmkvManager.formatTrafficBytesPublic(downloadBytes)
    binding.tvCombinedValue.text = MmkvManager.formatTrafficBytesPublic(uploadBytes + downloadBytes)

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .create()
    WindowBlurUtils.applyWindowBlur(dialog.window)

    binding.btnClose.setOnClickListener {
        dialog.dismiss()
    }
    dialog.show()
}
