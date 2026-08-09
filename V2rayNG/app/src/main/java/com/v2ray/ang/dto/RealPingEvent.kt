package com.v2ray.ang.dto

sealed class RealPingEvent {

    data class Progress(
        val text: String,
        val guid: String = "",
        val delayMillis: Long = -1L,
        val current: Int = 0,
        val total: Int = 0
    ) : RealPingEvent()

    data class Result(val guid: String, val delayMillis: Long) : RealPingEvent()

    data class Finish(val status: String) : RealPingEvent()
}

