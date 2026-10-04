package com.orbital.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TransactionalRollbackHarnessTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var harness: TransactionalRollbackHarness

    @Before
    fun setUp() {
        harness = TransactionalRollbackHarness()
    }

    @Test
    fun `beginTransaction captures initial checkpoint and tracks actions`() {
        val session = harness.beginTransaction("tx_101", currentForegroundPackage = "com.android.settings")
        assertNotNull(session)
        assertEquals("tx_101", session.transactionId)
        assertEquals("com.android.settings", session.initialCheckpoint.initialPackageName)

        harness.recordAction("TAP_ELEMENT", mapOf("target" to "Save"))
        assertEquals(1, session.journal.size)
        assertEquals("TAP_ELEMENT", session.journal.first().actionName)
    }

    @Test
    fun `rollback restores modified files and triggers recovery action`() {
        val testFile = tempFolder.newFile("notes.txt")
        testFile.writeText("Original safe text")

        val session = harness.beginTransaction("tx_102", currentForegroundPackage = "com.example.editor")
        harness.recordFileBackup(testFile.absolutePath, "Original safe text")

        // Simulate destructive file overwrite during agent execution
        testFile.writeText("Corrupted or wrong content written by agent")

        var executedRecoveryAction: String? = null
        val success = harness.rollback { recovery ->
            executedRecoveryAction = recovery
        }

        assertTrue(success)
        assertTrue(session.isRolledBack)
        assertNull(harness.getActiveSession())

        // Verify file is safely restored to original content
        assertEquals("Original safe text", testFile.readText())
        assertNotNull(executedRecoveryAction)
    }

    @Test
    fun `commit prevents rollback`() {
        val session = harness.beginTransaction("tx_103")
        assertTrue(harness.commit())
        assertTrue(session.isCommitted)

        val rollbackResult = harness.rollback()
        assertFalse(rollbackResult)
    }
}
