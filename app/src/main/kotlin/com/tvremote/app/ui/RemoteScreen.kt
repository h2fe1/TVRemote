package com.tvremote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvremote.app.AppViewModel
import com.tvremote.app.ConnectionState
import com.tvremote.app.discovery.DiscoveredAtv
import com.tvremote.app.theme.Palette
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import dev.companionremote.protocol.client.TouchPhase
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun RemoteScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val connection by viewModel.connectionState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val focusState by viewModel.keyboardFocus.collectAsState()
    val keyboardText by viewModel.keyboardText.collectAsState()
    val waking by viewModel.waking.collectAsState()

    var appsOpen by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var powerOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun buzz() {
        runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    fun press(command: HidCommand) {
        buzz()
        viewModel.pressButton(command)
    }
    fun hold(command: HidCommand) {
        buzz()
        viewModel.holdButton(command)
    }
    fun closeKeyboard() {
        keyboardOpen = false
        keyboard?.hide()
    }

    BackHandler {
        when {
            powerOpen -> powerOpen = false
            appsOpen -> appsOpen = false
            keyboardOpen -> closeKeyboard()
            else -> viewModel.closeRemote()
        }
    }

    LaunchedEffect(focusState) {
        when (focusState) {
            KeyboardFocusState.Focused -> {
                appsOpen = false
                keyboardOpen = true
            }
            KeyboardFocusState.Unfocused -> closeKeyboard()
            KeyboardFocusState.Unknown -> Unit
        }
    }

    LaunchedEffect(appsOpen) {
        if (appsOpen) {
            closeKeyboard()
            viewModel.loadApps()
        }
    }

    AmbientBackground {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val landscape = maxWidth > maxHeight
                if (landscape) {
                    LandscapeRemote(
                        device = device,
                        connection = connection,
                        connectionError = connectionError,
                        focusState = focusState,
                        keyboardOpen = keyboardOpen,
                        onBack = viewModel::closeRemote,
                        onKeyboard = { if (keyboardOpen) closeKeyboard() else keyboardOpen = true },
                        onApps = { appsOpen = true },
                        onPower = { powerOpen = true },
                        onReconnect = viewModel::reconnect,
                        press = ::press,
                        hold = ::hold,
                        onSelect = { viewModel.touchTap() },
                        onDirection = ::press,
                        onLongPress = { hold(HidCommand.Select) },
                        onTouch = viewModel::sendTouch,
                        onVolume = { up -> press(if (up) HidCommand.VolumeUp else HidCommand.VolumeDown) },
                    )
                } else {
                    PortraitRemote(
                        device = device,
                        connection = connection,
                        connectionError = connectionError,
                        focusState = focusState,
                        keyboardOpen = keyboardOpen,
                        onBack = viewModel::closeRemote,
                        onKeyboard = { if (keyboardOpen) closeKeyboard() else keyboardOpen = true },
                        onApps = { appsOpen = true },
                        onPower = { powerOpen = true },
                        onReconnect = viewModel::reconnect,
                        press = ::press,
                        hold = ::hold,
                        onSelect = { viewModel.touchTap() },
                        onDirection = ::press,
                        onLongPress = { hold(HidCommand.Select) },
                        onTouch = viewModel::sendTouch,
                        onVolume = { up -> press(if (up) HidCommand.VolumeUp else HidCommand.VolumeDown) },
                    )
                }
            }

            AnimatedVisibility(
                visible = keyboardOpen && !appsOpen,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(130)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.34f))
                        .clickable(
                            interactionSource = androidx.compose.runtime.remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                            onClick = ::closeKeyboard,
                        ),
                )
            }

            KeyboardPanel(
                visible = keyboardOpen,
                focused = focusState == KeyboardFocusState.Focused,
                text = keyboardText,
                deviceName = device.name,
                onTextChange = viewModel::onKeyboardTextChanged,
                onClear = viewModel::clearKeyboardText,
                onClose = ::closeKeyboard,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            )

            AnimatedVisibility(
                visible = powerOpen,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(140)),
                modifier = Modifier.fillMaxSize(),
            ) {
                PowerSheet(
                    waking = waking,
                    onDismiss = { powerOpen = false },
                    onWake = { powerOpen = false; viewModel.wake() },
                    onSleep = { powerOpen = false; viewModel.sleep() },
                    onControlCenter = { powerOpen = false; viewModel.controlCenter() },
                )
            }

            AppDrawer(
                visible = appsOpen,
                viewModel = viewModel,
                onClose = { appsOpen = false },
            )
        }
    }

}

