/*
 * SIP008 subscription parsing adapted from Exclave (ExclaveNetwork/Exclave),
 * Copyright (C) 2021 nekohasekai and Exclave contributors.
 *
 * Exclave is licensed under GNU GPL-3.0-or-later. This adapted file is
 * distributed under GNU GPL-3.0-or-later; the MikuRay project remains GPL-3.0.
 *
 * This implementation intentionally accepts only Shadowsocks methods and
 * plugin options that can be represented by MikuRay's existing profile model.
 */
package com.miku.ray.fmt

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType

/**
 * Parses SIP008 JSON subscriptions into MikuRay Shadowsocks profiles.
 *
 * SIP008 supports optional plugins. MikuRay can represent only the existing
 * `simple-obfs` HTTP mode mapping; unsupported plugins are skipped instead of
 * creating profiles that would fail at runtime.
 */
object SIP008Fmt {
    private val supportedMethods = setOf(
        "aes-128-gcm",
        "aes-192-gcm",
        "aes-256-gcm",
        "chacha20-ietf-poly1305",
    )

    fun parse(content: String?): List<ProfileItem> {
        if (content.isNullOrBlank()) return emptyList()

        val root = try {
            JsonParser.parseString(content).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        if (root.intValue("version") != 1) return emptyList()
        val servers = root.get("servers")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()

        return servers.mapNotNull { server ->
            server.takeIf { it.isJsonObject }?.asJsonObject?.let(::parseServer)
        }
    }

    private fun parseServer(server: JsonObject): ProfileItem? {
        val host = server.stringValue("server")?.trim().orEmpty()
        val port = server.intValue("server_port") ?: return null
        val password = server.stringValue("password") ?: return null
        val method = normalizeMethod(server.stringValue("method")) ?: return null

        if (host.isEmpty() || port !in 1..65535) return null

        return ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
            remarks = server.stringValue("remarks")?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "$host:$port"
            this.server = host
            serverPort = port.toString()
            this.password = password
            this.method = method

            if (!applyCompatiblePlugin(server)) return null
        }
    }

    private fun ProfileItem.applyCompatiblePlugin(server: JsonObject): Boolean {
        val plugin = server.stringValue("plugin")?.trim().orEmpty()
        if (plugin.isEmpty()) return true

        // MikuRay's current Shadowsocks URI parser supports the equivalent
        // simple-obfs HTTP mapping, but not arbitrary SIP003 plugins.
        if (plugin != "simple-obfs" && plugin != "obfs-local") return false

        val options = server.stringValue("plugin_opts")
            ?.split(';')
            ?.mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) null else {
                    entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
                }
            }
            ?.toMap()
            .orEmpty()

        if (options["obfs"] != "http") return false

        network = NetworkType.TCP.type
        headerType = "http"
        this.host = options["obfs-host"]?.takeIf { it.isNotBlank() }
        path = options["path"]?.takeIf { it.isNotBlank() }
        return true
    }

    private fun normalizeMethod(method: String?): String? {
        val normalized = method?.trim()?.lowercase()?.replace('_', '-') ?: return null
        return when (normalized) {
            "aead-chacha20-poly1305", "aead-chacha20-ietf-poly1305", "chacha20-poly1305" ->
                "chacha20-ietf-poly1305"
            "aead-aes-128-gcm" -> "aes-128-gcm"
            "aead-aes-192-gcm" -> "aes-192-gcm"
            "aead-aes-256-gcm" -> "aes-256-gcm"
            in supportedMethods -> normalized
            else -> null
        }
    }

    private fun JsonObject.stringValue(name: String): String? {
        val value = get(name) ?: return null
        return value.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonObject.intValue(name: String): Int? {
        val value = get(name) ?: return null
        return try {
            value.takeIf { it.isJsonPrimitive }?.asInt
        } catch (_: Exception) {
            null
        }
    }
}
