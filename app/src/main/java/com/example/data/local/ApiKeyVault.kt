package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.keyVaultDataStore by preferencesDataStore(name = "allchat_key_vault")

/**
 * Encrypts user-provided API keys locally using Android Keystore AES-GCM
 * and persists the encrypted payload in device-only DataStore.
 */
class ApiKeyVault(private val context: Context) {

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "allchat_api_key_aes_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
        const val PREFIX_ENC = "enc_v1:"
        const val PREFIX_FALLBACK = "b64_v1:"
    }

    private fun prefKey(providerId: Long) = stringPreferencesKey("provider_key_$providerId")

    suspend fun saveApiKey(providerId: Long, rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            deleteApiKey(providerId)
            return
        }
        val encrypted = encrypt(trimmed)
        context.keyVaultDataStore.edit { prefs ->
            prefs[prefKey(providerId)] = encrypted
        }
    }

    suspend fun getApiKey(providerId: Long): String {
        val stored = context.keyVaultDataStore.data.first()[prefKey(providerId)] ?: return ""
        return decrypt(stored)
    }

    suspend fun deleteApiKey(providerId: Long) {
        context.keyVaultDataStore.edit { prefs ->
            prefs.remove(prefKey(providerId))
        }
    }

    fun observeConfiguredProviderIds(): Flow<Set<Long>> {
        return context.keyVaultDataStore.data.map { prefs ->
            prefs.asMap().keys.mapNotNull { key ->
                key.name.removePrefix("provider_key_").toLongOrNull()
            }.toSet()
        }
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }.getOrNull()
    }

    private fun encrypt(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        if (secretKey != null) {
            val encrypted = runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + cipherBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
                PREFIX_ENC + Base64.encodeToString(combined, Base64.NO_WRAP)
            }.getOrNull()
            if (encrypted != null) return encrypted
        }
        return PREFIX_FALLBACK + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        if (encoded.startsWith(PREFIX_ENC)) {
            val secretKey = getOrCreateSecretKey() ?: return ""
            return runCatching {
                val raw = Base64.decode(encoded.removePrefix(PREFIX_ENC), Base64.NO_WRAP)
                if (raw.size <= IV_SIZE_BYTES) return ""
                val iv = raw.copyOfRange(0, IV_SIZE_BYTES)
                val cipherBytes = raw.copyOfRange(IV_SIZE_BYTES, raw.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            }.getOrDefault("")
        }
        if (encoded.startsWith(PREFIX_FALLBACK)) {
            return runCatching {
                String(Base64.decode(encoded.removePrefix(PREFIX_FALLBACK), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault("")
        }
        return ""
    }
}
