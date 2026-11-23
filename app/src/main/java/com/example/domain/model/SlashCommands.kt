package com.example.domain.model

enum class SlashCommandCategory(val label: String) {
    SESSION("SESSION"),
    PROVIDERS("MODELS & TOOLS"),
    INTERFACE("INTERFACE & APP")
}

enum class SlashCommandId {
    NEW,
    SESSIONS,
    COMPACT,
    UNDO,
    REDO,
    SHARE,
    UNSHARE,
    EXPORT,
    CONNECT,
    MODELS,
    MCP,
    INIT,
    HELP,
    THEMES,
    DETAILS,
    THINKING,
    EDITOR,
    COMMANDS,
    REMEMBER,
    MEMORY,
    NOTE,
    NOTES,
    ROUTINES,
    QR,
    PLOT,
    COMPARE,
    CANVAS,
    EXPENSES,
    WATCH,
    BLE,
    COMMUTE,
    INTERPRET,
    SPOTLIGHT,
    REDACT,
    CMD,
    FORM,
    GHOST,
    EXIT
}

data class SlashCommandDef(
    val id: SlashCommandId,
    val primary: String,
    val aliases: List<String> = emptyList(),
    val category: SlashCommandCategory,
    val summary: String
) {
    val allTriggers: List<String> get() = listOf(primary) + aliases
}

