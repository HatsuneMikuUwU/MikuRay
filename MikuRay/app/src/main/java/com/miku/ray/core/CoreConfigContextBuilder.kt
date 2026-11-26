package com.miku.ray.core

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.dto.CoreConfigContext
import com.miku.ray.dto.entities.ProfileEntry
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.BalancerStrategyType
import com.miku.ray.enums.CoreResolvedType
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

object CoreConfigContextBuilder {

    fun build(context: Context, guid: String): CoreConfigContext? {
        val config = MmkvManager.decodeServerConfig(guid) ?: return null

        if (config.configType == EConfigType.CUSTOM) {
            return CoreConfigContext(context = context, guid = guid, isCustom = true)
        }

        val primaryResolvedOutbound = resolveOutbound(AppConfig.TAG_PROXY, ProfileEntry(guid, config)) ?: run {
            LogUtil.e(AppConfig.TAG, "Failed to resolve main outbound for '${config.remarks}'")
            return null
        }

        val routingResolvedOutbounds = resolveRoutingOutbounds()
        val resolvedOutbounds = listOf(primaryResolvedOutbound) + routingResolvedOutbounds
        val fallbackResolvedOutbounds = resolveFallbackOutbounds(resolvedOutbounds)
        val routingDomainRules = collectRoutingDomainRulesForDns()
        val routingRegion = calculateRoutingRegionForDns()

        return CoreConfigContext(
            context = context,
            guid = guid,
            routingRegion = routingRegion,
            resolvedOutbounds = resolvedOutbounds + fallbackResolvedOutbounds,
            routingDomainRules = routingDomainRules,
        )
    }

    private fun resolveOutbound(tag: String, entry: ProfileEntry): CoreConfigContext.ResolvedOutbound? {
        if (entry.profile.configType == EConfigType.CUSTOM) {
            return null
        }

        val (resolvedEntries, resolvedType) = when (entry.profile.configType) {
            EConfigType.POLICYGROUP -> {
                val members = resolvePolicyGroupMembers(entry.profile)
                Pair(
                    members ?: listOf(entry),
                    CoreResolvedType.POLICYGROUP,
                )
            }

            EConfigType.PROXYCHAIN -> {
                val chainEntries = resolveProxyChainEntries(entry)
                val type = if (chainEntries.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainEntries, type)
            }

            else -> {
                val chainEntries = resolveProxyChainEntriesFromGroup(entry)
                val type = if (chainEntries.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainEntries, type)
            }
        }

        return CoreConfigContext.ResolvedOutbound(
            tag = tag,
            entry = entry,
            resolvedEntries = resolvedEntries,
            resolvedType = resolvedType,
        )
    }

