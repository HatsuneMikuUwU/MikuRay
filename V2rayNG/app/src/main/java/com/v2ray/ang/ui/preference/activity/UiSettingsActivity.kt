package com.v2ray.ang.ui.preference.activity

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.CheckUpdateActivity
import com.v2ray.ang.ui.bottomsheet.IndicatorStyleBottomSheet
import com.v2ray.ang.ui.dialog.DpiSliderDialog
import com.v2ray.ang.ui.dialog.ThemeColorDialog
import com.v2ray.ang.ui.preference.CustomBannerPreference
import com.v2ray.ang.util.ThemeManager
import com.v2ray.ang.util.showBlur
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.IOException

class UiSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_ui_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, UiSettingsFragment())
                .commit()
        }
    }

    class UiSettingsFragment : PreferenceFragmentCompat() {

        private val appTheme by lazy { findPreference<Preference>(AppConfig.PREF_APP_THEME) }
        private val dynamicColor by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_COLOR) }
        private val trueBlack by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_TRUE_BLACK) }
        private val enableBlur by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ENABLE_BLUR) }
        private val nightTheme by lazy { findPreference<ListPreference>(AppConfig.PREF_UI_MODE_NIGHT) }
        private val iconShape by lazy { findPreference<ListPreference>(AppConfig.PREF_ICON_SHAPE) }
        private val customDpi by lazy { findPreference<DpiSliderDialog>(AppConfig.PREF_CUSTOM_DPI) }
        private val indicatorStyle by lazy { findPreference<Preference>(AppConfig.PREF_INDICATOR_STYLE) }
        private val navigateCheckUpdate by lazy { findPreference<CustomBannerPreference>(AppConfig.PREF_NAVIGATE_CHECK_UPDATE) }

        private val pickProfileImage =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) startCropProfileActivity(uri)
            }

        private val pickHomeBannerImage =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) startCropHomeBannerActivity(uri)
            }

        private val cropHomeBannerImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    try {
                        val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
                        deleteOldFile(oldUri)
                        val savedUri = saveToCache(cacheUri, "home_banner_")
                        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_HOME_BANNER_URI, savedUri.toString())
                        broadcastHomeBannerChanged()
                        requireContext().toastSuccess(getString(R.string.home_banner_updated))
                    } catch (e: Exception) {
                        requireContext().toastSuccess(getString(R.string.home_banner_updated))
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    val err = UCrop.getError(result.data!!)
                    err?.printStackTrace()
                }
            }

        private val cropProfileImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    try {
                        val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
                        deleteOldFile(oldUri)
                        val savedUri = saveToCache(cacheUri, "profile_banner_")
                        MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_URI, savedUri.toString())
                        broadcastProfileChanged()
                        requireContext().toastSuccess(getString(R.string.custom_banner_profile_set))
                    } catch (e: Exception) {
                        requireContext().toastSuccess(getString(R.string.custom_banner_profile_set))
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    val err = UCrop.getError(result.data!!)
                    err?.printStackTrace()
                }
            }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_ui_settings)
            initPreferenceSummaries()

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

            setupProfilePreferences()
            setupHomeBannerPreferences()
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
                            deleteOldFile(savedUri)
                            MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_URI, "")
                            broadcastProfileChanged()
                            requireContext().toastSuccess(getString(R.string.custom_banner_profile_set))
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showBlur()
                } else {
                    requireContext().toastSuccess(getString(R.string.delete_custom_banner_profile_summary))
                }
                true
            }
        }

        private fun setupHomeBannerPreferences() {
            findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_HOME_BANNER)?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
                setOnPreferenceChangeListener { _, newValue ->
                    MmkvManager.encodeSettings(AppConfig.PREF_SHOW_HOME_BANNER, newValue as Boolean)
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
                            deleteOldFile(savedUri)
                            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_HOME_BANNER_URI, "")
                            broadcastHomeBannerChanged()
                            requireContext().toastSuccess(getString(R.string.home_banner_updated))
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .showBlur()
                } else {
                    requireContext().toastSuccess(getString(R.string.home_banner_delete_summary))
                }
                true
            }
        }

        private fun startCropHomeBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_home_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)

            val uCrop = UCrop.of(sourceUri, destUri)
                .withAspectRatio(16f, 9f)
                .withMaxResultSize(1920, 1080)

            try {
                val options = UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(false)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                }
                uCrop.withOptions(options)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cropHomeBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun broadcastHomeBannerChanged() {
            requireContext().sendBroadcast(
                android.content.Intent(AppConfig.BROADCAST_ACTION_HOME_BANNER_CHANGED)
            )
        }

        private fun startCropProfileActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_profile_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)

            val uCrop = UCrop.of(sourceUri, destUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(512, 512)

            try {
                val options = UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(true)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                }
                uCrop.withOptions(options)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cropProfileImage.launch(uCrop.getIntent(requireContext()))
        }

        @Throws(IOException::class)
        private fun saveToCache(sourceCacheUri: Uri, fileNamePrefix: String): Uri {
            val ctx = requireContext()
            val destFile = File(ctx.cacheDir, "${fileNamePrefix}${System.currentTimeMillis()}.jpg")

            ctx.contentResolver.openInputStream(sourceCacheUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            try {
                if (sourceCacheUri.scheme == "file") {
                    val tempFile = File(sourceCacheUri.path!!)
                    if (tempFile.exists() && tempFile.absolutePath.contains(ctx.cacheDir.absolutePath)) {
                        tempFile.delete()
                    }
                }
            } catch (_: Exception) {}

            return Uri.fromFile(destFile)
        }

        private fun deleteOldFile(uriString: String?) {
            if (uriString.isNullOrEmpty()) return
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

        private fun initPreferenceSummaries() {
            fun traverse(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val p = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> traverse(p)
                        is ListPreference -> {
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
            appTheme?.isEnabled = !isDynamicColor

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                dynamicColor?.isEnabled = false
                dynamicColor?.summary = requireContext().getString(R.string.summary_pref_dynamic_color_unavailable)
            }

            val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
            val systemDpi = resources.displayMetrics.densityDpi
            customDpi?.summary = if (savedDpi > 0) savedDpi.toString() else systemDpi.toString()
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
    }
}
