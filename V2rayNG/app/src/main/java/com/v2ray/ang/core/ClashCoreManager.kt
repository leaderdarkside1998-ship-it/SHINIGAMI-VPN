package com.v2ray.ang.core

import android.content.Context
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * Optional Mihomo/Clash-compatible backend. It owns a local SOCKS5+UDP listener; the existing
 * HEV TUN bridge feeds that listener exactly like it feeds Xray's SOCKS inbound. Only one backend
 * is active at a time, so the Android VPN never tries to establish two TUN interfaces.
 */
object ClashCoreManager {
    const val SOCKS_PORT = AppConfig.PORT_CLASH_SOCKS
    private const val BINARY_NAME = "mihomo"
    private const val READY_TIMEOUT_MS = 6_000L
    private val processRef = AtomicReference<Process?>()

    val isRunning: Boolean get() = processRef.get()?.isAlive == true

    fun isEnabled(): Boolean = com.v2ray.ang.handler.MmkvManager
        .decodeSettingsString(AppConfig.PREF_CORE_ENGINE, AppConfig.CORE_ENGINE_XRAY) == AppConfig.CORE_ENGINE_CLASH

    fun start(context: Context, profile: ProfileItem) {
        stop()
        val binary = ensureBinary(context)
        val workDir = File(context.filesDir, "mihomo").apply { mkdirs() }
        val config = File(workDir, "config.yaml").apply { writeText(buildConfig(profile)) }
        val process = ProcessBuilder(binary.absolutePath, "-d", workDir.absolutePath, "-f", config.absolutePath)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        processRef.set(process)
        Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { LogUtil.d(AppConfig.TAG, "Mihomo: $it") }
            }
        }.apply { isDaemon = true; name = "mihomo-log" }.start()
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (accepts(SOCKS_PORT)) return
            if (!process.isAlive) error("Mihomo exited during startup")
            Thread.sleep(100)
        }
        error("Mihomo SOCKS listener did not become ready")
    }

    fun stop() {
        processRef.getAndSet(null)?.let { p ->
            try { p.destroy() } catch (_: Exception) {}
            try { if (p.isAlive) p.destroyForcibly() } catch (_: Exception) {}
        }
    }

    private fun accepts(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 200) }
        true
    } catch (_: Exception) { false }

    private fun ensureBinary(context: Context): File {
        val target = File(context.filesDir, "mihomo/$BINARY_NAME")
        if (target.canExecute()) return target
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" || it == "x86_64" || it == "x86" }
            ?: error("Unsupported Android ABI for Mihomo")
        val assetName = when (abi) {
            "arm64-v8a" -> "mihomo/arm64-v8a/mihomo"
            "armeabi-v7a" -> "mihomo/armeabi-v7a/mihomo"
            "x86_64" -> "mihomo/x86_64/mihomo"
            else -> "mihomo/x86/mihomo"
        }
        context.assets.open(assetName).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        target.setExecutable(true)
        return target
    }

    private fun q(value: String?): String = "'" + (value ?: "").replace("'", "''") + "'"

    private fun buildConfig(p: ProfileItem): String {
        val type = when (p.configType) {
            EConfigType.VLESS -> "vless"
            EConfigType.VMESS -> "vmess"
            EConfigType.TROJAN -> "trojan"
            EConfigType.SHADOWSOCKS -> "ss"
            EConfigType.HTTP -> "http"
            else -> error("Selected profile is not supported by Mihomo: ${p.configType}")
        }
        val b = StringBuilder()
        b.appendLine("mixed-port: $SOCKS_PORT")
        b.appendLine("allow-lan: false")
        b.appendLine("bind-address: 127.0.0.1")
        b.appendLine("mode: rule")
        b.appendLine("log-level: warning")
        b.appendLine("ipv6: false")
        b.appendLine("tcp-concurrent: true")
        b.appendLine("unified-delay: true")
        b.appendLine("tun:")
        b.appendLine("  enable: false")
        b.appendLine("proxies:")
        b.appendLine("  - name: game-node")
        b.appendLine("    type: $type")
        b.appendLine("    server: ${q(p.server)}")
        b.appendLine("    port: ${p.serverPort?.toIntOrNull() ?: 443}")
        b.appendLine("    udp: true")
        when (p.configType) {
            EConfigType.VLESS -> {
                b.appendLine("    uuid: ${q(p.username)}")
                p.flow?.takeIf { it.isNotBlank() }?.let { b.appendLine("    flow: ${q(it)}") }
                b.appendLine("    packet-encoding: xudp")
                b.appendLine("    encryption: ''")
            }
            EConfigType.VMESS -> {
                b.appendLine("    uuid: ${q(p.username)}")
                b.appendLine("    alterId: 0")
                b.appendLine("    cipher: auto")
                b.appendLine("    packet-encoding: packetaddr")
            }
            EConfigType.TROJAN -> b.appendLine("    password: ${q(p.password)}")
            EConfigType.SHADOWSOCKS -> {
                b.appendLine("    cipher: ${q(p.method)}")
                b.appendLine("    password: ${q(p.password)}")
            }
            EConfigType.HTTP -> {
                p.username?.takeIf { it.isNotBlank() }?.let { b.appendLine("    username: ${q(it)}") }
                p.password?.takeIf { it.isNotBlank() }?.let { b.appendLine("    password: ${q(it)}") }
            }
            else -> Unit
        }
        val tls = p.security.equals("tls", true) || p.configType == EConfigType.TROJAN
        if (tls) {
            b.appendLine("    tls: true")
            b.appendLine("    skip-cert-verify: ${p.insecure == true}")
            p.sni?.takeIf { it.isNotBlank() }?.let { b.appendLine("    servername: ${q(it)}") }
            p.fingerPrint?.takeIf { it.isNotBlank() }?.let { b.appendLine("    client-fingerprint: ${q(it)}") }
            if (p.publicKey.isNullOrBlank().not()) {
                b.appendLine("    reality-opts:")
                b.appendLine("      public-key: ${q(p.publicKey)}")
                p.shortId?.takeIf { it.isNotBlank() }?.let { b.appendLine("      short-id: ${q(it)}") }
            }
        }
        p.network?.takeIf { it.isNotBlank() }?.let { network ->
            b.appendLine("    network: ${q(network)}")
            if (network.equals("ws", true)) {
                p.path?.takeIf { it.isNotBlank() }?.let { b.appendLine("    ws-opts:\n      path: ${q(it)}") }
                p.host?.takeIf { it.isNotBlank() }?.let { b.appendLine("      headers:\n        Host: ${q(it)}") }
            } else if (network.equals("grpc", true)) {
                p.serviceName?.takeIf { it.isNotBlank() }?.let { b.appendLine("    grpc-opts:\n      grpc-service-name: ${q(it)}") }
            }
        }
        b.appendLine("proxy-groups:")
        b.appendLine("  - name: PROXY")
        b.appendLine("    type: select")
        b.appendLine("    proxies: [game-node]")
        b.appendLine("rules:")
        b.appendLine("  - MATCH,PROXY")
        return b.toString()
    }
}
