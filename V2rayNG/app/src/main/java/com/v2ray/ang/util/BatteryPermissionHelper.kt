package com.v2ray.ang.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import java.util.Locale

/**
 * Detects OEM-specific battery/autostart managers (Xiaomi, Oppo, Samsung, Huawei, etc.) and
 * navigates the user to the relevant whitelist screen so MikuRay isn't killed in the background.
 *
 * Many OEM ROMs ship their own autostart/battery-manager whitelist that is completely separate
 * from the standard Doze API (PowerManager.isIgnoringBatteryOptimizations) — the system
 * "Disable battery optimization" dialog doesn't cover it at all, which is why that dialog alone
 * isn't enough on those devices even after the user thinks they've disabled it.
 *
 * Vendored (single-file, adapted to MikuRay's package conventions) from
 * com.waseemsabir:betterypermissionhelper (MIT license), rather than pulled in as a dependency.
 */
object BatteryPermissionHelper {

    /* HTC */
    private const val BRAND_HTC = "htc"
    private const val PACKAGE_HTC_MAIN = "com.htc.pitroad"
    private const val PACKAGE_HTC_COMPONENT = "com.htc.pitroad.landingpage.activity.LandingPageActivity"

    /* HUAWEI */
    private const val BRAND_HUAWEI = "huawei"
    private const val PACKAGE_HUAWEI_MAIN = "com.huawei.systemmanager"
    private const val PACKAGE_HUAWEI_ACTION = "huawei.intent.action.HSM_PROTECTED_APPS"

    /* LETV */
    private const val BRAND_LETV = "letv"
    private const val PACKAGE_LETV_MAIN = "com.letv.android.letvsafe"
    private const val PACKAGE_LETV_COMPONENT = "com.letv.android.letvsafe.BackgroundAppManageActivity"

    /* MEIZU */
    private const val BRAND_MEIZU = "meizu"
    private const val PACKAGE_MEIZU_MAIN = "com.meizu.safe"
    private const val PACKAGE_MEIZU_COMPONENT = "com.meizu.safe.powerui.PowerAppPermissionActivity"
    private const val PACKAGE_MEIZU_ACTION = "com.meizu.power.PowerAppKilledNotification"

    /* Oppo */
    private const val BRAND_OPPO = "oppo"
    private const val PACKAGE_OPPO_MAIN = "com.coloros.oppoguardelf"
    private const val PACKAGE_OPPO_COMPONENT = "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
    private const val PACKAGE_OPPO_COMPONENT_FALLBACK = "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"

    /* Samsung */
    private const val BRAND_SAMSUNG = "samsung"
    private const val PACKAGE_SAMSUNG_MAIN = "com.samsung.android.lool"
    private const val PACKAGE_SAMSUNG_FALLBACK = "com.samsung.android.sm_cn"
    private const val PACKAGE_SAMSUNG_COMPONENT = "com.samsung.android.sm.ui.battery.BatteryActivity"
    private const val PACKAGE_SAMSUNG_ACTION = "com.samsung.android.sm.ACTION_BATTERY"

    /* Xiaomi */
    private const val BRAND_XIAOMI = "xiaomi"
    private const val BRAND_XIAOMI_POCO = "poco"
    private const val BRAND_XIAOMI_REDMI = "redmi"
    private const val PACKAGE_XIAOMI_MAIN = "com.miui.powerkeeper"
    private const val PACKAGE_XIAOMI_COMPONENT = "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"

    /* ZTE */
    private const val BRAND_ZTE = "zte"
    private const val PACKAGE_ZTE_MAIN = "com.zte.heartyservice"
    private const val PACKAGE_ZTE_COMPONENT = "com.zte.heartyservice.setting.ClearAppSettingsActivity"

    private val PACKAGES_TO_CHECK_FOR_PERMISSION = listOf(
        PACKAGE_HTC_MAIN,
        PACKAGE_HUAWEI_MAIN,
        PACKAGE_LETV_MAIN,
        PACKAGE_MEIZU_MAIN,
        PACKAGE_OPPO_MAIN,
        PACKAGE_SAMSUNG_MAIN,
        PACKAGE_XIAOMI_MAIN,
        PACKAGE_ZTE_MAIN
    )

