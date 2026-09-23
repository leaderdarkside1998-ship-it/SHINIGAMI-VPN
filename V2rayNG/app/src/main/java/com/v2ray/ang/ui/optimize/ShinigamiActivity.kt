package com.v2ray.ang.ui.optimize

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.base.BaseComponentActivity

class ShinigamiActivity : BaseComponentActivity() {

    private val viewModel: ShinigamiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val result by viewModel.result.collectAsStateWithLifecycle()
        val chatText by viewModel.chatText.collectAsStateWithLifecycle()

        ShinigamiScreen(
            isLoading = isLoading,
            result = result,
            chatText = chatText,
            onBackClick = { finish() },
            onPresetClick = { preset -> viewModel.analyze(this, preset) },
            onChatTextChanged = { viewModel.onChatTextChanged(it) },
            onChatSubmit = { viewModel.submitChat(this) },
            onConnect = { guid ->
                viewModel.connect(this, guid)
                finish()
            },
            onNewAnalysis = { viewModel.clearResult() }
        )
    }
}
