package com.v2ray.ang.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

object IconSwitcher {

    private const val ALIAS_DEFAULT = "com.v2ray.ang.ui.LauncherDefault"
    private const val ALIAS_ALT1 = "com.v2ray.ang.ui.LauncherAlt1"
    private const val ALIAS_ALT2 = "com.v2ray.ang.ui.LauncherAlt2"
    private const val ALIAS_ALT3 = "com.v2ray.ang.ui.LauncherAlt3"
    private const val ALIAS_ALT4 = "com.v2ray.ang.ui.LauncherAlt4"
    private const val ALIAS_ALT5 = "com.v2ray.ang.ui.LauncherAlt5"

    fun currentVariant(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_APP_ICON) ?: AppConfig.APP_ICON_DEFAULT

    fun applyVariant(context: Context, variant: String) {
        val pm = context.packageManager

        setAliasEnabled(pm, context.packageName, ALIAS_DEFAULT, variant == AppConfig.APP_ICON_DEFAULT)
        setAliasEnabled(pm, context.packageName, ALIAS_ALT1, variant == AppConfig.APP_ICON_ALT1)
        setAliasEnabled(pm, context.packageName, ALIAS_ALT2, variant == AppConfig.APP_ICON_ALT2)
        setAliasEnabled(pm, context.packageName, ALIAS_ALT3, variant == AppConfig.APP_ICON_ALT3)
        setAliasEnabled(pm, context.packageName, ALIAS_ALT4, variant == AppConfig.APP_ICON_ALT4)
        setAliasEnabled(pm, context.packageName, ALIAS_ALT5, variant == AppConfig.APP_ICON_ALT5)

        MmkvManager.encodeSettings(AppConfig.PREF_APP_ICON, variant)
    }

    private fun setAliasEnabled(pm: PackageManager, packageName: String, aliasName: String, enabled: Boolean) {
        val component = ComponentName(packageName, aliasName)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
    }
}
