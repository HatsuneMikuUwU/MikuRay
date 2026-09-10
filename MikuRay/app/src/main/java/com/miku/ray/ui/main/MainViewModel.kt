package com.miku.ray.ui.main

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
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
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.ui.bottomsheet.SortSubBottomSheet
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.matchesPattern
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mainRepository = MainRepository(getApplication())
    private var mainServiceEventJob: Job? = null
    private var serverList = mutableListOf<String>()
    var subscriptionId: String = mainRepository.getSelectedSubscriptionId()
    var keywordFilter = ""
    private var activeTestId: String? = null
    private var activeTestCompleted = 0
    private var activeTestTotal = 0
    private var isRestarting = false
    private var pendingServerRestartGuid: String? = null
    private var reloadJob: Job? = null
    @Volatile
    private var serverCacheLoaded = false
    val serversCache = mutableListOf<ServersCache>()

    private val _serversState = MutableStateFlow<List<ServersCache>>(emptyList())
    val serversState: StateFlow<List<ServersCache>> = _serversState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val groupCache = ConcurrentHashMap<String, List<ServersCache>>()
    private val groupStates = ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()

    val isRunning by lazy { MutableLiveData(false) }
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

    init {
        reloadServerList(notify = false)
        mainServiceEventJob = viewModelScope.launch {
            mainRepository.mainServiceEvent.collectLatest(::onMainServiceEvent)
        }
    }

    fun startListenBroadcast() {
        mainRepository.sendMsg2Service(AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        reloadJob?.cancel()
        mainServiceEventJob?.cancel()
        mainRepository.close()
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    @Synchronized
    fun reloadServerList(notify: Boolean = true) {
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
        serverCacheLoaded = true
        if (notify) updateListAction.postValue(-1)
    }

    fun refreshServerList(updateSubscription: Boolean = false): Job {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                if (updateSubscription) {
                    updateConfigViaSubAll()
                    refreshAllGroupCaches()
                }
                reloadServerList(notify = false)
                withContext(Dispatchers.Main) {
                    updateListAction.value = -1
                    updateGroupBadgeAction.value = Unit
                }
            } finally {
                _isRefreshing.value = false
            }
        }
        return reloadJob!!
    }

    private fun refreshAllGroupCaches() {
        val groupIds = buildList {
            add("")
            addAll(MmkvManager.decodeSubsList())
        }.distinct()
        groupIds.forEach { groupId ->
            val snapshot = buildServerCache(groupId)
            groupCache[groupId] = snapshot
            groupStates.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }.value = snapshot
        }
    }

    fun refreshGroups() {
        refreshServerList(updateSubscription = false)
    }

    fun ensureServerCacheReady() {
        if (!serverCacheLoaded) reloadServerList()
    }

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        updateCache()
        refreshAllGroupCaches()
        updateListAction.postValue(-1)
        updateGroupBadgeAction.postValue(Unit)
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            return
        }

        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)
        val reordered = serversCache.toList()
        groupCache[subscriptionId] = reordered
        groupStates.computeIfAbsent(subscriptionId) { MutableStateFlow(emptyList()) }.value = reordered
        _serversState.value = reordered

        MmkvManager.encodeServerList(serverList, subscriptionId)
    }

    @Synchronized
    fun updateCache() {
        val groupId = subscriptionId
        val snapshot = buildServerCache(groupId)
        serverList = if (groupId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(groupId)
        }
        serversCache.clear()
        serversCache.addAll(snapshot)
        groupCache[groupId] = snapshot
        groupStates.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }.value = snapshot
        _serversState.value = snapshot
    }

    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> {
        val state = groupStates.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }
        if (state.value.isEmpty() && !groupCache.containsKey(groupId)) {
            val snapshot = buildServerCache(groupId)
            groupCache[groupId] = snapshot
            state.value = snapshot
        }
        return state.asStateFlow()
    }

    private fun buildServerCache(groupId: String): List<ServersCache> {
        val guids = if (groupId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(groupId)
        }
        val kw = keywordFilter.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (_: PatternSyntaxException) {
            null
        }
        val result = guids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            if (kw.isEmpty()) return@mapNotNull ServersCache(guid, profile)
            val matches = profile.remarks.matchesPattern(searchRegex, kw)
            || profile.description.orEmpty().matchesPattern(searchRegex, kw)
            || profile.server.orEmpty().matchesPattern(searchRegex, kw)
            || profile.configType.name.matchesPattern(searchRegex, kw)
            if (matches) ServersCache(guid, profile) else null
        }.toMutableList()

        val subId = groupId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        when (MmkvManager.decodeSettingsInt("${AppConfig.PREF_SERVER_ORDER}_$subId", 0)) {
            1 -> result.sortWith(compareBy { it.profile.remarks.lowercase() })
            2 -> result.sortWith(compareBy {
                    val delay = MmkvManager.decodeServerAffiliationInfo(it.guid)?.testDelayMillis ?: 0L
                    if (delay <= 0L) Long.MAX_VALUE else delay
            })
        }
        val pinnedServers = MmkvManager.decodePinnedServers()
        if (pinnedServers.isNotEmpty()) {
            result.sortByDescending { pinnedServers.contains(it.guid) }
        }
        return result
    }

    fun togglePinServer(guid: String): Boolean {
        val nowPinned = MmkvManager.togglePinnedServer(guid)
        updateCache()
        refreshAllGroupCaches()
        updateListAction.postValue(-1)
        return nowPinned
    }

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        if (subscriptionId.isEmpty()) {
            return mainRepository.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return SubscriptionUpdateResult()
            return mainRepository.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    fun exportAllServer(): Int {
        val serverListCopy =
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            serverList
        } else {
            serversCache.map { it.guid }.toList()
        }

        return mainRepository.shareNonCustomConfigsToClipboard(serverListCopy)
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        val testId = UUID.randomUUID().toString()
        activeTestId = testId
        activeTestCompleted = 0
        activeTestTotal = serversCache.size
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1

        viewModelScope.launch(Dispatchers.Default) {
            if (serversCache.isEmpty()) {
                withContext(Dispatchers.Main) {
                    reloadServerList()
                    activeTestTotal = serversCache.size
                }
            }
            if (serversCache.isEmpty()) {
                activeTestId = null
                withContext(Dispatchers.Main) {
                    testProgressAction.value = null
                }
                return@launch
            }
            mainRepository.sendMsg2TestService(
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
        mainRepository.sendMsg2CountryCodeTestService(
            CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL)
        )
        val guids = serversCache.map { it.guid }.toList()
        MmkvManager.clearAllCountryCodes(guids)
        updateListAction.value = -1

        viewModelScope.launch(Dispatchers.Default) {
            if (guids.isEmpty()) return@launch
            mainRepository.sendMsg2CountryCodeTestService(
                CountryCodeTestMessage(
                    key = AppConfig.MSG_COUNTRY_CODE_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) guids else emptyList()
                )
            )
        }
    }

    fun cancelCountryCodeTest() {
        mainRepository.sendMsg2CountryCodeTestService(
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
        mainRepository.testCurrentServerRealPing()
    }

    fun fetchCurrentIp() {
        mainRepository.sendMsg2Service(AppConfig.MSG_MEASURE_IP, "")
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
        groupCache.clear()
        refreshAllGroupCaches()
        reloadServerList()
    }

    fun beginServerRestart(guid: String): Boolean {
        if (guid == MmkvManager.getSelectServer() || pendingServerRestartGuid != null) return false

        MmkvManager.setSelectServer(guid)
        pendingServerRestartGuid = guid
        isRestarting = true
        return true
    }

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
        updateCache()
        updateListAction.postValue(-1)
    }

    private fun onMainServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> {
                if (!isRestarting) {
                    isRunning.value = true
                    updateListAction.postValue(-1)
                }
            }

            MainServiceEvent.StateNotRunning -> {
                if (!isRestarting) {
                    markConnectionStopped()
                    isRunning.value = false
                    updateListAction.postValue(-1)
                }
            }

            MainServiceEvent.StateRestart -> {
                markConnectionStopped()
                isRestarting = true
                serviceRestartAction.value = Unit
            }

            is MainServiceEvent.StateStartSuccess -> {
                val app = getApplication<AngApplication>()
                pendingServerRestartGuid = null
                isRestarting = false
                alertAction.value = Pair(
                    true,
                    app.getString(
                        if (event.restarted) R.string.toast_services_restart_success
                        else R.string.toast_services_success,
                    ),
                )
                isRunning.value = true
                updateListAction.postValue(-1)
            }

            is MainServiceEvent.StateStartFailure -> {
                val app = getApplication<AngApplication>()
                val msg = if (!event.message.isNullOrBlank()) {
                    event.message
                } else {
                    app.getString(R.string.toast_services_failure)
                }

                pendingServerRestartGuid = null
                isRestarting = false
                alertAction.value = Pair(false, msg)
                markConnectionStopped()
                isRunning.value = false
                updateListAction.postValue(-1)
            }

            MainServiceEvent.StateStopSuccess -> {
                pendingServerRestartGuid = null
                isRestarting = false
                markConnectionStopped()
                isRunning.value = false
                updateListAction.postValue(-1)
            }

            is MainServiceEvent.MeasureDelayResult -> {
                updateTestResultAction.value = event.text
            }

            is MainServiceEvent.MeasureIpResult -> {
                updateIpResultAction.value = event.ip
            }

            is MainServiceEvent.MeasureConfigResult -> {
                val result = event.result
                if (result != null) {
                    if (acceptsTestEvent(result.testId)) {
                        updateListAction.postValue(getPosition(result.guid))
                        activeTestCompleted += 1
                        activeTestTotal = maxOf(activeTestTotal, activeTestCompleted)
                        testProgressAction.postValue(
                            TestProgressInfo(
                                guid = result.guid,
                                delayMillis = result.delayMillis,
                                current = activeTestCompleted,
                                total = activeTestTotal,
                            )
                        )
                    }
                } else {
                    updateListAction.postValue(getPosition(event.rawGuid.orEmpty()))
                }
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                val progress = event.progress
                if (progress != null) {
                    if (acceptsTestEvent(progress.testId)) {
                        activeTestCompleted = maxOf(activeTestCompleted, progress.completed)
                        activeTestTotal = maxOf(activeTestTotal, progress.total)
                        testProgressAction.postValue(
                            TestProgressInfo(
                                guid = "",
                                delayMillis = -1L,
                                current = activeTestCompleted,
                                total = activeTestTotal,
                            )
                        )
                    }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val summary = event.summary
                if (summary != null) {
                    if (!acceptsTestEvent(summary.testId)) return
                    activeTestId = null
                    testProgressAction.postValue(null)
                    onTestsFinished(summary.cancelled)
                } else {
                    activeTestId = null
                    testProgressAction.postValue(null)
                    onTestsFinished()
                }
            }

            is MainServiceEvent.CountryCodeSuccess -> {
                updateListAction.postValue(getPosition(event.guid))
            }

            is MainServiceEvent.CountryCodeNotify -> {
                event.info?.let(countryCodeProgressAction::postValue)
            }

            MainServiceEvent.CountryCodeFinish -> {
                countryCodeProgressAction.postValue(null)
            }

            is MainServiceEvent.TrafficUpdated -> {
                updateListAction.postValue(getPosition(event.guid))
            }

            is MainServiceEvent.TrafficSpeedUpdated -> {
                updateTrafficSpeedAction.postValue(event.speedText)
            }

            MainServiceEvent.SubUpdateFinish -> {
                updateGroupOrderAction.postValue(Unit)
            }
        }
    }

    private fun acceptsTestEvent(testId: String): Boolean =
        testId.isEmpty() || testId == activeTestId
}
