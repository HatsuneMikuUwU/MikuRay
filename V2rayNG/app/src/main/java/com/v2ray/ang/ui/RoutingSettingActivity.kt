package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.util.showBlur
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ActivityRoutingSettingBinding
import com.v2ray.ang.extension.alertError
import com.v2ray.ang.extension.alertSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.ui.UserAssetActivity
import com.v2ray.ang.ui.bottomsheet.RoutingMenuBottomSheet
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.RoutingSettingsViewModel
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
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom,    displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.routing_settings_title))

        adapter = RoutingSettingRecyclerAdapter(viewModel, ActivityAdapterListener())

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)
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
        AlertDialog.Builder(this).setItems(resources.getStringArray(R.array.preset_rulesets)) { _, i ->
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    try {
                        lifecycleScope.launch(Dispatchers.IO) {
                            SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, i)
                            launch(Dispatchers.Main) {
                                refreshData()
                                alertSuccess(
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
                    //do nothing
                }
                .showBlur()
        }.showBlur()
    }

    private fun importFromClipboard() {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clipboard = try {
                    Utils.getClipboard(this)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to get clipboard content", e)
                    alertError(
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
                            alertSuccess(
                                getString(R.string.routing_settings_import_rulesets_from_clipboard),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            alertError(
                                getString(R.string.routing_settings_import_rulesets_from_clipboard),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
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
            alertError(
                getString(R.string.routing_settings_export_rulesets_to_clipboard),
                title = getString(R.string.title_alerter_error)
            )
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            alertSuccess(
                getString(R.string.routing_settings_export_rulesets_to_clipboard),
                title = getString(R.string.title_alerter_success)
            )
        }
    }


    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(qrcode)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            alertSuccess(
                                getString(R.string.routing_settings_import_rulesets_from_qrcode),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            alertError(
                                getString(R.string.routing_settings_import_rulesets_from_qrcode),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
            }
            .showBlur()
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        viewModel.reload()
        adapter.notifyDataSetChanged()
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(ownerActivity, RoutingEditActivity::class.java)
                    .putExtra("position", position)
            )
        }

        override fun onRemove(guid: String, position: Int) {
        }

        override fun onShare(url: String) {
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
