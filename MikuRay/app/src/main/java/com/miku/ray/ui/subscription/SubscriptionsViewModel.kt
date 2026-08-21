package com.miku.ray.ui.subscription

import androidx.lifecycle.ViewModel
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.dto.SubscriptionUpdateMessage
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.ui.bottomsheet.SortSubBottomSheet
import com.miku.ray.util.MessageUtil

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

