package com.tvremote.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * An Apple TV found over Bonjour.
 *
 * [deviceId] prefers Apple's stable `rpMRtID` TXT record. Falling back to the
 * hostname is deliberate: credentials must survive the ephemeral Companion
 * port changing after a reboot.
 */
data class DiscoveredAtv(
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int,
    val model: String?,
) {
    val displayModel: String
        get() = when (model) {
            null, "" -> "Apple TV"
            "AppleTV5,3" -> "Apple TV HD"
            "AppleTV6,2" -> "Apple TV 4K（第 1 代）"
            "AppleTV11,1" -> "Apple TV 4K（第 2 代）"
            "AppleTV14,1" -> "Apple TV 4K（第 3 代）"
            else -> if (model.startsWith("AppleTV")) "Apple TV" else model
        }
}

/**
 * mDNS discovery of Apple's `_companion-link._tcp` service.
 *
 * NsdManager only emits one resolve at a time on many Android builds, so found
 * services are resolved sequentially. A multicast lock is essential: without
 * it discovery can silently return zero results even on a healthy LAN.
 */
class AtvDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun scan(durationMs: Long = 6_000, onDevice: (DiscoveredAtv) -> Unit) {
        val multicastLock = wifiManager.createMulticastLock("tvremote-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
        val found = ConcurrentHashMap<String, NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                found[serviceInfo.serviceName] = serviceInfo
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            val deadline = System.currentTimeMillis() + durationMs
            val resolved = mutableSetOf<String>()
            while (System.currentTimeMillis() < deadline) {
                val pending = found.entries.firstOrNull { it.key !in resolved }
                if (pending == null) {
                    delay(180)
                    continue
                }
                resolved.add(pending.key)
                resolve(pending.value)?.let(onDevice)
            }
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            runCatching { multicastLock.release() }
        }
    }

    /**
     * Re-resolve the current host and port of an Apple TV by its stable ID.
     * Used after a failed direct connect, because tvOS changes this port on
     * reboot and after some sleep/wake cycles.
     */
    suspend fun resolve(device: DiscoveredAtv, timeoutMs: Long = 5_000): DiscoveredAtv? =
        withTimeoutOrNull(timeoutMs) {
            var result: DiscoveredAtv? = null
            scan(timeoutMs) { candidate ->
                if (result == null && candidate.deviceId == device.deviceId) result = candidate
            }
            result
        }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): DiscoveredAtv? =
        withTimeoutOrNull(4_000) {
            suspendCancellableCoroutine { continuation ->
                nsdManager.resolveService(
                    info,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress
                            if (host == null) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            val model = serviceInfo.attributes.string("rpMd")
                            val flags = serviceInfo.attributes.string("rpFl")
                                ?.removePrefix("0x")?.toIntOrNull(16) ?: 0
                            val isAppleTv = model?.startsWith("AppleTV") == true ||
                                (model.isNullOrEmpty() && (flags and PAIRABLE_MASK) != 0)
                            if (!isAppleTv) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            val stableId = serviceInfo.attributes.string("rpMRtID")
                                ?: serviceInfo.attributes.string("rpMac")
                                ?: info.serviceName
                            if (continuation.isActive) {
                                continuation.resume(
                                    DiscoveredAtv(
                                        deviceId = stableId,
                                        name = serviceInfo.serviceName,
                                        host = host,
                                        port = serviceInfo.port,
                                        model = model,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }

    private fun Map<String, ByteArray>.string(key: String): String? =
        this[key]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }

    companion object {
        const val SERVICE_TYPE = "_companion-link._tcp."
        private const val PAIRABLE_MASK = 0x4000
    }
}
