package com.miku.ray.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class MainRepository(
    private val app: AngApplication,
) : MainDataSource {
    private val closed = AtomicBoolean(false)
    private val _mainServiceEvent = MutableSharedFlow<MainServiceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val mainServiceEvent: SharedFlow<MainServiceEvent> = _mainServiceEvent.asSharedFlow()

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val event = when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
                AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
                AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess
                AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure
                AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess
                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> safeIntent
                    .serializable<RealPingResult>("content")
                    ?.let(MainServiceEvent::MeasureDelayResult)
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> MainServiceEvent.MeasureConfigSuccess
                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> MainServiceEvent.MeasureConfigNotify(
                    safeIntent.getStringExtra("content").orEmpty(),
                )
                AppConfig.MSG_MEASURE_CONFIG_FINISH -> MainServiceEvent.MeasureConfigFinish(
                    safeIntent.getStringExtra("content"),
                )
                else -> null
            }
            event?.let { _mainServiceEvent.tryEmit(it) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
        sendMsg2Service(AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { sendMsg2Service(AppConfig.MSG_UNREGISTER_CLIENT, "") }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to unregister service client", it) }
        runCatching { app.unregisterReceiver(serviceReceiver) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to unregister main service receiver", it) }
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    override fun sendMsg2Service(msgId: Int, content: String) {
        MessageUtil.sendMsg2Service(app, msgId, content)
    }

    override fun sendMsg2TestService(msg: TestServiceMessage) {
        MessageUtil.sendMsg2TestService(app, msg)
    }

    override fun sendMsg2CountryCodeTestService(msg: CountryCodeTestMessage) {
        MessageUtil.sendMsg2CountryCodeTestService(app, msg)
    }

    override fun testCurrentServerRealPing() {
        sendMsg2Service(AppConfig.MSG_MEASURE_DELAY, "")
    }
}
