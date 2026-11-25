package com.miku.ray.fmt

import com.miku.ray.enums.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class FmtBaseTest {

    private val fmt = object : FmtBase() {}

    @Test
    fun queryValueKeepsUnencodedEqualsSigns() {
        val params = fmt.getQueryParam(
            URI("vless://id@example.com:443?type=ws&path=/ws?ed=2048&ech=AEX+/AEE==&security=tls")
        )

        assertEquals("/ws?ed=2048", params["path"])
        assertEquals("AEX+/AEE==", params["ech"])
        assertEquals("tls", params["security"])
    }

    @Test
    fun pairsWithoutValueAreSkipped() {
        val params = fmt.getQueryParam(URI("vless://id@example.com:443?security=tls&&flag&type=ws&"))

        assertEquals(mapOf("security" to "tls", "type" to "ws"), params)
        assertFalse(params.containsKey("flag"))
    }

    @Test
    fun nullOrEmptyQueryReturnsEmptyMap() {
        assertTrue(fmt.getQueryParam(URI("vless://id@example.com:443")).isEmpty())
        assertTrue(fmt.getQueryParam(URI("vless://id@example.com:443?")).isEmpty())
    }

    @Test
    fun vlessLinkWithTrailingAmpersandIsParsed() {
        val profile = VlessFmt.parse("vless://id@example.com:443?security=tls&type=ws&path=%2Fws&#name")

        assertNotNull(profile)
        assertEquals("tls", profile!!.security)
        assertEquals(NetworkType.WS.type, profile.network)
        assertEquals("/ws", profile.path)
        assertEquals("name", profile.remarks)
    }
}
