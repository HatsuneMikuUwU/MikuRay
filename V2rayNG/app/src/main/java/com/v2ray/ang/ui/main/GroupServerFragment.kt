package com.v2ray.ang.ui.main
import com.v2ray.ang.ui.base.BaseFragment
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.core.view.isVisible
import com.v2ray.ang.util.showDeleteConfirmDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.databinding.FragmentGroupServerBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.server.ServerCustomConfigActivity
import com.v2ray.ang.ui.server.ServerGroupActivity
import com.v2ray.ang.ui.server.ServerHysteria2Activity
import com.v2ray.ang.ui.server.ServerProxyChainActivity
import com.v2ray.ang.ui.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.server.ServerSocksActivity
import com.v2ray.ang.ui.server.ServerTrojanActivity
import com.v2ray.ang.ui.server.ServerVlessActivity
import com.v2ray.ang.ui.server.ServerVmessActivity
import com.v2ray.ang.ui.server.ServerWireguardActivity
import com.v2ray.ang.extension.snackbarDefault
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.util.showBlur
import com.miku.ray.remixicon.R as RemixR

class GroupServerFragment : BaseFragment<FragmentGroupServerBinding>() {
    private val ownerActivity: MainActivity
        get() = requireActivity() as MainActivity
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: MainRecyclerAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private val subId: String by lazy { arguments?.getString(ARG_SUB_ID).orEmpty() }
    private val scrollButtonHideHandler = Handler(Looper.getMainLooper())
    private var scrollButtonVisible = false
    private val hideScrollButtonRunnable = Runnable { setScrollButtonsVisible(false) }
    private var bottomStatusCard: View? = null
    private val bottomStatusLayoutListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncButtonMarginWithBottomStatus() }
    private var hasLoadedData = false

    companion object {
        private const val ARG_SUB_ID = "subscriptionId"
        private const val SCROLL_BUTTON_AUTO_HIDE_DELAY_MS = 1500L
        fun newInstance(subId: String) = GroupServerFragment().apply {
            arguments = Bundle().apply { putString(ARG_SUB_ID, subId) }
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentGroupServerBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        hasLoadedData = false
        adapter = MainRecyclerAdapter(mainViewModel, ActivityAdapterListener())
        adapter.setGridMode(isDoubleColumnEnabled())
        binding.recyclerView.setHasFixedSize(true)

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount())
        applyGridEdgePadding(isDoubleColumnEnabled())

        binding.recyclerView.adapter = adapter

        val animator = binding.recyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }

        itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter, allowSwipe = false))
        itemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        mainViewModel.updateListAction.observe(viewLifecycleOwner) { index ->
            if (mainViewModel.subscriptionId != subId) {
                return@observe
            }
            adapter.setData(mainViewModel.serversCache, index)
            hasLoadedData = true
            updateEmptyState()
        }

        binding.btnScrollToSelected.setOnClickListener {
            ownerActivity.locateSelectedServer()
            scrollButtonHideHandler.removeCallbacks(hideScrollButtonRunnable)
            setScrollButtonsVisible(false)
        }

        binding.btnScrollToTop.setOnClickListener {
            binding.recyclerView.smoothScrollToPosition(0)
            scrollButtonHideHandler.removeCallbacks(hideScrollButtonRunnable)
            setScrollButtonsVisible(false)
        }

        binding.btnScrollToBottom.setOnClickListener {
            val lastPosition = adapter.itemCount - 1
            if (lastPosition >= 0) {
                binding.recyclerView.smoothScrollToPosition(lastPosition)
            }
            scrollButtonHideHandler.removeCallbacks(hideScrollButtonRunnable)
            setScrollButtonsVisible(false)
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy == 0) return
                if (isHideScrollButtonsEnabled()) return
                binding.btnScrollToSelected.isVisible = !MmkvManager.getSelectServer().isNullOrEmpty()
                setScrollButtonsVisible(true)
                scrollButtonHideHandler.removeCallbacks(hideScrollButtonRunnable)
                scrollButtonHideHandler.postDelayed(hideScrollButtonRunnable, SCROLL_BUTTON_AUTO_HIDE_DELAY_MS)
            }
        })

        bottomStatusCard = ownerActivity.findViewById(R.id.card_bottom_status)
        bottomStatusCard?.addOnLayoutChangeListener(bottomStatusLayoutListener)
        bottomStatusCard?.post { syncButtonMarginWithBottomStatus() }

        updateEmptyState()
    }

    private fun syncButtonMarginWithBottomStatus() {
        if (!isAdded || view == null) return
        val statusCard = bottomStatusCard ?: return
        val container = binding.layoutScrollButtons
        if (statusCard.height <= 0) return

        val cardMarginBottom = (statusCard.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        val gap = (16 * resources.displayMetrics.density).toInt()
        val desiredMargin = statusCard.height + cardMarginBottom + gap

        val containerParams = container.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (containerParams.bottomMargin != desiredMargin) {
            containerParams.bottomMargin = desiredMargin
            container.layoutParams = containerParams
        }
    }

    private fun setScrollButtonsVisible(wantVisible: Boolean) {
        val targetVisible = wantVisible && !isHideScrollButtonsEnabled()
        if (targetVisible == scrollButtonVisible) return
        scrollButtonVisible = targetVisible
        val container = binding.layoutScrollButtons
        container.clearAnimation()
        if (targetVisible) {
            container.visibility = View.VISIBLE
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        } else {
            container.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(150)
                .withEndAction { container.visibility = View.GONE }.start()
        }
    }

    override fun onDestroyView() {
        scrollButtonHideHandler.removeCallbacks(hideScrollButtonRunnable)
        scrollButtonVisible = false
        bottomStatusCard?.removeOnLayoutChangeListener(bottomStatusLayoutListener)
        bottomStatusCard = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.subscriptionIdChangedAsync(subId)

        applyGridModeState()

        if (isHideScrollButtonsEnabled()) {
            scrollButtonHideHandler.removeCallbacks(hideScrollButtonRunnable)
            setScrollButtonsVisible(false)
        }
    }

    private fun applyGridModeState() {
        val doubleColumnEnabled = isDoubleColumnEnabled()
        val layoutManager = binding.recyclerView.layoutManager as? GridLayoutManager
        val desiredSpanCount = if (doubleColumnEnabled) 2 else 1
        if (layoutManager != null && layoutManager.spanCount != desiredSpanCount) {
            layoutManager.spanCount = desiredSpanCount
        }
        adapter.setGridMode(doubleColumnEnabled)
        applyGridEdgePadding(doubleColumnEnabled)
    }

    private fun isDoubleColumnEnabled(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    }

    private fun isHideScrollButtonsEnabled(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_HIDE_SCROLL_BUTTONS, false)
    }

    private fun spanCount(): Int {
        return if (isDoubleColumnEnabled()) 2 else 1
    }

    private fun updateEmptyState() {
        if (!isAdded || view == null) return

        if (!hasLoadedData) {
            binding.layoutEmptyState.isVisible = false
            return
        }

        val isEmpty = adapter.isServerListEmpty
        binding.layoutEmptyState.isVisible = isEmpty
        binding.recyclerView.isVisible = !isEmpty
    }

    private fun applyGridEdgePadding(gridMode: Boolean) {
        val density = resources.displayMetrics.density
        val extraEdgePaddingPx = if (gridMode) (12 * density).toInt() else 0
        binding.recyclerView.setPadding(
            extraEdgePaddingPx,
            binding.recyclerView.paddingTop,
            extraEdgePaddingPx,
            binding.recyclerView.paddingBottom
        )
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val intent = Intent().putExtra("guid", guid)
            .putExtra("isRunning", mainViewModel.isRunning.value)
            .putExtra("createConfigType", profile.configType.value)
            .putExtra("subscriptionId", subId)
        when (profile.configType) {
            EConfigType.CUSTOM -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerCustomConfigActivity::class.java))
            }

            EConfigType.POLICYGROUP -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerGroupActivity::class.java))
            }

            EConfigType.PROXYCHAIN -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerProxyChainActivity::class.java))
            }

            EConfigType.VMESS -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerVmessActivity::class.java))
            }

            EConfigType.VLESS -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerVlessActivity::class.java))
            }

            EConfigType.TROJAN -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerTrojanActivity::class.java))
            }

            EConfigType.SHADOWSOCKS -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerShadowsocksActivity::class.java))
            }

            EConfigType.SOCKS, EConfigType.HTTP -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerSocksActivity::class.java))
            }

            EConfigType.WIREGUARD -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerWireguardActivity::class.java))
            }

            EConfigType.HYSTERIA2 -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerHysteria2Activity::class.java))
            }

            else -> {
                ownerActivity.startActivity(intent.setClass(ownerActivity, ServerVmessActivity::class.java))
            }
        }
    }

    private fun removeServer(guid: String, position: Int) {
        if (guid == MmkvManager.getSelectServer()) {
            ownerActivity.snackbarDefault(getString(R.string.toast_action_not_allowed), title = getString(R.string.title_alerter_info))
            return
        }

        if (MmkvManager.isServerPinned(guid)) {
            ownerActivity.snackbarDefault(getString(R.string.toast_pinned_server_delete_blocked), title = getString(R.string.title_alerter_info))
            return
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            showDeleteConfirmDialog(context = ownerActivity, messageRes = R.string.del_config_dialog_comfirm_message) {
                removeServerSub(guid, position)
            }
        } else {
            removeServerSub(guid, position)
        }
    }

    private fun removeServerSub(guid: String, position: Int) {
        mainViewModel.removeServer(guid)
        adapter.removeServerSub(guid, position)
        ownerActivity.refreshGroupTabTitles()
        updateEmptyState()
    }

    private fun togglePinServer(guid: String, currentlyPinned: Boolean) {
        val actionLabel = getString(
            if (currentlyPinned) R.string.action_unpin_server else R.string.action_pin_server
        )
        MaterialAlertDialogBuilder(ownerActivity)
            .setTitle(R.string.title_pin_server)
            .setIcon(RemixR.drawable.rmx_map_pushpin_line)
            .setItems(arrayOf(actionLabel)) { _, _ ->
                val nowPinned = mainViewModel.togglePinServer(guid)
                ownerActivity.snackbarSuccess(
                    getString(if (nowPinned) R.string.toast_server_pinned else R.string.toast_server_unpinned),
                    title = getString(R.string.title_alerter_success)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
    }

    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            MmkvManager.setSelectServer(guid)
            val fromPosition = mainViewModel.getPosition(selected.orEmpty())
            val toPosition = mainViewModel.getPosition(guid)
            adapter.setSelectServer(fromPosition, toPosition)

            LauncherManager.restartService(ownerActivity)
        }
    }

    private inner class ActivityAdapterListener : MainAdapterListener {
        override fun onEdit(guid: String, position: Int) {
        }

        override fun onShare(url: String) {
        }

        override fun onRefreshData() {
        }

        override fun onRemove(guid: String, position: Int) {
            removeServer(guid, position)
        }

        override fun onEdit(guid: String, position: Int, profile: ProfileItem) {
            editServer(guid, profile)
        }

        override fun onSelectServer(guid: String) {
            setSelectServer(guid)
        }

        override fun onShare(guid: String, profile: ProfileItem, position: Int, more: Boolean) {
            ownerActivity.showShareBottomSheet(guid, profile.configType.value)
        }

        override fun onPinToggle(guid: String, position: Int, isPinned: Boolean) {
            togglePinServer(guid, isPinned)
        }
    }

    fun refreshDisplayPrefs() {
        if (!isAdded) return
        applyGridModeState()
        adapter.refreshDisplayPrefs()
    }

    fun scrollToSelectedServer() {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            ownerActivity.snackbarDefault(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_info))
            return
        }

        val serversCache = mainViewModel.serversCache
        val position = serversCache.indexOfFirst { it.guid == selectedGuid }
        val recyclerView = binding.recyclerView

        if (position >= 0) {
            val layoutManager = recyclerView.layoutManager as? GridLayoutManager

            if (layoutManager != null) {
                recyclerView.post {
                    layoutManager.scrollToPositionWithOffset(position, recyclerView.height / 3)
                }
            } else {
                recyclerView.smoothScrollToPosition(position)
            }
        } else {
            ownerActivity.snackbarDefault(getString(R.string.toast_server_not_found_in_group), title = getString(R.string.title_alerter_info))
        }
    }
}
