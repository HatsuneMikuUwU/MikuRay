package com.v2ray.ang.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.server.fields.AddressPortFields
import com.v2ray.ang.ui.server.fields.TlsFields
import com.v2ray.ang.util.SoftInputAssist
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showDeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerHysteria2Activity : BaseActivity() {

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

    private val et_id: EditText by lazy { findViewById(R.id.et_id) }
    private val et_obfs_password: EditText? by lazy { findViewById(R.id.et_obfs_password) }
    private val et_port_hop: EditText? by lazy { findViewById(R.id.et_port_hop) }
    private val et_port_hop_interval: EditText? by lazy { findViewById(R.id.et_port_hop_interval) }
    private val et_bandwidth_down: EditText? by lazy { findViewById(R.id.et_bandwidth_down) }
    private val et_bandwidth_up: EditText? by lazy { findViewById(R.id.et_bandwidth_up) }

    private lateinit var addressPortFields: AddressPortFields
    private lateinit var tlsFields: TlsFields
    private lateinit var softInputAssist: SoftInputAssist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(R.layout.activity_server_hysteria2)

        addressPortFields = AddressPortFields(this)
        tlsFields = TlsFields(this)
        softInputAssist = SoftInputAssist(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = (config?.configType ?: createConfigType).toString())

        tlsFields.setOnSecurityChanged { security -> tlsFields.updateForSecurity(security) }
        tlsFields.setOnFetchCertClick { fetchPinnedCA256ForCurrentConfig() }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        addressPortFields.bind(config)
        et_id.text = Utils.getEditable(config.password.orEmpty())

        et_obfs_password?.text = Utils.getEditable(config.obfsPassword)
        et_port_hop?.text = Utils.getEditable(config.portHopping)
        et_port_hop_interval?.text = Utils.getEditable(config.portHoppingInterval)
        et_bandwidth_down?.text = Utils.getEditable(config.bandwidthDown)
        et_bandwidth_up?.text = Utils.getEditable(config.bandwidthUp)

        tlsFields.bind(config)
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        et_id.text = null

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
        // Hysteria2 doesn't require a fixed single port (port hopping is supported).
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)

        if (TextUtils.isEmpty(et_id.text.toString())) {
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
        config.password = et_id.text.toString().trim()

        config.obfsPassword = et_obfs_password?.text?.toString()
        config.portHopping = et_port_hop?.text?.toString()
        config.portHoppingInterval = et_port_hop_interval?.text?.toString()?.trim()
        config.bandwidthDown = et_bandwidth_down?.text?.toString()
        config.bandwidthUp = et_bandwidth_up?.text?.toString()
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
            if (editGuid != MmkvManager.getSelectServer()) {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                    showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                        MmkvManager.removeServer(editGuid)
                        finish()
                    }
                } else {
                    MmkvManager.removeServer(editGuid)
                    finish()
                }
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

    override fun onResume() {
        if (::softInputAssist.isInitialized) softInputAssist.onResume()
        super.onResume()
    }

    override fun onPause() {
        if (::softInputAssist.isInitialized) softInputAssist.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::softInputAssist.isInitialized) softInputAssist.onDestroy()
        super.onDestroy()
    }
}
