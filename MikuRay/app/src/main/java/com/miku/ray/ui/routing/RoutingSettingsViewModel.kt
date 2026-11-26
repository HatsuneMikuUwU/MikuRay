package com.miku.ray.ui.routing

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingsViewModel : ViewModel() {
    private val _rulesets = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesets: StateFlow<List<RulesetItem>> = _rulesets.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun getAll(): List<RulesetItem> = _rulesets.value

    fun reload() {
        val loaded = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
        if (SettingsManager.ensureRoutingRulesetIds(loaded)) {
            MmkvManager.encodeRoutingRulesets(loaded)
        }
        _rulesets.value = loaded.toList()
    }

    fun update(position: Int, item: RulesetItem) {
        val current = _rulesets.value.toMutableList()
        if (position !in current.indices) return
        current[position] = item
        _rulesets.value = current
        SettingsManager.saveRoutingRuleset(position, item)
    }

    fun remove(position: Int) {
        val current = _rulesets.value.toMutableList()
        if (position !in current.indices) return
        current.removeAt(position)
        _rulesets.value = current
        SettingsManager.removeRoutingRuleset(position)
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        val current = _rulesets.value
        if (fromPosition !in current.indices || toPosition !in current.indices) return
        SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
    }

    fun importRulesets(content: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (content.isNullOrBlank()) {
                onResult(false)
                return@launch
            }

            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    var realContent = content.trim()
                    if (URLUtil.isNetworkUrl(realContent)) {
                        val fetched = HttpUtil.getUrlContent(UrlContentRequest(url = realContent, timeout = 10000))
                        if (fetched.isNullOrEmpty()) {
                            val proxyUsername = SettingsManager.getSocksUsername()
                            val proxyPassword = SettingsManager.getSocksPassword()
                            val httpPort = SettingsManager.getHttpPort()
                            realContent = HttpUtil.getUrlContent(
                                UrlContentRequest(
                                    url = realContent,
                                    timeout = 10000,
                                    httpPort = httpPort,
                                    proxyUsername = proxyUsername,
                                    proxyPassword = proxyPassword
                                )
                            ).orEmpty()
                        } else {
                            realContent = fetched
                        }
                    }

                    SettingsManager.resetRoutingRulesets(realContent)
                }

                if (result) {
                    reload()
                }
                onResult(result)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
