package com.v2ray.ang.contracts

interface Tun2SocksControl {
    fun startTun2Socks()

    fun stopTun2Socks()

    /**
     * Whether the underlying tun2socks engine is currently running.
     */
    fun isTunnelRunning(): Boolean

    /**
     * Tunnel interface traffic statistics as [txPackets, txBytes, rxPackets, rxBytes],
     * or null if unavailable / not running.
     */
    fun getTunnelStats(): LongArray?
}
