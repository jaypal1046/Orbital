package com.orbital.security

import android.content.Context
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class TokenPayload(
    val provider: String,
    val apiKey: String,
    val selectedModel: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TokenLockType {
    DEVICE_LOCKED,
    PIN_PROTECTED
}

data class TokenPackageHeader(
    val version: Int,
    val format: String,
    val lockType: TokenLockType,
    val provider: String?,
    val createdAt: Long?
)

object OrbitalTokenCrypto {

    private const val FORMAT_IDENTIFIER = "ORBITAL_TOKEN_PACKAGE"
    private const val CURRENT_VERSION = 1
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH_BIT = 256

    /**
     * Inspects the token file header without decrypting to know if a PIN is required.
     */
    fun inspectTokenPackage(packageJson: String): Result<TokenPackageHeader> {
        return try {
            val root = JSONObject(packageJson)
            val format = root.optString("format", "")
            if (format != FORMAT_IDENTIFIER) {
                return Result.failure(IllegalArgumentException("Invalid token package format"))
            }

            val version = root.optInt("version", 1)
            val lockTypeStr = root.getString("lock_type")
            val lockType = TokenLockType.valueOf(lockTypeStr)
            val metadata = root.optJSONObject("metadata")
            val provider = metadata?.optString("provider")
            val createdAt = metadata?.optLong("created_at")

            Result.success(
                TokenPackageHeader(
                    version = version,
                    format = format,
                    lockType = lockType,
                    provider = provider,
                    createdAt = createdAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a token locked strictly to this physical device hardware ID.
     */
    fun exportDeviceLockedToken(context: Context, payload: TokenPayload): String {
        val salt = generateRandomBytes(SALT_LENGTH_BYTE)
        val secretKey = deriveDeviceKey(context, salt)
        return encryptPackage(secretKey, salt, TokenLockType.DEVICE_LOCKED, payload)
    }

    /**
     * Exports a token protected with a user-chosen PIN (can be decrypted on other devices).
     */
    fun exportPinProtectedToken(pin: String, payload: TokenPayload): String {
        require(pin.isNotBlank()) { "PIN cannot be empty" }
        val salt = generateRandomBytes(SALT_LENGTH_BYTE)
        val secretKey = derivePinKey(pin, salt)
        return encryptPackage(secretKey, salt, TokenLockType.PIN_PROTECTED, payload)
    }

    /**
     * Decrypts and imports a token package.
     */
    fun importTokenPackage(context: Context, packageJson: String, pin: String? = null): Result<TokenPayload> {
        return try {
            val root = JSONObject(packageJson)
            val format = root.optString("format", "")
            if (format != FORMAT_IDENTIFIER) {
                return Result.failure(IllegalArgumentException("Unrecognized file format. Expected an .orbtoken file."))
            }

            val lockTypeStr = root.getString("lock_type")
            val lockType = TokenLockType.valueOf(lockTypeStr)
            val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
            val payloadCipher = Base64.decode(root.getString("payload"), Base64.NO_WRAP)

            val secretKey = when (lockType) {
                TokenLockType.DEVICE_LOCKED -> deriveDeviceKey(context, salt)
                TokenLockType.PIN_PROTECTED -> {
                    if (pin.isNullOrBlank()) {
                        return Result.failure(IllegalStateException("PIN_REQUIRED"))
                    }
                    derivePinKey(pin, salt)
                }
            }

            val decryptedJsonString = try {
                decryptPayload(secretKey, iv, payloadCipher)
            } catch (e: Exception) {
                if (lockType == TokenLockType.DEVICE_LOCKED) {
                    return Result.failure(
                        SecurityException("This token file was encrypted for a different device. To use it on this phone, please re-export it with a PIN.")
                    )
                } else {
                    return Result.failure(
                        SecurityException("Incorrect PIN. Please try again.")
                    )
                }
            }

            val payloadObj = JSONObject(decryptedJsonString)
            val payload = TokenPayload(
                provider = payloadObj.getString("provider"),
                apiKey = payloadObj.getString("api_key"),
                selectedModel = payloadObj.optString("selected_model").takeIf { !it.isNullOrBlank() },
                createdAt = payloadObj.optLong("created_at", System.currentTimeMillis())
            )

            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encryptPackage(
        key: SecretKey,
        salt: ByteArray,
        lockType: TokenLockType,
        payload: TokenPayload
    ): String {
        val iv = generateRandomBytes(IV_LENGTH_BYTE)
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val payloadJson = JSONObject().apply {
            put("provider", payload.provider)
            put("api_key", payload.apiKey)
            put("selected_model", payload.selectedModel)
            put("created_at", payload.createdAt)
        }

        val cipherBytes = cipher.doFinal(payloadJson.toString().toByteArray(Charsets.UTF_8))

        val root = JSONObject().apply {
            put("version", CURRENT_VERSION)
            put("format", FORMAT_IDENTIFIER)
            put("lock_type", lockType.name)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("payload", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            put("metadata", JSONObject().apply {
                put("provider", payload.provider)
                put("created_at", payload.createdAt)
            })
        }

        return root.toString(2)
    }

    private fun decryptPayload(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun deriveDeviceKey(context: Context, salt: ByteArray): SecretKey {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "orbital_fallback_device_identifier_salt"

        return deriveKey(androidId.toCharArray(), salt)
    }

    private fun derivePinKey(pin: String, salt: ByteArray): SecretKey {
        return deriveKey(pin.toCharArray(), salt)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BIT)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}
