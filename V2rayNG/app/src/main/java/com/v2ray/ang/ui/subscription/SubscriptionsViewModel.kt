package com.v2ray.ang.ui.subscription

import androidx.lifecycle.ViewModel
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.bottomsheet.SortSubBottomSheet
import com.v2ray.ang.util.MessageUtil

class SubscriptionsViewModel : ViewModel() {
    private val subscriptions: MutableList<SubscriptionCache> =
        MmkvManager.decodeSubscriptions().toMutableList()

    init {
        applySortOrder()
    }

    fun getAll(): List<SubscriptionCache> = subscriptions.toList()

    fun reload() {
        applySortOrder()
    }

    fun applySortOrder() {
        // Always start from the persisted (origin) order, since sortByDescending
        // mutates the in-memory list in place and would otherwise permanently
        // lose the original order once a non-origin sort has been applied.
        val origin = MmkvManager.decodeSubscriptions()
        subscriptions.clear()
        subscriptions.addAll(
            SortSubBottomSheet.sorted(
                origin,
                addedTime = { it.subscription.addedTime },
                lastUpdated = { it.subscription.lastUpdated }
            )
        )
    }

    fun remove(subId: String): Boolean {
        val changed = subscriptions.removeAll { it.guid == subId }
        if (changed) {
            SettingsManager.removeSubscriptionWithDefault(subId)
            SettingsChangeManager.makeSetupGroupTab()
        }
        return changed
    }

    fun update(subId: String, item: SubscriptionItem) {
        val idx = subscriptions.indexOfFirst { it.guid == subId }
        if (idx >= 0) {
            subscriptions[idx] = SubscriptionCache(subId, item)
            MmkvManager.encodeSubscription(subId, item)
        }
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in subscriptions.indices && toPosition in subscriptions.indices) {
            val item = subscriptions.removeAt(fromPosition)
            subscriptions.add(toPosition, item)
        }
    }

    fun commitOrder() {
        SettingsManager.saveSubscriptionsOrder(subscriptions.map { it.guid })
        SettingsChangeManager.makeSetupGroupTab()
    }

    fun updateSubscriptionsMore() {
        val subIds = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.enabled && it.subscription.url.isNotEmpty() }
            .map { it.guid }
        if (subIds.isEmpty()) return

        MessageUtil.sendMsg2SubscriptionService(
            AngApplication.application,
            SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, false, subIds)
        )
    }

    fun updateSubscriptionsOnly(): SubscriptionUpdateResult {
        return AngConfigManager.updateConfigViaSubAll()
    }
}

