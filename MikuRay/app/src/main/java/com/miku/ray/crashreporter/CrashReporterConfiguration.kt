package com.miku.ray.crashreporter

import android.text.TextUtils
import com.miku.ray.crashreporter.utils.CrashUtil
import java.io.Serializable

class CrashReporterConfiguration : Serializable {
    var crashReportStoragePath: String = ""

    var maxNoOfCrashToBeReport = 5
    var extraInformation:String = ""
    var includeDeviceInformation  = true
    var emailSubject: String = ""

    var emailIds: Array<String> = arrayOf()


    /* Theme */
    var alertDialogThemeId : Int? = null

    var alertDialogTitle : String? = null
    var alertDialogMessage : String? = null
    var alertDialogPositiveButton : String? = null
    var alertDialogNegativeButton : String? = null


    fun setAlertDialogTheme(alertThemeId: Int): CrashReporterConfiguration {
        this.alertDialogThemeId = alertThemeId
        return this
    }


    fun setCrashReportStoragePath(path : String) : CrashReporterConfiguration{
        this.crashReportStoragePath  = path
        return this
    }

    fun setExtraInformation(information : String) : CrashReporterConfiguration{
        this.extraInformation  = information
        return this
    }

    fun setMaxNumberOfCrashToBeReport(count : Int) : CrashReporterConfiguration{
        this.maxNoOfCrashToBeReport  = if(count in 1..15) count else this.maxNoOfCrashToBeReport
        return this
    }

    fun setCrashReportSubjectForEmail(emailSubject : String) : CrashReporterConfiguration{
        this.emailSubject  =  emailSubject
        return this
    }

    fun setCrashReportSendEmailIds(emailIds : Array<String>) : CrashReporterConfiguration {
        this.emailIds = emailIds
        return this
    }

    fun setIncludeDeviceInformation(allow : Boolean) : CrashReporterConfiguration {
        this.includeDeviceInformation = allow
        return this
    }

    fun setAlertDialogTitle(title : String) : CrashReporterConfiguration {
        this.alertDialogTitle = title
        return this
    }

    fun setAlertDialogMessage(message : String) : CrashReporterConfiguration {
        this.alertDialogMessage = message
        return this
    }

    fun setAlertDialogPositiveButton(positiveButtonText : String) : CrashReporterConfiguration {
        this.alertDialogPositiveButton = positiveButtonText
        return this
    }

    fun setAlertDialogNegativeButton(negativeButtonText : String) : CrashReporterConfiguration {
        this.alertDialogNegativeButton = negativeButtonText
        return this
    }

}