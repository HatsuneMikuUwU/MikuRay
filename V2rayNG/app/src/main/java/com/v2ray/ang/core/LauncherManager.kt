package com.v2ray.ang.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.snackbarDefault
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreRootService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils

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

    /**
     * Starts the V2Ray service from a toggle action.
     * @param context The context from which the service is started.
     * @return True if the service was started successfully, false otherwise.
     */
    fun startServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            showFeedback(context, context.getString(R.string.app_tile_first_use), 2)
            return false
        }
        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            showFeedback(context, e.message ?: e.javaClass.simpleName, 2)
            return false
        }
        return true
    }

    /**
     * Starts the V2Ray service.
     * @param context The context from which the service is started.
     * @param guid The GUID of the server configuration to use (optional).
     */
    fun startService(context: Context, guid: String? = null) {
        LogUtil.i(AppConfig.TAG, "LauncherManager: startService from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            showFeedback(context, e.message ?: e.javaClass.simpleName, 2)
        }
    }

    /**
     * Stops the V2Ray service.
     * @param context The context from which the service is stopped.
     */
    fun stopService(context: Context) {
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    /**
     * Starts the context service for V2Ray.
     * Chooses between VPN service or Proxy-only service based on user settings.
     * @param context The context from which the service is started.
     * @throws IllegalStateException if no server is selected,
     *   server config cannot be decoded, or server configuration is invalid.
     * @throws Exception if the foreground service fails to start.
     *
     * Note: the isRunning check is intentionally not performed here, to avoid loading
     * native libraries in the UI process. That check happens in CoreServiceManager once
     * the service actually starts in the daemon process.
     */
    @Throws(Exception::class)
    private fun startContextService(context: Context) {
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

        // refresh socks port when enabled dynamic socks port
        SettingsManager.refreshRuntimeSocksPort()

        if (config.insecure == true && config.pinnedCA256.isNullOrEmpty()) {
            context.snackbarError(
                context.getString(R.string.toast_allow_insecure_deprecated),
                title = context.getString(R.string.title_alerter_error)
            )
            Utils.setClipboard(context, context.getString(R.string.toast_allow_insecure_deprecated))
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
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
