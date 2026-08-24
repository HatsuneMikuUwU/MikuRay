package com.miku.ray.ui.routing


import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.ui.base.HelperBaseActivity
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.miku.ray.util.showBlur
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.databinding.ActivityRoutingSettingBinding
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.helper.SimpleItemTouchHelperCallback
import com.miku.ray.ui.userasset.UserAssetActivity
import com.miku.ray.ui.bottomsheet.RoutingMenuBottomSheet
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import com.miku.ray.util.showDeleteConfirmDialog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingActivity : HelperBaseActivity(), RoutingMenuBottomSheet.OnRoutingMenuOptionClickListener {
    private val binding by lazy { ActivityRoutingSettingBinding.inflate(layoutInflater) }
    private val ownerActivity: RoutingSettingActivity
        get() = this
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private lateinit var adapter: RoutingSettingRecyclerAdapter
    private var mItemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.routingScrollContent.applyEdgeToEdgeListInsets()
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.routing_settings_title), subtitle = getString(R.string.subtitle_routing_setting))

        adapter = RoutingSettingRecyclerAdapter(viewModel, ActivityAdapterListener())

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        updateEmptyState()
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_routing_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.add_rule -> startActivity(Intent(this, RoutingEditActivity::class.java)).let { true }
        R.id.action_more_menu -> {
            val bottomSheet = RoutingMenuBottomSheet()
            bottomSheet.show(supportFragmentManager, RoutingMenuBottomSheet.TAG)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onRoutingMenuOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.import_predefined_rulesets -> importPredefined()
            R.id.import_rulesets_from_clipboard -> importFromClipboard()
            R.id.import_rulesets_from_qrcode -> importQRcode()
            R.id.export_rulesets_to_clipboard -> export2Clipboard()
            R.id.menu_user_asset_setting -> startActivity(Intent(this, UserAssetActivity::class.java))
        }
    }

    private fun importPredefined() {
        AlertDialog.Builder(this)
            .setTitle(R.string.routing_settings_import_predefined_rulesets_title)
            .setIcon(RemixR.drawable.rmx_device_router_line)
            .setItems(resources.getStringArray(R.array.preset_rulesets)) { _, i ->
            AlertDialog.Builder(this)
                .setTitle(R.string.routing_settings_import_predefined_rulesets_title)
                .setIcon(RemixR.drawable.rmx_error_warning_line)
                .setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    try {
                        lifecycleScope.launch(Dispatchers.IO) {
                            SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, i)
                            launch(Dispatchers.Main) {
                                refreshData()
                                snackbarSuccess(
                                    getString(R.string.routing_settings_import_predefined_rulesets),
                                    title = getString(R.string.title_alerter_success)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                }
                .showBlur()
        }.showBlur()
    }

    private fun importFromClipboard() {
        AlertDialog.Builder(this)
            .setTitle(R.string.routing_settings_import_rulesets_from_clipboard_title)
            .setIcon(RemixR.drawable.rmx_error_warning_line)
            .setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clipboard = try {
                    Utils.getClipboard(this)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to get clipboard content", e)
                    snackbarError(
                        getString(R.string.routing_settings_import_rulesets_from_clipboard),
                        title = getString(R.string.title_alerter_error)
                    )
                    return@setPositiveButton
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(clipboard)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            snackbarSuccess(
                                getString(R.string.routing_settings_import_rulesets_from_clipboard),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            snackbarError(
                                getString(R.string.routing_settings_import_rulesets_from_clipboard),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
            }
            .showBlur()
    }

    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importRulesetsFromQRcode(scanResult)
            }
        }
        return true
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            snackbarError(
                getString(R.string.routing_settings_export_rulesets_to_clipboard),
                title = getString(R.string.title_alerter_error)
            )
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            snackbarSuccess(
                getString(R.string.routing_settings_export_rulesets_to_clipboard),
                title = getString(R.string.title_alerter_success)
            )
        }
    }


    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        AlertDialog.Builder(this)
            .setTitle(R.string.routing_settings_import_rulesets_from_qrcode_title)
            .setIcon(RemixR.drawable.rmx_error_warning_line)
            .setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(qrcode)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            snackbarSuccess(
                                getString(R.string.routing_settings_import_rulesets_from_qrcode),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            snackbarError(
                                getString(R.string.routing_settings_import_rulesets_from_qrcode),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
            }
            .showBlur()
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        viewModel.reload()
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.routingScrollContent.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(ownerActivity, RoutingEditActivity::class.java)
                    .putExtra("position", position)
            )
        }

        override fun onRemove(guid: String, position: Int) {
            if (position !in 0 until adapter.itemCount) return
            showDeleteConfirmDialog(
                context = ownerActivity,
                messageRes = R.string.del_routing_dialog_comfirm_message
            ) {
                val remarks = viewModel.getAll().getOrNull(position)?.remarks.orEmpty()
                viewModel.remove(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, adapter.itemCount - position)
                updateEmptyState()
                snackbarSuccess(
                    message = remarks,
                    title = getString(R.string.title_alerter_success)
                )
            }
        }

        override fun onShare(url: String) {
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
