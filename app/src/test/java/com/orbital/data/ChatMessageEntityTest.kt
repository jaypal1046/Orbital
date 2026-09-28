package com.orbital.data

import com.orbital.data.db.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatMessageEntityTest {

    @Test
    fun createEntity_setsPropertiesCorrectly() {
        val now = System.currentTimeMillis()
        val entity = ChatMessageEntity(
            id = "msg_12345",
            role = "assistant",
            encryptedContent = "enc_12345",
            timestamp = now,
            providerName = "gemini",
            actionLabel = "⚡ Executed: Opened YouTube",
            actionDetails = "package: com.google.android.youtube"
        )

        assertEquals("msg_12345", entity.id)
        assertEquals("assistant", entity.role)
        assertEquals("enc_12345", entity.encryptedContent)
        assertEquals(now, entity.timestamp)
        assertEquals("gemini", entity.providerName)
        assertEquals("⚡ Executed: Opened YouTube", entity.actionLabel)
        assertEquals("package: com.google.android.youtube", entity.actionDetails)
    }
}
