package com.orbital.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LiveDeviceSmokeRunnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var smokeRunner: LiveDeviceSmokeRunner

    @Before
    fun setUp() {
        smokeRunner = LiveDeviceSmokeRunner()
    }

    @Test
    fun `runSmokeSuite executes all 7 sanity checks and passes 100 percent`() {
        val outputDir = tempFolder.newFolder("smoke_output")
        val report = smokeRunner.runSmokeSuite(reportOutputDir = outputDir)

        assertNotNull(report)
        assertEquals(7, report.totalTests)
        assertEquals(7, report.passedCount)
        assertEquals(0, report.failedCount)

        val md = report.toMarkdownReport()
        assertTrue(md.contains("✅ 100% HEALTHY"))
        assertTrue(md.contains("SMOKE-01"))
        assertTrue(md.contains("SMOKE-07"))

        // Verify report file was written to output directory
        val reportFile = outputDir.resolve("orbital_smoke_report.md")
        assertTrue(reportFile.exists())
        assertTrue(reportFile.readText().contains("# Orbital Live On-Device Smoke Test Report"))
    }
}
