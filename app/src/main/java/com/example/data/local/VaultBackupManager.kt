package com.example.data.local

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultSnapshot(
    val providers: List<ProviderEntity>,
    val apiKeysByProviderId: Map<Long, String>,
    val models: List<ModelEntity>,
    val personas: List<PersonaEntity>,
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>
)

/**
 * Encrypts and decrypts portable password-protected `.allchat` vault JSON archives
 * using PBKDF2-HMAC-SHA256 (100k iterations) + AES-256-GCM.
 */
object VaultBackupManager {

    private const val FORMAT_VERSION = 1
    private const val PBKDF2_ITERATIONS = 100_000
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encryptToAllChatJson(snapshot: VaultSnapshot, password: String): String {
        val cleanPwd = password.trim()
        require(cleanPwd.length >= 4) { "Backup password must be at least 4 characters." }

        val plainPayload = serializeSnapshot(snapshot).toString()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(cleanPwd, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainPayload.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject()
            .put("format", "allchat_encrypted_vault")
            .put("version", FORMAT_VERSION)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", PBKDF2_ITERATIONS)
            .put("cipher", "AES-256-GCM")
            .put("exportedAt", System.currentTimeMillis())
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        return envelope.toString(2)
    }

    fun decryptFromAllChatJson(rawJson: String, password: String): VaultSnapshot {
        val envelope = JSONObject(rawJson)
        require(envelope.optString("format") == "allchat_encrypted_vault") {
            "File is not a valid AllChat encrypted vault (.allchat)."
        }
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        val iterations = envelope.optInt("iterations", PBKDF2_ITERATIONS)

        val key = deriveKey(password.trim(), salt, iterations)
        val plainBytes = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (t: Throwable) {
            throw IllegalArgumentException("Incorrect backup password or corrupted vault file.")
        }
        val payload = JSONObject(String(plainBytes, Charsets.UTF_8))
        return deserializeSnapshot(payload)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun serializeSnapshot(s: VaultSnapshot): JSONObject {
        val providersArr = JSONArray()
        s.providers.forEach { p ->
            providersArr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("type", p.type)
                    .put("baseUrl", p.baseUrl)
                    .put("proxyUrl", p.proxyUrl ?: JSONObject.NULL)
                    .put("apiKey", s.apiKeysByProviderId[p.id] ?: "")
                    .put("createdAt", p.createdAt)
            )
        }

        val modelsArr = JSONArray()
        s.models.forEach { m ->
            modelsArr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("providerId", m.providerId)
                    .put("modelId", m.modelId)
                    .put("displayName", m.displayName)
                    .put("supportsTextChat", m.supportsTextChat)
                    .put("supportsImageInput", m.supportsImageInput)
                    .put("supportsImageGen", m.supportsImageGen)
                    .put("supportsVideoGen", m.supportsVideoGen)
                    .put("supportsSummarization", m.supportsSummarization)
                    .put("inputPricePerMillion", m.inputPricePerMillion)
                    .put("outputPricePerMillion", m.outputPricePerMillion)
            )
        }

        val personasArr = JSONArray()
        s.personas.forEach { p ->
            personasArr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("handle", p.handle)
                    .put("name", p.name)
                    .put("systemPrompt", p.systemPrompt)
                    .put("temperature", p.temperature.toDouble())
                    .put("maxTokens", p.maxTokens)
                    .put("defaultModelDbId", p.defaultModelDbId ?: JSONObject.NULL)
                    .put("createdAt", p.createdAt)
            )
        }

