package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.miku.ray.util.showDeleteConfirmDialog
import com.blacksquircle.ui.editorkit.utils.EditorTheme
import com.blacksquircle.ui.language.json.JsonLanguage
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerCustomConfigBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.fmt.CustomFmt
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

class ServerCustomConfigActivity : BaseActivity() {
    private val binding by lazy { ActivityServerCustomConfigBinding.inflate(layoutInflater) }

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.serverScrollContent.applyEdgeToEdgeListInsets()


        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = EConfigType.CUSTOM.toString(), subtitle = getString(R.string.subtitle_server_config))

        if (!Utils.getDarkModeStatus(this)) {
            binding.editor.colorScheme = EditorTheme.INTELLIJ_LIGHT
        }
        binding.editor.language = JsonLanguage()
        val config = MmkvManager.decodeServerConfig(editGuid)
        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(config.remarks)
        val raw = MmkvManager.decodeServerRaw(editGuid)
        val configContent = raw.orEmpty()

        binding.editor.setTextContent(Utils.getEditable(configContent))
        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        return true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(binding.etRemarks.text.toString())) {
            snackbarError(
                getString(R.string.server_lab_remarks),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }

        val profileItem = try {
            CustomFmt.parse(binding.editor.text.toString())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse custom configuration", e)
            snackbarDefault("${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", title = getString(R.string.title_alerter_info))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.CUSTOM)
        binding.etRemarks.text.let {
            config.remarks = if (it.isNullOrEmpty()) profileItem.remarks.orEmpty() else it.toString()
        }
        config.server = profileItem.server
        config.serverPort = profileItem.serverPort
        config.network = profileItem.network
        config.security = profileItem.security
        config.description = AngConfigManager.generateDescription(config)

        MmkvManager.encodeServerConfig(editGuid, config)
        MmkvManager.encodeServerRaw(editGuid, binding.editor.text.toString())
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (MmkvManager.isServerPinned(editGuid)) {
                snackbarError(getString(R.string.toast_pinned_server_delete_blocked), title = getString(R.string.title_alerter_error))
                return true
            }
            showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                MmkvManager.removeServer(editGuid)
                toastSuccess(R.string.toast_delete_success)
                finish()
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu.findItem(R.id.del_config)
        val saveButton = menu.findItem(R.id.save_config)

        if (editGuid.isNotEmpty()) {
            if (isRunning) {
                delButton?.isVisible = false
                saveButton?.isVisible = false
            }
        } else {
            delButton?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }

        R.id.save_config -> {
            saveServer()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

}
