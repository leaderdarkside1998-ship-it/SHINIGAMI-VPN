package com.v2ray.ang.ui.optimize

import android.app.Application
import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.GameExclusiveManager
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.Collator

class SelectGameViewModel(application: Application) : BaseViewModel(application) {

    private val _displayedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val displayedApps: StateFlow<List<AppInfo>> = _displayedApps.asStateFlow()

    private val _activePackage = MutableStateFlow(GameExclusiveManager.activePackage())
    val activePackage: StateFlow<String?> = _activePackage.asStateFlow()

    private var appsAll: List<AppInfo>? = null
    private var currentQuery = ""
    private var isAppListLoading = false

    fun loadApps(context: Context) {
        if (appsAll != null || isAppListLoading) return
        val applicationContext = context.applicationContext
        isAppListLoading = true
        launchLoading {
            try {
                val apps = withContext(Dispatchers.IO) {
                    sortApps(AppManagerUtil.loadLaunchableAppList(applicationContext))
                }
                appsAll = apps
                _displayedApps.value = applyFilter(currentQuery)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.ANG_PACKAGE, "SelectGame: error loading apps", e)
            } finally {
                isAppListLoading = false
            }
        }
    }

    fun filterApps(query: String) {
        currentQuery = query
        _displayedApps.value = applyFilter(query)
    }

    /** Starts (or switches) the exclusive session for [packageName] and launches it. */
    fun selectGame(context: Context, packageName: String) {
        // GameExclusiveManager.start() is main-thread safe: it does its fast MMKV/launch work
        // inline and runs the slower root/service-restart work on its own background scope.
        GameExclusiveManager.start(context.applicationContext, packageName)
        _activePackage.value = GameExclusiveManager.activePackage()
    }

    /** Ends the current exclusive session without picking a new game. */
    fun endExclusiveSession(context: Context) {
        GameExclusiveManager.stop(context.applicationContext)
        _activePackage.value = GameExclusiveManager.activePackage()
    }

    private fun applyFilter(query: String): List<AppInfo> {
        val apps = appsAll ?: return emptyList()
        if (query.isEmpty()) return apps
        return apps.filter {
            it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    /** Likely games first (see [AppManagerUtil.loadLaunchableAppList]), then alphabetically. */
    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        return apps.sortedWith { p1, p2 ->
            when {
                p1.isSelected != p2.isSelected -> p2.isSelected - p1.isSelected
                else -> collator.compare(p1.appName, p2.appName)
            }
        }
    }
}