    /**
     * Attempts to open the manufacturer-specific autostart/battery-manager settings screen for
     * this device. If [open] is false, it only checks whether such a screen is supported/found.
     *
     * @return true if the screen was opened (or found, when [open] is false), false otherwise.
     */
    fun getPermission(context: Context, open: Boolean = true, newTask: Boolean = false): Boolean {
        return try {
            when (Build.BRAND.lowercase(Locale.ROOT)) {
                BRAND_HTC -> startForHtc(context, open, newTask)
                BRAND_HUAWEI -> startForHuawei(context, open, newTask)
                BRAND_MEIZU -> startForMeizu(context, open, newTask)
                BRAND_OPPO -> startForOppo(context, open, newTask)
                BRAND_SAMSUNG -> startForSamsung(context, open, newTask)
                BRAND_XIAOMI, BRAND_XIAOMI_POCO, BRAND_XIAOMI_REDMI -> startForXiaomi(context, open, newTask)
                BRAND_ZTE -> startForZte(context, open, newTask)
                BRAND_LETV -> startForLetv(context, open, newTask)
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether a known OEM autostart/battery-manager whitelist screen exists on this
     * device.
     *
     * @param onlyIfSupported if true, only returns true when the screen is actually supported by
     *   this helper (not just that the OEM package is installed).
     */
    fun isBatterySaverPermissionAvailable(context: Context, onlyIfSupported: Boolean = false): Boolean {
        val packages: List<ApplicationInfo> = context.packageManager.getInstalledApplications(0)
        for (packageInfo in packages) {
            if (PACKAGES_TO_CHECK_FOR_PERMISSION.contains(packageInfo.packageName) &&
                (!onlyIfSupported || getPermission(context, open = false))
            ) {
                return true
            }
        }
        return false
    }

    private fun startForHtc(context: Context, open: Boolean, newTask: Boolean): Boolean =
        start(context, listOf(PACKAGE_HTC_MAIN), listOf(getIntent(PACKAGE_HTC_MAIN, PACKAGE_HTC_COMPONENT, newTask)), open)

    private fun startForHuawei(context: Context, open: Boolean, newTask: Boolean): Boolean =
        startFromAction(context, listOf(getIntentFromAction(PACKAGE_HUAWEI_ACTION, newTask)), open)

    private fun startForLetv(context: Context, open: Boolean, newTask: Boolean): Boolean =
        start(context, listOf(PACKAGE_LETV_MAIN), listOf(getIntent(PACKAGE_LETV_MAIN, PACKAGE_LETV_COMPONENT, newTask)), open)

    private fun startForMeizu(context: Context, open: Boolean, newTask: Boolean): Boolean =
        start(context, listOf(PACKAGE_MEIZU_MAIN), listOf(getIntent(PACKAGE_MEIZU_MAIN, PACKAGE_MEIZU_COMPONENT, newTask)), open) ||
            startFromAction(context, listOf(getIntentFromAction(PACKAGE_MEIZU_ACTION, newTask)), open)

    private fun startForOppo(context: Context, open: Boolean, newTask: Boolean): Boolean =
        start(
            context,
            listOf(PACKAGE_OPPO_MAIN),
            listOf(
                getIntent(PACKAGE_OPPO_MAIN, PACKAGE_OPPO_COMPONENT, newTask),
                getIntent(PACKAGE_OPPO_MAIN, PACKAGE_OPPO_COMPONENT_FALLBACK, newTask)
            ),
            open
        )

    private fun startForSamsung(context: Context, open: Boolean, newTask: Boolean): Boolean =
        startFromAction(context, listOf(getIntentFromAction(PACKAGE_SAMSUNG_ACTION, newTask)), open) ||
            start(
                context,
                listOf(PACKAGE_SAMSUNG_MAIN, PACKAGE_SAMSUNG_FALLBACK),
                listOf(getIntent(PACKAGE_SAMSUNG_MAIN, PACKAGE_SAMSUNG_COMPONENT, newTask)),
                open
            )

    private fun startForXiaomi(context: Context, open: Boolean, newTask: Boolean): Boolean =
        start(
            context,
            listOf(PACKAGE_XIAOMI_MAIN),
            listOf(
                getIntentWithExtras(
                    PACKAGE_XIAOMI_MAIN,
                    PACKAGE_XIAOMI_COMPONENT,
                    mapOf(
                        "package_name" to context.packageName,
                        "package_label" to context.applicationLabel()
                    ),
                    newTask
                )
            ),
            open
        )

    private fun startForZte(context: Context, open: Boolean, newTask: Boolean): Boolean =
        start(context, listOf(PACKAGE_ZTE_MAIN), listOf(getIntent(PACKAGE_ZTE_MAIN, PACKAGE_ZTE_COMPONENT, newTask)), open)

    private fun Context.applicationLabel(): String {
        val info = applicationInfo
        val stringId = info.labelRes
        return if (stringId == 0) info.nonLocalizedLabel?.toString().orEmpty() else getString(stringId)
    }

    private fun isPackageExists(context: Context, targetPackage: String): Boolean {
        val packages: List<ApplicationInfo> = context.packageManager.getInstalledApplications(0)
        return packages.any { it.packageName == targetPackage }
    }

    private fun getIntent(packageName: String, componentName: String, newTask: Boolean): Intent =
        Intent().apply {
            component = ComponentName(packageName, componentName)
            if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun getIntentWithExtras(
        packageName: String,
        componentName: String,
        extras: Map<String, String>,
        newTask: Boolean
    ): Intent = Intent().apply {
        component = ComponentName(packageName, componentName)
        if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        extras.forEach { (key, value) -> putExtra(key, value) }
    }

    private fun getIntentFromAction(intentAction: String, newTask: Boolean): Intent =
        Intent().apply {
            action = intentAction
            if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun getIntentActivities(context: Context, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    private fun isActivityFound(context: Context, intent: Intent): Boolean =
        getIntentActivities(context, intent).isNotEmpty()

    private fun areActivitiesFound(context: Context, intents: List<Intent>): Boolean =
        intents.any { isActivityFound(context, it) }

    private fun startScreen(context: Context, intents: List<Intent>): Boolean {
        intents.forEach {
            if (isActivityFound(context, it)) {
                context.startActivity(it)
                return true
            }
        }
        return false
    }

    private fun start(context: Context, packages: List<String>, intents: List<Intent>, open: Boolean): Boolean {
        if (packages.none { isPackageExists(context, it) }) return false
        return if (open) startScreen(context, intents) else areActivitiesFound(context, intents)
    }

    private fun startFromAction(context: Context, intentActions: List<Intent>, open: Boolean): Boolean {
        return if (open) startScreen(context, intentActions) else areActivitiesFound(context, intentActions)
    }
}
