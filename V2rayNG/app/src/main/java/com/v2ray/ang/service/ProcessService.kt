package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProcessService {
    private var process: Process? = null

    fun runProcess(context: Context, cmd: MutableList<String>) {
        LogUtil.i(AppConfig.TAG, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(context.filesDir)
                .start()

            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(50L)
                LogUtil.i(AppConfig.TAG, "runProcess check")
                process?.waitFor()
                LogUtil.i(AppConfig.TAG, "runProcess exited")
            }
            LogUtil.i(AppConfig.TAG, process.toString())

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, e.toString(), e)
        }
    }

    fun stopProcess() {
        try {
            LogUtil.i(AppConfig.TAG, "runProcess destroy")
            process?.destroy()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to destroy process", e)
        }
    }
}
