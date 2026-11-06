package com.tvremote.app

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.app.data.CredentialsRepository
import com.tvremote.app.data.SettingsRepository
import com.tvremote.app.discovery.AtvDiscovery
import com.tvremote.app.discovery.DiscoveredAtv
import com.tvremote.app.lockscreen.LockScreenCommand
import com.tvremote.app.lockscreen.LockScreenMedia
import com.tvremote.app.lockscreen.LockScreenState
import com.tvremote.app.lockscreen.NowPlayingService
import dev.companionremote.protocol.client.CompanionClient
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import dev.companionremote.protocol.client.NowPlaying
import dev.companionremote.protocol.client.PlaybackState
import dev.companionremote.protocol.client.SystemStatus
import dev.companionremote.protocol.client.TouchPhase
import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.protocol.transport.SocketTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface Screen {
    data object Devices : Screen
    data class Pairing(val device: DiscoveredAtv) : Screen
    data class Remote(val device: DiscoveredAtv) : Screen
}

enum class ConnectionState { Connecting, Connected, Disconnected }

data class PairingUi(
    val waitingForTv: Boolean = false,
    val awaitingPin: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
)

data class DeviceListUi(
    val devices: List<DiscoveredAtv> = emptyList(),
    val pairedKeys: Set<String> = emptySet(),
    val scanning: Boolean = false,
    val error: String? = null,
)

