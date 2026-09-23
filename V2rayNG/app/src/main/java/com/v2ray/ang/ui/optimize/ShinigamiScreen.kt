package com.v2ray.ang.ui.optimize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.shinigami.ShinigamiAnalysisResult
import com.v2ray.ang.shinigami.ShinigamiExplainer
import com.v2ray.ang.shinigami.ShinigamiPreset
import com.v2ray.ang.shinigami.ShinigamiServerScore
import com.v2ray.ang.ui.compose.AnimatedShinigamiIcon
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding

private data class PresetOption(
    val preset: ShinigamiPreset,
    val emoji: String,
    val titleRes: Int,
    val questionRes: Int
)

private val presetOptions = listOf(
    PresetOption(ShinigamiPreset.GAMING, "🎮", R.string.shinigami_preset_gaming_title, R.string.shinigami_preset_gaming_question),
    PresetOption(ShinigamiPreset.INSTAGRAM, "📱", R.string.shinigami_preset_instagram_title, R.string.shinigami_preset_instagram_question),
    PresetOption(ShinigamiPreset.DOWNLOAD, "⬇️", R.string.shinigami_preset_download_title, R.string.shinigami_preset_download_question),
    PresetOption(ShinigamiPreset.VOICE_CALL, "🎙️", R.string.shinigami_preset_voice_title, R.string.shinigami_preset_voice_question),
    PresetOption(ShinigamiPreset.STREAMING, "📺", R.string.shinigami_preset_streaming_title, R.string.shinigami_preset_streaming_question)
)

@Composable
fun ShinigamiScreen(
    isLoading: Boolean,
    result: ShinigamiAnalysisResult?,
    chatText: String,
    onBackClick: () -> Unit,
    onPresetClick: (ShinigamiPreset) -> Unit,
    onChatTextChanged: (String) -> Unit,
    onChatSubmit: () -> Unit,
    onConnect: (String) -> Unit,
    onNewAnalysis: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.shinigami_title),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> LoadingContent()
                result != null -> ResultContent(result, onConnect = onConnect, onNewAnalysis = onNewAnalysis)
                else -> HomeContent(
                    chatText = chatText,
                    onPresetClick = onPresetClick,
                    onChatTextChanged = onChatTextChanged,
                    onChatSubmit = onChatSubmit
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.shinigami_analyzing),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HomeContent(
    chatText: String,
    onPresetClick: (ShinigamiPreset) -> Unit,
    onChatTextChanged: (String) -> Unit,
    onChatSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = NavigationBarsBottomPadding()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedShinigamiIcon(iconSize = 28.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.shinigami_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(presetOptions) { option ->
            PresetCard(option, onClick = { onPresetClick(option.preset) })
        }
        item {
            ChatInputRow(
                text = chatText,
                onTextChanged = onChatTextChanged,
                onSubmit = onChatSubmit
            )
        }
    }
}

@Composable
private fun PresetCard(option: PresetOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = option.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(option.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(option.questionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatInputRow(text: String, onTextChanged: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.shinigami_chat_placeholder)) },
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onSubmit, enabled = text.isNotBlank()) {
            Icon(painterResource(R.drawable.ic_send_24dp), contentDescription = stringResource(R.string.shinigami_chat_send))
        }
    }
}

@Composable
private fun ResultContent(
    result: ShinigamiAnalysisResult,
    onConnect: (String) -> Unit,
    onNewAnalysis: () -> Unit
) {
    val best = result.best
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = NavigationBarsBottomPadding()
    ) {
        item {
            Text(
                text = "${ShinigamiExplainer.presetTitle(result.preset)} ${stringResource(R.string.shinigami_analysis_complete)}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (best != null) {
            item { RecommendedServerCard(best, onConnect = onConnect) }
        }
        item {
            Text(
                text = result.explanation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            Text(
                text = stringResource(R.string.shinigami_comparison_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(result.ranked) { row -> ComparisonRow(row) }
        item {
            OutlinedButton(
                onClick = onNewAnalysis,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(stringResource(R.string.shinigami_new_analysis))
            }
        }
    }
}

@Composable
private fun RecommendedServerCard(best: ShinigamiServerScore, onConnect: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = best.server.remarks,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.shinigami_score_format, best.score ?: 0),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            MetricsRow(best)
            Spacer(modifier = Modifier.height(12.dp))
            ElevatedButton(
                onClick = { onConnect(best.server.guid) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.shinigami_connect))
            }
        }
    }
}

@Composable
private fun MetricsRow(row: ShinigamiServerScore) {
    val m = row.metrics
    Column {
        Text(stringResource(R.string.shinigami_metric_ping, m.pingMillis), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.shinigami_metric_jitter, m.jitterMillis), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.shinigami_metric_loss, m.lossPercent), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ComparisonRow(row: ShinigamiServerScore) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.server.remarks,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.score?.toString() ?: stringResource(R.string.shinigami_score_na),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
