package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AutoRouterConfig
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.CustomSlashCommand
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.McpServerConfig
import com.example.domain.model.QuickContact
import com.example.domain.model.WebSearchProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val Context.keyVaultDataStore by preferencesDataStore(name = "allchat_key_vault")

/**
 * Encrypts user-provided API keys locally using Android Keystore AES-256-GCM
 * (with optional user PIN / Master Passphrase PBKDF2-HMAC-SHA256 second-layer key derivation
 * held exclusively in memory during an unlocked session).
 */
class ApiKeyVault(private val context: Context) {

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "allchat_api_key_aes_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
        const val PBKDF2_ITERATIONS = 100_000
        const val PREFIX_ENC = "enc_v1:"
        const val PREFIX_PIN_ENC = "pin_v2:"
        const val PREFIX_FALLBACK = "b64_v1:"

        val KEY_PIN_SALT = stringPreferencesKey("vault_pin_salt")
        val KEY_PIN_VERIFIER = stringPreferencesKey("vault_pin_verifier")
        val KEY_DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        val KEY_THEME_PRESET = stringPreferencesKey("theme_preset_id")
        val KEY_SHOW_DETAILS = booleanPreferencesKey("ui_show_details")
        val KEY_SHOW_THINKING = booleanPreferencesKey("ui_show_thinking")
        val KEY_AGENTS_MD = stringPreferencesKey("workspace_agents_md")
        val KEY_MCP_SERVERS = stringPreferencesKey("mcp_servers_json")
        val KEY_SHARED_SESSIONS = stringPreferencesKey("shared_sessions_json")
        val KEY_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val KEY_SEARCH_API_KEY = stringPreferencesKey("web_search_api_key_enc")
        val KEY_CUSTOM_SLASH_COMMANDS = stringPreferencesKey("custom_slash_commands_json")
        val KEY_AUTO_ROUTER_CONFIG = stringPreferencesKey("auto_router_config_json")
        val KEY_AUTO_ROUTE_ENABLED = booleanPreferencesKey("auto_route_enabled")
        val KEY_AUTO_EXTRACT_MEMORIES = booleanPreferencesKey("auto_extract_memories")
        val KEY_ACTIVE_CONVERSATION_ID = longPreferencesKey("active_conversation_id")
        val KEY_ACTIVE_MODEL_ID = longPreferencesKey("active_model_db_id")
        val KEY_QUICK_CONTACTS = stringPreferencesKey("quick_contacts_json")
        val KEY_CUSTOM_APPS = stringPreferencesKey("custom_apps_json")
        val KEY_VISION_FALLBACK_IDS = stringPreferencesKey("vision_fallback_model_ids_json")
        val KEY_LENS_INTERVAL_SEC = intPreferencesKey("live_lens_interval_sec")
    }

    private val sessionPinKey = MutableStateFlow<SecretKey?>(null)
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    val isPinEnabledFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { prefs ->
        !prefs[KEY_PIN_VERIFIER].isNullOrBlank()
    }

    val displayCurrencyFlow: Flow<DisplayCurrency> = context.keyVaultDataStore.data.map { prefs ->
        runCatching { DisplayCurrency.valueOf(prefs[KEY_DISPLAY_CURRENCY] ?: "USD") }
            .getOrDefault(DisplayCurrency.USD)
    }

    val themePresetIdFlow: Flow<String> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_THEME_PRESET] ?: "swiss_signal"
    }

    val showDetailsFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_SHOW_DETAILS] ?: true
    }

    val showThinkingFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_SHOW_THINKING] ?: true
    }

    val agentsMdFlow: Flow<String> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_AGENTS_MD].orEmpty()
    }

    val mcpServersFlow: Flow<List<McpServerConfig>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_MCP_SERVERS].orEmpty()
        if (raw.isBlank()) return@map emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        McpServerConfig(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            url = o.optString("url"),
                            authToken = o.optString("authToken", ""),
                            enabled = o.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    val sharedSessionsFlow: Flow<Map<Long, String>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_SHARED_SESSIONS].orEmpty()
        if (raw.isBlank()) return@map emptyMap()
        runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val cid = k.toLongOrNull() ?: continue
                    val code = obj.optString(k)
                    if (code.isNotBlank()) put(cid, code)
                }
            }
        }.getOrDefault(emptyMap())
    }

    val searchProviderFlow: Flow<WebSearchProvider> = context.keyVaultDataStore.data.map { prefs ->
        runCatching { WebSearchProvider.valueOf(prefs[KEY_SEARCH_PROVIDER] ?: "GEMINI_NATIVE") }
            .getOrDefault(WebSearchProvider.GEMINI_NATIVE)
    }

    val hasSearchApiKeyFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { prefs ->
        !prefs[KEY_SEARCH_API_KEY].isNullOrBlank()
    }

    val autoRouteEnabledFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_AUTO_ROUTE_ENABLED] ?: false
    }

    val autoExtractMemoriesFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_AUTO_EXTRACT_MEMORIES] ?: false
    }

    val activeConversationIdFlow: Flow<Long?> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_CONVERSATION_ID]?.takeIf { it > 0L }
    }

    suspend fun setActiveConversationId(conversationId: Long?) {
        context.keyVaultDataStore.edit { prefs ->
            if (conversationId == null || conversationId <= 0L) {
                prefs.remove(KEY_ACTIVE_CONVERSATION_ID)
            } else {
                prefs[KEY_ACTIVE_CONVERSATION_ID] = conversationId
            }
        }
    }

    val activeModelDbIdFlow: Flow<Long?> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_MODEL_ID]?.takeIf { it > 0L }
    }

    suspend fun setActiveModelDbId(modelDbId: Long?) {
        context.keyVaultDataStore.edit { prefs ->
            if (modelDbId == null || modelDbId <= 0L) {
                prefs.remove(KEY_ACTIVE_MODEL_ID)
            } else {
                prefs[KEY_ACTIVE_MODEL_ID] = modelDbId
            }
        }
    }

    val quickContactsFlow: Flow<List<QuickContact>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_QUICK_CONTACTS].orEmpty()
        if (raw.isBlank()) return@map emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val name = o.optString("name")
                    val phone = o.optString("phoneNumber")
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        add(QuickContact(id = id.ifBlank { "c_$i" }, name = name, phoneNumber = phone))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveQuickContact(contact: QuickContact) {
        val current = quickContactsFlow.first().toMutableList()
        val id = contact.id.ifBlank { "qc_${System.currentTimeMillis()}" }
        val normalized = contact.copy(id = id, name = contact.name.trim(), phoneNumber = contact.phoneNumber.trim())
        val idx = current.indexOfFirst { it.id == id || it.name.equals(normalized.name, ignoreCase = true) }
        if (idx >= 0) current[idx] = normalized else current.add(0, normalized)
        val arr = JSONArray()
        current.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("name", c.name).put("phoneNumber", c.phoneNumber))
        }
        context.keyVaultDataStore.edit { it[KEY_QUICK_CONTACTS] = arr.toString() }
    }

    suspend fun deleteQuickContact(id: String) {
        val updated = quickContactsFlow.first().filterNot { it.id == id }
        val arr = JSONArray()
        updated.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("name", c.name).put("phoneNumber", c.phoneNumber))
        }
        context.keyVaultDataStore.edit { it[KEY_QUICK_CONTACTS] = arr.toString() }
    }

    val customAppsFlow: Flow<List<CustomAppEntry>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_APPS].orEmpty()
        if (raw.isBlank()) return@map emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val name = o.optString("name")
                    val pkg = o.optString("packageName")
                    if (name.isNotBlank() && pkg.isNotBlank()) {
                        add(CustomAppEntry(id = id.ifBlank { "app_$i" }, name = name, packageName = pkg))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveCustomApp(entry: CustomAppEntry) {
        val current = customAppsFlow.first().toMutableList()
        val id = entry.id.ifBlank { "app_${System.currentTimeMillis()}" }
        val clean = entry.copy(id = id, name = entry.name.trim(), packageName = entry.packageName.trim())
        val idx = current.indexOfFirst { it.id == id || it.packageName.equals(clean.packageName, ignoreCase = true) }
        if (idx >= 0) current[idx] = clean else current.add(0, clean)
        val arr = JSONArray()
        current.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("name", a.name).put("packageName", a.packageName))
        }
        context.keyVaultDataStore.edit { it[KEY_CUSTOM_APPS] = arr.toString() }
    }

    suspend fun deleteCustomApp(id: String) {
        val updated = customAppsFlow.first().filterNot { it.id == id }
        val arr = JSONArray()
        updated.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("name", a.name).put("packageName", a.packageName))
        }
        context.keyVaultDataStore.edit { it[KEY_CUSTOM_APPS] = arr.toString() }
    }

    val visionFallbackModelIdsFlow: Flow<List<Long>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_VISION_FALLBACK_IDS].orEmpty()
        if (raw.isBlank()) return@map emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optLong(i, -1L)
                    if (v > 0L) add(v)
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun setVisionFallbackModelIds(ids: List<Long>) {
        val arr = JSONArray()
        ids.distinct().forEach { arr.put(it) }
        context.keyVaultDataStore.edit { it[KEY_VISION_FALLBACK_IDS] = arr.toString() }
    }

    val liveLensIntervalSecFlow: Flow<Int> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_LENS_INTERVAL_SEC] ?: 0
    }

    suspend fun setLiveLensIntervalSec(seconds: Int) {
        context.keyVaultDataStore.edit { it[KEY_LENS_INTERVAL_SEC] = seconds.coerceIn(0, 60) }
    }

    val autoRouterConfigFlow: Flow<AutoRouterConfig> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_AUTO_ROUTER_CONFIG].orEmpty()
        if (raw.isBlank()) return@map AutoRouterConfig()
        runCatching {
            val o = JSONObject(raw)
            AutoRouterConfig(
                fastChatModelId = o.optLong("fast", -1L).takeIf { it > 0L },
                heavyCodingModelId = o.optLong("coding", -1L).takeIf { it > 0L },
                visionModelId = o.optLong("vision", -1L).takeIf { it > 0L },
                longContextModelId = o.optLong("long", -1L).takeIf { it > 0L }
            )
        }.getOrDefault(AutoRouterConfig())
    }

    val customSlashCommandsFlow: Flow<List<CustomSlashCommand>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_SLASH_COMMANDS]
        if (raw == null) return@map defaultCustomSlashCommands()
        if (raw.isBlank()) return@map emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        CustomSlashCommand(
                            id = o.optString("id"),
                            trigger = o.optString("trigger"),
                            description = o.optString("description"),
                            template = o.optString("template")
                        )
                    )
                }
            }
        }.getOrDefault(defaultCustomSlashCommands())
    }

    private fun defaultCustomSlashCommands(): List<CustomSlashCommand> = listOf(
        CustomSlashCommand(
            id = "builtin_fix",
            trigger = "/fix",
            description = "Fix grammar and make text concise",
            template = "Fix grammar, clarity, and make concise:"
        ),
        CustomSlashCommand(
            id = "builtin_explain",
            trigger = "/explain",
            description = "Explain step-by-step with an analogy",
            template = "Explain step-by-step with a clear real-world analogy:"
        ),
        CustomSlashCommand(
            id = "builtin_json",
            trigger = "/json",
            description = "Convert input into strict JSON",
            template = "Convert the following into strict, valid JSON with clean keys and no extra prose:"
        )
    )

    suspend fun initializeLockState() {
        val prefs = context.keyVaultDataStore.data.first()
        val pinSet = !prefs[KEY_PIN_VERIFIER].isNullOrBlank()
        _isLocked.value = pinSet && sessionPinKey.value == null
    }

    suspend fun setDisplayCurrency(currency: DisplayCurrency) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_DISPLAY_CURRENCY] = currency.name
        }
    }

    suspend fun setThemePresetId(presetId: String) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_THEME_PRESET] = presetId
        }
    }

    suspend fun setShowDetails(show: Boolean) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_SHOW_DETAILS] = show
        }
    }

    suspend fun setShowThinking(show: Boolean) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_SHOW_THINKING] = show
        }
    }

    suspend fun setAgentsMd(content: String) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_AGENTS_MD] = content.trim()
        }
    }

    suspend fun saveMcpServers(servers: List<McpServerConfig>) {
        val arr = JSONArray()
        servers.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("url", s.url)
                    .put("authToken", s.authToken)
                    .put("enabled", s.enabled)
            )
        }
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_MCP_SERVERS] = arr.toString()
        }
    }

    suspend fun setSharedSessionCode(conversationId: Long, shareCode: String?) {
        val current = sharedSessionsFlow.first().toMutableMap()
        if (shareCode.isNullOrBlank()) {
            current.remove(conversationId)
        } else {
            current[conversationId] = shareCode
        }
        val obj = JSONObject()
        current.forEach { (k, v) -> obj.put(k.toString(), v) }
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_SHARED_SESSIONS] = obj.toString()
        }
    }

    suspend fun setSearchProvider(provider: WebSearchProvider) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_SEARCH_PROVIDER] = provider.name
        }
    }

    suspend fun saveSearchApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        context.keyVaultDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(KEY_SEARCH_API_KEY)
            } else {
                prefs[KEY_SEARCH_API_KEY] = encryptForStorage(trimmed)
            }
        }
    }

    suspend fun getSearchApiKey(): String {
        val stored = context.keyVaultDataStore.data.first()[KEY_SEARCH_API_KEY] ?: return ""
        return decryptFromStorage(stored)
    }

    suspend fun setAutoRouteEnabled(enabled: Boolean) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_AUTO_ROUTE_ENABLED] = enabled
        }
    }

    suspend fun setAutoExtractMemories(enabled: Boolean) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_AUTO_EXTRACT_MEMORIES] = enabled
        }
    }

    suspend fun saveAutoRouterConfig(config: AutoRouterConfig) {
        val obj = JSONObject()
        config.fastChatModelId?.let { obj.put("fast", it) }
        config.heavyCodingModelId?.let { obj.put("coding", it) }
        config.visionModelId?.let { obj.put("vision", it) }
        config.longContextModelId?.let { obj.put("long", it) }
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_AUTO_ROUTER_CONFIG] = obj.toString()
        }
    }

    suspend fun saveCustomSlashCommands(commands: List<CustomSlashCommand>) {
        val arr = JSONArray()
        commands.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("trigger", c.trigger)
                    .put("description", c.description)
                    .put("template", c.template)
            )
        }
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SLASH_COMMANDS] = arr.toString()
        }
    }

    private fun prefKey(providerId: Long) = stringPreferencesKey("provider_key_$providerId")

    suspend fun saveApiKey(providerId: Long, rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            deleteApiKey(providerId)
            return
        }
        val encrypted = encryptForStorage(trimmed)
        context.keyVaultDataStore.edit { prefs ->
            prefs[prefKey(providerId)] = encrypted
        }
    }

    suspend fun getApiKey(providerId: Long): String {
        val stored = context.keyVaultDataStore.data.first()[prefKey(providerId)] ?: return ""
        return decryptFromStorage(stored)
    }

    suspend fun getAllDecryptedKeys(): Map<Long, String> {
        val prefs = context.keyVaultDataStore.data.first()
        val result = mutableMapOf<Long, String>()
        prefs.asMap().forEach { (k, v) ->
            val providerId = k.name.removePrefix("provider_key_").toLongOrNull()
            if (providerId != null && v is String) {
                val plain = decryptFromStorage(v)
                if (plain.isNotBlank()) result[providerId] = plain
            }
        }
        return result
    }

    suspend fun clearAllApiKeys() {
        context.keyVaultDataStore.edit { prefs ->
            val keyNames = prefs.asMap().keys.filter { it.name.startsWith("provider_key_") }
            keyNames.forEach { prefs.remove(it) }
        }
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

    /**
     * Enables or updates the optional 4-6 digit PIN / master passphrase.
     * Re-encrypts all stored API keys under the derived AES-256-GCM key.
     */
    suspend fun setPinPassphrase(passphrase: String): Result<Unit> = runCatching {
        val clean = passphrase.trim()
        require(clean.length >= 4) { "PIN or passphrase must be at least 4 characters." }

        val existingPlain = getAllDecryptedKeys()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derivedKey = deriveAesKey(clean, salt)
        val verifier = computeVerifier(derivedKey, salt)

        sessionPinKey.value = derivedKey
        _isLocked.value = false

        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[KEY_PIN_VERIFIER] = Base64.encodeToString(verifier, Base64.NO_WRAP)
            existingPlain.forEach { (pid, rawKey) ->
                prefs[prefKey(pid)] = encryptWithKey(rawKey, derivedKey, PREFIX_PIN_ENC)
            }
        }
    }

    suspend fun unlockWithPin(passphrase: String): Boolean {
        val prefs = context.keyVaultDataStore.data.first()
        val saltB64 = prefs[KEY_PIN_SALT] ?: return true
        val verifierB64 = prefs[KEY_PIN_VERIFIER] ?: return true

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(verifierB64, Base64.NO_WRAP)
        val candidateKey = deriveAesKey(passphrase.trim(), salt)
        val candidateVerifier = computeVerifier(candidateKey, salt)

        return if (MessageDigest.isEqual(expected, candidateVerifier)) {
            sessionPinKey.value = candidateKey
            _isLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockNow() {
        sessionPinKey.value = null
        _isLocked.value = true
    }

    suspend fun disablePinPassphrase() {
        val existingPlain = getAllDecryptedKeys()
        sessionPinKey.value = null
        _isLocked.value = false
        context.keyVaultDataStore.edit { prefs ->
            prefs.remove(KEY_PIN_SALT)
            prefs.remove(KEY_PIN_VERIFIER)
            existingPlain.forEach { (pid, rawKey) ->
                prefs[prefKey(pid)] = encryptWithKeystore(rawKey)
            }
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun computeVerifier(key: SecretKey, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(key.encoded)
        md.update("allchat_vault_verifier_v2".toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    fun encryptSecret(plainText: String): String {
        val clean = plainText.trim()
        if (clean.isEmpty()) return ""
        return encryptForStorage(clean)
    }

    fun decryptSecret(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return decryptFromStorage(encoded)
    }

    private fun encryptForStorage(plainText: String): String {
        val pinKey = sessionPinKey.value
        if (pinKey != null) {
            return encryptWithKey(plainText, pinKey, PREFIX_PIN_ENC)
        }
        return encryptWithKeystore(plainText)
    }

    private fun decryptFromStorage(encoded: String): String {
        if (encoded.startsWith(PREFIX_PIN_ENC)) {
            val pinKey = sessionPinKey.value ?: return ""
            return decryptWithKey(encoded.removePrefix(PREFIX_PIN_ENC), pinKey)
        }
        if (encoded.startsWith(PREFIX_ENC)) {
            val secretKey = getOrCreateSecretKey() ?: return ""
            return decryptWithKey(encoded.removePrefix(PREFIX_ENC), secretKey)
        }
        if (encoded.startsWith(PREFIX_FALLBACK)) {
            return runCatching {
                String(Base64.decode(encoded.removePrefix(PREFIX_FALLBACK), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault("")
        }
        return ""
    }

    private fun encryptWithKey(plainText: String, secretKey: SecretKey, prefix: String): String {
        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return prefix + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptWithKey(b64Payload: String, secretKey: SecretKey): String {
        return runCatching {
            val raw = Base64.decode(b64Payload, Base64.NO_WRAP)
            if (raw.size <= IV_SIZE_BYTES) return ""
            val iv = raw.copyOfRange(0, IV_SIZE_BYTES)
            val cipherBytes = raw.copyOfRange(IV_SIZE_BYTES, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
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

    private fun encryptWithKeystore(plainText: String): String {
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
}
