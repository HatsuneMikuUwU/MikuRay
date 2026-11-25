package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import com.miku.ray.extension.idnHost
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.extension.nullIfBlank
import com.miku.ray.util.Utils
import java.net.URI

object Hysteria2Fmt : FmtBase() {
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.HYSTERIA2)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = (if (uri.port == -1) AppConfig.DEFAULT_PORT else uri.port).toString()
        config.password = uri.userInfo
        config.security = AppConfig.TLS
        config.network = NetworkType.HYSTERIA.type

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)

            getItemFormQuery(config, queryParam)

            config.security = queryParam["security"] ?: AppConfig.TLS
            config.obfs = queryParam["obfs"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "none" }
            config.obfsPassword = queryParam["obfs-password"]
            config.minPacketSize = queryParam["minPacketSize"]?.trim()?.nullIfBlank()
            config.maxPacketSize = queryParam["maxPacketSize"]?.trim()?.nullIfBlank()
            config.portHopping = queryParam["mport"]
            config.pinnedCA256 = queryParam["pinSHA256"]
            if (config.isGeckoObfs()) {
                if (config.minPacketSize.isNullOrEmpty()) {
                    config.minPacketSize = "512"
                }
                if (config.maxPacketSize.isNullOrEmpty()) {
                    config.maxPacketSize = "1200"
                }
            }
        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()

        config.security.let { if (it != null) dicQuery["security"] = it }
        config.sni?.nullIfBlank()?.let { dicQuery["sni"] = it }
        config.alpn?.nullIfBlank()?.let { dicQuery["alpn"] = it }
        config.insecure.let { dicQuery["insecure"] = if (it == true) "1" else "0" }

        val isGecko = config.isGeckoObfs()
        if (config.obfsPassword.isNotNullEmpty() && !config.obfs.equals("none", ignoreCase = true)) {
            dicQuery["obfs"] = if (isGecko) "gecko" else "salamander"
            dicQuery["obfs-password"] = config.obfsPassword.orEmpty()
            if (isGecko) {
                config.minPacketSize?.nullIfBlank()?.let { dicQuery["minPacketSize"] = it }
                config.maxPacketSize?.nullIfBlank()?.let { dicQuery["maxPacketSize"] = it }
            }
        }
        if (config.portHopping.isNotNullEmpty()) {
            dicQuery["mport"] = config.portHopping.orEmpty()
        }
        if (config.pinnedCA256.isNotNullEmpty()) {
            dicQuery["pinSHA256"] = config.pinnedCA256.orEmpty()
        }

        return toUri(config, config.password, dicQuery)
    }
}
