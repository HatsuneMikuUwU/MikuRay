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
import com.v2ray.ang.ui.preference.SearchPreferenceHighlighter
import com.v2ray.ang.ui.preference.CategoryStyleHelper

class CoreSettingsActivity : BaseActivity() {

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
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_core_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, CoreSettingsFragment())
                .commit()
        }
    }

    class CoreSettingsFragment : PreferenceFragmentCompat() {

        private val enableLocalProxy by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ENABLE_LOCAL_PROXY) }
        private val socksPort by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PORT) }
        private val dynamicSocksPort by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_SOCKS_PORT) }
        private val socksUsername by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_USERNAME) }
        private val socksPassword by lazy { findPreference<EditTextPreference>(AppConfig.PREF_SOCKS_PASSWORD) }
        private val socksEnableUdp by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SOCKS_ENABLE_UDP) }
        private val proxySharing by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_PROXY_SHARING) }
        private val appendHttpProxy by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_APPEND_HTTP_PROXY) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_core_settings)
            initPreferenceSummaries()
            CategoryStyleHelper.applyToFragment(this)

            enableLocalProxy?.setOnPreferenceChangeListener { _, newValue ->
                updateEnableLocalProxy(newValue as Boolean)
                true
            }

            dynamicSocksPort?.setOnPreferenceChangeListener { _, newValue ->
                updateDynamicSocksPort(newValue as Boolean)
                true
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
                            if (p.key == AppConfig.PREF_SOCKS_PASSWORD) {
                                p.summary = if (p.text.isNullOrEmpty()) "" else "******"
                                p.setOnPreferenceChangeListener { pref, newValue ->
                                    pref.summary = if ((newValue as? String).isNullOrEmpty()) "" else "******"
                                    true
                                }
                            } else {
                                p.summary = p.text.orEmpty()
                                p.setOnPreferenceChangeListener { pref, newValue ->
                                    pref.summary = (newValue as? String).orEmpty()
                                    true
                                }
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
            updateEnableLocalProxy(MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true))
            updateDynamicSocksPort(MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false))
        }

        private fun updateEnableLocalProxy(enabled: Boolean) {
            val dynamic = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)
            socksPort?.isEnabled = enabled && !dynamic
            dynamicSocksPort?.isEnabled = enabled
            socksUsername?.isEnabled = enabled
            socksPassword?.isEnabled = enabled
            socksEnableUdp?.isEnabled = enabled
            proxySharing?.isEnabled = enabled
            appendHttpProxy?.isEnabled = enabled
            if (!enabled) {
                if (appendHttpProxy?.isChecked == true) {
                    appendHttpProxy?.isChecked = false
                    MmkvManager.encodeSettings(AppConfig.PREF_APPEND_HTTP_PROXY, false)
                }
                appendHttpProxy?.isEnabled = false
            }
        }

        private fun updateDynamicSocksPort(enabled: Boolean) {
            socksPort?.isEnabled = (enableLocalProxy?.isChecked == true) && !enabled
        }
    }
}
