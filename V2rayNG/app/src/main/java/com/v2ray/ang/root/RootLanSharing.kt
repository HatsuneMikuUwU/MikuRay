package com.v2ray.ang.root

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


object RootLanSharing {

    private var lanSharingStarted = false
    private var lanShareJob: Job? = null

    fun startClientSharing(context: Context): Boolean {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING) && RootManager.cachedRoot()) {
            if (lanShareJob != null) return false

            lanSharingStarted = true
            lanShareJob = CoroutineScope(Dispatchers.IO).launch { RootProxyManager.startClientSharing(context) }
        }

        return true
    }

    fun stopClientSharing(context: Context) {
        if (!lanSharingStarted) return

        lanSharingStarted = false
        runBlocking { lanShareJob?.cancelAndJoin() }
        lanShareJob = null
        RootProxyManager.stop(context)
    }
}
