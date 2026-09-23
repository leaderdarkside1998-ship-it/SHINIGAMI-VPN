package com.v2ray.ang.shinigami

import android.content.Context
import com.v2ray.ang.core.RouteMetrics
import com.v2ray.ang.core.StabilityMeter
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Reads the servers that already exist in the app's own server repository (never creates or
 * invents a server - spec requirement), then measures each one with real network probes via
 * [StabilityMeter], which is the exact same code path the manual "real ping" button uses.
 *
 * Only display-safe fields are exposed in [ShinigamiServerSummary] (name, protocol, transport,
 * security) - [ProfileItem] fields like password/uuid/secretKey/host/port never leave this class.
 */
object ShinigamiServerAnalyzer {

    /** Every server currently in the app's repository, across all subscriptions. No filtering. */
    fun listServers(): List<ShinigamiServerSummary> =
        MmkvManager.decodeAllServerList()
            .distinct()
            .mapNotNull { guid -> MmkvManager.decodeServerConfig(guid)?.let { toSummary(guid, it) } }

    private fun toSummary(guid: String, profile: ProfileItem): ShinigamiServerSummary =
        ShinigamiServerSummary(
            guid = guid,
            remarks = profile.remarks.ifBlank { guid },
            subscriptionId = profile.subscriptionId,
            protocol = profile.configType.name,
            network = profile.network,
            security = profile.security
        )

    /**
     * Measures [servers] with [StabilityMeter], [maxConcurrent] at a time so a large server list
     * doesn't flood the daemon process with probes at once. [samplesPerServer] real probes are
     * sent per server (see [StabilityMeter.measure] for what each probe measures).
     */
    suspend fun measureAll(
        context: Context,
        servers: List<ShinigamiServerSummary>,
        samplesPerServer: Int = 4,
        maxConcurrent: Int = 3
    ): Map<String, RouteMetrics> = coroutineScope {
        val semaphore = Semaphore(maxConcurrent)
        servers.map { server ->
            async {
                semaphore.withPermit {
                    server.guid to StabilityMeter.measure(context, server.guid, samples = samplesPerServer)
                }
            }
        }.map { it.await() }.toMap()
    }
}
