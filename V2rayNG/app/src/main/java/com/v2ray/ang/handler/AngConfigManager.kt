package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.text.TextUtils
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.dto.ProfileDiffEntry
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.V2rayNFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import java.net.URI

object AngConfigManager {

    private val configFmtParsers: Map<String, (String) -> ProfileItem?> by lazy {
        mapOf(
            EConfigType.VMESS.protocolScheme to VmessFmt::parse,
            EConfigType.SHADOWSOCKS.protocolScheme to ShadowsocksFmt::parse,
            EConfigType.SOCKS.protocolScheme to SocksFmt::parse,
            AppConfig.SOCKS4 to SocksFmt::parse,
            AppConfig.SOCKS5 to SocksFmt::parse,
            EConfigType.TROJAN.protocolScheme to TrojanFmt::parse,
            EConfigType.VLESS.protocolScheme to VlessFmt::parse,
            EConfigType.WIREGUARD.protocolScheme to WireguardFmt::parse,
            EConfigType.HYSTERIA2.protocolScheme to Hysteria2Fmt::parse,
            AppConfig.HY2 to Hysteria2Fmt::parse,
            AppConfig.V2RAYNFMTS to V2rayNFmt::parse
        )
    }

    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config to clipboard", e)
            return -1
        }
        return 0
    }

    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count() - 1
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share non-custom configs to clipboard", e)
            return -1
        }
    }

    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config as QR code", e)
            return null
        }
    }

    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = CoreConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share full content to clipboard", e)
            return -1
        }
        return 0
    }

    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
                else -> {}
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config for GUID: $guid", e)
            return ""
        }
    }

    fun importBatchConfig(server: String?, subid: String, append: Boolean): Pair<Int, Int> {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid, append)
        }

        var countSub = parseBatchSubscription(server)
        if (countSub <= 0) {
            countSub = parseBatchSubscription(Utils.decode(server))
        }
        if (countSub > 0) {
            updateConfigViaSubAll()
        }

        return count to countSub
    }

    private fun parseBatchSubscription(servers: String?): Int {
        try {
            if (servers == null) {
                return 0
            }

            var count = 0
            servers.lines()
                .distinct()
                .forEach { str ->
                    if (Utils.isValidSubUrl(str)) {
                        count += importUrlAsSubscription(str)
                    }
                }
            return count
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch subscription", e)
        }
        return 0
    }

    private fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            if (servers == null) {
                return 0
            }
            val removedSelected = getRemovedSelectedProfile(subid, append)

            val subItem = MmkvManager.decodeSubscription(subid)

            val configs = mutableListOf<ProfileItem>()
            servers.lines()
                .distinct()
                .reversed()
                .forEach {
                    val config = parseConfig(it, subid, subItem)
                    if (config != null) {
                        configs.add(config)
                    }
                }

            if (configs.isNotEmpty()) {
                if (!append) {
                    MmkvManager.removeServerViaSubid(subid)
                }
                val keyToProfile = batchSaveConfigs(configs, subid)
                val matchKey = findMatchedProfileKey(keyToProfile, removedSelected)
                matchKey?.let { MmkvManager.setSelectServer(it) }
            }

            return configs.size
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch config", e)
        }
        return 0
    }

    private fun batchSaveConfigs(configs: List<ProfileItem>, subid: String): Map<String, ProfileItem> {
        val keyToProfile = mutableMapOf<String, ProfileItem>()

        val serverList = MmkvManager.decodeServerList(subid)

        configs.forEach { config ->
            val key = Utils.getUuid()
            MmkvManager.encodeProfileDirect(key, JsonUtil.toJson(config))

            if (!serverList.contains(key)) {
                serverList.add(0, key)
            }
            keyToProfile[key] = config
        }

        MmkvManager.encodeServerList(serverList, subid)
        return keyToProfile
    }

    private fun findMatchedProfileKey(keyToProfile: Map<String, ProfileItem>, target: ProfileItem?): String? {
        if (keyToProfile.isEmpty()) return null
        if (target == null) return null

        if (target.remarks.isNotBlank()) {
            keyToProfile.entries.firstOrNull { (_, saved) ->
                isSameText(saved.remarks, target.remarks) &&
                        isSameText(saved.server, target.server) &&
                        isSameText(saved.serverPort, target.serverPort) &&
                        isSameText(saved.password, target.password)
            }?.key?.let { return it }
        }

        if (target.remarks.isNotBlank()) {
            keyToProfile.entries.firstOrNull { (_, saved) ->
                isSameText(saved.remarks, target.remarks)
            }?.key?.let { return it }
        }

        keyToProfile.entries.firstOrNull { (_, saved) ->
            isSameText(saved.server, target.server) &&
                    isSameText(saved.serverPort, target.serverPort) &&
                    isSameText(saved.password, target.password)
        }?.key?.let { return it }

        keyToProfile.entries.firstOrNull { (_, saved) ->
            isSameText(saved.server, target.server) &&
                    isSameText(saved.serverPort, target.serverPort)
        }?.key?.let { return it }

        keyToProfile.entries.firstOrNull { (_, saved) ->
            isSameText(saved.server, target.server)
        }?.key?.let { return it }

        return keyToProfile.keys.firstOrNull()
    }

    private fun getRemovedSelectedProfile(subid: String, append: Boolean): ProfileItem? {
        if (subid.isBlank() || append) return null

        return MmkvManager.getSelectServer()
            .takeIf { it?.isNotBlank() == true }
            ?.let { MmkvManager.decodeServerConfig(it) }
            ?.takeIf { it.subscriptionId == subid }
    }

    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }

    private fun parseCustomConfigServer(server: String?, subid: String, append: Boolean): Int {
        if (server == null) {
            return 0
        }
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val serverList: Array<Any> =
                    JsonUtil.fromJson(server, Array<Any>::class.java) ?: arrayOf()

                if (serverList.isNotEmpty()) {
                    val removedSelected = getRemovedSelectedProfile(subid, append)
                    if (!append) {
                        MmkvManager.removeServerViaSubid(subid)
                    }
                    var count = 0
                    val keyToProfile = mutableMapOf<String, ProfileItem>()
                    for (srv in serverList.reversed()) {
                        val config = CustomFmt.parse(JsonUtil.toJson(srv))
                        config.subscriptionId = subid
                        val key = MmkvManager.encodeServerConfig("", config)
                        MmkvManager.encodeServerRaw(key, JsonUtil.toJsonPretty(srv) ?: "")
                        keyToProfile[key] = config
                        count += 1
                    }
                    if (count > 0) {
                        val matchKey = findMatchedProfileKey(keyToProfile, removedSelected)
                        matchKey?.let { MmkvManager.setSelectServer(it) }
                    }
                    return count
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                val config = CustomFmt.parse(server)
                config.subscriptionId = subid
                if (!append) {
                    MmkvManager.removeServerViaSubid(subid)
                }
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server as single config", e)
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config = WireguardFmt.parseWireguardConfFile(server)
                if (!append) {
                    MmkvManager.removeServerViaSubid(subid)
                }
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse WireGuard config file", e)
            }
            return 0
        } else {
            return 0
        }
    }

    private fun parseConfig(
        str: String?,
        subid: String,
        subItem: SubscriptionItem?
    ): ProfileItem? {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return null
            }

            val config = configFmtParsers.firstNotNullOfOrNull { (scheme, parser) ->
                if (str.startsWith(scheme)) parser(str) else null
            }

            if (config == null) {
                return null
            }

            if (subItem?.filter.isNotNullEmpty() && config.remarks.isNotNullEmpty()) {
                val matched = Regex(pattern = subItem?.filter.orEmpty())
                    .containsMatchIn(input = config.remarks)
                if (!matched) return null
            }

            if (subItem?.networkFilter.isNotNullEmpty()) {
                val allowedNetworks = subItem?.networkFilter.orEmpty()
                    .split(',', '，', ' ')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                if (allowedNetworks.isNotEmpty()) {
                    val configNetwork = config.network.orEmpty().lowercase().ifEmpty { "tcp" }
                    if (configNetwork !in allowedNetworks) return null
                }
            }

            if (subItem?.protocolFilter.isNotNullEmpty()) {
                val allowedProtocols = subItem?.protocolFilter.orEmpty()
                    .split(',', '，', ' ')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                if (allowedProtocols.isNotEmpty()) {
                    val configProtocol = config.configType.protocolScheme
                        .removeSuffix("://")
                        .lowercase()
                    if (configProtocol !in allowedProtocols) return null
                }
            }

            config.subscriptionId = subid

            if (str.startsWith(AppConfig.V2RAYNFMTS, ignoreCase = true)
                && config.policyGroupSubscriptionId == "self") {
                config.policyGroupSubscriptionId = subid
            }

            return config
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse config", e)
            return null
        }
    }

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        return try {
            val subscriptions = MmkvManager.decodeSubscriptions()
            subscriptions.fold(SubscriptionUpdateResult()) { acc, subscription ->
                acc + updateConfigViaSub(subscription)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via all subscriptions", e)
            SubscriptionUpdateResult()
        }
    }

    fun removeInvalidServer(subId: String) {
        val serverList = MmkvManager.decodeServerList(subId)
        val invalidServers = serverList.filter {
            val aff = MmkvManager.decodeServerAffiliationInfo(it)
            aff != null && aff.testDelayMillis < 0L
        }
        MmkvManager.removeServers(invalidServers, subId)
    }

    fun sortByTestResultsForSub(subId: String) {
        val serverList = MmkvManager.decodeServerList(subId)
        if (serverList.isEmpty()) return

        val sorted = serverList
            .map { guid ->
                val delay =
                    MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                guid to if (delay <= 0L) Long.MAX_VALUE else delay
            }
            .sortedBy { it.second }
            .map { it.first }
            .toMutableList()
        MmkvManager.encodeServerList(sorted, subId)
    }

    fun updateConfigViaSub(it: SubscriptionCache): SubscriptionUpdateResult {
        try {
            if (!it.subscription.enabled) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            if (TextUtils.isEmpty(it.guid)
                || TextUtils.isEmpty(it.subscription.remarks)
                || TextUtils.isEmpty(it.subscription.url)
            ) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            val url = HttpUtil.toIdnUrl(it.subscription.url)
            if (!Utils.isValidUrl(url)) {
                return SubscriptionUpdateResult(failureCount = 1)
            }
            if (!it.subscription.allowInsecureUrl) {
                if (!Utils.isValidSubUrl(url)) {
                    return SubscriptionUpdateResult(failureCount = 1)
                }
            }
            val oldProfileNames = snapshotProfileNames(it.guid)

            LogUtil.i(AppConfig.TAG, url)
            val userAgent = it.subscription.userAgent
            val requestHeaders = it.subscription.requestHeaders
            val proxyUsername = SettingsManager.getSocksUsername()
            val proxyPassword = SettingsManager.getSocksPassword()
            val hwid = if (SettingsManager.isSendHwidEnabled()) {
                Settings.Secure.getString(
                    AngApplication.application.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            } else null

            var configText = try {
                val httpPort = SettingsManager.getHttpPort()
                HttpUtil.getUrlContentWithUserAgent(
                    UrlContentRequest(
                        url = url,
                        userAgent = userAgent,
                        requestHeaders = requestHeaders,
                        timeout = 15000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword,
                        hwid = hwid
                    )
                )
            } catch (e: Exception) {
                LogUtil.e(AppConfig.ANG_PACKAGE, "Update subscription: proxy not ready or other error", e)
                ""
            }
            if (configText.isEmpty()) {
                configText = try {
                    HttpUtil.getUrlContentWithUserAgent(
                        UrlContentRequest(
                            url = url,
                            userAgent = userAgent,
                            requestHeaders = requestHeaders,
                            hwid = hwid
                        )
                    )
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Update subscription: Failed to get URL content with user agent", e)
                    ""
                }
            }
            if (configText.isEmpty()) {
                return SubscriptionUpdateResult(failureCount = 1)
            }

            val count = parseConfigViaSub(configText, it.guid, false)
            if (count > 0) {
                it.subscription.lastUpdated = System.currentTimeMillis()
                MmkvManager.encodeSubscription(it.guid, it.subscription)
                LogUtil.i(AppConfig.TAG, "Subscription updated: ${it.subscription.remarks}, $count configs")

                val newProfileNames = snapshotProfileNames(it.guid)
                val subName = it.subscription.remarks

                var added = (newProfileNames - oldProfileNames)
                    .map { name -> ProfileDiffEntry(subName, name) }
                val deleted = (oldProfileNames - newProfileNames)
                    .map { name -> ProfileDiffEntry(subName, name) }

                if (added.isEmpty() && deleted.isEmpty()) {
                    added = newProfileNames.map { name -> ProfileDiffEntry(subName, name) }
                }

                return SubscriptionUpdateResult(
                    configCount = count,
                    successCount = 1,
                    addedProfiles = added,
                    deletedProfiles = deleted
                )
            } else {
                return SubscriptionUpdateResult(failureCount = 1)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via subscription", e)
            return SubscriptionUpdateResult(failureCount = 1)
        }
    }

    private fun snapshotProfileNames(subId: String): Set<String> {
        return MmkvManager.decodeServerList(subId)
            .mapNotNull { guid -> MmkvManager.decodeServerConfig(guid)?.remarks }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid, append)
        }
        return count
    }

    private fun importUrlAsSubscription(url: String): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.subscription.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        MmkvManager.encodeSubscription("", subItem)
        return 1
    }

    fun generateDescription(profile: ProfileItem): String {
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        return "$server : ${port ?: ""}"
    }
}
