package com.miku.ray.handler

import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileReplacementTest {

    @Test
    fun `prefers a full match over a remarks-only match`() {
        val profiles = linkedMapOf(
            "remarks" to profile(remarks = "selected", server = "other"),
            "full" to profile(remarks = "selected", server = "host", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(
                remarks = " selected ",
                server = "HOST",
                port = "443",
                password = "secret",
            ),
        )

        assertEquals("full", result)
    }

    @Test
    fun `uses a remarks match when no full match exists`() {
        val profiles = linkedMapOf(
            "other" to profile(remarks = "other", server = "host"),
            "remarks" to profile(remarks = "selected", server = "other"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(remarks = "selected", server = "host"),
        )

        assertEquals("remarks", result)
    }

    @Test
    fun `matches endpoint and password when remarks are unavailable`() {
        val profiles = linkedMapOf(
            "endpoint" to profile(server = "host", port = "443", password = "secret"),
            "other" to profile(server = "other", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "HOST", port = "443", password = "secret"),
        )

        assertEquals("endpoint", result)
    }

    @Test
    fun `falls back to the first replacement profile`() {
        val profiles = linkedMapOf(
            "first" to profile(server = "first"),
            "second" to profile(server = "second"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "unmatched"),
        )

        assertEquals("first", result)
    }

    @Test
    fun `selects the first profile when there is no current selection`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = linkedMapOf(
                "first" to profile(server = "first"),
                "second" to profile(server = "second"),
            ),
            currentSelection = null,
            selectedProfile = null,
        )

        assertEquals("first", result)
    }

    @Test
    fun `keeps an existing selection when it is outside the replaced group`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = mapOf("candidate" to profile(server = "host")),
            currentSelection = "other-group",
            selectedProfile = null,
        )

        assertNull(result)
    }

    @Test
    fun `removes only superseded payloads, keeping the protected and replacement ones`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("orphan", "selected", "replacement"),
            replacementServers = setOf("replacement"),
            protectedServers = setOf("selected"),
        )

        assertEquals(setOf("orphan"), result)
    }

    @Test
    fun `keeps everything when nothing was previously indexed`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = emptyList(),
            replacementServers = emptySet(),
            protectedServers = emptySet(),
        )

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `keeps pinned servers even when they are not the selected server`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("orphan", "pinned", "selected", "replacement"),
            replacementServers = setOf("replacement"),
            protectedServers = setOf("selected", "pinned"),
        )

        assertEquals(setOf("orphan"), result)
    }

    private fun profile(
        remarks: String = "",
        server: String = "",
        port: String = "",
        password: String = "",
    ) = ProfileItem.create(EConfigType.VMESS).apply {
        this.remarks = remarks
        this.server = server
        this.serverPort = port
        this.password = password
    }
}
