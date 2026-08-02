package com.v2ray.ang.ui.shortcut
import com.v2ray.ang.ui.base.HelperBaseActivity
import com.v2ray.ang.ui.main.MainActivity

import android.content.Intent
import android.os.Bundle
import com.v2ray.ang.R
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.handler.AngConfigManager

class ScScannerActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                val (count, countSub) = AngConfigManager.importBatchConfig(scanResult, "", false)

                if (count + countSub > 0) {
                    snackbarSuccess(R.string.toast_success, title = getString(R.string.title_alerter_success))
                } else {
                    snackbarError(R.string.toast_failure, title = getString(R.string.title_alerter_error))
                }

                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}