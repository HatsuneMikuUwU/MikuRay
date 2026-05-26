package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.util.showBlur
import com.v2ray.ang.util.showDeleteConfirmDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.alert
import com.v2ray.ang.extension.alertSuccess
import com.v2ray.ang.extension.alertError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.ui.bottomsheet.AddConfigBottomSheet
import com.v2ray.ang.ui.bottomsheet.MainMenuBottomSheet
import com.v2ray.ang.ui.bottomsheet.MoreMenuBottomSheet
import com.v2ray.ang.ui.bottomsheet.ShareConfigBottomSheet
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.v2ray.ang.ui.preference.activity.SettingsActivity
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class MainActivity : HelperBaseActivity(),
    MainMenuBottomSheet.OnOptionClickListener,
    AddConfigBottomSheet.OnAddConfigClickListener,
    MoreMenuBottomSheet.OnMoreOptionClickListener,
    ShareConfigBottomSheet.OnShareOptionClickListener {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    private val TAG_HOME_BANNER_DEFAULT = "DEFAULT_HOME_BANNER"

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setupBottomAppBar()
        setupViewPager()
        setupListeners()
        setupInlineSearchView()
        setupGroupTab()
        setupViewModel()
        setupBannerHome()

        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    override fun onContentChanged() {
        super.onContentChanged()
        
        val root = findViewById<android.view.View>(R.id.main_content) ?: return
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            
            view.updatePadding(
                top   = 0, 
                left  = maxOf(systemBars.left,  displayCutout.left),
                right = maxOf(systemBars.right, displayCutout.right),
                bottom = 0
            )
            
            val bottomInset = maxOf(systemBars.bottom, displayCutout.bottom)
            binding.bottomAppBar.updatePadding(bottom = bottomInset)

            val headerContent = view.findViewById<android.view.View>(R.id.header_content)
            headerContent?.updatePadding(top = systemBars.top)
            
            insets
        }
    }

    private fun setupBannerHome() {
        val bannerHome = binding.bannerHome
        val headerImage = binding.headerImage
        val headerTopRow = binding.headerTopRow

        headerImage.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val paddingTopWithBanner = (16 * resources.displayMetrics.density).toInt()
        val paddingTopNoBanner = 0

        fun applyBannerVisibility(show: Boolean) {
            bannerHome.visibility = if (show) View.VISIBLE else View.GONE
            val topPad = if (show) paddingTopWithBanner else paddingTopNoBanner
            headerTopRow.setPadding(
                headerTopRow.paddingLeft,
                topPad,
                headerTopRow.paddingRight,
                headerTopRow.paddingBottom
            )
        }

        fun loadBannerImage() {
            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
            val targetTag = if (uriString.isNullOrBlank()) TAG_HOME_BANNER_DEFAULT else uriString
            if (headerImage.tag == targetTag) return
            if (!uriString.isNullOrBlank()) {
                Glide.with(this)
                    .load(Uri.parse(uriString))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .error(R.drawable.uwu_banner_image_about)
                    .into(headerImage)
            } else {
                Glide.with(this).clear(headerImage)
                headerImage.setImageResource(R.drawable.uwu_banner_image_about)
            }
            headerImage.tag = targetTag
        }

        val show = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
        applyBannerVisibility(show)
        loadBannerImage()

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED -> {
                        val showNow = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
                        applyBannerVisibility(showNow)
                        loadBannerImage()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter(AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    private fun setupBottomAppBar() {
        val bottomBarBackground = binding.bottomAppBar.background as MaterialShapeDrawable
        bottomBarBackground.shapeAppearanceModel = bottomBarBackground.shapeAppearanceModel.toBuilder()
            .setTopRightCorner(CornerFamily.ROUNDED, 48f)
            .setTopLeftCorner(CornerFamily.ROUNDED, 48f)
            .build()

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = false
    }

    private fun setupViewPager() {
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.apply {
            adapter = groupPagerAdapter
            isUserInputEnabled = true
        }
    }

    private fun setupListeners() {
        binding.fab.setOnClickListener { handleFabAction() }
        binding.bottomAppBar.setOnClickListener { handleLayoutTestClick() }
        
        binding.btnHome.setOnClickListener {
            MainMenuBottomSheet().show(supportFragmentManager, MainMenuBottomSheet.TAG)
        }
        
        binding.btnAddConfig.setOnClickListener {
            AddConfigBottomSheet().show(supportFragmentManager, AddConfigBottomSheet.TAG)
        }
        
        binding.btnMoreMenu.setOnClickListener {
            MoreMenuBottomSheet().show(supportFragmentManager, MoreMenuBottomSheet.TAG)
        }
        
        binding.btnAddSub.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, SubEditActivity::class.java))
        }
    }

    private fun setupInlineSearchView() {
        binding.searchViewInline.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                mainViewModel.filterConfig(newText.orEmpty())
                return false
            }
        })

        binding.searchViewInline.setOnCloseListener {
            mainViewModel.filterConfig("")
            false
        }
    }

    override fun onOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.menu_sub_setting -> requestActivityLauncher.launch(Intent(this, SubSettingActivity::class.java))
            R.id.menu_per_app_proxy_settings -> requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java))
            R.id.menu_routing_setting -> requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
            R.id.menu_user_asset_setting -> requestActivityLauncher.launch(Intent(this, UserAssetActivity::class.java))
            R.id.menu_settings -> requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.menu_logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.menu_backup_restore -> requestActivityLauncher.launch(Intent(this, BackupActivity::class.java))
            R.id.menu_about -> startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onAddConfigOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.import_qrcode -> importQRcode()
            R.id.import_clipboard -> importClipboard()
            R.id.import_local -> importConfigLocal()
            R.id.import_manually_policy_group -> importManually(EConfigType.POLICYGROUP.value)
            R.id.import_manually_proxy_chain -> importManually(EConfigType.PROXYCHAIN.value)
            R.id.import_manually_vmess -> importManually(EConfigType.VMESS.value)
            R.id.import_manually_vless -> importManually(EConfigType.VLESS.value)
            R.id.import_manually_ss -> importManually(EConfigType.SHADOWSOCKS.value)
            R.id.import_manually_socks -> importManually(EConfigType.SOCKS.value)
            R.id.import_manually_http -> importManually(EConfigType.HTTP.value)
            R.id.import_manually_trojan -> importManually(EConfigType.TROJAN.value)
            R.id.import_manually_wireguard -> importManually(EConfigType.WIREGUARD.value)
            R.id.import_manually_hysteria2 -> importManually(EConfigType.HYSTERIA2.value)
        }
    }

    override fun onMoreOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.export_all -> exportAll()
            R.id.ping_all -> {
                alert(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()), title = getString(R.string.title_ping_all_server))
                mainViewModel.testAllTcping()
            }
            R.id.real_ping_all -> {
                alert(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()), title = getString(R.string.title_real_ping_all_server))
                mainViewModel.testAllRealPing()
            }
            R.id.service_restart -> restartV2Ray()
            R.id.del_all_config -> delAllConfig()
            R.id.del_duplicate_config -> delDuplicateConfig()
            R.id.del_invalid_config -> delInvalidConfig()
            R.id.sort_by_test_results -> sortByTestResults()
            R.id.sub_update -> importConfigViaSub()
            R.id.locate_selected_config -> locateSelectedServer()
        }
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(isLoading = false, isRunning = isRunning)
        }
        
        mainViewModel.alertAction.observe(this) { (isSuccess, message) ->
            if (isSuccess) {
                alertSuccess(message, title = getString(R.string.title_alerter_success))
            } else {
                alertError(message, title = getString(R.string.title_alerter_error))
            }
        }

        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let {
                tab.text = it.remarks
                tab.tag = it.id
            }
        }.also { it.attach() }

        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }
            .takeIf { it >= 0 } ?: (groups.size - 1)
            
        if (targetIndex >= 0) {
            binding.viewPager.setCurrentItem(targetIndex, false)
        }
        
        val hasAnyGroup = groups.isNotEmpty()
        
        binding.layoutTabWrapper.isVisible = hasAnyGroup
        binding.tabGroup.isVisible = hasAnyGroup
        (binding.tabGroup.parent as? android.view.View)?.isVisible = hasAnyGroup 
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.isRunning.value == true) {
            setTestState(getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            alertError(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_error)) 
            applyRunningState(isLoading = false, isRunning = false) 
            return
        }
        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) {
            binding.fab.setImageResource(R.drawable.ic_fab_check)
            return
        }

        if (isRunning) {
            binding.fab.setImageResource(R.drawable.ic_stop_24dp)
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            setTestState(getString(R.string.connection_connected))
            binding.bottomAppBar.isFocusable = true
        } else {
            binding.fab.setImageResource(R.drawable.ic_play_24dp)
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            setTestState(getString(R.string.connection_not_connected))
            binding.bottomAppBar.isFocusable = false
        }
    }

    private fun importManually(createConfigType: Int) {
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
        } else {
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            scanResult?.let { importBatchConfig(it) }
        }
        return true
    }

    private fun importClipboard(): Boolean {
        return try {
            Utils.getClipboard(this)?.let { importBatchConfig(it) }
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            false
        }
    }

    private fun importBatchConfig(server: String?) {
        if (server.isNullOrEmpty()) return
        
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            alertSuccess(getString(R.string.title_import_config_count, count), title = getString(R.string.title_alerter_success))
                            mainViewModel.reloadServerList()
                        }
                        countSub > 0 -> setupGroupTab()
                        else -> alertError(getString(R.string.import_configuration), title = getString(R.string.title_alerter_error))
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    alertError(getString(R.string.import_configuration), title = getString(R.string.title_alerter_error))
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    private fun importConfigLocal(): Boolean {
        return try {
            showFileChooser()
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            false
        }
    }

    fun importConfigViaSub(): Boolean {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            withContext(Dispatchers.Main) {
                when {
                    result.successCount + result.failureCount + result.skipCount == 0 -> {
                        alert(getString(R.string.title_update_subscription_no_subscription), title = getString(R.string.title_sub_update))
                    }
                    result.successCount > 0 && result.failureCount + result.skipCount == 0 -> {
                        alertSuccess(getString(R.string.title_update_config_count, result.configCount), title = getString(R.string.title_sub_update))
                    }
                    else -> {
                        alert(
                            getString(
                                R.string.title_update_subscription_result,
                                result.configCount, result.successCount, result.failureCount, result.skipCount
                            ),
                            title = getString(R.string.title_sub_update)
                        )
                    }
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                }
                hideLoading()
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            withContext(Dispatchers.Main) {
                if (ret > 0) alertSuccess(getString(R.string.title_export_config_count, ret), title = getString(R.string.title_alerter_success))
                else alertError(getString(R.string.action_export), title = getString(R.string.title_alerter_error))
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                val ret = mainViewModel.removeAllServer()
                withContext(Dispatchers.Main) {
                    mainViewModel.reloadServerList()
                    alertSuccess(getString(R.string.title_del_config_count, ret), title = getString(R.string.title_alerter_success))
                    hideLoading()
                }
            }
        }
    }

    private fun delDuplicateConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                val ret = mainViewModel.removeDuplicateServer()
                withContext(Dispatchers.Main) {
                    mainViewModel.reloadServerList()
                    alertSuccess(getString(R.string.title_del_duplicate_config_count, ret), title = getString(R.string.title_alerter_success))
                    hideLoading()
                }
            }
        }
    }

    private fun delInvalidConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_invalid_config_comfirm) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                val ret = mainViewModel.removeInvalidServer()
                withContext(Dispatchers.Main) {
                    mainViewModel.reloadServerList()
                    alertSuccess(getString(R.string.title_del_config_count, ret), title = getString(R.string.title_alerter_success))
                    hideLoading()
                }
            }
        }
    }

    private fun sortByTestResults() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            withContext(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
            }
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            uri?.let { readContentFromUri(it) }
        }
    }

    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                importBatchConfig(input.bufferedReader().readText())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    private fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            alert(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_info))
            return
        }

        val targetGroupIndex = groupPagerAdapter.groups.indexOfFirst { it.id == targetSubscriptionId }
            if (targetGroupIndex < 0) {
            alert(getString(R.string.toast_server_not_found_in_group), title = getString(R.string.title_alerter_info))
            return
        }

        if (binding.viewPager.currentItem != targetGroupIndex) {
            binding.viewPager.setCurrentItem(targetGroupIndex, true)
            binding.viewPager.postDelayed({ scrollToSelectedServer(targetGroupIndex) }, 1000)
        } else {
            scrollToSelectedServer(targetGroupIndex)
        }
    }

    private fun scrollToSelectedServer(groupIndex: Int) {
        val itemId = groupPagerAdapter.getItemId(groupIndex)
        val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment

        if (fragment?.isAdded == true && fragment.view != null) {
            fragment.scrollToSelectedServer()
        } else {
            alert(getString(R.string.toast_fragment_not_available), title = getString(R.string.title_alerter_info))
        }
    }

    fun showShareBottomSheet(guid: String, configType: Int) {
        ShareConfigBottomSheet.newInstance(guid, configType).show(supportFragmentManager, ShareConfigBottomSheet.TAG)
    }

    override fun onShareOptionClicked(optionId: Int, guid: String) {
        when (optionId) {
            R.id.share_qrcode -> {
                try {
                    val ivBinding = ItemQrcodeBinding.inflate(LayoutInflater.from(this))
                    ivBinding.ivQcode.setImageBitmap(AngConfigManager.share2QRCode(guid))
                    ivBinding.ivQcode.contentDescription = "QR Code"
                    MaterialAlertDialogBuilder(this).setView(ivBinding.root).showBlur()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Error when sharing QR code", e)
                }
            }
            R.id.share_clipboard -> {
                if (AngConfigManager.share2Clipboard(this, guid) == 0) {
                    alertSuccess(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_success))
                } else {
                    alertError(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_error))
                }
            }
            R.id.share_full_clipboard -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
                    withContext(Dispatchers.Main) {
                        if (result == 0) alertSuccess(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_success))
                        else alertError(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_error))
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        tabMediator?.detach()
        super.onDestroy()
    }
}
