package com.miku.ray.ui.routing

import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.apppicker.AppPickerActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import com.miku.ray.util.showDeleteConfirmDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.miku.ray.AppConfig.TAG_PROXY
import com.miku.ray.R
import com.miku.ray.databinding.ActivityRoutingEditBinding
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.nullIfBlank
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutingEditActivity : BaseActivity() {
    private val binding by lazy { ActivityRoutingEditBinding.inflate(layoutInflater) }
    private val position by lazy { intent.getIntExtra("position", -1) }

    private val processPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedPackages = AppPickerActivity.getSelectedPackages(result.data)
            binding.etProcess.setText(Utils.getEditable(selectedPackages.joinToString(",")))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.editScrollContent.applyEdgeToEdgeListInsets()


        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.routing_settings_rule_title), subtitle = getString(R.string.subtitle_routing_edit))

        setupOutboundTagInput()
        setupProcessPicker()

        val rulesetItem = SettingsManager.getRoutingRuleset(position)
        if (rulesetItem != null) {
            bindingServer(rulesetItem)
        } else {
            clearServer()
        }

        SettingsManager.canUseProcessRouting().let { canUse ->
            binding.etProcess.isEnabled = canUse
            binding.tilProcess.isEndIconVisible = canUse
        }
    }

    private fun setupProcessPicker() {
        binding.tilProcess.setEndIconOnClickListener {
            processPickerLauncher.launch(
                AppPickerActivity.createIntent(
                    context = this,
                    selectedPackages = getSelectedProcessPackages(),
                    title = getString(R.string.routing_settings_process)
                )
            )
        }
    }

    private fun getSelectedProcessPackages(): List<String> {
        return binding.etProcess.text
            ?.toString()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct() ?: emptyList()
    }

    private fun setupOutboundTagInput() {
        val profileRemarks = SettingsManager.getProfileRemarks()

        val suggestions = (BUILTIN_OUTBOUND_TAGS.toList() + profileRemarks).distinct()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions)
        binding.spOutboundTag.setAdapter(adapter)
        binding.spOutboundTag.threshold = 0

        binding.spOutboundTag.setOnClickListener {
            binding.spOutboundTag.showDropDown()
        }
    }

    private fun bindingServer(rulesetItem: RulesetItem): Boolean {
        binding.etRemarks.setText(Utils.getEditable(rulesetItem.remarks))
        binding.chkLocked.isChecked = rulesetItem.locked == true
        binding.etDomain.setText(Utils.getEditable(rulesetItem.domain?.joinToString(",")))
        binding.etIp.setText(Utils.getEditable(rulesetItem.ip?.joinToString(",")))
        binding.etProcess.setText(Utils.getEditable(rulesetItem.process?.joinToString(",")))
        binding.etPort.setText(Utils.getEditable(rulesetItem.port))
        binding.etProtocol.setText(Utils.getEditable(rulesetItem.protocol?.joinToString(",")))
        binding.etNetwork.setText(Utils.getEditable(rulesetItem.network))
        binding.spOutboundTag.setText(rulesetItem.outboundTag, false)
        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.spOutboundTag.setText(BUILTIN_OUTBOUND_TAGS.first(), false)
        return true
    }

    private fun saveServer(): Boolean {
        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem()

        rulesetItem.apply {
            remarks = binding.etRemarks.text?.toString().orEmpty()
            locked = binding.chkLocked.isChecked
            domain = binding.etDomain.text?.toString()?.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ip = binding.etIp.text?.toString()?.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            process = binding.etProcess.text?.toString()?.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            protocol = binding.etProtocol.text?.toString()?.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            port = binding.etPort.text?.toString()?.nullIfBlank()
            network = binding.etNetwork.text?.toString()?.nullIfBlank()
            outboundTag = binding.spOutboundTag.text?.toString()?.trim().orEmpty().ifEmpty { TAG_PROXY }
        }

        if (rulesetItem.remarks.isNullOrEmpty()) {
            snackbarError(
                getString(R.string.sub_setting_remarks),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }

        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }


    private fun deleteServer(): Boolean {
        if (position >= 0) {
            showDeleteConfirmDialog(context = this, messageRes = R.string.del_routing_dialog_comfirm_message) {
                lifecycleScope.launch(Dispatchers.IO) {
                    SettingsManager.removeRoutingRuleset(position)
                    launch(Dispatchers.Main) {
                        finish()
                    }
                }
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delConfig = menu.findItem(R.id.del_config)

        if (position < 0) {
            delConfig?.isVisible = false
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