/**
 * Owns discovery, pairing, the live Companion connection and all UI state.
 * The protocol layer lives in :protocol and has no Android dependencies.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val discovery = AtvDiscovery(application)
    private val credentials = CredentialsRepository(application)
    private val settings = SettingsRepository(application)

    val screen = MutableStateFlow<Screen>(Screen.Devices)
    val devices = MutableStateFlow(DeviceListUi())
    val pairing = MutableStateFlow(PairingUi())
    val connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionError = MutableStateFlow<String?>(null)
    val keyboardFocus = MutableStateFlow(KeyboardFocusState.Unknown)
    val keyboardText = MutableStateFlow("")
    val apps = MutableStateFlow<List<AppEntry>?>(null)
    val appsLoading = MutableStateFlow(false)
    val appsError = MutableStateFlow<String?>(null)
    val activeDevice = MutableStateFlow<DiscoveredAtv?>(null)
    val demoMode = MutableStateFlow(false)
    val waking = MutableStateFlow(false)

    /** When true, the phone's volume keys adjust the TV instead of the phone. */
    val volumeKeysControlTv = MutableStateFlow(true)

    /** Playback state pushed by the TV; drives the play/pause button icon. */
    val playbackState = MutableStateFlow(PlaybackState.Unknown)

    /** Latest now-playing metadata, or null when nothing is reported. */
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /**
     * What the in-app bar should render. Falls back to a generic card when the
     * TV is playing but the current app reports no metadata (common for some
     * video/audio apps).
     */
    val displayNowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** Set after the mute button silences the TV; cleared when volume is restored. */
    val muted = MutableStateFlow(false)

    /** TV power state; the lock screen card is hidden while asleep/screensaver. */
    val tvPower = MutableStateFlow(SystemStatus.Unknown)

    /** The TV advertises absolute volume control (CEC). */
    val volumeControlAvailable = MutableStateFlow(false)

    /** One-shot UI message (toast); cleared by the UI after showing. */
    val message = MutableStateFlow<String?>(null)

    private var client: CompanionClient? = null
    private var connectionJob: Job? = null
    private var scanJob: Job? = null
    private var pairingJob: Job? = null
    private var pairingConnection: CompanionConnection? = null
    private var pairingSetup: PairSetup? = null
    private var keyboardJob: Job? = null
    private var textJob: Job? = null
    private var touchJob: Job? = null
    private var playbackJob: Job? = null
    private var nowPlayingJob: Job? = null
    private var powerJob: Job? = null
    private var volumeJob: Job? = null
    private var connectionToken = 0
    private var lastHoldAt = 0L
    private var lastVolumeKeyAt = 0L
    private var nowPlayingAnchorMs = SystemClock.elapsedRealtime()
    private var lockScreenServiceStarted = false
    private var demoDevicesMode = false
    private val touchQueue = Channel<TouchSample>(capacity = 256)

    init {
        viewModelScope.launch {
            settings.volumeKeysControlTv.collect { volumeKeysControlTv.value = it }
        }
        viewModelScope.launch { refreshPairedKeys() }
        startScan()
        startLockScreenSync()
    }

    /**
     * Publishes the TV's now-playing state to the system only while the TV has
     * an active media item and is connected. Home screen / sleep / stopped
     * playback clears the session, so the lock screen card disappears again.
     */
    private fun startLockScreenSync() {
        LockScreenMedia.commandHandler = { command ->
            when (command) {
                LockScreenCommand.Play -> {
                    if (playbackState.value != PlaybackState.Playing) togglePlayback()
                }
                LockScreenCommand.Pause -> {
                    if (playbackState.value == PlaybackState.Playing) togglePlayback()
                }
                LockScreenCommand.Rewind15 -> skipBy(-15.0)
                LockScreenCommand.Forward15 -> skipBy(15.0)
                LockScreenCommand.ToggleMute -> toggleMute()
                is LockScreenCommand.SeekTo -> seekTo(command.positionMs)
            }
        }
        viewModelScope.launch {
            connectionState.collect { connection ->
                if (connection == ConnectionState.Connected) {
                    LockScreenMedia.deviceName.value =
                        activeDevice.value?.name ?: "Apple TV"
                    startLockScreenService()
                } else {
                    LockScreenMedia.state.value = null
                    stopLockScreenService()
                }
            }
        }
        viewModelScope.launch {
            combine(
                nowPlaying,
                playbackState,
                connectionState,
                muted,
                tvPower,
            ) { playing, playback, connection, isMuted, power ->
                val active = connection == ConnectionState.Connected &&
                    power != SystemStatus.Asleep &&
                    power != SystemStatus.Screensaver &&
                    (playback == PlaybackState.Playing ||
                        playback == PlaybackState.Paused)
                if (!active) return@combine null
                val current = playing?.takeIf { !it.title.isNullOrBlank() }
                val deviceName = activeDevice.value?.name ?: "Apple TV"
                val display = current ?: NowPlaying(
                    title = "正在播放",
                    artist = deviceName,
                )
                displayNowPlaying.value = display
                LockScreenState(
                    title = display.title ?: deviceName,
                    artist = display.artist ?: display.album ?: "正在播放",
                    durationMs = ((display.durationSeconds ?: 0.0) * 1000.0).toLong(),
                    positionMs = ((display.elapsedSeconds ?: 0.0) * 1000.0).toLong(),
                    isPlaying = playback == PlaybackState.Playing &&
                        power != SystemStatus.Asleep &&
                        power != SystemStatus.Screensaver,
                    muted = isMuted,
                    artwork = display.artwork,
                )
            }.collect { state ->
                LockScreenMedia.state.value = state
                if (state == null) displayNowPlaying.value = null
            }
        }
    }

    private fun startLockScreenService() {
        if (lockScreenServiceStarted) return
        lockScreenServiceStarted = true
        runCatching {
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), NowPlayingService::class.java),
            )
        }.onFailure {
            lockScreenServiceStarted = false
        }
    }

    private fun stopLockScreenService() {
        if (!lockScreenServiceStarted) return
        lockScreenServiceStarted = false
        getApplication<Application>()
            .stopService(Intent(getApplication(), NowPlayingService::class.java))
    }

    private fun seekTo(positionMs: Long) = seekToSeconds(positionMs / 1000.0)

    /** Current transport position, including local interpolation since the last TV update. */
    private fun currentPositionSeconds(): Double {
        val media = nowPlaying.value ?: return 0.0
        val base = media.elapsedSeconds ?: 0.0
        val rate = if (playbackState.value == PlaybackState.Playing) {
            media.playbackRate?.takeIf { it > 0.0 } ?: 1.0
        } else {
            0.0
        }
        val advance = if (rate > 0.0) {
            (SystemClock.elapsedRealtime() - nowPlayingAnchorMs).coerceAtLeast(0L) /
                1000.0 * rate
        } else {
            0.0
        }
        val position = base + advance
        val duration = media.durationSeconds
        return if (duration != null && duration > 0.0) {
            position.coerceIn(0.0, duration)
        } else {
            position
        }
    }

    /** Seek from the in-app scrubber; target is in seconds. */
    fun seekToSeconds(targetSeconds: Double) {
        val media = nowPlaying.value ?: return
        val duration = media.durationSeconds
        val target = if (duration != null && duration > 0.0) {
            targetSeconds.coerceIn(0.0, duration)
        } else {
            targetSeconds.coerceAtLeast(0.0)
        }
        val delta = target - currentPositionSeconds()
        if (demoMode.value) {
            nowPlaying.value = media.copy(elapsedSeconds = target)
            nowPlayingAnchorMs = SystemClock.elapsedRealtime()
            return
        }
        if (kotlin.math.abs(delta) < 0.5) return
        // Keep the optimistic target on screen while tvOS processes the skip.
        client?.suppressNowPlayingFor(1_200)
        skipBy(delta)
        nowPlaying.value = media.copy(elapsedSeconds = target)
        nowPlayingAnchorMs = SystemClock.elapsedRealtime()
    }

    // ---------------------------------------------------------------- Discovery

    fun startScan() {
        if (demoDevicesMode) return
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            devices.update { it.copy(scanning = true, error = null) }
            refreshPairedKeys()
            try {
                discovery.scan(durationMs = 6_000) { found ->
                    val current = devices.value
                    if (current.devices.none { it.deviceId == found.deviceId }) {
                        devices.value = current.copy(
                            devices = (current.devices + found)
                                .sortedWith(compareBy({ !isPaired(it) }, { it.name.lowercase() })),
                        )
                    }
                }
            } catch (e: Exception) {
                devices.value = devices.value.copy(error = "搜索附近设备失败，请检查 Wi-Fi 和组播设置。")
            } finally {
                devices.value = devices.value.copy(scanning = false)
            }
        }
    }

    fun addManualDevice(host: String, port: Int): Boolean {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (cleanHost.isBlank() || port !in 1..65535) return false
        val manual = DiscoveredAtv(
            deviceId = "manual:$cleanHost",
            name = cleanHost,
            host = cleanHost,
            port = port,
            model = null,
        )
        devices.value = devices.value.copy(
            devices = (devices.value.devices + manual).distinctBy { it.deviceId },
        )
        selectDevice(manual)
        return true
    }

    fun isPaired(device: DiscoveredAtv): Boolean =
        devices.value.pairedKeys.contains(credentials.deviceKey(device.deviceId))

    private suspend fun refreshPairedKeys() {
        if (demoDevicesMode) return
        val keys = credentials.pairedKeys()
        if (demoDevicesMode) return
        // Read the current list *after* the suspend point. Otherwise a slow
        // DataStore read can resurrect an empty snapshot and wipe devices
        // discovered while it was in flight.
        devices.update { it.copy(pairedKeys = keys) }
    }

    fun selectDevice(device: DiscoveredAtv) {
        if (demoMode.value) return
        viewModelScope.launch {
            val stored = credentials.load(device.deviceId)
            if (stored != null) {
                openRemote(device, HapCredentials.parse(stored))
            } else {
                beginPairing(device)
            }
        }
    }

    fun forgetDevice(device: DiscoveredAtv) {
        viewModelScope.launch {
            credentials.delete(device.deviceId)
            refreshPairedKeys()
            if (activeDevice.value?.deviceId == device.deviceId) closeRemote()
        }
    }

    // ----------------------------------------------------------------- Pairing

    private suspend fun beginPairing(device: DiscoveredAtv) {
        closePairingTransport()
        screen.value = Screen.Pairing(device)
        pairing.value = PairingUi(waitingForTv = true)
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            try {
                withTimeout(15_000) {
                    val transport = SocketTransport.connect(device.host, device.port, timeoutMs = 8_000)
                    val connection = CompanionConnection(transport)
                    pairingConnection = connection
                    connection.start()
                    val setup = PairSetup(connection, name = "TV Remote")
                    setup.startPairing()
                    pairingSetup = setup
                    pairing.value = PairingUi(awaitingPin = true)
                }
            } catch (e: Exception) {
                closePairingTransport()
                pairing.value = PairingUi(error = friendlyError(e))
            }
        }
    }

    fun submitPin(pin: String) {
        if (pin.length != 4 || pin.any { !it.isDigit() }) return
        val setup = pairingSetup ?: return
        if (pairing.value.submitting) return
        val device = (screen.value as? Screen.Pairing)?.device ?: return
        pairing.value = pairing.value.copy(submitting = true, error = null)
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            try {
                val creds = withTimeout(15_000) { setup.finishPairing(pin) }
                credentials.save(device.deviceId, creds.toString())
                refreshPairedKeys()
                closePairingTransport()
                openRemote(device, creds)
            } catch (e: Exception) {
                closePairingTransport()
                pairing.value = PairingUi(error = friendlyError(e))
            }
        }
    }

    fun restartPairing() {
        val device = (screen.value as? Screen.Pairing)?.device ?: return
        pairingJob?.cancel()
        viewModelScope.launch { beginPairing(device) }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        closePairingTransport()
        pairing.value = PairingUi()
        screen.value = Screen.Devices
    }

    private fun closePairingTransport() {
        pairingSetup = null
        pairingConnection?.close()
        pairingConnection = null
    }

    // -------------------------------------------------------------- Connection

    private fun openRemote(device: DiscoveredAtv, creds: HapCredentials) {
        demoMode.value = false
        activeDevice.value = device
        screen.value = Screen.Remote(device)
        apps.value = null
        appsError.value = null
        keyboardFocus.value = KeyboardFocusState.Unknown
        keyboardText.value = ""
        playbackState.value = PlaybackState.Unknown
        nowPlaying.value = null
        tvPower.value = SystemStatus.Unknown
        muted.value = false
        connect(device, creds)
    }

    private fun connect(device: DiscoveredAtv, creds: HapCredentials) {
        connectionJob?.cancel()
        client?.close()
        client = null
        val token = ++connectionToken
        connectionJob = viewModelScope.launch {
            connectionState.value = ConnectionState.Connecting
            connectionError.value = null
            var lastError: Exception? = null

            suspend fun attempt(host: String, port: Int): Boolean {
                val transport = SocketTransport.connect(host, port, timeoutMs = 7_000)
                val newClient = CompanionClient(
                    CompanionConnection(transport),
                    creds,
                    name = "TV Remote",
                )
                try {
                    newClient.connect()
                    if (token != connectionToken) {
                        newClient.close()
                        return false
                    }
                    client?.close()
                    client = newClient
                    connectionState.value = ConnectionState.Connected
                    connectionError.value = null
                    observeKeyboard(newClient)
                    observePlayback(newClient)
                    observeNowPlaying(newClient)
                    observePower(newClient)
                    observeVolumeControl(newClient)
                    syncMuteState(newClient)
                    consumeTouches(newClient)
                    return true
                } catch (e: Exception) {
                    newClient.close()
                    throw e
                }
            }

            try {
                try {
                    attempt(device.host, device.port)
                } catch (e: Exception) {
                    lastError = e
                    // tvOS rotates its Companion port. Rediscover before giving up.
                    val fresh = discovery.resolve(device, timeoutMs = 5_000)
                    if (fresh != null && (fresh.host != device.host || fresh.port != device.port)) {
                        activeDevice.value = fresh
                        attempt(fresh.host, fresh.port)
                    } else {
                        throw e
                    }
                }
            } catch (e: Exception) {
                if (token == connectionToken) {
                    connectionState.value = ConnectionState.Disconnected
                    connectionError.value = friendlyError(lastError ?: e)
                    playbackState.value = PlaybackState.Unknown
                    nowPlaying.value = null
                    tvPower.value = SystemStatus.Unknown
                    volumeControlAvailable.value = false
                    muted.value = false
                    client = null
                }
            }
        }
    }

    fun reconnect() {
        val device = activeDevice.value ?: return
        if (demoMode.value) return
        viewModelScope.launch {
            val stored = credentials.load(device.deviceId) ?: return@launch
            connect(device, HapCredentials.parse(stored))
        }
    }

    fun onForeground() {
        if (screen.value is Screen.Remote &&
            !demoMode.value &&
            connectionState.value == ConnectionState.Disconnected &&
            activeDevice.value != null
        ) {
            reconnect()
        }
    }

    fun closeRemote() {
        connectionJob?.cancel()
        ++connectionToken
        client?.close()
        client = null
        demoMode.value = false
        activeDevice.value = null
        connectionState.value = ConnectionState.Disconnected
        connectionError.value = null
        keyboardFocus.value = KeyboardFocusState.Unknown
        keyboardText.value = ""
        playbackState.value = PlaybackState.Unknown
        nowPlaying.value = null
        muted.value = false
        displayNowPlaying.value = null
        apps.value = null
        appsError.value = null
        screen.value = Screen.Devices
    }

    private fun withClient(block: suspend (CompanionClient) -> Unit) {
        if (demoMode.value) return
        val current = client ?: return
        viewModelScope.launch {
            try {
                block(current)
            } catch (e: Exception) {
                connectionState.value = ConnectionState.Disconnected
                connectionError.value = friendlyError(e)
            }
        }
    }

    // ---------------------------------------------------------------- Commands

    fun pressButton(command: HidCommand) = withClient { it.pressButton(command) }

    fun holdButton(command: HidCommand) = withClient { it.holdButton(command, holdMs = 700) }

    /**
     * The physical Siri Remote play/pause key: a plain HID button press
     * (down + up), exactly what the hardware remote sends. Some tvOS apps
     * only react to this key — for example by refreshing a list — instead of
     * treating it as a media transport command, so this is deliberately not
     * `_mcc` Play/Pause.
     *
     * tvOS does not push a fresh `_iMC` for every app after this key, so the
     * card icon is flipped optimistically (and reverted if the press fails);
     * a real `_iMC` update still wins if one arrives.
     */
    fun playPause() {
        if (demoMode.value) {
            playbackState.value = if (playbackState.value == PlaybackState.Playing) {
                PlaybackState.Paused
            } else {
                PlaybackState.Playing
            }
            return
        }
        if (connectionState.value != ConnectionState.Connected) return
        val previous = playbackState.value
        // Only flip an existing transport state: in an app that merely uses
        // the key to refresh a list (no media), inventing "Playing" would pop
        // a fake now-playing card.
        val target = when (previous) {
            PlaybackState.Playing -> PlaybackState.Paused
            PlaybackState.Paused -> PlaybackState.Playing
            else -> null
        }
        if (target != null) playbackState.value = target
        withClient { active ->
            try {
                active.pressButton(HidCommand.PlayPause)
            } catch (e: Exception) {
                if (target != null) playbackState.value = previous
                throw e
            }
        }
    }

    /**
     * Explicit transport play/pause, used by the in-app now-playing card and
     * the lock-screen card where a real "play" / "pause" command is wanted
     * (and the icon flips immediately). More reliable than the HID toggle in
     * apps that ignore the physical key for transport.
     */
    fun togglePlayback() {
        if (demoMode.value) {
            playbackState.value = if (playbackState.value == PlaybackState.Playing) {
                PlaybackState.Paused
            } else {
                PlaybackState.Playing
            }
            return
        }
        if (connectionState.value != ConnectionState.Connected) return
        val active = client ?: return
        val previous = playbackState.value
        val target = if (previous == PlaybackState.Playing) {
            PlaybackState.Paused
        } else {
            PlaybackState.Playing
        }
        // Optimistic: the button icon must flip immediately even if tvOS never
        // pushes an `_iMC` event for this particular app.
        playbackState.value = target
        viewModelScope.launch {
            try {
                if (target == PlaybackState.Playing) active.play() else active.pause()
            } catch (e: Exception) {
                // Some titles only accept the HID toggle; fall back to it.
                val fallback = runCatching { active.pressButton(HidCommand.PlayPause) }
                if (fallback.isSuccess) {
                    playbackState.value = target
                } else {
                    playbackState.value = previous
                }
            }
        }
    }

    fun skipBy(seconds: Double) = withClient { it.skipBy(seconds) }

    /**
     * Simulates the Siri Remote's mute button: the physical remote sends HID
     * command 18 (down + up), and the Apple TV applies its own mute/CEC/IR
     * routing. This is different from writing volume 0 over `_mcc`, which can
     * change the reported volume without muting the actual audio route.
     */
    fun toggleMute() {
        if (demoMode.value) {
            muted.value = !muted.value
            return
        }
        if (connectionState.value != ConnectionState.Connected) return
        val active = client ?: return
        val target = !muted.value
        muted.value = target
        // Muting can make tvOS push a fresh (sometimes zero-position) snapshot;
        // ignore that churn so the progress bar stays where it was.
        active.suppressNowPlayingFor(1_500)
        Log.d("TVRemoteMute", "mute target=$target")
        viewModelScope.launch {
            runCatching { active.pressButton(HidCommand.Mute) }
                .onFailure {
                    muted.value = !target
                    message.value = "静音键发送失败，请重试。"
                }
        }
    }

    private fun syncMuteState(activeClient: CompanionClient) {
        viewModelScope.launch {
            if (!activeClient.volumeControlAvailable.value) return@launch
            val level = runCatching { activeClient.getVolume() }.getOrNull() ?: return@launch
            muted.value = level <= 0.01
        }
    }

    fun clearMessage() {
        message.value = null
    }


    fun setVolumeKeysControlTv(enabled: Boolean) {
        volumeKeysControlTv.value = enabled
        viewModelScope.launch { runCatching { settings.setVolumeKeysControlTv(enabled) } }
    }

    /**
     * Handles a hardware volume key press on behalf of the Activity.
     *
     * Returns true when the event should be swallowed. That only happens while
     * the remote screen owns a live session and the user has left the setting
     * on; everywhere else the phone keeps its normal volume behaviour. Repeats
     * are throttled so holding the key ramps the TV volume instead of flooding
     * the Companion connection.
     */
    fun handleHardwareVolumeKey(keyCode: Int, action: Int): Boolean {
        val up = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return false
        }
        if (!volumeKeysControlTv.value) return false
        if (screen.value !is Screen.Remote) return false
        if (connectionState.value != ConnectionState.Connected) return false
        if (action == KeyEvent.ACTION_UP) return true
        if (demoMode.value) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastVolumeKeyAt < VOLUME_KEY_REPEAT_MS) return true
        lastVolumeKeyAt = now
        if (up) muted.value = false
        pressButton(if (up) HidCommand.VolumeUp else HidCommand.VolumeDown)
        return true
    }

    fun sendTouch(x: Long, y: Long, phase: TouchPhase) {
        if (demoMode.value) return
        if (phase == TouchPhase.Hold) {
            val now = System.currentTimeMillis()
            if (now - lastHoldAt < 16) return
            lastHoldAt = now
        }
        touchQueue.trySend(TouchSample(x, y, phase))
    }

    private fun consumeTouches(newClient: CompanionClient) {
        touchJob?.cancel()
        touchJob = viewModelScope.launch {
            for (sample in touchQueue) {
                runCatching { newClient.touchEvent(sample.x, sample.y, sample.phase) }
            }
        }
    }

    /**
     * A tap on the touchpad is Select. In most media apps selecting a title
     * starts/resumes playback, but tvOS does not reliably push a fresh `_iMC`
     * for third-party apps, so a paused card is flipped to "playing"
     * optimistically. A real `_iMC` update still wins if one arrives.
     */
    fun touchTap() {
        if (demoMode.value) {
            playbackState.value = PlaybackState.Playing
            return
        }
        if (playbackState.value != PlaybackState.Paused) {
            withClient { it.tap() }
            return
        }
        val previous = playbackState.value
        playbackState.value = PlaybackState.Playing
        withClient { active ->
            try {
                active.tap()
            } catch (e: Exception) {
                playbackState.value = previous
                throw e
            }
        }
    }

    fun wake() {
        if (demoMode.value) return
        waking.value = true
        viewModelScope.launch {
            try {
                client?.let { runCatching { it.wake() } }
                delay(1_000)
                val device = activeDevice.value
                if (device != null && connectionState.value == ConnectionState.Disconnected) {
                    val stored = credentials.load(device.deviceId)
                    if (stored != null) connect(device, HapCredentials.parse(stored))
                }
            } finally {
                waking.value = false
            }
        }
    }

    fun sleep() = withClient { it.sleep() }

    fun controlCenter() = withClient { it.holdButton(HidCommand.Home, holdMs = 900) }

    // ---------------------------------------------------------------- Playback

    private fun observePlayback(activeClient: CompanionClient) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            activeClient.playbackState.collect { playbackState.value = it }
        }
    }

    private fun observeVolumeControl(activeClient: CompanionClient) {
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            activeClient.volumeControlAvailable.collect { volumeControlAvailable.value = it }
        }
    }

    private fun observePower(activeClient: CompanionClient) {
        powerJob?.cancel()
        powerJob = viewModelScope.launch {
            // A screensaver might already be running when we (re)connect, and
            // not every tvOS version pushes the state right away.
            launch {
                runCatching { activeClient.fetchAttentionState() }.getOrNull()?.let { status ->
                    if (tvPower.value == SystemStatus.Unknown) applyPowerStatus(status)
                }
            }
            activeClient.systemStatus.collect { status -> applyPowerStatus(status) }
        }
    }

    /**
     * Sleep and the screensaver both mean nothing is being watched. Reset the
     * transport state to paused so the in-app card, the lock-screen media card
     * and the notification action all show the play icon instead of a stale
     * pause icon; a real `_iMC` update after waking still takes over.
     */
    private fun applyPowerStatus(status: SystemStatus) {
        tvPower.value = status
        if ((status == SystemStatus.Asleep || status == SystemStatus.Screensaver) &&
            playbackState.value == PlaybackState.Playing
        ) {
            // Only "playing" needs the reset: stopped must stay stopped so the
            // pending now-playing teardown still runs, and paused already shows
            // the play icon.
            playbackState.value = PlaybackState.Paused
        }
    }

    private fun observeNowPlaying(activeClient: CompanionClient) {
        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            activeClient.nowPlaying.collect {
                nowPlayingAnchorMs = SystemClock.elapsedRealtime()
                nowPlaying.value = it
            }
        }
    }

    // ---------------------------------------------------------------- Keyboard

    private fun observeKeyboard(activeClient: CompanionClient) {
        keyboardJob?.cancel()
        keyboardJob = viewModelScope.launch {
            activeClient.keyboardFocus.collect { focus ->
                keyboardFocus.value = focus
                if (focus == KeyboardFocusState.Focused) {
                    runCatching { activeClient.textGet() }
                        .getOrNull()
                        ?.let { keyboardText.value = it }
                }
            }
        }
    }

    fun onKeyboardTextChanged(value: String) {
        keyboardText.value = value
        textJob?.cancel()
        if (demoMode.value) return
        textJob = viewModelScope.launch {
            delay(220)
            val active = client ?: return@launch
            runCatching { active.textSet(value) }
        }
    }

    fun clearKeyboardText() {
        keyboardText.value = ""
        textJob?.cancel()
        withClient { it.textClear() }
    }

    fun dismissKeyboard() {
        keyboardFocus.value = KeyboardFocusState.Unknown
    }

    // -------------------------------------------------------------------- Apps

    fun loadApps(force: Boolean = false) {
        if (demoMode.value) {
            if (apps.value == null || force) apps.value = demoApps
            return
        }
        if (appsLoading.value) return
        if (apps.value != null && !force) return
        appsError.value = null
        appsLoading.value = true
        val active = client
        if (active == null) {
            appsLoading.value = false
            appsError.value = "尚未连接 Apple TV"
            return
        }
        viewModelScope.launch {
            try {
                apps.value = active.appList()
                    .map { AppEntry(it.key, it.value) }
                    .sortedBy { it.name.lowercase() }
            } catch (e: Exception) {
                appsError.value = friendlyError(e)
            } finally {
                appsLoading.value = false
            }
        }
    }

    fun launchApp(bundleId: String) = withClient { it.launchApp(bundleId) }

    // ------------------------------------------------------------------- Demo

    /**
     * A debug-only preview used to review the interface without Apple TV
     * hardware. It never appears in release builds.
     */
    fun enterDemo() {
        if (!BuildConfig.DEBUG) return
        connectionJob?.cancel()
        client?.close()
        client = null
        demoMode.value = true
        activeDevice.value = DiscoveredAtv(
            deviceId = "demo",
            name = "客厅的 Apple TV",
            host = "demo",
            port = 0,
            model = "AppleTV14,1",
        )
        screen.value = Screen.Remote(activeDevice.value!!)
        connectionState.value = ConnectionState.Connected
        connectionError.value = null
        apps.value = demoApps
        keyboardFocus.value = KeyboardFocusState.Unknown
        playbackState.value = PlaybackState.Paused
        muted.value = false
        val demoArtwork = runCatching {
            getApplication<Application>().assets.open("demo_artwork.jpg").use { it.readBytes() }
        }.getOrNull()
        nowPlaying.value = NowPlaying(
            title = "甄嬛传",
            artist = "第 24 集 · 电视剧",
            durationSeconds = 2709.0,
            elapsedSeconds = 1466.0,
            playbackRate = 1.0,
            artwork = demoArtwork,
        )
    }

    fun showDemoDevices() {
        if (!BuildConfig.DEBUG) return
        demoDevicesMode = true
        scanJob?.cancel()
        scanJob = null
        devices.value = DeviceListUi(
            devices = demoDevices,
            pairedKeys = setOf(credentials.deviceKey("demo-living-room")),
            scanning = false,
        )
    }

    private fun friendlyError(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("proof mismatch", ignoreCase = true) -> "配对码不正确，请重新输入。"
            raw.contains("pairing error code 2", ignoreCase = true) ->
                "配对码不正确，请在电视上确认后重试。"
            raw.contains("pairing error", ignoreCase = true) ->
                "Apple TV 拒绝了配对请求，请重新获取配对码。"
            raw.contains("no salt", ignoreCase = true) -> "Apple TV 没有响应配对请求，请稍后重试。"
            e is java.net.ConnectException -> "无法连接 Apple TV，请确认两台设备在同一 Wi-Fi。"
            e is java.net.SocketTimeoutException -> "连接超时，请确认 Apple TV 已唤醒。"
            raw.contains("connection closed", ignoreCase = true) -> "连接已断开，请重新连接。"
            raw.contains("timeout", ignoreCase = true) -> "连接超时，请确认 Apple TV 已唤醒。"
            raw.isBlank() -> "发生未知错误（${e.javaClass.simpleName}）"
            else -> raw
        }
    }

    override fun onCleared() {
        connectionJob?.cancel()
        pairingJob?.cancel()
        scanJob?.cancel()
        playbackJob?.cancel()
        nowPlayingJob?.cancel()
        powerJob?.cancel()
        volumeJob?.cancel()
        closePairingTransport()
        client?.close()
        LockScreenMedia.commandHandler = null
        LockScreenMedia.state.value = null
        stopLockScreenService()
        super.onCleared()
    }

    private data class TouchSample(val x: Long, val y: Long, val phase: TouchPhase)

    private companion object {
        /** Minimum gap between volume steps while a volume key is held down. */
        const val VOLUME_KEY_REPEAT_MS = 120L
    }
}

