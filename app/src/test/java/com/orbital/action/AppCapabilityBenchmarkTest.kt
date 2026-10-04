package com.orbital.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class AppCapabilityBenchmarkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockCapabilityManager: AppCapabilityManager
    private lateinit var benchmark: AppCapabilityBenchmark

    @Before
    fun setUp() {
        mockCapabilityManager = mock(AppCapabilityManager::class.java)
        val mockApps = listOf(
            DynamicAppInfo(
                name = "Chrome",
                packageName = "com.android.chrome",
                category = AppCategory.BROWSER,
                capabilities = listOf("SEARCH_WEB", "OPEN_URL"),
                actionHint = "SEARCH_WEB"
            ),
            DynamicAppInfo(
                name = "Google Maps",
                packageName = "com.google.android.apps.maps",
                category = AppCategory.NAVIGATION,
                capabilities = listOf("NAVIGATE"),
                actionHint = "NAVIGATE"
            ),
            DynamicAppInfo(
                name = "WhatsApp",
                packageName = "com.whatsapp",
                category = AppCategory.MESSAGING,
                capabilities = listOf("SEND_SMS"),
                actionHint = "SEND_SMS"
            )
        )
        `when`(mockCapabilityManager.getInstalledApps(forceRefresh = true)).thenReturn(mockApps)
        benchmark = AppCapabilityBenchmark(mockCapabilityManager)
    }

    @Test
    fun `runBenchmark indexes categories and writes capability report`() {
        val outDir = tempFolder.newFolder("benchmark_out")
        val summary = benchmark.runBenchmark(outputDir = outDir)

        assertNotNull(summary)
        assertEquals(3, summary.totalApps)
        assertEquals(3, summary.categoryCounts.size)
        assertEquals(1, summary.categoryCounts[AppCategory.BROWSER])
        assertEquals(1, summary.categoryCounts[AppCategory.NAVIGATION])
        assertEquals(1, summary.categoryCounts[AppCategory.MESSAGING])

        val md = summary.toMarkdownReport()
        assertTrue(md.contains("Orbital On-Device App Capability Matrix & Benchmark"))
        assertTrue(md.contains("Chrome"))
        assertTrue(md.contains("Google Maps"))
        assertTrue(md.contains("WhatsApp"))

        val file = outDir.resolve("orbital_capabilities_matrix.md")
        assertTrue(file.exists())
        assertTrue(file.readText().contains("Detailed App Capability Index"))
    }
}
