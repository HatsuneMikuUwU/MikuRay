package com.miku.ray.ui.backup

import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.ui.base.HelperBaseActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.util.showBlur
import com.tencent.mmkv.MMKV
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.miku.ray.BuildConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityBackupBinding
import com.miku.ray.databinding.DialogWebdavBinding
import com.miku.ray.dto.entities.WebDavConfig
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.WebDavManager
import com.miku.ray.util.BannerColorExtractor
import com.miku.ray.util.LogUtil
import com.miku.ray.util.ZipUtil
import com.miku.ray.util.showDeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseActivity() {
    private val binding by lazy { ActivityBackupBinding.inflate(layoutInflater) }

    private val config_backup_options: Array<out String> by lazy {
        resources.getStringArray(R.array.config_backup_options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.backupScrollContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_configuration_backup_restore), subtitle = getString(R.string.subtitle_backup))

        binding.layoutBackup.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_configuration_backup)
                .setIcon(RemixR.drawable.rmx_device_save_3_line)
                .setItems(config_backup_options) { _, which ->
                    when (which) {
                        0 -> backupViaLocal()
                        1 -> backupViaWebDav()
                    }
                }
                .showBlur()
        }

        binding.layoutShare.setOnClickListener {
            val ret = backupConfigurationToCache()
            if (ret.first) {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("application/zip")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(
                                Intent.EXTRA_STREAM,
                                FileProvider.getUriForFile(
                                    this, BuildConfig.APPLICATION_ID + ".cache", File(ret.second)
                                )
                            ), getString(R.string.title_configuration_share)
                    )
                )
            } else {
                snackbarError(
                    getString(R.string.title_configuration_share),
                    title = getString(R.string.title_alerter_error)
                )
            }
        }

        binding.layoutRestore.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_configuration_restore)
                .setIcon(RemixR.drawable.rmx_history_line)
                .setItems(config_backup_options) { _, which ->
                    when (which) {
                        0 -> restoreViaLocal()
                        1 -> restoreViaWebDav()
                    }
                }
                .showBlur()
        }

        binding.layoutProfileStorageCleanup.setOnClickListener {
            showDeleteConfirmDialog(
                context = this,
                titleRes = R.string.title_profile_storage_cleanup,
                messageRes = R.string.message_profile_storage_cleanup,
            ) {
                cleanupProfileStorage()
            }
        }

        binding.layoutWebdavConfigSetting.setOnClickListener {
            showWebDavSettingsDialog()
        }
    }

    private fun cleanupProfileStorage() {
        showLoading()

        lifecycleScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    MmkvManager.removeOrphanedServerProfiles()
                }
                if (removed == null) {
                    snackbarError(
                        getString(R.string.toast_profile_storage_cleanup_skipped),
                        title = getString(R.string.title_alerter_error)
                    )
                } else {
                    snackbarSuccess(
                        getString(R.string.toast_profile_storage_cleanup, removed),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to clean up profile storage", e)
                snackbarError(
                    getString(R.string.toast_failure),
                    title = getString(R.string.title_alerter_error)
                )
            } finally {
                hideLoading()
            }
        }
    }

    private fun backupConfigurationToCache(): Pair<Boolean, String> {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormatted}"
        val backupDir = this.cacheDir.absolutePath + "/$folderName"
        val outputZipFilePath = "${this.cacheDir.absolutePath}/$folderName.zip"

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) {
            return Pair(false, "")
        }

        backupBannerImages(backupDir)

        backupCustomFont(backupDir)

        return if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            Pair(true, outputZipFilePath)
        } else {
            Pair(false, "")
        }
    }

    private fun backupBannerImages(backupDir: String) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_SELECTED_BANNER_URI,
            AppConfig.PREF_CUSTOM_THEME_BANNER_URI,
        )
        val bannersDir = java.io.File(backupDir, "banners").also { it.mkdirs() }
        for (key in bannerKeys) {
            val uriString = MmkvManager.decodeSettingsString(key) ?: continue
            if (uriString.isBlank()) continue
            try {
                val uri = Uri.parse(uriString)
                val srcFile = if (uri.scheme == "file") {
                    java.io.File(uri.path!!)
                } else {
                    val tmp = java.io.File(cacheDir, "banner_backup_tmp_${key}.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    tmp
                }
                if (srcFile.exists()) {
                    srcFile.copyTo(java.io.File(bannersDir, "$key.jpg"), overwrite = true)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to backup banner for key $key", e)
            }
        }
    }

    private fun backupCustomFont(backupDir: String) {
        val srcFile = com.miku.ray.util.CustomFontManager.getFontFile(this) ?: return
        try {
            val fontsDir = java.io.File(backupDir, "fonts").also { it.mkdirs() }
            srcFile.copyTo(java.io.File(fontsDir, srcFile.name), overwrite = true)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to backup custom font", e)
        }
    }

    private suspend fun restoreConfiguration(zipFile: File): Boolean = withContext(Dispatchers.IO) {
        val backupDir = File(cacheDir, "restore_${System.nanoTime()}")
        try {
            if (!ZipUtil.unzipToFolder(zipFile, backupDir.absolutePath)) {
                return@withContext false
            }

            val count = MMKV.restoreAllFromDirectory(backupDir.absolutePath)
            SettingsChangeManager.makeSetupGroupTab()
            SettingsChangeManager.makeRestartService()

            restoreBannerImages(backupDir.absolutePath)
            SettingsManager.preloadAllBanners(this@BackupActivity)
            restoreCustomFont(backupDir.absolutePath)

            val restoredHomeBannerUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
            if (!restoredHomeBannerUri.isNullOrBlank()) {
                lifecycleScope.launch {
                    BannerColorExtractor.extractAndSave(this@BackupActivity, Uri.parse(restoredHomeBannerUri))
                }
            }

            SettingsManager.initApp(this@BackupActivity)
            count > 0
        } finally {
            backupDir.deleteRecursively()
        }
    }

    private fun restoreCustomFont(backupDir: String) {
        val fontsDir = java.io.File(backupDir, "fonts")
        val srcFile = fontsDir.takeIf { it.exists() }?.listFiles()?.firstOrNull { it.isFile }

        if (srcFile == null) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            }
            return
        }

        val existingDisplayName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT_CUSTOM_NAME)
        val restored = com.miku.ray.util.CustomFontManager.restoreFontFile(this, srcFile, existingDisplayName ?: srcFile.name)
        if (restored == null) {
            LogUtil.e(AppConfig.TAG, "Restored custom font file was invalid, falling back to default")
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            }
        }
    }

    private fun restoreBannerImages(backupDir: String) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_SELECTED_BANNER_URI,
        )
        val bannersDir = java.io.File(backupDir, "banners")
        if (!bannersDir.exists()) return

        for (key in bannerKeys) {
            val srcFile = java.io.File(bannersDir, "$key.jpg")
            if (!srcFile.exists()) {
                MmkvManager.encodeSettings(key, "")
                continue
            }
            try {
                val bannersOutDir = java.io.File(filesDir, "banners").apply { mkdirs() }
                val destFile = java.io.File(bannersOutDir, "${key}_${System.currentTimeMillis()}.jpg")
                srcFile.copyTo(destFile, overwrite = true)
                MmkvManager.encodeSettings(key, Uri.fromFile(destFile).toString())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to restore banner for key $key", e)
                MmkvManager.encodeSettings(key, "")
            }
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val targetFile = File(cacheDir, "restore_download_${System.nanoTime()}.zip")
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        targetFile.outputStream().use { fileOut ->
                            input?.copyTo(fileOut)
                        }
                    }
                    val restored = restoreConfiguration(targetFile)
                    withContext(Dispatchers.Main) {
                        if (restored) {
                            snackbarSuccess(
                                getString(R.string.title_configuration_restore),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            snackbarError(
                                getString(R.string.title_configuration_restore),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } finally {
                    targetFile.delete()
                }
            }
        }
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                try {
                    val ret = backupConfigurationToCache()
                    if (ret.first) {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            File(ret.second).inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        File(ret.second).delete()
                        snackbarSuccess(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to backup configuration", e)
                    snackbarError(
                        getString(R.string.title_configuration_backup),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            }
        }
    }

    private fun restoreViaLocal() {
        showFileChooser()
    }

    private fun backupViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            snackbarError(
                getString(R.string.title_webdav_config_setting_unknown),
                title = getString(R.string.title_alerter_error)
            )
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val ret = backupConfigurationToCache()
                if (!ret.first) {
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    return@launch
                }

                tempFile = File(ret.second)
                WebDavManager.init(saved)

                val ok = try {
                    WebDavManager.uploadFile(tempFile, WEBDAV_BACKUP_FILE_NAME)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "WebDAV upload error", e)
                    false
                }

                withContext(Dispatchers.Main) {
                    if (ok) {
                        snackbarSuccess(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV backup error", e)
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.title_configuration_backup),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            } finally {
                try {
                    tempFile?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun restoreViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            snackbarError(
                getString(R.string.title_webdav_config_setting_unknown),
                title = getString(R.string.title_alerter_error)
            )
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var target: File? = null
            try {
                target = File(cacheDir, "download_${System.currentTimeMillis()}.zip")
                WebDavManager.init(saved)
                val ok = WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    return@launch
                }

                val restored = restoreConfiguration(target)
                withContext(Dispatchers.Main) {
                    if (restored) {
                        snackbarSuccess(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        snackbarError(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.title_configuration_restore),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            } finally {
                try {
                    target?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun showWebDavSettingsDialog() {
        val dialogBinding = DialogWebdavBinding.inflate(layoutInflater)

        MmkvManager.decodeWebDavConfig()?.let { cfg ->
            dialogBinding.etWebdavUrl.setText(cfg.baseUrl)
            dialogBinding.etWebdavUser.setText(cfg.username ?: "")
            dialogBinding.etWebdavPass.setText(cfg.password ?: "")
            dialogBinding.etWebdavRemotePath.setText(cfg.remoteBasePath)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_webdav_config_setting)
            .setIcon(RemixR.drawable.rmx_cloud_line)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.menu_item_save_config) { _, _ ->
                val url = dialogBinding.etWebdavUrl.text.toString().trim()
                val user = dialogBinding.etWebdavUser.text.toString().trim().ifEmpty { null }
                val pass = dialogBinding.etWebdavPass.text.toString()
                val remotePath = dialogBinding.etWebdavRemotePath.text.toString().trim().ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
                val cfg = WebDavConfig(baseUrl = url, username = user, password = pass, remoteBasePath = remotePath)
                MmkvManager.encodeWebDavConfig(cfg)

                snackbarSuccess(
                    getString(R.string.title_webdav_config_setting),
                    title = getString(R.string.title_alerter_success)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
    }
}
