package com.miku.ray.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionUserinfoParserTest {

    @Test
    fun parse_readsUsageRemainingAndExpiryCaseInsensitively() {
        val usage = SubscriptionUserinfoParser.parse(
            mapOf(
                "subscription-userinfo" to "upload=1024; download=2048; total=8192; expire=1735689600"
            )
        )

        assertEquals(3072L, usage.bytesUsed)
        assertEquals(5120L, usage.bytesRemaining)
        assertEquals(1735689600L, usage.expiresAt)
    }

    @Test
    fun parse_keepsUsageWhenTotalIsNotPublished() {
        val usage = SubscriptionUserinfoParser.parse(
            mapOf("Subscription-Userinfo" to "download=4096; upload=0")
        )

        assertEquals(4096L, usage.bytesUsed)
        assertEquals(-1L, usage.bytesRemaining)
        assertEquals(-1L, usage.expiresAt)
    }

    @Test
    fun parse_clampsRemainingAndSkipsInvalidValues() {
        val exhausted = SubscriptionUserinfoParser.parse(
            mapOf("Subscription-Userinfo" to "upload=100; download=200; total=250")
        )
        val malformed = SubscriptionUserinfoParser.parse(
            mapOf("Subscription-Userinfo" to "upload=nope; total=500; expire=invalid")
        )

        assertEquals(300L, exhausted.bytesUsed)
        assertEquals(0L, exhausted.bytesRemaining)
        assertEquals(-1L, malformed.bytesUsed)
        assertEquals(-1L, malformed.bytesRemaining)
        assertEquals(-1L, malformed.expiresAt)
    }

    @Test
    fun parse_returnsUnavailableValuesWithoutHeader() {
        val usage = SubscriptionUserinfoParser.parse(emptyMap())

        assertEquals(-1L, usage.bytesUsed)
        assertEquals(-1L, usage.bytesRemaining)
        assertEquals(-1L, usage.expiresAt)
    }
}
