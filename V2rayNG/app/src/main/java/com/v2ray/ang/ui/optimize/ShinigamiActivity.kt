package com.v2ray.ang.ui.optimize

import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity

class ShinigamiActivity : BaseComponentActivity() {

    private val viewModel: ShinigamiViewModel by viewModels()

    /** Guid awaiting connection while the system VPN consent dialog is showing. */
    private var pendingConnectGuid: String? = null

    // Mirrors MainActivity's requestVpnPermission flow: SHINIGAMI must not skip the VPN
    // consent dialog, or connect() silently fails to establish the tunnel when permission
    // hasn't been granted yet.
    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val guid = pendingConnectGuid
            pendingConnectGuid = null
            if (result.resultCode == RESULT_OK && guid != null) {
                viewModel.connect(this, guid)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val result by viewModel.result.collectAsStateWithLifecycle()
        val terminal by viewModel.terminal.collectAsStateWithLifecycle()
        val progress by viewModel.progress.collectAsStateWithLifecycle()
        val chatText by viewModel.chatText.collectAsStateWithLifecycle()

        ShinigamiScreen(
            isLoading = isLoading,
            result = result,
            terminal = terminal,
            progress = progress,
            chatText = chatText,
            onBackClick = { finish() },
            onPresetClick = { preset -> viewModel.analyze(this, preset) },
            onChatTextChanged = { viewModel.onChatTextChanged(it) },
            onChatSubmit = { viewModel.submitChat(this) },
            onConnect = { guid -> connectToServer(guid) },
            onNewAnalysis = { viewModel.clearResult() },
            onCancelAnalysis = { viewModel.cancelAnalysis() }
        )
    }

    private fun connectToServer(guid: String) {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                pendingConnectGuid = guid
                requestVpnPermission.launch(intent)
                return
            }
        }
        viewModel.connect(this, guid)
        finish()
    }
}
