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

    @Test
    fun `repeated subscription updates keep one copy of an imported group`() {
        val stored = mutableMapOf<String, ProfileItem>()
        var index = listOf<String>()

        repeat(3) { update ->
            val batch = linkedMapOf(
                "node-$update" to profile(remarks = "node", server = "host"),
                "group-$update" to group(EConfigType.POLICYGROUP, "Auto"),
            )
            val kept = ProfileReplacement.findKeptGroups(
                replacedProfiles = index.associateWith(stored::get),
                profiles = batch.values,
            )
            stored.putAll(batch)
            index = batch.keys.reversed() + kept
        }

        assertEquals(listOf("group-2", "node-2"), index)
    }

    @Test
    fun `keeps hand-made groups and drops groups imported again`() {
        val replaced = linkedMapOf(
            "imported" to group(EConfigType.POLICYGROUP, "Auto"),
            "earlier-copy" to group(EConfigType.POLICYGROUP, "Auto"),
            "hand-made" to group(EConfigType.POLICYGROUP, "Mine"),
            "hand-made-chain" to group(EConfigType.PROXYCHAIN, "Auto"),
            "node" to profile(remarks = "node"),
            "unreadable" to null,
        )

        val result = ProfileReplacement.findKeptGroups(
            replacedProfiles = replaced,
            profiles = listOf(profile(remarks = "node"), group(EConfigType.POLICYGROUP, "Auto")),
        )

        assertEquals(listOf("hand-made", "hand-made-chain"), result)
    }

    private fun group(type: EConfigType, remarks: String) = ProfileItem.create(type).apply {
        this.remarks = remarks
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
