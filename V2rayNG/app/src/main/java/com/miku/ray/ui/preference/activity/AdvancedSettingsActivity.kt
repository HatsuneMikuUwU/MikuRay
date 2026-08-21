package com.miku.ray.ui.preference.activity

import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.handler.MmkvManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.util.Utils
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.ui.preference.CategoryStyleHelper

class AdvancedSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_advanced), subtitle = getString(R.string.subtitle_advanced_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, AdvancedSettingsFragment())
                .commit()
        }
    }

    class AdvancedSettingsFragment : PreferenceFragmentCompat() {

        private val mode by lazy { findPreference<ListPreference>(AppConfig.PREF_MODE) }
        private val ipApiUrl by lazy { findPreference<EditTextPreference>(AppConfig.PREF_IP_API_URL) }
        private val realPingConcurrency by lazy { findPreference<EditTextPreference>(AppConfig.PREF_REAL_PING_CONCURRENCY) }
        private val countryCodeTimeout by lazy { findPreference<EditTextPreference>(AppConfig.PREF_COUNTRY_CODE_TIMEOUT) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_advanced_settings)
            initPreferenceSummaries()
            CategoryStyleHelper.applyToFragment(this)

            realPingConcurrency?.summary =
                MmkvManager.decodeSettingsString(AppConfig.PREF_REAL_PING_CONCURRENCY, "16")
            realPingConcurrency?.setOnPreferenceChangeListener { pref, newValue ->
                val concurrency = (newValue as? String)?.toIntOrNull() ?: 16
                pref.summary = concurrency.toString()
                true
            }

            countryCodeTimeout?.summary =
                MmkvManager.decodeSettingsString(AppConfig.PREF_COUNTRY_CODE_TIMEOUT, "5")
            countryCodeTimeout?.setOnPreferenceChangeListener { pref, newValue ->
                val timeoutSeconds = (newValue as? String)?.toIntOrNull() ?: 5
                pref.summary = timeoutSeconds.toString()
                true
            }

            mode?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                true
            }
            mode?.dialogLayoutResource = R.layout.preference_with_help_link

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP, false)) {
                ipApiUrl?.isEnabled = false
                ipApiUrl?.summary = getString(
                    R.string.summary_pref_disabled_realtime_traffic_ip,
                    getString(R.string.title_pref_show_realtime_traffic_ip)
                )
            }
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }

        private fun initPreferenceSummaries() {
            fun traverse(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val p = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> traverse(p)
                        is EditTextPreference -> {
                            p.summary = p.text.orEmpty()
                            p.setOnPreferenceChangeListener { pref, newValue ->
                                pref.summary = (newValue as? String).orEmpty()
                                true
                            }
                        }
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
    }

    fun onModeHelpClicked(view: View) {
        Utils.openUri(this, AppConfig.APP_WIKI_MODE)
    }
}
