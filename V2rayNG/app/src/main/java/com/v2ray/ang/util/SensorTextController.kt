package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager

object SensorTextController {

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_SENSOR_TEXT, false)

    fun getAddress(profile: ProfileItem): String {
        return if (isEnabled()) {
            val server = profile.server ?: ""
            val port = profile.serverPort ?: ""
            if (server.isBlank() && port.isBlank()) "" else "$server : $port"
        } else {
            generateMaskedDescription(profile)
        }
    }

    private fun generateMaskedDescription(profile: ProfileItem): String {
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        val addrPart = server?.let {
            if (it.contains(":"))
                it.split(":").take(2).joinToString(":", postfix = ":***")
            else
                it.split('.').dropLast(1).joinToString(".", postfix = ".***")
        } ?: ""

        return "$addrPart : ${port ?: ""}"
    }
}
