package com.tvremote.app.ui

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.companionremote.protocol.client.NowPlaying
import dev.companionremote.protocol.client.PlaybackState
import dev.companionremote.protocol.client.SystemStatus
import dev.companionremote.protocol.client.TouchPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun RemoteScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val connection by viewModel.connectionState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val focusState by viewModel.keyboardFocus.collectAsState()
    val keyboardText by viewModel.keyboardText.collectAsState()
    val waking by viewModel.waking.collectAsState()
    val volumeKeysControlTv by viewModel.volumeKeysControlTv.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val tvPower by viewModel.tvPower.collectAsState()
    // Screensaver / sleep means nothing is being watched: never leave the
    // play-pause controls showing "pause" because of a stale transport state.
    val playing = playback == PlaybackState.Playing &&
        tvPower != SystemStatus.Asleep &&
        tvPower != SystemStatus.Screensaver
    val nowPlaying by viewModel.displayNowPlaying.collectAsState()
    val muted by viewModel.muted.collectAsState()
    val uiMessage by viewModel.message.collectAsState()

    var appsOpen by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var powerOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

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
    fun playPause() {
        buzz()
        viewModel.playPause()
    }
    fun togglePlayback() {
        buzz()
        viewModel.togglePlayback()
    }
    fun toggleMute() {
        buzz()
        viewModel.toggleMute()
    }
    fun rewind15() {
        buzz()
        viewModel.skipBy(-15.0)
    }
    fun forward15() {
        buzz()
        viewModel.skipBy(15.0)
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
                        onLongPress = { hold(HidCommand.Select) },
                        onTouch = viewModel::sendTouch,
                        playing = playing,
                        onPlayPause = ::playPause,
                        onTogglePlayback = ::togglePlayback,
                        nowPlaying = nowPlaying,
                        muted = muted,
                        onToggleMute = ::toggleMute,
                        onRewind = ::rewind15,
                        onForward = ::forward15,
                        onSeek = viewModel::seekToSeconds,
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
                        onLongPress = { hold(HidCommand.Select) },
                        onTouch = viewModel::sendTouch,
                        playing = playing,
                        onPlayPause = ::playPause,
                        onTogglePlayback = ::togglePlayback,
                        nowPlaying = nowPlaying,
                        muted = muted,
                        onToggleMute = ::toggleMute,
                        onRewind = ::rewind15,
                        onForward = ::forward15,
                        onSeek = viewModel::seekToSeconds,
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
                    volumeKeysControlTv = volumeKeysControlTv,
                    onVolumeKeysControlTvChange = viewModel::setVolumeKeysControlTv,
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
    onLongPress: () -> Unit,
    onTouch: (Long, Long, TouchPhase) -> Unit,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onTogglePlayback: () -> Unit,
    nowPlaying: NowPlaying?,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Double) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
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
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            nowPlaying?.let { info ->
                NowPlayingBar(
                    nowPlaying = info,
                    playing = playing,
                    muted = muted,
                    onPlayPause = onTogglePlayback,
                    onToggleMute = onToggleMute,
                    onRewind = onRewind,
                    onForward = onForward,
                    onSeek = onSeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .widthIn(max = 420.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .widthIn(max = 420.dp)
                    .weight(1f)
                    .glassPanel(
                        shape = RoundedCornerShape(44.dp),
                        fillTop = Color(0xFF232326),
                        fillBottom = Color(0xFF141416),
                        border = Color.White.copy(alpha = 0.10f),
                    ),
            ) {
                TouchSurface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    onSelect = onSelect,
                    onLongPress = onLongPress,
                    onTouch = onTouch,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .widthIn(max = 420.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportKey(
                    PlayPauseKeyIcon,
                    "播放 / 暂停",
                    96.dp,
                    36.dp,
                ) { onPlayPause() }
                TransportKey(Icons.AutoMirrored.Rounded.ArrowBack, "返回", 120.dp, 46.dp) {
                    press(HidCommand.Menu)
                }
                TransportKey(
                    Icons.Rounded.Tv,
                    "主屏幕（长按：控制中心）",
                    96.dp,
                    36.dp,
                    onLongClick = { hold(HidCommand.Home) },
                ) { press(HidCommand.Home) }
            }
            Spacer(Modifier.height(20.dp))
        }
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
    onLongPress: () -> Unit,
    onTouch: (Long, Long, TouchPhase) -> Unit,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onTogglePlayback: () -> Unit,
    nowPlaying: NowPlaying?,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Double) -> Unit,
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
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Phone landscape is too short for the strip plus the remote;
            // only show it when there is height to spare (tablets).
            val showNowPlaying = nowPlaying != null && maxHeight > 400.dp
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showNowPlaying) {
                    nowPlaying?.let { info ->
                        NowPlayingBar(
                            nowPlaying = info,
                            playing = playing,
                            muted = muted,
                            onPlayPause = onTogglePlayback,
                            onToggleMute = onToggleMute,
                            onRewind = onRewind,
                            onForward = onForward,
                            onSeek = onSeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 560.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                BoxWithConstraints(Modifier.fillMaxHeight()) {
                    val cardHeight = maxHeight.coerceAtMost(520.dp)
                    val touchWidth = (cardHeight - 24.dp) * 1.12f
                    val cardWidth = (touchWidth + 154.dp + 36.dp).coerceIn(420.dp, 760.dp)
                    RemoteCard(
                        landscape = true,
                        modifier = Modifier
                            .width(cardWidth),
                        fixedHeight = cardHeight,
                        press = press,
                        hold = hold,
                        onSelect = onSelect,
                        onLongPress = onLongPress,
                        onTouch = onTouch,
                        onPlayPause = onPlayPause,
                    )
                }
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

/**
 * Android 17-style now-playing card above the remote: full-bleed artwork,
 * elapsed/remaining time around the scrubber, and four equal action buttons
 * (rewind 15s, play/pause, forward 15s, mute).
 */
@Composable
private fun NowPlayingBar(
    nowPlaying: NowPlaying,
    playing: Boolean,
    muted: Boolean,
    onPlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val syncedAt = remember(nowPlaying, playing) { SystemClock.elapsedRealtime() }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(nowPlaying, playing) {
        while (playing) {
            delay(1_000)
            tick++
        }
    }
    val duration = nowPlaying.durationSeconds?.takeIf { it > 0.0 }
    val elapsed = remember(nowPlaying, playing, tick) {
        val base = nowPlaying.elapsedSeconds ?: 0.0
        val rate = nowPlaying.playbackRate?.takeIf { it > 0.0 } ?: 1.0
        val advance = if (playing) {
            (SystemClock.elapsedRealtime() - syncedAt).coerceAtLeast(0L) / 1000.0 * rate
        } else {
            0.0
        }
        val position = base + advance
        if (duration != null) position.coerceIn(0.0, duration) else position
    }
    val progress = if (duration != null) (elapsed / duration).toFloat().coerceIn(0f, 1f) else 0f
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shownFraction = dragFraction ?: progress
    val shownElapsed = dragFraction?.let { fraction ->
        if (duration != null) fraction * duration else elapsed
    } ?: elapsed
    val remaining = (duration ?: 0.0) - shownElapsed
    val artwork = remember(nowPlaying.artwork) {
        nowPlaying.artwork?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF17171B))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp)),
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A2F55), Color(0xFF191A28), Color(0xFF0D0D12)),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.10f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f)),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp, end = 12.dp),
        ) {
            Text(
                nowPlaying.title ?: "正在播放",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                nowPlaying.artist ?: nowPlaying.album ?: "正在播放",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (duration != null) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 68.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatPlaybackTime(shownElapsed),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .pointerInput(duration) {
                            detectTapGestures { offset ->
                                if (size.width > 0) {
                                    val fraction =
                                        (offset.x / size.width).coerceIn(0f, 1f)
                                    onSeek(fraction * duration)
                                }
                            }
                        }
                        .pointerInput(duration) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (size.width > 0) {
                                        dragFraction =
                                            (offset.x / size.width).coerceIn(0f, 1f)
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (size.width > 0) {
                                        dragFraction =
                                            (change.position.x / size.width).coerceIn(0f, 1f)
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    val fraction = dragFraction
                                    dragFraction = null
                                    if (fraction != null) {
                                        onSeek(fraction * duration)
                                    }
                                },
                                onDragCancel = { dragFraction = null },
                            )
                        },
                ) {
                    val centerY = size.height / 2f
                    val stroke = 2.dp.toPx()
                    drawLine(
                        Color.White.copy(alpha = 0.28f),
                        Offset(0f, centerY),
                        Offset(size.width, centerY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    if (shownFraction > 0f) {
                        drawLine(
                            Color.White,
                            Offset(0f, centerY),
                            Offset(size.width * shownFraction, centerY),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                        drawCircle(
                            Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(size.width * shownFraction, centerY),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "-${formatPlaybackTime(remaining.coerceAtLeast(0.0))}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .height(44.dp),
        ) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarCircleButton(
                    description = "后退 15 秒",
                    onClick = onRewind,
                    background = Color.White.copy(alpha = 0.08f),
                    borderColor = Color.White.copy(alpha = 0.18f),
                ) {
                    Skip15Icon(forward = false, tint = Color.White)
                }
                BarCircleButton(
                    description = if (playing) "暂停" else "播放",
                    onClick = onPlayPause,
                    background = Palette.Accent,
                    borderColor = Color.Transparent,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(23.dp),
                    )
                }
                BarCircleButton(
                    description = "前进 15 秒",
                    onClick = onForward,
                    background = Color.White.copy(alpha = 0.08f),
                    borderColor = Color.White.copy(alpha = 0.18f),
                ) {
                    Skip15Icon(forward = true, tint = Color.White)
                }
            }
            BarCircleButton(
                description = if (muted) "取消静音" else "静音",
                onClick = onToggleMute,
                background = if (muted) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.08f),
                borderColor = if (muted) Color.Transparent else Color.White.copy(alpha = 0.18f),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = if (muted) Color(0xFF17171B) else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BarCircleButton(
    description: String,
    onClick: () -> Unit,
    background: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun Skip15Icon(forward: Boolean, tint: Color) {
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Replay,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (forward) -1f else 1f },
        )
    }
}

private fun formatPlaybackTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = (total % 60).toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$secs"
    } else {
        "$minutes:$secs"
    }
}

