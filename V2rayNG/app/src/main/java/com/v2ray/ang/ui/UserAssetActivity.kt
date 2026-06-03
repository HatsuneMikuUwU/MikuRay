package com.v2ray.ang.ui

import com.v2ray.ang.util.showDeleteConfirmDialog
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ActivityUserAssetBinding
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.alertError
import com.v2ray.ang.extension.alertSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.bottomsheet.AssetMenuBottomSheet
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.UserAssetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UserAssetActivity : HelperBaseActivity(), AssetMenuBottomSheet.OnAssetMenuOptionClickListener {
    private val binding by lazy { ActivityUserAssetBinding.inflate(layoutInflater) }
    private val ownerActivity: UserAssetActivity
        get() = this
    private val viewModel: UserAssetViewModel by viewModels()
    private lateinit var adapter: UserAssetAdapter

    val extDir by lazy { File(Utils.userAssetPath(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom,    displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_user_asset_setting))

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAssetAdapter(viewModel, extDir, ActivityAdapterListener())
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_asset, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.download_file -> downloadGeoFiles().let { true }
        R.id.action_more_menu -> {
            val bottomSheet = AssetMenuBottomSheet()
            bottomSheet.show(supportFragmentManager, AssetMenuBottomSheet.TAG)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onAssetMenuOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.add_file -> showFileChooser()
            R.id.add_url -> startActivity(Intent(this, UserAssetUrlActivity::class.java))
            R.id.add_qrcode -> importAssetFromQRcode()
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(
                    getCursorName(uri) ?: uri.toString(),
                    "file"
                )

                val assetList = MmkvManager.decodeAssetUrls()
                if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
                    toast(R.string.msg_remark_is_duplicate)
                } else {
                    MmkvManager.encodeAsset(assetId, assetItem)
                    copyFile(uri)
                }
            }.onFailure {
                alertError(
                    getString(R.string.toast_asset_copy_failed),
                    title = getString(R.string.title_alerter_error)
                )
                MmkvManager.removeAssetUrl(assetId)
            }
        }
    }

    private fun copyFile(uri: Uri): String {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                alertSuccess(
                    getString(R.string.menu_item_add_file),
                    title = getString(R.string.title_alerter_success)
                )
                refreshData()
            }
        }
        return targetFile.path
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.let { cursor ->
            cursor.run {
                if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }.also { cursor.close() }
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importAsset(scanResult)
            }
        }
        return true
    }

    private fun importAsset(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            startActivity(
                Intent(this, UserAssetUrlActivity::class.java)
                    .putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, url)
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import asset from URL", e)
            return false
        }
        return true
    }

    private fun downloadGeoFiles() {
        refreshData()
        showLoading()
        toast(R.string.msg_downloading_content)

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = viewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            withContext(Dispatchers.Main) {
                if (result.successCount > 0) {
                    alertSuccess(
                        getString(R.string.title_update_config_count, result.successCount),
                        title = getString(R.string.title_alerter_success)
                    )
                } else {
                    alertError(
                        getString(R.string.menu_item_download_file),
                        title = getString(R.string.title_alerter_error)
                    )
                }
                refreshData()
                hideLoading()
            }
        }
    }

    fun initAssets() {
        lifecycleScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(this@UserAssetActivity, assets)
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        val geoFilesSources = MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
        viewModel.reload(geoFilesSources)
        adapter.notifyDataSetChanged()
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(ownerActivity, UserAssetUrlActivity::class.java)
                    .putExtra("assetId", guid)
            )
        }

        override fun onRemove(guid: String, position: Int) {
            val asset = viewModel.getAsset(position)?.takeIf { it.guid == guid }
                ?: viewModel.getAssets().find { it.guid == guid }
                ?: return
            val file = extDir.listFiles()?.find { it.name == asset.assetUrl.remarks }

            showDeleteConfirmDialog(context = ownerActivity, messageRes = R.string.del_file_asset_dialog_comfirm_message) {
                file?.delete()
                MmkvManager.removeAssetUrl(guid)
                initAssets()
            }
        }

        override fun onShare(url: String) {
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
