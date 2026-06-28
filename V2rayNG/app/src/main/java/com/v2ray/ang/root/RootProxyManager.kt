package com.v2ray.ang.root

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import java.io.File

/**
 * Manages the iptables/ip-rule/tun setup for Root mode.
 *
 * Flow:
 *  1. Copy the hev-socks5-tunnel binary from nativeLibraryDir and chmod +x it.
 *  2. Create a TUN device (v2raytun0) and bring it up.
 *  3. Add an ip rule + route table so marked packets use the tun.
 *  4. Install iptables mangle MARK chains that redirect per-app or all-apps traffic.
 *  5. Start hev-socks5-tunnel pointing at the core's SOCKS inbound.
 *
 * Teardown is the reverse: kill hev process, flush iptables chains, delete ip rules/routes,
 * and delete the tun.
 */
object RootProxyManager {

    private const val TAG = AppConfig.TAG

    // PID file for the hev-socks5-tunnel standalone process
    private const val HEV_PID_FILE = "hev-root.pid"

    // Config file for hev-socks5-tunnel in root mode
    private const val HEV_CONF_FILE = "hev-root.yml"

    // ---- public API --------------------------------------------------------

    /**
     * Full setup: iptables + tun + hev process.
     * Called from a coroutine on Dispatchers.IO.
     */
    fun start(context: Context) {
        LogUtil.i(TAG, "RootProxyManager: start")
        val socksPort = SettingsManager.getSocksPort()
        val mtu = SettingsManager.getVpnMtu()
        val ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val runtimeDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).also { it.mkdirs() }

        // 1. Copy hev binary
        val hevBin = prepareHevBinary(nativeDir, runtimeDir) ?: run {
            LogUtil.e(TAG, "RootProxyManager: cannot prepare hev binary")
            return
        }

        // 2. Write hev config
        val confFile = writeHevConfig(runtimeDir, socksPort, mtu)

        // 3. Create TUN
        createTun(mtu, ipv6)

        // 4. Ip rule + route
        setupRouting(ipv6)

        // 5. Install iptables chains
        val selectedPackages = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?.toSet() ?: emptySet()
        val bypassMode = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        val selectedUids = resolveUids(context, selectedPackages)
        installMangleChain(selectedUids, bypassMode, ipv6)

