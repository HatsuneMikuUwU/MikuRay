package com.v2ray.ang.contracts

interface BaseAdapterListener {
    fun onEdit(guid: String, position: Int)

    fun onRemove(guid: String, position: Int)

    fun onShare(url: String)

    fun onRefreshData()
}
