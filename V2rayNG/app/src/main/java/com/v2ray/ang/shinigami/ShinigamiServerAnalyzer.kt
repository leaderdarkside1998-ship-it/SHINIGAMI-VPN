package com.v2ray.ang.shinigami

import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager

/**
 * Reads the servers that already exist in the app's own server repository (never creates or
 * invents a server - spec requirement). The probes themselves run in the app's test service
 * process, see [ShinigamiPingClient].
 *
 * Only display-safe fields are exposed in [ShinigamiServerSummary] (name, protocol, transport,
 * security) - [ProfileItem] fields like password/uuid/secretKey/host/port never leave this class.
 */
object ShinigamiServerAnalyzer {

    /**
     * Every server currently in the app's repository, across all subscriptions. Only servers the
     * app itself could start are left out: connecting to one of those would fail.
     */
    fun listServers(): List<ShinigamiServerSummary> =
        MmkvManager.decodeAllServerList()
            .distinct()
            .mapNotNull { guid ->
                MmkvManager.decodeServerConfig(guid)
                    ?.takeIf { LauncherManager.hasUsableServer(it) }
                    ?.let { toSummary(guid, it) }
            }

    private fun toSummary(guid: String, profile: ProfileItem): ShinigamiServerSummary =
        ShinigamiServerSummary(
            guid = guid,
            remarks = profile.remarks.ifBlank { guid },
            subscriptionId = profile.subscriptionId,
            protocol = profile.configType.name,
            network = profile.network,
            security = profile.security
        )
}