        // 6. Start hev process
        startHev(hevBin, confFile, runtimeDir)
    }

    /**
     * LAN/tethering sharing only — installs a FORWARD chain so tethered clients'
     * traffic also goes through the proxy that is already running (VPN mode).
     * Called from a coroutine on Dispatchers.IO.
     */
    fun startClientSharing(context: Context) {
        LogUtil.i(TAG, "RootProxyManager: startClientSharing")
        val socksPort = SettingsManager.getSocksPort()
        val ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)
        val dns = getLanDns(socksPort)
        buildLanShareSetup(dns, ipv6)
    }

    /**
     * Remove LAN sharing rules only (used when device is in VPN mode).
     */
    fun stop(context: Context) {
        LogUtil.i(TAG, "RootProxyManager: stop (LAN sharing)")
        teardownLanShare()
    }

    /**
     * Full teardown: kill hev, flush iptables, delete tun.
     * Called from a coroutine on Dispatchers.IO.
     */
    fun stopFull(context: Context) {
        LogUtil.i(TAG, "RootProxyManager: stopFull")
        val runtimeDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR)
        stopHev(runtimeDir)
        teardownMangleChain()
        teardownLanShare()
        teardownRouting()
        teardownTun()
    }

    // ---- hev binary --------------------------------------------------------

    private fun prepareHevBinary(nativeDir: String, runtimeDir: File): File? {
        val src = File(nativeDir, AppConfig.ROOT_TUN2SOCKS_BIN)
        if (!src.exists()) {
            LogUtil.e(TAG, "RootProxyManager: hev binary not found at ${src.absolutePath}")
            return null
        }
        val dst = File(runtimeDir, "hev-socks5-tunnel")
        src.copyTo(dst, overwrite = true)
        RootShell.run("chmod 755 ${dst.absolutePath}")
        return dst
    }

    // ---- hev config --------------------------------------------------------

    private fun writeHevConfig(runtimeDir: File, socksPort: Int, mtu: Int): File {
        val conf = """
            tunnel:
              name: ${AppConfig.ROOT_TUN_NAME}
              mtu: $mtu
              multi-queue: true
            socks5:
              port: $socksPort
              address: '${AppConfig.LOOPBACK}'
              udp: udp
              tcp-fast-open: true
            misc:
              log-level: warn
              log-file: stdout
              limit-nofile: 65535
        """.trimIndent()
        val confFile = File(runtimeDir, HEV_CONF_FILE)
        confFile.writeText(conf)
        return confFile
    }

    // ---- TUN device --------------------------------------------------------

    private fun createTun(mtu: Int, ipv6: Boolean) {
        val tun = AppConfig.ROOT_TUN_NAME
        // Bring up the tun with the well-known addresses used by hev
        RootShell.runAll(
            "ip tuntap add mode tun dev $tun",
            "ip link set dev $tun mtu $mtu up",
            "ip addr add ${AppConfig.ROOT_TUN_ADDR_V4} dev $tun",
        )
        if (ipv6) {
            RootShell.run("ip -6 addr add ${AppConfig.ROOT_TUN_ADDR_V6} dev $tun")
        }
    }

    private fun teardownTun() {
        val tun = AppConfig.ROOT_TUN_NAME
        RootShell.runSilent("ip link set dev $tun down")
        RootShell.runSilent("ip tuntap del mode tun dev $tun")
    }

    // ---- routing (ip rule + route table) -----------------------------------

    private fun setupRouting(ipv6: Boolean) {
        val table = AppConfig.ROOT_ROUTE_TABLE
        val mark = AppConfig.ROOT_MARK_ROUTE
        val priority = AppConfig.ROOT_RULE_PRIORITY
        val tun = AppConfig.ROOT_TUN_NAME

        // Add a dedicated route table: default via tun
        RootShell.runAll(
            "ip route add default dev $tun table $table",
            "ip rule add fwmark $mark table $table priority $priority",
        )
        if (ipv6) {
            RootShell.runAll(
                "ip -6 route add default dev $tun table $table",
                "ip -6 rule add fwmark $mark table $table priority $priority",
            )
        }
    }

    private fun teardownRouting() {
        val table = AppConfig.ROOT_ROUTE_TABLE
        val mark = AppConfig.ROOT_MARK_ROUTE
        val priority = AppConfig.ROOT_RULE_PRIORITY
        val tun = AppConfig.ROOT_TUN_NAME

        RootShell.runSilent("ip rule del fwmark $mark table $table priority $priority")
        RootShell.runSilent("ip route del default dev $tun table $table")
        RootShell.runSilent("ip -6 rule del fwmark $mark table $table priority $priority")
        RootShell.runSilent("ip -6 route del default dev $tun table $table")
    }

    // ---- iptables mangle chain ---------------------------------------------

    /**
     * Builds the mangle MARK chain that steers traffic into the tun.
     *
     * All-apps mode: mark everything except the hev bypass fwmark and loopback.
     * Per-app allow mode: mark only the selected UIDs (fail-closed if list is empty).
     * Per-app bypass mode: mark everything except the selected UIDs.
     */
    private fun installMangleChain(
        selectedUids: Set<Int>,
        bypassMode: Boolean,
        ipv6: Boolean,
    ) {
        val chain = AppConfig.ROOT_IPTABLES_CHAIN
        val v6chain = AppConfig.ROOT_V6_CHAIN
        val mark = AppConfig.ROOT_MARK_ROUTE
        val fwmark = AppConfig.ROOT_FWMARK
        val tun = AppConfig.ROOT_TUN_NAME

        val perApp = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY)

        // --- IPv4 mangle chain ---
        val cmds = mutableListOf(
            // Create chain
            "iptables -t mangle -N $chain 2>/dev/null || true",
            "iptables -t mangle -F $chain",
            // Skip loopback and tun itself
            "iptables -t mangle -A $chain -o lo -j RETURN",
            "iptables -t mangle -A $chain -o $tun -j RETURN",
            // Skip hev's own upstream socket (already on loopback, but defensive)
            "iptables -t mangle -A $chain -m mark --mark $fwmark -j RETURN",
        )

        if (perApp) {
            if (bypassMode) {
                // bypass: skip selected UIDs, mark rest
                for (uid in selectedUids) {
                    cmds += "iptables -t mangle -A $chain -m owner --uid-owner $uid -j RETURN"
                }
                cmds += "iptables -t mangle -A $chain -j MARK --set-mark $mark"
            } else {
                // allow: only mark selected UIDs, fail-closed when empty
                for (uid in selectedUids) {
                    cmds += "iptables -t mangle -A $chain -m owner --uid-owner $uid -j MARK --set-mark $mark"
                }
                // Do NOT add catch-all in allow mode (fail-closed)
            }
        } else {
            // all-apps: mark everything
            cmds += "iptables -t mangle -A $chain -j MARK --set-mark $mark"
        }

        // Hook chain into OUTPUT
        cmds += "iptables -t mangle -D OUTPUT -j $chain 2>/dev/null || true"
        cmds += "iptables -t mangle -I OUTPUT 1 -j $chain"

        cmds.forEach { RootShell.run(it) }

        // --- IPv6 chain ---
        if (ipv6) {
            val v6cmds = mutableListOf(
                "ip6tables -t mangle -N $v6chain 2>/dev/null || true",
                "ip6tables -t mangle -F $v6chain",
                "ip6tables -t mangle -A $v6chain -o lo -j RETURN",
                "ip6tables -t mangle -A $v6chain -o $tun -j RETURN",
                "ip6tables -t mangle -A $v6chain -m mark --mark $fwmark -j RETURN",
            )
            if (perApp) {
                if (bypassMode) {
                    for (uid in selectedUids) {
                        v6cmds += "ip6tables -t mangle -A $v6chain -m owner --uid-owner $uid -j RETURN"
                    }
                    v6cmds += "ip6tables -t mangle -A $v6chain -j MARK --set-mark $mark"
                } else {
                    for (uid in selectedUids) {
                        v6cmds += "ip6tables -t mangle -A $v6chain -m owner --uid-owner $uid -j MARK --set-mark $mark"
                    }
                    // fail-closed: no catch-all
                }
            } else {
                v6cmds += "ip6tables -t mangle -A $v6chain -j MARK --set-mark $mark"
            }
            v6cmds += "ip6tables -t mangle -D OUTPUT -j $v6chain 2>/dev/null || true"
            v6cmds += "ip6tables -t mangle -I OUTPUT 1 -j $v6chain"
            v6cmds.forEach { RootShell.run(it) }
        } else {
            // IPv6 disabled: reject all native v6 OUTPUT for captured apps
            val v6cmds = mutableListOf(
                "ip6tables -t filter -N $v6chain 2>/dev/null || true",
                "ip6tables -t filter -F $v6chain",
                "ip6tables -t filter -A $v6chain -o lo -j RETURN",
            )
            if (perApp && !bypassMode) {
                for (uid in selectedUids) {
                    v6cmds += "ip6tables -t filter -A $v6chain -m owner --uid-owner $uid -j REJECT"
                }
            } else {
                v6cmds += "ip6tables -t filter -A $v6chain -j REJECT"
            }
            v6cmds += "ip6tables -t filter -D OUTPUT -j $v6chain 2>/dev/null || true"
            v6cmds += "ip6tables -t filter -I OUTPUT 1 -j $v6chain"
            v6cmds.forEach { RootShell.run(it) }
        }
    }

    private fun teardownMangleChain() {
        val chain = AppConfig.ROOT_IPTABLES_CHAIN
        val v6chain = AppConfig.ROOT_V6_CHAIN

        RootShell.runSilent("iptables -t mangle -D OUTPUT -j $chain")
        RootShell.runSilent("iptables -t mangle -F $chain")
        RootShell.runSilent("iptables -t mangle -X $chain")

        // IPv6 mangle or filter depending on whether v6 was enabled
        RootShell.runSilent("ip6tables -t mangle -D OUTPUT -j $v6chain")
        RootShell.runSilent("ip6tables -t mangle -F $v6chain")
        RootShell.runSilent("ip6tables -t mangle -X $v6chain")
        RootShell.runSilent("ip6tables -t filter -D OUTPUT -j $v6chain")
        RootShell.runSilent("ip6tables -t filter -F $v6chain")
        RootShell.runSilent("ip6tables -t filter -X $v6chain")
    }

    // ---- LAN / tethering sharing ------------------------------------------

    /**
     * Installs FORWARD + NAT rules so tethered clients go through the proxy.
     * Called from startClientSharing() on Dispatchers.IO.
     */
    private fun buildLanShareSetup(dns: String, ipv6: Boolean) {
        val fwdChain = AppConfig.ROOT_FWD_CHAIN
        val dnsChain = AppConfig.ROOT_DNS_CHAIN
        val v6fwdChain = AppConfig.ROOT_V6_FWD_CHAIN
        val v6preChain = AppConfig.ROOT_V6_PRE_CHAIN
        val mark = AppConfig.ROOT_MARK_ROUTE
        val tun = AppConfig.ROOT_TUN_NAME
        val table = AppConfig.ROOT_ROUTE_TABLE

        // IPv4 FORWARD
        val cmds = mutableListOf(
            "iptables -t filter -N $fwdChain 2>/dev/null || true",
            "iptables -t filter -F $fwdChain",
            "iptables -t filter -A $fwdChain -i $tun -j ACCEPT",
            "iptables -t filter -A $fwdChain -o $tun -j ACCEPT",
            "iptables -t filter -D FORWARD -j $fwdChain 2>/dev/null || true",
            "iptables -t filter -I FORWARD 1 -j $fwdChain",
        )

        // DNS DNAT for tethered clients
        cmds += listOf(
            "iptables -t nat -N $dnsChain 2>/dev/null || true",
            "iptables -t nat -F $dnsChain",
            "iptables -t nat -A $dnsChain -p udp --dport 53 -j DNAT --to-destination $dns:53",
            "iptables -t nat -A $dnsChain -p tcp --dport 53 -j DNAT --to-destination $dns:53",
            "iptables -t nat -D PREROUTING -j $dnsChain 2>/dev/null || true",
            "iptables -t nat -I PREROUTING 1 -j $dnsChain",
        )

        // Mark forwarded packets so they go into the tun route table
        cmds += listOf(
            "iptables -t mangle -D PREROUTING -j $fwdChain 2>/dev/null || true",
            "iptables -t mangle -N $fwdChain 2>/dev/null || true",
            "iptables -t mangle -F $fwdChain",
            "iptables -t mangle -A $fwdChain -i $tun -j RETURN",
            "iptables -t mangle -A $fwdChain -j MARK --set-mark $mark",
            "iptables -t mangle -I PREROUTING 1 -j $fwdChain",
        )

        cmds.forEach { RootShell.run(it) }

        // IPv6 tethered clients
        if (ipv6) {
            val v6cmds = mutableListOf(
                "ip6tables -t mangle -N $v6preChain 2>/dev/null || true",
                "ip6tables -t mangle -F $v6preChain",
                // skip loopback / link-local / ULA / multicast — only mark globally routable forwarded v6
                "ip6tables -t mangle -A $v6preChain -i lo -j RETURN",
                "ip6tables -t mangle -A $v6preChain -d fe80::/10 -j RETURN",
                "ip6tables -t mangle -A $v6preChain -d fc00::/7 -j RETURN",
                "ip6tables -t mangle -A $v6preChain -d ff00::/8 -j RETURN",
                "ip6tables -t mangle -A $v6preChain -m addrtype --src-type LOCAL -j RETURN",
                "ip6tables -t mangle -A $v6preChain -j MARK --set-mark $mark",
                "ip6tables -t mangle -D PREROUTING -j $v6preChain 2>/dev/null || true",
                "ip6tables -t mangle -I PREROUTING 1 -j $v6preChain",
            )

            v6cmds += listOf(
                "ip6tables -t filter -N $v6fwdChain 2>/dev/null || true",
                "ip6tables -t filter -F $v6fwdChain",
                "ip6tables -t filter -A $v6fwdChain -i $tun -j ACCEPT",
                "ip6tables -t filter -A $v6fwdChain -o $tun -j ACCEPT",
                "ip6tables -t filter -D FORWARD -j $v6fwdChain 2>/dev/null || true",
                "ip6tables -t filter -I FORWARD 1 -j $v6fwdChain",
            )

            // Add v6 route into the tun table
            v6cmds += listOf(
                "ip -6 route add default dev $tun table $table 2>/dev/null || true",
                "ip -6 rule add fwmark $mark table $table 2>/dev/null || true",
            )

            v6cmds.forEach { RootShell.run(it) }
        } else {
            // IPv6 disabled: REJECT forwarded v6 so tethered clients fall back to v4
            val v6cmds = listOf(
                "ip6tables -t filter -N $v6fwdChain 2>/dev/null || true",
                "ip6tables -t filter -F $v6fwdChain",
                "ip6tables -t filter -A $v6fwdChain -j REJECT",
                "ip6tables -t filter -D FORWARD -j $v6fwdChain 2>/dev/null || true",
                "ip6tables -t filter -I FORWARD 1 -j $v6fwdChain",
            )
            v6cmds.forEach { RootShell.run(it) }
        }
    }

    private fun teardownLanShare() {
        val fwdChain = AppConfig.ROOT_FWD_CHAIN
        val dnsChain = AppConfig.ROOT_DNS_CHAIN
        val v6fwdChain = AppConfig.ROOT_V6_FWD_CHAIN
        val v6preChain = AppConfig.ROOT_V6_PRE_CHAIN

        RootShell.runSilent("iptables -t filter -D FORWARD -j $fwdChain")
        RootShell.runSilent("iptables -t filter -F $fwdChain")
        RootShell.runSilent("iptables -t filter -X $fwdChain")
        RootShell.runSilent("iptables -t nat -D PREROUTING -j $dnsChain")
        RootShell.runSilent("iptables -t nat -F $dnsChain")
        RootShell.runSilent("iptables -t nat -X $dnsChain")
        RootShell.runSilent("iptables -t mangle -D PREROUTING -j $fwdChain")
        RootShell.runSilent("iptables -t mangle -F $fwdChain")
        RootShell.runSilent("iptables -t mangle -X $fwdChain")

        RootShell.runSilent("ip6tables -t mangle -D PREROUTING -j $v6preChain")
        RootShell.runSilent("ip6tables -t mangle -F $v6preChain")
        RootShell.runSilent("ip6tables -t mangle -X $v6preChain")
        RootShell.runSilent("ip6tables -t filter -D FORWARD -j $v6fwdChain")
        RootShell.runSilent("ip6tables -t filter -F $v6fwdChain")
        RootShell.runSilent("ip6tables -t filter -X $v6fwdChain")
    }

    // ---- hev process -------------------------------------------------------

    private fun startHev(hevBin: File, confFile: File, runtimeDir: File) {
        // Wait until the tun device appears (hev needs it to already exist)
        var waited = 0
        while (!tunExists() && waited < 50) {
            Thread.sleep(100)
            waited++
        }
        if (!tunExists()) {
            LogUtil.e(TAG, "RootProxyManager: tun device not ready after ${waited * 100}ms")
        }

        val pidFile = File(runtimeDir, HEV_PID_FILE)
        // Adjust OOM score so the LMK leaves us alone
        val cmd = "echo ${AppConfig.ROOT_OOM_SCORE} > /proc/self/oom_score_adj;" +
                " ${hevBin.absolutePath} ${confFile.absolutePath} &" +
                " echo \$! > ${pidFile.absolutePath}"
        RootShell.run(cmd)
        LogUtil.i(TAG, "RootProxyManager: hev started")
    }

    private fun stopHev(runtimeDir: File) {
        val pidFile = File(runtimeDir, HEV_PID_FILE)
        if (pidFile.exists()) {
            val pid = pidFile.readText().trim()
            if (pid.isNotEmpty()) {
                RootShell.runSilent("kill $pid 2>/dev/null")
            }
            pidFile.delete()
        }
        // Also kill by binary name as a fallback
        RootShell.runSilent("pkill -f hev-socks5-tunnel 2>/dev/null")
        LogUtil.i(TAG, "RootProxyManager: hev stopped")
    }

    // ---- helpers -----------------------------------------------------------

    private fun tunExists(): Boolean {
        return RootShell.output("ip link show ${AppConfig.ROOT_TUN_NAME} 2>/dev/null") != null
    }

    /**
     * Resolve package names to UIDs. Packages that cannot be resolved are skipped.
     */
    private fun resolveUids(context: Context, packages: Set<String>): Set<Int> {
        val pm = context.packageManager
        return packages.mapNotNull { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0).uid
            } catch (_: Exception) {
                null
            }
        }.toSet()
    }

    /**
     * Pick a DNS server for LAN clients: prefer the first plain-IPv4 VPN DNS server
     * that isn't a domain name; fall back to ROOT_LAN_DNS.
     */
    private fun getLanDns(socksPort: Int): String {
        return SettingsManager.getVpnDnsServers()
            .firstOrNull { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) }
            ?: AppConfig.ROOT_LAN_DNS
    }
}
