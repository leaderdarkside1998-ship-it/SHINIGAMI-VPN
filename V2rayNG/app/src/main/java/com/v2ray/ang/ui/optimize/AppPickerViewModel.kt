package com.v2ray.ang.ui.optimize

import android.app.Application
import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
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

/**
 * Shared logic for the Gaming and Boost app pickers: both let the user pick, from the phone's
 * installed apps, which ones the mode should apply to. The concrete subclasses only differ in
 * which MMKV keys they read/write.
 */
abstract class AppPickerViewModel(application: Application) : BaseViewModel(application) {

    protected abstract val enabledKey: String
    protected abstract val selectedSetKey: String

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _selectedApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedApps: StateFlow<Set<String>> = _selectedApps.asStateFlow()

    private val _displayedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val displayedApps: StateFlow<List<AppInfo>> = _displayedApps.asStateFlow()

    private var appsAll: List<AppInfo>? = null
    private var currentQuery = ""
    private var isAppListLoading = false

    init {
        _enabled.value = MmkvManager.decodeSettingsBool(enabledKey, false)
        _selectedApps.value = MmkvManager.decodeSettingsStringSet(selectedSetKey)?.toSet() ?: emptySet()
    }

    fun setEnabled(value: Boolean) {
        if (_enabled.value != value) {
            _enabled.value = value
            MmkvManager.encodeSettings(enabledKey, value)
        }
    }

    fun toggleApp(packageName: String) {
        val current = _selectedApps.value
        val updated = if (packageName in current) current - packageName else current + packageName
        _selectedApps.value = updated
        MmkvManager.encodeSettings(selectedSetKey, updated.toMutableSet())
        SettingsChangeManager.makeRestartService()
    }

    fun loadApps(context: Context) {
        if (appsAll != null || isAppListLoading) return
        val applicationContext = context.applicationContext
        isAppListLoading = true
        launchLoading {
            try {
                val apps = withContext(Dispatchers.IO) {
                    sortApps(AppManagerUtil.loadNetworkAppList(applicationContext))
                }
                appsAll = apps
                _displayedApps.value = applyFilter(currentQuery)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.ANG_PACKAGE, "Error loading apps", e)
            } finally {
                isAppListLoading = false
            }
        }
    }

    fun filterApps(query: String) {
        currentQuery = query
        _displayedApps.value = applyFilter(query)
    }

    private fun applyFilter(query: String): List<AppInfo> {
        val apps = appsAll ?: return emptyList()
        if (query.isEmpty()) return apps
        return apps.filter {
            it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        val selected = _selectedApps.value
        return apps.sortedWith { p1, p2 ->
            val s1 = p1.packageName in selected
            val s2 = p2.packageName in selected
            when {
                s1 && !s2 -> -1
                !s1 && s2 -> 1
                p1.isSystemApp && !p2.isSystemApp -> 1
                !p1.isSystemApp && p2.isSystemApp -> -1
                else -> collator.compare(p1.appName, p2.appName)
            }
        }
    }
}

/** Picks which installed games should be routed through the lowest-ping server, with no delay. */
class GamingAppsViewModel(application: Application) : AppPickerViewModel(application) {
    override val enabledKey = AppConfig.PREF_GAMING_ENABLED
    override val selectedSetKey = AppConfig.PREF_GAMING_APPS_SET

    private val _routeLocked = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_GAMING_ROUTE_LOCK, false)
    )
    val routeLocked: StateFlow<Boolean> = _routeLocked.asStateFlow()

    fun setRouteLocked(value: Boolean) {
        _routeLocked.value = value
        MmkvManager.encodeSettings(AppConfig.PREF_GAMING_ROUTE_LOCK, value)
    }
}

/** Picks which installed apps (Telegram, YouTube, etc.) BOOST should keep on the fastest server. */
class BoostAppsViewModel(application: Application) : AppPickerViewModel(application) {
    override val enabledKey = AppConfig.PREF_BOOST_ENABLED
    override val selectedSetKey = AppConfig.PREF_BOOST_APPS_SET
}
