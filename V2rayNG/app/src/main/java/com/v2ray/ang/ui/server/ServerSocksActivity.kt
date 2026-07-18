package com.v2ray.ang.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.server.fields.AddressPortFields
import com.v2ray.ang.util.SoftInputAssist
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showDeleteConfirmDialog

class ServerSocksActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.SOCKS.value)) ?: EConfigType.SOCKS
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private val et_id: EditText by lazy { findViewById(R.id.et_id) }
    private val et_security: EditText? by lazy { findViewById(R.id.et_security) }

    private lateinit var addressPortFields: AddressPortFields
    private lateinit var softInputAssist: SoftInputAssist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(R.layout.activity_server_socks)

        addressPortFields = AddressPortFields(this)
        softInputAssist = SoftInputAssist(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = (config?.configType ?: createConfigType).toString())

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        addressPortFields.bind(config)
        et_id.text = Utils.getEditable(config.password.orEmpty())
        et_security?.text = Utils.getEditable(config.username.orEmpty())
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        et_id.text = null
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

        // Socks/Http don't require an id/password.
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
        config.password = et_id.text.toString().trim()

        if (!TextUtils.isEmpty(et_security?.text) || !TextUtils.isEmpty(et_id.text)) {
            config.username = et_security?.text.toString().trim()
        }
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
