package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerHysteria2Binding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.nullIfBlank
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.CertificateFingerprintManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.server.fields.AddressPortFields
import com.miku.ray.ui.server.fields.TlsFields
import com.miku.ray.util.Utils
import com.miku.ray.util.showDeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerHysteria2Activity : BaseActivity() {
    private val binding by lazy { ActivityServerHysteria2Binding.inflate(layoutInflater) }
    private val obfsTypes by lazy { resources.getStringArray(R.array.hysteria2_obfs_types) }

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
        && editGuid.isNotEmpty()
        && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.HYSTERIA2.value)) ?: EConfigType.HYSTERIA2
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var addressPortFields: AddressPortFields
    private lateinit var tlsFields: TlsFields

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(binding.root)

        binding.serverScrollContent.applyEdgeToEdgeListInsets()

        addressPortFields = AddressPortFields(binding.root)
        tlsFields = TlsFields(binding.root)

        setupToolbar(binding.toolbar, showHomeAsUp = true, title = (config?.configType ?: createConfigType).toString(), subtitle = getString(R.string.subtitle_server_config))

        binding.spObfsType.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position)?.toString()?.lowercase().orEmpty()
            onObfsTypeChanged(selected)
        }

        tlsFields.setOnSecurityChanged { security -> tlsFields.updateForSecurity(security) }
        tlsFields.setOnFetchCertClick { fetchPinnedCA256ForCurrentConfig() }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun onObfsTypeChanged(obfsType: String) {
        if (obfsType == "none" || obfsType.isBlank()) {
            binding.etObfsPassword.text = null
            binding.etMinPacketSize.text = null
            binding.etMaxPacketSize.text = null
        } else if (obfsType == "salamander") {
            binding.etMinPacketSize.text = null
            binding.etMaxPacketSize.text = null
        }
        updateObfsVisibility(obfsType)
    }

    private fun updateObfsVisibility(obfsType: String) {
        val showPassword = obfsType == "salamander" || obfsType == "gecko"
        val showPacketSize = obfsType == "gecko"

        binding.layObfsPassword.visibility = if (showPassword) View.VISIBLE else View.GONE
        binding.layMinPacketSize.visibility = if (showPacketSize) View.VISIBLE else View.GONE
        binding.layMaxPacketSize.visibility = if (showPacketSize) View.VISIBLE else View.GONE
    }

    private fun resolveSelectedObfs(config: ProfileItem): String = when {
        config.isGeckoObfs() -> "gecko"
        config.obfs.equals("salamander", ignoreCase = true) -> "salamander"
        !config.obfsPassword.isNullOrBlank() && !config.obfs.equals("none", ignoreCase = true) -> "salamander"
        else -> "none"
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        addressPortFields.bind(config)
        binding.etId.text = Utils.getEditable(config.password.orEmpty())

        val selectedObfs = resolveSelectedObfs(config)
        binding.spObfsType.setText(selectedObfs, false)
        binding.etObfsPassword.text = Utils.getEditable(config.obfsPassword)
        binding.etMinPacketSize.text = Utils.getEditable(config.minPacketSize)
        binding.etMaxPacketSize.text = Utils.getEditable(config.maxPacketSize)
        updateObfsVisibility(selectedObfs)

        binding.etPortHop.text = Utils.getEditable(config.portHopping)
        binding.etPortHopInterval.text = Utils.getEditable(config.portHoppingInterval)
        binding.etBandwidthDown.text = Utils.getEditable(config.bandwidthDown)
        binding.etBandwidthUp.text = Utils.getEditable(config.bandwidthUp)

        tlsFields.bind(config)
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        binding.etId.text = null

        val defaultObfs = obfsTypes.firstOrNull() ?: "none"
        binding.spObfsType.setText(defaultObfs, false)
        binding.etObfsPassword.text = null
        binding.etMinPacketSize.text = null
        binding.etMaxPacketSize.text = null
        updateObfsVisibility(defaultObfs)

        tlsFields.clear()
        return true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(addressPortFields.remarksText)) {
            snackbarError(getString(R.string.server_lab_remarks), title = getString(R.string.title_alerter_error))
            return false
        }
        if (TextUtils.isEmpty(addressPortFields.addressText)) {
            snackbarError(getString(R.string.server_lab_address), title = getString(R.string.title_alerter_error))
            return false
        }
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)

        if (TextUtils.isEmpty(binding.etId.text.toString())) {
            snackbarError(getString(R.string.server_lab_id3), title = getString(R.string.title_alerter_error))
            return false
        }

        saveCommon(config)
        tlsFields.save(config)

        config.description = AngConfigManager.generateDescription(config)

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun saveCommon(config: ProfileItem) {
        addressPortFields.save(config)
        config.password = binding.etId.text.toString().trim()

        val selectedObfs = binding.spObfsType.text?.toString()?.trim()?.lowercase().orEmpty()
        when (selectedObfs) {
            "gecko" -> {
                config.obfs = "gecko"
                config.obfsPassword = binding.etObfsPassword.text?.toString()?.nullIfBlank()
                config.minPacketSize = binding.etMinPacketSize.text?.toString()?.trim()?.nullIfBlank()
                config.maxPacketSize = binding.etMaxPacketSize.text?.toString()?.trim()?.nullIfBlank()
            }
            "salamander" -> {
                config.obfs = "salamander"
                config.obfsPassword = binding.etObfsPassword.text?.toString()?.nullIfBlank()
                config.minPacketSize = null
                config.maxPacketSize = null
            }
            else -> {
                config.obfs = null
                config.obfsPassword = null
                config.minPacketSize = null
                config.maxPacketSize = null
            }
        }
        config.portHopping = binding.etPortHop.text?.toString()
        config.portHoppingInterval = binding.etPortHopInterval.text?.toString()?.trim()
        config.bandwidthDown = binding.etBandwidthDown.text?.toString()
        config.bandwidthUp = binding.etBandwidthUp.text?.toString()
    }

    private fun fetchPinnedCA256ForCurrentConfig() {
        val config = buildCurrentProfileForCertificateFetch() ?: return

        lifecycleScope.launch {
            tlsFields.setFetchButtonEnabled(false)
            try {
                val sha256 = withContext(Dispatchers.IO) {
                    CertificateFingerprintManager.fetchForManualFill(config)
                }
                if (sha256.isNullOrBlank()) {
                    snackbarError(
                        getString(R.string.toast_fetch_cert_sha256_failed),
                        title = getString(R.string.title_alerter_error)
                    )
                } else {
                    tlsFields.setPinnedCa256Text(sha256)
                    snackbarSuccess(
                        getString(R.string.toast_fetch_cert_sha256_success),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            } finally {
                tlsFields.setFetchButtonEnabled(true)
            }
        }
    }

    private fun buildCurrentProfileForCertificateFetch(): ProfileItem? {
        if (TextUtils.isEmpty(addressPortFields.addressText)) {
            snackbarError(getString(R.string.server_lab_address), title = getString(R.string.title_alerter_error))
            return null
        }

        val configType = MmkvManager.decodeServerConfig(editGuid)?.configType ?: createConfigType
        val config = ProfileItem.create(configType)
        saveCommon(config)
        tlsFields.save(config)

        return config
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (editGuid != MmkvManager.getSelectServer() && !MmkvManager.isServerPinned(editGuid)) {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                    showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                        MmkvManager.removeServer(editGuid)
                        SettingsChangeManager.makeSetupGroupTab()
                        toastSuccess(R.string.toast_delete_success)
                        finish()
                    }
                } else {
                    MmkvManager.removeServer(editGuid)
                    SettingsChangeManager.makeSetupGroupTab()
                    toastSuccess(R.string.toast_delete_success)
                    finish()
                }
            } else if (MmkvManager.isServerPinned(editGuid)) {
                snackbarError(getString(R.string.toast_pinned_server_delete_blocked), title = getString(R.string.title_alerter_error))
            } else {
                snackbarError(getString(R.string.toast_action_not_allowed), title = getString(R.string.title_alerter_error))
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
