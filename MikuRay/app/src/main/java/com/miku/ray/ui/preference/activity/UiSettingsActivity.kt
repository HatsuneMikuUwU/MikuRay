package com.miku.ray.ui.preference.activity


import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.AppConfig
import com.miku.ray.SearchBarChipMode
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastError
import com.miku.ray.extension.toastInfo
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.ui.checkupdate.CheckUpdateActivity
import com.miku.ray.util.TabIconPickerAdapter
import com.miku.ray.ui.bottomsheet.IndicatorStyleBottomSheet
import com.miku.ray.ui.dialog.DpiSliderDialog
import com.miku.ray.ui.dialog.FontSizeSliderDialog
import kotlin.math.roundToInt
import com.miku.ray.ui.dialog.BlurIntensityDialog
import com.miku.ray.ui.dialog.BlurBottomIntensityDialog
import com.miku.ray.ui.dialog.ThemeColorDialog
import com.miku.ray.ui.dialog.TabIconPickerDialog
import com.miku.ray.ui.dialog.BannerHeightSliderDialog
import com.miku.ray.ui.dialog.HeaderTopRowPaddingDialog
import com.miku.ray.ui.preference.CustomBannerPreference
import com.miku.ray.ui.preference.CategoryStyleHelper
import com.miku.ray.util.AppNameHelper
import com.miku.ray.util.BannerColorExtractor
import com.miku.ray.util.CustomFontManager
import com.miku.ray.util.ThemeManager
import com.miku.ray.ui.weather.WeatherHelper
import com.miku.ray.util.showBlur
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class UiSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_ui_settings), subtitle = getString(R.string.subtitle_ui_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, UiSettingsFragment())
                .commit()
        }
    }

    class UiSettingsFragment : PreferenceFragmentCompat() {

        private val locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (SearchBarChipMode.current() == SearchBarChipMode.WEATHER) {
                    WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                }
            }

        private val appTheme by lazy { findPreference<Preference>(AppConfig.PREF_APP_THEME) }
        private val dynamicColor by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_COLOR) }
        private val dynamicColorBanner by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_COLOR_BANNER) }
        private val disableHomeBanner by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DISABLE_HOME_BANNER) }
        private val trueBlack by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_TRUE_BLACK) }
        private val enableBlur by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ENABLE_BLUR) }
        private val blurBottomStatus by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_BLUR_BOTTOM_STATUS) }
        private val appLanguage by lazy { findPreference<ListPreference>(AppConfig.PREF_LANGUAGE) }
        private val nightTheme by lazy { findPreference<ListPreference>(AppConfig.PREF_UI_MODE_NIGHT) }
        private val iconShape by lazy { findPreference<ListPreference>(AppConfig.PREF_ICON_SHAPE) }
        private val arrowShape by lazy { findPreference<ListPreference>(AppConfig.PREF_ARROW_SHAPE) }
        private val appIcon by lazy { findPreference<com.miku.ray.ui.dialog.AppIconPickerDialog>(AppConfig.PREF_APP_ICON) }
        private val customAppName by lazy { findPreference<ListPreference>(AppConfig.PREF_CUSTOM_APP_NAME) }
        private val customDpi by lazy { findPreference<DpiSliderDialog>(AppConfig.PREF_CUSTOM_DPI) }
        private val fontSizeSlider by lazy { findPreference<FontSizeSliderDialog>(AppConfig.PREF_APP_FONT_SIZE) }
        private val blurIntensity by lazy { findPreference<BlurIntensityDialog>(AppConfig.PREF_BLUR_INTENSITY) }
        private val blurBottomIntensity by lazy { findPreference<BlurBottomIntensityDialog>(AppConfig.PREF_BLUR_BOTTOM_INTENSITY) }
        private val indicatorStyle by lazy { findPreference<Preference>(AppConfig.PREF_INDICATOR_STYLE) }
        private val navigateCheckUpdate by lazy { findPreference<CustomBannerPreference>(AppConfig.PREF_NAVIGATE_CHECK_UPDATE) }
        private val appFont by lazy { findPreference<Preference>(AppConfig.PREF_APP_FONT) }
        private val customFontSwitch by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_APP_FONT_USE_CUSTOM) }
        private val customFontPick by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_PICK_CUSTOM_FONT) }
        private val customFontDelete by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_CUSTOM_FONT) }
        private val categoryStyle by lazy { findPreference<ListPreference>(AppConfig.PREF_CATEGORY_STYLE) }
        private val showSplash by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_SPLASH) }
        private val bannerHeightSlider by lazy { findPreference<BannerHeightSliderDialog>(AppConfig.PREF_HOME_BANNER_HEIGHT) }
        private val headerTopRowPaddingSlider by lazy { findPreference<HeaderTopRowPaddingDialog>(AppConfig.PREF_HEADER_TOP_ROW_PADDING) }
        private val changeHomeBannerImageAction by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_HOME_BANNER) }
        private val deleteHomeBannerImageAction by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_HOME_BANNER) }
        private val groupAllTabIcon by lazy { findPreference<Preference>(AppConfig.PREF_GROUP_ALL_TAB_ICON) }
        private val searchBarChip by lazy { findPreference<ListPreference>(AppConfig.PREF_SEARCH_BAR_CHIP) }
        private val selectedBannerStyleEnabled by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED) }
        private val selectedBannerCategory by lazy { findPreference<PreferenceCategory>("pref_category_selected_banner") }

        private val weatherUnit by lazy { findPreference<ListPreference>(AppConfig.PREF_WEATHER_USE_CELSIUS) }
        private val weatherCustomLocation by lazy { findPreference<EditTextPreference>(AppConfig.PREF_WEATHER_CUSTOM_LOCATION) }
        private val clearTotalTraffic by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_CLEAR_TOTAL_TRAFFIC) }
        private val searchChipGradient by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SEARCH_CHIP_GRADIENT) }
        private val toolbarCenterSubtitleMode by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_TOOLBAR_CENTER_SUBTITLE_MODE) }
        private val showRealtimeTrafficIp by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP) }
        private val showIspInfo by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_ISP_INFO) }

        private var tabIconPickerDialog: androidx.appcompat.app.AlertDialog? = null

        private val pickProfileImage =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) startCropProfileActivity(uri)
            }

        private val pickHomeBannerImage =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    if (isGif(uri)) {
                        saveGifBannerDirectly(uri, AppConfig.PREF_CUSTOM_HOME_BANNER_URI, "home_banner_") {
                            extractAndSaveBannerColor(it)
                            broadcastHomeBannerChanged()
                            requireContext().toastSuccess(getString(R.string.home_banner_updated))
                        }
                    } else {
                        startCropHomeBannerActivity(uri)
                    }
                }
            }

        private val pickSheetBannerImage =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    if (isGif(uri)) {
                        saveGifBannerDirectly(uri, AppConfig.PREF_CUSTOM_SHEET_BANNER_URI, "sheet_banner_") {
                            requireContext().toastSuccess(getString(R.string.sheet_banner_updated))
                        }
                    } else {
                        startCropSheetBannerActivity(uri)
                    }
                }
            }

        private val pickSelectedBannerImage =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) startCropSelectedBannerActivity(uri)
            }

        private val pickThemeBannerImage =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) startCropThemeBannerActivity(uri)
            }

        private val pickCustomFontFile =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@registerForActivityResult
                val displayName = queryDisplayName(uri)
                lifecycleScope.launch {
                    val savedFile = withContext(Dispatchers.IO) {
                        CustomFontManager.saveFontFile(requireContext(), uri, displayName)
                    }
                    if (savedFile != null) {
                        MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, true)
                        customFontSwitch?.isChecked = true
                        appFont?.isEnabled = false
                        updateCustomFontSummary()
                        activity?.recreate()
                    } else {
                        requireContext().toastError(getString(R.string.custom_font_invalid))
                    }
                }
            }

        private val cropHomeBannerImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    lifecycleScope.launch {
                        try {
                            val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
                            deleteOldFile(oldUri)
                            val savedUri = saveBannerFile(cacheUri, "home_banner_")
                            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_HOME_BANNER_URI, savedUri.toString())
                            SettingsManager.preloadBanner(requireContext(), savedUri.toString())

                            extractAndSaveBannerColor(savedUri)
                            broadcastHomeBannerChanged()
                            requireContext().toastSuccess(getString(R.string.home_banner_updated))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    UCrop.getError(result.data!!)?.printStackTrace()
                }
            }

        private val cropThemeBannerImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    lifecycleScope.launch {
                        try {
                            val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_THEME_BANNER_URI)
                            deleteOldFile(oldUri)
                            val savedUri = saveBannerFile(cacheUri, "theme_banner_")
                            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_THEME_BANNER_URI, savedUri.toString())
                            SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                            navigateCheckUpdate?.refresh()
                            requireContext().toastSuccess(getString(R.string.theme_banner_updated))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    UCrop.getError(result.data!!)?.printStackTrace()
                }
            }

        private val cropProfileImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    lifecycleScope.launch {
                        try {
                            val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
                            deleteOldFile(oldUri)
                            val savedUri = saveBannerFile(cacheUri, "profile_banner_")
                            MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_URI, savedUri.toString())
                            SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                            broadcastProfileChanged()
                            requireContext().toastSuccess(getString(R.string.custom_banner_profile_set))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    UCrop.getError(result.data!!)?.printStackTrace()
                }
            }

        private val cropSheetBannerImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    lifecycleScope.launch {
                        try {
                            val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
                            deleteOldFile(oldUri)
                            val savedUri = saveBannerFile(cacheUri, "sheet_banner_")
                            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI, savedUri.toString())
                            SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                            requireContext().toastSuccess(getString(R.string.sheet_banner_updated))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    UCrop.getError(result.data!!)?.printStackTrace()
                }
            }

        private val cropSelectedBannerImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    lifecycleScope.launch {
                        try {
                            val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
                            deleteOldFile(oldUri)
                            val savedUri = saveBannerFile(cacheUri, "selected_banner_")
                            MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_URI, savedUri.toString())
                            SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                            updateIndicatorStyleEnabledState()
                            broadcastSelectedBannerChanged()
                            requireContext().toastSuccess(getString(R.string.selected_banner_updated))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    UCrop.getError(result.data!!)?.printStackTrace()
                }
            }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_ui_settings)
            SearchBarChipMode.current()
            initPreferenceSummaries()
            updateCheckUpdateSummary()

            navigateCheckUpdate?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), CheckUpdateActivity::class.java))
                true
            }

            navigateCheckUpdate?.onImageClick = {
                pickThemeBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            navigateCheckUpdate?.onImageLongClick = {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_THEME_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.theme_banner_delete_title)
                        .setIcon(RemixR.drawable.rmx_delete_bin_line)
                        .setMessage(R.string.theme_banner_delete_summary)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            lifecycleScope.launch {
                                deleteOldFile(savedUri)
                                MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_THEME_BANNER_URI, "")
                                navigateCheckUpdate?.refresh()
                                requireContext().snackbarSuccess(getString(R.string.theme_banner_delete_summary), title = getString(R.string.title_alerter_success))
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showBlur()
                }
            }

            appTheme?.setOnPreferenceClickListener {
                ThemeColorDialog.show(parentFragmentManager)
                true
            }

            indicatorStyle?.setOnPreferenceClickListener {
                IndicatorStyleBottomSheet(requireContext()) {
                    SettingsChangeManager.makeRefreshDisplayPrefs()
                }.show()
                true
            }

            dynamicColor?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)

                if (enabled) {
                    MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
                    dynamicColorBanner?.isChecked = false
                }

                dynamicColorBanner?.isEnabled = !enabled && disableHomeBanner?.isChecked == false
                appTheme?.isEnabled = !enabled

                activity?.recreate()
                true
            }

            dynamicColorBanner?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR_BANNER, enabled)

                if (enabled) {
                    MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, false)
                    dynamicColor?.isChecked = false
                }

                dynamicColor?.isEnabled = !enabled
                appTheme?.isEnabled = !enabled

                activity?.recreate()
                true
            }

            trueBlack?.apply {
                val isNightModeActive = ThemeManager.isDarkMode(requireActivity())
                isEnabled = isNightModeActive
                summary = if (!isNightModeActive) getString(R.string.pref_true_black_only_in_night_mode)
                          else getString(R.string.summary_pref_true_black)
                setOnPreferenceChangeListener { _, _ -> activity?.recreate(); true }
            }

            toolbarCenterSubtitleMode?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_TOOLBAR_CENTER_SUBTITLE_MODE, newValue as Boolean)
                activity?.recreate()
                true
            }

            enableBlur?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_BLUR, newValue as Boolean)
                true
            }

            blurBottomStatus?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_STATUS, newValue as Boolean)
                true
            }

            setupLanguagePreference()

            nightTheme?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                updateTrueBlackState(isNightModeAfterChange(valueStr.toInt()))
                true
            }

            iconShape?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                requireContext().sendBroadcast(
                    android.content.Intent(AppConfig.BROADCAST_ACTION_ICON_SHAPE_CHANGED).apply {
                        putExtra(AppConfig.PREF_ICON_SHAPE, valueStr.ifEmpty { AppConfig.PREF_ICON_SHAPE_DEFAULT })
                    }
                )
                true
            }

            arrowShape?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                requireContext().sendBroadcast(
                    android.content.Intent(AppConfig.BROADCAST_ACTION_ARROW_SHAPE_CHANGED).apply {
                        putExtra(AppConfig.PREF_ARROW_SHAPE, valueStr.ifEmpty { AppConfig.PREF_ARROW_SHAPE_DEFAULT })
                    }
                )
                true
            }

            appIcon?.setOnPreferenceChangeListener { _, _ ->
                requireContext().toastSuccess(getString(R.string.app_icon_updated))
                true
            }

            customAppName?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                com.miku.ray.util.LauncherAliasSwitcher.applyNameVariant(requireContext().applicationContext, valueStr)
                updateCheckUpdateSummary(valueStr)
                true
            }

            appFont?.setOnPreferenceClickListener {
                val currentValue = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "default"
                com.miku.ray.ui.bottomsheet.FontPickerBottomSheet(requireContext(), currentValue) { value, label ->
                    MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT, value)
                    appFont?.summary = label
                    activity?.recreate()
                }.show()
                true
            }
            updateAppFontSummary()
            setupCustomFontPreferences()

            CategoryStyleHelper.applyToFragment(this)
            categoryStyle?.setOnPreferenceChangeListener { pref, newValue ->
                val styleValue = newValue as String
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(styleValue)
                    lp.summary = if (idx >= 0) lp.entries[idx] else styleValue
                }
                MmkvManager.encodeSettings(AppConfig.PREF_CATEGORY_STYLE, styleValue)
                preferenceScreen?.let { screen ->
                    CategoryStyleHelper.applyToGroup(styleValue, screen)
                    listView.adapter?.notifyDataSetChanged()
                }
                requireContext().sendBroadcast(
                    android.content.Intent(AppConfig.BROADCAST_ACTION_CATEGORY_STYLE_CHANGED)
                )
                true
            }

            showSplash?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_SHOW_SPLASH, newValue as Boolean)
                true
            }

            searchBarChip?.apply {
                value = SearchBarChipMode.current()
                setOnPreferenceChangeListener { _, newValue ->
                    val mode = SearchBarChipMode.save(newValue.toString())
                    value = mode
                    val selectedIndex = findIndexOfValue(mode)
                    summary = if (selectedIndex >= 0) entries[selectedIndex] else mode
                    if (mode == SearchBarChipMode.WEATHER) {
                        val hasForegroundPermission = ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasForegroundPermission && !WeatherHelper.hasCustomLocation()) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                        }
                    } else {
                        WeatherHelper.cancelBackgroundUpdates(requireContext())
                    }
                    updateChipPreferenceEnabledState()
                    updateClearTotalTrafficSummary()
                    true
                }
            }

            weatherUnit?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_USE_CELSIUS, valueStr)
                true
            }

            updateWeatherCustomLocationSummary(weatherCustomLocation?.text.orEmpty())
            weatherCustomLocation?.setOnPreferenceChangeListener { _, newValue ->
                val raw = (newValue as? String)?.trim().orEmpty()
                MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION, raw)
                WeatherHelper.clearCustomLocationCache()
                updateWeatherCustomLocationSummary(raw)
                if (SearchBarChipMode.current() == SearchBarChipMode.WEATHER) {
                    WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                }
                true
            }

            updateClearTotalTrafficSummary()
            clearTotalTraffic?.setOnPreferenceClickListener {
                if (MmkvManager.getTotalTrafficDetail() == null) {
                    requireContext().toastInfo(getString(R.string.pref_action_clear_total_traffic_summary_empty))
                    return@setOnPreferenceClickListener true
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.pref_action_clear_total_traffic_title)
                    .setIcon(RemixR.drawable.rmx_delete_bin_line)
                    .setMessage(R.string.confirm_clear_total_traffic)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        MmkvManager.clearTotalTrafficDataAndHistory()
                        updateClearTotalTrafficSummary()
                        requireContext().snackbarSuccess(getString(R.string.toast_total_traffic_cleared), title = getString(R.string.title_alerter_success))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
                true
            }

            updateChipPreferenceEnabledState()

            updateShowIspInfoEnabledState()
            showRealtimeTrafficIp?.setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP, checked)
                showIspInfo?.isEnabled = !checked
                showIspInfo?.summary = if (checked) {
                    getString(
                        R.string.summary_pref_disabled_realtime_traffic_ip,
                        getString(R.string.title_pref_show_realtime_traffic_ip)
                    )
                } else {
                    getString(R.string.summary_pref_show_isp_info)
                }
                true
            }

            updateGroupAllTabIconSummary()
            groupAllTabIcon?.setOnPreferenceClickListener {
                val currentIcon = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON)
                tabIconPickerDialog = TabIconPickerDialog(
                    context      = requireContext(),
                    currentIcon  = currentIcon,
                    onSelected   = { iconName ->
                        MmkvManager.encodeSettings(AppConfig.PREF_GROUP_ALL_TAB_ICON, iconName)
                        SettingsChangeManager.makeSetupGroupTab()
                        updateGroupAllTabIconSummary()
                    }
                ).show()
                true
            }

            setupProfilePreferences()
            setupHomeBannerPreferences()
            setupSheetBannerPreferences()
            setupSelectedBannerPreferences()
            setupParticlesPreferences()
            updateSelectedBannerCategoryVisibility()
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }

        private fun extractAndSaveBannerColor(uri: Uri) {
            lifecycleScope.launch {
                BannerColorExtractor.extractAndSave(requireContext(), uri) { colorChanged ->
                    if (colorChanged && MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)) {
                        activity?.recreate()
                    }
                }
            }
        }

        private fun setupSheetBannerPreferences() {
            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_SHEET_BANNER)?.setOnPreferenceClickListener {
                pickSheetBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_SHEET_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.sheet_banner_delete_title)
                        .setIcon(RemixR.drawable.rmx_delete_bin_line)
                        .setMessage(R.string.sheet_banner_delete_summary)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            lifecycleScope.launch {
                                deleteOldFile(savedUri)
                                MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI, "")
                                requireContext().snackbarSuccess(getString(R.string.sheet_banner_delete_summary), title = getString(R.string.title_alerter_success))
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showBlur()
                }
                true
            }
        }

        private fun setupCustomFontPreferences() {
            updateCustomFontSummary()

            val useCustom = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            customFontSwitch?.isChecked = useCustom
            appFont?.isEnabled = !useCustom

            customFontSwitch?.setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                if (checked && CustomFontManager.getFontFile(requireContext()) == null) {
                    pickCustomFontFile.launch(arrayOf("*/*"))
                    false
                } else {
                    MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, checked)
                    appFont?.isEnabled = !checked
                    activity?.recreate()
                    true
                }
            }

            customFontPick?.setOnPreferenceClickListener {
                pickCustomFontFile.launch(arrayOf("*/*"))
                true
            }

            customFontDelete?.setOnPreferenceClickListener {
                if (CustomFontManager.getFontFile(requireContext()) == null) {
                    requireContext().toastInfo(getString(R.string.custom_font_none_to_remove))
                    return@setOnPreferenceClickListener true
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.title_pref_app_font_custom_delete)
                    .setIcon(RemixR.drawable.rmx_delete_bin_line)
                    .setMessage(R.string.custom_font_delete_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        CustomFontManager.clearFont(requireContext())
                        MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
                        customFontSwitch?.isChecked = false
                        appFont?.isEnabled = true
                        updateCustomFontSummary()
                        activity?.recreate()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
                true
            }
        }

        private fun updateAppFontSummary() {
            val currentValue = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "default"
            val values = resources.getStringArray(R.array.app_font_values)
            val labels = resources.getStringArray(R.array.app_font_entries)
            val idx = values.indexOf(currentValue)
            appFont?.summary = if (idx >= 0) labels[idx] else currentValue
        }

        private fun updateCustomFontSummary() {
            val name = CustomFontManager.getFontDisplayName()
            customFontPick?.summary = name ?: getString(R.string.summary_pref_app_font_custom_pick_empty)
            customFontDelete?.apply {
                isEnabled = name != null
                summary = if (name != null) {
                    getString(R.string.summary_pref_app_font_custom_delete_set, name)
                } else {
                    getString(R.string.summary_pref_app_font_custom_delete_empty)
                }
            }
        }

        private fun updateClearTotalTrafficSummary() {
            val chipOn = SearchBarChipMode.current() == SearchBarChipMode.TOTAL_TRAFFIC
            val detail = MmkvManager.getTotalTrafficDetail()
            clearTotalTraffic?.apply {
                if (!chipOn) {
                    isEnabled = false
                    summary = getString(
                        R.string.summary_pref_action_clear_total_traffic_disabled,
                        getString(R.string.pref_search_bar_chip_total_traffic)
                    )
                    return@apply
                }
                isEnabled = detail != null
                summary = if (detail != null) {
                    val (uplink, downlink) = detail
                    val totalText = MmkvManager.formatTrafficBytesPublic(uplink + downlink)
                    getString(R.string.pref_action_clear_total_traffic_summary_set, totalText)
                } else {
                    getString(R.string.pref_action_clear_total_traffic_summary_empty)
                }
            }
        }

        private fun queryDisplayName(uri: Uri): String? {
            return try {
                requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun setupSelectedBannerPreferences() {
            updateIndicatorStyleEnabledState()

            selectedBannerStyleEnabled?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)
                setOnPreferenceChangeListener { _, newValue ->
                    val checked = newValue as Boolean
                    MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, checked)
                    updateIndicatorStyleEnabledState()
                    broadcastSelectedBannerChanged()
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_SELECTED_BANNER)?.setOnPreferenceClickListener {
                pickSelectedBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_SELECTED_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.selected_banner_delete_title)
                        .setIcon(RemixR.drawable.rmx_delete_bin_line)
                        .setMessage(R.string.selected_banner_delete_summary)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            lifecycleScope.launch {
                                deleteOldFile(savedUri)
                                MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_URI, "")
                                updateIndicatorStyleEnabledState()
                                broadcastSelectedBannerChanged()
                                requireContext().snackbarSuccess(getString(R.string.selected_banner_delete_summary), title = getString(R.string.title_alerter_success))
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showBlur()
                }
                true
            }
        }

        private fun updateIndicatorStyleEnabledState() {
            val bannerEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)

            indicatorStyle?.apply {
                isEnabled = !bannerEnabled
                summary = if (bannerEnabled) {
                    getString(R.string.pref_indicator_style_summary_disabled_by_banner)
                } else {
                    getString(R.string.pref_indicator_style_summary)
                }
            }
        }

        private fun setupProfilePreferences() {
            findPreference<EditTextPreference>(AppConfig.PREF_CUSTOM_PROFILE_NAME)?.apply {
                val currentName = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_PROFILE_NAME) ?: ""
                text = currentName
                summary = currentName.ifEmpty { getString(R.string.uwu_profile_banner_title) }
                setOnBindEditTextListener { editText ->
                    editText.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
                    editText.setSingleLine()
                }
                setOnPreferenceChangeListener { _, newValue ->
                    val newName = newValue.toString()
                    MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_PROFILE_NAME, newName)
                    summary = newName.ifEmpty { getString(R.string.uwu_profile_banner_title) }
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_PROFILE_BANNER)?.setOnPreferenceClickListener {
                pickProfileImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<ListPreference>(AppConfig.PREF_PROFILE_BANNER_SHAPE)?.apply {
                val savedShape = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_SHAPE)
                    ?: AppConfig.PREF_PROFILE_BANNER_SHAPE_DEFAULT
                value = savedShape
                summary = "%s"
                setOnPreferenceChangeListener { _, newValue ->
                    MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_SHAPE, newValue.toString())
                    broadcastProfileChanged()
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_PROFILE_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.delete_custom_banner_profile)
                        .setIcon(RemixR.drawable.rmx_delete_bin_line)
                        .setMessage(R.string.delete_custom_banner_profile_summary)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            lifecycleScope.launch {
                                deleteOldFile(savedUri)
                                MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_URI, "")
                                broadcastProfileChanged()
                                requireContext().snackbarSuccess(getString(R.string.delete_custom_banner_profile_summary), title = getString(R.string.title_alerter_success))
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showBlur()
                }
                true
            }
        }

        private fun setupHomeBannerPreferences() {
            disableHomeBanner?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)

                bannerHeightSlider?.isEnabled = !isChecked
                headerTopRowPaddingSlider?.isEnabled = !isChecked
                changeHomeBannerImageAction?.isEnabled = !isChecked
                deleteHomeBannerImageAction?.isEnabled = !isChecked

                setOnPreferenceChangeListener { _, newValue ->
                    val checked = newValue as Boolean
                    MmkvManager.encodeSettings(AppConfig.PREF_DISABLE_HOME_BANNER, checked)

                    val isDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
                    dynamicColorBanner?.isEnabled = !checked && !isDynamicColor

                    bannerHeightSlider?.isEnabled = !checked
                    headerTopRowPaddingSlider?.isEnabled = !checked
                    changeHomeBannerImageAction?.isEnabled = !checked
                    deleteHomeBannerImageAction?.isEnabled = !checked

                    if (checked) {
                        val isDynamicBannerActive = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
                        if (isDynamicBannerActive) {
                            MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
                            dynamicColorBanner?.isChecked = false
                            appTheme?.isEnabled = !isDynamicColor
                            activity?.recreate()
                        }
                    }

                    broadcastHomeBannerChanged()
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_HOME_BANNER)?.setOnPreferenceClickListener {
                pickHomeBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_HOME_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.home_banner_delete_title)
                        .setIcon(RemixR.drawable.rmx_delete_bin_line)
                        .setMessage(R.string.home_banner_delete_summary)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            lifecycleScope.launch {
                                deleteOldFile(savedUri)
                                MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_HOME_BANNER_URI, "")
                                MmkvManager.encodeSettings(AppConfig.PREF_BANNER_COLOR, 0)

                                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)) {
                                    activity?.recreate()
                                }
                                broadcastHomeBannerChanged()
                                requireContext().snackbarSuccess(getString(R.string.home_banner_delete_summary), title = getString(R.string.title_alerter_success))
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showBlur()
                }
                true
            }
        }

        private fun setupLanguagePreference() {
            val languageValues = resources.getStringArray(R.array.language_select_value)
            val languageLabels = resources.getStringArray(R.array.language_select)

            fun labelFor(tag: String): CharSequence {
                val idx = languageValues.indexOf(tag)
                return if (idx >= 0) languageLabels[idx] else tag
            }

            val currentTag = when (val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                "id" -> "in"
                else -> tag
            }
            val resolvedTag = if (currentTag in languageValues) currentTag else ""

            appLanguage?.apply {
                value = resolvedTag
                summary = labelFor(resolvedTag)
                setOnPreferenceChangeListener { _, newValue ->
                    val newTag = newValue as String
                    AppCompatDelegate.setApplicationLocales(
                        if (newTag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(newTag)
                    )
                    summary = labelFor(newTag)
                    value = newTag
                    true
                }
            }
        }

        private fun setupParticlesPreferences() {
            fun applySettingsEnabled(enabled: Boolean) {
                findPreference<Preference>(AppConfig.PREF_PARTICLES_SETTINGS)?.isEnabled = enabled
            }

            findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ENABLE_PARTICLES_SHEET)?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_PARTICLES_SHEET, false)
                applySettingsEnabled(isChecked)
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_PARTICLES_SHEET, enabled)
                    applySettingsEnabled(enabled)
                    true
                }
            }
        }

        private fun startCropSheetBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_sheet_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)
            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            val targetHeightPx = displayMetrics.density * 150

            val uCrop = UCrop.of(sourceUri, destUri)
                .withAspectRatio(screenWidthPx, targetHeightPx)
                .withMaxResultSize(1920, 1080)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(false)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropSheetBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropThemeBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_theme_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)
            
            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            val screenHeightPx = displayMetrics.heightPixels.toFloat()

            val uCrop = UCrop.of(sourceUri, destUri)
                .withAspectRatio(screenWidthPx, screenHeightPx)
                .withMaxResultSize(896, 1984)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(false)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(true)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropThemeBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropSelectedBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_selected_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)

            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            val targetHeightPx = displayMetrics.density * 120

            val uCrop = UCrop.of(sourceUri, destUri)
                .withAspectRatio(screenWidthPx, targetHeightPx)
                .withMaxResultSize(1280, 720)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(false)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropSelectedBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropHomeBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_home_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)

            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()

            val heightDp = MmkvManager.decodeSettingsInt(
                AppConfig.PREF_HOME_BANNER_HEIGHT,
                AppConfig.HOME_BANNER_HEIGHT_DEFAULT
            )
            val targetHeightPx = displayMetrics.density * heightDp

            val uCrop = UCrop.of(sourceUri, destUri)
                .withAspectRatio(screenWidthPx, targetHeightPx)
                .withMaxResultSize(1920, 1080)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(false)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }

            cropHomeBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropProfileActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_profile_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)
            val uCrop = UCrop.of(sourceUri, destUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(512, 512)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(true)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropProfileImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun isGif(uri: Uri): Boolean {
            val mimeType = requireContext().contentResolver.getType(uri)
            if (mimeType == "image/gif") return true
            val path = uri.path ?: return false
            return path.lowercase().endsWith(".gif")
        }

        private fun saveGifBannerDirectly(
            sourceUri: Uri,
            prefKey: String,
            fileNamePrefix: String,
            onSuccess: (Uri) -> Unit
        ) {
            lifecycleScope.launch {
                try {
                    val oldUri = MmkvManager.decodeSettingsString(prefKey)
                    deleteOldFile(oldUri)
                    val savedUri = saveBannerFile(sourceUri, fileNamePrefix, ext = "gif")
                    MmkvManager.encodeSettings(prefKey, savedUri.toString())
                    SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                    onSuccess(savedUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @Throws(IOException::class)
        private suspend fun saveBannerFile(sourceUri: Uri, fileNamePrefix: String, ext: String = "jpg"): Uri = withContext(Dispatchers.IO) {
            val ctx = requireContext()
            val bannersDir = File(ctx.filesDir, "banners").apply { mkdirs() }
            val destFile = File(bannersDir, "${fileNamePrefix}${System.currentTimeMillis()}.$ext")
            ctx.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                if (sourceUri.scheme == "file") {
                    val tempFile = File(sourceUri.path!!)
                    if (tempFile.exists() && tempFile.absolutePath.contains(ctx.cacheDir.absolutePath)) {
                        tempFile.delete()
                    }
                }
            } catch (_: Exception) {}
            return@withContext Uri.fromFile(destFile)
        }

        private suspend fun deleteOldFile(uriString: String?) = withContext(Dispatchers.IO) {
            if (uriString.isNullOrEmpty()) return@withContext
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    File(uri.path!!).takeIf { it.exists() }?.delete()
                } else {
                    try { requireContext().contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        private fun broadcastProfileChanged() {
            requireContext().sendBroadcast(
                android.content.Intent(AppConfig.BROADCAST_ACTION_PROFILE_BANNER_CHANGED)
            )
        }

        private fun broadcastHomeBannerChanged() {
            requireContext().sendBroadcast(
                android.content.Intent(AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED)
            )
        }

        private fun broadcastSelectedBannerChanged() {
            com.miku.ray.util.SelectedProfileBannerController.broadcastChanged(requireContext())
        }

        private fun updateCheckUpdateSummary(pendingVariant: String? = null) {
            val appName = if (pendingVariant != null) {
                AppNameHelper.getDisplayName(requireContext(), pendingVariant)
            } else {
                AppNameHelper.getDisplayName(requireContext())
            }
            navigateCheckUpdate?.summary = getString(R.string.uwu_update_summary, appName)
        }

        private fun initPreferenceSummaries() {
            appIcon?.refreshSummary()
            fun traverse(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val p = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> traverse(p)
                        is ListPreference -> {
                            if (p.value == null && !p.entryValues.isNullOrEmpty()) {
                                p.value = p.entryValues[0].toString()
                            }
                            p.summary = p.entry ?: ""
                            p.setOnPreferenceChangeListener { pref, newValue ->
                                val lp = pref as ListPreference
                                val idx = lp.findIndexOfValue(newValue as? String)
                                lp.summary = (if (idx >= 0) lp.entries[idx] else newValue) as CharSequence?
                                true
                            }
                        }
                        else -> {}
                    }
                }
            }
            preferenceScreen?.let { traverse(it) }
        }


        override fun onStart() {
            super.onStart()
            val isDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
            val isDynamicBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
            val isDisableHomeBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)

            appTheme?.isEnabled = !isDynamicColor && !isDynamicBanner

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                dynamicColor?.isEnabled = false
                dynamicColor?.summary = requireContext().getString(R.string.summary_pref_dynamic_color_unavailable)
                dynamicColorBanner?.isEnabled = false
                dynamicColorBanner?.summary = requireContext().getString(R.string.summary_pref_dynamic_color_unavailable)
            } else {
                dynamicColor?.isEnabled = !isDynamicBanner
                dynamicColorBanner?.isEnabled = !isDynamicColor && !isDisableHomeBanner
            }

            bannerHeightSlider?.isEnabled = !isDisableHomeBanner
            headerTopRowPaddingSlider?.isEnabled = !isDisableHomeBanner
            changeHomeBannerImageAction?.isEnabled = !isDisableHomeBanner
            deleteHomeBannerImageAction?.isEnabled = !isDisableHomeBanner

            val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
            val systemDpi = resources.displayMetrics.densityDpi
            customDpi?.summary = if (savedDpi > 0) savedDpi.toString() else systemDpi.toString()

            val savedFontSize = MmkvManager.decodeSettingsFloat(AppConfig.PREF_APP_FONT_SIZE, AppConfig.FONT_SIZE_DEFAULT)
            fontSizeSlider?.summary = "${(savedFontSize * 100f).roundToInt()}%"

            val savedRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS)
            val savedRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
            blurIntensity?.updateSummary(savedRadius, savedRounds)

            val savedBottomRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS)
            val savedBottomAlpha = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ALPHA, AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA)
            blurBottomIntensity?.updateSummary(savedBottomRadius, savedBottomAlpha)

            updateSelectedBannerCategoryVisibility()
        }

        private fun updateSelectedBannerCategoryVisibility() {
            val isGridMode = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
            selectedBannerCategory?.isVisible = !isGridMode
        }

        private fun updateTrueBlackState(isNight: Boolean) {
            trueBlack?.isEnabled = isNight
            trueBlack?.summary = if (!isNight) getString(R.string.pref_true_black_only_in_night_mode)
                                  else getString(R.string.summary_pref_true_black)
            if (!isNight && trueBlack?.isChecked == true) {
                trueBlack?.isChecked = false
                MmkvManager.encodeSettings(AppConfig.PREF_TRUE_BLACK, false)
            }
        }

        private fun isNightModeAfterChange(mode: Int): Boolean = when (mode) {
            1    -> true
            2    -> false
            3    -> !ThemeManager.isAutoDayTime()
            else -> ThemeManager.isDarkMode(requireActivity())
        }

        private fun updateGroupAllTabIconSummary() {
            val iconName = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON)
            if (iconName.isNullOrEmpty()) {
                groupAllTabIcon?.summary = getString(R.string.sub_tab_icon_none)
                groupAllTabIcon?.setIcon(RemixR.drawable.rmx_apps_line)
            } else {
                groupAllTabIcon?.summary = TabIconPickerAdapter.labelFor(iconName)
                val resId = resources.getIdentifier(iconName, "drawable", requireContext().packageName)
                if (resId != 0) groupAllTabIcon?.setIcon(resId)
            }
        }

        private fun updateWeatherSubPrefsEnabled(weatherOn: Boolean) {
            weatherUnit?.isEnabled = weatherOn
            weatherCustomLocation?.isEnabled = weatherOn
        }

        private fun updateWeatherCustomLocationSummary(raw: String) {
            val pref = weatherCustomLocation ?: return
            pref.summary = if (raw.isNotBlank()) {
                raw
            } else {
                val entry = WeatherHelper.getCachedWeatherEntry()
                if (entry != null && (entry.latitude != 0.0 || entry.longitude != 0.0)) {
                    getString(
                        R.string.pref_weather_custom_location_summary_current_coords,
                        entry.latitude, entry.longitude
                    )
                } else {
                    getString(R.string.pref_weather_custom_location_summary_auto)
                }
            }
        }

        private fun updateChipPreferenceEnabledState() {
            val mode = SearchBarChipMode.current()
            searchBarChip?.value = mode
            searchChipGradient?.isEnabled = mode != SearchBarChipMode.DISABLED
            updateWeatherSubPrefsEnabled(mode == SearchBarChipMode.WEATHER)
        }

        private fun updateShowIspInfoEnabledState() {
            val realtimeTrafficOn = showRealtimeTrafficIp?.isChecked == true
            showIspInfo?.isEnabled = !realtimeTrafficOn
            showIspInfo?.summary = if (realtimeTrafficOn) {
                getString(
                    R.string.summary_pref_disabled_realtime_traffic_ip,
                    getString(R.string.title_pref_show_realtime_traffic_ip)
                )
            } else {
                getString(R.string.summary_pref_show_isp_info)
            }
        }

        override fun onDestroyView() {
            tabIconPickerDialog?.dismiss()
            tabIconPickerDialog = null
            super.onDestroyView()
        }
    }
}
