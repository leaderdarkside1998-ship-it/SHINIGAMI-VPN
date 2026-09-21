package com.v2ray.ang.ui.optimize

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.OptimizeDiagnostics
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the Diagnostics screen with what the Gaming and Boost engines measure.
 *
 * The engines run in the `:daemon` process and this ViewModel lives in the main process, so it
 * cannot observe the engines' own flows (each process has its own copy of those objects, which is
 * why the screen used to show nothing but defaults). It polls the snapshots the engines publish
 * to the multi-process store instead, and only while the screen is on display.
 */
class DiagnosticsViewModel(application: Application) : BaseViewModel(application) {

    val gaming: StateFlow<OptimizeDiagnostics> = snapshots(
        mode = "GAMING",
        snapshotKey = AppConfig.DIAGNOSTICS_GAMING,
        enabledKey = AppConfig.PREF_GAMING_ENABLED,
        appsKey = AppConfig.PREF_GAMING_APPS_SET
    )

    val boost: StateFlow<OptimizeDiagnostics> = snapshots(
        mode = "BOOST",
        snapshotKey = AppConfig.DIAGNOSTICS_BOOST,
        enabledKey = AppConfig.PREF_BOOST_ENABLED,
        appsKey = AppConfig.PREF_BOOST_APPS_SET
    )

    private fun snapshots(
        mode: String,
        snapshotKey: String,
        enabledKey: String,
        appsKey: String
    ): StateFlow<OptimizeDiagnostics> {
        // A few small MMKV reads, cheap enough to seed the first frame as well.
        fun current() = OptimizeDiagnostics.resolve(
            published = OptimizeDiagnostics.read(snapshotKey),
            mode = mode,
            modeEnabled = MmkvManager.decodeSettingsBool(enabledKey, false),
            selectedAppCount = MmkvManager.decodeSettingsStringSet(appsKey)?.size ?: 0,
            nowMillis = System.currentTimeMillis()
        )

        return flow {
            while (true) {
                emit(current())
                delay(REFRESH_MILLIS)
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), current())
    }

    private companion object {
        const val REFRESH_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
