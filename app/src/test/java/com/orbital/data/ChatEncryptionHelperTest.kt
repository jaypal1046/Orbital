package com.orbital.data

import com.orbital.data.db.ChatEncryptionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatEncryptionHelperTest {

    private val encryptionHelper = ChatEncryptionHelper()

    @Test
    fun encryptAndDecrypt_returnsOriginalPlaintext() {
        val original = "Hello! Set a timer and open the requested app."
        val encrypted = encryptionHelper.encrypt(original)

        assertNotNull(encrypted)
        assertNotEquals(original, encrypted)

        val decrypted = encryptionHelper.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encryptAndDecrypt_handlesEmptyString() {
        val original = ""
        val encrypted = encryptionHelper.encrypt(original)
        assertEquals("", encrypted)

        val decrypted = encryptionHelper.decrypt(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun encryptAndDecrypt_handlesSpecialCharactersAndMarkdown() {
        val original = "```kotlin\nval secret = \"AES-256-GCM\"\n```\n🌟 Emoji test: 🚀 ⏱️ 🤖"
        val encrypted = encryptionHelper.encrypt(original)

        val decrypted = encryptionHelper.decrypt(encrypted)
        assertEquals(original, decrypted)
    }
}
