package com.orbital.bridge

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredLaptop(
    val name: String,
    val host: String,
    val port: Int,
    val pin: String? = null,
    val ip: String? = null,
    val fingerprint: String? = null
)

@Singleton
class OrbitalDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OrbitalDiscoveryService"
        const val SERVICE_TYPE = "_orbital-bridge._tcp"
        const val DEFAULT_PORT = 8765
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredLaptops = MutableStateFlow<List<DiscoveredLaptop>>(emptyList())
    val discoveredLaptops: StateFlow<List<DiscoveredLaptop>> = _discoveredLaptops.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    val isScanning: StateFlow<Boolean> get() = isSearching

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var probeJob: Job? = null

    private val fastProbeClient = OkHttpClient.Builder()
        .connectTimeout(650, TimeUnit.MILLISECONDS)
        .readTimeout(650, TimeUnit.MILLISECONDS)
        .build()

    fun startDiscovery() {
        if (_isSearching.value) return
        _isSearching.value = true
        _discoveredLaptops.value = emptyList()

        // 1. Acquire Wi-Fi Multicast Lock for Android OS mDNS packet reception
        try {
            multicastLock = wifiManager?.createMulticastLock("orbital-bridge-multicast")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Wi-Fi Multicast Lock acquired.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }

        // 2. Start Android Native mDNS Discovery
        startNsdDiscovery()

        // 3. Start Concurrent Subnet Fast Probe (bypasses router AP isolation & multicast filters)
        startSubnetSweep()
    }

    private fun startNsdDiscovery() {
        val manager = nsdManager ?: return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service found: ${service.serviceName}")
                if (service.serviceType.contains("orbital-bridge")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service lost: ${service.serviceName}")
                _discoveredLaptops.value = _discoveredLaptops.value.filterNot { it.name == service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: Error code: $errorCode")
                try {
                    manager.stopServiceDiscovery(this)
                } catch (_: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: Error code: $errorCode")
            }
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val manager = nsdManager ?: return

        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: Error code: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName
                val pin = serviceInfo.attributes?.get("pin")?.let { String(it) }
                val fingerprint = serviceInfo.attributes?.get("fingerprint")?.let { String(it) }

                addDiscoveredLaptop(
                    DiscoveredLaptop(
                        name = name,
                        host = host,
                        port = port,
                        pin = pin,
                        ip = host,
                        fingerprint = fingerprint
                    )
                )
            }
        })
    }

    /**
     * Fast local subnet prober to find laptop even if router blocks mDNS multicast.
     */
    private fun startSubnetSweep() {
        probeJob?.cancel()
        probeJob = scope.launch {
            val localIp = getLocalIpAddress() ?: return@launch
            val prefix = localIp.substringBeforeLast(".") + "."
            val myLastOctet = localIp.substringAfterLast(".").toIntOrNull() ?: 1

            // Prioritize common gateway / laptop IPs and nearby octets
            val prioritizedOctets = mutableListOf<Int>()
            for (offset in 1..20) {
                if (myLastOctet - offset in 1..254) prioritizedOctets.add(myLastOctet - offset)
                if (myLastOctet + offset in 1..254) prioritizedOctets.add(myLastOctet + offset)
            }
            prioritizedOctets.addAll(listOf(1, 2, 100, 101, 102, 103, 104, 105, 106, 111, 112, 115, 120, 150, 200))
            val allOctets = (prioritizedOctets + (1..254).toList()).distinct()

            allOctets.chunked(32).forEach { batch ->
                if (!_isSearching.value) return@launch
                batch.map { octet ->
                    async {
                        probeHost("$prefix$octet", DEFAULT_PORT)
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun probeHost(ip: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$ip:${port + 1}/")
                .build()

            fastProbeClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    if (json.optString("status") == "ok") {
                        val name = json.optString("host", "Laptop Bridge")
                        val pin = json.optString("pin")
                        addDiscoveredLaptop(
                            DiscoveredLaptop(
                                name = "$name (Orbital Bridge)",
                                host = ip,
                                port = port,
                                pin = pin,
                                ip = ip
                            )
                        )
                        Log.i(TAG, "🎯 Direct subnet probe discovered laptop at $ip:$port!")
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun addDiscoveredLaptop(laptop: DiscoveredLaptop) {
        val current = _discoveredLaptops.value.toMutableList()
        current.removeAll { it.host == laptop.host && it.port == laptop.port }
        current.add(laptop)
        _discoveredLaptops.value = current
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun stopDiscovery() {
        _isSearching.value = false
        probeJob?.cancel()
        probeJob = null

        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
        discoveryListener = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d(TAG, "Wi-Fi Multicast Lock released.")
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
