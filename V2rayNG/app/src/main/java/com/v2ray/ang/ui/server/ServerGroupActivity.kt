package com.v2ray.ang.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import com.v2ray.ang.util.showDeleteConfirmDialog
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityServerGroupBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.snackbarDefault
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.SoftInputAssist

class ServerGroupActivity : BaseActivity() {
    private val binding by lazy { ActivityServerGroupBinding.inflate(layoutInflater) }

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy {
        intent.getStringExtra("subscriptionId")
    }
    
    private val subIds = mutableListOf<String>()
    private val displayList = mutableListOf<String>()
    
    private val policyGroupTypes: Array<out String> by lazy { 
        resources.getStringArray(R.array.policy_group_type) 
    }
    
    private lateinit var softInputAssist: SoftInputAssist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(binding.root)
        
        softInputAssist = SoftInputAssist(this)
        

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = EConfigType.POLICYGROUP.toString())

        val config = MmkvManager.decodeServerConfig(editGuid)
        
        populateSubscriptionSpinner()

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    /**
     * Binding selected server config
     */
    private fun bindingServer(config: ProfileItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(config.remarks)
        binding.etPolicyGroupFilter.text = Utils.getEditable(config.policyGroupFilter)

        val typeIndex = config.policyGroupType?.toIntOrNull() ?: 0
        if (typeIndex in policyGroupTypes.indices) {
            binding.spPolicyGroupType.setText(policyGroupTypes[typeIndex], false)
        }

        val pos = subIds.indexOf(config.policyGroupSubscriptionId ?: "").let { if (it >= 0) it else 0 }
        if (pos in displayList.indices) {
            binding.spPolicyGroupSubId.setText(displayList[pos], false)
        }

        return true
    }

    /**
     * clear or init server config
     */
    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.etPolicyGroupFilter.text = null

        // Default type ke index 0
        if (policyGroupTypes.isNotEmpty()) {
            binding.spPolicyGroupType.setText(policyGroupTypes[0], false)
        }

        if (subscriptionId.isNotNullEmpty()) {
            val pos = subIds.indexOf(subscriptionId).let { if (it >= 0) it else 0 }
            if (pos in displayList.indices) {
                binding.spPolicyGroupSubId.setText(displayList[pos], false)
            }
        } else if (displayList.isNotEmpty()) {
            binding.spPolicyGroupSubId.setText(displayList[0], false)
        }
        return true
    }

    /**
     * save server config
     */
    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(binding.etRemarks.text.toString())) {
            snackbarError(
                getString(R.string.server_lab_remarks),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.POLICYGROUP)
        config.remarks = binding.etRemarks.text.toString().trim()
        config.policyGroupFilter = binding.etPolicyGroupFilter.text.toString().trim()

        val selectedTypeStr = binding.spPolicyGroupType.text.toString()
        val typePos = policyGroupTypes.indexOf(selectedTypeStr).let { if (it >= 0) it else 0 }
        config.policyGroupType = typePos.toString()

        val selectedSubStr = binding.spPolicyGroupSubId.text.toString()
        val selPos = displayList.indexOf(selectedSubStr)
        config.policyGroupSubscriptionId = if (selPos >= 0 && selPos < subIds.size) subIds[selPos] else null

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        config.description = "$selectedTypeStr - $selectedSubStr - ${config.policyGroupFilter}"

        MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    /**
     * delete server config
     */
    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                MmkvManager.removeServer(editGuid)
                finish()
            }
        }
        return true
    }

    private fun populateSubscriptionSpinner() {
        val subs = MmkvManager.decodeSubscriptions()
        displayList.clear()
        subIds.clear()
        
        displayList.add(getString(R.string.filter_config_all)) // none
        subIds.add("") // index 0 => All
        
        subs.forEach { sub ->
            val name = when {
                sub.subscription.remarks.isNotBlank() -> sub.subscription.remarks
                else -> sub.guid
            }
            displayList.add(name)
            subIds.add(sub.guid)
        }
        
        val subAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)
        binding.spPolicyGroupSubId.setAdapter(subAdapter)
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
        if (::softInputAssist.isInitialized) {
            softInputAssist.onResume()
        }
        super.onResume()
    }

    override fun onPause() {
        if (::softInputAssist.isInitialized) {
            softInputAssist.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::softInputAssist.isInitialized) {
            softInputAssist.onDestroy()
        }
        super.onDestroy()
    } 
}
