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
import com.example.domain.model.DailyBudgetConfig
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.McpServerConfig
import com.example.domain.model.MemoryProfile
import com.example.domain.model.QuickContact
import com.example.domain.model.SmartZone
import com.example.domain.model.ThreadCheckpoint
import com.example.domain.model.VoiceFormTemplate
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
        val KEY_SMART_ZONES = stringPreferencesKey("smart_geofence_zones_json")
        val KEY_TIME_PROFILES_ENABLED = booleanPreferencesKey("time_profiles_enabled")
        val KEY_AUTO_ECO_MODE = booleanPreferencesKey("auto_eco_mode_enabled")
        val KEY_AUTO_DELETE_JUNK = booleanPreferencesKey("auto_delete_junk_chats_7d")
        val KEY_LOCAL_WORKSPACE_URI = stringPreferencesKey("local_workspace_tree_uri")
        val KEY_WAKE_PHRASE_ENABLED = booleanPreferencesKey("wake_phrase_enabled")
        val KEY_AMBIENT_WHISPER_ENABLED = booleanPreferencesKey("ambient_whisper_enabled")
        val KEY_DAILY_BUDGET_CONFIG = stringPreferencesKey("daily_budget_config_json")
        val KEY_VOICE_FORM_TEMPLATES = stringPreferencesKey("voice_form_templates_json")
        val KEY_AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_idle_minutes")
        val KEY_LOCK_ON_APP_SWITCH = booleanPreferencesKey("lock_on_app_switch")
        val KEY_THREAD_CHECKPOINTS = stringPreferencesKey("thread_checkpoints_json")
        val KEY_AUTO_DIAL_WEBHOOK_URL = stringPreferencesKey("auto_dial_webhook_url")
        val KEY_PINNED_MEMORY_IDS = stringPreferencesKey("pinned_memory_ids_json")
        val KEY_PINNED_MODEL_IDS = stringPreferencesKey("pinned_model_ids_json")
        val KEY_MODEL_LATENCY_MAP = stringPreferencesKey("model_latency_ms_json")
        val KEY_FAST_UTILITY_MODEL_ID = longPreferencesKey("fast_utility_model_db_id")
        val KEY_ACTIVE_MEMORY_PROFILE = stringPreferencesKey("active_memory_profile")
        val KEY_MEMORY_PROFILE_MAP = stringPreferencesKey("memory_profile_map_json")
        val KEY_SEMANTIC_AUTO_ATTACH = booleanPreferencesKey("semantic_auto_attach")
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
        prefs[KEY_AUTO_EXTRACT_MEMORIES] ?: true
    }

    val pinnedMemoryIdsFlow: Flow<Set<Long>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_PINNED_MEMORY_IDS].orEmpty()
        if (raw.isBlank()) return@map emptySet()
        runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val v = arr.optLong(i, -1L)
                    if (v > 0L) add(v)
                }
            }
        }.getOrDefault(emptySet())
    }

    suspend fun setMemoryPinned(id: Long, pinned: Boolean) {
        context.keyVaultDataStore.edit { prefs ->
            val current = runCatching {
                val arr = JSONArray(prefs[KEY_PINNED_MEMORY_IDS].orEmpty().ifBlank { "[]" })
                buildSet {
                    for (i in 0 until arr.length()) {
                        val v = arr.optLong(i, -1L)
                        if (v > 0L) add(v)
                    }
                }
            }.getOrDefault(emptySet()).toMutableSet()
            if (pinned) current.add(id) else current.remove(id)
            val out = JSONArray()
            current.forEach { out.put(it) }
            prefs[KEY_PINNED_MEMORY_IDS] = out.toString()
        }
    }

    suspend fun clearPinnedMemoryIds() {
        context.keyVaultDataStore.edit { prefs ->
            prefs.remove(KEY_PINNED_MEMORY_IDS)
        }
    }

    val pinnedModelIdsFlow: Flow<List<Long>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_PINNED_MODEL_IDS].orEmpty()
        if (raw.isBlank()) return@map emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optLong(i, -1L)
                    if (v > 0L && v !in this) add(v)
                }
            }.take(4)
        }.getOrDefault(emptyList())
    }

    /**
     * Toggles a model DB ID in the pinned favorites list (max 4).
     * Returns true if pinned after toggle, false if unpinned or rejected due to max limit.
     */
    suspend fun togglePinnedModelId(modelDbId: Long, maxPins: Int = 4): Pair<Boolean, Boolean> {
        var isNowPinned = false
        var hitLimit = false
        context.keyVaultDataStore.edit { prefs ->
            val current = runCatching {
                val arr = JSONArray(prefs[KEY_PINNED_MODEL_IDS].orEmpty().ifBlank { "[]" })
                buildList {
                    for (i in 0 until arr.length()) {
                        val v = arr.optLong(i, -1L)
                        if (v > 0L && v !in this) add(v)
                    }
                }
            }.getOrDefault(emptyList()).toMutableList()

            if (modelDbId in current) {
                current.remove(modelDbId)
                isNowPinned = false
            } else if (current.size >= maxPins) {
                isNowPinned = false
                hitLimit = true
            } else {
                current.add(modelDbId)
                isNowPinned = true
            }
            val out = JSONArray()
            current.take(maxPins).forEach { out.put(it) }
            prefs[KEY_PINNED_MODEL_IDS] = out.toString()
        }
        return isNowPinned to hitLimit
    }

    val modelLatencyMsFlow: Flow<Map<Long, Long>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_MODEL_LATENCY_MAP].orEmpty()
        if (raw.isBlank()) return@map emptyMap()
        runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val id = k.toLongOrNull() ?: continue
                    val ms = obj.optLong(k, -1L)
                    if (ms > 0L) put(id, ms)
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun recordModelLatency(modelDbId: Long, latencyMs: Long) {
        if (modelDbId <= 0L || latencyMs <= 0L) return
        context.keyVaultDataStore.edit { prefs ->
            val obj = runCatching {
                JSONObject(prefs[KEY_MODEL_LATENCY_MAP].orEmpty().ifBlank { "{}" })
            }.getOrDefault(JSONObject())
            val prev = obj.optLong(modelDbId.toString(), -1L)
            // Exponential moving average so latency badges stay smooth yet responsive
            val blended = if (prev > 0L) ((prev * 0.45) + (latencyMs * 0.55)).toLong() else latencyMs
            obj.put(modelDbId.toString(), blended.coerceIn(1L, 120_000L))
            prefs[KEY_MODEL_LATENCY_MAP] = obj.toString()
        }
    }

    val fastUtilityModelIdFlow: Flow<Long?> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_FAST_UTILITY_MODEL_ID]?.takeIf { it > 0L }
    }

    suspend fun setFastUtilityModelId(modelDbId: Long?) {
        context.keyVaultDataStore.edit { prefs ->
            if (modelDbId == null || modelDbId <= 0L) {
                prefs.remove(KEY_FAST_UTILITY_MODEL_ID)
            } else {
                prefs[KEY_FAST_UTILITY_MODEL_ID] = modelDbId
            }
        }
    }

    val activeMemoryProfileFlow: Flow<MemoryProfile> = context.keyVaultDataStore.data.map { prefs ->
        runCatching {
            MemoryProfile.valueOf(prefs[KEY_ACTIVE_MEMORY_PROFILE] ?: MemoryProfile.STUDY.name)
        }.getOrDefault(MemoryProfile.STUDY)
    }

    suspend fun setActiveMemoryProfile(profile: MemoryProfile) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_MEMORY_PROFILE] = profile.name
        }
    }

    val memoryProfileMapFlow: Flow<Map<Long, String>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_MEMORY_PROFILE_MAP].orEmpty()
        if (raw.isBlank()) return@map emptyMap()
        runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val id = k.toLongOrNull() ?: continue
                    val p = obj.optString(k, "ALL").uppercase()
                    if (p.isNotBlank()) put(id, p)
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun setMemoryProfile(memoryId: Long, profile: String) {
        if (memoryId <= 0L) return
        val normalized = profile.trim().uppercase().ifBlank { "ALL" }
        context.keyVaultDataStore.edit { prefs ->
            val obj = runCatching {
                JSONObject(prefs[KEY_MEMORY_PROFILE_MAP].orEmpty().ifBlank { "{}" })
            }.getOrDefault(JSONObject())
            obj.put(memoryId.toString(), normalized)
            prefs[KEY_MEMORY_PROFILE_MAP] = obj.toString()
        }
    }

    suspend fun clearMemoryProfiles() {
        context.keyVaultDataStore.edit { it.remove(KEY_MEMORY_PROFILE_MAP) }
    }

    val semanticAutoAttachFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { prefs ->
        prefs[KEY_SEMANTIC_AUTO_ATTACH] ?: false
    }

    suspend fun setSemanticAutoAttach(enabled: Boolean) {
        context.keyVaultDataStore.edit { prefs ->
            prefs[KEY_SEMANTIC_AUTO_ATTACH] = enabled
        }
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
                    val email = o.optString("email")
                    val upiId = o.optString("upiId")
                    val nicknames = o.optString("nicknames")
                    if (name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank() || upiId.isNotBlank())) {
                        add(
                            QuickContact(
                                id = id.ifBlank { "c_$i" },
                                name = name,
                                phoneNumber = phone,
                                email = email,
                                upiId = upiId,
                                nicknames = nicknames
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeQuickContacts(list: List<QuickContact>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("phoneNumber", c.phoneNumber)
                    .put("email", c.email)
                    .put("upiId", c.upiId)
                    .put("nicknames", c.nicknames)
            )
        }
        return arr.toString()
    }

    suspend fun saveQuickContact(contact: QuickContact) {
        val current = quickContactsFlow.first().toMutableList()
        val id = contact.id.ifBlank { "qc_${System.currentTimeMillis()}_${current.size}" }
        val cleanName = contact.name.trim()
        val cleanPhone = contact.phoneNumber.trim()
        val existing = current.firstOrNull {
            it.id == id || (
                it.name.equals(cleanName, ignoreCase = true) &&
                    (cleanPhone.isBlank() || it.phoneNumber.isBlank() || it.phoneNumber == cleanPhone)
                )
        }
        val mergedNicknames = listOf(existing?.nicknames.orEmpty(), contact.nicknames.trim())
            .flatMap { it.split(',', ';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
        val normalized = contact.copy(
            id = existing?.id ?: id,
            name = cleanName,
            phoneNumber = cleanPhone.ifBlank { existing?.phoneNumber.orEmpty() },
            email = contact.email.trim().ifBlank { existing?.email.orEmpty() },
            upiId = contact.upiId.trim().ifBlank { existing?.upiId.orEmpty() },
            nicknames = mergedNicknames
        )
        val idx = current.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) current[idx] = normalized else current.add(0, normalized)
        context.keyVaultDataStore.edit { it[KEY_QUICK_CONTACTS] = serializeQuickContacts(current) }
    }

    suspend fun saveQuickContactsBulk(contacts: List<QuickContact>): Int {
        if (contacts.isEmpty()) return 0
        val current = quickContactsFlow.first().toMutableList()
        var addedOrUpdated = 0
        val baseTs = System.currentTimeMillis()
        contacts.forEachIndexed { idxOffset, raw ->
            val cleanName = raw.name.trim()
            val cleanPhone = raw.phoneNumber.trim()
            val cleanEmail = raw.email.trim()
            val cleanUpi = raw.upiId.trim()
            if (cleanName.isBlank() || (cleanPhone.isBlank() && cleanEmail.isBlank() && cleanUpi.isBlank())) {
                return@forEachIndexed
            }
            val existingIdx = current.indexOfFirst {
                it.name.equals(cleanName, ignoreCase = true) &&
                    (cleanPhone.isBlank() || it.phoneNumber == cleanPhone)
            }
            if (existingIdx >= 0) {
                val ex = current[existingIdx]
                val mergedAliases = listOf(ex.nicknames, raw.nicknames)
                    .flatMap { it.split(',', ';') }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .joinToString(", ")
                current[existingIdx] = ex.copy(
                    phoneNumber = cleanPhone.ifBlank { ex.phoneNumber },
                    email = cleanEmail.ifBlank { ex.email },
                    upiId = cleanUpi.ifBlank { ex.upiId },
                    nicknames = mergedAliases
                )
            } else {
                current.add(
                    0,
                    QuickContact(
                        id = raw.id.ifBlank { "qc_${baseTs}_$idxOffset" },
                        name = cleanName,
                        phoneNumber = cleanPhone,
                        email = cleanEmail,
                        upiId = cleanUpi,
                        nicknames = raw.nicknames.trim()
                    )
                )
            }
            addedOrUpdated++
        }
        if (addedOrUpdated > 0) {
            context.keyVaultDataStore.edit { it[KEY_QUICK_CONTACTS] = serializeQuickContacts(current) }
        }
        return addedOrUpdated
    }

    suspend fun deleteQuickContact(id: String) {
        val updated = quickContactsFlow.first().filterNot { it.id == id }
        context.keyVaultDataStore.edit { it[KEY_QUICK_CONTACTS] = serializeQuickContacts(updated) }
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

    val timeProfilesEnabledFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { it[KEY_TIME_PROFILES_ENABLED] ?: true }
    suspend fun setTimeProfilesEnabled(enabled: Boolean) {
        context.keyVaultDataStore.edit { it[KEY_TIME_PROFILES_ENABLED] = enabled }
    }

    val autoEcoModeFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { it[KEY_AUTO_ECO_MODE] ?: true }
    suspend fun setAutoEcoMode(enabled: Boolean) {
        context.keyVaultDataStore.edit { it[KEY_AUTO_ECO_MODE] = enabled }
    }

    val autoDeleteJunkChatsFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { it[KEY_AUTO_DELETE_JUNK] ?: false }
    suspend fun setAutoDeleteJunkChats(enabled: Boolean) {
        context.keyVaultDataStore.edit { it[KEY_AUTO_DELETE_JUNK] = enabled }
    }

    val localWorkspaceUriFlow: Flow<String?> = context.keyVaultDataStore.data.map { it[KEY_LOCAL_WORKSPACE_URI] }
    suspend fun setLocalWorkspaceUri(uri: String?) {
        context.keyVaultDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(KEY_LOCAL_WORKSPACE_URI) else prefs[KEY_LOCAL_WORKSPACE_URI] = uri
        }
    }

    val wakePhraseEnabledFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { it[KEY_WAKE_PHRASE_ENABLED] ?: false }
    suspend fun setWakePhraseEnabled(enabled: Boolean) {
        context.keyVaultDataStore.edit { it[KEY_WAKE_PHRASE_ENABLED] = enabled }
    }

    val ambientWhisperEnabledFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { it[KEY_AMBIENT_WHISPER_ENABLED] ?: true }
    suspend fun setAmbientWhisperEnabled(enabled: Boolean) {
        context.keyVaultDataStore.edit { it[KEY_AMBIENT_WHISPER_ENABLED] = enabled }
    }

    val autoLockMinutesFlow: Flow<Int> = context.keyVaultDataStore.data.map { it[KEY_AUTO_LOCK_MINUTES] ?: 5 }
    suspend fun setAutoLockMinutes(minutes: Int) {
        context.keyVaultDataStore.edit { it[KEY_AUTO_LOCK_MINUTES] = minutes.coerceIn(0, 60) }
    }

    val lockOnAppSwitchFlow: Flow<Boolean> = context.keyVaultDataStore.data.map { it[KEY_LOCK_ON_APP_SWITCH] ?: false }
    suspend fun setLockOnAppSwitch(enabled: Boolean) {
        context.keyVaultDataStore.edit { it[KEY_LOCK_ON_APP_SWITCH] = enabled }
    }

    val autoDialWebhookUrlFlow: Flow<String> = context.keyVaultDataStore.data.map { it[KEY_AUTO_DIAL_WEBHOOK_URL].orEmpty() }
    suspend fun setAutoDialWebhookUrl(url: String) {
        val clean = url.trim()
        context.keyVaultDataStore.edit { prefs ->
            if (clean.isEmpty()) prefs.remove(KEY_AUTO_DIAL_WEBHOOK_URL) else prefs[KEY_AUTO_DIAL_WEBHOOK_URL] = clean
        }
    }

    val dailyBudgetConfigFlow: Flow<DailyBudgetConfig> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_DAILY_BUDGET_CONFIG].orEmpty()
        if (raw.isBlank()) return@map DailyBudgetConfig()
        runCatching {
            val o = JSONObject(raw)
            DailyBudgetConfig(
                dailyCapUsd = o.optDouble("cap", 0.0).coerceAtLeast(0.0),
                autoDowngradeEnabled = o.optBoolean("autoDowngrade", true),
                downgradeModelDbId = o.optLong("downgradeModelId", -1L).takeIf { it > 0L }
            )
        }.getOrDefault(DailyBudgetConfig())
    }

    suspend fun saveDailyBudgetConfig(config: DailyBudgetConfig) {
        val obj = JSONObject()
            .put("cap", config.dailyCapUsd.coerceAtLeast(0.0))
            .put("autoDowngrade", config.autoDowngradeEnabled)
            .put("downgradeModelId", config.downgradeModelDbId ?: -1L)
        context.keyVaultDataStore.edit { it[KEY_DAILY_BUDGET_CONFIG] = obj.toString() }
    }

    val voiceFormTemplatesFlow: Flow<List<VoiceFormTemplate>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_VOICE_FORM_TEMPLATES]
        if (raw == null) return@map defaultVoiceFormTemplates()
        if (raw.isBlank()) return@map defaultVoiceFormTemplates()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val fieldsArr = o.optJSONArray("fields")
                    val fields = buildList {
                        if (fieldsArr != null) {
                            for (j in 0 until fieldsArr.length()) {
                                val f = fieldsArr.optString(j).trim()
                                if (f.isNotEmpty()) add(f)
                            }
                        }
                    }
                    if (fields.isNotEmpty()) {
                        add(
                            VoiceFormTemplate(
                                id = o.optString("id"),
                                name = o.optString("name"),
                                category = o.optString("category", "CUSTOM"),
                                fields = fields
                            )
                        )
                    }
                }
            }.ifEmpty { defaultVoiceFormTemplates() }
        }.getOrDefault(defaultVoiceFormTemplates())
    }

    suspend fun saveVoiceFormTemplates(templates: List<VoiceFormTemplate>) {
        val arr = JSONArray()
        templates.forEach { tpl ->
            val fieldsArr = JSONArray()
            tpl.fields.forEach { fieldsArr.put(it) }
            arr.put(
                JSONObject()
                    .put("id", tpl.id.ifBlank { java.util.UUID.randomUUID().toString() })
                    .put("name", tpl.name)
                    .put("category", tpl.category)
                    .put("fields", fieldsArr)
            )
        }
        context.keyVaultDataStore.edit { it[KEY_VOICE_FORM_TEMPLATES] = arr.toString() }
    }

    private fun defaultVoiceFormTemplates(): List<VoiceFormTemplate> = listOf(
        VoiceFormTemplate(
            id = "tpl_bug_report",
            name = "Bug Report",
            category = "ENGINEERING",
            fields = listOf("Title", "Severity", "Platform / Device", "Steps to Reproduce", "Expected vs Actual")
        ),
        VoiceFormTemplate(
            id = "tpl_daily_standup",
            name = "Daily Standup Log",
            category = "WORK",
            fields = listOf("Date", "Completed Yesterday", "Focus Today", "Blockers", "ETA / Notes")
        ),
        VoiceFormTemplate(
            id = "tpl_workout_log",
            name = "Workout Log",
            category = "FITNESS",
            fields = listOf("Exercise", "Sets x Reps", "Weight / Load", "RPE / Effort", "Notes")
        ),
        VoiceFormTemplate(
            id = "tpl_lab_experiment",
            name = "Lab Experiment Observation",
            category = "SCIENCE",
            fields = listOf("Experiment / Sample", "Independent Variable", "Measured Observation", "Anomalies", "Next Step")
        )
    )

    val threadCheckpointsFlow: Flow<Map<Long, List<ThreadCheckpoint>>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_THREAD_CHECKPOINTS].orEmpty()
        if (raw.isBlank()) return@map emptyMap()
        runCatching {
            val arr = JSONArray(raw)
            val grouped = LinkedHashMap<Long, MutableList<ThreadCheckpoint>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val convId = o.optLong("convId", 0L)
                if (convId <= 0L) continue
                val cp = ThreadCheckpoint(
                    id = o.optString("id"),
                    conversationId = convId,
                    afterMessageId = o.optLong("afterMsgId", 0L),
                    messageCountCovered = o.optInt("count", 0),
                    summaryMarkdown = o.optString("summary"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
                grouped.getOrPut(convId) { mutableListOf() }.add(cp)
            }
            grouped
        }.getOrDefault(emptyMap())
    }

    suspend fun saveThreadCheckpoints(map: Map<Long, List<ThreadCheckpoint>>) {
        val arr = JSONArray()
        map.values.flatten().takeLast(80).forEach { cp ->
            arr.put(
                JSONObject()
                    .put("id", cp.id)
                    .put("convId", cp.conversationId)
                    .put("afterMsgId", cp.afterMessageId)
                    .put("count", cp.messageCountCovered)
                    .put("summary", cp.summaryMarkdown)
                    .put("createdAt", cp.createdAt)
            )
        }
        context.keyVaultDataStore.edit { it[KEY_THREAD_CHECKPOINTS] = arr.toString() }
    }

    val smartZonesFlow: Flow<List<SmartZone>> = context.keyVaultDataStore.data.map { prefs ->
        val raw = prefs[KEY_SMART_ZONES].orEmpty()
        if (raw.isBlank()) return@map emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val chipsArr = o.optJSONArray("chips")
                    val chips = buildList {
                        if (chipsArr != null) {
                            for (j in 0 until chipsArr.length()) {
                                val c = chipsArr.optString(j).trim()
                                if (c.isNotEmpty()) add(c)
                            }
                        }
                    }
                    add(
                        SmartZone(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            latitude = o.optDouble("lat", 0.0),
                            longitude = o.optDouble("lng", 0.0),
                            radiusMeters = o.optDouble("radius", 250.0).toFloat(),
                            quickChips = chips
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveSmartZones(zones: List<SmartZone>) {
        val arr = JSONArray()
        zones.forEach { z ->
            val chips = JSONArray()
            z.quickChips.forEach { chips.put(it) }
            arr.put(
                JSONObject()
                    .put("id", z.id.ifBlank { java.util.UUID.randomUUID().toString() })
                    .put("name", z.name)
                    .put("lat", z.latitude)
                    .put("lng", z.longitude)
                    .put("radius", z.radiusMeters.toDouble())
                    .put("chips", chips)
            )
        }
        context.keyVaultDataStore.edit { it[KEY_SMART_ZONES] = arr.toString() }
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
