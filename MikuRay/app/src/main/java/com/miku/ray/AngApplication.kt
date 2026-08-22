package com.miku.ray

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.ANG_PACKAGE
import com.miku.ray.extension.ForegroundActivityTracker
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.CustomFontManager
import com.miku.ray.util.AppFontResolver
import com.miku.ray.util.MikuRayLogTree
import com.miku.ray.crashlog.CrashHandler
import timber.log.Timber

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
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        Timber.plant(MikuRayLogTree())
        MmkvManager.initialize(this)
        ForegroundActivityTracker.register(this)
        registerActivityLifecycleCallbacks(this)
        WorkManager.initialize(this, workManagerConfiguration)
        SettingsManager.initApp(this)
        SettingsManager.setNightMode()
    }

    /**
     * Applies all activity-level themes before AppCompat/material widgets inflate.
     * Applying a textAppearance after setContentView is too late for many widgets.
     */
    fun applyActivityTheme(activity: Activity) {
        ThemeManager.applyTheme(activity)

        val useCustomFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
        if (useCustomFont) {
            CustomFontManager.applyGlobalOverride(activity)
        } else {
            CustomFontManager.restoreGlobalOverride()
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

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyActivityTheme(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Fallback for pre-Q devices, where onActivityPreCreated is not dispatched.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            applyActivityTheme(activity)
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
        }
    }

    private fun getFontStyleResId(fontName: String?): Int {
        return when (fontName) {
        	"ios15"       -> R.style.StyleFontIos15
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
