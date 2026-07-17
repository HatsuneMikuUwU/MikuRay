package com.v2ray.ang.ui.preference.activity

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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.preference.SearchPreferenceHighlighter
import com.v2ray.ang.ui.CheckUpdateActivity
import com.v2ray.ang.ui.TabIconPickerAdapter
import com.v2ray.ang.ui.bottomsheet.IndicatorStyleBottomSheet
import com.v2ray.ang.ui.dialog.DpiSliderDialog
import com.v2ray.ang.ui.dialog.BlurIntensityDialog
import com.v2ray.ang.ui.dialog.BlurBottomIntensityDialog
import com.v2ray.ang.ui.dialog.ThemeColorDialog
import com.v2ray.ang.ui.dialog.TabIconPickerDialog
import com.v2ray.ang.ui.dialog.BannerHeightSliderDialog
import com.v2ray.ang.ui.dialog.HeaderTopRowPaddingDialog
import com.v2ray.ang.ui.preference.CustomBannerPreference
import com.v2ray.ang.ui.preference.CategoryStyleHelper
import com.v2ray.ang.util.AppNameHelper
import com.v2ray.ang.util.BannerColorExtractor
import com.v2ray.ang.util.CustomFontManager
import com.v2ray.ang.util.ThemeManager
import com.v2ray.ang.ui.weather.WeatherHelper
import com.v2ray.ang.util.showBlur
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
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_ui_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, UiSettingsFragment())
                .commit()
        }
    }

    class UiSettingsFragment : PreferenceFragmentCompat() {

        companion object {
            private const val REQUEST_CODE_LOCATION = 9001
        }

        private val appTheme by lazy { findPreference<Preference>(AppConfig.PREF_APP_THEME) }
        private val dynamicColor by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_COLOR) }
        private val dynamicColorBanner by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_COLOR_BANNER) }
        private val showHomeBanner by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_HOME_BANNER) }
        private val trueBlack by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_TRUE_BLACK) }
        private val enableBlur by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ENABLE_BLUR) }
        private val blurBottomStatus by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_BLUR_BOTTOM_STATUS) }
        private val nightTheme by lazy { findPreference<ListPreference>(AppConfig.PREF_UI_MODE_NIGHT) }
        private val iconShape by lazy { findPreference<ListPreference>(AppConfig.PREF_ICON_SHAPE) }
        private val appIcon by lazy { findPreference<com.v2ray.ang.ui.dialog.AppIconPickerDialog>(AppConfig.PREF_APP_ICON) }
        private val customAppName by lazy { findPreference<ListPreference>(AppConfig.PREF_CUSTOM_APP_NAME) }
        private val customDpi by lazy { findPreference<DpiSliderDialog>(AppConfig.PREF_CUSTOM_DPI) }
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
        private val groupAllTabIcon by lazy { findPreference<Preference>(AppConfig.PREF_GROUP_ALL_TAB_ICON) }
        private val showWeatherChip by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_WEATHER_CHIP) }
        private val selectedBannerStyleEnabled by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED) }

        private val weatherUnit by lazy { findPreference<ListPreference>(AppConfig.PREF_WEATHER_USE_CELSIUS) }
        private val weatherCustomLocation by lazy { findPreference<EditTextPreference>(AppConfig.PREF_WEATHER_CUSTOM_LOCATION) }
        private val showTotalTrafficChip by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP) }
        private val searchChipGradient by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SEARCH_CHIP_GRADIENT) }

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
            initPreferenceSummaries()
            updateCheckUpdateSummary()

            navigateCheckUpdate?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), CheckUpdateActivity::class.java))
                true
            }

            appTheme?.setOnPreferenceClickListener {
                ThemeColorDialog.show(parentFragmentManager)
                true
            }

            indicatorStyle?.setOnPreferenceClickListener {
                IndicatorStyleBottomSheet(requireContext()) {}.show()
                true
            }

            dynamicColor?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
                
                if (enabled) {
                    MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
                    dynamicColorBanner?.isChecked = false
                }
                
                dynamicColorBanner?.isEnabled = !enabled && showHomeBanner?.isChecked == true
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

            enableBlur?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_BLUR, newValue as Boolean)
                true
            }

            blurBottomStatus?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_STATUS, newValue as Boolean)
                true
            }

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
                com.v2ray.ang.util.LauncherAliasSwitcher.applyNameVariant(requireContext().applicationContext, valueStr)
                updateCheckUpdateSummary(valueStr)
                true
            }

            appFont?.setOnPreferenceClickListener {
                val currentValue = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "default"
                com.v2ray.ang.ui.bottomsheet.FontPickerBottomSheet(requireContext(), currentValue) { value, label ->
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

            showWeatherChip?.setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_SHOW_WEATHER_CHIP, checked)
                if (checked) {
                    val hasForegroundPermission = ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasForegroundPermission && !WeatherHelper.hasCustomLocation()) {
                        requestPermissions(
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                            REQUEST_CODE_LOCATION
                        )
                    } else {
                        WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                    }
                } else {
                    WeatherHelper.cancelBackgroundUpdates(requireContext())
                }
                showTotalTrafficChip?.isEnabled = !checked
                updateWeatherSubPrefsEnabled(checked)
                searchChipGradient?.isEnabled = checked || (showTotalTrafficChip?.isChecked == true)
                true
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
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
                    WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                }
                true
            }

            showTotalTrafficChip?.setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP, checked)
                showWeatherChip?.isEnabled = !checked
                searchChipGradient?.isEnabled = checked || (showWeatherChip?.isChecked == true)
                true
            }

            updateChipPreferenceEnabledState()

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
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
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
                    // No custom font saved yet — send the user to pick one instead of
                    // silently flipping the switch on with nothing loaded.
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
            showHomeBanner?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
                
                bannerHeightSlider?.isEnabled = isChecked
                headerTopRowPaddingSlider?.isEnabled = isChecked
                
                setOnPreferenceChangeListener { _, newValue ->
                    val checked = newValue as Boolean
                    MmkvManager.encodeSettings(AppConfig.PREF_SHOW_HOME_BANNER, checked)
                    
                    val isDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
                    dynamicColorBanner?.isEnabled = checked && !isDynamicColor
                    
                    bannerHeightSlider?.isEnabled = checked
                    headerTopRowPaddingSlider?.isEnabled = checked
                    
                    if (!checked) {
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

        private fun setupParticlesPreferences() {
            findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DISABLE_PARTICLES_SHEET)?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_PARTICLES_SHEET, false)
                setOnPreferenceChangeListener { _, newValue ->
                    MmkvManager.encodeSettings(AppConfig.PREF_DISABLE_PARTICLES_SHEET, newValue as Boolean)
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
            // Persistent banners must live in filesDir, NOT cacheDir — the system's
            // "Clear Cache" wipes cacheDir but leaves filesDir untouched.
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
            com.v2ray.ang.util.SelectedProfileBannerController.broadcastChanged(requireContext())
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

        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            when (requestCode) {
                REQUEST_CODE_LOCATION -> {
                    if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
                        WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                    }
                }
            }
        }

        override fun onStart() {
            super.onStart()
            val isDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
            val isDynamicBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
            val isShowHomeBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
            
            appTheme?.isEnabled = !isDynamicColor && !isDynamicBanner
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                dynamicColor?.isEnabled = false
                dynamicColor?.summary = requireContext().getString(R.string.summary_pref_dynamic_color_unavailable)
                dynamicColorBanner?.isEnabled = false
                dynamicColorBanner?.summary = requireContext().getString(R.string.summary_pref_dynamic_color_unavailable)
            } else {
                dynamicColor?.isEnabled = !isDynamicBanner
                dynamicColorBanner?.isEnabled = !isDynamicColor && isShowHomeBanner
            }
            
            bannerHeightSlider?.isEnabled = isShowHomeBanner
            headerTopRowPaddingSlider?.isEnabled = isShowHomeBanner
            
            val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
            val systemDpi = resources.displayMetrics.densityDpi
            customDpi?.summary = if (savedDpi > 0) savedDpi.toString() else systemDpi.toString()
            
            val savedRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS)
            val savedRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
            blurIntensity?.updateSummary(savedRadius, savedRounds)
            
            val savedBottomRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS)
            val savedBottomRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS)
            blurBottomIntensity?.updateSummary(savedBottomRadius, savedBottomRounds)
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
            else -> ThemeManager.isDarkMode(requireActivity())
        }

        private fun updateGroupAllTabIconSummary() {
            val iconName = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON)
            if (iconName.isNullOrEmpty()) {
                groupAllTabIcon?.summary = getString(R.string.sub_tab_icon_none)
                groupAllTabIcon?.setIcon(R.drawable.filter_all)
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
            val weatherOn = showWeatherChip?.isChecked == true
            val trafficOn = showTotalTrafficChip?.isChecked == true
            showWeatherChip?.isEnabled = !trafficOn
            showTotalTrafficChip?.isEnabled = !weatherOn
            searchChipGradient?.isEnabled = weatherOn || trafficOn
            updateWeatherSubPrefsEnabled(weatherOn)
        }

        override fun onDestroyView() {
            tabIconPickerDialog?.dismiss()
            tabIconPickerDialog = null
            super.onDestroyView()
        }
    }
}
