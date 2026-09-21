package com.v2ray.ang.ui.optimize

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.base.BaseComponentActivity

class SelectGameActivity : BaseComponentActivity() {

    private val viewModel: SelectGameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val activePackage by viewModel.activePackage.collectAsStateWithLifecycle()

        SelectGameScreen(
            apps = apps,
            isLoading = isLoading,
            activePackage = activePackage,
            onBackClick = { finish() },
            onSelectGame = { packageName ->
                viewModel.selectGame(this, packageName)
                finish()
            },
            onEndSession = { viewModel.endExclusiveSession(this) },
            onSearch = { viewModel.filterApps(it) }
        )
    }
}
