package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.MikuRayExportPayload
import com.v2ray.ang.dto.entities.MikuRayExportedProfile
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.MikuRayFileCrypto
import com.v2ray.ang.util.Utils
import java.io.File

/**
 * Builds/encrypts .mikuray export payloads and restores them back into MMKV storage on import.
 * Pairs with [MikuRayFileCrypto] for the actual AES-256-GCM work.
 */
object MikuRayGroupFileManager {

    const val FILE_EXTENSION = ".mikuray"

    /** Gathers every server in [subscriptionId] into an exportable payload, or null if empty. */
    fun buildGroupExportPayload(subscriptionId: String, groupName: String): MikuRayExportPayload? {
        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isEmpty()) return null

        val profiles = guids.mapNotNull { buildExportedProfile(it) }
        if (profiles.isEmpty()) return null

        // The default/ungrouped "group" has no SubscriptionItem entry of its own, so this can
        // legitimately be null - importGroupPayload() falls back to bare defaults in that case.
        val groupSettings = MmkvManager.decodeSubscription(subId)?.copy(remarks = groupName)

        return MikuRayExportPayload(
            type = MikuRayExportPayload.TYPE_GROUP,
            name = groupName,
            groupSettings = groupSettings,
            profiles = profiles
        )
    }

    /** Wraps a single profile into an exportable payload, or null if the guid doesn't resolve. */
    fun buildProfileExportPayload(guid: String): MikuRayExportPayload? {
        val exported = buildExportedProfile(guid) ?: return null
        return MikuRayExportPayload(
            type = MikuRayExportPayload.TYPE_PROFILE,
            name = exported.profile.remarks,
            profiles = listOf(exported)
        )
    }

    private fun buildExportedProfile(guid: String): MikuRayExportedProfile? {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        val raw = if (profile.configType == EConfigType.CUSTOM) MmkvManager.decodeServerRaw(guid) else null
        return MikuRayExportedProfile(profile = profile, raw = raw)
    }

    /** Serializes + encrypts [payload] with [password], writes it to the cache dir, returns the file. */
    fun encryptPayloadToFile(context: Context, payload: MikuRayExportPayload, password: String, fileNamePrefix: String): File {
        val json = JsonUtil.toJson(payload)
        val encrypted = MikuRayFileCrypto.encrypt(json, password)
        val safeName = sanitizeFileName(fileNamePrefix)
        val file = File(context.cacheDir, "$safeName$FILE_EXTENSION")
        file.writeBytes(encrypted)
        return file
    }

    /** Decrypts raw .mikuray file bytes with [password] and parses the resulting JSON payload. */
    fun decryptPayloadFromFile(bytes: ByteArray, password: String): MikuRayExportPayload {
        val json = MikuRayFileCrypto.decrypt(bytes, password)
        return JsonUtil.fromJsonSafe(json, MikuRayExportPayload::class.java)
            ?: throw MikuRayFileCrypto.MikuRayCryptoException("Malformed .mikuray payload")
    }

    /**
     * Restores a decrypted [payload] into storage.
     * - "group" payloads spawn a brand new subscription group named after [MikuRayExportPayload.name].
     * - "profile" payloads land in [targetSubscriptionId] (typically whatever group is open in the UI).
     * Returns how many profiles were imported.
     */
    fun importPayload(payload: MikuRayExportPayload, targetSubscriptionId: String): Int {
        val subId = if (payload.type == MikuRayExportPayload.TYPE_GROUP) {
            val newSubId = Utils.getUuid()
            // Restore every group-level setting that came with the export (subscription URL,
            // auto-update, filters, tab icon, ...), not just the display name. Time metadata is
            // reset since this is effectively a brand new group as far as this device is concerned.
            val restoredSettings = (payload.groupSettings ?: SubscriptionItem())
                .copy(
                    remarks = payload.name,
                    addedTime = System.currentTimeMillis(),
                    lastUpdated = -1
                )
            MmkvManager.encodeSubscription(newSubId, restoredSettings)
            newSubId
        } else {
            targetSubscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        }

        var count = 0
        payload.profiles.forEach { exported ->
            val profile = exported.profile.copy(subscriptionId = subId)
            val newGuid = MmkvManager.encodeServerConfig("", profile)
            if (profile.configType == EConfigType.CUSTOM && !exported.raw.isNullOrBlank()) {
                MmkvManager.encodeServerRaw(newGuid, exported.raw)
            }
            count++
        }
        return count
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.trim().ifEmpty { "MikuRay" }
        return cleaned.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
    }
}
