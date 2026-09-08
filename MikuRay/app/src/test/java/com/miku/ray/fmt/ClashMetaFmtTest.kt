package com.miku.ray.fmt

import com.miku.ray.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashMetaFmtTest {
    @Test
    fun parsesCommonClashProxyTypes() {
        val yaml = """
            proxies:
              - name: vless-reality
                type: vless
                server: vless.example.com
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                tls: true
                servername: www.example.com
                reality-opts:
                  public-key: public-key
                  short-id: short-id
              - name: vmess-ws
                type: vmess
                server: vmess.example.com
                port: 443
                uuid: 22222222-2222-2222-2222-222222222222
                cipher: auto
                tls: true
                network: ws
                ws-opts:
                  path: /ray
                  headers:
                    Host: cdn.example.com
              - name: shadowsocks
                type: ss
                server: ss.example.com
                port: 8388
                cipher: aes-128-gcm
                password: secret
              - name: socks
                type: socks5
                server: socks.example.com
                port: 1080
                username: user
                password: pass
              - name: unsupported
                type: tuic
                server: tuic.example.com
                port: 443
        """.trimIndent()

        val result = ClashMetaFmt.parse(yaml, "sub-1")

        assertEquals(4, result.size)
        assertEquals(listOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.SHADOWSOCKS, EConfigType.SOCKS), result.map { it.configType })
        assertEquals("sub-1", result.first().subscriptionId)
        assertEquals("public-key", result.first().publicKey)
        assertEquals("cdn.example.com", result[1].host)
        assertEquals("secret", result[2].password)
        assertTrue(result.none { it.remarks == "unsupported" })
    }

    @Test
    fun malformedOrNonClashYamlReturnsEmpty() {
        assertTrue(ClashMetaFmt.parse("proxies: [", "sub-1").isEmpty())
        assertTrue(ClashMetaFmt.parse("port: 443\nserver: example.com", "sub-1").isEmpty())
    }
}
