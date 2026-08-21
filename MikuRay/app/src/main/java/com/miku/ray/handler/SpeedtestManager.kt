package com.miku.ray.handler

import com.miku.ray.AppConfig
import com.miku.ray.dto.IPAPIInfo
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

object SpeedtestManager {

    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 1500): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(url, port), timeoutMs)

            return System.currentTimeMillis() - start
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: ${e.message}")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) {
                        s.close()
                    }
                } catch (closeEx: IOException) {
                }
            }
        }
        return -1
    }

    /**
     * Reads the country of the outbound currently exposed by a temporary profile core.
     * The request must use the profile's local HTTP inbound; httpPort == 0 is rejected
     * to prevent accidentally querying through the device connection.
     */
    fun getCountryCodeThroughProxy(httpPort: Int, timeoutMs: Int = 3500): String? {
        if (httpPort <= 0) return null

        val configuredUrl = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL
        val url = configuredUrl.replace("{ip}", "", ignoreCase = true)
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = timeoutMs,
                httpPort = httpPort
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        return listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.length == 2 }
    }

    fun getRemoteIPInfo(): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        if (httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        val showIsp = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_ISP_INFO, true)
        val isp = if (showIsp) {
            listOf(
                ipInfo.isp,
                ipInfo.organization,
                ipInfo.org,
                ipInfo.asn_organization,
                ipInfo.asOrg,
                ipInfo.asname
            ).firstOrNull { !it.isNullOrBlank() }
        } else {
            null
        }

        val flag = Utils.countryCodeToFlag(country)
        val flagPrefix = if (flag.isNotEmpty()) "$flag " else ""
        val ispSuffix = if (!isp.isNullOrBlank()) " · $isp" else ""
        return "${flagPrefix}(${country ?: "unknown"}) ${ip ?: "unknown"}$ispSuffix"
    }
}
