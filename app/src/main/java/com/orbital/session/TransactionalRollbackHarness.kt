package com.orbital.session

import com.orbital.file.UniversalFileEngine
import com.orbital.foreman.DeviceStateCheckpoint
import com.orbital.foreman.DeviceStateCheckpointManager
import java.io.File

data class TransactionJournalEntry(
    val actionName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: Map<String, String> = emptyMap()
)

data class TransactionSession(
    val transactionId: String,
    val initialCheckpoint: DeviceStateCheckpoint,
    val fileBackups: MutableMap<String, String> = mutableMapOf(),
    val journal: MutableList<TransactionJournalEntry> = mutableListOf(),
    var isCommitted: Boolean = false,
    var isRolledBack: Boolean = false
)

class TransactionalRollbackHarness(
    private val checkpointManager: DeviceStateCheckpointManager = DeviceStateCheckpointManager(
        pressBackAction = { true },
        goHomeAction = { true }
    )
) {
    private var activeSession: TransactionSession? = null

    fun beginTransaction(transactionId: String, currentForegroundPackage: String? = null): TransactionSession {
        val checkpoint = checkpointManager.captureCheckpoint(currentForegroundPackage)
        val session = TransactionSession(
            transactionId = transactionId,
            initialCheckpoint = checkpoint
        )
        activeSession = session
        return session
    }

    fun recordFileBackup(filePath: String, contentBeforeEdit: String) {
        val session = activeSession ?: return
        if (!session.fileBackups.containsKey(filePath)) {
            session.fileBackups[filePath] = contentBeforeEdit
        }
    }

    fun recordAction(actionName: String, payload: Map<String, String> = emptyMap()) {
        activeSession?.journal?.add(TransactionJournalEntry(actionName = actionName, payload = payload))
    }

    fun commit(): Boolean {
        val session = activeSession ?: return false
        session.isCommitted = true
        activeSession = null
        return true
    }

    fun rollback(onPerformRecoveryAction: ((String) -> Unit)? = null): Boolean {
        val session = activeSession ?: return false
        if (session.isCommitted) return false

        // 1. Restore modified files
        for ((filePath, originalContent) in session.fileBackups) {
            UniversalFileEngine.writeTextFile(File(filePath), originalContent, overwrite = true)
        }

        // 2. Perform state rollback
        checkpointManager.rollbackToInitialState()
        onPerformRecoveryAction?.invoke("DEVICE_ROLLBACK")

        session.isRolledBack = true
        activeSession = null
        return true
    }

    fun getActiveSession(): TransactionSession? = activeSession
}
