package com.v2ray.ang.ui.optimize

import android.app.Application
import android.content.Context
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.RouteMetrics
import com.v2ray.ang.shinigami.ShinigamiAnalysisResult
import com.v2ray.ang.shinigami.ShinigamiExplainer
import com.v2ray.ang.shinigami.ShinigamiIntentClassifier
import com.v2ray.ang.shinigami.ShinigamiNetworkAnalyzer
import com.v2ray.ang.shinigami.ShinigamiPreset
import com.v2ray.ang.shinigami.ShinigamiScoringEngine
import com.v2ray.ang.shinigami.ShinigamiServerAnalyzer
import com.v2ray.ang.shinigami.ShinigamiServerScore
import com.v2ray.ang.shinigami.ShinigamiWeightsConfig
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Follows the pipeline required by the spec:
 * User Selection -> Preset Selection -> Network Analyzer -> Server Analyzer -> Benchmark ->
 * Scoring Engine -> SHINIGAMI AI (explanation) -> Recommendation.
 *
 * The explanation step ([ShinigamiExplainer]) is a local, deterministic generator, so an AI
 * outage can never block server selection - the Local Scoring Engine result is always shown
 * (spec: "AI نباید Single Point of Failure باشد").
 */
class ShinigamiViewModel(application: Application) : BaseViewModel(application) {

    private val _result = MutableStateFlow<ShinigamiAnalysisResult?>(null)
    val result: StateFlow<ShinigamiAnalysisResult?> = _result.asStateFlow()

    private val _chatText = MutableStateFlow("")
    val chatText: StateFlow<String> = _chatText.asStateFlow()

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
        val appContext = context.applicationContext
        launchLoading {
            val network = ShinigamiNetworkAnalyzer.snapshot(appContext)
            val servers = ShinigamiServerAnalyzer.listServers()
            if (servers.isEmpty()) {
                _result.value = ShinigamiAnalysisResult(
                    preset = preset,
                    network = network,
                    ranked = emptyList(),
                    explanation = "هیچ سروری در برنامه پیدا نشد. ابتدا یک Subscription یا Config اضافه کنید.",
                    usedAi = false
                )
                return@launchLoading
            }

            val metricsByGuid = ShinigamiServerAnalyzer.measureAll(appContext, servers)
            val weight = ShinigamiWeightsConfig.load().forPreset(preset)

            val scored = servers.map { server ->
                val metrics = metricsByGuid[server.guid]
                    ?: RouteMetrics(guid = server.guid)
                val score = ShinigamiScoringEngine.score(metrics, weight, metrics.sampleCount)
                ShinigamiServerScore(server = server, metrics = metrics, score = score)
            }

            val ranked = scored.sortedWith(
                compareByDescending<ShinigamiServerScore> { it.score ?: -1 }
            )

            val explanation = ShinigamiExplainer.explain(preset, ranked.firstOrNull { it.score != null })

            _result.value = ShinigamiAnalysisResult(
                preset = preset,
                network = network,
                ranked = ranked,
                explanation = explanation,
                usedAi = false
            )
        }
    }

    fun clearResult() {
        _result.value = null
    }

    /** Selects [guid] as the active server and starts the VPN service, reusing the app's own
     * connect path - SHINIGAMI never re-implements connection logic. */
    fun connect(context: Context, guid: String) {
        LauncherManager.startService(context, guid)
    }
}