@Composable
private fun RemoteCard(
    landscape: Boolean,
    modifier: Modifier,
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onTouch: (Long, Long, TouchPhase) -> Unit,
    onPlayPause: () -> Unit,
    fixedHeight: androidx.compose.ui.unit.Dp? = null,
) {
    Box(
        modifier
            .then(
                if (fixedHeight != null) {
                    Modifier.height(fixedHeight)
                } else {
                    Modifier
                },
            )
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
                    TransportKey(
                        PlayPauseKeyIcon,
                        "播放 / 暂停",
                        52.dp,
                        23.dp,
                    ) { onPlayPause() }
                    Spacer(Modifier.height(9.dp))
                    TransportKey(Icons.AutoMirrored.Rounded.ArrowBack, "返回", 64.dp, 28.dp) { press(HidCommand.Menu) }
                    Spacer(Modifier.height(9.dp))
                    TransportKey(Icons.Rounded.Tv, "主屏幕（长按：控制中心）", 52.dp, 23.dp, onLongClick = { hold(HidCommand.Home) }) {
                        press(HidCommand.Home)
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                val touchModifier = if (fixedHeight != null) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.02f)
                }
                TouchSurface(
                    modifier = touchModifier,
                    onSelect = onSelect,
                    onLongPress = onLongPress,
                    onTouch = onTouch,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportKey(
                        PlayPauseKeyIcon,
                        "播放 / 暂停",
                        64.dp,
                        28.dp,
                    ) { onPlayPause() }
                    TransportKey(Icons.AutoMirrored.Rounded.ArrowBack, "返回", 80.dp, 32.dp) { press(HidCommand.Menu) }
                    TransportKey(
                        Icons.Rounded.Tv,
                        "主屏幕（长按：控制中心）",
                        64.dp,
                        27.dp,
                        onLongClick = { hold(HidCommand.Home) },
                    ) { press(HidCommand.Home) }
                }
            }
        }
    }
}

