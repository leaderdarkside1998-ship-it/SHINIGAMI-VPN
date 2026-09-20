package com.v2ray.ang.ui.optimize

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import androidx.compose.ui.res.stringResource

class BoostActivity : BaseComponentActivity() {

    private val viewModel: BoostAppsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val enabled by viewModel.enabled.collectAsStateWithLifecycle()
        val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()

        AppPickerScreen(
            title = stringResource(R.string.title_boost_mode),
            switchLabel = stringResource(R.string.summary_boost_mode),
            apps = apps,
            isLoading = isLoading,
            enabled = enabled,
            selectedApps = selectedApps,
            onBackClick = { finish() },
            onEnabledChanged = { viewModel.setEnabled(it) },
            onToggleApp = { viewModel.toggleApp(it) },
            onSearch = { viewModel.filterApps(it) }
        )
    }
}
