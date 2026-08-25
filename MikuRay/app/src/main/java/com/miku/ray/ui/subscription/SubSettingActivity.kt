package com.miku.ray.ui.subscription


import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.ui.base.BaseActivity
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.sidesheet.SideSheetDialog
import androidx.appcompat.app.AlertDialog
import com.miku.ray.util.WindowBlurUtils
import com.miku.ray.util.showBlur
import com.miku.ray.util.showDeleteConfirmDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.databinding.ActivitySubSettingBinding
import com.miku.ray.databinding.DialogSubUpdateOptionsBinding
import com.miku.ray.databinding.ItemQrcodeBinding
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.toastSuccess
import com.miku.ray.extension.toastError
import com.miku.ray.handler.MmkvManager
import com.miku.ray.helper.SimpleItemTouchHelperCallback
import com.miku.ray.util.LogUtil
import com.miku.ray.util.QRCodeDecoder
import com.miku.ray.util.Utils
import com.miku.ray.util.showSubUpdateDiffDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.miku.ray.ui.bottomsheet.ShareSubBottomSheet
import com.miku.ray.ui.bottomsheet.SortSubBottomSheet

class SubSettingActivity : BaseActivity(),
    ShareSubBottomSheet.OnShareSubOptionClickListener,
    SortSubBottomSheet.OnSortSubOptionClickListener {
    private val binding by lazy { ActivitySubSettingBinding.inflate(layoutInflater) }
    private val ownerActivity: SubSettingActivity
        get() = this
    private val viewModel: SubscriptionsViewModel by viewModels()
    private lateinit var adapter: SubSettingRecyclerAdapter
    private var mItemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.recyclerView.applyEdgeToEdgeListInsets()
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_sub_setting), subtitle = getString(R.string.subtitle_sub_setting))

        adapter = SubSettingRecyclerAdapter(viewModel, ActivityAdapterListener())

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
        R.id.sub_sort -> {
            SortSubBottomSheet.newInstance().show(supportFragmentManager, SortSubBottomSheet.TAG)
            true
        }
        else -> super.onOptionsItemSelected(item)
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
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showSubUpdateOptionsDialog() {
        val dialogBinding = DialogSubUpdateOptionsBinding.inflate(layoutInflater)

        dialogBinding.switchUpdateSubscription.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        dialogBinding.switchAutoTest.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
        dialogBinding.switchAutoRemoveInvalid.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        dialogBinding.switchAutoSort.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)
        dialogBinding.switchSendHwid.isChecked =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_SEND_HWID, false)

        dialogBinding.rowUpdateSubscription.setOnClickListener {
            dialogBinding.switchUpdateSubscription.toggle()
        }
        dialogBinding.rowAutoTest.setOnClickListener {
            dialogBinding.switchAutoTest.toggle()
        }
        dialogBinding.rowAutoRemoveInvalid.setOnClickListener {
            dialogBinding.switchAutoRemoveInvalid.toggle()
        }
        dialogBinding.rowAutoSort.setOnClickListener {
            dialogBinding.switchAutoSort.toggle()
        }
        dialogBinding.rowSendHwid.setOnClickListener {
            dialogBinding.switchSendHwid.toggle()
        }

        val sideSheetDialog = SideSheetDialog(this)
        sideSheetDialog.setContentView(dialogBinding.root)

        dialogBinding.btnCancel.setOnClickListener { sideSheetDialog.dismiss() }
        dialogBinding.btnOk.setOnClickListener {
            MmkvManager.encodeSettings(
                AppConfig.PREF_UPDATE_SUBSCRIPTION,
                dialogBinding.switchUpdateSubscription.isChecked
            )
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
            MmkvManager.encodeSettings(
                AppConfig.PREF_SEND_HWID,
                dialogBinding.switchSendHwid.isChecked
            )

            when {
                dialogBinding.switchAutoTest.isChecked -> {
                    viewModel.updateSubscriptionsMore()
                    toastSuccess(R.string.subscription_updater_job_tips)
                }
                dialogBinding.switchUpdateSubscription.isChecked -> {
                    showLoading()
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val result = viewModel.updateSubscriptionsOnly()
                            withContext(Dispatchers.Main) {
                                if (result.addedProfiles.isNotEmpty() || result.deletedProfiles.isNotEmpty()) {
                                    showSubUpdateDiffDialog(this@SubSettingActivity, result)
                                }
                                refreshData()
                                com.miku.ray.handler.SettingsChangeManager.makeSetupGroupTab()
                                hideLoading()
                            }
                        } catch (e: Exception) {
                            LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                            withContext(Dispatchers.Main) {
                                toastError(R.string.toast_failure)
                                hideLoading()
                            }
                        }
                    }
                }
            }
            sideSheetDialog.dismiss()
        }

        sideSheetDialog.window?.let { window ->
            WindowBlurUtils.applyWindowBlur(window)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                window.findViewById<View>(R.id.side_sheet_root)?.updatePadding(
                    top = statusBarInset,
                    bottom = navBarInset
                )
                insets
            }
        }

        sideSheetDialog.show()

        val sideSheetContainer = sideSheetDialog.findViewById<View>(com.google.android.material.R.id.m3_side_sheet)
        sideSheetContainer?.clipToOutline = true
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSortSubOptionClicked(order: Int) {
        viewModel.applySortOrder()
        adapter.notifyDataSetChanged()
        com.miku.ray.handler.SettingsChangeManager.makeSetupGroupTab()
    }

    override fun onShareSubOptionClicked(optionId: Int, url: String) {
        try {
            when (optionId) {
                R.id.share_qrcode -> {
                    val ivBinding = ItemQrcodeBinding.inflate(LayoutInflater.from(this))
                    ivBinding.ivQcode.setImageBitmap(
                        QRCodeDecoder.createQRCode(url)
                    )
                    AlertDialog.Builder(this)
                        .setTitle(R.string.title_qr_code)
                        .setIcon(RemixR.drawable.rmx_qr_code_line)
                        .setView(ivBinding.root).showBlur()
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
            val remarks = viewModel.getAll().find { it.guid == guid }?.subscription?.remarks.orEmpty()
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                showDeleteConfirmDialog(context = ownerActivity, messageRes = R.string.del_sub_dialog_comfirm_message) {
                    viewModel.remove(guid)
                    refreshData()
                    snackbarSuccess(
                        message = getString(R.string.toast_delete_success),
                        title = remarks
                    )
                }
            } else {
                viewModel.remove(guid)
                refreshData()
                snackbarSuccess(
                    message = getString(R.string.toast_delete_success),
                    title = remarks
                )
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
