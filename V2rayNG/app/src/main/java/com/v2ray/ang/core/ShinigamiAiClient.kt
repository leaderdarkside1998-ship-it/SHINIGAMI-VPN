package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the SHINIGAMI AI Cloudflare Worker proxy (see /shinigami-proxy/worker.js).
 *
 * IMPORTANT — no secret belongs in this file:
 *  - The real Gemini API key lives ONLY as a Cloudflare Worker Secret, never here, never in any
 *    other app file, never in a string resource. This client never sees it.
 *  - [APP_SHARED_SECRET] below is NOT the Gemini key. It is a low-stakes header the Worker can
 *    optionally check just to make casual abuse of your Gemini quota harder. If it leaks, the
 *    worst case is someone else uses your Worker/quota — not an account or billing compromise.
 *    Leave it blank (empty string) if you didn't set APP_SHARED_SECRET on the Worker.
 *
 * Fill in [PROXY_URL] once the Worker is deployed (see worker.js's header comment for the
 * step-by-step). Everything else (network analysis, scoring) runs locally in the app and never
 * needs this client; only the "explain the result" / chat step of SHINIGAMI calls it.
 */
object ShinigamiAiClient {

    /** Replace with your deployed Worker URL, e.g. "https://shinigami-ai.<sub>.workers.dev/" */
    private const val PROXY_URL = "https://REPLACE-WITH-YOUR-WORKER-URL.workers.dev/"

    /** Optional. Must match the Worker's APP_SHARED_SECRET Secret, or leave "" to skip it. */
    private const val APP_SHARED_SECRET = ""

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val TAG = "ShinigamiAiClient"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    sealed interface AiResult {
        data class Success(val text: String) : AiResult
        /** AI unreachable/failed — callers must fall back to the local scoring explanation. */
        data class Failure(val reason: String) : AiResult
    }

    /**
     * Sends [prompt] (already built from LOCAL analyzer/scoring results — never raw server
     * secrets, hosts, or credentials) to the proxy and returns Gemini's text, or a [Failure] the
     * caller should treat as "AI unavailable" per the offline-fallback requirement — this call
     * failing must never block server selection.
     */
    suspend fun ask(prompt: String): AiResult = withContext(Dispatchers.IO) {
        if (PROXY_URL.contains("REPLACE-WITH-YOUR-WORKER-URL")) {
            return@withContext AiResult.Failure("Proxy URL not configured yet")
        }

        val payload = JsonObject().apply { addProperty("prompt", prompt) }
        val body = payload.toString().toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(PROXY_URL)
            .post(body)
        if (APP_SHARED_SECRET.isNotEmpty()) {
            requestBuilder.addHeader("X-App-Secret", APP_SHARED_SECRET)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    LogUtil.w(TAG, "SHINIGAMI AI proxy returned ${response.code}: $raw")
                    return@withContext AiResult.Failure("HTTP ${response.code}")
                }
                val text = runCatching {
                    JsonParser.parseString(raw).asJsonObject.get("text")?.asString
                }.getOrNull()
                if (text.isNullOrBlank()) {
                    AiResult.Failure("Empty response")
                } else {
                    AiResult.Success(text)
                }
            }
        } catch (e: IOException) {
            LogUtil.w(TAG, "SHINIGAMI AI proxy unreachable", e)
            AiResult.Failure(e.message ?: "Network error")
        }
    }
}
