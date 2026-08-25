package com.miku.ray.ui.main

import com.miku.ray.remixicon.R as RemixR
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.text.style.ClickableSpan
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.king.camera.scan.CameraScan
import com.miku.ray.AppConfig
import com.miku.ray.util.SearchBarChipMode
import com.miku.ray.BuildConfig
import com.miku.ray.R
import com.miku.ray.core.LauncherManager
import com.miku.ray.databinding.ActivityMainBinding
import com.miku.ray.databinding.ItemQrcodeBinding
import com.miku.ray.dto.entities.MikuRayExportPayload
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.PermissionType
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toSpeedString
import com.miku.ray.extension.toastError
import com.miku.ray.extension.toastInfo
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MikuRayGroupFileManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SubscriptionUpdater
import com.miku.ray.ui.about.AboutActivity
import com.miku.ray.ui.backup.BackupActivity
import com.miku.ray.ui.base.HelperBaseActivity
import com.miku.ray.ui.bottomsheet.AddConfigBottomSheet
import com.miku.ray.ui.bottomsheet.MainMenuBottomSheet
import com.miku.ray.ui.bottomsheet.MoreMenuBottomSheet
import com.miku.ray.ui.bottomsheet.ShareConfigBottomSheet
import com.miku.ray.ui.logcat.LogcatActivity
import com.miku.ray.ui.preference.activity.SettingsActivity
import com.miku.ray.ui.routing.RoutingSettingActivity
import com.miku.ray.ui.scanner.QrCaptureActivity
import com.miku.ray.ui.server.ServerGroupActivity
import com.miku.ray.ui.server.ServerHysteria2Activity
import com.miku.ray.ui.server.ServerProxyChainActivity
import com.miku.ray.ui.server.ServerShadowsocksActivity
import com.miku.ray.ui.server.ServerSocksActivity
import com.miku.ray.ui.server.ServerTrojanActivity
import com.miku.ray.ui.server.ServerVlessActivity
import com.miku.ray.ui.server.ServerVmessActivity
import com.miku.ray.ui.server.ServerWireguardActivity
import com.miku.ray.ui.subscription.SubEditActivity
import com.miku.ray.ui.subscription.SubSettingActivity
import com.miku.ray.ui.weather.WeatherForecastActivity
import com.miku.ray.ui.weather.WeatherHelper
import com.miku.ray.util.BlurBottomStatusController
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MikuRayFileCrypto
import com.miku.ray.util.QRCodeDecoder
import com.miku.ray.util.SearchChipGradientController
import com.miku.ray.util.TestProgressDialogController
import com.miku.ray.util.Utils
import com.miku.ray.util.getColorAttr
import com.miku.ray.util.showBlur
import com.miku.ray.util.showDeleteConfirmDialog
import com.miku.ray.util.showMikuRayExportPasswordDialog
import com.miku.ray.util.showMikuRayImportPasswordDialog
import com.miku.ray.util.showSubUpdateDiffDialog
import com.miku.ray.util.showTotalTrafficDetailDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class MainActivity : HelperBaseActivity(),
    MainMenuBottomSheet.OnOptionClickListener,
    AddConfigBottomSheet.OnAddConfigClickListener,
    MoreMenuBottomSheet.OnMoreOptionClickListener,
    ShareConfigBottomSheet.OnShareOptionClickListener {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    val mainViewModel: MainViewModel by viewModels()

    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    private var bannerReceiver: BroadcastReceiver? = null
    
    private var isColdStart = true
    private var dualSwipeChipSelection = SearchBarChipMode.WEATHER
    private var pendingConnectionTest = false
    private var lastIpStateText: String = ""
    private var lastTrafficSpeedText: String = ""
    private var lastTestResultText: String = ""

    private val urlTestProgressDialog: TestProgressDialogController by lazy {
        TestProgressDialogController(this, TestProgressDialogController.Mode.URL_TEST) { mainViewModel.cancelRealPingTest() }
    }

    private val countryCodeProgressDialog: TestProgressDialogController by lazy {
        TestProgressDialogController(this, TestProgressDialogController.Mode.COUNTRY_CODE) { mainViewModel.cancelCountryCodeTest() }
    }

    private val TAG_HOME_BANNER_DEFAULT = "DEFAULT_HOME_BANNER"
    private val TAG_HOME_BANNER_HIDDEN = "HIDDEN_HOME_BANNER"

    private val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            applyTabSelectedStyle(tab, true, tab.position, binding.tabGroup.tabCount)
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
            applyTabSelectedStyle(tab, false, tab.position, binding.tabGroup.tabCount)
        }

        override fun onTabReselected(tab: TabLayout.Tab) {}
    }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService()) {
            LauncherManager.restartService(this)
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
        showTestBuildInfoIfNeeded()

        hideLoading()
        window.statusBarColor = Color.TRANSPARENT

        setupViewPager()
        setupListeners()
        setupInlineSearchView()
        setupSearchBarChipSwipe()
        setupGroupTab()
        setupViewModel()
        setupBannerHome()
        
        BlurBottomStatusController.applyState(this, binding)
        SubscriptionUpdater.sync()
        syncWeatherBackgroundUpdates()
        
        mainViewModel.reloadServerList()
        refreshGroupTabTitles(true)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        maybeShowTrafficDetailFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeShowTrafficDetailFromIntent(intent)
    }

    private fun maybeShowTrafficDetailFromIntent(intent: Intent?) {
        val launchIntent = intent ?: return
        if (!launchIntent.getBooleanExtra(AppConfig.EXTRA_SHOW_TOTAL_TRAFFIC_DETAIL, false)) return
        launchIntent.removeExtra(AppConfig.EXTRA_SHOW_TOTAL_TRAFFIC_DETAIL)
        window.decorView.post { showTotalTrafficDetailDialog(this) }
    }

    private fun showTestBuildInfoIfNeeded() {
        if (BuildConfig.BUILD_TYPE != "releaseTesting") return
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_DISMISS_TEST_BUILD_INFO, false)) return

        val channelLabel = getString(R.string.test_build_info_channel)
        val message = getString(R.string.test_build_info_message, channelLabel)
        val styledMessage = SpannableString(message)
        val channelStart = message.indexOf(channelLabel)
        if (channelStart >= 0) {
            styledMessage.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://t.me/uwuowoumuchannel")
                            )
                        )
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = getColorAttr("colorPrimary")
                        ds.isUnderlineText = false
                    }
                },
                channelStart,
                channelStart + channelLabel.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.test_build_info_title)
            .setIcon(RemixR.drawable.rmx_system_alarm_warning_line)
            .setMessage(styledMessage)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.test_build_info_dont_show_again) { _, _ ->
                MmkvManager.encodeSettings(AppConfig.PREF_DISMISS_TEST_BUILD_INFO, true)
            }
            .showBlur()

        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onResume() {
        super.onResume()
        
        refreshSearchBarChip()
        refreshIpStateText()
        
        if (SettingsChangeManager.consumeRefreshDisplayPrefs()) {
            refreshAllGroupListDisplays()
        }
        
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            refreshGroupTabTitles()
        }
        
        mainViewModel.resyncState()
    }

    override fun onContentChanged() {
        super.onContentChanged()

        val root = findViewById<View>(R.id.main_content) ?: return

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

            view.updatePadding(
                top = 0,
                left = maxOf(systemBars.left, displayCutout.left),
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

    private fun weatherLocationReady(): Boolean =
        WeatherHelper.hasCustomLocation() || WeatherHelper.hasLocationPermission(this)

    private fun syncWeatherBackgroundUpdates() {
        val weatherEnabled = SearchBarChipMode.current() in setOf(
            SearchBarChipMode.WEATHER,
            SearchBarChipMode.DUAL_SWIPE
        )
        val canRunInBackground = WeatherHelper.hasCustomLocation() || WeatherHelper.hasBackgroundLocationPermission(this)

        if (weatherEnabled && canRunInBackground) {
            WeatherHelper.scheduleBackgroundUpdates(this)
        } else if (!weatherEnabled) {
            WeatherHelper.cancelBackgroundUpdates(this)
        }
    }

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

    private fun refreshAllGroupListDisplays() {
        for (i in groupPagerAdapter.groups.indices) {
            val itemId = groupPagerAdapter.getItemId(i)
            val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment
            fragment?.refreshDisplayPrefs()
        }
    }

    private fun setupSearchBarChipSwipe() {
        val swipeThreshold = 64 * resources.displayMetrics.density
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                binding.layoutWeatherChip.performClick()
                return true
            }

            override fun onFling(
                firstEvent: MotionEvent?,
                lastEvent: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (firstEvent == null) return false
                val distanceX = lastEvent.x - firstEvent.x
                val distanceY = lastEvent.y - firstEvent.y
                if (abs(distanceY) <= abs(distanceX) || abs(distanceY) < swipeThreshold) return false

                // With two items, both vertical directions advance the carousel with wrap-around.
                dualSwipeChipSelection = if (dualSwipeChipSelection == SearchBarChipMode.WEATHER) {
                    SearchBarChipMode.TOTAL_TRAFFIC
                } else {
                    SearchBarChipMode.WEATHER
                }
                SearchBarChipMode.saveDualSelection(dualSwipeChipSelection)
                refreshSearchBarChip()
                return true
            }
        })

        binding.layoutWeatherChip.setOnTouchListener { view, event ->
            if (SearchBarChipMode.current() == SearchBarChipMode.DUAL_SWIPE) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                gestureDetector.onTouchEvent(event)
                true
            } else {
                false
            }
        }
    }

    private fun isWeatherChipSelected(): Boolean {
        val mode = SearchBarChipMode.current()
        return mode == SearchBarChipMode.WEATHER ||
            (mode == SearchBarChipMode.DUAL_SWIPE && dualSwipeChipSelection == SearchBarChipMode.WEATHER)
    }

    private fun isTotalTrafficChipSelected(): Boolean {
        val mode = SearchBarChipMode.current()
        return mode == SearchBarChipMode.TOTAL_TRAFFIC ||
            (mode == SearchBarChipMode.DUAL_SWIPE && dualSwipeChipSelection == SearchBarChipMode.TOTAL_TRAFFIC)
    }

    private fun refreshSearchBarChip() {
        val mode = SearchBarChipMode.current()
        if (mode == SearchBarChipMode.DUAL_SWIPE) {
            dualSwipeChipSelection = SearchBarChipMode.currentDualSelection()
        } else {
            dualSwipeChipSelection = SearchBarChipMode.WEATHER
        }
        val weatherEnabled = isWeatherChipSelected()
        val totalTrafficEnabled = isTotalTrafficChipSelected()

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
        
        binding.tvTotalTraffic.text = totalTraffic
        if (isTotalTrafficChipSelected()) {
            binding.ivTotalTrafficIcon.isVisible = true
            binding.tvTotalTraffic.isVisible = true
            binding.layoutWeatherChip.isVisible = true
        }
    }

    private fun refreshWeatherChip() {
        if (!isWeatherChipSelected()) {
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
        if (!isWeatherChipSelected()) return

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
            binding.ivWeatherIcon.setImageResource(RemixR.drawable.rmx_cloud_line)
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.text = getString(R.string.weather_loading)
            binding.tvWeatherTemp.isVisible = true
        }

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@MainActivity, force = true)
            if (weather == null) {
                if (cached == null && isWeatherChipSelected()) binding.layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun loadWeatherChip() {
        if (!isWeatherChipSelected()) return
        binding.layoutWeatherChip.isVisible = true

        val fresh = WeatherHelper.getCachedWeather()
        val stale = fresh ?: WeatherHelper.getCachedWeatherStale()

        if (stale != null) {
            applyWeatherToChip(stale)
        } else {
            binding.ivWeatherIcon.setImageResource(RemixR.drawable.rmx_cloud_line)
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.text = getString(R.string.weather_loading)
            binding.tvWeatherTemp.isVisible = true
        }

        if (fresh != null) return

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@MainActivity)
            if (weather == null) {
                if (stale == null && isWeatherChipSelected()) binding.layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun applyWeatherToChip(weather: WeatherHelper.WeatherResult) {
        binding.ivWeatherIcon.setImageResource(weather.iconRes)
        binding.tvWeatherTemp.text = weather.getTemperatureString(WeatherHelper.isCelsius())

        if (isWeatherChipSelected()) {
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.isVisible = true
            binding.layoutWeatherChip.isVisible = true
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
            headerImage.scaleType = ImageView.ScaleType.CENTER_CROP
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
            val disableBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
            val paddingDp = if (!disableBanner) {
                MmkvManager.decodeSettingsInt(
                    AppConfig.PREF_HEADER_TOP_ROW_PADDING,
                    AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
                )
            } else 0
            
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

            val disableBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
            if (disableBanner) {
                Glide.with(headerImage).clear(headerImage)
                headerImage.setImageDrawable(null)
                headerImage.tag = TAG_HOME_BANNER_HIDDEN
                return
            }

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

        val disableBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
        applyBannerVisibility(!disableBanner)
        applyBannerHeight()
        applyHeaderTopRowPadding()
        loadBannerImage()

        bannerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED -> {
                        val disableBannerNow = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
                        applyBannerVisibility(!disableBannerNow)
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

        val filter = IntentFilter(AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED).apply {
            addAction(AppConfig.BROADCAST_ACTION_HEADER_TOP_ROW_PADDING_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bannerReceiver, filter, RECEIVER_NOT_EXPORTED)
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
            MoreMenuBottomSheet.newInstance(mainViewModel.subscriptionId)
                .show(supportFragmentManager, MoreMenuBottomSheet.TAG)
        }

        binding.btnAddSub.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, SubEditActivity::class.java))
        }

        binding.layoutWeatherChip.setOnClickListener {
            when {
                isWeatherChipSelected() -> {
                    startActivity(Intent(this, WeatherForecastActivity::class.java))
                }
                isTotalTrafficChipSelected() -> {
                    showTotalTrafficDetailDialog(this) { refreshTotalTrafficChip() }
                }
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
            R.id.export_group_file -> exportGroupAsFile()
            R.id.real_ping_all -> {
                urlTestProgressDialog.show(mainViewModel.serversCache.count(), R.string.title_real_ping_all_server)
                mainViewModel.testAllRealPing()
            }
            R.id.country_code_all -> {
                countryCodeProgressDialog.show(mainViewModel.serversCache.count())
                mainViewModel.testAllCountryCodes()
            }
            R.id.tcping_all -> {
                urlTestProgressDialog.show(mainViewModel.serversCache.count(), R.string.title_ping_all_server)
                mainViewModel.testAllRealPing(true)
            }
            R.id.service_restart -> LauncherManager.restartServiceOrStart(this, ::startV2Ray)
            R.id.action_scroll_to_selected -> locateSelectedServer()
            R.id.del_all_config -> delAllConfig()
            R.id.del_duplicate_config -> delDuplicateConfig()
            R.id.del_invalid_config -> delInvalidConfig()
            R.id.sub_update -> importConfigViaSub()
            R.id.clear_test_results -> {
                val options = arrayOf(
                    getString(R.string.reset_traffic_scope_group, currentGroupDisplayName()),
                    getString(R.string.reset_traffic_scope_all)
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.title_clear_test_results)
                    .setIcon(RemixR.drawable.rmx_refresh_line)
                    .setItems(options) { _, which ->
                        val msgRes: Int
                        val action: () -> Unit

                        when (which) {
                            0 -> {
                                msgRes = R.string.confirm_clear_test_results_group
                                action = {
                                    mainViewModel.clearTestResultsForGroup()
                                    refreshAllGroupListDisplays()
                                }
                            }
                            else -> {
                                msgRes = R.string.confirm_clear_test_results_all
                                action = {
                                    mainViewModel.clearTestResults()
                                    refreshAllGroupListDisplays()
                                }
                            }
                        }

                        showDeleteConfirmDialog(
                            context = this,
                            titleRes = R.string.title_clear_test_results,
                            messageRes = msgRes
                        ) { action() }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
            }
            R.id.clear_country_codes -> {
                val options = arrayOf(
                    getString(R.string.reset_traffic_scope_group, currentGroupDisplayName()),
                    getString(R.string.reset_traffic_scope_all)
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.title_clear_country_codes)
                    .setIcon(RemixR.drawable.rmx_refresh_line)
                    .setItems(options) { _, which ->
                        val msgRes: Int
                        val action: () -> Unit

                        when (which) {
                            0 -> {
                                msgRes = R.string.confirm_clear_country_codes_group
                                action = {
                                    mainViewModel.clearCountryCodesForGroup()
                                    refreshAllGroupListDisplays()
                                }
                            }
                            else -> {
                                msgRes = R.string.confirm_clear_country_codes_all
                                action = {
                                    mainViewModel.clearCountryCodes()
                                    refreshAllGroupListDisplays()
                                }
                            }
                        }

                        showDeleteConfirmDialog(
                            context = this,
                            titleRes = R.string.title_clear_country_codes,
                            messageRes = msgRes
                        ) { action() }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
            }
            R.id.reset_traffic -> {
                val options = arrayOf(
                    getString(R.string.reset_traffic_scope_profile),
                    getString(R.string.reset_traffic_scope_group, currentGroupDisplayName()),
                    getString(R.string.reset_traffic_scope_all)
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.title_reset_traffic)
                    .setIcon(RemixR.drawable.rmx_refresh_line)
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
                    .setNegativeButton(android.R.string.cancel, null)
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
            if (SearchBarChipMode.current() in setOf(
                    SearchBarChipMode.TOTAL_TRAFFIC,
                    SearchBarChipMode.DUAL_SWIPE
                )) {
                SearchChipGradientController.applyState(this, binding)
                if (isTotalTrafficChipSelected()) refreshTotalTrafficChip()
            }
        }

        mainViewModel.updateGroupBadgeAction.observe(this) { refreshTabBadges() }

        mainViewModel.updateGroupOrderAction.observe(this) {
            mainViewModel.reloadServerList()
            refreshGroupTabTitles()
        }
        
        mainViewModel.updateTestResultAction.observe(this) {
            lastTestResultText = it.orEmpty()
            setTestState(it)
        }

        mainViewModel.testProgressAction.observe(this) { info ->
            if (info == null) {
                urlTestProgressDialog.finish()
            } else {
                urlTestProgressDialog.update(info)
            }
        }

        mainViewModel.countryCodeProgressAction.observe(this) { info ->
            if (info == null) {
                countryCodeProgressDialog.finish()
            } else {
                countryCodeProgressDialog.update(info)
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
            if (isRunning == true && pendingConnectionTest) {
                pendingConnectionTest = false
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            }
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

    private fun setTabIcon(iconView: ImageView?, iconName: String?) {
        iconView ?: return
        if (iconName.isNullOrBlank()) {
            iconView.visibility = View.GONE
            return
        }
        
        val resId = resources.getIdentifier(iconName, "drawable", packageName)
        if (resId == 0) {
            iconView.visibility = View.GONE
            return
        }
        
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    private fun applyTabSelectedStyle(
        tab: TabLayout.Tab?,
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
                        
                        val tabIcon = tabView.findViewById<ImageView>(R.id.tab_icon)
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
        // setupGroupTab() already diffs tab ids/icons/remarks against the current
        // TabLayout state and only rebuilds when the structure actually changed
        // (e.g. sort-by-updated order shifting after a subscription update),
        // otherwise it just falls back to a badge refresh. This keeps tab order
        // in sync live without needing to restart the activity.
        setupGroupTab()
    }

    private fun refreshTabBadges() {
        lifecycleScope.launch(Dispatchers.IO) {
            val groups = mainViewModel.getSubscriptions(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                
                for (i in groups.indices) {
                    val tab = binding.tabGroup.getTabAt(i) ?: continue
                    val tabBadge = tab.customView?.findViewById<TextView>(R.id.tab_badge) ?: continue
                    val tabLabel = tab.customView?.findViewById<TextView>(R.id.tab_label) ?: continue
                    
                    val count = groups.getOrNull(i)?.serverCount ?: 0
                    setBadgeVisibility(tabBadge, tabLabel, count)
                }
            }
        }
    }

    private fun handleFabAction() {
        mainViewModel.resyncState()
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
        } else {
            pendingConnectionTest = true
            mainViewModel.resyncState()
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

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        binding.fab.isEnabled = true
        binding.fabNoBlur.isEnabled = true

        if (isLoading) {
            binding.fab.setImageResource(RemixR.drawable.rmx_system_check_line)
            binding.fabNoBlur.setImageResource(RemixR.drawable.rmx_system_check_line)
            return
        }

        binding.cardBottomStatus.isClickable = true
        binding.cardBottomStatus.isFocusable = true

        if (isRunning) {
            binding.fab.setImageResource(RemixR.drawable.rmx_media_stop_line)
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            
            binding.fabNoBlur.setImageResource(RemixR.drawable.rmx_media_stop_line)
            binding.fabNoBlur.contentDescription = getString(R.string.action_stop_service)
            
            setTestState(lastTestResultText.ifEmpty { getString(R.string.connection_connected) })
        } else {
            binding.fab.setImageResource(RemixR.drawable.rmx_media_play_line)
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            
            binding.fabNoBlur.setImageResource(RemixR.drawable.rmx_media_play_line)
            binding.fabNoBlur.contentDescription = getString(R.string.tasker_start_service)
            
            setTestState(getString(R.string.connection_not_connected))
            lastTestResultText = ""
            lastTrafficSpeedText = ""
            lastIpStateText = getString(R.string.ip_unknown)
            refreshIpStateText()
            pendingConnectionTest = false
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
            .setIcon(RemixR.drawable.rmx_qr_code_line)
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
                            snackbarSuccess(
                                getString(R.string.title_import_config_count, count),
                                title = getString(R.string.title_alerter_success)
                            )
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                        }
                        countSub > 0 -> setupGroupTab()
                        else -> snackbarError(
                            getString(R.string.import_configuration),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.import_configuration),
                        title = getString(R.string.title_alerter_error)
                    )
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
                    snackbarError(
                        getString(R.string.title_update_subscription_no_subscription),
                        title = getString(R.string.title_alerter_error)
                    )
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
                    if (ret > 0) {
                        snackbarSuccess(
                            getString(R.string.title_export_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        snackbarError(
                            getString(R.string.action_export),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
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

    private fun currentGroupDisplayName(): String =
        mainViewModel.getSubscriptions(this)
            .firstOrNull { it.id == mainViewModel.subscriptionId }
            ?.remarks
            ?: getString(R.string.filter_config_all)

    private fun exportGroupAsFile() {
        val currentGroupName = currentGroupDisplayName()

        val payload = MikuRayGroupFileManager.buildGroupExportPayload(mainViewModel.subscriptionId, currentGroupName)
        if (payload == null) {
            snackbarError(getString(R.string.title_export_group_file), title = getString(R.string.title_alerter_error))
            return
        }
        
        exportPayloadAndShare(payload, currentGroupName)
    }

    private fun shareProfileAsFile(guid: String) {
        val payload = MikuRayGroupFileManager.buildProfileExportPayload(guid)
        if (payload == null) {
            snackbarError(getString(R.string.title_export_group_file), title = getString(R.string.title_alerter_error))
            return
        }
        
        exportPayloadAndShare(payload, payload.name)
    }

    private fun exportPayloadAndShare(payload: MikuRayExportPayload, fileNamePrefix: String) {
        showMikuRayExportPasswordDialog(this) { password ->
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val file = MikuRayGroupFileManager.encryptPayloadToFile(
                        this@MainActivity,
                        payload,
                        password,
                        fileNamePrefix
                    )
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        shareMikuRayFile(file)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to export .mikuray file", e)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        snackbarError(
                            getString(R.string.title_export_group_file),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            }
        }
    }

    private fun shareMikuRayFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.cache", file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("application/octet-stream")
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(Intent.EXTRA_STREAM, uri),
                    getString(R.string.title_configuration_share)
                )
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share .mikuray file", e)
            snackbarError(getString(R.string.title_export_group_file), title = getString(R.string.title_alerter_error))
        }
    }

    private fun importMikuRayFile(bytes: ByteArray) {
        showMikuRayImportPasswordDialog(this) { password ->
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val payload = MikuRayGroupFileManager.decryptPayloadFromFile(bytes, password)
                    val count = MikuRayGroupFileManager.importPayload(payload, mainViewModel.subscriptionId)
                    
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        if (count > 0) {
                            snackbarSuccess(
                                getString(R.string.title_import_mikuray_count, count),
                                title = getString(R.string.title_alerter_success)
                            )
                            if (payload.type == MikuRayExportPayload.TYPE_GROUP) {
                                setupGroupTab()
                            } else {
                                mainViewModel.reloadServerList()
                                refreshGroupTabTitles()
                            }
                        } else {
                            snackbarError(
                                getString(R.string.title_import_mikuray_error),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                } catch (e: MikuRayFileCrypto.MikuRayCryptoException) {
                    LogUtil.e(AppConfig.TAG, "Failed to decrypt .mikuray file", e)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        snackbarError(
                            getString(R.string.title_import_mikuray_wrong_password),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import .mikuray file", e)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        snackbarError(
                            getString(R.string.title_import_mikuray_error),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
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
                        snackbarSuccess(
                            getString(R.string.title_del_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove all configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.del_config_dialog_comfirm_message),
                            title = getString(R.string.title_alerter_error)
                        )
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
                        snackbarSuccess(
                            getString(R.string.title_del_duplicate_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove duplicate configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.del_config_dialog_comfirm_message),
                            title = getString(R.string.title_alerter_error)
                        )
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
                        snackbarSuccess(
                            getString(R.string.title_del_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove invalid configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.del_invalid_config_comfirm),
                            title = getString(R.string.title_alerter_error)
                        )
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
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            if (MikuRayFileCrypto.isMikuRayFile(bytes)) {
                importMikuRayFile(bytes)
            } else {
                importBatchConfig(String(bytes, Charsets.UTF_8))
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
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.title_qr_code)
                        .setIcon(RemixR.drawable.rmx_qr_code_line)
                        .setView(ivBinding.root).showBlur()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Error when sharing QR code", e)
                }
            }
            R.id.share_clipboard -> {
                if (AngConfigManager.share2Clipboard(this, guid) == 0) {
                    snackbarSuccess(
                        getString(R.string.menu_item_export_proxy_app),
                        title = getString(R.string.title_alerter_success)
                    )
                } else {
                    snackbarError(
                        getString(R.string.menu_item_export_proxy_app),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            }
            R.id.share_full_clipboard -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
                    withContext(Dispatchers.Main) {
                        if (result == 0) {
                            snackbarSuccess(
                                getString(R.string.menu_item_export_proxy_app),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            snackbarError(
                                getString(R.string.menu_item_export_proxy_app),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            R.id.share_file -> shareProfileAsFile(guid)
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
        runCatching {
            Glide.with(applicationContext).clear(binding.headerImage)
            binding.headerImage.setImageDrawable(null)
            binding.headerImage.tag = null
        }
        try {
            bannerReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unregister bannerReceiver", e)
        }

        super.onDestroy()
    }
}
