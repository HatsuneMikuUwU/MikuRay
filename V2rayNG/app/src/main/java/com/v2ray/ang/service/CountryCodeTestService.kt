package com.v2ray.ang.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.miku.ray.remixicon.R as RemixR
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.CountryCodeTestMessage
import com.v2ray.ang.dto.TestProgressInfo
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import libv2ray.CoreCallbackHandler
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class CountryCodeTestService : Service() {

    private val cancelled = AtomicBoolean(false)
    private var worker: Thread? = null

    private val cancelAction by lazy {
        val intent = Intent(this, CountryCodeTestService::class.java).putExtra(
            "content",
            CountryCodeTestMessage(AppConfig.MSG_COUNTRY_CODE_CANCEL)
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Action.Builder(
            RemixR.drawable.rmx_media_stop_line,
            getString(android.R.string.cancel),
            pendingIntent
        ).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelled.set(true)
        worker?.interrupt()
        worker = null
        NotificationHelper.stopForeground(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<CountryCodeTestMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (message.key) {
            AppConfig.MSG_COUNTRY_CODE_START -> handleStart(message, startId)
            AppConfig.MSG_COUNTRY_CODE_CANCEL -> handleCancel()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(message: CountryCodeTestMessage, startId: Int) {
        if (worker?.isAlive == true) return

        val guids = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }
        if (guids.isEmpty()) {
            sendFinish()
            stopSelf(startId)
            return
        }

        cancelled.set(false)
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.title_country_code_all_server),
            cancelAction
        )

        worker = thread(name = "CountryCodeTest", start = true) {
            try {
                SettingsManager.initAssets(this, assets)
                CoreNativeManager.initCoreEnv(this)
                guids.forEachIndexed { index, guid ->
                    if (cancelled.get() || Thread.currentThread().isInterrupted) return@thread

                    val countryCode = lookupThroughProfile(guid)
                    MmkvManager.encodeServerCountryCode(guid, countryCode)
                    MessageUtil.sendMsg2UI(this, AppConfig.MSG_COUNTRY_CODE_SUCCESS, guid)
                    MessageUtil.sendMsg2UI(
                        this,
                        AppConfig.MSG_COUNTRY_CODE_NOTIFY,
                        TestProgressInfo(guid, 0L, index + 1, guids.size)
                    )
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "CountryCodeTestService failed", e)
            } finally {
                sendFinish()
                stopSelf(startId)
            }
        }
    }

    private fun lookupThroughProfile(guid: String): String? {
        val result = CoreConfigManager.getV2rayConfig4Speedtest(this, guid)
        if (!result.status || result.content.isBlank()) return null

        val config = JsonUtil.parseString(result.content) ?: return null
        val inbounds = JsonArray()
        config.add("inbounds", inbounds)

        val httpPort = Utils.findRandomFreePort()
        var socksPort = Utils.findRandomFreePort()
        while (socksPort == httpPort) {
            socksPort = Utils.findRandomFreePort()
        }
        inbounds.add(createSocksInbound(socksPort))
        inbounds.add(createHttpInbound(httpPort))

        val controller = CoreNativeManager.newCoreController(CountryCallback())
        return try {
            controller.startLoop(JsonUtil.toJson(config), 0)
            if (!waitForProxy(httpPort)) return null

            val timeoutMs = SettingsManager.getCountryCodeTestTimeout()
            val countryCode = SpeedtestManager.getCountryCodeThroughProxy(httpPort, timeoutMs)
            if (countryCode != null || cancelled.get()) {
                countryCode
            } else {
                Thread.sleep(120)
                val retryTimeoutMs = (timeoutMs - 1000).coerceAtLeast(1000)
                SpeedtestManager.getCountryCodeThroughProxy(httpPort, timeoutMs = retryTimeoutMs)
            }
        } finally {
            runCatching { controller.stopLoop() }
        }
    }

    private fun waitForProxy(port: Int, timeoutMs: Int = 1800): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cancelled.get() && System.currentTimeMillis() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 120)
                }
                return true
            } catch (_: Exception) {
                Thread.sleep(60)
            }
        }
        return false
    }

    private fun createSocksInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "country-socks")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "socks")
            add("settings", JsonObject().apply {
                addProperty("auth", "noauth")
                addProperty("udp", true)
            })
        }
    }

    private fun createHttpInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "country-http")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "http")
            add("settings", JsonObject())
        }
    }

    private fun handleCancel() {
        cancelled.set(true)
        worker?.interrupt()
        worker = null
        sendFinish()
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    private fun sendFinish() {
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_COUNTRY_CODE_FINISH, "0")
    }

    private class CountryCallback : CoreCallbackHandler {
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0L
        }
    }
}

