package com.v2ray.ang.ui.preference

import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

object CategoryStyleHelper {

    fun layoutForStyle(styleValue: String?): Int = when (styleValue) {
        "miku"  -> R.layout.uwu_preference_category_miku_1
        "miku2"  -> R.layout.uwu_preference_category_miku_2
        "teto"  -> R.layout.uwu_preference_category_teto_1
        "teto2"  -> R.layout.uwu_preference_category_teto_2
        "neru"  -> R.layout.uwu_preference_category_neru
        "gradient" -> R.layout.uwu_preference_category_gradient
        else      -> R.layout.uwu_preference_category_gradient
    }

    fun applyToGroup(styleValue: String?, group: PreferenceGroup) {
        val layout = layoutForStyle(styleValue)
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceCategory) pref.layoutResource = layout
            if (pref is PreferenceGroup) applyToGroup(styleValue, pref)
        }
    }

    fun applyToFragment(fragment: PreferenceFragmentCompat) {
        val saved = MmkvManager.decodeSettingsString(AppConfig.PREF_CATEGORY_STYLE, "gradient")
        fragment.preferenceScreen?.let { applyToGroup(saved, it) }
    }
}