object SlashCommandRegistry {
    val all: List<SlashCommandDef> = listOf(
        SlashCommandDef(
            id = SlashCommandId.NEW,
            primary = "/new",
            aliases = listOf("/clear"),
            category = SlashCommandCategory.SESSION,
            summary = "Starts a fresh session while saving the current one to your history."
        ),
        SlashCommandDef(
            id = SlashCommandId.SESSIONS,
            primary = "/sessions",
            aliases = listOf("/resume", "/continue"),
            category = SlashCommandCategory.SESSION,
            summary = "Lists previous conversations so you can switch between them."
        ),
        SlashCommandDef(
            id = SlashCommandId.COMPACT,
            primary = "/compact",
            aliases = listOf("/summarize"),
            category = SlashCommandCategory.SESSION,
            summary = "Summarizes the current conversation to free up context window space."
        ),
        SlashCommandDef(
            id = SlashCommandId.UNDO,
            primary = "/undo",
            category = SlashCommandCategory.SESSION,
            summary = "Reverts your last message turn. Run repeatedly to step further back."
        ),
        SlashCommandDef(
            id = SlashCommandId.REDO,
            primary = "/redo",
            category = SlashCommandCategory.SESSION,
            summary = "Restores a message turn after running /undo."
        ),
        SlashCommandDef(
            id = SlashCommandId.SHARE,
            primary = "/share",
            category = SlashCommandCategory.SESSION,
            summary = "Generates a share link (opncd.ai/s/<id>) & exports a shareable snapshot."
        ),
        SlashCommandDef(
            id = SlashCommandId.UNSHARE,
            primary = "/unshare",
            category = SlashCommandCategory.SESSION,
            summary = "Revokes shared access and deletes the shared session link."
        ),
        SlashCommandDef(
            id = SlashCommandId.EXPORT,
            primary = "/export",
            category = SlashCommandCategory.SESSION,
            summary = "Exports the current session transcript to a Markdown (.md) file."
        ),
        SlashCommandDef(
            id = SlashCommandId.CONNECT,
            primary = "/connect",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Adds an AI model provider and configures its API key."
        ),
        SlashCommandDef(
            id = SlashCommandId.MODELS,
            primary = "/models",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens the model picker to switch active models."
        ),
        SlashCommandDef(
            id = SlashCommandId.MCP,
            primary = "/mcp",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Lists configured Model Context Protocol (MCP) servers & checks status."
        ),
        SlashCommandDef(
            id = SlashCommandId.INIT,
            primary = "/init",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Creates or updates a project-level AGENTS.md instruction file."
        ),
        SlashCommandDef(
            id = SlashCommandId.HELP,
            primary = "/help",
            category = SlashCommandCategory.INTERFACE,
            summary = "Opens the help dialog showing all available commands and aliases."
        ),
        SlashCommandDef(
            id = SlashCommandId.THEMES,
            primary = "/themes",
            category = SlashCommandCategory.INTERFACE,
            summary = "Lists and switches UI 3-color themes live."
        ),
        SlashCommandDef(
            id = SlashCommandId.DETAILS,
            primary = "/details",
            category = SlashCommandCategory.INTERFACE,
            summary = "Toggles inline execution details (tokens, cost, model metadata)."
        ),
        SlashCommandDef(
            id = SlashCommandId.THINKING,
            primary = "/thinking",
            category = SlashCommandCategory.INTERFACE,
            summary = "Toggles visibility of the model's reasoning (<think>) blocks."
        ),
        SlashCommandDef(
            id = SlashCommandId.EDITOR,
            primary = "/editor",
            category = SlashCommandCategory.INTERFACE,
            summary = "Opens the full-screen prompt editor to draft longer prompts."
        ),
        SlashCommandDef(
            id = SlashCommandId.COMMANDS,
            primary = "/commands",
            aliases = listOf("/templates"),
            category = SlashCommandCategory.INTERFACE,
            summary = "Creates, edits, or deletes your custom /slash prompt templates."
        ),
        SlashCommandDef(
            id = SlashCommandId.REMEMBER,
            primary = "/remember",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Saves a persistent fact/preference to your Memory Bank (e.g. /remember I code in Kotlin)."
        ),
        SlashCommandDef(
            id = SlashCommandId.MEMORY,
            primary = "/memory",
            aliases = listOf("/memories"),
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens the Memory Bank manager to view, edit, toggle, or delete saved facts."
        ),
        SlashCommandDef(
            id = SlashCommandId.NOTE,
            primary = "/note",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Cleans up dictated text or a memo into a structured Markdown note in your Voice Notes Inbox."
        ),
        SlashCommandDef(
            id = SlashCommandId.NOTES,
            primary = "/notes",
            aliases = listOf("/inbox"),
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens your Voice Quick-Notes Inbox to search, copy, edit, or delete saved notes."
        ),
        SlashCommandDef(
            id = SlashCommandId.ROUTINES,
            primary = "/routine",
            aliases = listOf("/routines"),
            category = SlashCommandCategory.PROVIDERS,
            summary = "Runs a saved 1-tap Assistant Routine by name/phrase or opens Routines in Settings."
        ),
        SlashCommandDef(
            id = SlashCommandId.QR,
            primary = "/qr",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Generates an instant scannable QR code locally (e.g. /qr https://example.com)."
        ),
        SlashCommandDef(
            id = SlashCommandId.PLOT,
            primary = "/plot",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens the Interactive Desmos-Style Function Plotter (e.g. /plot a*exp(-b*x)*sin(c*x))."
        ),
        SlashCommandDef(
            id = SlashCommandId.COMPARE,
            primary = "/compare",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Builds a Weighted Decision Matrix Card with live sliders (e.g. /compare MacBook vs ThinkPad)."
        ),
        SlashCommandDef(
            id = SlashCommandId.CANVAS,
            primary = "/canvas",
            aliases = listOf("/scratchpad"),
            category = SlashCommandCategory.INTERFACE,
            summary = "Opens the Interactive Split-Editor Canvas with Highlight-to-Edit & Version History."
        ),
        SlashCommandDef(
            id = SlashCommandId.EXPENSES,
            primary = "/expenses",
            aliases = listOf("/receipt"),
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens your private Receipt & Monthly Expense Tracker with 1-tap .xlsx export."
        ),
        SlashCommandDef(
            id = SlashCommandId.WATCH,
            primary = "/watch",
            aliases = listOf("/watchlist"),
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens the Web Page & Topic Change Watcher to track what's new."
        ),
        SlashCommandDef(
            id = SlashCommandId.BLE,
            primary = "/ble",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens the Bluetooth LE Smart-Device & ESP32/Arduino Sensor Inspector."
        ),
        SlashCommandDef(
            id = SlashCommandId.COMMUTE,
            primary = "/commute",
            aliases = listOf("/hud"),
            category = SlashCommandCategory.INTERFACE,
            summary = "Launches the high-contrast Hands-Free Commute / Driving HUD with Earbud Tap-to-Talk."
        ),
        SlashCommandDef(
            id = SlashCommandId.INTERPRET,
            primary = "/interpret",
            aliases = listOf("/interpreter"),
            category = SlashCommandCategory.INTERFACE,
            summary = "Launches Two-Person Face-to-Face Split-Screen Live Voice Interpreter."
        ),
        SlashCommandDef(
            id = SlashCommandId.SPOTLIGHT,
            primary = "/spotlight",
            aliases = listOf("/find"),
            category = SlashCommandCategory.SESSION,
            summary = "Global Spotlight Search across all chats, code, notes, docs, expenses + Visual Topic Map."
        ),
        SlashCommandDef(
            id = SlashCommandId.REDACT,
            primary = "/redact",
            category = SlashCommandCategory.PROVIDERS,
            summary = "Opens the local Privacy-First PII Redactor (masks phones, Aadhaar, PAN, emails, UPI)."
        ),
        SlashCommandDef(
            id = SlashCommandId.CMD,
            primary = "/cmd",
            aliases = listOf("/cli", "/shell"),
            category = SlashCommandCategory.PROVIDERS,
            summary = "Interactive CLI Command Builder for FFmpeg, ADB, Git, Bash & PowerShell with tappable flag tooltips."
        ),
        SlashCommandDef(
            id = SlashCommandId.FORM,
            primary = "/form",
            aliases = listOf("/log", "/table"),
            category = SlashCommandCategory.PROVIDERS,
            summary = "Voice-to-Structured-Form & Instant Table Filler (Standup, Bug Report, Workout, Lab Log)."
        ),
        SlashCommandDef(
            id = SlashCommandId.GHOST,
            primary = "/ghost",
            aliases = listOf("/incognito"),
            category = SlashCommandCategory.SESSION,
            summary = "Toggles Incognito Ghost Chat mode (nothing written to local history or search index)."
        ),
        SlashCommandDef(
            id = SlashCommandId.EXIT,
            primary = "/exit",
            aliases = listOf("/quit", "/q"),
            category = SlashCommandCategory.INTERFACE,
            summary = "Closes the app."
        )
    )

    fun matchExact(rawInput: String): SlashCommandDef? {
        val token = rawInput.trim().takeWhile { !it.isWhitespace() }.lowercase()
        if (!token.startsWith("/")) return null
        return all.firstOrNull { def -> def.allTriggers.any { it.equals(token, ignoreCase = true) } }
    }

    fun filterSuggestions(rawInput: String): List<SlashCommandDef> {
        val token = rawInput.trim().takeWhile { !it.isWhitespace() }.lowercase()
        if (!token.startsWith("/")) return emptyList()
        if (token == "/") return all
        return all.filter { def ->
            def.allTriggers.any { it.startsWith(token, ignoreCase = true) } ||
                def.summary.contains(token.removePrefix("/"), ignoreCase = true)
        }
    }
}

data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val authToken: String = "",
    val enabled: Boolean = true
)

data class McpCheckResult(
    val serverId: String,
    val isOnline: Boolean,
    val latencyMs: Long,
    val statusDetail: String
)
