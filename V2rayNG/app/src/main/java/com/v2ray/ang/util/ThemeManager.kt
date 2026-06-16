package com.v2ray.ang.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

object ThemeManager {

    fun applyTheme(activity: Activity) {
        val isDynamic   = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        val useCustom   = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        val customColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        val isTrueBlack = isDarkMode(activity) && MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        val isDynamicBanner = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
        val bannerColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_BANNER_COLOR, 0)

        var themeApplied = false

        if (isDynamicBanner && bannerColor != 0) {
            val builder = DynamicColorsOptions.Builder()
                .setContentBasedSource(bannerColor)
            
            if (isTrueBlack) {
                builder.setThemeOverlay(R.style.ThemeOverlay_App_TrueBlack)
            }
            
            DynamicColors.applyToActivityIfAvailable(activity, builder.build())
            themeApplied = true
        }

        if (!themeApplied) {
            when {
                isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    DynamicColors.applyToActivityIfAvailable(activity)
                }
                useCustom && customColor != 0 -> {
                    applyCustomColorTheme(activity, customColor)
                }
                else -> {
                    val key = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "9"
                    activity.setTheme(getThemeStyleRes(key))
                }
            }
        }

        if (isTrueBlack && !themeApplied) {
            activity.theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack, true)
        }
    }

    @StyleRes
    fun getThemeStyleRes(key: String): Int {
        return when (key) {
            "1"  -> R.style.AppTheme_Red
            "2"  -> R.style.AppTheme_Pink
            "3"  -> R.style.AppTheme_Purple
            "4"  -> R.style.AppTheme_DeepPurple
            "5"  -> R.style.AppTheme_Indigo
            "6"  -> R.style.AppTheme_Blue
            "7"  -> R.style.AppTheme_Cyan
            "8"  -> R.style.AppTheme_Teal
            "9" -> R.style.AppTheme_Green
            "10" -> R.style.AppTheme_LightGreen
            "11" -> R.style.AppTheme_Lime
            "12" -> R.style.AppTheme_Yellow
            "13" -> R.style.AppTheme_Amber
            "14" -> R.style.AppTheme_Orange
            "15" -> R.style.AppTheme_Brown
            "16" -> R.style.AppTheme_BlueGrey
            else -> R.style.AppTheme_Teal
        }
    }

    fun applyCustomColorTheme(activity: Activity, @ColorInt seedColor: Int, isTrueBlack: Boolean = false) {
        val optionsBuilder = DynamicColorsOptions.Builder()
            .setContentBasedSource(seedColor)

        if (isTrueBlack) {
            optionsBuilder.setThemeOverlay(R.style.ThemeOverlay_App_TrueBlack)
        }

        DynamicColors.applyToActivityIfAvailable(activity, optionsBuilder.build())
    }

    fun getDynamicScheme(activity: Activity, @ColorInt seedColor: Int): SchemeTonalSpot {
        val hct = Hct.fromInt(seedColor)
        return SchemeTonalSpot(hct, isDarkMode(activity), 0.0)
    }

    fun isDarkMode(activity: Activity): Boolean {
        val uiMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun setAndSaveTheme(activity: Activity, key: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_APP_THEME, key)
        activity.recreate()
    }

    fun saveCustomColor(activity: Activity, @ColorInt color: Int) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_COLOR, true)
        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_COLOR, color)
        activity.recreate()
    }
    
    fun clearCustomColor(activity: Activity) {
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_COLOR, 0)
        activity.recreate()
    }
}

fun Context.getColorAttr(@AttrRes resId: Int): Int {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(resId, typedValue, true)) {
        if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    } else {
        Color.TRANSPARENT
    }
}

fun Context.getColorAttr(attrName: String): Int {
    var packageNameToUse = packageName
    var finalAttrName = attrName

    if (attrName.startsWith("android:")) {
        packageNameToUse = "android"
        finalAttrName = attrName.removePrefix("android:")
    }

    val resId = resources.getIdentifier(finalAttrName, "attr", packageNameToUse)
    if (resId == 0) return Color.TRANSPARENT
    return getColorAttr(resId)
}
