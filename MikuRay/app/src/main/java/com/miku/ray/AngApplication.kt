package com.miku.ray

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.os.Process
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
import com.miku.ray.crashreporter.CrashReporter
import com.miku.ray.crashreporter.CrashReporterConfiguration
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
        CrashReporter.initialize(
            this,
            CrashReporterConfiguration()
                .setMaxNumberOfCrashToBeReport(5)
                .setCrashReportSubjectForEmail("MikuRay Crash Report")
                .setExtraInformation("MikuRay ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                .setAlertDialogTheme(R.style.AppThemeCrashLogger)
        )

        Timber.plant(MikuRayLogTree())
        MmkvManager.initialize(this)
        SettingsManager.initApp(this)

        // Probe and subscription-update services intentionally run in disposable
        // processes. UI lifecycle tracking and WorkManager belong to the main/
        // background process and can prevent those services from starting cleanly.
        if (!isDisposableProcess()) {
            ForegroundActivityTracker.register(this)
            registerActivityLifecycleCallbacks(this)
            WorkManager.initialize(this, workManagerConfiguration)
            SettingsManager.setNightMode()
        }
    }

    private fun isDisposableProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
                .orEmpty()
        }
        return processName.endsWith(":Probe") || processName.endsWith(":SubscriptionUpdate")
    }

    /**
     * Applies all activity-level themes before AppCompat/material widgets inflate.
     * Applying a textAppearance after setContentView is too late for many widgets.
     */
    fun applyActivityTheme(activity: Activity) {
        ThemeManager.applyTheme(activity)

        CustomFontManager.restoreGlobalOverride()

        val useCustomFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
        val typeface = if (useCustomFont) {
            CustomFontManager.getTypeface(activity)
        } else {
            val fontName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT)
            AppFontResolver.getTypeface(activity, fontName)
        }
        typeface?.let { CustomFontManager.applyGlobalOverride(it) }

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

    

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
