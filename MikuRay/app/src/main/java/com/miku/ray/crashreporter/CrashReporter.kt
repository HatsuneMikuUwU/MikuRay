package com.miku.ray.crashreporter

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import com.miku.ray.crashreporter.interfaces.CrashAlertClickListener
import com.miku.ray.crashreporter.utils.AppUtils
import com.miku.ray.crashreporter.utils.Constants
import com.miku.ray.crashreporter.utils.CrashReporterExceptionHandler
import com.miku.ray.crashreporter.utils.CrashUtil
import java.io.File


object CrashReporter {
    lateinit var context: Context
    var config: CrashReporterConfiguration = CrashReporterConfiguration()

    fun initialize(context: Context, crashReporterConfiguration: CrashReporterConfiguration) {
        this.context = context
        config = crashReporterConfiguration
        setUpExceptionHandler()
    }


    private fun setUpExceptionHandler() {
        Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashReporterExceptionHandler())
    }

    //LOG Exception APIs
    fun logException(exception: Exception) {
        CrashUtil.logException(exception)
    }

    val crashLogFilesPath: String
        get() {
            return if (TextUtils.isEmpty(config.crashReportStoragePath)) {
                val defaultPath =
                    (context.getExternalFilesDir(null)!!.absolutePath + File.separator + Constants.CRASH_REPORT_DIR)
                val file = File(defaultPath)
                file.mkdirs()
                defaultPath
            } else
                config.crashReportStoragePath

        }


    private fun getBodyContent(): String {
        val sb = StringBuilder()

        sb.append("APP NAME : ").append(AppUtils.getApplicationName(context)).append("\n\n")
        if (config.includeDeviceInformation) {
            sb.append("======= DEVICE INFORMATION =======").append("\n")
            sb.append(AppUtils.getDeviceDetails(context)).append("\n\n")
        }

        if (!TextUtils.isEmpty(config.extraInformation)) {
            sb.append("======= EXTRA INFORMATION =======").append("\n")
            sb.append(config.extraInformation).append("\n\n")
        }

        sb.append("\n======= CRASH INFORMATION =======").append("\n\n")
        sb.append(CrashUtil.crashMsg).append("\n")
        sb.append("=============== END ===============").append("\n\n")

        return sb.toString()

    }

    fun getAllCrashInfo(): String {
        return if (CrashUtil.isHaveCrashData) getBodyContent() else ""
    }

    fun shareCrash(activity: Activity) {
        val subject = if (!TextUtils.isEmpty(config.emailSubject)) config.emailSubject else "${AppUtils.getApplicationName(activity)} App - Collected Crashes"
        val body = getBodyContent()

        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO)
            emailIntent.type = "message/rfc822"
            emailIntent.data = Uri.parse("mailto:")
            emailIntent.putExtra(Intent.EXTRA_EMAIL, config.emailIds)
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
            emailIntent.putExtra(Intent.EXTRA_TEXT, body)
            emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(emailIntent)
        } catch (e: Exception) {
            // No email client available (or another failure) - fall back to a generic
            // share sheet instead of letting an unhandled exception crash the app.
            e.printStackTrace()
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(Intent.createChooser(shareIntent, subject).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun showAlertDialogForShareCrash(activity: Activity, clearCrashLogsAfterShare : Boolean) {
        showAlertDialogForShareCrash(activity, null, clearCrashLogsAfterShare)
    }

    fun showAlertDialogForShareCrash(activity: Activity, listener: CrashAlertClickListener?, clearCrashLogsAfterShare: Boolean) {
        try {
            val builder = if(config.alertDialogThemeId != null) AlertDialog.Builder(activity, config.alertDialogThemeId!!) else AlertDialog.Builder(activity)
            //set title for alert dialog
            builder.setTitle(if(config.alertDialogTitle!=null) config.alertDialogTitle else "Do you want to share crash information?")
            //set message for alert dialog
            val message = "A previous crash was collected. Would you like to send the crash logs to developer to fix this issue in the future?"
            builder.setMessage(if(config.alertDialogMessage != null) config.alertDialogMessage else message)

            //performing positive action
            builder.setPositiveButton(if(!TextUtils.isEmpty(config.alertDialogPositiveButton)) config.alertDialogPositiveButton else "Yes"){ _, _ ->
                shareCrash(activity)
                try {
                    if(clearCrashLogsAfterShare)
                        CrashUtil.clearAllCrashLogs()
                }catch (e : Exception){
                    e.printStackTrace()
                }
                listener?.onOkClick()
            }
            //performing negative action
            builder.setNegativeButton(if(!TextUtils.isEmpty(config.alertDialogNegativeButton)) config.alertDialogNegativeButton else "Cancel"){ dialogInterface, _ ->
                dialogInterface.dismiss()
                listener?.onCancelClick()
            }
            // Create the AlertDialog
            val alertDialog: AlertDialog = builder.create()
            // Set other dialog properties
            alertDialog.setCancelable(false)
            alertDialog.show()
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

}