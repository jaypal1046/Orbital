package com.orbital.security

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OrbitalTokenCryptoTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testPinProtectedExportAndImport_success() {
        val payload = TokenPayload(
            provider = "GEMINI",
            apiKey = "AIzaSy_test_secret_api_key_12345",
            selectedModel = "gemini-3.6-flash"
        )
        val pin = "4321"

        val exportedJson = OrbitalTokenCrypto.exportPinProtectedToken(pin, payload)
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("ORBITAL_TOKEN_PACKAGE"))
        assertTrue(exportedJson.contains("PIN_PROTECTED"))

        // Inspect header
        val headerResult = OrbitalTokenCrypto.inspectTokenPackage(exportedJson)
        assertTrue(headerResult.isSuccess)
        val header = headerResult.getOrThrow()
        assertEquals(TokenLockType.PIN_PROTECTED, header.lockType)
        assertEquals("GEMINI", header.provider)

        // Decrypt with correct PIN
        val importResult = OrbitalTokenCrypto.importTokenPackage(context, exportedJson, pin)
        assertTrue(importResult.isSuccess)
        val importedPayload = importResult.getOrThrow()
        assertEquals(payload.provider, importedPayload.provider)
        assertEquals(payload.apiKey, importedPayload.apiKey)
        assertEquals(payload.selectedModel, importedPayload.selectedModel)
    }

    @Test
    fun testPinProtectedImport_wrongPinFails() {
        val payload = TokenPayload(
            provider = "GROQ",
            apiKey = "gsk_test_groq_api_token_xyz"
        )
        val exportedJson = OrbitalTokenCrypto.exportPinProtectedToken("9999", payload)

        val importResult = OrbitalTokenCrypto.importTokenPackage(context, exportedJson, "1111")
        assertTrue(importResult.isFailure)
        val errorMsg = importResult.exceptionOrNull()?.message
        assertTrue(errorMsg?.contains("Incorrect PIN") == true || errorMsg?.contains("PIN") == true)
    }

    @Test
    fun testDeviceLockedExportAndImport_successOnSameDevice() {
        val payload = TokenPayload(
            provider = "OPENROUTER",
            apiKey = "sk-or-v1-test-openrouter-key-abc",
            selectedModel = "deepseek/deepseek-r1"
        )

        val exportedJson = OrbitalTokenCrypto.exportDeviceLockedToken(context, payload)
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("DEVICE_LOCKED"))

        val importResult = OrbitalTokenCrypto.importTokenPackage(context, exportedJson, null)
        assertTrue(importResult.isSuccess)
        val importedPayload = importResult.getOrThrow()
        assertEquals(payload.provider, importedPayload.provider)
        assertEquals(payload.apiKey, importedPayload.apiKey)
        assertEquals(payload.selectedModel, importedPayload.selectedModel)
    }

    @Test
    fun testCorruptPayloadRejection() {
        val invalidJson = """{ "format": "INVALID_FORMAT", "lock_type": "DEVICE_LOCKED" }"""
        val headerResult = OrbitalTokenCrypto.inspectTokenPackage(invalidJson)
        assertTrue(headerResult.isFailure)

        val importResult = OrbitalTokenCrypto.importTokenPackage(context, invalidJson, null)
        assertTrue(importResult.isFailure)
    }
}
