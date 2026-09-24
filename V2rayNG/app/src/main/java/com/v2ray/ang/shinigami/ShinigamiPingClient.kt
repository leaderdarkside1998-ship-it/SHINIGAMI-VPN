package com.v2ray.ang.shinigami

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Runs real-ping batches for SHINIGAMI through [com.v2ray.ang.service.CoreTestService], the same
 * `:tasks` process the server list's own "real ping" button uses.
 *
 * The probe needs the native core environment, which only the service processes initialise.
 * Probing from the UI process instead (what the analysis used to do) never produced results in
 * a useful time, so the screen sat on a spinner.
 */
class ShinigamiPingClient(context: Context) {

    private val app = context.applicationContext

    /**
     * Pings [guids] in one batch and reports every server as soon as its result arrives (on the
     * main thread, in the order the service finishes them). A delay below zero means the probe
     * failed. Returns true when the service finished the whole batch, false when the batch was
     * cut short (service cancelled or [timeoutMillis] elapsed); results already reported stay
     * valid. Cancelling the calling coroutine stops the running batch.
     */
    suspend fun pingBatch(
        guids: List<String>,
        timeoutMillis: Long,
        onResult: (guid: String, delayMillis: Long) -> Unit
    ): Boolean {
        if (guids.isEmpty()) return true

        // Every reply of the service echoes this id, so the server list screen's own tests
        // (which listen on the same broadcast action) are never mixed into this batch.
        val requestId = UUID.randomUUID().toString()
        val outcome = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val safeIntent = intent ?: return
                if (safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID) != requestId) return
                when (safeIntent.getIntExtra("key", 0)) {
                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS ->
                        safeIntent.serializable<RealPingResult>("content")?.let { onResult(it.guid, it.delayMillis) }

                    AppConfig.MSG_MEASURE_CONFIG_FINISH -> outcome.complete(true)
                    AppConfig.MSG_MEASURE_CONFIG_CANCEL -> outcome.complete(false)
                }
            }
        }

        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
        try {
            MessageHelper.sendMsg2TestService(
                app,
                TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, serverGuids = guids),
                requestId
            )
            return withTimeoutOrNull(timeoutMillis) { outcome.await() } ?: false
        } finally {
            runCatching { app.unregisterReceiver(receiver) }
                .onFailure { LogUtil.e(AppConfig.TAG, "ShinigamiPingClient: failed to unregister receiver", it) }
            if (!outcome.isCompleted) {
                // Timed out or the caller was cancelled while the batch was still running.
                MessageHelper.sendMsg2TestService(app, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL))
            }
        }
    }
}
