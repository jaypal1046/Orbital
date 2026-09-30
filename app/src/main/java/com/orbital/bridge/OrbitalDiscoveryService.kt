package com.orbital.bridge

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredLaptop(
    val name: String,
    val host: String,
    val port: Int,
    val pin: String? = null,
    val ip: String? = null
)

@Singleton
class OrbitalDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OrbitalDiscoveryService"
        const val SERVICE_TYPE = "_orbital-bridge._tcp."
    }

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val _discoveredLaptops = MutableStateFlow<List<DiscoveredLaptop>>(emptyList())
    val discoveredLaptops: StateFlow<List<DiscoveredLaptop>> = _discoveredLaptops.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        if (_isSearching.value) return
        val manager = nsdManager ?: return

        _isSearching.value = true
        _discoveredLaptops.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "QuickShare NSD Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}")
                if (service.serviceType.contains("orbital-bridge")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
                _discoveredLaptops.value = _discoveredLaptops.value.filterNot { it.name == service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isSearching.value = false
                Log.d(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: Error code: $errorCode")
                _isSearching.value = false
                try {
                    manager.stopServiceDiscovery(this)
                } catch (_: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: Error code: $errorCode")
                _isSearching.value = false
            }
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
            _isSearching.value = false
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

                val laptop = DiscoveredLaptop(
                    name = name,
                    host = host,
                    port = port,
                    pin = pin,
                    ip = host
                )

                Log.i(TAG, "Resolved Laptop: $name at $host:$port (PIN=$pin)")

                val current = _discoveredLaptops.value.toMutableList()
                current.removeAll { it.host == host && it.port == port }
                current.add(laptop)
                _discoveredLaptops.value = current
            }
        })
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
            discoveryListener = null
        }
        _isSearching.value = false
    }
}
