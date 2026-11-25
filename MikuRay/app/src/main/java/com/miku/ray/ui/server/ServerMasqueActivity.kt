package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerMasqueBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
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
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.Utils
import com.miku.ray.util.showDeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerMasqueActivity : BaseActivity() {
    private val binding by lazy { ActivityServerMasqueBinding.inflate(layoutInflater) }
    private val uTlsItems by lazy { resources.getStringArray(R.array.streamsecurity_utls) }

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
            && editGuid.isNotEmpty()
            && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.MASQUE.value)) ?: EConfigType.MASQUE
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var addressPortFields: AddressPortFields

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(binding.root)

        binding.serverScrollContent.applyEdgeToEdgeListInsets()

        addressPortFields = AddressPortFields(binding.root)

        setupToolbar(
            binding.toolbar,
            showHomeAsUp = true,
            title = (config?.configType ?: createConfigType).toString(),
            subtitle = getString(R.string.subtitle_server_config)
        )

        binding.layRemoteDns.helperText = getString(
            R.string.server_lab_remote_dns_hint,
            AppConfig.MASQUE_LOCAL_REMOTE_DNS
        )
        binding.layLocalAddress.helperText = getString(
            R.string.server_lab_local_address_hint,
            "${AppConfig.MASQUE_LOCAL_ADDRESS_V4},${AppConfig.MASQUE_LOCAL_ADDRESS_V6}"
        )

        binding.spStreamAlpn.setOnItemClickListener { parent, _, position, _ ->
            val selectedAlpn = parent.getItemAtPosition(position)?.toString()?.trim().orEmpty()
            updateFingerprintVisibility(selectedAlpn)
        }
        binding.btnPinnedCa256Action.setOnClickListener { fetchPinnedCA256ForCurrentConfig() }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun updateFingerprintVisibility(alpn: String) {
        binding.layStreamFingerprint.visibility = if (alpn == ALPN_H2) View.VISIBLE else View.GONE
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        addressPortFields.bind(config)
        binding.etMasqueHost.text = Utils.getEditable(config.host)
        binding.etMasquePath.text = Utils.getEditable(config.path)
        binding.etMasqueHeaders.text = Utils.getEditable(config.headers)
        binding.etRemoteDns.text = Utils.getEditable(config.remoteDNS)
        binding.etFinalMask.text = Utils.getEditable(config.finalMask)

        binding.etLocalAddress.text = Utils.getEditable(config.localAddress)
        binding.etSecretKey.text = Utils.getEditable(config.secretKey)
        binding.etPublicKey.text = Utils.getEditable(config.publicKey)

        binding.etSni.text = Utils.getEditable(config.sni)
        val alpn = config.alpn?.trim()?.takeIf { it == ALPN_H2 || it == ALPN_H3 } ?: ALPN_H3
        binding.spStreamAlpn.setText(alpn, false)
        updateFingerprintVisibility(alpn)

        val utlsIndex = Utils.arrayFind(uTlsItems, config.fingerPrint.orEmpty())
        if (utlsIndex >= 0) {
            binding.spStreamFingerprint.setText(uTlsItems[utlsIndex], false)
        } else {
            binding.spStreamFingerprint.setText(uTlsItems.firstOrNull().orEmpty(), false)
        }
        binding.etVerifyPeerCertByName.text = Utils.getEditable(config.verifyPeerCertByName)
        binding.etPinnedCa256.text = Utils.getEditable(config.pinnedCA256)
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        binding.etMasqueHost.text = null
        binding.etMasquePath.text = null
        binding.etMasqueHeaders.text = null
        binding.etRemoteDns.text = Utils.getEditable(AppConfig.MASQUE_LOCAL_REMOTE_DNS)
        binding.etFinalMask.text = null

        binding.etLocalAddress.text = Utils.getEditable(
            "${AppConfig.MASQUE_LOCAL_ADDRESS_V4},${AppConfig.MASQUE_LOCAL_ADDRESS_V6}"
        )
        binding.etSecretKey.text = null
        binding.etPublicKey.text = null

        binding.etSni.text = null
        binding.spStreamAlpn.setText(ALPN_H3, false)
        updateFingerprintVisibility(ALPN_H3)
        binding.spStreamFingerprint.setText(uTlsItems.firstOrNull().orEmpty(), false)
        binding.etVerifyPeerCertByName.text = null
        binding.etPinnedCa256.text = null
        return true
    }

    private fun validateHeaders(headersRaw: String?): Boolean {
        if (headersRaw.isNullOrBlank()) return true
        val headers = try {
            JsonUtil.fromJson(headersRaw, JsonObject::class.java)
        } catch (_: Exception) {
            null
        }
        return headers != null &&
            headers.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString }
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
        val port = Utils.parseInt(addressPortFields.portText)
        if (port <= 0) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return false
        }
        val headersRaw = binding.etMasqueHeaders.text?.toString()?.nullIfBlank()
        if (!validateHeaders(headersRaw)) {
            snackbarError(getString(R.string.sub_setting_request_headers), title = getString(R.string.title_alerter_error))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)
        saveCommon(config)

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
        config.network = NetworkType.MASQUE.type
        config.security = AppConfig.TLS
        config.insecure = false

        config.host = binding.etMasqueHost.text?.toString()?.trim().nullIfBlank()
        config.path = binding.etMasquePath.text?.toString()?.trim().nullIfBlank()
        config.headers = binding.etMasqueHeaders.text?.toString()?.nullIfBlank()
        config.remoteDNS = binding.etRemoteDns.text?.toString()?.trim()
        config.finalMask = binding.etFinalMask.text?.toString()?.nullIfBlank()

        config.localAddress = binding.etLocalAddress.text?.toString()?.trim()
        config.secretKey = binding.etSecretKey.text?.toString()?.trim()
        config.publicKey = binding.etPublicKey.text?.toString()?.trim()

        config.sni = binding.etSni.text?.toString()?.trim().nullIfBlank()
        val alpn = binding.spStreamAlpn.text?.toString()?.trim()?.takeIf { it == ALPN_H2 || it == ALPN_H3 } ?: ALPN_H3
        config.alpn = alpn
        config.fingerPrint = if (alpn == ALPN_H2) {
            binding.spStreamFingerprint.text?.toString()?.trim().nullIfBlank()
        } else {
            null
        }
        config.verifyPeerCertByName = binding.etVerifyPeerCertByName.text?.toString()?.trim().nullIfBlank()
        config.pinnedCA256 = binding.etPinnedCa256.text?.toString()?.trim().nullIfBlank()
    }

    private fun fetchPinnedCA256ForCurrentConfig() {
        val config = buildCurrentProfileForCertificateFetch() ?: return

        lifecycleScope.launch {
            binding.btnPinnedCa256Action.isEnabled = false
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
                    binding.etPinnedCa256.text = Utils.getEditable(sha256)
                    snackbarSuccess(
                        getString(R.string.toast_fetch_cert_sha256_success),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            } finally {
                binding.btnPinnedCa256Action.isEnabled = true
            }
        }
    }

    private fun buildCurrentProfileForCertificateFetch(): ProfileItem? {
        if (TextUtils.isEmpty(addressPortFields.addressText)) {
            snackbarError(getString(R.string.server_lab_address), title = getString(R.string.title_alerter_error))
            return null
        }
        if (Utils.parseInt(addressPortFields.portText) <= 0) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return null
        }

        val configType = MmkvManager.decodeServerConfig(editGuid)?.configType ?: createConfigType
        val config = ProfileItem.create(configType)
        saveCommon(config)
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

    private companion object {
        const val ALPN_H2 = "h2"
        const val ALPN_H3 = "h3"
    }
}
