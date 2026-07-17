package com.v2ray.ang.util

import android.app.Activity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

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
        val showBannerHome: Boolean,
        val bannerHomeUri: String,
        val bannerHeight: Int,
        val blurBottomStatus: Boolean,
        val blurBottomRadius: Int,
        val blurBottomRounds: Int,
        val font: String,
        val useCustomFont: Boolean,
        val customFontName: String,
        val headerTopRowPadding: Int
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
            showBannerHome = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true),
            bannerHomeUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI) ?: "",
            bannerHeight = MmkvManager.decodeSettingsInt(AppConfig.PREF_HOME_BANNER_HEIGHT, AppConfig.HOME_BANNER_HEIGHT_DEFAULT),
            blurBottomStatus = MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false),
            blurBottomRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS),
            blurBottomRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS),
            font = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "",
            useCustomFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false),
            customFontName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT_CUSTOM_NAME) ?: "",
            headerTopRowPadding = MmkvManager.decodeSettingsInt(AppConfig.PREF_HEADER_TOP_ROW_PADDING, AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT)
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
