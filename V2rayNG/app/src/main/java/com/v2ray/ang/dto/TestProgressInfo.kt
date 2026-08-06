package com.v2ray.ang.dto

import java.io.Serializable

/**
 * Snapshot of a URL-test (real ping / tcping) batch, sent from [com.v2ray.ang.service.CoreTestService]
 * to the UI so it can render the running progress dialog.
 *
 * [guid] is the server that just finished testing ("" before the first result arrives).
 * [delayMillis] is that server's result (-1 on failure/timeout).
 * [current]/[total] describe how many servers have been tested out of the whole batch.
 */
data class TestProgressInfo(
    val guid: String,
    val delayMillis: Long,
    val current: Int,
    val total: Int
) : Serializable
