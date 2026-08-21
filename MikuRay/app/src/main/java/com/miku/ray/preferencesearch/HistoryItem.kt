package com.miku.ray.preferencesearch

class HistoryItem(private val term: String) : ListItem() {

    companion object {
        const val TYPE = 1
    }

    override fun getType(): Int {
        return TYPE
    }

    fun getTerm(): String {
        return term
    }

    override fun equals(other: Any?): Boolean {
        if (other is HistoryItem) {
            return other.term == term
        }
        return false
    }
}