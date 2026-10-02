package com.orbital.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OrbitalCryptoAuthTest {

    private lateinit var cryptoAuth: OrbitalCryptoAuth

    @Before
    fun setUp() {
        cryptoAuth = OrbitalCryptoAuth()
    }

    private fun buildTestSignatureKey(action: ActionPayload): String {
        val stepsStr = action.batchSteps?.joinToString(";") { s ->
            "${s.stepIndex}:${s.actionType}:${s.targetText.orEmpty()}:${s.targetId.orEmpty()}:${s.packageName.orEmpty()}:${s.keyCode.orEmpty()}:${s.textToType.orEmpty()}:${s.coordinates?.joinToString(",") ?: ""}:${s.deviceAction.orEmpty()}:${s.assertionText.orEmpty()}"
        }.orEmpty()
        return "${action.actionId}:${action.actionType}:${action.targetText.orEmpty()}:${action.targetId.orEmpty()}:${action.packageName.orEmpty()}:${action.keyCode.orEmpty()}:${action.textToType.orEmpty()}:${action.coordinates?.joinToString(",") ?: ""}:${action.deviceAction.orEmpty()}:${action.customPrompt.orEmpty()}:${action.sessionCommand.orEmpty()}:${action.sessionId.orEmpty()}:${action.sessionTitle.orEmpty()}:$stepsStr"
    }

    @Test
    fun testInitialStateIsUnauthenticated() {
        assertFalse("Initial session should not be active", cryptoAuth.isSessionActive())
        assertEquals("UNAUTHENTICATED", cryptoAuth.getFingerprint())
    }

    @Test
    fun testEstablishSessionAndFingerprint() {
        val testToken = "test-crypto-token-256bit-secret-key-12345"
        val testPin = "ORB-9999"
        val testHost = "MacBook-Pro"

        cryptoAuth.establishSession(testToken, testPin, testHost)

        assertTrue("Session should be active", cryptoAuth.isSessionActive())
        assertEquals(testToken, cryptoAuth.getSessionToken())
        assertEquals(testPin, cryptoAuth.getSessionPin())
        assertEquals(testHost, cryptoAuth.getAuthenticatedHost())

        val fingerprint = cryptoAuth.getFingerprint()
        assertNotNull("Fingerprint must not be null", fingerprint)
        assertTrue("Fingerprint should be formatted with colons", fingerprint.contains(":"))
        assertEquals("Fingerprint should be 19 chars (4 groups of 4 with 3 colons)", 19, fingerprint.length)
    }

    @Test
    fun testSignAndVerifyMessageWithNonce() {
        val testToken = "secure-secret-token"
        cryptoAuth.establishSession(testToken, "ORB-1111", "Host")

        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val action = ActionPayload(actionId = "act-123", actionType = BridgeActionType.OPEN_APP, packageName = "com.test.app")
        val actionKey = buildTestSignatureKey(action)
        val signature = cryptoAuth.signMessage(actionKey, timestamp, nonce)

        assertNotNull("Signature should be generated", signature)
        assertTrue("Signature should not be empty", signature!!.isNotEmpty())

        val bridgeMsg = BridgeMessage(
            type = "EXECUTE_ACTION",
            action = action,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )

        val isValid = cryptoAuth.verifyIncomingMessage(bridgeMsg)
        assertTrue("Incoming validly signed message must pass verification", isValid)
    }

    @Test
    fun testReplayedNonceIsRejected() {
        val testToken = "secure-secret-token"
        cryptoAuth.establishSession(testToken, "ORB-1111", "Host")

        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val action = ActionPayload(actionId = "act-123", actionType = BridgeActionType.INSPECT_SCREEN)
        val actionKey = buildTestSignatureKey(action)
        val signature = cryptoAuth.signMessage(actionKey, timestamp, nonce)

        val bridgeMsg = BridgeMessage(
            type = "EXECUTE_ACTION",
            action = action,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )

        val firstPass = cryptoAuth.verifyIncomingMessage(bridgeMsg)
        assertTrue("First message with fresh nonce must pass", firstPass)

        val secondPass = cryptoAuth.verifyIncomingMessage(bridgeMsg)
        assertFalse("Second message reusing the identical nonce must be rejected as replay", secondPass)
    }

    @Test
    fun testReplayAttackExpiredTimestampRejected() {
        val testToken = "secure-secret-token"
        cryptoAuth.establishSession(testToken, "ORB-1111", "Host")

        // Timestamp 30 seconds in the past (> 15s replay window)
        val expiredTimestamp = System.currentTimeMillis() - 30_000L
        val nonce = UUID.randomUUID().toString()
        val action = ActionPayload(actionId = "act-expired", actionType = BridgeActionType.OPEN_APP)
        val actionKey = buildTestSignatureKey(action)
        val signature = cryptoAuth.signMessage(actionKey, expiredTimestamp, nonce)

        val expiredMsg = BridgeMessage(
            type = "EXECUTE_ACTION",
            action = action,
            timestamp = expiredTimestamp,
            nonce = nonce,
            signature = signature
        )

        val isValid = cryptoAuth.verifyIncomingMessage(expiredMsg)
        assertFalse("Expired message outside replay window must be rejected", isValid)
    }

    @Test
    fun testTamperedSignatureRejected() {
        val testToken = "secure-secret-token"
        cryptoAuth.establishSession(testToken, "ORB-1111", "Host")

        val timestamp = System.currentTimeMillis()
        val tamperedMsg = BridgeMessage(
            type = "EXECUTE_ACTION",
            action = ActionPayload(actionId = "ORIGINAL_ACTION", actionType = BridgeActionType.OPEN_APP),
            timestamp = timestamp,
            nonce = UUID.randomUUID().toString(),
            signature = "tampered_invalid_signature_hex_0000000000000"
        )

        val isValid = cryptoAuth.verifyIncomingMessage(tamperedMsg)
        assertFalse("Tampered signature must be rejected", isValid)
    }

    @Test
    fun testClearSessionResetsState() {
        cryptoAuth.establishSession("token", "pin", "host")
        assertTrue(cryptoAuth.isSessionActive())

        cryptoAuth.clearSession()
        assertFalse("Session should be inactive after clear", cryptoAuth.isSessionActive())
        assertEquals("UNAUTHENTICATED", cryptoAuth.getFingerprint())
    }
}
