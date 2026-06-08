package com.v2ray.ang.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

object ThemeManager {

    private var cachedBannerUri: String? = null
    private var cachedBannerBitmap: Bitmap? = null

    fun clearBannerCache() {
        cachedBannerUri = null
        cachedBannerBitmap?.recycle()
        cachedBannerBitmap = null
    }

    fun applyTheme(activity: Activity) {

        val isDynamic   = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        val useCustom   = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        val customColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        val isTrueBlack = isDarkMode(activity) && MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        val isDynamicBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
        val bannerUriStr = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)

        var themeApplied = false

        if (isDynamicBanner && !bannerUriStr.isNullOrEmpty()) {
            try {
                val bitmapToUse: Bitmap? = if (bannerUriStr == cachedBannerUri && cachedBannerBitmap != null) {
                    cachedBannerBitmap
                } else {
                    cachedBannerBitmap?.recycle() 
                    
                    val uri = Uri.parse(bannerUriStr)
                    val inputStream = activity.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    cachedBannerUri = bannerUriStr
                    cachedBannerBitmap = bitmap
                    bitmap
                }

                if (bitmapToUse != null) {
                    val builder = DynamicColorsOptions.Builder()
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setContentBasedSource(bitmapToUse)
                    } else {
                        val scaled = Bitmap.createScaledBitmap(bitmapToUse, 1, 1, true)
                        val dominantColor = scaled.getPixel(0, 0)
                        scaled.recycle()
                        builder.setContentBasedSource(dominantColor)
                    }
                    
                    if (isTrueBlack) {
                        builder.setThemeOverlay(R.style.ThemeOverlay_App_TrueBlack)
                    }
                    
                    DynamicColors.applyToActivityIfAvailable(activity, builder.build())
                    themeApplied = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            clearBannerCache()
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
                    applyCustomColorTheme(activity, themeSeedColorFor(activity, key))
                }
            }
        }

        if (isTrueBlack && !themeApplied) {
            activity.theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack, true)
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

    @ColorInt
    fun themeSeedColorFor(context: Context, key: String): Int {
        val colorRes = when (key) {
            "1"  -> R.color.palette_red
            "2"  -> R.color.palette_pink
            "3"  -> R.color.palette_purple
            "4"  -> R.color.palette_deep_purple
            "5"  -> R.color.palette_indigo
            "6"  -> R.color.palette_blue
            "7"  -> R.color.palette_light_blue
            "8"  -> R.color.palette_cyan
            "9"  -> R.color.palette_teal
            "10" -> R.color.palette_green
            "11" -> R.color.palette_light_green
            "12" -> R.color.palette_lime
            "13" -> R.color.palette_yellow
            "14" -> R.color.palette_amber
            "15" -> R.color.palette_orange
            "16" -> R.color.palette_deep_orange
            "17" -> R.color.palette_brown
            "18" -> R.color.palette_blue_grey
            else -> R.color.palette_teal
        }
        return ContextCompat.getColor(context, colorRes)
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
