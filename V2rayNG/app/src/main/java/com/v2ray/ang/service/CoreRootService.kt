package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.TrafficController
import com.v2ray.ang.root.RootProxyManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.SoftReference

/**
 * Foreground service for Root mode (system-wide proxy without VpnService).
 *
 * Unlike [CoreVpnService] this service does not extend [android.net.VpnService].
 * Traffic is captured via iptables mangle MARK chains + a TUN device managed by
 * [RootProxyManager], which also spawns hev-socks5-tunnel as a standalone root process.
 *
 * The iptables setup runs asynchronously in [setupJob] so it doesn't block
 * [onStartCommand]. [onDestroy] waits for [setupJob] to finish before tearing down
 * so teardown always runs after setup — preventing orphan routing rules.
 */
class CoreRootService : Service(), ServiceControl {

    private var isRunning = false
    private var setupJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service command received")
        NotificationManager.showNotification(null)
        TrafficController.start()

        // Start core first so the SOCKS inbound is ready before hev connects to it
        if (!CoreServiceManager.startCoreLoop(null)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Root: Failed to start core loop")
            stopAllService()
            return START_NOT_STICKY
        }

        isRunning = true

        // Sound
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
            SoundPlayer.playConnect(this)
        }

        // Async iptables + tun + hev setup
        setupJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                RootProxyManager.start(this@CoreRootService)
                LogUtil.i(AppConfig.TAG, "StartCore-Root: iptables/tun/hev setup complete")
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: setup failed", e)
                stopAllService()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service destroyed")
        if (isRunning) {
            stopAllService(isForced = false)
        }
    }

    override fun getService(): Service = this

    override fun startService() {
        // nothing; core loop is started in onStartCommand
    }

    override fun stopService() {
        stopAllService()
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    // ---- private -----------------------------------------------------------

    private fun stopAllService(isForced: Boolean = true) {
        isRunning = false

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
            SoundPlayer.playDisconnect(this)
        }

        // Wait for any in-flight setup to finish before tearing down.
        // If setup is still running when we try to tear down, teardown would finish
        // first and setup would then re-install the rules into a dead core,
        // leaving orphan iptables chains and a tun that black-holes all traffic.
        runBlocking {
            setupJob?.cancelAndJoin()
        }
        setupJob = null

        // Tear down iptables/tun/hev
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RootProxyManager.stopFull(this@CoreRootService)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: teardown error", e)
            }
        }

        CoreServiceManager.stopCoreLoop()

        if (isForced) {
            stopSelf()
        }
    }
}
