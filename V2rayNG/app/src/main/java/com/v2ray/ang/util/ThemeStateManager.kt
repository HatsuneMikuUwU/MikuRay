package com.v2ray.ang.util

import android.app.Activity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

class ThemeStateManager(private val activity: Activity) {

    private var currentThemeKey: String = "8"
    private var currentDynamicColor: Boolean = false
    private var currentTrueBlack: Boolean = false
    private var currentUseCustomColor: Boolean = false
    private var currentCustomColor: Int = 0
    private var currentDpi: Int = 0
    private var currentShowBannerHome: Boolean = true
    private var currentBannerHomeUri: String = ""
    private var currentBlurBottomStatus: Boolean = false
    private var currentBlurBottomRadius: Int = 20
    private var currentBlurBottomRounds: Int = 3
    private var currentFont: String = "" 

    init {
        loadState()
    }

    private fun loadState() {
        currentThemeKey = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8"
        currentDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        currentTrueBlack = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        currentUseCustomColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        currentCustomColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        currentDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)      
        currentShowBannerHome = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
        currentBannerHomeUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI) ?: ""
        currentBlurBottomStatus = MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)
        currentBlurBottomRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS)
        currentBlurBottomRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS)
        currentFont = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "" 
    }

    fun checkThemeChangedAndRecreate() {
        val newThemeKey = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8"
        val newDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        val newTrueBlack = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        val newUseCustomColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        val newCustomColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        val newDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val newShowBannerHome = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
        val newBannerHomeUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI) ?: ""
        val newBlurBottomStatus = MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)
        val newBlurBottomRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS)
        val newBlurBottomRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS)
        val newFont = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "" 

        if (currentThemeKey != newThemeKey ||
            currentDynamicColor != newDynamicColor ||
            currentTrueBlack != newTrueBlack ||
            currentUseCustomColor != newUseCustomColor ||
            currentCustomColor != newCustomColor ||
            currentDpi != newDpi ||
            currentShowBannerHome != newShowBannerHome ||
            currentBannerHomeUri != newBannerHomeUri ||
            currentBlurBottomStatus != newBlurBottomStatus ||
            currentBlurBottomRadius != newBlurBottomRadius ||
            currentBlurBottomRounds != newBlurBottomRounds ||
            currentFont != newFont
        ) {
            loadState()
            activity.recreate()
        }
    }
}
