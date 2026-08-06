package com.v2ray.ang.handler

import kotlinx.coroutines.flow.MutableStateFlow

object SettingsChangeManager {
    private val _restartService = MutableStateFlow(false)
    private val _setupGroupTab = MutableStateFlow(false)
    private val _refreshDisplayPrefs = MutableStateFlow(false)

    // Mark restartService as requiring a restart
    fun makeRestartService() {
        _restartService.value = true
    }

    // Read and clear the restartService flag
    fun consumeRestartService(): Boolean {
        val v = _restartService.value
        _restartService.value = false
        return v
    }

    // Mark reinitGroupTab as requiring tab reinitialization
    fun makeSetupGroupTab() {
        _setupGroupTab.value = true
    }

    // Read and clear the reinitGroupTab flag
    fun consumeSetupGroupTab(): Boolean {
        val v = _setupGroupTab.value
        _setupGroupTab.value = false
        return v
    }

    // Mark that per-item display prefs changed (traffic/sensor-text/network-security toggles)
    // and every already-created group list needs a rebind — without a full tab/service reset.
    fun makeRefreshDisplayPrefs() {
        _refreshDisplayPrefs.value = true
    }

    // Read and clear the refreshDisplayPrefs flag
    fun consumeRefreshDisplayPrefs(): Boolean {
        val v = _refreshDisplayPrefs.value
        _refreshDisplayPrefs.value = false
        return v
    }
}
