package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import com.miku.ray.extension.idnHost
import com.miku.ray.extension.nullIfBlank
import com.miku.ray.util.Utils
import java.net.URI

object MasqueFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.MASQUE)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = getQueryParam(uri)

        getItemFormQuery(config, queryParam)

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.secretKey = uri.userInfo.orEmpty()
        config.publicKey = queryParam["publickey"].orEmpty()
        config.security = AppConfig.TLS
        config.network = NetworkType.MASQUE.type
        config.insecure = false
        config.alpn = config.alpn?.trim()?.nullIfBlank() ?: "h3"
        if (config.alpn != "h2" && config.alpn != "h3") return null
        config.localAddress = queryParam["address"] ?: ""
        config.remoteDNS = queryParam["dns"] ?: ""
        config.headers = queryParam["headers"]?.nullIfBlank()

        return config
    }

    fun toUri(config: ProfileItem): String {
        val dicQuery = getQueryDic(
            config.copy(
                security = AppConfig.TLS,
                network = NetworkType.MASQUE.type,
                insecure = false,
                alpn = config.alpn?.trim()?.nullIfBlank() ?: "h3"
            )
        )
        // already have publickey in query
        dicQuery.remove("pbk")
        config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
        config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
        config.publicKey?.nullIfBlank()?.let { dicQuery["publickey"] = it }
        dicQuery["address"] = config.localAddress.orEmpty()
        dicQuery["dns"] = config.remoteDNS.orEmpty()
        config.headers?.nullIfBlank()?.let { dicQuery["headers"] = it }
        return toUri(config, config.secretKey, dicQuery)
    }
}
