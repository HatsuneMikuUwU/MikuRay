package com.miku.ray.ui.urlscheme

import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.main.MainActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityLogcatBinding
import com.miku.ray.extension.snackbarError
import com.miku.ray.util.LogUtil
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val mainIntent = Intent(this, MainActivity::class.java)
        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        getStringExtra(Intent.EXTRA_TEXT)?.let {
                            resolveImportConfig(it, null)?.let { config ->
                                mainIntent.putExtra(MainActivity.EXTRA_IMPORT_CONFIG, config)
                            }
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config", "install-sub" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url").orEmpty()
                            resolveImportConfig(shareUrl, uri?.fragment)?.let { config ->
                                mainIntent.putExtra(MainActivity.EXTRA_IMPORT_CONFIG, config)
                            }
                        }

                        else -> {
                            snackbarError(R.string.toast_failure, title = getString(R.string.title_alerter_error))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
        }

        // MainActivity owns the actual import so it survives this activity finishing right after.
        startActivity(mainIntent)
        finish()
    }

    /** Decodes the shared/deep-linked URL and reattaches its remarks fragment if missing. */
    private fun resolveImportConfig(uriString: String?, fragment: String?): String? {
        if (uriString.isNullOrEmpty()) {
            return null
        }
        LogUtil.i(AppConfig.TAG, uriString)

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl) ?: return null
        if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
            decodedUrl += "#${fragment}"
        }
        LogUtil.i(AppConfig.TAG, decodedUrl)
        return decodedUrl
    }
}