@Composable
private fun PortraitRemote(
    device: DiscoveredAtv,
    connection: ConnectionState,
    connectionError: String?,
    focusState: KeyboardFocusState,
    keyboardOpen: Boolean,
    onBack: () -> Unit,
    onKeyboard: () -> Unit,
    onApps: () -> Unit,
    onPower: () -> Unit,
    onReconnect: () -> Unit,
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    onSelect: () -> Unit,
    onDirection: (HidCommand) -> Unit,
    onLongPress: () -> Unit,
    onTouch: (Long, Long, TouchPhase) -> Unit,
    onVolume: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        RemoteHeader(
            device = device,
            connection = connection,
            focusState = focusState,
            keyboardOpen = keyboardOpen,
            onBack = onBack,
            onKeyboard = onKeyboard,
            onApps = onApps,
            onPower = onPower,
        )
        ConnectionBanner(connection, connectionError, onReconnect)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            RemoteCard(
                landscape = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                press = press,
                hold = hold,
                onSelect = onSelect,
                onDirection = onDirection,
                onLongPress = onLongPress,
                onTouch = onTouch,
                onVolume = onVolume,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "轻点选择 · 滑动导航 · 长按打开选项",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun LandscapeRemote(
    device: DiscoveredAtv,
    connection: ConnectionState,
    connectionError: String?,
    focusState: KeyboardFocusState,
    keyboardOpen: Boolean,
    onBack: () -> Unit,
    onKeyboard: () -> Unit,
    onApps: () -> Unit,
    onPower: () -> Unit,
    onReconnect: () -> Unit,
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    onSelect: () -> Unit,
    onDirection: (HidCommand) -> Unit,
    onLongPress: () -> Unit,
    onTouch: (Long, Long, TouchPhase) -> Unit,
    onVolume: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        RemoteHeader(
            device = device,
            connection = connection,
            focusState = focusState,
            keyboardOpen = keyboardOpen,
            onBack = onBack,
            onKeyboard = onKeyboard,
            onApps = onApps,
            onPower = onPower,
        )
        ConnectionBanner(connection, connectionError, onReconnect)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxHeight()) {
                val cardHeight = maxHeight.coerceAtMost(520.dp)
                val touchWidth = (cardHeight - 24.dp) * 1.12f
                val cardWidth = (touchWidth + 154.dp + 36.dp).coerceIn(420.dp, 760.dp)
                RemoteCard(
                    landscape = true,
                    modifier = Modifier
                        .height(cardHeight)
                        .width(cardWidth),
                    press = press,
                    hold = hold,
                    onSelect = onSelect,
                    onDirection = onDirection,
                    onLongPress = onLongPress,
                    onTouch = onTouch,
                    onVolume = onVolume,
                )
            }
        }
    }
}

@Composable
private fun RemoteHeader(
    device: DiscoveredAtv,
    connection: ConnectionState,
    focusState: KeyboardFocusState,
    keyboardOpen: Boolean,
    onBack: () -> Unit,
    onKeyboard: () -> Unit,
    onApps: () -> Unit,
    onPower: () -> Unit,
) {
    val statusColor = when (connection) {
        ConnectionState.Connected -> Palette.Connected
        ConnectionState.Connecting -> Palette.Warning
        ConnectionState.Disconnected -> Palette.Danger
    }
    val statusText = when (connection) {
        ConnectionState.Connected -> "已连接"
        ConnectionState.Connecting -> "正在连接"
        ConnectionState.Disconnected -> "未连接"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftIconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "返回设备列表",
            onClick = onBack,
            size = 38.dp,
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "$statusText · ${device.displayModel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SoftIconButton(
            icon = Icons.Rounded.Keyboard,
            contentDescription = "键盘",
            onClick = onKeyboard,
            size = 36.dp,
            iconSize = 17.dp,
            active = keyboardOpen || focusState == KeyboardFocusState.Focused,
        )
        Spacer(Modifier.width(6.dp))
        SoftIconButton(
            icon = Icons.Rounded.Apps,
            contentDescription = "App",
            onClick = onApps,
            size = 36.dp,
            iconSize = 17.dp,
        )
        Spacer(Modifier.width(6.dp))
        SoftIconButton(
            icon = Icons.Rounded.PowerSettingsNew,
            contentDescription = "电源",
            onClick = onPower,
            size = 36.dp,
            iconSize = 17.dp,
        )
    }
}

