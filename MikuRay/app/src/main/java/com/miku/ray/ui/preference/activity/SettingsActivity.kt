package com.miku.ray.ui.preference.activity


import com.miku.ray.remixicon.R as RemixR
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.NonNull
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.miku.ray.ui.preference.preferencesearch.SearchPreferenceActionView
import com.miku.ray.ui.preference.preferencesearch.SearchPreferenceFragment
import com.miku.ray.ui.preference.preferencesearch.SearchPreferenceResult
import com.miku.ray.ui.preference.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.SearchBarChipMode
import com.miku.ray.R
import com.miku.ray.enums.PermissionType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.HelperBaseActivity
import com.miku.ray.ui.dialog.BannerCharacterLayoutDialog
import com.miku.ray.ui.preference.BannerSettingsPreference
import com.miku.ray.ui.weather.WeatherForecastActivity
import com.miku.ray.util.SearchChipGradientController
import com.miku.ray.ui.weather.WeatherHelper
import com.miku.ray.util.showDeleteConfirmDialog
import com.miku.ray.util.showTotalTrafficDetailDialog
import kotlinx.coroutines.launch
import kotlin.math.abs

class SettingsActivity : HelperBaseActivity(), SearchPreferenceResultListener {

    private lateinit var searchActionView: SearchPreferenceActionView
    private lateinit var btnClearHistory: com.google.android.material.button.MaterialButton
    private lateinit var layoutWeatherChip: LinearLayout
    private lateinit var ivWeatherIcon: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var ivTotalTrafficIcon: ImageView
    private lateinit var tvTotalTraffic: TextView

