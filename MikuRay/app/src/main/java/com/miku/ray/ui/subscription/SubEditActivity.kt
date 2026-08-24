package com.miku.ray.ui.subscription

import com.miku.ray.util.TabIconPickerAdapter
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.remixicon.R as RemixR
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.util.showDeleteConfirmDialog
import androidx.lifecycle.lifecycleScope
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivitySubEditBinding
import com.miku.ray.databinding.DialogTabIconPickerBinding
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SubscriptionUpdater
import com.miku.ray.util.Utils
import com.miku.ray.util.WindowBlurUtils
import com.miku.ray.util.getColorAttr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubEditActivity : BaseActivity() {
    private val binding by lazy { ActivitySubEditBinding.inflate(layoutInflater) }

    private var del_config: MenuItem? = null
    private var save_config: MenuItem? = null

    private val editSubId by lazy { intent.getStringExtra("subId").orEmpty() }

    private var selectedIconDrawable: String? = null

    private val tabIcons: List<String> = TabIconPickerAdapter.DEFAULT_ICONS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.editScrollContent.applyEdgeToEdgeListInsets()

        setupToolbar(binding.toolbar, showHomeAsUp = true, title = getString(R.string.title_sub_setting), subtitle = getString(R.string.subtitle_sub_edit))

        setupProfileRemarkInputs()
        setupTabIconField()

        SettingsChangeManager.makeSetupGroupTab()
        val subItem = MmkvManager.decodeSubscription(editSubId)
        if (subItem != null) {
            bindingServer(subItem)
        } else {
            clearServer()
        }
    }

    private fun setupTabIconField() {
        binding.etTabIcon.setOnClickListener { showIconPickerDialog() }
        binding.tilTabIcon.setEndIconOnClickListener { showIconPickerDialog() }
    }

    private fun showIconPickerDialog() {
        val dialogBinding = DialogTabIconPickerBinding.inflate(layoutInflater)
        val rowNone = dialogBinding.rowNone
        val checkNone = dialogBinding.checkNone
        val rv = dialogBinding.rvIcons

        val adapter = TabIconPickerAdapter(
            context     = this,
            icons       = tabIcons,
            selectedIcon = selectedIconDrawable,
            onSelect    = { name ->
                applyIconSelection(name)
                dialog?.dismiss()
            }
        )
        rv.layoutManager = GridLayoutManager(this, 5)
        rv.adapter = adapter

        fun refreshNoneCheck() {
            val noneSelected = selectedIconDrawable == null
            checkNone.visibility = if (noneSelected) android.view.View.VISIBLE else android.view.View.GONE
            val tint = if (noneSelected) getColorAttr("colorPrimary") else 0
            checkNone.imageTintList = ColorStateList.valueOf(tint)
        }
        refreshNoneCheck()

        rowNone.setOnClickListener {
            applyIconSelection(null)
            dialog?.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sub_setting_tab_icon)
            .setIcon(RemixR.drawable.rmx_apps_line)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog!!.window)
        dialog!!.show()
    }

    private var dialog: androidx.appcompat.app.AlertDialog? = null

    private fun applyIconSelection(iconName: String?) {
        selectedIconDrawable = iconName
        if (iconName == null) {
            binding.etTabIcon.setText(getString(R.string.sub_tab_icon_none))
            binding.tilTabIcon.setStartIconDrawable(RemixR.drawable.rmx_apps_line)
            binding.tilTabIcon.setStartIconTintList(
                ColorStateList.valueOf(getColorAttr("colorOnSurfaceVariant"))
            )
        } else {
            val resId = resources.getIdentifier(iconName, "drawable", packageName)
            val label = TabIconPickerAdapter.labelFor(iconName)
            binding.etTabIcon.setText(label)
            if (resId != 0) {
                binding.tilTabIcon.setStartIconDrawable(resId)
                binding.tilTabIcon.setStartIconTintList(
                    ColorStateList.valueOf(getColorAttr("colorOnSurfaceVariant"))
                )
            }
        }
    }

    private fun bindingServer(subItem: SubscriptionItem): Boolean {
        binding.etRemarks.setText(Utils.getEditable(subItem.remarks))
        binding.etUrl.setText(Utils.getEditable(subItem.url))
        binding.etUserAgent.setText(Utils.getEditable(subItem.userAgent))
        binding.etRequestHeaders.setText(Utils.getEditable(subItem.requestHeaders))
        binding.etFilter.setText(Utils.getEditable(subItem.filter))
        binding.etNetworkFilter.setText(Utils.getEditable(subItem.networkFilter))
        binding.etProtocolFilter.setText(Utils.getEditable(subItem.protocolFilter))
        binding.chkEnable.isChecked = subItem.enabled
        binding.autoUpdateCheck.isChecked = subItem.autoUpdate
        binding.etUpdateInterval.setText(Utils.getEditable(subItem.updateInterval.toString()))
        binding.allowInsecureUrl.isChecked = subItem.allowInsecureUrl
        binding.etPreProfile.setText(subItem.prevProfile, false)
        binding.etNextProfile.setText(subItem.nextProfile, false)
        applyIconSelection(subItem.tabIcon)
        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.etUrl.text = null
        binding.etUserAgent.text = null
        binding.etRequestHeaders.text = null
        binding.etFilter.text = null
        binding.etNetworkFilter.text = null
        binding.etProtocolFilter.text = null
        binding.chkEnable.isChecked = true
        binding.autoUpdateCheck.isChecked = false
        binding.etUpdateInterval.text = null
        binding.allowInsecureUrl.isChecked = false
        binding.etPreProfile.text = null
        binding.etNextProfile.text = null
        applyIconSelection(null)
        return true
    }


    private fun setupProfileRemarkInputs() {
        val suggestions = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN,
            )
        )
        setupProfileRemarkInput(binding.etPreProfile, suggestions)
        setupProfileRemarkInput(binding.etNextProfile, suggestions)
    }

    private fun setupProfileRemarkInput(
        input: AutoCompleteTextView,
        suggestions: List<String>
    ) {
        val noneOption = ""
        val items = listOf(noneOption) + suggestions
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        input.setAdapter(adapter)
        input.setOnItemClickListener { _, _, position, _ ->
            if (items[position] == noneOption) {
                input.setText("", false)
            }
        }
    }


    private fun saveServer(): Boolean {
        val subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()

        subItem.remarks = binding.etRemarks.text?.toString().orEmpty()
        subItem.url = binding.etUrl.text?.toString().orEmpty()
        subItem.userAgent = binding.etUserAgent.text?.toString().orEmpty()
        subItem.requestHeaders = binding.etRequestHeaders.text?.toString().orEmpty()
        subItem.filter = binding.etFilter.text?.toString().orEmpty()
        subItem.networkFilter = binding.etNetworkFilter.text?.toString().orEmpty()
        subItem.protocolFilter = binding.etProtocolFilter.text?.toString().orEmpty()
        subItem.enabled = binding.chkEnable.isChecked
        subItem.autoUpdate = binding.autoUpdateCheck.isChecked

        val intervalInput = binding.etUpdateInterval.text?.toString()?.trim().orEmpty()
        val intervalMinutes = intervalInput.toLongOrNull()

        if (subItem.autoUpdate) {
            if (intervalMinutes == null) {
                subItem.updateInterval = SubscriptionItem().updateInterval
            } else if (intervalMinutes < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
                snackbarError(
                    getString(R.string.toast_invalid_update_interval),
                    title = getString(R.string.title_alerter_error)
                )
                return false
            } else {
                subItem.updateInterval = intervalMinutes
            }
        } else {
            if (intervalMinutes != null && intervalMinutes >= AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
                subItem.updateInterval = intervalMinutes
            }
        }

        subItem.prevProfile = binding.etPreProfile.text?.toString().orEmpty()
        subItem.nextProfile = binding.etNextProfile.text?.toString().orEmpty()
        subItem.allowInsecureUrl = binding.allowInsecureUrl.isChecked
        subItem.tabIcon = selectedIconDrawable

        if (TextUtils.isEmpty(subItem.remarks)) {
            snackbarError(
                getString(R.string.sub_setting_remarks),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }
        if (subItem.url.isNotEmpty()) {
            if (!Utils.isValidUrl(subItem.url)) {
                snackbarError(
                    getString(R.string.toast_invalid_url),
                    title = getString(R.string.title_alerter_error)
                )
                return false
            }
            if (!Utils.isValidSubUrl(subItem.url)) {
                snackbarError(
                    getString(R.string.toast_insecure_url_protocol),
                    title = getString(R.string.title_alerter_error)
                )
                if (!subItem.allowInsecureUrl) return false
            }
        }

        MmkvManager.encodeSubscription(editSubId, subItem)
        SubscriptionUpdater.syncOne(subId = editSubId)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }


    private fun deleteServer(): Boolean {
        if (editSubId.isNotEmpty()) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                showDeleteConfirmDialog(context = this, messageRes = R.string.del_sub_dialog_comfirm_message) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        SettingsManager.removeSubscriptionWithDefault(editSubId)
                        launch(Dispatchers.Main) {
                            toastSuccess(R.string.toast_delete_success)
                            finish()
                        }
                    }
                }
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    SettingsManager.removeSubscriptionWithDefault(editSubId)
                    launch(Dispatchers.Main) {
                        toastSuccess(R.string.toast_delete_success)
                        finish()
                    }
                }
            }
        }
        return true
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        del_config = menu.findItem(R.id.del_config)
        save_config = menu.findItem(R.id.save_config)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> { deleteServer(); true }
        R.id.save_config -> { saveServer(); true }
        else -> super.onOptionsItemSelected(item)
    }


    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }
}
