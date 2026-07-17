package com.v2ray.ang.ui.preference.activity

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.NonNull
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bytehamster.lib.preferencesearch.SearchPreferenceActionView
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.HelperBaseActivity
import com.v2ray.ang.ui.PerAppProxyActivity
import com.v2ray.ang.ui.weather.WeatherForecastActivity
import com.v2ray.ang.util.SearchChipGradientController
import com.v2ray.ang.ui.weather.WeatherHelper
import com.v2ray.ang.util.showDeleteConfirmDialog
import kotlinx.coroutines.launch
import com.v2ray.ang.ui.preference.FullSpanPreference

class SettingsActivity : HelperBaseActivity(), SearchPreferenceResultListener {

    private lateinit var searchActionView: SearchPreferenceActionView
    private lateinit var btnClearHistory: com.google.android.material.button.MaterialButton
    private lateinit var layoutWeatherChip: LinearLayout
    private lateinit var ivWeatherIcon: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var ivTotalTrafficIcon: ImageView
    private lateinit var tvTotalTraffic: TextView

    private var isColdStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_search)

        val rootView = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom, displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_settings))

        setupSearchActionView()
        setupWeatherTrafficChip()

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
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
                startActivity(Intent(this, WeatherForecastActivity::class.java))
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

    private fun refreshSearchBarChip() {
        val weatherEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)
        val totalTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP, false)

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
        if (totalTraffic == null) {
            layoutWeatherChip.isVisible = false
            return
        }
        tvTotalTraffic.text = totalTraffic
        ivTotalTrafficIcon.isVisible = true
        tvTotalTraffic.isVisible = true
        layoutWeatherChip.isVisible = true
    }

    private fun refreshWeatherChip() {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
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
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) return

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
            ivWeatherIcon.setImageResource(R.drawable.ic_cloud)
            ivWeatherIcon.isVisible = true
            tvWeatherTemp.text = getString(R.string.weather_loading)
            tvWeatherTemp.isVisible = true
        }

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@SettingsActivity, force = true)
            if (weather == null) {
                if (cached == null) layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun loadWeatherChip() {
        layoutWeatherChip.isVisible = true

        val fresh = WeatherHelper.getCachedWeather()
        val stale = fresh ?: WeatherHelper.getCachedWeatherStale()

        if (stale != null) {
            applyWeatherToChip(stale)
        } else {
            ivWeatherIcon.setImageResource(R.drawable.ic_cloud)
            ivWeatherIcon.isVisible = true
            tvWeatherTemp.text = getString(R.string.weather_loading)
            tvWeatherTemp.isVisible = true
        }

        if (fresh != null) return

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@SettingsActivity)
            if (weather == null) {
                if (stale == null) layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun applyWeatherToChip(weather: WeatherHelper.WeatherResult) {
        ivWeatherIcon.setImageResource(weather.iconRes)
        tvWeatherTemp.text = weather.getTemperatureString(WeatherHelper.isCelsius())
        ivWeatherIcon.isVisible = true
        tvWeatherTemp.isVisible = true
        layoutWeatherChip.isVisible = true
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
                    iconRes = R.drawable.ic_restore_24dp,
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

        private val navigateUiSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_SETTINGS) }
        private val navigateVpnSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_VPN_SETTINGS) }
        private val navigateCoreSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_CORE_SETTINGS) }
        private val navigateMuxSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_MUX_SETTINGS) }
        private val navigateFragmentSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_FRAGMENT_SETTINGS) }
        private val navigateAdvancedSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_ADVANCED_SETTINGS) }
        private val navigateObservatorySettings by lazy { findPreference<FullSpanPreference>(AppConfig.PREF_NAVIGATE_OBSERVATORY_SETTINGS) }

        override fun onCreateRecyclerView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            savedInstanceState: Bundle?
        ): RecyclerView {
            val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
            recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

            val paddingHorizontalPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                resources.displayMetrics
            ).toInt()

            val paddingVerticalPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                resources.displayMetrics
            ).toInt()

            recyclerView.setPadding(
                paddingHorizontalPx,
                paddingVerticalPx,
                paddingHorizontalPx,
                paddingVerticalPx
            )

            recyclerView.clipToPadding = false

            return recyclerView
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_settings)

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
