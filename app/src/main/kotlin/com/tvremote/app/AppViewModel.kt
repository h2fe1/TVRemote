package com.tvremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.app.data.CredentialsRepository
import com.tvremote.app.discovery.AtvDiscovery
import com.tvremote.app.discovery.DiscoveredAtv
import dev.companionremote.protocol.client.CompanionClient
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import dev.companionremote.protocol.client.TouchPhase
import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.protocol.transport.SocketTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

    private var client: CompanionClient? = null
    private var connectionJob: Job? = null
    private var scanJob: Job? = null
    private var pairingJob: Job? = null
    private var pairingConnection: CompanionConnection? = null
    private var pairingSetup: PairSetup? = null
    private var keyboardJob: Job? = null
    private var textJob: Job? = null
    private var touchJob: Job? = null
    private var connectionToken = 0
    private var lastHoldAt = 0L
    private var demoDevicesMode = false
    private val touchQueue = Channel<TouchSample>(capacity = 256)

    init {
        viewModelScope.launch { refreshPairedKeys() }
        startScan()
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

    fun touchTap() = withClient { it.tap() }

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
        closePairingTransport()
        client?.close()
        super.onCleared()
    }

    private data class TouchSample(val x: Long, val y: Long, val phase: TouchPhase)
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
