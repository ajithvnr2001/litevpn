package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ApiKeySource {
    CUSTOM_USER_KEY,
    BUILD_SECRET,
    NONE
}

data class ActiveApiKeyInfo(
    val key: String?,
    val source: ApiKeySource,
    val maskedPreview: String?
)

/**
 * Hardware/AndroidKeyStore-backed AES-256-GCM encryption for storing user-swappable Nord API keys
 * safely at rest with zero third-party dependencies.
 */
class SecureKeyStorage(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveCustomKey(rawKey: String): Boolean {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) return false
        return runCatching {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(cleaned.toByteArray(Charsets.UTF_8))

            prefs.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
            true
        }.getOrDefault(false)
    }

    fun getCustomKey(): String? {
        val cipherB64 = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return runCatching {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val encrypted = Base64.decode(cipherB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8).trim().ifEmpty { null }
        }.getOrNull()
    }

    fun hasCustomKey(): Boolean =
        prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)

    fun clearCustomKey() {
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .apply()
    }

    fun getActiveKeyInfo(): ActiveApiKeyInfo {
        val custom = getCustomKey()
        if (!custom.isNullOrBlank()) {
            return ActiveApiKeyInfo(
                key = custom,
                source = ApiKeySource.CUSTOM_USER_KEY,
                maskedPreview = maskKey(custom)
            )
        }
        val buildKey = BuildConfig.NORD_API_KEY.trim()
        if (buildKey.isNotEmpty() && buildKey != PLACEHOLDER_KEY) {
            return ActiveApiKeyInfo(
                key = buildKey,
                source = ApiKeySource.BUILD_SECRET,
                maskedPreview = maskKey(buildKey)
            )
        }
        return ActiveApiKeyInfo(
            key = null,
            source = ApiKeySource.NONE,
            maskedPreview = null
        )
    }

    private fun maskKey(key: String): String {
        return if (key.length <= 8) {
            "••••••••"
        } else {
            "${key.take(4)}••••${key.takeLast(4)}"
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "swifttunnel_nord_api_key_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "swifttunnel_secure_keys"
        private const val KEY_CIPHERTEXT = "nord_key_ct"
        private const val KEY_IV = "nord_key_iv"
        private const val PLACEHOLDER_KEY = "YOUR_NORD_API_KEY"
    }
}
