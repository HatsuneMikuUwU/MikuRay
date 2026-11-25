package com.miku.ray.handler

import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.extension.isGroupType

internal object ProfileReplacement {

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

    fun findRemovablePayloads(
        replacedServers: Collection<String>,
        replacementServers: Set<String>,
        protectedServers: Set<String>,
    ): Set<String> {
        return replacedServers.filterTo(linkedSetOf()) { guid ->
            guid !in protectedServers && guid !in replacementServers
        }
    }

    /**
     * Finds the replaced group profiles that stay in the replaced group index.
     *
     * Groups are kept so that groups created by hand survive a subscription update. A group
     * that the batch imports again (same type and remarks) is dropped: share links such as
     * v2rayn:// create it with a new GUID on every update, so keeping it would add a copy.
     */
    fun findKeptGroups(
        replacedProfiles: Map<String, ProfileItem?>,
        profiles: Collection<ProfileItem>,
    ): List<String> {
        val importedGroups = profiles
            .filter { it.configType.isGroupType() }
            .mapTo(hashSetOf()) { it.configType to it.remarks }

        return replacedProfiles.filter { (_, profile) ->
            profile != null &&
                profile.configType.isGroupType() &&
                (profile.configType to profile.remarks) !in importedGroups
        }.keys.toList()
    }

    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }
}
