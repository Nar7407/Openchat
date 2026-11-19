package com.example.widget

import android.content.Context
import com.example.AllChatApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class WidgetQaState(
    val question: String = "",
    val answer: String = "",
    val modelLabel: String = "",
    val isLoading: Boolean = false,
    val isError: Boolean = false
) {
    val hasContent: Boolean get() = question.isNotBlank() || answer.isNotBlank() || isLoading
}

object WidgetQuickAskRunner {

    private const val PREFS_NAME = "allchat_widget_state"
    private const val KEY_QUESTION = "question"
    private const val KEY_ANSWER = "answer"
    private const val KEY_MODEL = "model_label"
    private const val KEY_LOADING = "is_loading"
    private const val KEY_ERROR = "is_error"

    @Volatile
    private var activeJob: Job? = null

    fun readState(context: Context): WidgetQaState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetQaState(
            question = prefs.getString(KEY_QUESTION, "").orEmpty(),
            answer = prefs.getString(KEY_ANSWER, "").orEmpty(),
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
            .putString(KEY_MODEL, state.modelLabel)
            .putBoolean(KEY_LOADING, state.isLoading)
            .putBoolean(KEY_ERROR, state.isError)
            .apply()
        AllChatWidgetProvider.updateAllWidgets(context.applicationContext)
    }

    fun clearState(context: Context) {
        activeJob?.cancel()
        activeJob = null
        saveState(context, WidgetQaState())
    }

    fun submitQuestion(context: Context, rawQuestion: String) {
        val question = rawQuestion.trim()
        if (question.isEmpty()) return

        val appContext = context.applicationContext
        val app = appContext as? AllChatApplication ?: return

        activeJob?.cancel()
        saveState(
            appContext,
            WidgetQaState(
                question = question,
                answer = "Thinking…",
                modelLabel = "STREAMING",
                isLoading = true,
                isError = false
            )
        )

        activeJob = app.applicationScope.launch {
            runCatching {
                app.repository.quickAskForWidget(
                    prompt = question,
                    onPartialUpdate = { modelLabel, partialText ->
                        saveState(
                            appContext,
                            WidgetQaState(
                                question = question,
                                answer = partialText,
                                modelLabel = modelLabel,
                                isLoading = true,
                                isError = false
                            )
                        )
                    }
                )
            }.onSuccess { (modelLabel, finalAnswer) ->
                saveState(
                    appContext,
                    WidgetQaState(
                        question = question,
                        answer = finalAnswer,
                        modelLabel = modelLabel,
                        isLoading = false,
                        isError = false
                    )
                )
            }.onFailure { err ->
                saveState(
                    appContext,
                    WidgetQaState(
                        question = question,
                        answer = err.message ?: "Request failed. Check your provider & API key in Settings.",
                        modelLabel = "ERROR",
                        isLoading = false,
                        isError = true
                    )
                )
            }
        }
    }
}
