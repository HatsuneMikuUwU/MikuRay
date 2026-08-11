package com.v2ray.ang.dto.entities

/**
 * A single exported profile entry. [profile] mirrors the stored [ProfileItem] for the server,
 * while [raw] carries the raw JSON body for EConfigType.CUSTOM configs (stored separately in
 * MmkvManager's server-raw storage and not part of ProfileItem itself).
 */
data class MikuRayExportedProfile(
    val profile: ProfileItem,
    val raw: String? = null
)

/**
 * The plaintext payload that gets JSON-serialized and then AES-encrypted into a .mikuray file.
 *
 * [type] is either "group" or "profile":
 *  - "group": [name] is the subscription/group's remarks, [groupSettings] carries the rest of
 *    the group's config (subscription URL, auto-update, filters, tab icon, etc. — everything
 *    besides the server list itself), and [profiles] holds every server in it.
 *  - "profile": [name] is the single profile's remarks, [groupSettings] is null, [profiles]
 *    holds exactly one entry.
 */
data class MikuRayExportPayload(
    val formatVersion: Int = 1,
    val type: String,
    val name: String,
    val exportedTime: Long = System.currentTimeMillis(),
    val groupSettings: SubscriptionItem? = null,
    val profiles: List<MikuRayExportedProfile>
) {
    companion object {
        const val TYPE_GROUP = "group"
        const val TYPE_PROFILE = "profile"
    }
}
