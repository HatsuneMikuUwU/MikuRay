package com.miku.ray.dto

/**
 * Konten respons langganan beserta header final setelah pengalihan HTTP.
 */
data class UrlContentResponse(
    val content: String,
    val headers: Map<String, String>,
)
