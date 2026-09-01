package com.miku.ray.crashreporter.utils

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import java.util.*
import timber.log.Timber


object AppUtils {

    @JvmStatic
    fun getDeviceDetails(context: Context): String {
        return """
        DATE & TIME : ${DateFormat.format("dd-MM-yyyy hh:mm:ss a", Date())}
        APP.VERSION : ${getAppVersion(context)}
        APP.VERSION_NAME : ${getAppVersionName(context)}
        TIMEZONE : ${timeZone()}
        PACKAGE NAME : ${context.packageName}
        VERSION.RELEASE : ${Build.VERSION.RELEASE}
        VERSION.SDK.NUMBER : ${Build.VERSION.SDK_INT}
        BRAND : ${Build.BRAND}
        MODEL : ${Build.MODEL}
        MANUFACTURER : ${Build.MANUFACTURER}
        DEVICE : ${Build.DEVICE}
        BOARD : ${Build.BOARD}
        PRODUCT : ${Build.PRODUCT}
        ID : ${Build.ID}
        HOST : ${Build.HOST}
        HARDWARE : ${Build.HARDWARE}
        BOOTLOADER : ${Build.BOOTLOADER}
        CPU_ABI : ${Build.CPU_ABI}
        CPU_ABI2 : ${Build.CPU_ABI2}
        DISPLAY : ${Build.DISPLAY}
        TIMESTAMP : ${Build.TIME}""".trimIndent()
    }

    private fun timeZone(): String {
        val tz = TimeZone.getDefault()
        return tz.id
    }

    private fun getAppVersion(context: Context): Long {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        var versionCode: Long = 0
        try {
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to read app version code")
        }
        return versionCode
    }

    private fun getAppVersionName(context: Context): String {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.versionName.orEmpty()
    }

    fun getApplicationName(context: Context): String {
        val applicationInfo = context.applicationInfo
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else context.getString(
            stringId
        )
    }
}