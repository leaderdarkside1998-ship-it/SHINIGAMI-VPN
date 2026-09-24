package com.v2ray.ang.ui.optimize

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.core.text.BidiFormatter
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.RouteMetrics
import com.v2ray.ang.core.StabilityMeter
import com.v2ray.ang.shinigami.ShinigamiAnalysisPlan
import com.v2ray.ang.shinigami.ShinigamiAnalysisResult
import com.v2ray.ang.shinigami.ShinigamiExplainer
import com.v2ray.ang.shinigami.ShinigamiIntentClassifier
import com.v2ray.ang.shinigami.ShinigamiLogLevel
import com.v2ray.ang.shinigami.ShinigamiLogLine
import com.v2ray.ang.shinigami.ShinigamiNetworkAnalyzer
import com.v2ray.ang.shinigami.ShinigamiPingClient
import com.v2ray.ang.shinigami.ShinigamiPreset
import com.v2ray.ang.shinigami.ShinigamiScoringEngine
import com.v2ray.ang.shinigami.ShinigamiServerAnalyzer
import com.v2ray.ang.shinigami.ShinigamiServerScore
import com.v2ray.ang.shinigami.ShinigamiWeightsConfig
import com.v2ray.ang.shinigami.describe
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Follows the pipeline required by the spec:
 * User Selection -> Preset Selection -> Network Analyzer -> Server Analyzer -> Benchmark ->
 * Scoring Engine -> SHINIGAMI AI (explanation) -> Recommendation.
 *
 * The benchmark step pings every server the app has, several rounds, through the test service
 * process ([ShinigamiPingClient]), and narrates each step into [terminal] so the screen can show
 * the analysis live instead of a bare spinner.
 *
 * The explanation step ([ShinigamiExplainer]) is a local, deterministic generator, so an AI
 * outage can never block server selection - the Local Scoring Engine result is always shown
 * (spec: "AI نباید Single Point of Failure باشد").
 */
class ShinigamiViewModel(application: Application) : BaseViewModel(application) {

    private val _result = MutableStateFlow<ShinigamiAnalysisResult?>(null)
    val result: StateFlow<ShinigamiAnalysisResult?> = _result.asStateFlow()

    private val _terminal = MutableStateFlow<List<ShinigamiLogLine>>(emptyList())
    val terminal: StateFlow<List<ShinigamiLogLine>> = _terminal.asStateFlow()

    /** Overall progress of the running analysis, 0..1. */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _chatText = MutableStateFlow("")
    val chatText: StateFlow<String> = _chatText.asStateFlow()

    private var analysisJob: Job? = null

    fun onChatTextChanged(text: String) {
        _chatText.value = text
    }

    fun submitChat(context: Context) {
        val text = _chatText.value
        val preset = ShinigamiIntentClassifier.classify(text)
        if (preset == null) {
            toast(R.string.shinigami_chat_unrecognized)
            return
        }
        _chatText.value = ""
        analyze(context, preset)
    }

