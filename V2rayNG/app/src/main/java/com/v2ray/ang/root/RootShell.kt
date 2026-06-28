package com.v2ray.ang.root

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * Minimal helper for running shell commands as root via `su -c`.
 * Each call forks a new `su` process; callers are responsible for batching
 * commands when low overhead matters.
 */
object RootShell {

    /**
     * Runs [cmd] as root. Returns true if `su` exited with code 0.
     * Stderr is merged into stdout for logging.
     */
    fun run(cmd: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exit = process.waitFor()
            if (exit != 0 && output.isNotEmpty()) {
                LogUtil.w(AppConfig.TAG, "RootShell: [$cmd] exit=$exit out=$output")
            }
            exit == 0
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell: failed to run [$cmd]", e)
            false
        }
    }

    /**
     * Runs multiple commands joined with " && " so the chain aborts on first failure.
     * Returns true only if all commands succeeded.
     */
    fun runAll(vararg cmds: String): Boolean = run(cmds.joinToString(" && "))

    /**
     * Runs [cmd] as root and returns the trimmed stdout output, or null on failure.
     */
    fun output(cmd: String): String? {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(false)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            out.ifEmpty { null }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell: output failed for [$cmd]", e)
            null
        }
    }

    /**
     * Runs [cmd] silently, ignoring errors. Useful for cleanup commands that may fail
     * because a rule/chain/device doesn't exist yet.
     */
    fun runSilent(cmd: String) {
        try {
            ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (_: Exception) {
        }
    }
}
