package com.v2ray.ang.ui.preference.activity

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.preference.SearchPreferenceHighlighter
import com.v2ray.ang.ui.preference.CategoryStyleHelper

class ObservatorySettingsActivity : BaseActivity() {

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
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_observatory_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, ObservatorySettingsFragment())
                .commit()
        }
    }

    class ObservatorySettingsFragment : PreferenceFragmentCompat() {

        private val observatoryLeastPingInterval by lazy { findPreference<EditTextPreference>(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL) }
        private val observatoryLeastLoadInterval by lazy { findPreference<EditTextPreference>(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL) }
        private val observatoryLeastLoadSampling by lazy { findPreference<EditTextPreference>(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING) }
        private val observatoryLeastLoadTimeout by lazy { findPreference<EditTextPreference>(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_observatory_settings)
            initPreferenceSummaries()
            CategoryStyleHelper.applyToFragment(this)

            listOf(
                observatoryLeastPingInterval,
                observatoryLeastLoadInterval,
                observatoryLeastLoadTimeout
            ).forEach { pref ->
                pref?.setOnPreferenceChangeListener { preference, newValue ->
                    val duration = (newValue as? String).orEmpty().trim()
                    if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
                        preference.summary = duration
                        true
                    } else {
                        requireContext().snackbarError(getString(R.string.toast_invalid_observatory_duration), title = getString(R.string.title_alerter_error))
                        false
                    }
                }
            }

            observatoryLeastLoadSampling?.setOnPreferenceChangeListener { preference, newValue ->
                val sampling = (newValue as? String).orEmpty().trim().toIntOrNull()?.takeIf { it > 0 }
                if (sampling != null) {
                    preference.summary = sampling.toString()
                    true
                } else {
                    requireContext().snackbarError(getString(R.string.toast_invalid_observatory_sampling), title = getString(R.string.title_alerter_error))
                    false
                }
            }
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
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
}
