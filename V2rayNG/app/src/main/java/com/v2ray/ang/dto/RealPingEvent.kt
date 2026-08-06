package com.v2ray.ang.dto

sealed class RealPingEvent {

    /**
     * Periodic progress update while the batch is still running.
     * [guid]/[delayMillis] describe the item that just finished (delayMillis may be -1 on failure).
     * [current]/[total] describe how many items have completed out of the whole batch.
     */
    data class Progress(
        val text: String,
        val guid: String = "",
        val delayMillis: Long = -1L,
        val current: Int = 0,
        val total: Int = 0
    ) : RealPingEvent()

    /** A single server result is available. */
    data class Result(val guid: String, val delayMillis: Long) : RealPingEvent()

    /** The entire batch has finished or been cancelled. */
    data class Finish(val status: String) : RealPingEvent()
}