data class AppEntry(val bundleId: String, val name: String)

private val demoDevices = listOf(
    DiscoveredAtv(
        deviceId = "demo-living-room",
        name = "客厅的 Apple TV",
        host = "192.168.1.42",
        port = 49153,
        model = "AppleTV14,1",
    ),
    DiscoveredAtv(
        deviceId = "demo-bedroom",
        name = "卧室 Apple TV",
        host = "192.168.1.43",
        port = 49153,
        model = "AppleTV11,1",
    ),
)

private val demoApps = listOf(
    AppEntry("com.apple.TVWatchList", "Apple TV"),
    AppEntry("com.apple.TVMusic", "音乐"),
    AppEntry("com.apple.TVAppStore", "App Store"),
    AppEntry("com.apple.podcasts", "播客"),
    AppEntry("com.apple.TVPhotos", "照片"),
    AppEntry("com.apple.TVSearch", "搜索"),
    AppEntry("com.apple.TVSettings", "设置"),
    AppEntry("com.netflix.Netflix", "Netflix"),
    AppEntry("com.google.ios.youtube", "YouTube"),
    AppEntry("com.disney.disneyplus", "Disney+"),
    AppEntry("com.hulu.plus", "Hulu"),
    AppEntry("com.spotify.client", "Spotify"),
)
