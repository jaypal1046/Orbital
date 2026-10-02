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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OrbitalCryptoAuthTest {

    private lateinit var cryptoAuth: OrbitalCryptoAuth

    @Before
    fun setUp() {
        cryptoAuth = OrbitalCryptoAuth()
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
    fun testSignAndVerifyMessage() {
        val testToken = "secure-secret-token"
        cryptoAuth.establishSession(testToken, "ORB-1111", "Host")

        val timestamp = System.currentTimeMillis()
        val content = "TEST_ACTION"
        val signature = cryptoAuth.signMessage(content, timestamp)

        assertNotNull("Signature should be generated", signature)
        assertTrue("Signature should not be empty", signature!!.isNotEmpty())

        val bridgeMsg = BridgeMessage(
            type = "ACTION",
            action = ActionPayload(actionId = content, actionType = BridgeActionType.OPEN_APP),
            timestamp = timestamp,
            signature = signature
        )

        val isValid = cryptoAuth.verifyIncomingMessage(bridgeMsg)
        assertTrue("Incoming validly signed message must pass verification", isValid)
    }

    @Test
    fun testReplayAttackExpiredTimestampRejected() {
        val testToken = "secure-secret-token"
        cryptoAuth.establishSession(testToken, "ORB-1111", "Host")

        // Timestamp 5 minutes in the past (> 2 min replay window)
        val expiredTimestamp = System.currentTimeMillis() - 300_000L
        val content = "TEST_ACTION"
        val signature = cryptoAuth.signMessage(content, expiredTimestamp)

        val expiredMsg = BridgeMessage(
            type = "ACTION",
            action = ActionPayload(actionId = content, actionType = BridgeActionType.OPEN_APP),
            timestamp = expiredTimestamp,
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
            type = "ACTION",
            action = ActionPayload(actionId = "ORIGINAL_ACTION", actionType = BridgeActionType.OPEN_APP),
            timestamp = timestamp,
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