/**
 * The play/pause symbol printed on the physical Siri Remote key: a play
 * triangle followed by two pause bars, drawn as a single static glyph. Using
 * one symbol (instead of swapping PlayArrow / Pause) matches the hardware
 * remote, whose key never changes appearance with the playback state.
 */
private val PlayPauseKeyIcon: ImageVector = ImageVector.Builder(
    name = "PlayPauseKey",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // Play triangle.
        moveTo(2f, 4.5f)
        lineTo(2f, 19.5f)
        lineTo(12f, 12f)
        close()
        // Two pause bars.
        moveTo(14.5f, 4.5f)
        lineTo(17.5f, 4.5f)
        lineTo(17.5f, 19.5f)
        lineTo(14.5f, 19.5f)
        close()
        moveTo(19f, 4.5f)
        lineTo(22f, 4.5f)
        lineTo(22f, 19.5f)
        lineTo(19f, 19.5f)
        close()
    }
}.build()

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

/**
 * The Siri-Remote-style surface.
 *
 * A tap is Select, holding opens the context menu, and dragging streams the
 * finger position to tvOS in the Companion touchpad's 0..1000 space. Position
 * mapping is done by [touchPadPoint]; it is absolute (edges stay edges) and
 * never lets the virtual finger stop while the physical finger is still
 * moving.
 */
