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
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.util.Utils
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.preference.CategoryStyleHelper

class AdvancedSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rootView = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_advanced))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, AdvancedSettingsFragment())
                .commit()
        }
    }

    class AdvancedSettingsFragment : PreferenceFragmentCompat() {

        private val mode by lazy { findPreference<ListPreference>(AppConfig.PREF_MODE) }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_advanced_settings)
            initPreferenceSummaries()
            CategoryStyleHelper.applyToFragment(this)

            mode?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                true
            }
            mode?.dialogLayoutResource = R.layout.preference_with_help_link
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
