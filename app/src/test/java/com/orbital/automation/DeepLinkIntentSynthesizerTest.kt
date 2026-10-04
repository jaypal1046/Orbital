package com.orbital.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepLinkIntentSynthesizerTest {

    private lateinit var synthesizer: DeepLinkIntentSynthesizer

    @Before
    fun setUp() {
        synthesizer = DeepLinkIntentSynthesizer()
    }

    @Test
    fun `synthesizes direct web URLs`() {
        val result = synthesizer.synthesizeIntent("Open https://github.com in browser")
        assertTrue(result.isAccelerated)
        assertEquals("android.intent.action.VIEW", result.intentAction)
        assertEquals("https://github.com", result.intentDataUri)
        assertTrue(result.stepSavingsEstimate > 0)
    }

    @Test
    fun `synthesizes maps navigation intent`() {
        val result = synthesizer.synthesizeIntent("Directions to Eiffel Tower Paris")
        assertTrue(result.isAccelerated)
        assertEquals("android.intent.action.VIEW", result.intentAction)
        assertTrue(result.intentDataUri?.startsWith("geo:0,0?q=") == true)
        assertTrue(result.intentDataUri?.contains("eiffel", ignoreCase = true) == true)
    }

    @Test
    fun `synthesizes settings shortcuts`() {
        val wifi = synthesizer.synthesizeIntent("Open Wi-Fi settings")
        assertTrue(wifi.isAccelerated)
        assertEquals("android.settings.WIFI_SETTINGS", wifi.intentAction)

        val bluetooth = synthesizer.synthesizeIntent("Open bluetooth settings")
        assertTrue(bluetooth.isAccelerated)
        assertEquals("android.settings.BLUETOOTH_SETTINGS", bluetooth.intentAction)

        val battery = synthesizer.synthesizeIntent("Check battery saver settings")
        assertTrue(battery.isAccelerated)
        assertEquals("android.settings.BATTERY_SAVER_SETTINGS", battery.intentAction)
    }

    @Test
    fun `synthesizes phone call dialer intent`() {
        val result = synthesizer.synthesizeIntent("Call +1234567890")
        assertTrue(result.isAccelerated)
        assertEquals("android.intent.action.DIAL", result.intentAction)
        assertEquals("tel:+1234567890", result.intentDataUri)
    }

    @Test
    fun `synthesizes email composer intent`() {
        val result = synthesizer.synthesizeIntent("Send email to test@example.com")
        assertTrue(result.isAccelerated)
        assertEquals("android.intent.action.SENDTO", result.intentAction)
        assertEquals("mailto:test@example.com", result.intentDataUri)
    }

    @Test
    fun `returns non-accelerated for arbitrary UI tasks`() {
        val result = synthesizer.synthesizeIntent("Click on the checkout button in the shopping app")
        assertFalse(result.isAccelerated)
    }
}
