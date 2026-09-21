package com.v2ray.ang.root

import android.content.Context
import android.content.pm.PackageManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil

/**
 * Root-only, best-effort "true exclusive" firewall for Select Game.
 *
 * [VpnService.Builder.addAllowedApplication] only decides which apps' traffic enters *this*
 * app's tunnel -- any app left out simply keeps using the phone's normal connection directly,
 * it does not lose internet. There is no public, non-root Android API that lets a regular app
 * cut another app's network access outright. On a rooted device this object closes that gap for
 * real, by dropping every other user-facing app's outbound packets at the kernel (iptables) level
 * by UID, so only the selected game (and this app itself, which still needs network to run the
 * tunnel) can reach the internet. On a non-rooted device, callers should treat "exclusive" as
 * "only this app is routed through the VPN tunnel" instead -- see [com.v2ray.ang.core.GameExclusiveManager].
 */
object RootFirewall {

    private const val CHAIN = "OUTPUT"

    /**
     * Blocks outbound traffic for every installed, launchable app except [keepPackage] and this
     * app itself. Safe to call repeatedly; it always rebuilds the block list from scratch after
     * clearing any rules it previously installed.
     *
     * @return true if the block was (re)installed, false if root isn't available or nothing
     * needed blocking.
     */
    fun blockAllExcept(context: Context, keepPackage: String): Boolean {
        if (!RootManager.cachedRoot()) return false

        // Always start from a clean slate so re-picking a different game doesn't leave the
        // previous game's UID blocked too.
        unblockAll(context)

        val pm = context.packageManager
        val selfPackage = BuildConfig.APPLICATION_ID
        val uids = launchableAppUids(pm)
            .filterKeys { it != keepPackage && it != selfPackage }
            .values
            .toSortedSet()

        if (uids.isEmpty()) return false

        val script = buildString {
            uids.forEach { uid ->
                appendLine("iptables -I $CHAIN -m owner --uid-owner $uid -j DROP 2>/dev/null")
                appendLine("ip6tables -I $CHAIN -m owner --uid-owner $uid -j DROP 2>/dev/null")
            }
        }
        val result = RootShell.runScript(context, "game_exclusive_block.sh", script)
        if (!result.success) {
            LogUtil.w(AppConfig.TAG, "RootFirewall: block script reported errors: ${result.output}")
        }

        MmkvManager.encodeSettings(
            AppConfig.PREF_GAME_EXCLUSIVE_BLOCKED_UIDS,
            uids.map { it.toString() }.toMutableSet()
        )
        return true
    }

    /** Removes every rule this object previously installed, if any. */
    fun unblockAll(context: Context) {
        val uids = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_GAME_EXCLUSIVE_BLOCKED_UIDS)
        if (uids.isNullOrEmpty()) return

        val script = buildString {
            uids.forEach { uid ->
                // -D only removes a rule that exists; repeat is harmless if it's already gone.
                appendLine("iptables -D $CHAIN -m owner --uid-owner $uid -j DROP 2>/dev/null")
                appendLine("ip6tables -D $CHAIN -m owner --uid-owner $uid -j DROP 2>/dev/null")
            }
        }
        val result = RootShell.runScript(context, "game_exclusive_unblock.sh", script)
        if (!result.success) {
            LogUtil.w(AppConfig.TAG, "RootFirewall: unblock script reported errors: ${result.output}")
        }
        MmkvManager.encodeSettings(AppConfig.PREF_GAME_EXCLUSIVE_BLOCKED_UIDS, mutableSetOf())
    }

    /**
     * Maps package name -> UID for every installed app that has its own launcher entry (i.e. an
     * actual user-facing app, not a background system component). Several packages can legally
     * share one UID (shared user IDs); that's fine here since we only ever act on the UID.
     */
    private fun launchableAppUids(pm: PackageManager): Map<String, Int> {
        val launchable = HashMap<String, Int>()
        try {
            for (pkg in pm.getInstalledPackages(0)) {
                val appInfo = pkg.applicationInfo ?: continue
                if (pm.getLaunchIntentForPackage(pkg.packageName) != null) {
                    launchable[pkg.packageName] = appInfo.uid
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootFirewall: failed to enumerate installed apps", e)
        }
        return launchable
    }
}
