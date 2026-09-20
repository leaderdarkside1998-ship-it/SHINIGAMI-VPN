package com.v2ray.ang.ui.optimize

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.BoostEngine
import com.v2ray.ang.core.DnsAutoEngine
import com.v2ray.ang.core.DnsDiagnostics
import com.v2ray.ang.core.GamingEngine
import com.v2ray.ang.core.OptimizeDiagnostics
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NA = "N/A"

class DiagnosticsActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        val gaming by GamingEngine.diagnostics.collectAsState()
        val boost by BoostEngine.diagnostics.collectAsState()
        val dns by DnsAutoEngine.diagnostics.collectAsState()
        var autoDnsEnabled by remember {
            mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_DNS_ENABLED, false))
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.title_diagnostics),
                    onBackClick = { finish() },
                    isLoading = false
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.title_auto_dns),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoDnsEnabled,
                            modifier = Modifier.scale(0.8f),
                            onCheckedChange = {
                                autoDnsEnabled = it
                                MmkvManager.encodeSettings(AppConfig.PREF_AUTO_DNS_ENABLED, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    HorizontalDivider()
                }
                item { SectionTitle("GAMING") }
                items(gamingRows(gaming)) { DiagnosticRow(it.first, it.second) }
                item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

                item { SectionTitle("BOOST") }
                items(boostRows(boost)) { DiagnosticRow(it.first, it.second) }
                item { HorizontalDivider() }

                item { SectionTitle("DNS ENGINE") }
                items(dnsRows(dns)) { DiagnosticRow(it.first, it.second) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(millis: Long?): String = millis?.let { timeFormat.format(Date(it)) } ?: NA
private fun formatMillis(v: Long?): String = v?.let { "${it}ms" } ?: NA
private fun formatPercent(v: Int?): String = v?.let { "$it%" } ?: NA
private fun formatScore(v: Double?): String = v?.let { "%.0f".format(it) } ?: NA

private fun gamingRows(d: OptimizeDiagnostics): List<Pair<String, String>> = listOf(
    "MODE" to d.mode,
    "GAMING ENABLED" to if (d.modeEnabled) "YES" else "NO",
    "SELECTED APPS" to d.selectedAppCount.toString(),
    "VPN" to if (d.vpnActive) "ACTIVE" else "INACTIVE",
    "PER-APP ROUTING" to if (d.perAppRoutingActive) "ACTIVE" else "INACTIVE",
    "GAME" to d.label,
    "ROUTE" to (d.routeName ?: NA),
    "SERVER" to (d.server ?: NA),
    "TRANSPORT" to (d.transport ?: NA),
    "NETWORK" to d.network,
    "DNS" to (d.dns ?: NA),
    "PING" to formatMillis(d.pingMillis),
    "JITTER" to formatMillis(d.jitterMillis),
    "PACKET LOSS" to formatPercent(d.lossPercent),
    "STABILITY" to formatScore(d.stabilityScore),
    "ROUTE SCORE" to formatScore(d.routeScore),
    "STATUS" to d.status,
    "ROUTE LOCK" to if (d.routeLock) "ON" else "OFF",
    "LAST CHECK" to formatTime(d.lastCheckMillis)
)

private fun boostRows(d: OptimizeDiagnostics): List<Pair<String, String>> = listOf(
    "MODE" to d.mode,
    "APP" to d.label,
    "ROUTE" to (d.routeName ?: NA),
    "DNS" to (d.dns ?: NA),
    "PING" to formatMillis(d.pingMillis),
    "JITTER" to formatMillis(d.jitterMillis),
    "PACKET LOSS" to formatPercent(d.lossPercent),
    "STABILITY" to formatScore(d.stabilityScore),
    "STATUS" to d.status,
    "LAST CHECK" to formatTime(d.lastCheckMillis)
)

private fun dnsRows(d: DnsDiagnostics): List<Pair<String, String>> = listOf(
    "DNS ENGINE" to "AUTO",
    "CURRENT DNS" to (d.currentDns ?: NA),
    "DNS RESPONSE" to formatMillis(d.dnsResponseMillis),
    "SUCCESS RATE" to formatPercent(d.successRatePercent),
    "DNS SCORE" to formatScore(d.dnsScore),
    "LAST BENCHMARK" to formatTime(d.lastBenchmarkMillis)
)
