package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem

/**
 * Pure matching logic used when a batch of new profiles replaces an existing group
 * (subscription update, batch import, custom config import, etc.).
 *
 * Kept storage-free on purpose: [MmkvManager.saveServerProfiles] owns the actual
 * read/write ordering, this object only decides *what* should happen.
 */
internal object ProfileReplacement {

    /**
     * Finds the profile that should become selected after publishing a replacement batch.
     * The first profile becomes selected when the store has no current selection.
     */
    fun findSelectedReplacement(
        profiles: Map<String, ProfileItem>,
        currentSelection: String?,
        selectedProfile: ProfileItem?,
    ): String? {
        if (profiles.isEmpty()) return null
        if (currentSelection.isNullOrBlank()) return profiles.keys.first()
        if (selectedProfile == null) return null

        if (selectedProfile.remarks.isNotBlank()) {
            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks) &&
                        isSameText(candidate.server, selectedProfile.server) &&
                        isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                        isSameText(candidate.password, selectedProfile.password)
            }?.key?.let { return it }

            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks)
            }?.key?.let { return it }
        }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                    isSameText(candidate.password, selectedProfile.password)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server)
        }?.key?.let { return it }

        return profiles.keys.firstOrNull()
    }

    /**
     * Finds replaced payloads that are safe to remove once the replacement batch is
     * already published.
     *
     * @param replacedServers GUIDs that were indexed under the group before this replacement.
     * @param replacementServers GUIDs of the newly published batch.
     * @param protectedServers GUIDs that are never removed: the currently selected server
     * (new or old) plus any pinned server. Pinned servers survive subscription updates the
     * same way the selected server does.
     */
    fun findRemovablePayloads(
        replacedServers: Collection<String>,
        replacementServers: Set<String>,
        protectedServers: Set<String>,
    ): Set<String> {
        return replacedServers.filterTo(linkedSetOf()) { guid ->
            guid !in protectedServers && guid !in replacementServers
        }
    }

    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }
}
