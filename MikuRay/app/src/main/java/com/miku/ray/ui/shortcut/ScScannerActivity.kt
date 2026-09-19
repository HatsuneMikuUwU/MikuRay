package com.miku.ray.ui.shortcut
import com.miku.ray.ui.base.HelperBaseActivity
import com.miku.ray.ui.main.MainActivity

import android.content.Intent
import android.os.Bundle
import com.miku.ray.R

class ScScannerActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (!scanResult.isNullOrEmpty()) {
                // MainActivity owns the actual import so it survives this activity finishing right after.
                startActivity(Intent(this@ScScannerActivity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_IMPORT_CONFIG, scanResult)
                })
            }
            finish()
        }
    }
}
