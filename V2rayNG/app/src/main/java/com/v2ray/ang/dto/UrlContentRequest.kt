package com.v2ray.ang.dto

data class UrlContentRequest(
    val url: String?,
    val timeout: Int = 15000,
    val httpPort: Int = 0,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val userAgent: String? = null,
    val requestHeaders: String? = null,
    /**
     * Called once, synchronously, with the final successful response's headers -- before the
     * body is read. Lets a caller (subscription updates, currently) pick up response metadata
     * such as the `subscription-userinfo` traffic-quota header without HttpUtil needing to know
     * anything about what that metadata means.
     */
    val onResponseHeaders: ((okhttp3.Headers) -> Unit)? = null
)