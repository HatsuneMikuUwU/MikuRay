package com.v2ray.ang.dto

import java.io.Serializable

data class CountryCodeTestMessage(
    val key: Int,
    val subscriptionId: String = "",
    val serverGuids: List<String> = emptyList()
) : Serializable

