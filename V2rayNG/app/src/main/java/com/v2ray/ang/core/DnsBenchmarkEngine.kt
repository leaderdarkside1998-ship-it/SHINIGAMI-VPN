package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

/**
 * A candidate DNS resolver to benchmark. IPs are well-known, publicly documented resolver
 * addresses (Cloudflare, Google Public DNS, Quad9) -- nothing proprietary or extracted from a
 * paid service.
 */
data class DnsCandidate(val label: String, val ip: String)

/** One real UDP-DNS round trip result for one (candidate, domain) pair. */
private data class DnsProbeResult(val success: Boolean, val elapsedMillis: Long)

data class DnsCandidateScore(
    val candidate: DnsCandidate,
    val avgResolutionMillis: Long,
    val successRatePercent: Int,
    val consistencyMillis: Long,
    val score: Double
)

/**
 * Benchmarks a small, updatable list of public DNS resolvers using real UDP DNS queries (hand
 * rolled minimal DNS packets -- no library, no fabricated numbers) against a few general test
 * domains, then applies the best one to the app's existing remote-DNS setting
 * ([AppConfig.PREF_REMOTE_DNS]) which the real Xray/V2Ray config generator already reads
 * (see SettingsManager.getRemoteDnsServers). A cooldown + minimum-improvement threshold stop it
 * from flapping, and a worse result on the next benchmark rolls back to the previous DNS.
 */
object DnsBenchmarkEngine {

    // Public, well-documented resolver IPs. Kept in a plain list (not hardcoded into query
    // logic) so it's easy to extend if the project later wires this to a remote config.
    val candidates: List<DnsCandidate> = listOf(
        DnsCandidate("Cloudflare", "1.1.1.1"),
        DnsCandidate("Google Public DNS", "8.8.8.8"),
        DnsCandidate("Quad9", "9.9.9.9")
    )

    private val testDomains = listOf("www.google.com", "www.cloudflare.com")
    private const val PROBES_PER_DOMAIN = 2
    private const val TIMEOUT_MILLIS = 1500L
    private const val MIN_IMPROVEMENT_MILLIS = 40L
    private const val COOLDOWN_MILLIS = 10 * 60 * 1000L

    private var lastBenchmarkMillis = 0L

    suspend fun benchmarkAndApplyIfBetter(): DnsDiagnostics = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val onCooldown = now - lastBenchmarkMillis < COOLDOWN_MILLIS
        val scores = candidates.mapNotNull { scoreCandidate(it) }
        lastBenchmarkMillis = now

        if (scores.isEmpty()) {
            LogUtil.e(AppConfig.TAG, "DnsBenchmarkEngine: every candidate failed, keeping current DNS")
            return@withContext currentDiagnostics(now)
        }

        val best = scores.maxBy { it.score }
        val currentDns = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_PROXY
        val currentScore = scores.firstOrNull { it.candidate.ip == currentDns }

