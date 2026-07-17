package com.v2ray.ang.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

object LauncherAliasSwitcher {

    private const val ALIAS_PACKAGE_PREFIX = "com.v2ray.ang.ui.Launcher"

    private val ICON_KEYS = listOf(
        AppConfig.APP_ICON_DEFAULT,
        AppConfig.APP_ICON_ALT1,
        AppConfig.APP_ICON_ALT2,
        AppConfig.APP_ICON_ALT3,
        AppConfig.APP_ICON_ALT4,
        AppConfig.APP_ICON_ALT5,
    )

    private val NAME_KEYS = listOf(
        AppConfig.APP_NAME_DEFAULT,
        AppConfig.APP_NAME_MIKU1,
        AppConfig.APP_NAME_MIKU2,
        AppConfig.APP_NAME_MIKU3,
        AppConfig.APP_NAME_MIKU4,
        AppConfig.APP_NAME_MIKU5,
        AppConfig.APP_NAME_MIKU6,
        AppConfig.APP_NAME_MIKU7,
        AppConfig.APP_NAME_MIKU8,
        AppConfig.APP_NAME_MIKU9,
        AppConfig.APP_NAME_MIKU10,
        AppConfig.APP_NAME_MIKU11,
        AppConfig.APP_NAME_MIKU12,
    )

    private fun segment(key: String): String = key.replaceFirstChar { it.uppercase() }

    private fun aliasName(iconKey: String, nameKey: String): String =
        "$ALIAS_PACKAGE_PREFIX${segment(iconKey)}${segment(nameKey)}"

    fun currentIconVariant(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_APP_ICON) ?: AppConfig.APP_ICON_DEFAULT

    fun currentNameVariant(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_APP_NAME) ?: AppConfig.APP_NAME_DEFAULT

    fun applyIconVariant(context: Context, iconVariant: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_APP_ICON, iconVariant)
        applyAliases(context, iconVariant, currentNameVariant())
    }

    fun applyNameVariant(context: Context, nameVariant: String) {
        applyAliases(context, currentIconVariant(), nameVariant)
    }

    private fun applyAliases(context: Context, iconVariant: String, nameVariant: String) {
        val pm = context.packageManager
        val target = aliasName(iconVariant, nameVariant)
        for (iconKey in ICON_KEYS) {
            for (nameKey in NAME_KEYS) {
                val alias = aliasName(iconKey, nameKey)
                setAliasEnabled(pm, context.packageName, alias, alias == target)
            }
        }
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