        val convsArr = JSONArray()
        s.conversations.forEach { c ->
            convsArr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("title", c.title)
                    .put("selectedModelDbId", c.selectedModelDbId ?: JSONObject.NULL)
                    .put("activePersonaId", c.activePersonaId ?: JSONObject.NULL)
                    .put("totalPromptTokens", c.totalPromptTokens)
                    .put("totalCompletionTokens", c.totalCompletionTokens)
                    .put("estimatedCostUsd", c.estimatedCostUsd)
                    .put("createdAt", c.createdAt)
                    .put("updatedAt", c.updatedAt)
            )
        }

        val msgsArr = JSONArray()
        s.messages.forEach { m ->
            msgsArr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("conversationId", m.conversationId)
                    .put("parentMessageId", m.parentMessageId ?: JSONObject.NULL)
                    .put("role", m.role)
                    .put("content", m.content)
                    .put("attachmentUri", m.attachmentUri ?: JSONObject.NULL)
                    .put("promptTokens", m.promptTokens ?: JSONObject.NULL)
                    .put("completionTokens", m.completionTokens ?: JSONObject.NULL)
                    .put("estimatedCostUsd", m.estimatedCostUsd)
                    .put("modelLabel", m.modelLabel ?: JSONObject.NULL)
                    .put("isError", m.isError)
                    .put("suggestProxySetting", m.suggestProxySetting)
                    .put("providerIdForError", m.providerIdForError ?: JSONObject.NULL)
                    .put("createdAt", m.createdAt)
            )
        }

        return JSONObject()
            .put("providers", providersArr)
            .put("models", modelsArr)
            .put("personas", personasArr)
            .put("conversations", convsArr)
            .put("messages", msgsArr)
    }

    private fun deserializeSnapshot(root: JSONObject): VaultSnapshot {
        val providers = mutableListOf<ProviderEntity>()
        val apiKeys = mutableMapOf<Long, String>()
        val pArr = root.optJSONArray("providers") ?: JSONArray()
        for (i in 0 until pArr.length()) {
            val o = pArr.getJSONObject(i)
            val id = o.getLong("id")
            providers += ProviderEntity(
                id = id,
                name = o.getString("name"),
                type = o.getString("type"),
                baseUrl = o.getString("baseUrl"),
                proxyUrl = o.optString("proxyUrl").takeIf { !o.isNull("proxyUrl") && it.isNotBlank() },
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
            val key = o.optString("apiKey", "")
            if (key.isNotBlank()) apiKeys[id] = key
        }

        val models = mutableListOf<ModelEntity>()
        val mArr = root.optJSONArray("models") ?: JSONArray()
        for (i in 0 until mArr.length()) {
            val o = mArr.getJSONObject(i)
            models += ModelEntity(
                id = o.getLong("id"),
                providerId = o.getLong("providerId"),
                modelId = o.getString("modelId"),
                displayName = o.getString("displayName"),
                supportsTextChat = o.optBoolean("supportsTextChat", true),
                supportsImageInput = o.optBoolean("supportsImageInput", false),
                supportsImageGen = o.optBoolean("supportsImageGen", false),
                supportsVideoGen = o.optBoolean("supportsVideoGen", false),
                supportsSummarization = o.optBoolean("supportsSummarization", false),
                inputPricePerMillion = o.optDouble("inputPricePerMillion", 0.0),
                outputPricePerMillion = o.optDouble("outputPricePerMillion", 0.0)
            )
        }

        val personas = mutableListOf<PersonaEntity>()
        val perArr = root.optJSONArray("personas") ?: JSONArray()
        for (i in 0 until perArr.length()) {
            val o = perArr.getJSONObject(i)
            personas += PersonaEntity(
                id = o.getLong("id"),
                handle = o.getString("handle"),
                name = o.getString("name"),
                systemPrompt = o.getString("systemPrompt"),
                temperature = o.optDouble("temperature", 0.7).toFloat(),
                maxTokens = o.optInt("maxTokens", 2048),
                defaultModelDbId = if (o.isNull("defaultModelDbId")) null else o.optLong("defaultModelDbId"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }

        val conversations = mutableListOf<ConversationEntity>()
        val cArr = root.optJSONArray("conversations") ?: JSONArray()
        for (i in 0 until cArr.length()) {
            val o = cArr.getJSONObject(i)
            conversations += ConversationEntity(
                id = o.getLong("id"),
                title = o.getString("title"),
                selectedModelDbId = if (o.isNull("selectedModelDbId")) null else o.optLong("selectedModelDbId"),
                activePersonaId = if (o.isNull("activePersonaId")) null else o.optLong("activePersonaId"),
                totalPromptTokens = o.optInt("totalPromptTokens", 0),
                totalCompletionTokens = o.optInt("totalCompletionTokens", 0),
                estimatedCostUsd = o.optDouble("estimatedCostUsd", 0.0),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        val messages = mutableListOf<MessageEntity>()
        val msgArr = root.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until msgArr.length()) {
            val o = msgArr.getJSONObject(i)
            messages += MessageEntity(
                id = o.getLong("id"),
                conversationId = o.getLong("conversationId"),
                parentMessageId = if (o.isNull("parentMessageId")) null else o.optLong("parentMessageId"),
                role = o.getString("role"),
                content = o.optString("content", ""),
                attachmentUri = o.optString("attachmentUri").takeIf { !o.isNull("attachmentUri") && it.isNotBlank() },
                promptTokens = if (o.isNull("promptTokens")) null else o.optInt("promptTokens"),
                completionTokens = if (o.isNull("completionTokens")) null else o.optInt("completionTokens"),
                estimatedCostUsd = o.optDouble("estimatedCostUsd", 0.0),
                modelLabel = o.optString("modelLabel").takeIf { !o.isNull("modelLabel") && it.isNotBlank() },
                isError = o.optBoolean("isError", false),
                suggestProxySetting = o.optBoolean("suggestProxySetting", false),
                providerIdForError = if (o.isNull("providerIdForError")) null else o.optLong("providerIdForError"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }

        return VaultSnapshot(
            providers = providers,
            apiKeysByProviderId = apiKeys,
            models = models,
            personas = personas,
            conversations = conversations,
            messages = messages
        )
    }
}
