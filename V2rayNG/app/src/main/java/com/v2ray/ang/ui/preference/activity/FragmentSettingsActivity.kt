package com.v2ray.ang.ui.preference.activity

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.preference.CategoryStyleHelper

class FragmentSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rootView = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navigationBars.bottom)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_fragment_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, FragmentSettingsFragment())
                .commit()
        }
    }

    class FragmentSettingsFragment : PreferenceFragmentCompat() {

        private val fragment by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_FRAGMENT_ENABLED) }
        private val fragmentPackets by lazy { findPreference<ListPreference>(AppConfig.PREF_FRAGMENT_PACKETS) }
        private val fragmentLength by lazy { findPreference<EditTextPreference>(AppConfig.PREF_FRAGMENT_LENGTH) }
        private val fragmentInterval by lazy { findPreference<EditTextPreference>(AppConfig.PREF_FRAGMENT_INTERVAL) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_fragment_settings)
            initPreferenceSummaries()
            CategoryStyleHelper.applyToFragment(this)

            fragment?.setOnPreferenceChangeListener { _, newValue ->
                updateFragment(newValue as Boolean)
                true
            }
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

        override fun onStart() {
            super.onStart()
            updateFragment(MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false))
        }

        private fun updateFragment(enabled: Boolean) {
            fragmentPackets?.isEnabled = enabled
            fragmentLength?.isEnabled = enabled
            fragmentInterval?.isEnabled = enabled
        }
    }
}
