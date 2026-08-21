package com.miku.ray.preferencesearch

object Breadcrumb {
    @JvmStatic
    fun concat(s1: String?, s2: String): String {
        return if (s1.isNullOrEmpty()) s2 else "$s1 > $s2"
    }
}