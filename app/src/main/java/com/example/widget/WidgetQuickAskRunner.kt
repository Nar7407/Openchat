package com.example.widget

import android.content.Context
import com.example.AllChatApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class WidgetTurn(
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val isError: Boolean = false
)

data class WidgetQaState(
    val question: String = "",
    val answer: String = "",
    val turns: List<WidgetTurn> = emptyList(),
    val modelLabel: String = "",
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val loadingStartedAtMs: Long = 0L
) {
    val hasContent: Boolean
        get() = turns.isNotEmpty() || question.isNotBlank() || answer.isNotBlank() || isLoading

    /**
     * Renders a compact multi-turn transcript for the Home Screen widget so prior turns remain
     * visible alongside the latest streaming answer.
     */
    fun formattedTranscript(): String {
        if (turns.isEmpty()) return answer
        return turns.takeLast(6).joinToString("\n\n") { turn ->
            val prefix = if (turn.role == "USER") "YOU: " else "AI: "
            prefix + turn.content
        }
    }
}

object WidgetQuickAskRunner {

    private const val PREFS_NAME = "allchat_widget_state"
    private const val KEY_QUESTION = "question"
    private const val KEY_ANSWER = "answer"
    private const val KEY_TURNS_JSON = "turns_json"
    private const val KEY_MODEL = "model_label"
    private const val KEY_LOADING = "is_loading"
    private const val KEY_ERROR = "is_error"
    private const val KEY_LOADING_STARTED_AT = "loading_started_at"

    private const val HARD_TIMEOUT_MS = 30_000L
    private const val STALE_LOADING_RECOVERY_MS = 35_000L

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeRequestToken: Long = 0L

    @Volatile
    private var activeServiceRunning: Boolean = false

    @Volatile
    private var pendingPreparedTurns: Pair<Long, List<WidgetTurn>>? = null

    @Volatile
    private var pendingPreparedHistory: Pair<Long, List<Pair<String, String>>>? = null

    fun readState(context: Context): WidgetQaState {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val turns = decodeTurns(prefs.getString(KEY_TURNS_JSON, "").orEmpty())
        val raw = WidgetQaState(
            question = prefs.getString(KEY_QUESTION, "").orEmpty(),
            answer = prefs.getString(KEY_ANSWER, "").orEmpty(),
            turns = turns,
            modelLabel = prefs.getString(KEY_MODEL, "").orEmpty(),
            isLoading = prefs.getBoolean(KEY_LOADING, false),
            isError = prefs.getBoolean(KEY_ERROR, false),
            loadingStartedAtMs = prefs.getLong(KEY_LOADING_STARTED_AT, 0L)
        )

        // Self-heal stuck "Thinking…" / "STREAMING…" state if process was killed/frozen or exceeded 35s
        if (raw.isLoading) {
            val hasLiveWork = activeJob?.isActive == true || activeServiceRunning
            val elapsed = if (raw.loadingStartedAtMs > 0L) {
                System.currentTimeMillis() - raw.loadingStartedAtMs
            } else {
                STALE_LOADING_RECOVERY_MS + 1L
            }
            if (!hasLiveWork || elapsed > STALE_LOADING_RECOVERY_MS) {
                val timeoutMsg = "Request timed out or paused by system. Tap RETRY or ASK to try again."
                val healedTurns = raw.turns.dropLastWhile {
                    it.role == "ASSISTANT" && (it.content == "Thinking…" || it.content == "Thinking...")
                } + WidgetTurn("ASSISTANT", timeoutMsg, isError = true)
                val healed = raw.copy(
                    answer = timeoutMsg,
                    turns = healedTurns,
                    modelLabel = "TIMEOUT",
                    isLoading = false,
                    isError = true,
                    loadingStartedAtMs = 0L
                )
                persistStateOnly(appContext, healed)
                return healed
            }
        }
        return raw
    }

