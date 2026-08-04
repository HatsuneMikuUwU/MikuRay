package com.v2ray.ang.helper

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.king.camera.scan.CameraScan
import com.v2ray.ang.ui.scanner.QrCaptureActivity

/**
 * Helper for scanning QR codes.
 *
 * This class encapsulates the logic for launching the QR code scanner directly
 * using the ZXingLite (ZXing + CameraScan) library and handling the scan result.
 */
class QRCodeScannerHelper(private val activity: AppCompatActivity) {
    private var scanCallback: ((String?) -> Unit)? = null

    private val scanLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(CameraScan.SCAN_RESULT)
        } else {
            null
        }
        scanCallback?.invoke(text)
        scanCallback = null
    }

    /**
     * Launch the QR code scanner camera.
     *
     * @param onResult Callback invoked with the scan result (null if cancelled or failed)
     */
    fun launch(onResult: (String?) -> Unit) {
        scanCallback = onResult
        scanLauncher.launch(Intent(activity, QrCaptureActivity::class.java))
    }
}
