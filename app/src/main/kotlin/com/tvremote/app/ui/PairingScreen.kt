package com.tvremote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvremote.app.AppViewModel
import com.tvremote.app.discovery.DiscoveredAtv
import com.tvremote.app.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun PairingScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val state by viewModel.pairing.collectAsState()
    var pin by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current

    BackHandler { viewModel.cancelPairing() }

    LaunchedEffect(state.error) {
        if (state.error != null) pin = ""
    }
    LaunchedEffect(pin) {
        if (pin.length == 4 && !state.submitting) {
            delay(140)
            viewModel.submitPin(pin)
        }
    }

    AmbientBackground(tint = Palette.Accent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoftIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = viewModel::cancelPairing,
                    size = 38.dp,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::cancelPairing) {
                    Text("取消", color = Palette.TextSecondary)
                }
            }

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(112.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Palette.Accent.copy(alpha = 0.20f), Color.Transparent),
                                ),
                                CircleShape,
                            ),
                    )
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF343437), Color(0xFF1D1D1F))))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "配对 Apple TV",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))

                when {
                    state.waitingForTv -> WaitingForTv()
                    state.awaitingPin || state.submitting -> PinEntry(
                        pin = pin,
                        submitting = state.submitting,
                        error = state.error,
                        onDigit = { digit ->
                            if (!state.submitting && pin.length < 4) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                pin += digit
                            }
                        },
                        onBackspace = {
                            if (!state.submitting && pin.isNotEmpty()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                pin = pin.dropLast(1)
                            }
                        },
                        onRestart = viewModel::restartPairing,
                    )
                    else -> PairingFailure(
                        message = state.error ?: "无法开始配对",
                        onRetry = viewModel::restartPairing,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WaitingForTv() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "正在请求配对…",
            style = MaterialTheme.typography.titleLarge,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "请查看电视屏幕，配对码稍后会显示在那里。",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(44.dp))
    }
}

@Composable
private fun PinEntry(
    pin: String,
    submitting: Boolean,
    error: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onRestart: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (submitting) "正在验证…" else "输入电视上显示的 4 位配对码",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { index ->
                val filled = index < pin.length
                val active = index == pin.length && !submitting
                val scale by animateFloatAsState(
                    targetValue = if (active) 1f else 0.96f,
                    animationSpec = tween(160),
                    label = "pinBox",
                )
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (filled) Color.White.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = 0.045f),
                        )
                        .border(
                            1.dp,
                            when {
                                submitting && filled -> Palette.Connected.copy(alpha = 0.6f)
                                active -> Color.White.copy(alpha = 0.45f)
                                else -> Color.White.copy(alpha = 0.08f)
                            },
                            RoundedCornerShape(18.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filled) pin[index].toString() else "·",
                        color = if (filled) Palette.TextPrimary else Palette.TextTertiary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        AnimatedVisibility(visible = error != null) {
            Text(
                error ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (submitting) {
            Spacer(Modifier.height(14.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.height(20.dp))
        NumericKeypad(
            enabled = !submitting,
            onDigit = onDigit,
            onBackspace = onBackspace,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRestart, enabled = !submitting) {
            Text("没有看到配对码？重新获取", color = Palette.TextTertiary)
        }
    }
}

@Composable
private fun NumericKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit ->
                    KeypadKey(digit, enabled) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.size(width = 78.dp, height = 58.dp))
            KeypadKey("0", enabled) { onDigit("0") }
            Box(
                Modifier
                    .size(width = 78.dp, height = 58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.045f))
                    .clickable(enabled = enabled, onClick = onBackspace),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "删除",
                    tint = if (enabled) Palette.TextSecondary else Palette.TextTertiary,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 78.dp, height = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2C2C2F), Color(0xFF202022))))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Palette.TextPrimary else Palette.TextTertiary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PairingFailure(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "配对没有完成",
            style = MaterialTheme.typography.titleLarge,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Danger,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onRetry)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) {
            Text("重新开始", color = Color.Black, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(30.dp))
    }
}