    private fun persistStateOnly(context: Context, state: WidgetQaState) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_QUESTION, state.question)
            .putString(KEY_ANSWER, state.answer)
            .putString(KEY_TURNS_JSON, encodeTurns(state.turns))
            .putString(KEY_MODEL, state.modelLabel)
            .putBoolean(KEY_LOADING, state.isLoading)
            .putBoolean(KEY_ERROR, state.isError)
            .putLong(KEY_LOADING_STARTED_AT, state.loadingStartedAtMs)
            .apply()
    }

    fun saveState(context: Context, state: WidgetQaState) {
        persistStateOnly(context, state)
        AllChatWidgetProvider.updateAllWidgets(context.applicationContext)
    }

    fun clearState(context: Context) {
        activeRequestToken = 0L
        activeServiceRunning = false
        pendingPreparedTurns = null
        pendingPreparedHistory = null
        activeJob?.cancel()
        activeJob = null
        WidgetQuickAskService.stopIfRunning(context)
        saveState(context, WidgetQaState())
    }

    /**
     * Entry point used by [WidgetPromptActivity] and widget RETRY actions.
     * Launches [WidgetQuickAskService] (`shortService`) so the HTTP/DNS call runs with
     * foreground network priority even after the prompt overlay closes.
     */
    fun submitQuestion(context: Context, rawQuestion: String) {
        val question = rawQuestion.trim()
        if (question.isEmpty()) return
        WidgetQuickAskService.startForQuestion(context, question)
    }

    internal fun prepareQuestionState(context: Context, question: String): Long {
        val appContext = context.applicationContext
        val existingState = readState(appContext)

        // Keep valid prior turns; strip trailing error or stale placeholder turns so retries stay clean
        val cleanedPrior = existingState.turns
            .filter { it.content.isNotBlank() && it.content != "Thinking…" && it.content != "Thinking..." }
            .let { list ->
                // If the last turn was an error for the exact same question (RETRY), replace that failed turn cleanly
                if (list.size >= 2 &&
                    list.last().role == "ASSISTANT" &&
                    list.last().isError &&
                    list[list.size - 2].role == "USER" &&
                    list[list.size - 2].content.equals(question, ignoreCase = true)
                ) {
                    list.dropLast(2)
                } else {
                    list
                }
            }
            .takeLast(14)

        // Never send failed error banners into the LLM's conversation history
        val apiHistory = cleanedPrior
            .filter { !it.isError }
            .map { it.role to it.content }

        val turnsWithUser = cleanedPrior + WidgetTurn("USER", question)

        activeJob?.cancel()
        activeJob = null
        val now = System.currentTimeMillis()
        val requestToken = if (now <= activeRequestToken) activeRequestToken + 1L else now
        activeRequestToken = requestToken
        activeServiceRunning = true
        pendingPreparedTurns = requestToken to turnsWithUser
        pendingPreparedHistory = requestToken to apiHistory

        saveState(
            appContext,
            WidgetQaState(
                question = question,
                answer = "Thinking…",
                turns = turnsWithUser + WidgetTurn("ASSISTANT", "Thinking…"),
                modelLabel = "STREAMING",
                isLoading = true,
                isError = false,
                loadingStartedAtMs = now
            )
        )
        return requestToken
    }

    internal fun launchQuestionInScope(context: Context, question: String, preparedToken: Long) {
        val appContext = context.applicationContext
        val app = appContext as? AllChatApplication ?: return
        activeJob = app.applicationScope.launch {
            try {
                executePreparedQuestion(appContext, question, preparedToken)
            } finally {
                markTokenFinished(preparedToken)
            }
        }
    }

    internal suspend fun executePreparedQuestion(
        context: Context,
        question: String,
        requestToken: Long
    ) {
        val appContext = context.applicationContext
        val app = appContext as? AllChatApplication ?: return

        val turnsWithUser = pendingPreparedTurns
            ?.takeIf { it.first == requestToken }
            ?.second
            ?: listOf(WidgetTurn("USER", question))
        val apiHistory = pendingPreparedHistory
            ?.takeIf { it.first == requestToken }
            ?.second
            ?: emptyList()

        val startedAt = System.currentTimeMillis()
        try {
            val (modelLabel, finalAnswer) = withTimeout(HARD_TIMEOUT_MS) {
                app.repository.quickAskForWidget(
                    prompt = question,
                    history = apiHistory,
                    onPartialUpdate = { lbl, partialText ->
                        if (activeRequestToken == requestToken) {
                            saveState(
                                appContext,
                                WidgetQaState(
                                    question = question,
                                    answer = partialText,
                                    turns = turnsWithUser + WidgetTurn("ASSISTANT", partialText),
                                    modelLabel = lbl,
                                    isLoading = true,
                                    isError = false,
                                    loadingStartedAtMs = startedAt
                                )
                            )
                        }
                    }
                )
            }
            if (activeRequestToken == requestToken) {
                saveState(
                    appContext,
                    WidgetQaState(
                        question = question,
                        answer = finalAnswer,
                        turns = turnsWithUser + WidgetTurn("ASSISTANT", finalAnswer),
                        modelLabel = modelLabel,
                        isLoading = false,
                        isError = false,
                        loadingStartedAtMs = 0L
                    )
                )
            }
        } catch (tce: TimeoutCancellationException) {
            if (activeRequestToken == requestToken) {
                val msg = "Request timed out after 30s. Tap RETRY to try again."
                saveState(
                    appContext,
                    WidgetQaState(
                        question = question,
                        answer = msg,
                        turns = turnsWithUser + WidgetTurn("ASSISTANT", msg, isError = true),
                        modelLabel = "TIMEOUT",
                        isLoading = false,
                        isError = true,
                        loadingStartedAtMs = 0L
                    )
                )
            }
        } catch (ce: CancellationException) {
            // Canceled by Clear/Reset or newer question — never overwrite cleared state
            throw ce
        } catch (err: Throwable) {
            if (activeRequestToken == requestToken) {
                val errMsg = err.message ?: "Request failed. Check your provider & API key in Settings."
                saveState(
                    appContext,
                    WidgetQaState(
                        question = question,
                        answer = errMsg,
                        turns = turnsWithUser + WidgetTurn("ASSISTANT", errMsg, isError = true),
                        modelLabel = "ERROR",
                        isLoading = false,
                        isError = true,
                        loadingStartedAtMs = 0L
                    )
                )
            }
        }
    }

    internal fun isRequestActive(requestToken: Long): Boolean =
        activeRequestToken == requestToken

    internal fun markTokenFinished(requestToken: Long) {
        if (activeRequestToken == requestToken) {
            activeServiceRunning = false
        }
    }

    internal fun failActiveAsTimeout(context: Context) {
        val appContext = context.applicationContext
        activeJob?.cancel()
        activeJob = null
        activeServiceRunning = false
        activeRequestToken = 0L
        val current = readState(appContext)
        if (current.isLoading) {
            val msg = "Request timed out. Tap RETRY to try again."
            val cleanedTurns = current.turns.dropLastWhile {
                it.role == "ASSISTANT" && (it.content == "Thinking…" || it.content == "Thinking...")
            } + WidgetTurn("ASSISTANT", msg, isError = true)
            saveState(
                appContext,
                current.copy(
                    answer = msg,
                    turns = cleanedTurns,
                    modelLabel = "TIMEOUT",
                    isLoading = false,
                    isError = true,
                    loadingStartedAtMs = 0L
                )
            )
        }
    }

    private fun encodeTurns(turns: List<WidgetTurn>): String {
        val arr = JSONArray()
        turns.takeLast(16).forEach { turn ->
            arr.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.content)
                    .put("isError", turn.isError)
            )
        }
        return arr.toString()
    }

    private fun decodeTurns(raw: String): List<WidgetTurn> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val role = obj.optString("role")
                    val content = obj.optString("content")
                    val isErr = obj.optBoolean("isError", false) ||
                        (role == "ASSISTANT" && (
                            content.startsWith("Could not connect to ") ||
                                content.startsWith("Request timed out") ||
                                content.startsWith("Network error communicating")
                            ))
                    if (role.isNotBlank() && content.isNotBlank()) {
                        add(WidgetTurn(role, content, isError = isErr))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
