package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import com.miku.ray.util.showDeleteConfirmDialog
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerGroupBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.BalancerStrategyType
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.util.Utils

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

    private val fallbackSuggestions: List<String> by lazy {
        (AppConfig.BUILTIN_OUTBOUND_TAGS + SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP)
        )).filter { it != AppConfig.TAG_PROXY }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.serverScrollContent.applyEdgeToEdgeListInsets()


        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = EConfigType.POLICYGROUP.toString(), subtitle = getString(R.string.subtitle_server_config))

        val config = MmkvManager.decodeServerConfig(editGuid)

        populateSubscriptionSpinner()
        populateFallbackSuggestions()

        binding.spPolicyGroupType.setOnItemClickListener { _, _, _, _ -> updateFallbackVisibility() }
        binding.chkPolicyGroupTestOutbounds.setOnCheckedChangeListener { _, _ -> updateFallbackVisibility() }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
        updateFallbackVisibility()
    }

    private fun populateFallbackSuggestions() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fallbackSuggestions)
        binding.spPolicyGroupFallback.setAdapter(adapter)
    }

    private fun updateFallbackVisibility() {
        val typePos = policyGroupTypes.indexOf(binding.spPolicyGroupType.text.toString()).let { if (it >= 0) it else 0 }
        val supportsObservatory = BalancerStrategyType.from(typePos.toString()).supportsObservatory
        binding.layoutPolicyGroupTestOutbounds.visibility = if (supportsObservatory) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutPolicyGroupFallback.visibility =
            if (supportsObservatory && binding.chkPolicyGroupTestOutbounds.isChecked) android.view.View.VISIBLE else android.view.View.GONE
    }

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

        val supportsObservatory = BalancerStrategyType.from(config.policyGroupType).supportsObservatory
        binding.chkPolicyGroupTestOutbounds.isChecked =
            config.policyGroupTestOutbounds != false || !supportsObservatory
        binding.spPolicyGroupFallback.setText(config.policyGroupFallbackTag.orEmpty(), false)
        updateFallbackVisibility()

        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.etPolicyGroupFilter.text = null

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

        binding.chkPolicyGroupTestOutbounds.isChecked = true
        binding.spPolicyGroupFallback.setText("", false)
        updateFallbackVisibility()
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

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.POLICYGROUP)
        config.remarks = binding.etRemarks.text.toString().trim()
        config.policyGroupFilter = binding.etPolicyGroupFilter.text.toString().trim()

        val selectedTypeStr = binding.spPolicyGroupType.text.toString()
        val typePos = policyGroupTypes.indexOf(selectedTypeStr).let { if (it >= 0) it else 0 }
        config.policyGroupType = typePos.toString()

        val selectedSubStr = binding.spPolicyGroupSubId.text.toString()
        val selPos = displayList.indexOf(selectedSubStr)
        config.policyGroupSubscriptionId = if (selPos >= 0 && selPos < subIds.size) subIds[selPos] else null

        config.policyGroupTestOutbounds = binding.chkPolicyGroupTestOutbounds.isChecked
        config.policyGroupFallbackTag = binding.spPolicyGroupFallback.text.toString().trim().takeIf { it.isNotEmpty() }

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        config.description = "$selectedTypeStr - $selectedSubStr - ${config.policyGroupFilter}"

        MmkvManager.encodeServerConfig(editGuid, config)
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

    private fun populateSubscriptionSpinner() {
        val subs = MmkvManager.decodeSubscriptions()
        displayList.clear()
        subIds.clear()

        displayList.add(getString(R.string.filter_config_all))
        subIds.add("")

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

}
