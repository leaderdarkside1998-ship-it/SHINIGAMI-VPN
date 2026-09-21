package com.v2ray.ang.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.receiver.GameExclusiveReceiver
import com.v2ray.ang.root.RootFirewall
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Backs the "Select Game" feature: the user manually picks one installed game, the app launches
 * it, and for as long as the session is active every byte of the phone's tunnel capacity is
 * reserved for that game alone.
 *
 * Two layers, depending on what the device allows:
 *  - Always (no root needed): the VPN's per-app allow-list is narrowed to just the selected
 *    game, via the existing [AppConfig.PREF_PER_APP_PROXY_SET] mechanism that
 *    `CoreVpnService.configurePerAppProxy()` already reads. Only the game's traffic uses the
 *    tunnel/proxy; nothing else competes for its bandwidth.
 *  - Root only: [RootFirewall] additionally drops every other app's outbound packets by UID, so
 *    those apps genuinely lose internet rather than just falling back to a direct connection.
 *    Without root, Android's public APIs give no way to cut another app's network access outright
 *    -- that limitation is real and is surfaced to the user rather than silently pretended away.
 *
 * [start] and [stop] are safe to call from the main thread: the fast MMKV/launch work runs
 * inline, while the potentially slow root and service-restart work runs on a dedicated
 * background scope that outlives whichever screen triggered it (mirrors [com.v2ray.ang.root.RootLanSharing]).
 */
object GameExclusiveManager {

    private const val NOTIFICATION_ID = 2
    private const val NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.game_exclusive"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var sessionJob: Job? = null

    fun isActive(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAME_EXCLUSIVE_MODE, false)

    fun activePackage(): String? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_GAME_EXCLUSIVE_PACKAGE)?.takeIf { isActive() && it.isNotEmpty() }

    /** Starts (or switches) an exclusive session for [packageName] and launches it. */
    fun start(context: Context, packageName: String) {
        val appContext = context.applicationContext
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            appContext.toast(R.string.toast_select_game_not_launchable)
            return
        }

        if (!isActive()) {
            // Only snapshot on the very first session -- switching games mid-session must not
            // overwrite the snapshot with the exclusive settings we ourselves installed.
            snapshotCurrentPerAppProxySettings()
        }

        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, false)
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, mutableSetOf(packageName))
        MmkvManager.encodeSettings(AppConfig.PREF_GAMING_ENABLED, true)
        MmkvManager.encodeSettings(AppConfig.PREF_GAMING_APPS_SET, mutableSetOf(packageName))
        MmkvManager.encodeSettings(AppConfig.PREF_GAME_EXCLUSIVE_MODE, true)
        MmkvManager.encodeSettings(AppConfig.PREF_GAME_EXCLUSIVE_PACKAGE, packageName)

        appContext.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        sessionJob?.cancel()
        sessionJob = scope.launch {
            // RootFirewall itself checks RootManager.cachedRoot() and no-ops without root.
            val usedRoot = RootFirewall.blockAllExcept(appContext, packageName)
            if (CoreServiceManager.isRunning() == true) {
                LauncherManager.restartService(appContext)
            }
            showNotification(appContext, packageName, usedRoot)
        }
    }

    /** Ends the current exclusive session, if any, restoring the routing that preceded it. */
    fun stop(context: Context) {
        if (!isActive()) return
        val appContext = context.applicationContext

        val savedProxyEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAME_EXCLUSIVE_SAVED_PER_APP_PROXY, false)
        val savedProxySet = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_GAME_EXCLUSIVE_SAVED_PER_APP_PROXY_SET) ?: mutableSetOf()
        val savedBypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAME_EXCLUSIVE_SAVED_BYPASS_APPS, false)

        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, savedProxyEnabled)
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, savedProxySet)
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, savedBypass)
        MmkvManager.encodeSettings(AppConfig.PREF_GAMING_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_GAMING_APPS_SET, mutableSetOf())
        MmkvManager.encodeSettings(AppConfig.PREF_GAME_EXCLUSIVE_MODE, false)
        MmkvManager.encodeSettings(AppConfig.PREF_GAME_EXCLUSIVE_PACKAGE, "")

        appContext.getSystemService<SystemNotificationManager>()?.cancel(NOTIFICATION_ID)

        sessionJob?.cancel()
        sessionJob = scope.launch {
            RootFirewall.unblockAll(appContext)
            if (CoreServiceManager.isRunning() == true) {
                LauncherManager.restartService(appContext)
            }
        }
    }

    private fun snapshotCurrentPerAppProxySettings() {
        MmkvManager.encodeSettings(
            AppConfig.PREF_GAME_EXCLUSIVE_SAVED_PER_APP_PROXY,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        )
        MmkvManager.encodeSettings(
            AppConfig.PREF_GAME_EXCLUSIVE_SAVED_PER_APP_PROXY_SET,
            MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET) ?: mutableSetOf()
        )
        MmkvManager.encodeSettings(
            AppConfig.PREF_GAME_EXCLUSIVE_SAVED_BYPASS_APPS,
            MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
        )
    }

    private fun showNotification(context: Context, packageName: String, usedRoot: Boolean) {
        val appName = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "GameExclusiveManager: failed to resolve app label for $packageName", e)
            packageName
        }

        val notificationChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.title_select_game),
                SystemNotificationManager.IMPORTANCE_LOW
            )
            channel.lightColor = Color.DKGRAY
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            context.getSystemService<SystemNotificationManager>()?.createNotificationChannel(channel)
            NOTIFICATION_CHANNEL_ID
        } else {
            ""
        }

        val stopIntent = Intent(context, GameExclusiveReceiver::class.java).apply {
            action = GameExclusiveReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val summary = context.getString(
            if (usedRoot) R.string.notification_game_exclusive_root else R.string.notification_game_exclusive_no_root
        )

        val notification = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.notification_game_exclusive_title, appName))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_delete_24dp, context.getString(R.string.notification_action_end_game_exclusive), stopPendingIntent)
            .build()

        context.getSystemService<SystemNotificationManager>()?.notify(NOTIFICATION_ID, notification)
    }
}
