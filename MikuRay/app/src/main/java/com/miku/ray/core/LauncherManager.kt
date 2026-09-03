package com.miku.ray.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.toastError
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.root.RootManager
import com.miku.ray.service.CoreProxyOnlyService
import com.miku.ray.service.CoreRootService
import com.miku.ray.service.CoreVpnService
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils

object LauncherManager {

    private fun showFeedback(context: Context, message: String, type: Int = 0) {
        if (context is Activity) {
            when (type) {
                1 -> context.snackbarDefault(
                    message,
                    title = context.getString(R.string.title_alerter_success)
                )
                2 -> context.snackbarError(
                    message,
                    title = context.getString(R.string.title_alerter_error)
                )
                else -> context.snackbarDefault(
                    message,
                    title = context.getString(R.string.title_alerter_info)
                )
            }
        } else {
            context.snackbarDefault(message, title = context.getString(R.string.title_alerter_info))
        }
    }

    fun startServiceFromToggle(context: Context): Boolean =
        requestServiceStart(context, guid = null, showLifecycleFeedback = true)

    fun startService(context: Context, guid: String? = null) {
        requestServiceStart(context, guid, showLifecycleFeedback = true)
    }

    /** Starts a replacement service after a daemon-managed restart. */
    internal fun startServiceAfterRestart(context: Context): Boolean =
        requestServiceStart(context, guid = null, showLifecycleFeedback = false)

    private fun requestServiceStart(
        context: Context,
        guid: String?,
        showLifecycleFeedback: Boolean,
    ): Boolean {
        LogUtil.i(AppConfig.TAG, "LauncherManager: startService from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        try {
            startContextService(context, showLifecycleFeedback)
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            val message = e.message ?: e.javaClass.simpleName
            if (showLifecycleFeedback) {
                showFeedback(context, message, 2)
                MessageUtil.sendMsg2UI(context, AppConfig.MSG_STATE_START_FAILURE, message)
            }
            return false
        }
    }

    fun stopService(context: Context) {
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    /** Restarts the active daemon without starting a stopped service. */
    fun restartService(context: Context) {
        restartService(context) { }
    }

    /** Restarts the active daemon and reports whether a daemon accepted the request. */
    fun restartService(context: Context, onResult: (handled: Boolean) -> Unit) {
        MessageUtil.sendMsg2ServiceForResult(
            context,
            AppConfig.MSG_STATE_RESTART,
            "",
            onResult,
        )
    }

    /** Restarts the active daemon, or delegates to the caller's permission-aware start flow. */
    fun restartServiceOrStart(context: Context, startIfStopped: () -> Unit) {
        restartService(context) { handled ->
            if (!handled) startIfStopped()
        }
    }

    @Throws(Exception::class)
    private fun startContextService(context: Context, showLifecycleFeedback: Boolean) {
        val guid = MmkvManager.getSelectServer()
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: No server selected")
                error(context.getString(R.string.app_tile_first_use))
            }

        val config = MmkvManager.decodeServerConfig(guid)
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to decode server config")
                error(context.getString(R.string.toast_config_file_invalid))
            }

        if (!config.configType.isComplexType()
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        SettingsManager.refreshRuntimeSocksPort()

        if (config.insecure == true && config.pinnedCA256.isNullOrEmpty()) {
            context.toastError(context.getString(R.string.toast_allow_insecure_deprecated))
            Utils.setClipboard(context, context.getString(R.string.toast_allow_insecure_deprecated))
        }

        if (showLifecycleFeedback && MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            showFeedback(context, context.getString(R.string.toast_warning_pref_proxysharing_short), 0)
        }

        val isRootMode = SettingsManager.isRootMode()
        if (isRootMode && !RootManager.isRootAvailable()) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: root mode requires root but none available")
            error(context.getString(R.string.toast_root_required))
        }

        val intent = if (isRootMode) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Root service")
            Intent(context.applicationContext, CoreRootService::class.java)
        } else if (SettingsManager.isVpnMode()) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }
}
