package com.miku.ray.ui.main

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.GroupMapItem
import com.miku.ray.dto.entities.ServersCache
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.ui.bottomsheet.SortSubBottomSheet
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.matchesPattern
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID
import java.util.regex.PatternSyntaxException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = mutableListOf<String>()
    var subscriptionId: String = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
    var keywordFilter = ""
    private var activeTestId: String? = null
    private var activeTestCompleted = 0
    private var activeTestTotal = 0
    private var isRestarting = false
    private var pendingServerRestartGuid: String? = null
    val serversCache = mutableListOf<ServersCache>()

    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    val testProgressAction by lazy { MutableLiveData<TestProgressInfo?>() }
    val countryCodeProgressAction by lazy { MutableLiveData<TestProgressInfo?>() }
    val updateIpResultAction by lazy { MutableLiveData<String>() }
    val updateTrafficSpeedAction by lazy { MutableLiveData<String>() }
    val serviceRestartAction by lazy { MutableLiveData<Unit>() }
    val alertAction by lazy { MutableLiveData<Pair<Boolean, String>>() }
    val updateGroupBadgeAction by lazy { MutableLiveData<Unit>() }
    val updateGroupOrderAction by lazy { MutableLiveData<Unit>() }

    fun startListenBroadcast() {
        isRunning.value = isServiceActuallyRunning()
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    private fun isServiceActuallyRunning(): Boolean {
        val app = getApplication<Application>()
        return try {
            when {
                SettingsManager.isRootMode() ->
                    isRunningServiceClass(app, com.miku.ray.service.CoreRootService::class.java)

                SettingsManager.isVpnMode() -> {
                    val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        ?: return false
                    val network = cm.activeNetwork ?: return false
                    val capabilities = cm.getNetworkCapabilities(network) ?: return false
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }

                else -> isRunningServiceClass(app, com.miku.ray.service.CoreProxyOnlyService::class.java)
            }
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun isRunningServiceClass(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == serviceClass.name
        }
    }

    fun resyncState() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        try {
            getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    @Synchronized
    fun reloadServerList() {
        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        val order = MmkvManager.decodeSettingsInt("${AppConfig.PREF_SERVER_ORDER}_$subId", 0)
        if (order == 0) {
            if (subscriptionId.isEmpty()) {
                MmkvManager.decodeSubsList().forEach { MmkvManager.restoreOriginServerList(it) }
            } else {
                MmkvManager.restoreOriginServerList(subscriptionId)
            }
        }

        serverList = if (subscriptionId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(subscriptionId)
        }

        updateCache()
        updateListAction.postValue(-1)
    }

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) {
            serversCache.removeAt(index)
        }
        updateGroupBadgeAction.postValue(Unit)
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            return
        }

        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)

        MmkvManager.encodeServerList(serverList, subscriptionId)
    }

    @Synchronized
    fun updateCache() {
        serversCache.clear()
        val kw = keywordFilter.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (e: PatternSyntaxException) {
            null
        }
        for (guid in serverList) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue
            if (kw.isEmpty()) {
                serversCache.add(ServersCache(guid, profile))
                continue
            }

            val remarks = profile.remarks
            val description = profile.description.orEmpty()
            val server = profile.server.orEmpty()
            val protocol = profile.configType.name
            if (remarks.matchesPattern(searchRegex, kw)
                || description.matchesPattern(searchRegex, kw)
                || server.matchesPattern(searchRegex, kw)
                || protocol.matchesPattern(searchRegex, kw)
            ) {
                serversCache.add(ServersCache(guid, profile))
            }
        }

        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        val order = MmkvManager.decodeSettingsInt("${AppConfig.PREF_SERVER_ORDER}_$subId", 0)
        when (order) {
            1 -> serversCache.sortWith(compareBy { it.profile.remarks.lowercase() })
            2 -> serversCache.sortWith(compareBy {
                val delay = MmkvManager.decodeServerAffiliationInfo(it.guid)?.testDelayMillis ?: 0L
                if (delay <= 0L) Long.MAX_VALUE else delay
            })
        }

        // Pinned servers float to the top regardless of the active order above.
        // sortByDescending is stable, so it only reshuffles the pinned/unpinned
        // partitions without disturbing the relative order within each.
        val pinnedServers = MmkvManager.decodePinnedServers()
        if (pinnedServers.isNotEmpty()) {
            serversCache.sortByDescending { pinnedServers.contains(it.guid) }
        }
    }

    /**
     * Toggles the pinned state of [guid], re-sorts the cache so pinned
     * servers float to the top immediately, and returns the new state.
     */
    fun togglePinServer(guid: String): Boolean {
        val nowPinned = MmkvManager.togglePinnedServer(guid)
        updateCache()
        updateListAction.postValue(-1)
        return nowPinned
    }

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        if (subscriptionId.isEmpty()) {
            return AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return SubscriptionUpdateResult()
            return AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    fun exportAllServer(): Int {
        val serverListCopy =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                serverList
            } else {
                serversCache.map { it.guid }.toList()
            }

        val ret = AngConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            serverListCopy
        )
        return ret
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        val testId = UUID.randomUUID().toString()
        // CoreTestService replaces an active batch itself and redelivers the
        // newest start intent from the fresh disposable probe process. Sending
        // a separate cancel here races that replacement and can drop the URL test.
        activeTestId = testId
        activeTestCompleted = 0
        activeTestTotal = serversCache.size
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1

        viewModelScope.launch(Dispatchers.Default) {
            if (serversCache.isEmpty()) {
                activeTestId = null
                return@launch
            }
            MessageUtil.sendMsg2TestService(
                getApplication(),
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    testId = testId,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) serversCache.map { it.guid } else emptyList(),
                    onlyTcp = onlyTcp
                )
            )
        }
    }

    fun testAllCountryCodes() {
        MessageUtil.sendMsg2CountryCodeTestService(
            getApplication(),
            CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL)
        )
        val guids = serversCache.map { it.guid }.toList()
        MmkvManager.clearAllCountryCodes(guids)
        updateListAction.value = -1

        viewModelScope.launch(Dispatchers.Default) {
            if (guids.isEmpty()) return@launch
            MessageUtil.sendMsg2CountryCodeTestService(
                getApplication(),
                CountryCodeTestMessage(
                    key = AppConfig.MSG_COUNTRY_CODE_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) guids else emptyList()
                )
            )
        }
    }

    fun cancelCountryCodeTest() {
        MessageUtil.sendMsg2CountryCodeTestService(
            getApplication(),
            CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL)
        )
    }

    fun clearCountryCodes() {
        cancelCountryCodeTest()
        MmkvManager.clearAllCountryCodes(MmkvManager.decodeAllServerList())
        updateListAction.postValue(-1)
    }

    fun clearCountryCodesForGroup() {
        cancelCountryCodeTest()
        MmkvManager.clearAllCountryCodes(MmkvManager.decodeServerList(subscriptionId))
        updateListAction.postValue(-1)
    }

    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    fun fetchCurrentIp() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_IP, "")
    }

    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        reloadServerList()
    }

    fun subscriptionIdChangedAsync(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        viewModelScope.launch(Dispatchers.IO) {
            reloadServerList()
        }
    }

    fun getSubscriptions(context: Context): List<GroupMapItem> {
        val subscriptions = SortSubBottomSheet.sorted(
            MmkvManager.decodeSubscriptions(),
            addedTime = { it.subscription.addedTime },
            lastUpdated = { it.subscription.lastUpdated }
        )
        if (subscriptionId.isNotEmpty()
            && !subscriptions.map { it.guid }.contains(subscriptionId)
        ) {
            subscriptionIdChanged("")
        }

        val groups = mutableListOf<GroupMapItem>()
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)) {
            groups.add(
                GroupMapItem(
                    id = "",
                    remarks = context.getString(R.string.filter_config_all),
                    serverCount = MmkvManager.decodeAllServerList().size,
                    icon = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON),
                )
            )
        }
        subscriptions.forEach { sub ->
            groups.add(
                GroupMapItem(
                    id = sub.guid,
                    remarks = sub.subscription.remarks,
                    serverCount = MmkvManager.decodeServerList(sub.guid).size,
                    icon = sub.subscription.tabIcon,
                )
            )
        }
        return groups
    }

    fun getPosition(guid: String): Int {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        return -1
    }

    fun removeDuplicateServer(): Int {
        val serversCacheCopy = serversCache.toList().toMutableList()
        val deleteServer = mutableListOf<String>()
        val pinnedServers = MmkvManager.decodePinnedServers()

        serversCacheCopy.forEachIndexed { index, sc ->
            val profile = sc.profile
            if (profile.configType.isComplexType()) {
                return@forEachIndexed
            }

            serversCacheCopy.forEachIndexed { index2, sc2 ->
                if (index2 > index) {
                    val profile2 = sc2.profile
                    if (profile2.configType.isComplexType()) {
                        return@forEachIndexed
                    }

                    // Pinned servers are never treated as the removable duplicate, so
                    // pinning a server is enough to keep it even if an identical
                    // config exists elsewhere in the list.
                    if (profile == profile2 && !deleteServer.contains(sc2.guid) && !pinnedServers.contains(sc2.guid)) {
                        deleteServer.add(sc2.guid)
                    }
                }
            }
        }
        for (it in deleteServer) {
            MmkvManager.removeServer(it)
        }

        return deleteServer.count()
    }

    fun removeAllServer(): Int {
        val count =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                MmkvManager.removeAllServer()
            } else {
                val pinnedServers = MmkvManager.decodePinnedServers()
                val serversCopy = serversCache.toList().filterNot { pinnedServers.contains(it.guid) }
                for (item in serversCopy) {
                    MmkvManager.removeServer(item.guid)
                }
                serversCopy.count()
            }
        return count
    }

    fun removeInvalidServer(): Int {
        var count = 0
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            count += MmkvManager.removeInvalidServer("")
        } else {
            val serversCopy = serversCache.toList()
            for (item in serversCopy) {
                // MmkvManager.removeInvalidServer already skips pinned servers.
                count += MmkvManager.removeInvalidServer(item.guid)
            }
        }
        return count
    }

    fun sortByTestResults() {
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubsList().forEach { guid ->
                sortByTestResultsForSub(guid)
            }
        } else {
            sortByTestResultsForSub(subscriptionId)
        }
    }

    private fun sortByTestResultsForSub(subId: String) {
        data class ServerDelay(var guid: String, var testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverListToSort = MmkvManager.decodeServerList(subId)

        serverListToSort.forEach { key ->
            val delay = MmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        val sortedServerList = serverDelays.map { it.guid }.toMutableList()

        MmkvManager.encodeServerList(sortedServerList, subId)
    }


    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(getApplication<AngApplication>(), assets)
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) {
            return
        }
        keywordFilter = keyword
        reloadServerList()
    }

    /** Persists a new selection while waiting for the active daemon to accept restart. */
    fun beginServerRestart(guid: String): Boolean {
        if (guid == MmkvManager.getSelectServer() || pendingServerRestartGuid != null) return false

        MmkvManager.setSelectServer(guid)
        pendingServerRestartGuid = guid
        isRestarting = true
        return true
    }

    /** Clears the optimistic restart state when no active daemon accepted the request. */
    fun onServerRestartRequestResult(guid: String, handled: Boolean) {
        if (handled || pendingServerRestartGuid != guid) return

        pendingServerRestartGuid = null
        isRestarting = false
        markConnectionStopped()
        isRunning.value = false
    }

    private fun markConnectionStopped() {
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)
    }

    fun findSubscriptionIdBySelect(): String? {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            return null
        }

        val config = MmkvManager.decodeServerConfig(selectedGuid)
        return config?.subscriptionId
    }

    fun onTestsFinished(cancelled: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            if (cancelled) return@launch
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST)) {
                removeInvalidServer()
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST)) {
                if (subscriptionId.isEmpty()) {
                    MmkvManager.decodeSubsList().forEach { subId ->
                        MmkvManager.saveOriginServerList(subId)
                        MmkvManager.encodeSettings("${AppConfig.PREF_SERVER_ORDER}_$subId", 2)
                    }
                    MmkvManager.encodeSettings("${AppConfig.PREF_SERVER_ORDER}_${AppConfig.DEFAULT_SUBSCRIPTION_ID}", 2)
                } else {
                    MmkvManager.saveOriginServerList(subscriptionId)
                    val subIdToSave = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
                    MmkvManager.encodeSettings("${AppConfig.PREF_SERVER_ORDER}_$subIdToSave", 2)
                }
                sortByTestResults()
            }

            withContext(Dispatchers.Main) {
                reloadServerList()
            }
        }
    }

    fun resetCurrentProfileTraffic() {
        MmkvManager.getSelectServer()?.let { guid ->
            MmkvManager.resetProfileTraffic(guid)
            updateListAction.postValue(getPosition(guid))
        }
    }

    fun resetGroupTraffic() {
        MmkvManager.resetGroupTraffic(subscriptionId)
        updateListAction.postValue(-1)
    }

    fun resetAllTraffic() {
        MmkvManager.resetAllTraffic()
        updateListAction.postValue(-1)
    }

    fun cancelRealPingTest() {
        val testId = activeTestId.orEmpty()
        activeTestId = null
        testProgressAction.value = null
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_CANCEL,
                testId = testId,
            )
        )
    }

    fun clearTestResults() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_CANCEL,
                testId = activeTestId.orEmpty(),
            )
        )
        MmkvManager.clearAllTestDelayResults(MmkvManager.decodeAllServerList())
        // Re-sort serversCache immediately so order=by-delay drops back to its
        // tie-break order right away, instead of waiting for a reload/restart.
        updateCache()
        updateListAction.postValue(-1)
    }

    fun clearTestResultsForGroup() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_CANCEL,
                testId = activeTestId.orEmpty(),
            )
        )
        MmkvManager.clearAllTestDelayResults(MmkvManager.decodeServerList(subscriptionId))
        // Re-sort serversCache immediately so order=by-delay drops back to its
        // tie-break order right away, instead of waiting for a reload/restart.
        updateCache()
        updateListAction.postValue(-1)
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    if (!isRestarting) {
                        isRunning.value = true
                    }
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    if (!isRestarting) {
                        markConnectionStopped()
                        isRunning.value = false
                    }
                }

                AppConfig.MSG_STATE_RESTART -> {
                    // A service restart is a new connection lifecycle. Clear the
                    // previous start time before the replacement service starts.
                    markConnectionStopped()
                    isRestarting = true
                    serviceRestartAction.value = Unit
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    val app = getApplication<AngApplication>()
                    val restarted = intent.serializable<Boolean>("content") == true
                    pendingServerRestartGuid = null
                    isRestarting = false
                    alertAction.value = Pair(
                        true,
                        app.getString(
                            if (restarted) R.string.toast_services_restart_success
                            else R.string.toast_services_success,
                        ),
                    )
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    val app = getApplication<AngApplication>()
                    val errorMessage = intent.getStringExtra("content")
                    val msg = if (!errorMessage.isNullOrBlank()) {
                        errorMessage
                    } else {
                        app.getString(R.string.toast_services_failure)
                    }

                    pendingServerRestartGuid = null
                    isRestarting = false
                    alertAction.value = Pair(false, msg)
                    markConnectionStopped()
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    pendingServerRestartGuid = null
                    isRestarting = false
                    markConnectionStopped()
                    isRunning.value = false
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    updateTestResultAction.value = intent.getStringExtra("content").orEmpty()
                }

                AppConfig.MSG_MEASURE_IP_SUCCESS -> {
                    val ip = intent.getStringExtra("content")
                    updateIpResultAction.value = ip
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val result = intent.serializable<RealPingResult>("content")
                    if (result != null) {
                        if (acceptsTestEvent(result.testId)) {
                            updateListAction.value = getPosition(result.guid)
                            // Result events also drive the dialog's detail list.
                            // The old adapter only receives TestProgressInfo rows,
                            // so forwarding only updateListAction leaves it empty.
                            activeTestCompleted += 1
                            activeTestTotal = maxOf(activeTestTotal, activeTestCompleted)
                            testProgressAction.value = TestProgressInfo(
                                guid = result.guid,
                                delayMillis = result.delayMillis,
                                current = activeTestCompleted,
                                total = activeTestTotal,
                            )
                        }
                    } else {
                        val content = intent.getStringExtra("content")
                        updateListAction.value = getPosition(content ?: "")
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val progress = intent.serializable<RealPingProgress>("content")
                    if (progress != null) {
                        if (acceptsTestEvent(progress.testId)) {
                            activeTestCompleted = maxOf(activeTestCompleted, progress.completed)
                            activeTestTotal = maxOf(activeTestTotal, progress.total)
                            testProgressAction.value = TestProgressInfo(
                                guid = "",
                                delayMillis = -1L,
                                current = activeTestCompleted,
                                total = activeTestTotal,
                            )
                        }
                    } else {
                        testProgressAction.value = intent.serializable<TestProgressInfo>("content")
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    val summary = intent.serializable<RealPingSummary>("content")
                    if (summary != null) {
                        if (!acceptsTestEvent(summary.testId)) return
                        activeTestId = null
                        testProgressAction.value = null
                        onTestsFinished(summary.cancelled)
                    } else {
                        activeTestId = null
                        testProgressAction.value = null
                        onTestsFinished()
                    }
                }

                AppConfig.MSG_COUNTRY_CODE_SUCCESS -> {
                    val content = intent.getStringExtra("content")
                    updateListAction.value = getPosition(content ?: "")
                }

                AppConfig.MSG_COUNTRY_CODE_NOTIFY -> {
                    countryCodeProgressAction.value = intent.serializable<TestProgressInfo>("content")
                }

                AppConfig.MSG_COUNTRY_CODE_FINISH -> {
                    countryCodeProgressAction.value = null
                }

                AppConfig.MSG_TRAFFIC_UPDATED -> {
                    val guid = intent.getStringExtra("content") ?: return
                    updateListAction.postValue(getPosition(guid))
                }

                AppConfig.MSG_TRAFFIC_SPEED_UPDATED -> {
                    val speedText = intent.getStringExtra("content") ?: return
                    updateTrafficSpeedAction.postValue(speedText)
                }

                AppConfig.MSG_SUB_UPDATE_FINISH -> {
                    updateGroupOrderAction.postValue(Unit)
                }
            }
        }
    }

    private fun acceptsTestEvent(testId: String): Boolean =
        testId.isEmpty() || testId == activeTestId
}
