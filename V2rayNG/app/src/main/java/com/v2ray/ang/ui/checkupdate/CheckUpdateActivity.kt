package com.v2ray.ang.ui.checkupdate

import com.v2ray.ang.ui.base.BaseActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.util.showBlur
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityCheckUpdateBinding
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.extension.applyEdgeToEdgeListInsets
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

class CheckUpdateActivity : BaseActivity() {

    private val binding by lazy { ActivityCheckUpdateBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.checkUpdateScrollContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.update_check_for_update), subtitle = getString(R.string.subtitle_check_update))

        binding.layoutCheckUpdate.setOnClickListener {
            checkForUpdates(binding.checkPreRelease.isChecked)
        }

        binding.checkPreRelease.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, isChecked)
        }
        binding.checkPreRelease.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

        "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})".also {
            binding.tvVersion.text = it
        }

        checkForUpdates(binding.checkPreRelease.isChecked)
    }

    private fun checkForUpdates(includePreRelease: Boolean) {
        showLoading()

        lifecycleScope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
                if (result.hasUpdate) {
                    showUpdateDialog(result)
                } else {
                    snackbarSuccess(
                        getString(R.string.update_already_latest_version),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates: ${e.message}")
                snackbarError(
                    e.message ?: getString(R.string.update_check_for_update),
                    title = getString(R.string.title_alerter_error)
                )
            } finally {
                hideLoading()
            }
        }
    }

    private fun showUpdateDialog(result: CheckUpdateResult) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_new_version_found, result.latestVersion))
            .setIcon(R.drawable.ic_check_update_24dp)
            .setMessage(result.releaseNotes)
            .setPositiveButton(R.string.update_now) { _, _ ->
                result.downloadUrl?.let {
                    Utils.openUri(this, it)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
    }
}
