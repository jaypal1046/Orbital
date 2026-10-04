package com.orbital.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SemanticDeviceSearchEngineTest {

    private lateinit var searchEngine: SemanticDeviceSearchEngine

    @Before
    fun setUp() {
        searchEngine = SemanticDeviceSearchEngine()
        val mockIndex = listOf(
            SearchableItem(
                id = "app_browser",
                title = "Chrome",
                subtitle = "Google LLC",
                category = SearchCategory.APP,
                keywords = listOf("internet", "web", "browser", "search"),
                actionPayload = "com.android.chrome",
                iconHint = "🌐"
            ),
            SearchableItem(
                id = "app_calculator",
                title = "Calculator",
                subtitle = "System Tools",
                category = SearchCategory.APP,
                keywords = listOf("math", "sum", "calc", "convert"),
                actionPayload = "com.google.android.calculator",
                iconHint = "🧮"
            ),
            SearchableItem(
                id = "file_pdf_invoice",
                title = "Invoice_September_2026.pdf",
                subtitle = "/sdcard/Download/Invoice_September_2026.pdf",
                category = SearchCategory.FILE,
                keywords = listOf("receipt", "bill", "finance", "taxes"),
                actionPayload = "/sdcard/Download/Invoice_September_2026.pdf",
                iconHint = "📄"
            ),
            SearchableItem(
                id = "setting_bluetooth",
                title = "Bluetooth Settings",
                subtitle = "Connected devices & wireless",
                category = SearchCategory.SETTING,
                keywords = listOf("pair", "wireless", "headphones", "bt"),
                actionPayload = "android.settings.BLUETOOTH_SETTINGS",
                iconHint = "📶"
            ),
            SearchableItem(
                id = "action_flashlight",
                title = "Toggle Flashlight",
                subtitle = "Torch hardware toggle",
                category = SearchCategory.ACTION,
                keywords = listOf("torch", "light", "illumination"),
                actionPayload = "ACTION_FLASHLIGHT",
                iconHint = "🔦"
            )
        )
        searchEngine.setIndex(mockIndex)
    }

    @Test
    fun `exact and prefix queries return top relevant item`() {
        val exactResults = searchEngine.search("Chrome")
        assertTrue(exactResults.isNotEmpty())
        assertEquals("app_browser", exactResults.first().item.id)
        assertEquals(1.0, exactResults.first().relevanceScore, 0.01)

        val prefixResults = searchEngine.search("calc")
        assertTrue(prefixResults.isNotEmpty())
        assertEquals("app_calculator", prefixResults.first().item.id)
    }

    @Test
    fun `keyword queries match semantic terms`() {
        val results = searchEngine.search("torch")
        assertTrue(results.isNotEmpty())
        assertEquals("action_flashlight", results.first().item.id)
        assertTrue(results.first().matchReason.contains("Keyword"))
    }

    @Test
    fun `category filter restricts results`() {
        val fileOnly = searchEngine.search("invoice", categoryFilter = SearchCategory.FILE)
        assertEquals(1, fileOnly.size)
        assertEquals(SearchCategory.FILE, fileOnly.first().item.category)

        val appOnly = searchEngine.search("invoice", categoryFilter = SearchCategory.APP)
        assertTrue(appOnly.isEmpty())
    }

    @Test
    fun `fuzzy typos find closest match`() {
        val typoResults = searchEngine.search("chromee")
        assertTrue(typoResults.isNotEmpty())
        assertEquals("app_browser", typoResults.first().item.id)
    }
}
