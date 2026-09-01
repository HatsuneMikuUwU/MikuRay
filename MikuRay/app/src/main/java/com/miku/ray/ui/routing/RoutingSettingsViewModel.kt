package com.miku.ray.ui.routing

import androidx.lifecycle.ViewModel
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager

class RoutingSettingsViewModel : ViewModel() {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        rulesets.clear()
        rulesets.addAll(MmkvManager.decodeRoutingRulesets() ?: mutableListOf())
    }

    fun update(position: Int, item: RulesetItem) {
        if (position !in rulesets.indices) return
        val current = rulesets[position]
        val targetIndex = rulesets.indexOfFirst { it.id == current.id }
        if (targetIndex >= 0) {
            rulesets[targetIndex] = item
            SettingsManager.saveRoutingRuleset(current.id, item)
        }
    }

    fun remove(id: String) {
        if (id.isBlank()) return
        val targetIndex = rulesets.indexOfFirst { it.id == id }
        if (targetIndex >= 0) {
            rulesets.removeAt(targetIndex)
            SettingsManager.removeRoutingRuleset(id)
        }
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
        }
    }
}

