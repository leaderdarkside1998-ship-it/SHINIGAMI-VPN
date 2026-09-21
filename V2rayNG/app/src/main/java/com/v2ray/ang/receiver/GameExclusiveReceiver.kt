package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.GameExclusiveManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ends the current Select Game exclusive session from the notification's action button.
 * Runs off the main thread since ending a root session runs blocking `su` shell commands.
 */
class GameExclusiveReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "com.v2ray.ang.action.GAME_EXCLUSIVE_STOP"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GameExclusiveManager.stop(appContext)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "GameExclusiveReceiver: failed to end session", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
