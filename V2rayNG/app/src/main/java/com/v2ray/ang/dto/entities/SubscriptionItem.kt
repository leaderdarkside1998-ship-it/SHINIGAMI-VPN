package com.v2ray.ang.dto.entities

data class SubscriptionItem(
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    val addedTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = -1,
    var autoUpdate: Boolean = true,
    var updateInterval: Long = 15, // in minutes (minimum allowed is 15)
    var prevProfile: String? = null,
    var nextProfile: String? = null,
    var filter: String? = null,
    var allowInsecureUrl: Boolean = false,
    var userAgent: String? = null,
    var requestHeaders: String? = null,
    var overrideAddress: String? = null,
    var overridePort: Int? = null,
    /**
     * Traffic quota reported by the subscription server's own `subscription-userinfo` response
     * header (the de-facto standard most subscription panels send), captured the last time this
     * subscription was updated. Null fields mean the server didn't report that value -- never a
     * guess. `trafficUpdatedMillis` is when this snapshot was captured, separate from
     * [lastUpdated] which also covers subscriptions with no such header at all.
     */
    var trafficUploadBytes: Long? = null,
    var trafficDownloadBytes: Long? = null,
    var trafficTotalBytes: Long? = null,
    var trafficExpireEpochSeconds: Long? = null,
    var trafficUpdatedMillis: Long? = null,
)

