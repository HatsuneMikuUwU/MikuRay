package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityNoneBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import com.v2ray.ang.util.showBlur

class ScannerActivity : HelperBaseActivity() {
    private val binding by lazy { ActivityNoneBinding.inflate(layoutInflater) }

    private val scanQrCode = registerForActivityResult(ScanCustomCode(), ::handleResult)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       
        setContentView(binding.root)

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_START_SCAN_IMMEDIATE)) {
            launchScan()
        } else {
            showSelectionDialog()
        }
    }

    private fun showSelectionDialog() {
        val options = arrayOf(
            getString(R.string.scan_code),
            getString(R.string.select_photo)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_item_import_config_qrcode)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchScan()
                    1 -> showFileChooser()
                }
            }
            .setOnCancelListener {
                finish() 
            }
            .showBlur()
    }

    private fun launchScan() {
        scanQrCode.launch(
            ScannerConfig.build {
                setHapticSuccessFeedback(true)
                setShowTorchToggle(true)
                setShowCloseButton(true)
                setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
            }
        )
    }

    private fun handleResult(result: QRResult) {
        if (result is QRResult.QRSuccess) {
            finished(result.content.rawValue.orEmpty())
        } else {
            showSelectionDialog()
        }
    }

    private fun finished(text: String) {
        val intent = Intent()
        intent.putExtra("SCAN_RESULT", text)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun showFileChooser() {
        launchFileChooser("image/*") { uri ->
            if (uri == null) {
                showSelectionDialog()
                return@launchFileChooser
            }
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                if (text.isNullOrEmpty()) {
                    toast(R.string.toast_decoding_failed)
                    showSelectionDialog()
                } else {
                    finished(text)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to decode QR code from file", e)
                toast(R.string.toast_decoding_failed)
                showSelectionDialog()
            }
        }
    }
}
