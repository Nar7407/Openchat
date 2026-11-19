package com.example.data.local

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class ImportedMessageDraft(
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val createdAt: Long
)

data class ImportedConversationDraft(
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ImportedMessageDraft>
)

data class ConversationImportParseResult(
    val sourceLabel: String, // "ChatGPT" or "Claude"
    val conversations: List<ImportedConversationDraft>
)

/**
 * Parses standard ChatGPT (`conversations.json` with `mapping` node trees)
 * and Claude (`conversations.json` with `chat_messages` arrays) export files.
 */
object ConversationImporter {

    fun parseExportJson(rawJson: String): ConversationImportParseResult {
        val trimmed = rawJson.trim()
        require(trimmed.isNotEmpty()) { "Selected file is empty." }

        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("conversations")
                    ?: JSONArray().put(root)
            }
            else -> throw IllegalArgumentException("File is not valid JSON.")
        }

        val chatGptDrafts = mutableListOf<ImportedConversationDraft>()
        val claudeDrafts = mutableListOf<ImportedConversationDraft>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            when {
                obj.has("mapping") -> parseChatGptConversation(obj)?.let { chatGptDrafts += it }
                obj.has("chat_messages") -> parseClaudeConversation(obj)?.let { claudeDrafts += it }
                obj.has("messages") -> parseGenericConversation(obj)?.let { chatGptDrafts += it }
            }
        }

        return when {
            chatGptDrafts.isNotEmpty() && claudeDrafts.isEmpty() ->
                ConversationImportParseResult("ChatGPT", chatGptDrafts)
            claudeDrafts.isNotEmpty() && chatGptDrafts.isEmpty() ->
                ConversationImportParseResult("Claude", claudeDrafts)
            chatGptDrafts.isNotEmpty() || claudeDrafts.isNotEmpty() ->
                ConversationImportParseResult("ChatGPT / Claude", chatGptDrafts + claudeDrafts)
            else -> throw IllegalArgumentException(
                "No recognizable ChatGPT or Claude conversations found in this JSON file."
            )
        }
    }

    private fun parseChatGptConversation(obj: JSONObject): ImportedConversationDraft? {
        val title = obj.optString("title").trim().ifBlank { "Imported ChatGPT Thread" }
        val createTimeMs = parseEpochSecondsOrMs(obj.optDouble("create_time", Double.NaN))
        val updateTimeMs = parseEpochSecondsOrMs(obj.optDouble("update_time", Double.NaN))
            .takeIf { it > 0L } ?: createTimeMs

        val mapping = obj.optJSONObject("mapping") ?: return null
        val currentNodeId = obj.optString("current_node").takeIf { it.isNotBlank() && it != "null" }

        val orderedNodes = mutableListOf<JSONObject>()
        if (currentNodeId != null && mapping.has(currentNodeId)) {
            val visited = mutableSetOf<String>()
            var cursor: String? = currentNodeId
            while (cursor != null && cursor !in visited) {
                visited += cursor
                val node = mapping.optJSONObject(cursor) ?: break
                orderedNodes += node
                val parent = node.optString("parent")
                cursor = parent.takeIf { it.isNotBlank() && it != "null" }
            }
            orderedNodes.reverse()
        } else {
            val keys = mapping.keys()
            while (keys.hasNext()) {
                mapping.optJSONObject(keys.next())?.let { orderedNodes += it }
            }
            orderedNodes.sortBy { node ->
                node.optJSONObject("message")?.optDouble("create_time", 0.0) ?: 0.0
            }
        }

        val messages = mutableListOf<ImportedMessageDraft>()
        orderedNodes.forEachIndexed { idx, node ->
            val msgObj = node.optJSONObject("message") ?: return@forEachIndexed
            val roleRaw = msgObj.optJSONObject("author")?.optString("role").orEmpty().lowercase()
            val role = when (roleRaw) {
                "user" -> "USER"
                "assistant" -> "ASSISTANT"
                else -> return@forEachIndexed
            }
            val contentObj = msgObj.optJSONObject("content") ?: return@forEachIndexed
            val text = extractChatGptContentText(contentObj).trim()
            if (text.isEmpty()) return@forEachIndexed

            val ts = parseEpochSecondsOrMs(msgObj.optDouble("create_time", Double.NaN))
                .takeIf { it > 0L } ?: (createTimeMs + idx * 1000L)
            messages += ImportedMessageDraft(role = role, content = text, createdAt = ts)
        }

        if (messages.isEmpty()) return null
        return ImportedConversationDraft(
            title = title,
            createdAt = createTimeMs,
            updatedAt = updateTimeMs,
            messages = messages
        )
    }

    private fun extractChatGptContentText(contentObj: JSONObject): String {
        val parts = contentObj.optJSONArray("parts")
        if (parts != null) {
            val out = mutableListOf<String>()
            for (i in 0 until parts.length()) {
                val item = parts.opt(i)
                when (item) {
                    is String -> if (item.isNotBlank()) out += item
                    is JSONObject -> {
                        val t = item.optString("text").ifBlank { item.optString("content") }
                        if (t.isNotBlank()) out += t
                    }
                }
            }
            if (out.isNotEmpty()) return out.joinToString("\n")
        }
        return contentObj.optString("text", "")
    }

    private fun parseClaudeConversation(obj: JSONObject): ImportedConversationDraft? {
        val title = obj.optString("name").trim()
            .ifBlank { obj.optString("summary").trim() }
            .ifBlank { "Imported Claude Thread" }
        val createMs = parseIsoOrEpoch(obj.optString("created_at"))
        val updateMs = parseIsoOrEpoch(obj.optString("updated_at")).takeIf { it > 0L } ?: createMs

        val msgArr = obj.optJSONArray("chat_messages") ?: return null
        val messages = mutableListOf<ImportedMessageDraft>()
        for (i in 0 until msgArr.length()) {
            val m = msgArr.optJSONObject(i) ?: continue
            val sender = m.optString("sender").lowercase()
            val role = when (sender) {
                "human", "user" -> "USER"
                "assistant", "claude" -> "ASSISTANT"
                else -> continue
            }
            val directText = m.optString("text").trim()
            val text = if (directText.isNotEmpty()) {
                directText
            } else {
                extractClaudeContentBlocks(m.optJSONArray("content"))
            }
            if (text.isBlank()) continue
            val ts = parseIsoOrEpoch(m.optString("created_at")).takeIf { it > 0L }
                ?: (createMs + i * 1000L)
            messages += ImportedMessageDraft(role = role, content = text, createdAt = ts)
        }

        if (messages.isEmpty()) return null
        return ImportedConversationDraft(
            title = title,
            createdAt = createMs,
            updatedAt = updateMs,
            messages = messages
        )
    }

    private fun extractClaudeContentBlocks(arr: JSONArray?): String {
        if (arr == null) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val block = arr.optJSONObject(i) ?: continue
            val type = block.optString("type")
            when (type) {
                "thinking" -> {
                    val thought = block.optString("thinking").trim()
                    if (thought.isNotEmpty()) sb.append("<think>\n").append(thought).append("\n</think>\n\n")
                }
                "text" -> {
                    val t = block.optString("text")
                    if (t.isNotEmpty()) sb.append(t)
                }
            }
        }
        return sb.toString().trim()
    }

    private fun parseGenericConversation(obj: JSONObject): ImportedConversationDraft? {
        val title = obj.optString("title").trim().ifBlank { "Imported Thread" }
        val now = System.currentTimeMillis()
        val arr = obj.optJSONArray("messages") ?: return null
        val messages = mutableListOf<ImportedMessageDraft>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val r = m.optString("role").lowercase()
            val role = when (r) {
                "user", "human" -> "USER"
                "assistant", "model" -> "ASSISTANT"
                else -> continue
            }
            val text = m.optString("content").trim()
            if (text.isNotEmpty()) {
                messages += ImportedMessageDraft(role, text, now + i * 1000L)
            }
        }
        if (messages.isEmpty()) return null
        return ImportedConversationDraft(title, now, now, messages)
    }

    private fun parseEpochSecondsOrMs(value: Double): Long {
        if (value.isNaN() || value <= 0.0) return System.currentTimeMillis()
        return if (value > 10_000_000_000.0) value.toLong() else (value * 1000.0).toLong()
    }

    private fun parseIsoOrEpoch(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return System.currentTimeMillis()
        s.toDoubleOrNull()?.let { return parseEpochSecondsOrMs(it) }
        return runCatching { Instant.parse(s).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }
}