    fun analyze(context: Context, preset: ShinigamiPreset) {
        if (analysisJob?.isActive == true) return
        val appContext = context.applicationContext
        analysisJob = viewModelScope.launch {
            _isLoading.value = true
            _result.value = null
            _terminal.value = emptyList()
            _progress.value = 0f
            try {
                _result.value = runAnalysis(appContext, preset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Shinigami: analysis failed for preset ${preset.name}", e)
                toastError(R.string.shinigami_analysis_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Stops a running analysis (probes included) and returns to the start screen. */
    fun cancelAnalysis() {
        analysisJob?.cancel()
    }

    private suspend fun runAnalysis(appContext: Context, preset: ShinigamiPreset): ShinigamiAnalysisResult {
        log(ShinigamiLogLevel.CMD, getString(R.string.shinigami_log_cmd, preset.name.lowercase()))

        val network = withContext(Dispatchers.Default) { ShinigamiNetworkAnalyzer.snapshot(appContext) }
        log(ShinigamiLogLevel.INFO, getString(R.string.shinigami_log_network, network.describe()))

        val servers = withContext(Dispatchers.IO) { ShinigamiServerAnalyzer.listServers() }
        if (servers.isEmpty()) {
            log(ShinigamiLogLevel.WARN, getString(R.string.shinigami_no_servers))
            return ShinigamiAnalysisResult(
                preset = preset,
                network = network,
                ranked = emptyList(),
                explanation = getString(R.string.shinigami_no_servers),
                usedAi = false
            )
        }
        log(ShinigamiLogLevel.INFO, getString(R.string.shinigami_log_servers_found, servers.size))

        val names = servers.associate { it.guid to it.remarks }
        val samples = HashMap<String, MutableList<Long>>()
        val client = ShinigamiPingClient(appContext)
        val startedAt = SystemClock.elapsedRealtime()
        var candidates = servers.map { it.guid }

        for (round in 1..ShinigamiAnalysisPlan.ROUNDS) {
            if (candidates.isEmpty()) break
            val remaining = ShinigamiAnalysisPlan.TIME_BUDGET_MILLIS - (SystemClock.elapsedRealtime() - startedAt)
            if (remaining <= 0L) {
                log(ShinigamiLogLevel.WARN, getString(R.string.shinigami_log_budget))
                break
            }
            log(
                ShinigamiLogLevel.INFO,
                getString(R.string.shinigami_log_round, round, ShinigamiAnalysisPlan.ROUNDS, candidates.size)
            )

            val total = candidates.size
            var done = 0
            val finished = client.pingBatch(candidates, remaining) { guid, delayMillis ->
                samples.getOrPut(guid) { mutableListOf() }.add(delayMillis)
                done++
                _progress.value = ShinigamiAnalysisPlan.progress(round, done, total)
                val name = displayName(names[guid] ?: guid)
                if (delayMillis >= 0L) {
                    log(ShinigamiLogLevel.OK, getString(R.string.shinigami_log_probe_ok, round, done, total, name, delayMillis))
                } else {
                    log(ShinigamiLogLevel.ERROR, getString(R.string.shinigami_log_probe_fail, round, done, total, name))
                }
            }
            if (!finished) log(ShinigamiLogLevel.WARN, getString(R.string.shinigami_log_incomplete))

            candidates = ShinigamiAnalysisPlan.respondingServers(candidates, samples)
            if (round == 1) {
                log(
                    ShinigamiLogLevel.INFO,
                    getString(R.string.shinigami_log_responding, candidates.size, servers.size - candidates.size)
                )
            }
        }

        log(ShinigamiLogLevel.INFO, getString(R.string.shinigami_log_scoring, ShinigamiExplainer.presetTitle(preset)))
        _progress.value = 1f
        val weight = ShinigamiWeightsConfig.load().forPreset(preset)
        val ranked = withContext(Dispatchers.Default) {
            servers
                .map { server ->
                    val metrics: RouteMetrics = StabilityMeter.summarize(server.guid, samples[server.guid].orEmpty())
                    ShinigamiServerScore(
                        server = server,
                        metrics = metrics,
                        score = ShinigamiScoringEngine.score(metrics, weight, metrics.sampleCount)
                    )
                }
                .sortedWith(
                    compareByDescending<ShinigamiServerScore> { it.score ?: -1 }
                        .thenBy { if (it.metrics.isMeasurable) it.metrics.pingMillis else Long.MAX_VALUE }
                )
        }

        ranked.filter { it.score != null }.take(TOP_LOGGED).forEachIndexed { index, row ->
            log(
                ShinigamiLogLevel.INFO,
                getString(R.string.shinigami_log_rank, index + 1, displayName(row.server.remarks), row.score ?: 0)
            )
        }

        val best = ranked.firstOrNull { it.score != null }
        if (best != null) {
            log(
                ShinigamiLogLevel.OK,
                getString(R.string.shinigami_log_best, displayName(best.server.remarks), best.score ?: 0)
            )
        } else {
            log(ShinigamiLogLevel.ERROR, getString(R.string.shinigami_log_no_result))
        }
        // Leave the final line readable for a moment before the result screen replaces the terminal.
        delay(FINAL_LINE_HOLD_MILLIS)

        return ShinigamiAnalysisResult(
            preset = preset,
            network = network,
            ranked = ranked,
            explanation = ShinigamiExplainer.explain(preset, best),
            usedAi = false
        )
    }

    private fun log(level: ShinigamiLogLevel, text: String) {
        _terminal.update { (it + ShinigamiLogLine(level, text)).takeLast(MAX_LOG_LINES) }
    }

    /** Server names mix scripts and emoji; isolate them so they cannot reorder the log line around them. */
    private fun displayName(remarks: String): String = BidiFormatter.getInstance().unicodeWrap(remarks)

    fun clearResult() {
        _result.value = null
        _terminal.value = emptyList()
        _progress.value = 0f
    }

    /** Selects [guid] as the active server and starts the VPN service, reusing the app's own
     * connect path - SHINIGAMI never re-implements connection logic. */
    fun connect(context: Context, guid: String) {
        LauncherManager.startService(context, guid)
    }

    private companion object {
        const val MAX_LOG_LINES = 500
        const val TOP_LOGGED = 3
        const val FINAL_LINE_HOLD_MILLIS = 1_200L
    }
}
