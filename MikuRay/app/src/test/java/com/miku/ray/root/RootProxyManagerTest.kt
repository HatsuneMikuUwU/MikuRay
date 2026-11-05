package com.miku.ray.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootProxyManagerTest {
    @Test
    fun ipv6LanForwardingDisablesJumboAndTso() {
        val offload = RootProxyManager.buildHevOffload(ipv6 = true, lanSharing = true)

        assertTrue(offload.contains("  offload:\n    jumbo: false\n"))
        assertTrue(offload.contains("    tso: false\n"))
        assertFalse(offload.contains("uso:"))
    }

    @Test
    fun ipv4LanForwardingDisablesJumboOnly() {
        val offload = RootProxyManager.buildHevOffload(ipv6 = false, lanSharing = true)

        assertTrue(offload.contains("  offload:\n    jumbo: false\n"))
        assertFalse(offload.contains("tso:"))
    }

    @Test
    fun localRootKeepsDefaultOffloads() {
        assertEquals("", RootProxyManager.buildHevOffload(ipv6 = true, lanSharing = false))
    }

    @Test
    fun yamlScalarEscapesSingleQuotes() {
        assertEquals("'user''name'", "user'name".toSingleQuotedYamlScalar())
        assertEquals("'p@ss''word'", "p@ss'word".toSingleQuotedYamlScalar())
    }
}
