package com.v2ray.ang.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogMikurayPasswordBinding

/**
 * Prompts for a password + confirmation, used before encrypting a .mikuray export.
 * The dialog stays open (button click is intercepted manually) until the two fields
 * are non-empty and match, so users can't fat-finger a password they'll never get back.
 */
fun showMikuRayExportPasswordDialog(
    context: Context,
    onConfirm: (password: String) -> Unit
) {
    val binding = DialogMikurayPasswordBinding.inflate(LayoutInflater.from(context))
    binding.tvMikurayPasswordDesc.setText(R.string.mikuray_password_export_desc)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.mikuray_password_export_title)
        .setIcon(R.drawable.ic_lock_24dp)
        .setView(binding.root)
        .setPositiveButton(R.string.mikuray_password_confirm_button, null)
        .setNegativeButton(android.R.string.cancel, null)
        .showBlur()

    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val password = binding.etMikurayPassword.text.toString()
        val confirm = binding.etMikurayPasswordConfirm.text.toString()

        binding.etMikurayPassword.error = null
        binding.etMikurayPasswordConfirm.error = null

        when {
            password.isEmpty() -> {
                binding.etMikurayPassword.error = context.getString(R.string.mikuray_password_error_empty)
            }
            password != confirm -> {
                binding.etMikurayPasswordConfirm.error = context.getString(R.string.mikuray_password_error_mismatch)
            }
            else -> {
                dialog.dismiss()
                onConfirm(password)
            }
        }
    }
}

/** Prompts for a single password, used before decrypting a .mikuray file being imported. */
fun showMikuRayImportPasswordDialog(
    context: Context,
    onConfirm: (password: String) -> Unit
) {
    val binding = DialogMikurayPasswordBinding.inflate(LayoutInflater.from(context))
    binding.tvMikurayPasswordDesc.setText(R.string.mikuray_password_import_desc)
    binding.tilMikurayPasswordConfirm.visibility = View.GONE

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.mikuray_password_import_title)
        .setIcon(R.drawable.ic_unlock_24dp)
        .setView(binding.root)
        .setPositiveButton(R.string.mikuray_password_confirm_button, null)
        .setNegativeButton(android.R.string.cancel, null)
        .showBlur()

    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val password = binding.etMikurayPassword.text.toString()
        binding.etMikurayPassword.error = null

        if (password.isEmpty()) {
            binding.etMikurayPassword.error = context.getString(R.string.mikuray_password_error_empty)
        } else {
            dialog.dismiss()
            onConfirm(password)
        }
    }
}