    private var isColdStart = true
    private var dualSwipeChipSelection = SearchBarChipMode.WEATHER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_search)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_settings), subtitle = getString(R.string.subtitle_settings))

        setupSearchActionView()
        setupWeatherTrafficChip()
        setupSearchBarChipSwipe()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val searchFragment = supportFragmentManager.fragments.find {
                    it.javaClass.name.contains("SearchPreferenceFragment")
                }

                if (searchFragment != null && searchFragment.isVisible) {
                    searchActionView.cancelSearch()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshSearchBarChip()
    }

    private fun setupSearchActionView() {
        searchActionView = findViewById(R.id.search_action_view)
        btnClearHistory = findViewById(R.id.btn_clear_history)
        searchActionView.setActivity(this)
        searchActionView.getSearchConfiguration().apply {
            setHistoryEnabled(true)
            setBreadcrumbsEnabled(true)
            setFragmentContainerViewId(R.id.settings_container)
            index(R.xml.pref_ui_settings).addBreadcrumb(R.string.title_ui_settings)
            index(R.xml.pref_vpn_settings).addBreadcrumb(R.string.title_vpn_settings)
            index(R.xml.pref_core_settings).addBreadcrumb(R.string.title_core_settings)
            index(R.xml.pref_mux_settings).addBreadcrumb(R.string.title_mux_settings)
            index(R.xml.pref_fragment_settings).addBreadcrumb(R.string.title_fragment_settings)
            index(R.xml.pref_advanced_settings).addBreadcrumb(R.string.title_advanced)
            index(R.xml.pref_observatory_settings).addBreadcrumb(R.string.title_observatory_settings)
        }

        btnClearHistory.setOnClickListener {
            currentSearchFragment()?.clearHistory()
            btnClearHistory.isVisible = false
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (f is SearchPreferenceFragment) {
                        btnClearHistory.isVisible = f.hasHistory()
                    }
                }

                override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                    if (f is SearchPreferenceFragment) {
                        btnClearHistory.isVisible = false
                    }
                }
            },
            true
        )
    }

    private fun currentSearchFragment(): SearchPreferenceFragment? =
        supportFragmentManager.fragments.filterIsInstance<SearchPreferenceFragment>().firstOrNull()

    private fun setupWeatherTrafficChip() {
        layoutWeatherChip = findViewById(R.id.layout_weather_chip)
        ivWeatherIcon = findViewById(R.id.iv_weather_icon)
        tvWeatherTemp = findViewById(R.id.tv_weather_temp)
        ivTotalTrafficIcon = findViewById(R.id.iv_total_traffic_icon)
        tvTotalTraffic = findViewById(R.id.tv_total_traffic)

        layoutWeatherChip.setOnClickListener {
            when {
                isWeatherChipSelected() -> {
                    startActivity(Intent(this, WeatherForecastActivity::class.java))
                }
                isTotalTrafficChipSelected() -> {
                    showTotalTrafficDetailDialog(this)
                }
            }
        }
    }

    private fun chipViews() = SearchChipGradientController.ChipViews(
        layoutWeatherChip = layoutWeatherChip,
        ivWeatherIcon = ivWeatherIcon,
        tvWeatherTemp = tvWeatherTemp,
        ivTotalTrafficIcon = ivTotalTrafficIcon,
        tvTotalTraffic = tvTotalTraffic
    )

    private fun weatherLocationReady(): Boolean =
        WeatherHelper.hasCustomLocation() || WeatherHelper.hasLocationPermission(this)

    private fun setupSearchBarChipSwipe() {
        val swipeThreshold = 64 * resources.displayMetrics.density
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                layoutWeatherChip.performClick()
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

        layoutWeatherChip.setOnTouchListener { view, event ->
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

        SearchChipGradientController.applyState(this, chipViews())

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
                layoutWeatherChip.isVisible = false
            }
        }
    }

    private fun hideWeatherChipViews() {
        ivWeatherIcon.isVisible = false
        tvWeatherTemp.isVisible = false
    }

    private fun hideTotalTrafficChip() {
        ivTotalTrafficIcon.isVisible = false
        tvTotalTraffic.isVisible = false
    }

    private fun refreshTotalTrafficChip() {
        val totalTraffic = MmkvManager.getTotalTrafficString()
        tvTotalTraffic.text = totalTraffic
        if (isTotalTrafficChipSelected()) {
            ivTotalTrafficIcon.isVisible = true
            tvTotalTraffic.isVisible = true
            layoutWeatherChip.isVisible = true
        }
    }

    private fun refreshWeatherChip() {
        if (!isWeatherChipSelected()) {
            layoutWeatherChip.isVisible = false
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
        layoutWeatherChip.isVisible = true
        if (cached != null) {
            applyWeatherToChip(cached)
        } else {
            ivWeatherIcon.setImageResource(RemixR.drawable.rmx_cloud_line)
            ivWeatherIcon.isVisible = true
            tvWeatherTemp.text = getString(R.string.weather_loading)
            tvWeatherTemp.isVisible = true
        }

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@SettingsActivity, force = true)
            if (weather == null) {
                if (cached == null && isWeatherChipSelected()) layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun loadWeatherChip() {
        if (!isWeatherChipSelected()) return
        layoutWeatherChip.isVisible = true

        val fresh = WeatherHelper.getCachedWeather()
        val stale = fresh ?: WeatherHelper.getCachedWeatherStale()

        if (stale != null) {
            applyWeatherToChip(stale)
        } else {
            ivWeatherIcon.setImageResource(RemixR.drawable.rmx_cloud_line)
            ivWeatherIcon.isVisible = true
            tvWeatherTemp.text = getString(R.string.weather_loading)
            tvWeatherTemp.isVisible = true
        }

        if (fresh != null) return

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@SettingsActivity)
            if (weather == null) {
                if (stale == null && isWeatherChipSelected()) layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun applyWeatherToChip(weather: WeatherHelper.WeatherResult) {
        ivWeatherIcon.setImageResource(weather.iconRes)
        tvWeatherTemp.text = weather.getTemperatureString(WeatherHelper.isCelsius())
        if (isWeatherChipSelected()) {
            ivWeatherIcon.isVisible = true
            tvWeatherTemp.isVisible = true
            layoutWeatherChip.isVisible = true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_settings -> {
                showDeleteConfirmDialog(
                    context = this,
                    titleRes = R.string.dialog_reset_settings_title,
                    messageRes = R.string.dialog_reset_settings_message,
                    iconRes = RemixR.drawable.rmx_arrow_go_back_line,
                    positiveTextRes = R.string.dialog_reset_settings_confirm,
                ) {
                    SettingsManager.resetAllSettings(applicationContext)
                    toastSuccess(R.string.reset_settings_success)
                    recreate()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSearchResultClicked(@NonNull result: SearchPreferenceResult) {
        searchActionView.cancelSearch()

        val targetActivity: Class<*>? = when (result.resourceFile) {
            R.xml.pref_ui_settings       -> UiSettingsActivity::class.java
            R.xml.pref_vpn_settings      -> VpnSettingsActivity::class.java
            R.xml.pref_core_settings     -> CoreSettingsActivity::class.java
            R.xml.pref_mux_settings      -> MuxSettingsActivity::class.java
            R.xml.pref_fragment_settings -> FragmentSettingsActivity::class.java
            R.xml.pref_advanced_settings -> AdvancedSettingsActivity::class.java
            R.xml.pref_observatory_settings -> ObservatorySettingsActivity::class.java
            else                         -> null
        }

        if (targetActivity != null) {
            val intent = Intent(this, targetActivity).apply {
                putExtra(AppConfig.EXTRA_HIGHLIGHT_KEY, result.key)
            }

            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )

            startActivity(intent, options.toBundle())
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            applyEdgeToEdgeListInsets()
        }

        private val bannerSettingsCard by lazy { findPreference<BannerSettingsPreference>("pref_banner_settings_card") }
        private val bannerSettingsCharacter by lazy { findPreference<ListPreference>(AppConfig.PREF_BANNER_SETTINGS_CHARACTER) }
        private val bannerCharacterLayout by lazy { findPreference<BannerCharacterLayoutDialog>("pref_banner_character_layout") }
        private val navigateUiSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_SETTINGS) }
        private val navigateVpnSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_VPN_SETTINGS) }
        private val navigateCoreSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_CORE_SETTINGS) }
        private val navigateMuxSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_MUX_SETTINGS) }
        private val navigateFragmentSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_FRAGMENT_SETTINGS) }
        private val navigateAdvancedSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_ADVANCED_SETTINGS) }
        private val navigateObservatorySettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_OBSERVATORY_SETTINGS) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_settings)

            bannerSettingsCharacter?.setOnPreferenceChangeListener { _, newValue ->
                // Persist immediately so the banner reflects the new value on this same bind pass
                // (onPreferenceChangeListener fires before the ListPreference itself persists).
                MmkvManager.encodeSettings(AppConfig.PREF_BANNER_SETTINGS_CHARACTER, newValue as? String)
                bannerSettingsCard?.refreshBanner()
                true
            }

            bannerSettingsCard?.setOnPreferenceClickListener {
                val expand = bannerSettingsCharacter?.isVisible != true
                bannerSettingsCharacter?.isVisible = expand
                bannerCharacterLayout?.isVisible = expand
                true
            }

            navigateUiSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), UiSettingsActivity::class.java))
                true
            }

            navigateVpnSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), VpnSettingsActivity::class.java))
                true
            }

            navigateCoreSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), CoreSettingsActivity::class.java))
                true
            }

            navigateMuxSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), MuxSettingsActivity::class.java))
                true
            }

            navigateFragmentSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), FragmentSettingsActivity::class.java))
                true
            }

            navigateAdvancedSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), AdvancedSettingsActivity::class.java))
                true
            }

            navigateObservatorySettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), ObservatorySettingsActivity::class.java))
                true
            }
        }
    }
}
