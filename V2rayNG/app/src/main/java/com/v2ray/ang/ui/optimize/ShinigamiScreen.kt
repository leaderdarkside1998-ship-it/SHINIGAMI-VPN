package com.v2ray.ang.ui.optimize

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.shinigami.ShinigamiAnalysisResult
import com.v2ray.ang.shinigami.ShinigamiExplainer
import com.v2ray.ang.shinigami.ShinigamiLogLevel
import com.v2ray.ang.shinigami.ShinigamiLogLine
import com.v2ray.ang.shinigami.ShinigamiPreset
import com.v2ray.ang.shinigami.ShinigamiServerScore
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.RotatingLoadingIcon
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding

private data class PresetOption(
    val preset: ShinigamiPreset,
    val emoji: String,
    val accent: Color,
    val titleRes: Int,
    val questionRes: Int
)

private val presetOptions = listOf(
    PresetOption(ShinigamiPreset.GAMING, "🎮", Color(0xFF8B6CFF), R.string.shinigami_preset_gaming_title, R.string.shinigami_preset_gaming_question),
    PresetOption(ShinigamiPreset.INSTAGRAM, "📱", Color(0xFFEC4899), R.string.shinigami_preset_instagram_title, R.string.shinigami_preset_instagram_question),
    PresetOption(ShinigamiPreset.DOWNLOAD, "⬇️", Color(0xFF38BDF8), R.string.shinigami_preset_download_title, R.string.shinigami_preset_download_question),
    PresetOption(ShinigamiPreset.VOICE_CALL, "🎙️", Color(0xFFF59E0B), R.string.shinigami_preset_voice_title, R.string.shinigami_preset_voice_question),
    PresetOption(ShinigamiPreset.STREAMING, "📺", Color(0xFF2DD4BF), R.string.shinigami_preset_streaming_title, R.string.shinigami_preset_streaming_question)
)

private val ScoreGood = Color(0xFF3FB950)
private val ScoreMedium = Color(0xFFD29922)
private val ScoreBad = Color(0xFFF85149)

private val TerminalBackground = Color(0xFF0B0F14)
private val TerminalTitleBar = Color(0xFF131A22)
private val TerminalBorder = Color(0xFF1F2A37)
private val TerminalMuted = Color(0xFF8B98A5)

@Composable
fun ShinigamiScreen(
    isLoading: Boolean,
    result: ShinigamiAnalysisResult?,
    terminal: List<ShinigamiLogLine>,
    progress: Float,
    chatText: String,
    onBackClick: () -> Unit,
    onPresetClick: (ShinigamiPreset) -> Unit,
    onChatTextChanged: (String) -> Unit,
    onChatSubmit: () -> Unit,
    onConnect: (String) -> Unit,
    onNewAnalysis: () -> Unit,
    onCancelAnalysis: () -> Unit
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
                isLoading -> AnalysisContent(terminal = terminal, progress = progress, onCancel = onCancelAnalysis)
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

// ---------------------------------------------------------------------------------------------
// Live analysis: progress + terminal
// ---------------------------------------------------------------------------------------------

@Composable
private fun AnalysisContent(terminal: List<ShinigamiLogLine>, progress: Float, onCancel: () -> Unit) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "shinigami_analysis_progress")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .padding(NavigationBarsBottomPadding())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RotatingLoadingIcon(iconSize = 28.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.shinigami_analyzing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.shinigami_percent_format, (animatedProgress * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        TerminalWindow(lines = terminal, modifier = Modifier.weight(1f).fillMaxWidth())
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Text(stringResource(R.string.shinigami_cancel_analysis))
        }
    }
}

@Composable
private fun TerminalWindow(lines: List<ShinigamiLogLine>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val listState = rememberLazyListState()
    // The extra trailing item is the blinking cursor, so scroll to lines.size to keep it visible.
    LaunchedEffect(lines.size) { listState.animateScrollToItem(lines.size) }

    // A terminal reads left-to-right whatever the app language is.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(TerminalBackground)
                .border(1.dp, TerminalBorder, shape)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(TerminalTitleBar).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalDot(Color(0xFFFF5F56))
                Spacer(modifier = Modifier.width(6.dp))
                TerminalDot(Color(0xFFFFBD2E))
                Spacer(modifier = Modifier.width(6.dp))
                TerminalDot(Color(0xFF27C93F))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.shinigami_terminal_title),
                    color = TerminalMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line.text,
                        color = terminalColor(line.level),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (line.level == ShinigamiLogLevel.CMD) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                item { BlinkingCursor() }
            }
        }
    }
}

