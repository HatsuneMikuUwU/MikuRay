package com.v2ray.ang

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.extension.ForegroundActivityTracker
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.ThemeManager
import com.v2ray.ang.util.CustomFontManager
import com.v2ray.ang.util.AppFontResolver
import com.neko.crashlog.CrashHandler

class AngApplication : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        lateinit var application: AngApplication

        fun getCustomTypeface(context: Context, fontName: String? = null): Typeface? {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                return CustomFontManager.getTypeface(context)
            }
            val name = fontName ?: MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT)
            return AppFontResolver.getTypeface(context, name)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        ForegroundActivityTracker.register(this)
        registerActivityLifecycleCallbacks(this)
        WorkManager.initialize(this, workManagerConfiguration)
        SettingsManager.initApp(this)
        SettingsManager.setNightMode()
        SettingsManager.preloadAllBanners(this)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(activity)

        val useCustomFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
        if (useCustomFont) {
            com.v2ray.ang.util.CustomFontManager.applyGlobalOverride(activity)
        } else {
            com.v2ray.ang.util.CustomFontManager.restoreGlobalOverride()
            val fontName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT)
            val fontOverlayId = getFontStyleResId(fontName)
            if (fontOverlayId != 0) {
                activity.theme.applyStyle(fontOverlayId, true)
            }
        }
        
        val isTrueBlack = ThemeManager.isDarkMode(activity) && MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        if (isTrueBlack) {
            activity.theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack_DialogFix, true)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val hide = MmkvManager.decodeSettingsBool(AppConfig.PREF_HIDE_FROM_RECENT_APPS, false)
        try {
            val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = activityManager.appTasks
            if (tasks.isNotEmpty()) {
                tasks[0].setExcludeFromRecents(hide)
            }
        } catch (e: Exception) {
            Log.w("AngApplication", "Failed to set excludeFromRecents: ${e.message}")
        }
    }

    private fun getFontStyleResId(fontName: String?): Int {
        return when (fontName) {
            "google"       -> R.style.StyleFontGoogle
            "roboto"       -> R.style.StyleFontRoboto
            "poppins"      -> R.style.StyleFontPoppins
            "chococooky"   -> R.style.StyleFontChocoCooky
            "simpleday"    -> R.style.StyleFontSimpleDay
            "fucek"        -> R.style.StyleFontFucek
            "sfprodisplay" -> R.style.StyleFontSFProDisplay
            "dancingscript"-> R.style.StyleFontDancingScript
            "cream"        -> R.style.StyleFontCream
            "oneui"        -> R.style.StyleFontOneUI
            "inconsolata"  -> R.style.StyleFontInconsolata
            "emilyscandy"  -> R.style.StyleFontEmilysCandy
            "summerdream"  -> R.style.StyleFontSummerDream
            "rine"         -> R.style.StyleFontRine
            "evolve"       -> R.style.StyleFontEvolve
            else           -> 0
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