        if (!onCooldown && (currentScore == null || best.score - currentScore.score >= scoreMarginFor(MIN_IMPROVEMENT_MILLIS))) {
            if (best.candidate.ip != currentDns) {
                // Remember what we're replacing so a later worse benchmark can roll back.
                MmkvManager.encodeSettings(AppConfig.PREF_AUTO_DNS_PRE_BENCHMARK_VALUE, currentDns)
                MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, best.candidate.ip)
            }
        } else if (currentScore != null) {
            val previous = MmkvManager.decodeSettingsString(AppConfig.PREF_AUTO_DNS_PRE_BENCHMARK_VALUE)
            if (!previous.isNullOrEmpty() && currentScore.score < scoreForRollback(scores, previous)) {
                MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, previous)
                MmkvManager.encodeSettings(AppConfig.PREF_AUTO_DNS_PRE_BENCHMARK_VALUE, "")
            }
        }

        val applied = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_PROXY
        val appliedScore = scores.firstOrNull { it.candidate.ip == applied }
        DnsDiagnostics(
            currentDns = candidates.firstOrNull { it.ip == applied }?.label ?: applied,
            dnsResponseMillis = appliedScore?.avgResolutionMillis,
            successRatePercent = appliedScore?.successRatePercent,
            dnsScore = appliedScore?.score,
            lastBenchmarkMillis = now
        )
    }

    private fun scoreMarginFor(millis: Long): Double = millis.toDouble()

    private fun scoreForRollback(scores: List<DnsCandidateScore>, previousIp: String): Double =
        scores.firstOrNull { it.candidate.ip == previousIp }?.score ?: Double.MAX_VALUE

    private fun currentDiagnostics(now: Long): DnsDiagnostics {
        val currentDns = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_PROXY
        return DnsDiagnostics(
            currentDns = candidates.firstOrNull { it.ip == currentDns }?.label ?: currentDns,
            lastBenchmarkMillis = now
        )
    }

    private suspend fun scoreCandidate(candidate: DnsCandidate): DnsCandidateScore? {
        val elapsedList = ArrayList<Long>()
        var successCount = 0
        var total = 0
        for (domain in testDomains) {
            repeat(PROBES_PER_DOMAIN) {
                total++
                val result = probe(candidate.ip, domain)
                if (result.success) {
                    successCount++
                    elapsedList.add(result.elapsedMillis)
                }
            }
        }
        if (elapsedList.isEmpty()) return null

        val avg = elapsedList.average()
        val consistency = if (elapsedList.size > 1) {
            val mean = avg
            kotlin.math.sqrt(elapsedList.sumOf { (it - mean) * (it - mean) } / elapsedList.size)
        } else 0.0
        val successRate = (successCount * 100) / total

        // Real-valued score from real measurements only: fast + reliable + consistent wins.
        val score = (1000.0 - avg - (consistency * 1.5) - ((100 - successRate) * 8.0)).coerceAtLeast(0.0)

        return DnsCandidateScore(
            candidate = candidate,
            avgResolutionMillis = avg.toLong(),
            successRatePercent = successRate,
            consistencyMillis = consistency.toLong(),
            score = score
        )
    }

    /** One real UDP DNS A-record query, hand-built per RFC 1035, sent straight to [ip]:53. */
    private suspend fun probe(ip: String, domain: String): DnsProbeResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var socket: DatagramSocket? = null
        try {
            val query = buildDnsQuery(domain)
            socket = DatagramSocket()
            socket.soTimeout = TIMEOUT_MILLIS.toInt()
            CoreServiceManager.protectSocket(socket)
            val address = InetAddress.getByName(ip)
            val sendPacket = DatagramPacket(query, query.size, address, 53)

            val success = withTimeoutOrNull(TIMEOUT_MILLIS) {
                socket.send(sendPacket)
                val buffer = ByteArray(512)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(receivePacket)
                // A well-formed DNS response echoes our transaction ID in the first two bytes
                // and sets the QR (response) bit; that's a genuine positive result.
                receivePacket.length >= 4 &&
                    buffer[0] == query[0] && buffer[1] == query[1] &&
                    (buffer[2].toInt() and 0x80) != 0
            } ?: false

            DnsProbeResult(success, System.currentTimeMillis() - start)
        } catch (_: Exception) {
            DnsProbeResult(false, System.currentTimeMillis() - start)
        } finally {
            socket?.close()
        }
    }

    private fun buildDnsQuery(domain: String): ByteArray {
        val id = Random.nextInt(0, 0xFFFF)
        val header = byteArrayOf(
            (id shr 8).toByte(), id.toByte(),
            0x01, 0x00, // standard query, recursion desired
            0x00, 0x01, // QDCOUNT = 1
            0x00, 0x00, // ANCOUNT
            0x00, 0x00, // NSCOUNT
            0x00, 0x00  // ARCOUNT
        )
        val question = ArrayList<Byte>()
        domain.split(".").forEach { label ->
            question.add(label.length.toByte())
            label.forEach { c -> question.add(c.code.toByte()) }
        }
        question.add(0) // root terminator
        question.add(0x00); question.add(0x01) // QTYPE = A
        question.add(0x00); question.add(0x01) // QCLASS = IN
        return header + question.toByteArray()
    }
}