@Composable
private fun TerminalDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "shinigami_cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shinigami_cursor_alpha"
    )
    Text(
        text = "█",
        color = Color(0xFF7EE787).copy(alpha = alpha),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
}

private fun terminalColor(level: ShinigamiLogLevel): Color = when (level) {
    ShinigamiLogLevel.CMD -> Color(0xFF7EE787)
    ShinigamiLogLevel.INFO -> Color(0xFFB6C2CF)
    ShinigamiLogLevel.OK -> ScoreGood
    ShinigamiLogLevel.WARN -> ScoreMedium
    ShinigamiLogLevel.ERROR -> ScoreBad
}

// ---------------------------------------------------------------------------------------------
// Home: preset cards + chat input
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeContent(
    chatText: String,
    onPresetClick: (ShinigamiPreset) -> Unit,
    onChatTextChanged: (String) -> Unit,
    onChatSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = NavigationBarsBottomPadding()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.shinigami_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(presetOptions, key = { it.preset.name }) { option ->
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
    val shape = RoundedCornerShape(22.dp)
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, option.accent.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.horizontalGradient(listOf(option.accent.copy(alpha = 0.24f), surface))
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(option.accent.copy(alpha = 0.20f))
                        .border(1.dp, option.accent.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = option.emoji, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(option.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(option.questionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_forward_24dp),
                    contentDescription = null,
                    tint = option.accent
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
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(stringResource(R.string.shinigami_chat_placeholder)) },
            singleLine = true
        )
        Spacer(modifier = Modifier.width(10.dp))
        FilledIconButton(
            onClick = onSubmit,
            enabled = text.isNotBlank(),
            modifier = Modifier.size(52.dp)
        ) {
            Icon(painterResource(R.drawable.ic_send_24dp), contentDescription = stringResource(R.string.shinigami_chat_send))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Result: best server + ranking
// ---------------------------------------------------------------------------------------------

@Composable
private fun ResultContent(
    result: ShinigamiAnalysisResult,
    onConnect: (String) -> Unit,
    onNewAnalysis: () -> Unit
) {
    val best = result.best
    val others = result.ranked.filter { it.server.guid != best?.server?.guid }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = NavigationBarsBottomPadding()
    ) {
        item {
            Text(
                text = "${ShinigamiExplainer.presetTitle(result.preset)} ${stringResource(R.string.shinigami_analysis_complete)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
        if (best != null) {
            item { BestServerCard(best, onConnect = onConnect) }
        }
        item { ExplanationCard(result.explanation) }
        if (others.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.shinigami_comparison_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
                )
            }
            items(others, key = { it.server.guid }) { row ->
                RankRow(rank = result.ranked.indexOfFirst { it.server.guid == row.server.guid } + 1, row = row)
            }
        }
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
private fun BestServerCard(best: ShinigamiServerScore, onConnect: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val score = best.score ?: 0
    val shape = RoundedCornerShape(26.dp)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(primary.copy(alpha = 0.30f), MaterialTheme.colorScheme.surfaceContainerHigh))
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.shinigami_best_match),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(primary.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shinigami_best_server_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = best.server.remarks,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = listOfNotNull(best.server.protocol, best.server.network).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ScoreRing(score = score)
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(
                        label = stringResource(R.string.shinigami_stat_ping),
                        value = stringResource(R.string.shinigami_unit_ms, best.metrics.pingMillis.toInt()),
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = stringResource(R.string.shinigami_stat_jitter),
                        value = stringResource(R.string.shinigami_unit_ms, best.metrics.jitterMillis.toInt()),
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = stringResource(R.string.shinigami_stat_loss),
                        value = stringResource(R.string.shinigami_unit_percent, best.metrics.lossPercent),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { onConnect(best.server.guid) },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(stringResource(R.string.shinigami_connect), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScoreRing(score: Int) {
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxSize(),
            color = scoreColor(score),
            strokeWidth = 7.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExplanationCard(explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankRow(rank: Int, row: ShinigamiServerScore) {
    val score = row.score
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shinigami_rank_position, rank),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.server.remarks,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (score ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (score != null) scoreColor(score) else MaterialTheme.colorScheme.outline,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = score?.toString() ?: stringResource(R.string.shinigami_score_na),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (score != null) scoreColor(score) else MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 75 -> ScoreGood
    score >= 50 -> ScoreMedium
    else -> ScoreBad
}