@Composable
private fun TouchSurface(
    modifier: Modifier,
    onSelect: () -> Unit,
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
                        if (change.changedToUp()) {
                            ended = true
                            break
                        }
                        if ((change.position - start).getDistance() > moveSlop) {
                            dragged = true
                            // Anchor the press where the finger went down, but
                            // do not emit a Hold for this same event: the
                            // press and the first move would carry the same
                            // timestamp, which tvOS reads as a teleport with
                            // infinite velocity.
                            val press = touchPadPoint(start.x, start.y, width, height)
                            onTouch(press.x, press.y, TouchPhase.Press)
                            last = change.position
                            touchPoint = last
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
                            last = change.position
                            touchPoint = last
                            if (!change.pressed) break
                            val point = touchPadPoint(last.x, last.y, width, height)
                            onTouch(point.x, point.y, TouchPhase.Hold)
                            change.consume()
                        }
                        val end = touchPadPoint(last.x, last.y, width, height)
                        touchPoint = null
                        onTouch(end.x, end.y, TouchPhase.Release)
                        return@awaitEachGesture
                    }

                    touchPoint = null
                    if (!ended) return@awaitEachGesture

                    // The whole surface is a trackpad: any tap selects.
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                    onSelect()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
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

    }
}

@Composable
private fun PowerSheet(
    waking: Boolean,
    volumeKeysControlTv: Boolean,
    onVolumeKeysControlTvChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onWake: () -> Unit,
    onSleep: () -> Unit,
    onControlCenter: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Landscape phones are short: trim the decorative spacing so the
        // cancel button stays on screen instead of hiding below the fold.
        val compact = maxHeight < 620.dp
        val sectionGap = if (compact) 10.dp else 16.dp
        val actionGap = if (compact) 6.dp else 8.dp
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF232326), Color(0xFF151517))))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .clickable {}
                .then(
                    if (compact) {
                        Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    } else {
                        Modifier
                    },
                )
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 14.dp),
        ) {
            if (!compact) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Spacer(Modifier.height(18.dp))
            }
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
            Spacer(Modifier.height(sectionGap))
            PowerAction(
                icon = Icons.Rounded.WbSunny,
                title = "唤醒 Apple TV",
                subtitle = "从睡眠中唤醒电视",
                loading = waking,
                compact = compact,
                onClick = onWake,
            )
            Spacer(Modifier.height(actionGap))
            PowerAction(
                icon = Icons.Rounded.DarkMode,
                title = "进入睡眠",
                subtitle = "让 Apple TV 进入待机",
                compact = compact,
                onClick = onSleep,
            )
            Spacer(Modifier.height(actionGap))
            PowerAction(
                icon = Icons.Rounded.Tune,
                title = "打开控制中心",
                subtitle = "显示 tvOS 控制中心",
                compact = compact,
                onClick = onControlCenter,
            )
            Spacer(Modifier.height(actionGap))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.055f))
                    .padding(
                        start = 14.dp,
                        end = 10.dp,
                        top = if (compact) 5.dp else 8.dp,
                        bottom = if (compact) 5.dp else 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = Palette.TextPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "音量键控制电视",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        "遥控界面按手机音量键调节电视",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextTertiary,
                    )
                }
                Switch(
                    checked = volumeKeysControlTv,
                    onCheckedChange = onVolumeKeysControlTvChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Palette.Accent,
                        uncheckedThumbColor = Palette.TextSecondary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.18f),
                    ),
                )
            }
            Spacer(Modifier.height(actionGap))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = if (compact) 10.dp else 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        }
    }
}

@Composable
private fun PowerAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    loading: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 13.dp),
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
