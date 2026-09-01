package com.miku.ray.crashreporter.utils

import com.miku.ray.crashreporter.CrashReporter
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class CrashUtil private constructor() {

    companion object {
        private val crashLogTime: String
            get() {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault())
                return dateFormat.format(Date())
            }

        fun saveCrashReport(throwable: Throwable){
            val filename = crashLogTime + Constants.CRASH_SUFFIX + Constants.FILE_EXTENSION
            writeToFile(CrashReporter.crashLogFilesPath, filename, getStackTrace(throwable))
        }

        fun logException(exception: Exception) {
            Thread(Runnable {
                val filename = crashLogTime + Constants.EXCEPTION_SUFFIX + Constants.FILE_EXTENSION
                writeToFile(
                    CrashReporter.crashLogFilesPath,
                    filename,
                    getStackTrace(exception)
                )
            }).start()
        }

        private fun writeToFile(crashReportPath: String, filename: String, crashLog: String) {
            val bufferedWriter: BufferedWriter
            try {
                val fullFileName = "$crashReportPath/$filename"
                if(!File(fullFileName).exists())
                    File(fullFileName).createNewFile()

                bufferedWriter = BufferedWriter(FileWriter(fullFileName))
                bufferedWriter.write(crashLog)
                bufferedWriter.flush()
                bufferedWriter.close()
                Timber.d("Crash report saved in: %s", fullFileName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save crash report: %s", filename)
            }
        }

        private fun getStackTrace(e: Throwable): String {
            val result: Writer = StringWriter()
            val printWriter = PrintWriter(result)
            e.printStackTrace(printWriter)
            val crashLog = result.toString()
            printWriter.close()
            return crashLog
        }

        private val exceptionFileList: Array<File>?
            get() {
                val dir = File(CrashReporter.crashLogFilesPath).listFiles()
                dir?.filter { it.name.endsWith(Constants.FILE_EXTENSION)}
                dir?.sortByDescending { it.lastModified() }
                return dir
            }

        val isHaveCrashData: Boolean get() = crashLogsCount > 0

        val crashLogsCount : Int get() = if(exceptionFileList != null) exceptionFileList?.size!! else 0

        fun clearAllCrashLogs() {
            Thread(Runnable {
                File(CrashReporter.crashLogFilesPath).deleteRecursively()
            }).start()
        }

        val crashMsg: String
            get() {
                val sb = StringBuilder()
                if (isHaveCrashData) {
                    var crashCount = 1
                    for (crashFile in exceptionFileList!!) {
                        if(crashCount <= CrashReporter.config.maxNoOfCrashToBeReport) {
                            if (crashCount > 1) sb.append("\n\n")
                            sb.append("==================================\n")
                            sb.append("Crash Report : ").append(crashCount++).append("\n")
                            sb.append("==================================\n")
                            sb.append("Crashed Date & Time : ").append(
                                crashFile.name.replace(Constants.EXCEPTION_SUFFIX, "")
                                    .replace(Constants.CRASH_SUFFIX, "")
                                    .replace(Constants.FILE_EXTENSION, "")
                            ).append("\n\n")
                            val input = BufferedReader(FileReader(crashFile))
                            var line: String?
                            while (input.readLine().also { line = it } != null) {
                                sb.append(line).append("\n")
                            }
                            input.close()
                        }
                    }
                }
                return sb.toString()
            }
    }
}