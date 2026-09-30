package com.orbital.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrbitalBridgeClient @Inject constructor(
    private val context: Context,
    private val actionDispatcher: BridgeActionDispatcher,
    val cryptoAuth: OrbitalCryptoAuth,
    private val httpClient: OkHttpClient = OrbitalTlsHelper.createSecureBridgeHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val TAG = "OrbitalBridgeClient"
        const val DEFAULT_LOCAL_PORT = 8765
        const val PUBLIC_RELAY_BASE = "wss://relay.orbital-agent.workers.dev/ws"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(BridgeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BridgeConnectionState> = _connectionState.asStateFlow()

    private val _activeChannelCode = MutableStateFlow<String?>(null)
    val activeChannelCode: StateFlow<String?> = _activeChannelCode.asStateFlow()

    private val _connectedHostName = MutableStateFlow<String?>("Authorized Laptop")
    val connectedHostName: StateFlow<String?> = _connectedHostName.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(emptyList())
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    fun log(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formatted = "[$time] $message"
        _eventLogs.value = (_eventLogs.value + formatted).takeLast(50)
        Log.i(TAG, message)
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
    }

    /**
     * Connects directly from a scanned QR Code URI payload:
     * e.g. orbital://pair?v=1&host=192.168.1.5&port=8765&pin=ORB-1234&key=0123...&name=MacBook
     */
    fun connectFromQr(qrContent: String): Boolean {
        try {
            val uri = android.net.Uri.parse(qrContent.trim())
            if (uri.scheme != "orbital" || uri.host != "pair") {
                // If it's a raw WSS or IP string
                connect(qrContent.trim())
                return true
            }

            val host = uri.getQueryParameter("host") ?: return false
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: DEFAULT_LOCAL_PORT
            val pin = uri.getQueryParameter("pin")
            val authKey = uri.getQueryParameter("key")
            val hostName = uri.getQueryParameter("name") ?: "Laptop Bridge"

            if (!authKey.isNullOrBlank()) {
                cryptoAuth.establishSession(authKey, pin, hostName)
                _connectedHostName.value = hostName
                log("🔐 Bitcoin-grade crypto key registered (Fingerprint: ${cryptoAuth.getFingerprint()})")
            }

            val target = "wss://$host:$port"
            connect(target)
            return true
        } catch (e: Exception) {
            log("❌ Invalid QR code payload: ${e.message}")
            return false
        }
    }

    /**
     * Connects directly to a discovered laptop via QuickShare NSD.
     */
    fun connect(laptop: DiscoveredLaptop) {
        val target = "wss://${laptop.host}:${laptop.port}"
        _connectedHostName.value = laptop.name
        connect(target)
    }

    /**
     * Connects to a laptop via 6-digit PIN or direct IP/Host.
     */
    fun connect(target: String) {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) return

        disconnect()
        _connectionState.value = BridgeConnectionState.CONNECTING
        _activeChannelCode.value = cleanTarget
        log("Connecting to bridge target: $cleanTarget")

        val wsUrl = resolveWebSocketUrl(cleanTarget)
        log("Target URL: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = BridgeConnectionState.CONNECTED
                log("🟢 Connected to Laptop AI Bridge successfully!")

                val pairingMsg = BridgeMessage(
                    type = "PAIRING",
                    channelCode = cleanTarget,
                    token = cryptoAuth.getSessionToken(),
                    authFingerprint = cryptoAuth.getFingerprint(),
                    rawText = "Orbital Android connected (${android.os.Build.MODEL})"
                )
                sendMessage(pairingMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleIncomingMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log("Connection closing: $reason (code=$code)")
                _connectionState.value = BridgeConnectionState.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("Connection closed: $reason")
                _connectionState.value = BridgeConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("🔴 Connection error: ${t.message ?: "Unknown error"}")
                _connectionState.value = BridgeConnectionState.ERROR
            }
        })
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        _connectionState.value = BridgeConnectionState.DISCONNECTED
        _activeChannelCode.value = null
        log("Disconnected from Laptop AI Bridge")
    }

    fun sendMessage(msg: BridgeMessage) {
        val timestamp = System.currentTimeMillis()
        val signature = cryptoAuth.signMessage(msg.action?.actionId ?: msg.type, timestamp)
        val securedMsg = msg.copy(
            token = cryptoAuth.getSessionToken() ?: msg.token,
            signature = signature ?: msg.signature,
            authFingerprint = cryptoAuth.getFingerprint(),
            timestamp = timestamp
        )
        val text = json.encodeToString(BridgeMessage.serializer(), securedMsg)
        webSocket?.send(text)
    }

    private suspend fun handleIncomingMessage(rawJson: String) {
        val msg = runCatching {
            json.decodeFromString(BridgeMessage.serializer(), rawJson)
        }.getOrNull() ?: return

        // 🔒 Cryptographic Message Verification (Bitcoin-grade HMAC-SHA256)
        if (!cryptoAuth.verifyIncomingMessage(msg)) {
            log("🚨 UNAUTHORIZED COMMAND BLOCKED: Message failed cryptographic signature verification!")
            val alert = BridgeMessage(
                type = "SECURITY_ALERT",
                rawText = "Rejected: Cryptographic signature mismatch or unauthorized sender"
            )
            val signedAlert = json.encodeToString(BridgeMessage.serializer(), alert)
            webSocket?.send(signedAlert)
            return
        }

        when (msg.type) {
            "HEARTBEAT" -> {
                sendMessage(BridgeMessage(type = "HEARTBEAT_ACK"))
            }

            "INSPECT_SCREEN" -> {
                log("Received screen inspection request from Laptop AI")
                val screenState = actionDispatcher.captureScreenState()
                val reply = BridgeMessage(
                    type = "SCREEN_STATE",
                    screenState = screenState
                )
                sendMessage(reply)
                log("Sent screen state snapshot (${screenState.nodes.size} nodes)")
            }

            "EXECUTE_ACTION" -> {
                val action = msg.action
                if (action != null) {
                    log("⚡ Executing authenticated AI action: ${action.actionType} ${action.targetText.orEmpty()}")
                    val result = actionDispatcher.dispatchAction(action)
                    val reply = BridgeMessage(
                        type = "ACTION_RESULT",
                        result = result
                    )
                    sendMessage(reply)
                    log("Action result sent: ${result.message} (${result.executionDurationMs}ms)")
                }
            }
        }
    }

    private fun resolveWebSocketUrl(target: String): String {
        return if (target.startsWith("ws://") || target.startsWith("wss://")) {
            target
        } else if (target.contains(".")) {
            val portSuffix = if (!target.contains(":")) ":$DEFAULT_LOCAL_PORT" else ""
            "wss://$target$portSuffix"
        } else {
            val cleanCode = target.uppercase().removePrefix("ORB-")
            "$PUBLIC_RELAY_BASE?channel=$cleanCode"
        }
    }
}
