package com.v2ray.ang.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.util.showDeleteConfirmDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivitySubEditBinding
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.alertError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.WindowBlurUtils
import com.v2ray.ang.util.getColorAttr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.v2ray.ang.util.SoftInputAssist

class SubEditActivity : BaseActivity() {
    private val binding by lazy { ActivitySubEditBinding.inflate(layoutInflater) }

    private var del_config: MenuItem? = null
    private var save_config: MenuItem? = null

    private val editSubId by lazy { intent.getStringExtra("subId").orEmpty() }

    private lateinit var softInputAssist: SoftInputAssist

    private var selectedIconDrawable: String? = null

    private val tabIcons: List<String> = TabIconPickerAdapter.DEFAULT_ICONS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        softInputAssist = SoftInputAssist(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_sub_setting))

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
        val dialogView = layoutInflater.inflate(R.layout.dialog_tab_icon_picker, null)
        val rowNone    = dialogView.findViewById<android.view.View>(R.id.row_none)
        val checkNone  = dialogView.findViewById<ImageView>(R.id.check_none)
        val rv         = dialogView.findViewById<RecyclerView>(R.id.rv_icons)

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
            .setView(dialogView)
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
            binding.tilTabIcon.setStartIconDrawable(R.drawable.filter_all)
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
        binding.etFilter.setText(Utils.getEditable(subItem.filter))
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
        binding.etFilter.text = null
        binding.chkEnable.isChecked = true
        binding.autoUpdateCheck.isChecked = false
        binding.etUpdateInterval.text = null
        binding.allowInsecureUrl.isChecked = false
        binding.etPreProfile.text = null
        binding.etNextProfile.text = null
        applyIconSelection(null)
        return true
    }

    // ── Profile remark autocomplete ─────────────────────────────────────────

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
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions)
        input.setAdapter(adapter)
    }

    // ── Save ────────────────────────────────────────────────────────────────

    private fun saveServer(): Boolean {
        val subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()

        subItem.remarks = binding.etRemarks.text?.toString().orEmpty()
        subItem.url = binding.etUrl.text?.toString().orEmpty()
        subItem.userAgent = binding.etUserAgent.text?.toString().orEmpty()
        subItem.filter = binding.etFilter.text?.toString().orEmpty()
        subItem.enabled = binding.chkEnable.isChecked
        subItem.autoUpdate = binding.autoUpdateCheck.isChecked

        val intervalInput = binding.etUpdateInterval.text?.toString()?.trim().orEmpty()
        val intervalMinutes = intervalInput.toLongOrNull()

        if (subItem.autoUpdate) {
            if (intervalMinutes == null) {
                subItem.updateInterval = SubscriptionItem().updateInterval
            } else if (intervalMinutes < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
                alertError(
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
            alertError(
                getString(R.string.sub_setting_remarks),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }
        if (subItem.url.isNotEmpty()) {
            if (!Utils.isValidUrl(subItem.url)) {
                alertError(
                    getString(R.string.toast_invalid_url),
                    title = getString(R.string.title_alerter_error)
                )
                return false
            }
            if (!Utils.isValidSubUrl(subItem.url)) {
                alertError(
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

    // ── Delete ──────────────────────────────────────────────────────────────

    private fun deleteServer(): Boolean {
        if (editSubId.isNotEmpty()) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                showDeleteConfirmDialog(context = this, messageRes = R.string.del_sub_dialog_comfirm_message) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        SettingsManager.removeSubscriptionWithDefault(editSubId)
                        launch(Dispatchers.Main) { finish() }
                    }
                }
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    SettingsManager.removeSubscriptionWithDefault(editSubId)
                    launch(Dispatchers.Main) { finish() }
                }
            }
        }
        return true
    }

    // ── Menu ────────────────────────────────────────────────────────────────

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

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onResume() {
        if (::softInputAssist.isInitialized) softInputAssist.onResume()
        super.onResume()
    }

    override fun onPause() {
        if (::softInputAssist.isInitialized) softInputAssist.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        if (::softInputAssist.isInitialized) softInputAssist.onDestroy()
        super.onDestroy()
    }
}
