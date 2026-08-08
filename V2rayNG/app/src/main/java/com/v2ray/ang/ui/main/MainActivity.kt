package com.v2ray.ang.ui.main

import com.v2ray.ang.ui.base.HelperBaseActivity
import com.v2ray.ang.ui.about.AboutActivity
import com.v2ray.ang.ui.subscription.SubSettingActivity
import com.v2ray.ang.ui.subscription.SubEditActivity
import com.v2ray.ang.ui.routing.RoutingSettingActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.ui.backup.BackupActivity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.TextView
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.server.ServerGroupActivity
import com.v2ray.ang.ui.server.ServerHysteria2Activity
import com.v2ray.ang.ui.server.ServerProxyChainActivity
import com.v2ray.ang.ui.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.server.ServerSocksActivity
import com.v2ray.ang.ui.server.ServerTrojanActivity
import com.v2ray.ang.ui.server.ServerVlessActivity
import com.v2ray.ang.ui.server.ServerVmessActivity
import com.v2ray.ang.ui.server.ServerWireguardActivity
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.snackbarDefault
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.bottomsheet.AddConfigBottomSheet
import com.v2ray.ang.ui.bottomsheet.MainMenuBottomSheet
import com.v2ray.ang.ui.bottomsheet.MoreMenuBottomSheet
import com.v2ray.ang.ui.bottomsheet.ShareConfigBottomSheet
import com.v2ray.ang.ui.preference.activity.SettingsActivity
import com.v2ray.ang.util.BlurBottomStatusController
import com.v2ray.ang.util.SearchChipGradientController
import com.v2ray.ang.util.getColorAttr
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.ui.weather.WeatherHelper
import com.v2ray.ang.ui.weather.WeatherForecastActivity
import com.v2ray.ang.util.showBlur
import com.v2ray.ang.util.showDeleteConfirmDialog
import com.v2ray.ang.util.showSubUpdateDiffDialog
import com.v2ray.ang.util.UrlTestProgressDialogController
import com.king.camera.scan.CameraScan
import com.v2ray.ang.ui.scanner.QrCaptureActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    
    private var bannerReceiver: android.content.BroadcastReceiver? = null 

    private val tabSelectedListener = object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
            applyTabSelectedStyle(tab, true, tab.position, binding.tabGroup.tabCount)
        }
        override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
            applyTabSelectedStyle(tab, false, tab.position, binding.tabGroup.tabCount)
        }
        override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
    }
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

    private val scanQrCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(CameraScan.SCAN_RESULT)?.let { importBatchConfig(it) }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        hideLoading()

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setupViewPager()
        setupListeners()
        setupInlineSearchView()
        setupGroupTab()
        setupViewModel()
        setupBannerHome()
        BlurBottomStatusController.applyState(this, binding)

        SubscriptionUpdater.sync()
        syncWeatherBackgroundUpdates()
        mainViewModel.reloadServerList()
        refreshGroupTabTitles(true)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    private fun weatherLocationReady(): Boolean =
        WeatherHelper.hasCustomLocation() || WeatherHelper.hasLocationPermission(this)

    private fun syncWeatherBackgroundUpdates() {
        val weatherEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)
        val canRunInBackground = WeatherHelper.hasCustomLocation() ||
            WeatherHelper.hasBackgroundLocationPermission(this)
        if (weatherEnabled && canRunInBackground) {
            WeatherHelper.scheduleBackgroundUpdates(this)
        } else if (!weatherEnabled) {
            WeatherHelper.cancelBackgroundUpdates(this)
        }
    }

    private var isColdStart = true

    private var lastIpStateText: String = ""
    private val urlTestProgressDialog: UrlTestProgressDialogController by lazy {
        UrlTestProgressDialogController(this) { mainViewModel.cancelRealPingTest() }
    }
    private var lastTrafficSpeedText: String = ""

    private fun refreshIpStateText() {
        val showRealtimeTraffic = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP, false)
        binding.tvIpState.text = if (showRealtimeTraffic) {
            if (mainViewModel.isRunning.value == true && lastTrafficSpeedText.isNotEmpty()) {
                lastTrafficSpeedText
            } else {
                "↑ ${0L.toSpeedString()}  ↓ ${0L.toSpeedString()}"
            }
        } else {
            lastIpStateText.ifEmpty { getString(R.string.ip_unknown) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSearchBarChip()
        refreshIpStateText()
        if (SettingsChangeManager.consumeRefreshDisplayPrefs()) {
            refreshAllGroupListDisplays()
        }
    }

    // Rebind every already-created group tab's list (not just the one currently
    // visible) so per-item display prefs toggled from Settings apply right away,
    // without needing to swipe through each ViewPager tab manually.
    private fun refreshAllGroupListDisplays() {
        for (i in groupPagerAdapter.groups.indices) {
            val itemId = groupPagerAdapter.getItemId(i)
            val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment
            fragment?.refreshDisplayPrefs()
        }
    }

    private fun refreshSearchBarChip() {
        val weatherEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)
        val totalTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP, false)

        SearchChipGradientController.applyState(this, binding)

        when {
            weatherEnabled -> {
                hideTotalTrafficChip()
                refreshWeatherChip()
            }
            totalTrafficEnabled -> {
                hideWeatherChipViews()
                refreshTotalTrafficChip()
            }
            else -> {
                binding.layoutWeatherChip.isVisible = false
            }
        }
    }

    private fun hideWeatherChipViews() {
        binding.ivWeatherIcon.isVisible = false
        binding.tvWeatherTemp.isVisible = false
    }

    private fun hideTotalTrafficChip() {
        binding.ivTotalTrafficIcon.isVisible = false
        binding.tvTotalTraffic.isVisible = false
    }

    private fun refreshTotalTrafficChip() {
        val totalTraffic = MmkvManager.getTotalTrafficString()
        if (totalTraffic == null) {
            binding.layoutWeatherChip.isVisible = false
            return
        }
        binding.tvTotalTraffic.text = totalTraffic
        binding.ivTotalTrafficIcon.isVisible = true
        binding.tvTotalTraffic.isVisible = true
        binding.layoutWeatherChip.isVisible = true
    }

    private fun refreshWeatherChip() {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
            binding.layoutWeatherChip.isVisible = false
            return
        }
        val coldStart = isColdStart.also { isColdStart = false }
        if (weatherLocationReady()) {
            if (coldStart) forceRefreshWeatherChip() else loadWeatherChip()
        } else {
            checkAndRequestPermission(PermissionType.LOCATION) {
                if (coldStart) forceRefreshWeatherChip() else loadWeatherChip()
            }
        }
    }

    private fun forceRefreshWeatherChip() {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) return

        if (!weatherLocationReady()) {
            checkAndRequestPermission(PermissionType.LOCATION) {
                forceRefreshWeatherChip()
            }
            return
        }

        val cached = WeatherHelper.getCachedWeatherStale()
        binding.layoutWeatherChip.isVisible = true
        if (cached != null) {
            applyWeatherToChip(cached)
        } else {
            binding.ivWeatherIcon.setImageResource(R.drawable.ic_cloud)
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.text = getString(R.string.weather_loading)
            binding.tvWeatherTemp.isVisible = true
        }

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@MainActivity, force = true)
            if (weather == null) {
                if (cached == null) binding.layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun loadWeatherChip() {
        binding.layoutWeatherChip.isVisible = true

        val fresh = WeatherHelper.getCachedWeather()
        val stale = fresh ?: WeatherHelper.getCachedWeatherStale()

        if (stale != null) {
            applyWeatherToChip(stale)
        } else {
            binding.ivWeatherIcon.setImageResource(R.drawable.ic_cloud)
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.text = getString(R.string.weather_loading)
            binding.tvWeatherTemp.isVisible = true
        }

        if (fresh != null) return

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@MainActivity)
            if (weather == null) {
                if (stale == null) binding.layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun applyWeatherToChip(weather: WeatherHelper.WeatherResult) {
        binding.ivWeatherIcon.setImageResource(weather.iconRes)
        binding.tvWeatherTemp.text = weather.getTemperatureString(WeatherHelper.isCelsius())
        binding.ivWeatherIcon.isVisible = true
        binding.tvWeatherTemp.isVisible = true
        binding.layoutWeatherChip.isVisible = true
    }

    override fun onContentChanged() {
        super.onContentChanged()
        
        val root = findViewById<View>(R.id.main_content) ?: return
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            
            view.updatePadding(
                top   = 0, 
                left  = maxOf(systemBars.left,  displayCutout.left),
                right = maxOf(systemBars.right, displayCutout.right),
                bottom = maxOf(systemBars.bottom, displayCutout.bottom)
            )
            
            val bottomInset = maxOf(systemBars.bottom, displayCutout.bottom)
            binding.cardBottomStatus.updatePadding(bottom = bottomInset)

            val headerContent = view.findViewById<View>(R.id.header_content)
            headerContent?.updatePadding(top = systemBars.top)
            
            insets
        }
    }

    private fun setupBannerHome() {
        val bannerHome = binding.bannerHome
        val headerImage = binding.headerImage
        val headerTopRow = binding.headerTopRow

        headerImage.setLayerType(View.LAYER_TYPE_NONE, null)

        val paddingTopWithBanner = (16 * resources.displayMetrics.density).toInt()
        val paddingTopNoBanner = 0

        fun applyBannerHeight() {
            val heightDp = MmkvManager.decodeSettingsInt(
                AppConfig.PREF_HOME_BANNER_HEIGHT,
                AppConfig.HOME_BANNER_HEIGHT_DEFAULT
            )
            val heightPx = (heightDp * resources.displayMetrics.density).toInt()
            val lp = bannerHome.layoutParams
            lp.height = heightPx
            bannerHome.layoutParams = lp
            headerImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }

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

        fun applyHeaderTopRowPadding() {
            val showBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
            val paddingDp = if (showBanner) MmkvManager.decodeSettingsInt(
                AppConfig.PREF_HEADER_TOP_ROW_PADDING,
                AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
            ) else 0
            val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()
            headerTopRow.setPadding(
                headerTopRow.paddingLeft,
                paddingPx,
                headerTopRow.paddingRight,
                headerTopRow.paddingBottom
            )
        }

        fun loadBannerImage() {
            if (isDestroyed || isFinishing) return

            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
            val targetTag = if (uriString.isNullOrBlank()) TAG_HOME_BANNER_DEFAULT else uriString
            if (headerImage.tag == targetTag) return
            if (!uriString.isNullOrBlank()) {
                val isGif = uriString.lowercase().endsWith(".gif")
                if (isGif) {
                    Glide.with(this@MainActivity)
                        .asGif()
                        .load(Uri.parse(uriString))
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .error(R.drawable.uwu_banner_home)
                        .into(headerImage)
                } else {
                    Glide.with(this@MainActivity)
                        .load(Uri.parse(uriString))
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .error(R.drawable.uwu_banner_home)
                        .into(headerImage)
                }
            } else {
                Glide.with(this@MainActivity).clear(headerImage)
                headerImage.setImageResource(R.drawable.uwu_banner_home)
            }
            headerImage.tag = targetTag
        }

        val show = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
        applyBannerVisibility(show)
        applyBannerHeight()
        applyHeaderTopRowPadding()
        loadBannerImage()

        bannerReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED -> {
                        val showNow = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
                        applyBannerVisibility(showNow)
                        applyBannerHeight()
                        applyHeaderTopRowPadding()
                        loadBannerImage()
                    }
                    AppConfig.BROADCAST_ACTION_HEADER_TOP_ROW_PADDING_CHANGED -> {
                        applyHeaderTopRowPadding()
                    }
                }
            }
        }
        
        val filter = android.content.IntentFilter(AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED)
        filter.addAction(AppConfig.BROADCAST_ACTION_HEADER_TOP_ROW_PADDING_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bannerReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(bannerReceiver, filter)
        }
    }

    private fun setupViewPager() {
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.apply {
            adapter = groupPagerAdapter
            isUserInputEnabled = true
            offscreenPageLimit = 10
        }
    }

    private fun setupListeners() {
        binding.fab.setOnClickListener { handleFabAction() }
        binding.fabNoBlur.setOnClickListener { handleFabAction() }
        
        binding.cardBottomStatus.setOnClickListener { handleLayoutTestClick() }
        binding.btnHome.setOnClickListener {
            MainMenuBottomSheet().show(supportFragmentManager, MainMenuBottomSheet.TAG)
        }
        
        binding.btnAddConfig.setOnClickListener {
            AddConfigBottomSheet().show(supportFragmentManager, AddConfigBottomSheet.TAG)
        }
        
        binding.btnMoreMenu.setOnClickListener {
            MoreMenuBottomSheet.newInstance(mainViewModel.subscriptionId).show(supportFragmentManager, MoreMenuBottomSheet.TAG)
        }
        
        binding.btnAddSub.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, SubEditActivity::class.java))
        }

        binding.layoutWeatherChip.setOnClickListener {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
                startActivity(Intent(this, WeatherForecastActivity::class.java))
            }
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
            R.id.menu_routing_setting -> requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
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
            R.id.real_ping_all -> {
                urlTestProgressDialog.show(mainViewModel.serversCache.count())
                mainViewModel.testAllRealPing()
            }
            R.id.tcping_all -> {
                urlTestProgressDialog.show(mainViewModel.serversCache.count())
                mainViewModel.testAllRealPing(true)
            }
            R.id.service_restart -> restartV2Ray()
            R.id.action_scroll_to_selected -> locateSelectedServer()
            R.id.del_all_config -> delAllConfig()
            R.id.del_duplicate_config -> delDuplicateConfig()
            R.id.del_invalid_config -> delInvalidConfig()
            R.id.sub_update -> importConfigViaSub()
            R.id.clear_test_results -> {
                mainViewModel.clearTestResults()
                // Clears results for every server across every group, not just the
                // currently visible tab — refresh every already-created tab now.
                refreshAllGroupListDisplays()
            }
            R.id.reset_traffic -> {
                val currentGroupName = mainViewModel.getSubscriptions(this)
                    .firstOrNull { it.id == mainViewModel.subscriptionId }
                    ?.remarks
                    ?: getString(R.string.filter_config_all)

                val options = arrayOf(
                    getString(R.string.reset_traffic_scope_profile),
                    getString(R.string.reset_traffic_scope_group, currentGroupName),
                    getString(R.string.reset_traffic_scope_all)
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.title_reset_traffic)
                    .setItems(options) { _, which ->
                        val msgRes: Int
                        val action: () -> Unit
                        
                        when (which) {
                            0 -> {
                                msgRes = R.string.confirm_reset_traffic_profile
                                action = { mainViewModel.resetCurrentProfileTraffic() }
                            }
                            1 -> {
                                msgRes = R.string.confirm_reset_traffic_group
                                action = { mainViewModel.resetGroupTraffic() }
                            }
                            else -> {
                                msgRes = R.string.confirm_reset_traffic_all
                                // Touches every server across every group — refresh every
                                // already-created tab, not just the one currently visible.
                                action = {
                                    mainViewModel.resetAllTraffic()
                                    refreshAllGroupListDisplays()
                                }
                            }
                        }
                        
                        showDeleteConfirmDialog(
                            context = this,
                            titleRes = R.string.title_reset_traffic,
                            messageRes = msgRes
                        ) { action() }
                    }
                    .showBlur()
            }
            R.id.action_order_origin,
            R.id.action_order_by_name,
            R.id.action_order_by_delay -> {
                mainViewModel.reloadServerList()
            }
        }
    }

    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) {
            refreshTabBadges()
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP, false) &&
                !MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)
            ) {
                SearchChipGradientController.applyState(this, binding)
                refreshTotalTrafficChip()
            }
        }
        mainViewModel.updateGroupBadgeAction.observe(this) { refreshTabBadges() }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.testProgressAction.observe(this) { info ->
            if (info == null) {
                urlTestProgressDialog.finish()
            } else {
                urlTestProgressDialog.update(info)
            }
        }
        mainViewModel.updateIpResultAction.observe(this) { ip ->
            lastIpStateText = if (ip.isNullOrEmpty()) {
                getString(R.string.ip_unknown)
            } else {
                getString(R.string.ip_connected, ip)
            }
            refreshIpStateText()
        }
        mainViewModel.updateTrafficSpeedAction.observe(this) { speedText ->
            lastTrafficSpeedText = speedText
            refreshIpStateText()
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(isLoading = false, isRunning = isRunning)
        }
        
        mainViewModel.alertAction.observe(this) { (isSuccess, message) ->
            if (isSuccess) {
                snackbarSuccess(message, title = getString(R.string.title_alerter_success))
                mainViewModel.fetchCurrentIp()
            } else {
                snackbarError(message, title = getString(R.string.title_alerter_error))
            }
        }

        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setBadgeVisibility(badge: TextView, label: TextView, count: Int) {
        if (count > 0) {
            badge.text = if (count > 99) "99+" else count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun setTabIcon(iconView: android.widget.ImageView?, iconName: String?) {
        iconView ?: return
        if (iconName.isNullOrBlank()) {
            iconView.visibility = android.view.View.GONE
            return
        }
        val resId = resources.getIdentifier(iconName, "drawable", packageName)
        if (resId == 0) {
            iconView.visibility = android.view.View.GONE
            return
        }
        iconView.setImageResource(resId)
        iconView.visibility = android.view.View.VISIBLE
    }

    private fun applyTabSelectedStyle(
        tab: com.google.android.material.tabs.TabLayout.Tab?,
        selected: Boolean,
        position: Int = tab?.position ?: 0,
        tabCount: Int = binding.tabGroup.tabCount
    ) {
    }

    private fun setupGroupTab() {
        lifecycleScope.launch(Dispatchers.IO) {
            val groups = mainViewModel.getSubscriptions(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext

                val currentIds = (0 until binding.tabGroup.tabCount).map { binding.tabGroup.getTabAt(it)?.tag }
                val structureUnchanged = currentIds == groups.map { it.id } &&
                    groupPagerAdapter.groups.map { it.icon } == groups.map { it.icon } &&
                    groupPagerAdapter.groups.map { it.remarks } == groups.map { it.remarks }

                groupPagerAdapter.update(groups)

                if (structureUnchanged && binding.tabGroup.tabCount == groups.size) {
                    refreshTabBadges()
                    return@withContext
                }

                val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }
                    .takeIf { it >= 0 } ?: (groups.size - 1)

                tabMediator?.detach()
                tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
                    groupPagerAdapter.groups.getOrNull(position)?.let { group ->
                        tab.tag = group.id
                        val tabView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_tab_group, null)
                        val tabIcon = tabView.findViewById<android.widget.ImageView>(R.id.tab_icon)
                        val tabLabel = tabView.findViewById<TextView>(R.id.tab_label)
                        val tabBadge = tabView.findViewById<TextView>(R.id.tab_badge)
                        tabLabel.text = group.remarks
                        setTabIcon(tabIcon, group.icon)
                        setBadgeVisibility(tabBadge, tabLabel, group.serverCount)
                        tab.customView = tabView
                    }
                }.also { it.attach() }

                binding.tabGroup.post {
                    for (i in 0 until binding.tabGroup.tabCount) {
                        val tab = binding.tabGroup.getTabAt(i)
                        applyTabSelectedStyle(tab, i == binding.tabGroup.selectedTabPosition, i, binding.tabGroup.tabCount)
                    }
                }

                binding.tabGroup.removeOnTabSelectedListener(tabSelectedListener)
                binding.tabGroup.addOnTabSelectedListener(tabSelectedListener)

                if (targetIndex >= 0) {
                    binding.viewPager.setCurrentItem(targetIndex, false)
                }
                
                val hasAnyGroup = groups.isNotEmpty()
                
                binding.layoutTabWrapper.isVisible = hasAnyGroup
                binding.tabGroup.isVisible = hasAnyGroup
                (binding.tabGroup.parent as? View)?.isVisible = hasAnyGroup
            }
        }
    }

    fun refreshGroupTabTitles(refreshAll: Boolean = false) {
        refreshTabBadges()
    }

    private fun refreshTabBadges() {
        lifecycleScope.launch(Dispatchers.IO) {
            val groups = mainViewModel.getSubscriptions(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                for (i in groups.indices) {
                    val tab = binding.tabGroup.getTabAt(i) ?: continue
                    val tabBadge = tab.customView?.findViewById<TextView>(R.id.tab_badge) ?: continue
                    val count = groups.getOrNull(i)?.serverCount ?: 0
                    val tabLabel = tab.customView?.findViewById<TextView>(R.id.tab_label) ?: continue
                    setBadgeVisibility(tabBadge, tabLabel, count)
                }
            }
        }
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            LauncherManager.stopService(this)
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
            snackbarError(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_error)) 
            applyRunningState(isLoading = false, isRunning = false) 
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        LauncherManager.startService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            LauncherManager.stopService(this)
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
            binding.fabNoBlur.setImageResource(R.drawable.ic_fab_check)
            return
        }

        if (isRunning) {
            binding.fab.setImageResource(R.drawable.ic_stop_24dp)
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            binding.fabNoBlur.setImageResource(R.drawable.ic_stop_24dp)
            binding.fabNoBlur.contentDescription = getString(R.string.action_stop_service)
            setTestState(getString(R.string.connection_connected))
            binding.cardBottomStatus.isFocusable = true
        } else {
            binding.fab.setImageResource(R.drawable.ic_play_24dp)
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            binding.fabNoBlur.setImageResource(R.drawable.ic_play_24dp)
            binding.fabNoBlur.contentDescription = getString(R.string.tasker_start_service)
            setTestState(getString(R.string.connection_not_connected))
            lastTrafficSpeedText = ""
            lastIpStateText = getString(R.string.ip_unknown)
            refreshIpStateText()
            binding.cardBottomStatus.isFocusable = false
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
            val targetActivity = when (EConfigType.fromInt(createConfigType)) {
                EConfigType.VLESS -> ServerVlessActivity::class.java
                EConfigType.TROJAN -> ServerTrojanActivity::class.java
                EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
                EConfigType.SOCKS, EConfigType.HTTP -> ServerSocksActivity::class.java
                EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
                EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
                else -> ServerVmessActivity::class.java
            }
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, targetActivity)
            )
        }
    }

    private fun importQRcode(): Boolean {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_START_SCAN_IMMEDIATE)) {
            launchScan()
        } else {
            showQRCodeSelectionDialog()
        }
        return true
    }

    private fun showQRCodeSelectionDialog() {
        val options = arrayOf(
            getString(R.string.scan_code),
            getString(R.string.select_photo)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_item_import_config_qrcode)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchScan()
                    1 -> showQRFileChooser()
                }
            }
            .showBlur()
    }

    private fun launchScan() {
        scanQrCode.launch(Intent(this, QrCaptureActivity::class.java))
    }

    private fun showQRFileChooser() {
        launchFileChooser("image/*") { uri ->
            if (uri == null) return@launchFileChooser
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                if (text.isNullOrEmpty()) {
                    snackbarDefault(R.string.toast_decoding_failed, title = getString(R.string.title_alerter_info))
                } else {
                    importBatchConfig(text)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to decode QR code from file", e)
                snackbarDefault(R.string.toast_decoding_failed, title = getString(R.string.title_alerter_info))
            }
        }
    }

    private fun importClipboard(): Boolean {
        return try {
            Utils.getClipboard(this).let { importBatchConfig(it) }
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
                            snackbarSuccess(getString(R.string.title_import_config_count, count), title = getString(R.string.title_alerter_success))
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                        }
                        countSub > 0 -> setupGroupTab()
                        else -> snackbarError(getString(R.string.import_configuration), title = getString(R.string.title_alerter_error))
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarError(getString(R.string.import_configuration), title = getString(R.string.title_alerter_error))
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
            try {
                val result = mainViewModel.updateConfigViaSubAll()
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 -> {
                            toastInfo(getString(R.string.title_update_subscription_no_subscription))
                        }
                        result.successCount > 0 && result.failureCount + result.skipCount == 0 -> {
                            toastSuccess(getString(R.string.title_update_config_count, result.configCount))
                        }
                        else -> {
                            toastInfo(
                                getString(
                                    R.string.title_update_subscription_result,
                                    result.configCount, result.successCount, result.failureCount, result.skipCount
                                )
                            )
                        }
                    }
                    if (result.configCount > 0) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                    }
                    if (result.addedProfiles.isNotEmpty() || result.deletedProfiles.isNotEmpty()) {
                        showSubUpdateDiffDialog(this@MainActivity, result)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to update config via subscription", e)
                withContext(Dispatchers.Main) {
                    snackbarError(getString(R.string.title_update_subscription_no_subscription), title = getString(R.string.title_alerter_error))
                }
            } finally {
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ret = mainViewModel.exportAllServer()
                withContext(Dispatchers.Main) {
                    if (ret > 0) snackbarSuccess(getString(R.string.title_export_config_count, ret), title = getString(R.string.title_alerter_success))
                    else snackbarError(getString(R.string.action_export), title = getString(R.string.title_alerter_error))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to export all configs", e)
                withContext(Dispatchers.Main) {
                    snackbarError(getString(R.string.action_export), title = getString(R.string.title_alerter_error))
                }
            } finally {
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun delAllConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val ret = mainViewModel.removeAllServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        snackbarSuccess(getString(R.string.title_del_config_count, ret), title = getString(R.string.title_alerter_success))
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove all configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(getString(R.string.del_config_dialog_comfirm_message), title = getString(R.string.title_alerter_error))
                    }
                } finally {
                    withContext(Dispatchers.Main) { hideLoading() }
                }
            }
        }
    }

    private fun delDuplicateConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val ret = mainViewModel.removeDuplicateServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        snackbarSuccess(getString(R.string.title_del_duplicate_config_count, ret), title = getString(R.string.title_alerter_success))
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove duplicate configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(getString(R.string.del_config_dialog_comfirm_message), title = getString(R.string.title_alerter_error))
                    }
                } finally {
                    withContext(Dispatchers.Main) { hideLoading() }
                }
            }
        }
    }

    private fun delInvalidConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_invalid_config_comfirm) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val ret = mainViewModel.removeInvalidServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        snackbarSuccess(getString(R.string.title_del_config_count, ret), title = getString(R.string.title_alerter_success))
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove invalid configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(getString(R.string.del_invalid_config_comfirm), title = getString(R.string.title_alerter_error))
                    }
                } finally {
                    withContext(Dispatchers.Main) { hideLoading() }
                }
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

    fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            snackbarDefault(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_info))
            return
        }

        val targetGroupIndex = groupPagerAdapter.groups.indexOfFirst { it.id == targetSubscriptionId }
            if (targetGroupIndex < 0) {
            snackbarDefault(getString(R.string.toast_server_not_found_in_group), title = getString(R.string.title_alerter_info))
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
            snackbarDefault(getString(R.string.toast_fragment_not_available), title = getString(R.string.title_alerter_info))
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
                    snackbarSuccess(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_success))
                } else {
                    snackbarError(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_error))
                }
            }
            R.id.share_full_clipboard -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
                    withContext(Dispatchers.Main) {
                        if (result == 0) snackbarSuccess(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_success))
                        else snackbarError(getString(R.string.menu_item_export_proxy_app), title = getString(R.string.title_alerter_error))
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
        hideLoading()
        urlTestProgressDialog.dismiss()

        tabMediator?.detach()
        
        try {
            bannerReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unregister bannerReceiver", e)
        }
        
        super.onDestroy()
    }
}