    private fun resolveRoutingOutbounds(): List<CoreConfigContext.ResolvedOutbound> {
        val rulesetItems = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        val resolvedOutbounds = mutableListOf<CoreConfigContext.ResolvedOutbound>()
        val processedTags = mutableSetOf<String>()

        try {
            rulesetItems
            .filter { it.enabled }
            .mapNotNull { it.outboundTag.takeIf { tag -> tag.isNotBlank() } }
            .filter { tag -> tag !in AppConfig.BUILTIN_OUTBOUND_TAGS }
            .distinct()
            .forEach { tag ->
                if (tag in processedTags) {
                    return@forEach
                }
                processedTags.add(tag)

                try {
                    val entry = SettingsManager.getProfileEntryViaRemarks(tag) ?: run {
                        LogUtil.w(AppConfig.TAG, "Routing tag '$tag' has no matching profile — will fall back to proxy at routing time")
                        return@forEach
                    }
                    val resolvedOutbound = resolveOutbound(tag, entry) ?: run {
                        LogUtil.w(AppConfig.TAG, "Cannot use CUSTOM profile as routing outbound for tag '$tag', skipping")
                        return@forEach
                    }
                    if (resolvedOutbound.resolvedEntries.isEmpty()) {
                        LogUtil.w(AppConfig.TAG, "Routing outbound '$tag' resolved to empty list, skipping")
                        return@forEach
                    }
                    resolvedOutbounds.add(resolvedOutbound)
                    LogUtil.d(AppConfig.TAG, "Resolved routing outbound: tag='$tag', type='${resolvedOutbound.resolvedType}', profiles=${resolvedOutbound.resolvedEntries.size}")
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbound for tag '$tag', skipping", e)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbounds from rulesets", e)
        }

        return resolvedOutbounds
    }

    internal fun resolvePolicyGroupGuids(config: ProfileItem): List<String> =
    resolvePolicyGroupMembers(config)?.map { it.guid }?.distinct().orEmpty()

    private fun resolvePolicyGroupMembers(config: ProfileItem): List<ProfileEntry>? {
        try {
            val serverList = MmkvManager.decodeAllServerList()
            return serverList
            .asSequence()
            .mapNotNull { guid ->
                MmkvManager.decodeServerConfig(guid)?.let { profile -> ProfileEntry(guid, profile) }
            }
            .filter {
                val subscriptionId = config.policyGroupSubscriptionId
                if (subscriptionId.isNullOrBlank()) {
                    true
                } else {
                    it.profile.subscriptionId == subscriptionId
                }
            }
            .filter {
                val filter = config.policyGroupFilter
                if (filter.isNullOrBlank()) {
                    true
                } else {
                    try {
                        Regex(filter).containsMatchIn(it.profile.remarks)
                    } catch (_: Exception) {
                        it.profile.remarks.contains(filter)
                    }
                }
            }
            .filter { it.profile.server.isNotNullEmpty() }
            .filter {
                Utils.isPureIpAddress(it.profile.server!!) || Utils.isValidUrl(it.profile.server!!)
            }
            .filter { !it.profile.configType.isComplexType() }
            .toList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve policy group profiles for '${config.remarks}'", e)
            return null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveProxyChainEntries(entry: ProfileEntry): List<ProfileEntry> {
        val chainRemarks = entry.profile.proxyChainProfileList
            ?.takeIf { it.isNotEmpty() }
            ?: entry.profile.proxyChainProfiles
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
        if (chainRemarks.isNullOrEmpty()) {
            return listOf(entry)
        }

        try {
            return chainRemarks
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { remark -> SettingsManager.getProfileEntryViaRemarks(remark) }
            .filter { it.profile.server.isNotNullEmpty() }
            .filter { Utils.isPureIpAddress(it.profile.server!!) || Utils.isValidUrl(it.profile.server!!) }
            .filter { !it.profile.configType.isComplexType() }
            .toList()
            .reversed()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain profiles for '${entry.profile.remarks}'", e)
            return listOf(entry)
        }
    }

    private fun resolveProxyChainEntriesFromGroup(entry: ProfileEntry): List<ProfileEntry> {
        if (entry.profile.subscriptionId.isEmpty()) {
            return listOf(entry)
        }

        try {
            val subItem = MmkvManager.decodeSubscription(entry.profile.subscriptionId) ?: return listOf(entry)
            val resolved = mutableListOf<ProfileEntry>()
            SettingsManager.getProfileEntryViaRemarks(subItem.nextProfile)?.let { resolved.add(it) }
            resolved.add(entry)
            SettingsManager.getProfileEntryViaRemarks(subItem.prevProfile)?.let { resolved.add(it) }
            return resolved
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain from group for '${entry.profile.remarks}'", e)
            return listOf(entry)
        }
    }

    private fun collectRoutingDomainRulesForDns(): List<CoreConfigContext.RoutingDomainRule> {
        val rulesetItems = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        val result = mutableListOf<CoreConfigContext.RoutingDomainRule>()

        rulesetItems
        .asSequence()
        .filter { it.enabled }
        .filter { !it.domain.isNullOrEmpty() }
        .forEach { rule ->
            val normalizedOutboundTag = when (rule.outboundTag) {
                AppConfig.TAG_DIRECT -> AppConfig.TAG_DIRECT
                AppConfig.TAG_BLOCKED -> AppConfig.TAG_BLOCKED
                else -> AppConfig.TAG_PROXY
            }
            result.add(
                CoreConfigContext.RoutingDomainRule(
                    domain = rule.domain.orEmpty(),
                    outboundTag = normalizedOutboundTag
                )
            )
        }

        return result
    }

    private fun calculateRoutingRegionForDns(): String? {
        val rulesetItems = MmkvManager.decodeRoutingRulesets() ?: return null
        if (rulesetItems.size < 2) {
            return null
        }

        val lastTwo = rulesetItems.takeLast(2)
        val secondLastValue = lastTwo[0].ip
            ?.singleOrNull()
            ?.takeIf { it.startsWith("geoip:") }
            ?.substringAfter("geoip:")
            ?.takeIf { it.isNotBlank() }
        val lastValue = lastTwo[1].domain
            ?.singleOrNull()
            ?.takeIf { it.startsWith("geosite:") }
            ?.substringAfter("geosite:")
            ?.takeIf { it.isNotBlank() }

        return if (secondLastValue != null && secondLastValue == lastValue) {
            secondLastValue
        } else {
            null
        }
    }

    private fun resolveFallbackOutbounds(resolvedOutbounds: List<CoreConfigContext.ResolvedOutbound>): List<CoreConfigContext.ResolvedOutbound> {
        return resolvedOutbounds
        .asSequence()
        .filter { it.resolvedType == CoreResolvedType.POLICYGROUP }
        .filter { BalancerStrategyType.from(it.entry.profile.policyGroupType).supportsObservatory && it.entry.profile.policyGroupTestOutbounds != false }
        .mapNotNull { it.entry.profile.policyGroupFallbackTag }
        .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS && resolvedOutbounds.none { outbound -> outbound.tag == it } }
        .distinct()
        .mapNotNull { tag ->
            SettingsManager.getProfileEntryViaRemarks(tag)
            ?.takeUnless { it.profile.configType == EConfigType.CUSTOM || it.profile.configType == EConfigType.POLICYGROUP }
            ?.let { resolveOutbound(tag, it) }
        }
        .toList()
    }
}