@Composable
private fun ConnectionBanner(
    connection: ConnectionState,
    error: String?,
    onReconnect: () -> Unit,
) {
    AnimatedVisibility(visible = connection != ConnectionState.Connected) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (connection == ConnectionState.Connecting) {
                        Palette.Warning.copy(alpha = 0.08f)
                    } else {
                        Palette.Danger.copy(alpha = 0.09f)
                    },
                )
                .padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (connection == ConnectionState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Palette.Warning,
                )
            } else {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Palette.Danger),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                error ?: if (connection == ConnectionState.Connecting) "正在连接 Apple TV…" else "连接已断开",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            if (connection == ConnectionState.Disconnected) {
                TextButton(onClick = onReconnect, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Text("重连", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun RemoteCard(
    landscape: Boolean,
    modifier: Modifier,
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    onSelect: () -> Unit,
    onDirection: (HidCommand) -> Unit,
    onLongPress: () -> Unit,
    onTouch: (Long, Long, TouchPhase) -> Unit,
    onVolume: (Boolean) -> Unit,
) {
    Box(
        modifier
            .glassPanel(
                shape = RoundedCornerShape(44.dp),
                fillTop = Color(0xFF232326),
                fillBottom = Color(0xFF141416),
                border = Color.White.copy(alpha = 0.10f),
            ),
    ) {
        // A faint physical sheen along the top edge of the remote body.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 40.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                    ),
                ),
        )
        if (landscape) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TouchSurface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1.12f),
                    onSelect = onSelect,
                    onDirection = onDirection,
                    onLongPress = onLongPress,
                    onTouch = onTouch,
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .width(154.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TransportKey(Icons.AutoMirrored.Rounded.ArrowBack, "返回", 52.dp, 22.dp) { press(HidCommand.Menu) }
                    Spacer(Modifier.height(9.dp))
                    TransportKey(Icons.Rounded.Tv, "主屏幕（长按：控制中心）", 52.dp, 22.dp, onLongClick = { hold(HidCommand.Home) }) {
                        press(HidCommand.Home)
                    }
                    Spacer(Modifier.height(9.dp))
                    TransportKey(Icons.Rounded.PlayArrow, "播放 / 暂停", 52.dp, 23.dp) { press(HidCommand.PlayPause) }
                    Spacer(Modifier.height(11.dp))
                    VolumeBar(onVolume = onVolume, compact = true)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                TouchSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.02f),
                    onSelect = onSelect,
                    onDirection = onDirection,
                    onLongPress = onLongPress,
                    onTouch = onTouch,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportKey(Icons.AutoMirrored.Rounded.ArrowBack, "返回", 64.dp, 26.dp) { press(HidCommand.Menu) }
                    TransportKey(
                        Icons.Rounded.Tv,
                        "主屏幕（长按：控制中心）",
                        64.dp,
                        27.dp,
                        onLongClick = { hold(HidCommand.Home) },
                    ) { press(HidCommand.Home) }
                    TransportKey(Icons.Rounded.PlayArrow, "播放 / 暂停", 64.dp, 28.dp) {
                        press(HidCommand.PlayPause)
                    }
                }
                Spacer(Modifier.height(14.dp))
                VolumeBar(onVolume = onVolume)
            }
        }
    }
}

@Composable
private fun TransportKey(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    RoundKey(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        onLongClick = onLongClick,
        size = size,
        iconSize = iconSize,
    )
}

@Composable
private fun VolumeBar(
    onVolume: (Boolean) -> Unit,
    compact: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val height = if (compact) 42.dp else 48.dp
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VolumeStepButton(
            icon = Icons.Rounded.Remove,
            description = "音量 −",
            modifier = Modifier.size(height),
        ) {
            runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            onVolume(false)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                "音量",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextTertiary,
            )
        }
        VolumeStepButton(
            icon = Icons.Rounded.Add,
            description = "音量 +",
            modifier = Modifier.size(height),
        ) {
            runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            onVolume(true)
        }
    }
}

@Composable
private fun VolumeStepButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Palette.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The Siri-Remote-style surface.
 *
 * A centre tap is Select, a tap on the rim is a directional click, holding
 * opens the context menu, and dragging streams accelerated touch coordinates
 * so tvOS gets the same kind of finger movement as a physical trackpad.
 */
