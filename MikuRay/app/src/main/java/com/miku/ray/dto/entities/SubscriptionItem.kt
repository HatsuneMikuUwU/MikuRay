package com.miku.ray.dto.entities

data class SubscriptionItem(
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    val addedTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = -1,
    var autoUpdate: Boolean = false,
    var updateInterval: Long = 1440,
    var prevProfile: String? = null,
    var nextProfile: String? = null,
    var filter: String? = null,
    var networkFilter: String? = null,
    var protocolFilter: String? = null,
    var allowInsecureUrl: Boolean = false,
    var userAgent: String? = null,
    var requestHeaders: String? = null,
    var tabIcon: String? = null,
    /** Byte terpakai dari header Subscription-Userinfo; -1 berarti tidak tersedia. */
    var bytesUsed: Long = -1L,
    /** Byte tersisa dari header Subscription-Userinfo; -1 berarti tidak tersedia. */
    var bytesRemaining: Long = -1L,
    /** Waktu kedaluwarsa Unix dalam detik; -1 berarti tidak tersedia. */
    var expiresAt: Long = -1L,
)

