package com.example.widget

import android.content.Context
import com.example.AllChatApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class WidgetTurn(
    val role: String, // "USER" or "ASSISTANT"
    val content: String
)

data class WidgetQaState(
    val question: String = "",
    val answer: String = "",
    val turns: List<WidgetTurn> = emptyList(),
    val modelLabel: String = "",
    val isLoading: Boolean = false,
    val isError: Boolean = false
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

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeRequestToken: Long = 0L

    fun readState(context: Context): WidgetQaState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val turns = decodeTurns(prefs.getString(KEY_TURNS_JSON, "").orEmpty())
        return WidgetQaState(
            question = prefs.getString(KEY_QUESTION, "").orEmpty(),
            answer = prefs.getString(KEY_ANSWER, "").orEmpty(),
            turns = turns,
            modelLabel = prefs.getString(KEY_MODEL, "").orEmpty(),
            isLoading = prefs.getBoolean(KEY_LOADING, false),
            isError = prefs.getBoolean(KEY_ERROR, false)
        )
    }

    fun saveState(context: Context, state: WidgetQaState) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_QUESTION, state.question)
            .putString(KEY_ANSWER, state.answer)
            .putString(KEY_TURNS_JSON, encodeTurns(state.turns))
            .putString(KEY_MODEL, state.modelLabel)
            .putBoolean(KEY_LOADING, state.isLoading)
            .putBoolean(KEY_ERROR, state.isError)
            .apply()
        AllChatWidgetProvider.updateAllWidgets(context.applicationContext)
    }

    fun clearState(context: Context) {
        activeRequestToken = 0L
        activeJob?.cancel()
        activeJob = null
        saveState(context, WidgetQaState())
    }

    fun submitQuestion(context: Context, rawQuestion: String) {
        val question = rawQuestion.trim()
        if (question.isEmpty()) return

        val appContext = context.applicationContext
        val app = appContext as? AllChatApplication ?: return

        // Preserve existing multi-turn history instead of replacing it
        val priorTurns = readState(appContext).turns
            .filter { it.content.isNotBlank() && it.content != "Thinking…" }
            .takeLast(14)

        val apiHistory = priorTurns.map { it.role to it.content }
        val turnsWithUser = priorTurns + WidgetTurn("USER", question)

        activeJob?.cancel()
        val requestToken = System.currentTimeMillis()
        activeRequestToken = requestToken
        saveState(
            appContext,
            WidgetQaState(
                question = question,
                answer = "Thinking…",
                turns = turnsWithUser + WidgetTurn("ASSISTANT", "Thinking…"),
                modelLabel = "STREAMING",
                isLoading = true,
                isError = false
            )
        )

        activeJob = app.applicationScope.launch {
            try {
                val (modelLabel, finalAnswer) = app.repository.quickAskForWidget(
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
                                    isError = false
                                )
                            )
                        }
                    }
                )
                if (activeRequestToken == requestToken) {
                    saveState(
                        appContext,
                        WidgetQaState(
                            question = question,
                            answer = finalAnswer,
                            turns = turnsWithUser + WidgetTurn("ASSISTANT", finalAnswer),
                            modelLabel = modelLabel,
                            isLoading = false,
                            isError = false
                        )
                    )
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
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
                            turns = turnsWithUser + WidgetTurn("ASSISTANT", errMsg),
                            modelLabel = "ERROR",
                            isLoading = false,
                            isError = true
                        )
                    )
                }
            }
        }
    }

    private fun encodeTurns(turns: List<WidgetTurn>): String {
        val arr = JSONArray()
        turns.takeLast(16).forEach { turn ->
            arr.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.content)
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
                    if (role.isNotBlank() && content.isNotBlank()) {
                        add(WidgetTurn(role, content))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
