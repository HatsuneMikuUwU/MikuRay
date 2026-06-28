package com.v2ray.ang.ui.preference.activity

import android.os.Bundle
import android.view.View
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
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.preference.SearchPreferenceHighlighter
import com.v2ray.ang.ui.PerAppProxyActivity
import com.v2ray.ang.ui.preference.CategoryStyleHelper
import kotlinx.coroutines.launch

class VpnSettingsActivity : BaseActivity() {

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
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_vpn_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, VpnSettingsFragment())
                .commit()
        }
    }

    class VpnSettingsFragment : PreferenceFragmentCompat() {

        private val localDns by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_LOCAL_DNS_ENABLED) }
        private val fakeDns by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_FAKE_DNS_ENABLED) }
        private val appendHttpProxy by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_APPEND_HTTP_PROXY) }
        private val vpnDns by lazy { findPreference<EditTextPreference>(AppConfig.PREF_VPN_DNS) }
        private val vpnBypassLan by lazy { findPreference<ListPreference>(AppConfig.PREF_VPN_BYPASS_LAN) }
        private val vpnInterfaceAddress by lazy { findPreference<ListPreference>(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX) }
        private val vpnMtu by lazy { findPreference<EditTextPreference>(AppConfig.PREF_VPN_MTU) }
        private val useHevTun by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_USE_HEV_TUNNEL) }
        private val hevTunLogLevel by lazy { findPreference<ListPreference>(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL) }
        private val hevTunRwTimeout by lazy { findPreference<EditTextPreference>(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) }
        private val navigatePerAppProxy by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_PER_APP_PROXY_SETTINGS) }
        private val keepAwake by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_KEEP_AWAKE) }
        private val tcpKeepaliveIdle by lazy { findPreference<EditTextPreference>(AppConfig.PREF_TCP_KEEPALIVE_IDLE) }
        private val wsHeartbeatPeriod by lazy { findPreference<EditTextPreference>(AppConfig.PREF_WS_HEARTBEAT_PERIOD) }
        private val enableRootMode by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ROOT_MODE_ENABLE) }
        private val lanSharing by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ROOT_LAN_SHARING) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_vpn_settings)
            initPreferenceSummaries()
            CategoryStyleHelper.applyToFragment(this)

            localDns?.setOnPreferenceChangeListener { _, any ->
                updateLocalDns(any as Boolean)
                true
            }

            useHevTun?.setOnPreferenceChangeListener { _, newValue ->
                updateHevTunSettings(newValue as Boolean)
                true
            }

            navigatePerAppProxy?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), PerAppProxyActivity::class.java))
                true
            }

            enableRootMode?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && !RootManager.cachedRoot()) {
                    lifecycleScope.launch {
                        if (checkAndRequestRoot()) {
                            enableRootMode?.isChecked = true
                        }
                    }
                    false
                } else {
                    true
                }
            }

            lanSharing?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && !RootManager.cachedRoot()) {
                    lifecycleScope.launch {
                        if (checkAndRequestRoot()) {
                            lanSharing?.isChecked = true
                        }
                    }
                    false
                } else {
                    true
                }
            }
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
        }

        /**
         * Probes for root access off the main thread (spawns su, can block briefly) and
         * surfaces a snackbar if it's not available. Used to gate the Root mode and LAN
         * sharing toggles so they can't be left on without root actually being granted.
         */
        private suspend fun checkAndRequestRoot(): Boolean {
            val hasRoot = RootManager.refresh()
            if (!isAdded) return false
            if (!hasRoot) {
                requireContext().snackbarError(
                    getString(R.string.toast_root_required),
                    title = getString(R.string.title_alerter_error)
                )
            }
            return hasRoot
        }

        private fun initPreferenceSummaries() {
            fun traverse(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val p = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> traverse(p)
                        is EditTextPreference -> {
                            val defaults = mapOf(
                                AppConfig.PREF_TCP_KEEPALIVE_IDLE to "30",
                                AppConfig.PREF_WS_HEARTBEAT_PERIOD to "60"
                            )
                            p.summary = p.text.takeUnless { it.isNullOrEmpty() }
                                ?: defaults[p.key]
                                ?: ""
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
            val isVpnMode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, VPN) == VPN
            updateModeDependent(isVpnMode)
            if (isVpnMode) {
                updateLocalDns(MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false))
                updateHevTunSettings(MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true))
            }
        }

        private fun updateModeDependent(vpn: Boolean) {
            localDns?.isEnabled = vpn
            fakeDns?.isEnabled = vpn
            appendHttpProxy?.isEnabled = vpn
            vpnDns?.isEnabled = vpn
            vpnBypassLan?.isEnabled = vpn
            vpnInterfaceAddress?.isEnabled = vpn
            vpnMtu?.isEnabled = vpn
            useHevTun?.isEnabled = vpn
            keepAwake?.isEnabled = vpn
        }

        private fun updateLocalDns(enabled: Boolean) {
            fakeDns?.isEnabled = enabled
            vpnDns?.isEnabled = !enabled
        }

        private fun updateHevTunSettings(enabled: Boolean) {
            hevTunLogLevel?.isEnabled = enabled
            hevTunRwTimeout?.isEnabled = enabled
        }
    }
}
