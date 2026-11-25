package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.V2rayConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.util.JsonUtil

object CustomOutboundFmt : FmtBase() {
    fun parse(str: String): Pair<ProfileItem, String> {
        val config = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND)

        val outbound = JsonUtil.fromJson(str, V2rayConfig.OutboundBean::class.java)
            ?: throw IllegalArgumentException("Invalid outbound JSON")

        config.remarks = outbound.tag.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort()?.toString()
        config.customProtocol = outbound.protocol.takeIf { it.isNotBlank() }
        config.network = outbound.streamSettings?.network
        config.security = outbound.streamSettings?.security

        outbound.tag = AppConfig.TAG_PROXY
        val rawOutbound = JsonUtil.toJsonPretty(outbound).orEmpty()
        return Pair(config, rawOutbound)
    }
}
