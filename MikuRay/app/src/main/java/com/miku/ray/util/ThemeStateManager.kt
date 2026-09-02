package com.miku.ray.util

import android.app.Activity
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager

class ThemeStateManager(private val activity: Activity) {

    private data class ThemeState(
        val themeKey: String,
        val dynamicColor: Boolean,
        val dynamicBanner: Boolean,
        val trueBlack: Boolean,
        val useCustomColor: Boolean,
        val customColor: Int,
        val bannerColor: Int,
        val dpi: Int,
        val fontScale: Float,
        val disableBannerHome: Boolean,
        val bannerHomeUri: String,
        val bannerHeight: Int,
        val blurBottomStatus: Boolean,
        val blurBottomRadius: Float,
        val blurBottomAlpha: Int,
        val font: String,
        val useCustomFont: Boolean,
        val customFontName: String,
        val headerTopRowPadding: Int,
        val fabExtended: Boolean,
        val showQuickActions: Boolean
    )

    private var currentState: ThemeState = fetchCurrentState()

    private fun fetchCurrentState(): ThemeState {
        return ThemeState(
            themeKey = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8",
            dynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false),
            dynamicBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false),
            trueBlack = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false),
            useCustomColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false),
            customColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0),
            bannerColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_BANNER_COLOR, 0),
            dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0),
            fontScale = MmkvManager.decodeSettingsFloat(AppConfig.PREF_APP_FONT_SIZE, AppConfig.FONT_SIZE_DEFAULT),
            disableBannerHome = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false),
            bannerHomeUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI) ?: "",
            bannerHeight = MmkvManager.decodeSettingsInt(AppConfig.PREF_HOME_BANNER_HEIGHT, AppConfig.HOME_BANNER_HEIGHT_DEFAULT),
            blurBottomStatus = MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false),
            blurBottomRadius = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS),
            blurBottomAlpha = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ALPHA, AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA),
            font = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "",
            useCustomFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false),
            customFontName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT_CUSTOM_NAME) ?: "",
            headerTopRowPadding = MmkvManager.decodeSettingsInt(AppConfig.PREF_HEADER_TOP_ROW_PADDING, AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT),
            fabExtended = MmkvManager.decodeSettingsBool(AppConfig.PREF_FAB_EXTENDED, false),
            showQuickActions = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_QUICK_ACTIONS, true)
        )
    }

    fun checkThemeChangedAndRecreate() {
        val newState = fetchCurrentState()

        if (currentState != newState) {
            currentState = newState
            activity.recreate()
        }
    }
}
