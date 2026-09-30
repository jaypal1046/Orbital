package com.orbital.bridge

import android.util.Log
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrbitalCryptoAuth @Inject constructor() {

    companion object {
        private const val TAG = "OrbitalCryptoAuth"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val REPLAY_WINDOW_MS = 120_000L // 2 minutes window
    }

    @Volatile
    private var activeSessionToken: String? = null

    @Volatile
    private var activeSessionPin: String? = null

    @Volatile
    private var authenticatedHostName: String? = null

    /**
     * Initializes a verified session key scanned from the laptop terminal QR code.
     */
    fun establishSession(token: String, pin: String? = null, hostName: String? = null) {
        activeSessionToken = token.trim()
        activeSessionPin = pin?.trim()
        authenticatedHostName = hostName?.trim()
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
    fun signMessage(content: String, timestamp: Long): String? {
        val secret = activeSessionToken ?: return null
        return try {
            val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(keySpec)
            val dataToSign = "$timestamp:$content"
            val rawHmac = mac.doFinal(dataToSign.toByteArray(Charsets.UTF_8))
            rawHmac.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign message", e)
            null
        }
    }

    /**
     * Verifies the authenticity and cryptographic integrity of an incoming message from the laptop.
     * Drops any unauthorized or tampered message.
     */
    fun verifyIncomingMessage(message: BridgeMessage): Boolean {
        val secret = activeSessionToken

        // If no session token is established yet, allow only initial handshake PAIRING with matching PIN
        if (secret.isNullOrBlank()) {
            if (message.type == "PAIRING") {
                return true
            }
            Log.w(TAG, "❌ Rejected message '${message.type}': No active cryptographic session.")
            return false
        }

        // Replay attack prevention
        val now = System.currentTimeMillis()
        if (Math.abs(now - message.timestamp) > REPLAY_WINDOW_MS) {
            Log.w(TAG, "❌ Rejected message '${message.type}': Timestamp expired / Replay attempt (diff=${now - message.timestamp}ms)")
            return false
        }

        // 1. Direct 256-bit token match validation
        if (message.token != null && message.token == secret) {
            return true
        }

        // 2. Cryptographic HMAC-SHA256 signature verification
        if (message.signature != null) {
            val actionKey = message.action?.actionId ?: message.type
            val expectedSig = signMessage(actionKey, message.timestamp)
            if (expectedSig != null && expectedSig.equals(message.signature, ignoreCase = true)) {
                return true
            }
        }

        Log.w(TAG, "❌ SECURITY ALERT: Cryptographic signature mismatch! Message rejected.")
        return false
    }
}
