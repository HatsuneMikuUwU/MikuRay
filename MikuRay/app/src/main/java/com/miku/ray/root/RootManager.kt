package com.miku.ray.root

import com.miku.ray.AppConfig
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootManager {

    @Volatile
    private var cached: Boolean? = null

    fun cachedRoot(): Boolean = cached ?: false

    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) cached?.let { return it }
        val result = probe()
        cached = result
        return result
    }

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val result = probe()
        cached = result
        result
    }

    private fun probe(): Boolean {
        return try {
            val result = RootProcessRunner.run(listOf("su", "-c", "id -u"), 10000, 1024)
            if (result.code == -1) {
                LogUtil.w(AppConfig.TAG, "RootManager: su probe timed out")
                return false
            }
            val isRoot = result.code == 0 && result.output.lineSequence().filter { it.isNotBlank() }.lastOrNull()?.trim() == "0"
            LogUtil.i(AppConfig.TAG, "RootManager: root available = $isRoot")
            isRoot
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            LogUtil.w(AppConfig.TAG, "RootManager: root probe failed", e)
            false
        }
    }
}
