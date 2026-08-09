package com.v2ray.ang.dto

import java.io.Serializable

data class TestProgressInfo(
    val guid: String,
    val delayMillis: Long,
    val current: Int,
    val total: Int
) : Serializable