@Composable
private fun TouchSurface(
    modifier: Modifier,
    onSelect: () -> Unit,
    onDirection: (HidCommand) -> Unit,
    onLongPress: () -> Unit,
    onTouch: (Long, Long, TouchPhase) -> Unit,
) {
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    0f to Palette.TouchTop,
                    1f to Palette.TouchBottom,
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(32.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    touchPoint = down.position
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val start = down.position
                    var last = start
                    var lastTime = down.uptimeMillis
                    var vx = (start.x / width * 1000f).coerceIn(0f, 1000f)
                    var vy = (start.y / height * 1000f).coerceIn(0f, 1000f)
                    var up = start
                    var dragged = false
                    var longPressed = false
                    var ended = false
                    val moveSlop = 13.dp.toPx()

                    // Stage 1: disambiguate tap, swipe and long-press.
                    while (true) {
                        val event = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            longPressed = true
                            runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                            onLongPress()
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        up = change.position
                        if (change.changedToUp()) {
                            ended = true
                            break
                        }
                        if ((change.position - start).getDistance() > moveSlop) {
                            dragged = true
                            onTouch(vx.toLong(), vy.toLong(), TouchPhase.Press)
                            val delta = change.position - last
                            val dt = ((change.uptimeMillis - lastTime) / 1000f).coerceAtLeast(0.001f)
                            val gain = touchGain(delta.getDistance(), dt, 300.dp.toPx())
                            vx = (vx + delta.x / (300.dp.toPx()) * 1000f * gain).coerceIn(0f, 1000f)
                            vy = (vy + delta.y / (300.dp.toPx()) * 1000f * gain).coerceIn(0f, 1000f)
                            last = change.position
                            lastTime = change.uptimeMillis
                            onTouch(vx.toLong(), vy.toLong(), TouchPhase.Hold)
                            change.consume()
                            break
                        }
                        change.consume()
                    }

                    if (longPressed) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                        }
                        touchPoint = null
                        return@awaitEachGesture
                    }

                    if (dragged) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val now = change.uptimeMillis
                            val dt = ((now - lastTime) / 1000f).coerceAtLeast(0.001f)
                            val delta = change.position - last
                            val gain = touchGain(delta.getDistance(), dt, 300.dp.toPx())
                            vx = (vx + delta.x / (300.dp.toPx()) * 1000f * gain).coerceIn(0f, 1000f)
                            vy = (vy + delta.y / (300.dp.toPx()) * 1000f * gain).coerceIn(0f, 1000f)
                            last = change.position
                            lastTime = now
                            touchPoint = change.position
                            onTouch(vx.toLong(), vy.toLong(), TouchPhase.Hold)
                            if (!change.pressed) break
                            change.consume()
                        }
                        onTouch(vx.toLong(), vy.toLong(), TouchPhase.Release)
                        touchPoint = null
                        return@awaitEachGesture
                    }

                    touchPoint = null
                    if (!ended) return@awaitEachGesture

                    // A rim tap is a directional click; the centre selects.
                    val centre = Offset(width / 2f, height / 2f)
                    val mid = Offset((start.x + up.x) / 2f, (start.y + up.y) / 2f)
                    val radius = min(width, height) * 0.36f
                    if ((mid - centre).getDistance() < radius) {
                        runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                        onSelect()
                    } else {
                        val dx = mid.x - centre.x
                        val dy = mid.y - centre.y
                        val direction = if (abs(dx) > abs(dy)) {
                            if (dx > 0) HidCommand.Right else HidCommand.Left
                        } else {
                            if (dy > 0) HidCommand.Down else HidCommand.Up
                        }
                        runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                        onDirection(direction)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) * 0.30f
            drawCircle(
                color = Color.White.copy(alpha = 0.022f),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.045f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.018f),
                radius = radius * 0.55f,
                center = center,
                style = Stroke(width = 0.7.dp.toPx()),
            )
            touchPoint?.let { point ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        center = point,
                        radius = 92.dp.toPx(),
                    ),
                    radius = 92.dp.toPx(),
                    center = point,
                )
            }
        }

        Icon(
            Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.17f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(24.dp),
        )
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.17f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .size(24.dp),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.17f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(24.dp),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.17f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .size(24.dp),
        )
        Text(
            "轻点 · 滑动",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.20f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
        )
    }
}

private fun touchGain(distancePx: Float, dtSeconds: Float, referencePx: Float): Float {
    val speed = (distancePx / referencePx) / dtSeconds.coerceAtLeast(0.001f)
    return (0.85f + speed * 0.65f).coerceIn(0.85f, 3.2f)
}

@Composable
private fun PowerSheet(
    waking: Boolean,
    onDismiss: () -> Unit,
    onWake: () -> Unit,
    onSleep: () -> Unit,
    onControlCenter: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF232326), Color(0xFF151517))))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .clickable {}
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "电源与控制",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                SoftIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    onClick = onDismiss,
                    size = 34.dp,
                )
            }
            Spacer(Modifier.height(16.dp))
            PowerAction(
                icon = Icons.Rounded.WbSunny,
                title = "唤醒 Apple TV",
                subtitle = "从睡眠中唤醒电视",
                loading = waking,
                onClick = onWake,
            )
            Spacer(Modifier.height(8.dp))
            PowerAction(
                icon = Icons.Rounded.DarkMode,
                title = "进入睡眠",
                subtitle = "让 Apple TV 进入待机",
                onClick = onSleep,
            )
            Spacer(Modifier.height(8.dp))
            PowerAction(
                icon = Icons.Rounded.Tune,
                title = "打开控制中心",
                subtitle = "显示 tvOS 控制中心",
                onClick = onControlCenter,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PowerAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.5.dp,
                    color = Palette.TextSecondary,
                )
            } else {
                Icon(icon, contentDescription = null, tint = Palette.TextPrimary, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary)
        }
        Text("›", color = Palette.TextTertiary, style = MaterialTheme.typography.titleLarge)
    }
}
