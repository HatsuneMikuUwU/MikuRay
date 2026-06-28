package com.v2ray.ang.ui.preference.activity

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.util.showDeleteConfirmDialog

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rootView = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom,    displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val navigateUiSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_SETTINGS) }
        private val navigateVpnSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_VPN_SETTINGS) }
        private val navigateCoreSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_CORE_SETTINGS) }
        private val navigateMuxSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_MUX_SETTINGS) }
        private val navigateFragmentSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_FRAGMENT_SETTINGS) }
        private val navigateAdvancedSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_ADVANCED_SETTINGS) }
        private val resetAllSettings by lazy { findPreference<Preference>(AppConfig.PREF_RESET_ALL_SETTINGS) }

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

            resetAllSettings?.setOnPreferenceClickListener {
                showDeleteConfirmDialog(
                    context = requireContext(),
                    titleRes = R.string.dialog_reset_settings_title,
                    messageRes = R.string.dialog_reset_settings_message,
                    iconRes = R.drawable.ic_restore_24dp,
                    positiveTextRes = R.string.dialog_reset_settings_confirm,
                ) {
                    SettingsManager.resetAllSettings(requireContext().applicationContext)
                    requireContext().toastSuccess(R.string.reset_settings_success)
                    activity?.recreate()
                }
                true
            }
        }
    }
}
