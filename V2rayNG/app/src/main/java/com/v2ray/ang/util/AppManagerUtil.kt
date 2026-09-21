package com.v2ray.ang.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.v2ray.ang.dto.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManagerUtil {
    /**
     * Load the list of network applications.
     *
     * @param context The context to use.
     * @return A list of AppInfo objects representing the network applications.
     */
    suspend fun loadNetworkAppList(context: Context): ArrayList<AppInfo> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val apps = ArrayList<AppInfo>()

            for (pkg in packages) {
                val applicationInfo = pkg.applicationInfo ?: continue

                val appName = applicationInfo.loadLabel(packageManager).toString()
                val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM > 0

                val appInfo = AppInfo(appName, pkg.packageName, isSystemApp, 0)
                apps.add(appInfo)
            }

            return@withContext apps
        }

    fun getLastUpdateTime(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    /**
     * Load only the installed apps that have their own launcher entry (i.e. something the user
     * can actually open), for Select Game's picker. Apps self-tagged as [ApplicationInfo.CATEGORY_GAME]
     * are marked via [AppInfo.isSelected] == 1 so callers can sort likely games first; many games
     * don't set this category, so the full launchable list is still returned rather than filtered
     * down to it.
     */
    suspend fun loadLaunchableAppList(context: Context): ArrayList<AppInfo> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledPackages(0)
            val apps = ArrayList<AppInfo>()

            for (pkg in packages) {
                val applicationInfo = pkg.applicationInfo ?: continue
                if (packageManager.getLaunchIntentForPackage(pkg.packageName) == null) continue

                val appName = applicationInfo.loadLabel(packageManager).toString()
                val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM > 0
                val isGame = isLikelyGame(applicationInfo)

                apps.add(AppInfo(appName, pkg.packageName, isSystemApp, if (isGame) 1 else 0))
            }

            return@withContext apps
        }

    private fun isLikelyGame(applicationInfo: ApplicationInfo): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (applicationInfo.category == ApplicationInfo.CATEGORY_GAME) return true
        }
        @Suppress("DEPRECATION")
        return applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0
    }
}
