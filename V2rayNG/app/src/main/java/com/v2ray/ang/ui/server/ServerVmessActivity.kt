package com.v2ray.ang.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.server.fields.AddressPortFields
import com.v2ray.ang.ui.server.fields.TlsFields
import com.v2ray.ang.ui.server.fields.TransportFields
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.SoftInputAssist
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showDeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerVmessActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.VMESS.value)) ?: EConfigType.VMESS
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private val securitys: Array<out String> by lazy { resources.getStringArray(R.array.securitys) }

    private val et_id: EditText by lazy { findViewById(R.id.et_id) }
    private val sp_security: AutoCompleteTextView? by lazy { findViewById(R.id.sp_security) }

    private lateinit var addressPortFields: AddressPortFields
    private lateinit var transportFields: TransportFields
    private lateinit var tlsFields: TlsFields
    private lateinit var softInputAssist: SoftInputAssist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(R.layout.activity_server_vmess)

        addressPortFields = AddressPortFields(this)
        transportFields = TransportFields(this)
        tlsFields = TlsFields(this)
        softInputAssist = SoftInputAssist(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = (config?.configType ?: createConfigType).toString())

        transportFields.setOnNetworkChanged { network -> transportFields.updateForNetwork(network, config) }
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

        val securityPos = Utils.arrayFind(securitys, config.method.orEmpty())
        if (securityPos >= 0) sp_security?.setText(securitys[securityPos], false)

        tlsFields.bind(config)
        transportFields.bind(config)
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        et_id.text = null
        sp_security?.setText(securitys.firstOrNull().orEmpty(), false)

        transportFields.clear()
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
        if (Utils.parseInt(addressPortFields.portText) <= 0) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return false
        }
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)

        if (TextUtils.isEmpty(et_id.text.toString())) {
            snackbarError(getString(R.string.server_lab_id), title = getString(R.string.title_alerter_error))
            return false
        }
        if (transportFields.extraText.isNotNullEmpty() && JsonUtil.parseString(transportFields.extraText) == null) {
            snackbarError(getString(R.string.server_lab_xhttp_extra), title = getString(R.string.title_alerter_error))
            return false
        }
        if (transportFields.finalMaskText.isNotNullEmpty() && JsonUtil.parseString(transportFields.finalMaskText) == null) {
            snackbarError(getString(R.string.server_lab_final_mask), title = getString(R.string.title_alerter_error))
            return false
        }

        saveCommon(config)
        transportFields.save(config)
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

        val secPos = Utils.arrayFind(securitys, sp_security?.text.toString())
        config.method = securitys[if (secPos >= 0) secPos else 0]
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
        if (Utils.parseInt(addressPortFields.portText) <= 0) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return null
        }

        val configType = MmkvManager.decodeServerConfig(editGuid)?.configType ?: createConfigType
        val config = ProfileItem.create(configType)
        saveCommon(config)
        transportFields.save(config)
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
