/*
 * Subscription-Userinfo parsing adapted from Exclave (ExclaveNetwork/Exclave),
 * Copyright (C) 2021 nekohasekai and Exclave contributors.
 *
 * Exclave is licensed under GNU GPL-3.0-or-later. This adapted file is
 * distributed under GNU GPL-3.0-or-later; the MikuRay project remains GPL-3.0.
 */
package com.miku.ray.util

/**
 * Parses the de facto `Subscription-Userinfo` response header commonly used
 * by proxy subscription providers. Unknown or malformed values are represented
 * by -1 and never prevent the subscription content from being imported.
 */
object SubscriptionUserinfoParser {
    data class Usage(
        val bytesUsed: Long = -1L,
        val bytesRemaining: Long = -1L,
        val expiresAt: Long = -1L,
    )

    fun parse(headers: Map<String, String>): Usage {
        val userinfo = headers.entries
            .firstOrNull { (name, _) -> name.equals(HEADER_NAME, ignoreCase = true) }
            ?.value
            ?.trim()
            .orEmpty()
        if (userinfo.isEmpty()) return Usage()

        val values = userinfo
            .split(';')
            .mapNotNull { token ->
                val separator = token.indexOf('=')
                if (separator <= 0) null else {
                    token.substring(0, separator).trim().lowercase() to
                        token.substring(separator + 1).trim()
                }
            }
            .toMap()

        val upload = values["upload"].toNonNegativeLong()
        val download = values["download"].toNonNegativeLong()
        val used = when {
            upload == null && download == null -> -1L
            else -> (upload ?: 0L).saturatingAdd(download ?: 0L)
        }
        val total = values["total"].toNonNegativeLong()
        val remaining = if (used >= 0L && total != null) {
            (total - used).coerceAtLeast(0L)
        } else {
            -1L
        }

        return Usage(
            bytesUsed = used,
            bytesRemaining = remaining,
            expiresAt = values["expire"].toNonNegativeLong() ?: -1L,
        )
    }

    private fun String?.toNonNegativeLong(): Long? =
        this?.toLongOrNull()?.takeIf { it >= 0L }

    private fun Long.saturatingAdd(other: Long): Long =
        if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other

    private const val HEADER_NAME = "Subscription-Userinfo"
}
