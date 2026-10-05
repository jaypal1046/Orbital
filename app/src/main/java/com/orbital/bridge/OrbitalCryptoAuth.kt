package com.orbital.bridge

import android.util.Log
import com.orbital.data.SecureStorage
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrbitalCryptoAuth @Inject constructor(
    private val secureStorage: SecureStorage
) {

    companion object {
        private const val TAG = "OrbitalCryptoAuth"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val REPLAY_WINDOW_MS = 15_000L // Strict 15-second replay window
        private const val MAX_SEEN_NONCES = 1000
    }

    @Volatile
    private var activeSessionToken: String? = null

    @Volatile
    private var activeSessionPin: String? = null

    @Volatile
    private var authenticatedHostName: String? = null

    // Thread-safe LRU cache for tracking message nonces to prevent replay attacks
    private val seenNonces: MutableMap<String, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(MAX_SEEN_NONCES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > MAX_SEEN_NONCES
            }
        }
    )

    init {
        secureStorage?.getBridgeSessionToken()?.takeIf { it.isNotBlank() }?.let { token ->
            activeSessionToken = token
            activeSessionPin = secureStorage.getBridgeSessionPin()
            authenticatedHostName = secureStorage.getBridgeSessionHost()
        }
    }

    /**
     * Initializes a verified session key scanned from the laptop terminal QR code or authenticated handshake.
     */
    fun establishSession(token: String, pin: String? = null, hostName: String? = null) {
        activeSessionToken = token.trim()
        activeSessionPin = pin?.trim()
        authenticatedHostName = hostName?.trim()
        secureStorage?.saveBridgeSession(activeSessionToken.orEmpty(), activeSessionPin, authenticatedHostName)
        Log.i(TAG, "🔒 Bitcoin-grade Cryptographic Session Established. Fingerprint: ${getFingerprint()}")
    }

    fun isSessionActive(): Boolean {
        return !activeSessionToken.isNullOrBlank()
    }

    fun getSessionToken(): String? = activeSessionToken

    fun getSessionPin(): String? = activeSessionPin

    fun getAuthenticatedHost(): String = authenticatedHostName ?: "Authorized Laptop"

    fun clearSession() {
        activeSessionToken = null
        activeSessionPin = null
        authenticatedHostName = null
        seenNonces.clear()
        secureStorage?.clearBridgeSession()
        Log.i(TAG, "Session key cleared.")
    }

    /**
     * Generates a human-readable SHA-256 fingerprint of the active crypto key.
     * E.g. "4A8F:B12C:99E1:F3D0"
     */
    fun getFingerprint(): String {
        val token = activeSessionToken ?: return "UNAUTHENTICATED"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(token.toByteArray(Charsets.UTF_8))
            val hex = hash.joinToString("") { "%02X".format(it) }
            hex.take(16).chunked(4).joinToString(":")
        } catch (e: Exception) {
            "ERR_FINGERPRINT"
        }
    }

    /**
     * Generates an HMAC-SHA256 signature for outgoing messages.
     */
    fun signMessage(content: String, timestamp: Long, nonce: String? = null): String? {
        val secret = activeSessionToken ?: return null
        return try {
            val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(keySpec)
            val dataToSign = "$timestamp:${nonce.orEmpty()}:$content"
            val rawHmac = mac.doFinal(dataToSign.toByteArray(Charsets.UTF_8))
            rawHmac.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign message", e)
            null
        }
    }

    /**
     * Verifies the authenticity and cryptographic integrity of an incoming message from the laptop.
     * Strictly validates HMAC-SHA256 signature and rejects expired timestamps or replayed nonces.
     */
    fun verifyIncomingMessage(message: BridgeMessage): Boolean {
        val secret = activeSessionToken

        // Pairing ACK is valid if session is already established with matching token OR if transitioning to active session
        if (message.type == "PAIRING_ACK") {
            if (!secret.isNullOrBlank() && message.token != secret) {
                Log.w(TAG, "❌ Rejected pairing message: session token mismatch.")
                return false
            }
            return true
        }

        if (secret.isNullOrBlank()) {
            Log.w(TAG, "❌ Rejected message '${message.type}': No active cryptographic session.")
            return false
        }

        // 1. Replay attack prevention: Strict timestamp skew check
        val now = System.currentTimeMillis()
        if (Math.abs(now - message.timestamp) > REPLAY_WINDOW_MS) {
            Log.w(TAG, "❌ Rejected message '${message.type}': Timestamp expired (skew=${now - message.timestamp}ms)")
            return false
        }

        // 2. Replay attack prevention: Nonce uniqueness check
        val nonce = message.nonce
        if (nonce.isNullOrBlank()) {
            Log.w(TAG, "❌ SECURITY ALERT: Missing nonce! Message rejected.")
            return false
        }
        if (seenNonces.containsKey(nonce)) {
            Log.w(TAG, "🚨 REPLAY ATTACK BLOCKED: Nonce '$nonce' already consumed!")
            return false
        }

        // 3. Cryptographic HMAC-SHA256 signature verification over payload
        val incomingSig = message.signature
        if (incomingSig.isNullOrBlank()) {
            Log.w(TAG, "❌ SECURITY ALERT: Missing cryptographic signature! Message rejected.")
            return false
        }

        val actionKey = buildActionSignatureKey(message)
        val expectedSig = signMessage(actionKey, message.timestamp, nonce) ?: return false

        // Constant-time signature comparison to prevent timing attacks
        val isAuthentic = MessageDigest.isEqual(
            expectedSig.toByteArray(Charsets.UTF_8),
            incomingSig.toByteArray(Charsets.UTF_8)
        )

        if (!isAuthentic) {
            Log.w(TAG, "❌ SECURITY ALERT: Cryptographic signature mismatch! Message rejected.")
            return false
        }

        seenNonces[nonce] = now

        return true
    }

    private fun buildActionSignatureKey(message: BridgeMessage): String {
        val action = message.action
        return if (action != null) {
            val stepsStr = action.batchSteps?.joinToString(";") { s ->
                "${s.stepIndex}:${s.actionType}:${s.targetText.orEmpty()}:${s.targetId.orEmpty()}:${s.packageName.orEmpty()}:${s.keyCode.orEmpty()}:${s.textToType.orEmpty()}:${s.coordinates?.joinToString(",") ?: ""}:${s.deviceAction.orEmpty()}:${s.assertionText.orEmpty()}"
            }.orEmpty()
            "${action.actionId}:${action.actionType}:${action.targetText.orEmpty()}:${action.targetId.orEmpty()}:${action.packageName.orEmpty()}:${action.keyCode.orEmpty()}:${action.textToType.orEmpty()}:${action.coordinates?.joinToString(",") ?: ""}:${action.deviceAction.orEmpty()}:${action.customPrompt.orEmpty()}:${action.sessionCommand.orEmpty()}:${action.sessionId.orEmpty()}:${action.sessionTitle.orEmpty()}:$stepsStr"
        } else {
            message.type
        }
    }
}
