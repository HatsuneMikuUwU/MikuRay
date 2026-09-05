package com.miku.ray.ui.routing

import androidx.lifecycle.ViewModel
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager

class RoutingSettingsViewModel : ViewModel() {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        val loaded = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
        // Repair legacy/imported IDs in one write before exposing them as RecyclerView keys.
        if (SettingsManager.ensureRoutingRulesetIds(loaded)) {
            MmkvManager.encodeRoutingRulesets(loaded)
        }
        rulesets.clear()
        rulesets.addAll(loaded)
    }

    fun update(position: Int, item: RulesetItem) {
        if (position !in rulesets.indices) return
        rulesets[position] = item
        SettingsManager.saveRoutingRuleset(position, item)
    }

    fun remove(position: Int) {
        if (position !in rulesets.indices) return
        rulesets.removeAt(position)
        SettingsManager.removeRoutingRuleset(position)
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            val item = rulesets.removeAt(fromPosition)
            rulesets.add(toPosition, item)
            SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
        }
    }
}
