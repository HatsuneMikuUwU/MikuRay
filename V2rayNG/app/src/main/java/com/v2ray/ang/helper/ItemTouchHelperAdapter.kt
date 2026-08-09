package com.v2ray.ang.helper

interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean


    fun onItemMoveCompleted()

    fun onItemDismiss(position: Int)
}
