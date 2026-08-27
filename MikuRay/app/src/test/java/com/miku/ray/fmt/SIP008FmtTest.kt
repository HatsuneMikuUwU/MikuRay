package com.miku.ray.fmt

import com.miku.ray.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SIP008FmtTest {

    @Test
    fun parse_acceptsStandardSIP008ShadowsocksProfiles() {
        val content = """
            {
              "version": 1,
              "servers": [
                {
                  "id": "standard-aead",
                  "remarks": "SIP008 AEAD",
                  "server": "ss.example.com",
                  "server_port": 8388,
                  "method": "aes-256-gcm",
                  "password": "super-secret"
                },
                {
                  "id": "normalized-method",
                  "server": "198.51.100.20",
                  "server_port": 443,
                  "method": "aead-chacha20-poly1305",
                  "password": "another-secret"
                }
              ]
            }
        """.trimIndent()

        val profiles = SIP008Fmt.parse(content)

        assertEquals(2, profiles.size)
        assertEquals(EConfigType.SHADOWSOCKS, profiles[0].configType)
        assertEquals("SIP008 AEAD", profiles[0].remarks)
        assertEquals("ss.example.com", profiles[0].server)
        assertEquals("8388", profiles[0].serverPort)
        assertEquals("aes-256-gcm", profiles[0].method)
        assertEquals("chacha20-ietf-poly1305", profiles[1].method)
        assertEquals("198.51.100.20:443", profiles[1].remarks)
    }

    @Test
    fun parse_mapsSupportedSimpleObfsHttpPlugin() {
        val content = """
            {
              "version": 1,
              "servers": [
                {
                  "server": "obfs.example.com",
                  "server_port": 443,
                  "method": "aes-128-gcm",
                  "password": "secret",
                  "plugin": "simple-obfs",
                  "plugin_opts": "obfs=http;obfs-host=cdn.example.com;path=/health"
                }
              ]
            }
        """.trimIndent()

        val profile = SIP008Fmt.parse(content).single()

        assertEquals("tcp", profile.network)
        assertEquals("http", profile.headerType)
        assertEquals("cdn.example.com", profile.host)
        assertEquals("/health", profile.path)
    }

    @Test
    fun parse_skipsUnsupportedRuntimeConfigurations() {
        val content = """
            {
              "version": 1,
              "servers": [
                {
                  "server": "plugin.example.com",
                  "server_port": 8388,
                  "method": "aes-256-gcm",
                  "password": "secret",
                  "plugin": "v2ray-plugin"
                },
                {
                  "server": "legacy.example.com",
                  "server_port": 8388,
                  "method": "rc4-md5",
                  "password": "secret"
                },
                {
                  "server": "invalid-port.example.com",
                  "server_port": 0,
                  "method": "aes-256-gcm",
                  "password": "secret"
                }
              ]
            }
        """.trimIndent()

        assertTrue(SIP008Fmt.parse(content).isEmpty())
    }

    @Test
    fun parse_rejectsNonSIP008Documents() {
        assertTrue(SIP008Fmt.parse("{\"version\": 2, \"servers\": []}").isEmpty())
        assertTrue(SIP008Fmt.parse("{\"servers\": []}").isEmpty())
        assertTrue(SIP008Fmt.parse("not-json").isEmpty())
    }
}
