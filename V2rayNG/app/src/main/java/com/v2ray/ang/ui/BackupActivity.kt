package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.util.showBlur
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityBackupBinding
import com.v2ray.ang.databinding.DialogWebdavBinding
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.extension.alertError
import com.v2ray.ang.extension.alertSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.util.BannerColorExtractor
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.ZipUtil
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

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_configuration_backup_restore))

        binding.layoutBackup.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_configuration_backup)
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
                alertError(
                    getString(R.string.title_configuration_share), 
                    title = getString(R.string.title_alerter_error)
                )
            }
        }

        binding.layoutRestore.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_configuration_restore)
                .setItems(config_backup_options) { _, which ->
                    when (which) {
                        0 -> restoreViaLocal()
                        1 -> restoreViaWebDav()
                    }
                }
                .showBlur()
        }

        binding.layoutWebdavConfigSetting.setOnClickListener {
            showWebDavSettingsDialog()
        }
    }

    /**
     * Backup configuration to cache directory.
     * Returns Pair<success, zipFilePath>
     */
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

        // Backup custom banner image files alongside MMKV data
        backupBannerImages(backupDir)

        return if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            Pair(true, outputZipFilePath)
        } else {
            Pair(false, "")
        }
    }

    /**
     * Copy banner image files into the backup directory.
     * Each banner is saved as "banners/<key>.jpg" so restore can find them by key.
     */
    private fun backupBannerImages(backupDir: String) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
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

    private fun restoreConfiguration(zipFile: File): Boolean {
        val backupDir = this.cacheDir.absolutePath + "/${System.currentTimeMillis()}"

        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
            return false
        }

        val count = MMKV.restoreAllFromDirectory(backupDir)
        SettingsChangeManager.makeSetupGroupTab()
        SettingsChangeManager.makeRestartService()

        // Restore custom banner image files and fix their paths in MMKV
        restoreBannerImages(backupDir)

        // Re-extract banner color from restored home banner image if present
        val restoredHomeBannerUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
        if (!restoredHomeBannerUri.isNullOrBlank()) {
            lifecycleScope.launch {
                BannerColorExtractor.extractAndSave(this@BackupActivity, Uri.parse(restoredHomeBannerUri))
            }
        }

        SettingsManager.initApp(this)
        return count > 0
    }

    /**
     * Copy banner image files from the backup dir into cacheDir,
     * then update each URI key in MMKV to the new path.
     */
    private fun restoreBannerImages(backupDir: String) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
        )
        val bannersDir = java.io.File(backupDir, "banners")
        if (!bannersDir.exists()) return

        for (key in bannerKeys) {
            val srcFile = java.io.File(bannersDir, "$key.jpg")
            if (!srcFile.exists()) {
                // No backup for this banner — clear the stale URI so we don't point to a missing file
                MmkvManager.encodeSettings(key, "")
                continue
            }
            try {
                val destFile = java.io.File(cacheDir, "${key}_${System.currentTimeMillis()}.jpg")
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
            try {
                val targetFile =
                    File(this.cacheDir.absolutePath, "${System.currentTimeMillis()}.zip")
                contentResolver.openInputStream(uri).use { input ->
                    targetFile.outputStream().use { fileOut ->
                        input?.copyTo(fileOut)
                    }
                }
                if (restoreConfiguration(targetFile)) {
                    alertSuccess(
                        getString(R.string.title_configuration_restore), 
                        title = getString(R.string.title_alerter_success)
                    )
                } else {
                    alertError(
                        getString(R.string.title_configuration_restore), 
                        title = getString(R.string.title_alerter_error)
                    )
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                alertError(
                    getString(R.string.title_configuration_restore), 
                    title = getString(R.string.title_alerter_error)
                )
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
                        // Copy the cached zip file to user-selected location
                        contentResolver.openOutputStream(uri)?.use { output ->
                            File(ret.second).inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        // Clean up cache file
                        File(ret.second).delete()
                        alertSuccess(
                            getString(R.string.title_configuration_backup), 
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        alertError(
                            getString(R.string.title_configuration_backup), 
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to backup configuration", e)
                    alertError(
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
            alertError(
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
                        alertError(
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
                        alertSuccess(
                            getString(R.string.title_configuration_backup), 
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        alertError(
                            getString(R.string.title_configuration_backup), 
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV backup error", e)
                withContext(Dispatchers.Main) {
                    alertError(
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
            alertError(
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
                        alertError(
                            getString(R.string.title_configuration_restore), 
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    return@launch
                }

                val restored = restoreConfiguration(target)
                withContext(Dispatchers.Main) {
                    if (restored) {
                        alertSuccess(
                            getString(R.string.title_configuration_restore), 
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        alertError(
                            getString(R.string.title_configuration_restore), 
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                withContext(Dispatchers.Main) { 
                    alertError(
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
            dialogBinding.etWebdavRemotePath.setText(cfg.remoteBasePath ?: "/")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_webdav_config_setting)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.menu_item_save_config) { _, _ ->
                val url = dialogBinding.etWebdavUrl.text.toString().trim()
                val user = dialogBinding.etWebdavUser.text.toString().trim().ifEmpty { null }
                val pass = dialogBinding.etWebdavPass.text.toString()
                val remotePath = dialogBinding.etWebdavRemotePath.text.toString().trim().ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
                val cfg = WebDavConfig(baseUrl = url, username = user, password = pass, remoteBasePath = remotePath)
                MmkvManager.encodeWebDavConfig(cfg)
                
                alertSuccess(
                    getString(R.string.title_webdav_config_setting), 
                    title = getString(R.string.title_alerter_success)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
    }
}
