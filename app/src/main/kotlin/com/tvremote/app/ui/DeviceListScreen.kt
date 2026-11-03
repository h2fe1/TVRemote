package com.tvremote.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tvremote.app.AppViewModel
import com.tvremote.app.BuildConfig
import com.tvremote.app.discovery.DiscoveredAtv
import com.tvremote.app.theme.Palette

@Composable
fun DeviceListScreen(viewModel: AppViewModel) {
    val ui by viewModel.devices.collectAsState()
    var showManual by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var forgetTarget by remember { mutableStateOf<DiscoveredAtv?>(null) }

    AmbientBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp),
        ) {
            item { Header() }
            item { Spacer(Modifier.height(34.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "附近设备",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    if (ui.scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.5.dp,
                            color = Palette.TextTertiary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (ui.scanning) "搜索中…" else "${ui.devices.size} 台设备",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextTertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (ui.devices.isEmpty()) {
                item {
                    EmptyDiscoveryCard(
                        scanning = ui.scanning,
                        onRescan = viewModel::startScan,
                        onManual = { showManual = true },
                    )
                }
            } else {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .glassPanel(RoundedCornerShape(24.dp), Color(0xFF1B1B1D), Color(0xFF151517)),
                    ) {
                        ui.devices.forEachIndexed { index, device ->
                            DeviceRow(
                                device = device,
                                paired = viewModel.isPaired(device),
                                onClick = { viewModel.selectDevice(device) },
                                onForget = { forgetTarget = device },
                            )
                            if (index != ui.devices.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 78.dp)
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.07f)),
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "轻点设备自动连接",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextTertiary,
                        )
                        TextButton(onClick = viewModel::startScan) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = Palette.TextSecondary,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("重新搜索", color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                ManualConnectRow { showManual = true }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.035f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Palette.TextTertiary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "配对密钥仅保存在本机，并由 Android Keystore 加密。",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextTertiary,
                    )
                }
            }
            if (BuildConfig.DEBUG && ui.devices.isEmpty()) {
                item {
                    Spacer(Modifier.height(18.dp))
                    TextButton(
                        onClick = viewModel::enterDemo,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "预览遥控器界面",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextTertiary,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(28.dp))
                Text(
                    "需要 Apple TV HD / 4K（tvOS 13 或更高版本）\n手机与电视需连接同一 Wi-Fi。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { showAbout = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "关于与开源许可",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextTertiary,
                    )
                }
            }
        }
    }

    if (showManual) {
        ManualConnectDialog(
            onDismiss = { showManual = false },
            onConnect = { host, port ->
                if (viewModel.addManualDevice(host, port)) showManual = false
            },
        )
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    forgetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            containerColor = Palette.Surface,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
            title = { Text("忘记这台 Apple TV？") },
            text = {
                Text("“${target.name}”的配对密钥会从此设备删除。下次使用时需要重新输入电视上的配对码。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetDevice(target)
                    forgetTarget = null
                }) { Text("忘记", color = Palette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { forgetTarget = null }) { Text("取消", color = Palette.TextSecondary) }
            },
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notice by androidx.compose.runtime.produceState("") {
        value = runCatching {
            context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("许可信息不可用")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        titleContentColor = Palette.TextPrimary,
        textContentColor = Palette.TextSecondary,
        title = { Text("TV Remote") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}\n\n",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.TextTertiary,
                )
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("好", color = Palette.TextPrimary) }
        },
    )
}

@Composable
private fun Header() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1E1E20))))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "TV REMOTE",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextTertiary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("遥控器", style = MaterialTheme.typography.displayLarge, color = Palette.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "把 Android 手机变成 Apple TV 的随身影院遥控器。",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredAtv,
    paired: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF202022))))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (paired) "已配对 · ${device.displayModel}" else "未配对 · ${device.displayModel}",
                style = MaterialTheme.typography.labelMedium,
                color = if (paired) Palette.Connected.copy(alpha = 0.85f) else Palette.TextTertiary,
                maxLines = 1,
            )
        }
        if (paired) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "更多选项",
                        tint = Palette.TextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Palette.SurfaceHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("忘记配对", color = Palette.Danger) },
                        onClick = { menuOpen = false; onForget() },
                    )
                }
            }
        } else {
            Text(
                "配对",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.TextTertiary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Chevrone()
    }
}

@Composable
private fun Chevrone() {
    Text("›", color = Palette.TextTertiary, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun EmptyDiscoveryCard(
    scanning: Boolean,
    onRescan: () -> Unit,
    onManual: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glow",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(28.dp), Color(0xFF1A1A1C), Color(0xFF141416))
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(92.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Palette.Accent.copy(alpha = 0.22f * glow), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF333336), Color(0xFF202022))))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = Palette.TextPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (scanning) "正在查找 Apple TV…" else "未发现 Apple TV",
            style = MaterialTheme.typography.titleLarge,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "确保电视已唤醒，并且和手机连接在同一个 Wi-Fi 网络。\n首次连接时，电视会显示 4 位配对码。",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = if (scanning) "搜索中" else "重新搜索",
                onClick = onRescan,
                filled = true,
            )
            PillButton(text = "手动连接", onClick = onManual, filled = false)
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, filled: Boolean) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (filled) 0f else 0.10f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) Color.Black else Palette.TextPrimary,
        )
    }
}

@Composable
private fun ManualConnectRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Link,
            contentDescription = null,
            tint = Palette.TextTertiary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "按 IP 地址手动连接",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text("›", color = Palette.TextTertiary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ManualConnectDialog(onDismiss: () -> Unit, onConnect: (String, Int) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("49153") }
    val valid = host.isNotBlank() && port.toIntOrNull()?.let { it in 1..65535 } == true
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Palette.TextPrimary,
        unfocusedTextColor = Palette.TextPrimary,
        focusedBorderColor = Color.White.copy(alpha = 0.30f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
        focusedLabelColor = Palette.TextSecondary,
        unfocusedLabelColor = Palette.TextTertiary,
        cursorColor = Palette.TextPrimary,
        focusedContainerColor = Color.White.copy(alpha = 0.04f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = Palette.Surface,
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "手动连接",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Palette.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Palette.TextSecondary)
                    }
                }
                Text(
                    "输入 Apple TV 的局域网 IP 和 Companion 端口。\n电视重启后端口可能会变化。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP 地址") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("端口") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消", color = Palette.TextSecondary)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (valid) Color.White else Color.White.copy(alpha = 0.10f))
                            .clickable(enabled = valid) {
                                onConnect(host.trim(), port.toInt())
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "连接",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (valid) Color.Black else Palette.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}
