package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.util.showBlur
import com.v2ray.ang.util.showDeleteConfirmDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ActivitySubSettingBinding
import com.v2ray.ang.databinding.DialogSubUpdateOptionsBinding
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SubscriptionsViewModel
import com.v2ray.ang.ui.bottomsheet.ShareSubBottomSheet

class SubSettingActivity : BaseActivity(), ShareSubBottomSheet.OnShareSubOptionClickListener {
    private val binding by lazy { ActivitySubSettingBinding.inflate(layoutInflater) }
    private val ownerActivity: SubSettingActivity
        get() = this
    private val viewModel: SubscriptionsViewModel by viewModels()
    private lateinit var adapter: SubSettingRecyclerAdapter
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
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_sub_setting))

        adapter = SubSettingRecyclerAdapter(viewModel, ActivityAdapterListener())

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
        menuInflater.inflate(R.menu.action_sub_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_config -> {
            startActivity(Intent(this, SubEditActivity::class.java))
            true
        }
        R.id.sub_update -> {
            showSubUpdateOptionsDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        viewModel.reload()
        adapter.notifyDataSetChanged()
    }

    /**
     * Shows a dialog to pick auto-test/remove-invalid/sort options, then enqueues
     * the background subscription update job via [SubscriptionsViewModel.updateSubscriptions].
     */
    private fun showSubUpdateOptionsDialog() {
        val dialogBinding = DialogSubUpdateOptionsBinding.inflate(layoutInflater)

        dialogBinding.switchAutoTest.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
        dialogBinding.switchAutoRemoveInvalid.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        dialogBinding.switchAutoSort.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)

        // Tapping anywhere on a row toggles its switch too, not just the thumb itself.
        dialogBinding.rowAutoTest.setOnClickListener {
            dialogBinding.switchAutoTest.toggle()
        }
        dialogBinding.rowAutoRemoveInvalid.setOnClickListener {
            dialogBinding.switchAutoRemoveInvalid.toggle()
        }
        dialogBinding.rowAutoSort.setOnClickListener {
            dialogBinding.switchAutoSort.toggle()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_sub_update)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                MmkvManager.encodeSettings(
                    AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION,
                    dialogBinding.switchAutoTest.isChecked
                )
                MmkvManager.encodeSettings(
                    AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST,
                    dialogBinding.switchAutoRemoveInvalid.isChecked
                )
                MmkvManager.encodeSettings(
                    AppConfig.PREF_AUTO_SORT_AFTER_TEST,
                    dialogBinding.switchAutoSort.isChecked
                )

                viewModel.updateSubscriptions()
                toast(getString(R.string.subscription_updater_job_tips))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
    }

    override fun onShareSubOptionClicked(optionId: Int, url: String) {
        try {
            when (optionId) {
                R.id.share_qrcode -> {
                    val ivBinding = ItemQrcodeBinding.inflate(LayoutInflater.from(this))
                    ivBinding.ivQcode.setImageBitmap(
                        QRCodeDecoder.createQRCode(url)
                    )
                    AlertDialog.Builder(this).setView(ivBinding.root).showBlur()
                }
                R.id.share_clipboard -> {
                    Utils.setClipboard(this, url)
                    snackbarSuccess(
                        getString(R.string.menu_item_export_proxy_app),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Share subscription failed", e)
            snackbarError(
                getString(R.string.menu_item_export_proxy_app),
                title = getString(R.string.title_alerter_error)
            )
        }
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(ownerActivity, SubEditActivity::class.java)
                    .putExtra("subId", guid)
            )
        }

        override fun onRemove(guid: String, position: Int) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                showDeleteConfirmDialog(context = ownerActivity, messageRes = R.string.del_sub_dialog_comfirm_message) {
                    viewModel.remove(guid)
                    refreshData()
                }
            } else {
                viewModel.remove(guid)
                refreshData()
            }
        }

        override fun onShare(url: String) {
            val bottomSheet = ShareSubBottomSheet.newInstance(url)
            bottomSheet.show(supportFragmentManager, ShareSubBottomSheet.TAG)
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
