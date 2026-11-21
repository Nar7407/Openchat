package com.example.domain.model

import com.example.data.remote.WebSearchClient
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine

data class ToolExecutionOutcome(
    val toolName: String,
    val resultJson: String,
    val displaySummary: String,
    val inlineArtifactMarkdown: String? = null,
    val triggeredDeviceAction: DeviceAction? = null
)

/**
 * Zero-API Client-Side Utility Tool Pack for native Gemini, Anthropic, and OpenAI-compatible
 * Function / Tool Calling.
 */
object ClientToolEngine {

    private data class ToolSchema(
        val name: String,
        val description: String,
        val parameters: JSONObject
    )

    private fun buildSchemas(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "calculator",
            description = "Evaluates mathematical expressions with exact arithmetic and scientific functions (+, -, *, /, ^, %, parentheses, sqrt, sin, cos, tan, log, ln, abs, pow, min, max, round, floor, ceil, pi, e, factorial !). Use whenever accurate calculation is needed.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "expression",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "Mathematical expression to evaluate, e.g. '(1450 * 0.18) + sqrt(144)' or '2^16 - 1'")
                    )
                )
                .put("required", JSONArray().put("expression"))
        ),
        ToolSchema(
            name = "get_current_time",
            description = "Returns the current live date, time, day of week, timezone offset, and Unix timestamp.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "timezone",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "Optional IANA timezone ID such as 'UTC', 'America/New_York', 'Europe/London', 'Asia/Kolkata', 'Asia/Tokyo'. Defaults to device local timezone.")
                    )
                )
        ),
        ToolSchema(
            name = "timezone_convert",
            description = "Converts a date/time between two IANA timezones.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "datetime",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Date/time string such as '2026-05-10 14:30', '2026-05-10T14:30:00', or '14:30' (assumes today).")
                        )
                        .put(
                            "from_timezone",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Source IANA timezone ID, e.g. 'America/Los_Angeles' or 'UTC'.")
                        )
                        .put(
                            "to_timezone",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Target IANA timezone ID, e.g. 'Asia/Kolkata' or 'Europe/Berlin'.")
                        )
                )
                .put("required", JSONArray().put("datetime").put("from_timezone").put("to_timezone"))
        ),
        ToolSchema(
            name = "fetch_url",
            description = "Fetches and strips HTML from any public webpage URL into clean Markdown text for reading or analysis.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "url",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "Full HTTP or HTTPS URL to fetch.")
                    )
                )
                .put("required", JSONArray().put("url"))
        ),
        ToolSchema(
            name = "generate_chart",
            description = "Generates an interactive Chart.js data chart (bar, line, pie, doughnut, radar) or a Mermaid.js diagram rendered directly inside the user's chat view.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "chart_type",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "One of: 'bar', 'line', 'pie', 'doughnut', 'radar', or 'mermaid'.")
                        )
                        .put(
                            "title",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Concise chart or diagram title.")
                        )
                        .put(
                            "labels",
                            JSONObject()
                                .put("type", "array")
                                .put("items", JSONObject().put("type", "string"))
                                .put("description", "X-axis or category labels for Chart.js charts.")
                        )
                        .put(
                            "datasets",
                            JSONObject()
                                .put("type", "array")
                                .put(
                                    "items",
                                    JSONObject()
                                        .put("type", "object")
                                        .put(
                                            "properties",
                                            JSONObject()
                                                .put("label", JSONObject().put("type", "string"))
                                                .put(
                                                    "data",
                                                    JSONObject()
                                                        .put("type", "array")
                                                        .put("items", JSONObject().put("type", "number"))
                                                )
                                        )
                                )
                                .put("description", "Series datasets for Chart.js charts.")
                        )
                        .put(
                            "mermaid_code",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "Mermaid diagram syntax when chart_type is 'mermaid', e.g. 'graph TD; A[Client]-->B[API];'.")
                        )
                )
                .put("required", JSONArray().put("chart_type").put("title"))
        ),
        ToolSchema(
            name = "execute_device_action",
            description = "Triggers a native Android device action confirmation card to make a phone call (make_call), play/search a YouTube video (open_youtube), send a WhatsApp message to a contact or number (send_whatsapp), launch any installed Android app (open_app), set an alarm (set_alarm), or start a countdown timer (set_timer).",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "action_type",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "One of: 'make_call', 'open_youtube', 'send_whatsapp', 'open_app', 'set_alarm', 'set_timer'.")
                        )
                        .put(
                            "phone_number",
                            JSONObject().put("type", "string").put("description", "Phone number for make_call or send_whatsapp.")
                        )
                        .put(
                            "contact_name",
                            JSONObject().put("type", "string").put("description", "Contact name (e.g. 'Mom', 'Rahul') to resolve from saved Quick Contacts for make_call or send_whatsapp.")
                        )
                        .put(
                            "message",
                            JSONObject().put("type", "string").put("description", "Pre-filled message text for send_whatsapp.")
                        )
                        .put(
                            "youtube_query",
                            JSONObject().put("type", "string").put("description", "Song/video search query or direct YouTube URL for open_youtube.")
                        )
                        .put(
                            "app_name",
                            JSONObject().put("type", "string").put("description", "App name or Android package ID (e.g. 'WhatsApp', 'PhonePe', 'com.spotify.music') for open_app.")
                        )
                        .put(
                            "hour",
                            JSONObject().put("type", "integer").put("description", "Hour (0-23) for set_alarm.")
                        )
                        .put(
                            "minute",
                            JSONObject().put("type", "integer").put("description", "Minute (0-59) for set_alarm.")
                        )
                        .put(
                            "duration_seconds",
                            JSONObject().put("type", "integer").put("description", "Duration in seconds for set_timer.")
                        )
                        .put(
                            "label",
                            JSONObject().put("type", "string").put("description", "Optional label for alarm, timer, or contact.")
                        )
                )
                .put("required", JSONArray().put("action_type"))
        )
    )

    fun toOpenAiToolsJson(): JSONArray {
        val arr = JSONArray()
        buildSchemas().forEach { schema ->
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", schema.name)
                            .put("description", schema.description)
                            .put("parameters", schema.parameters)
                    )
            )
        }
        return arr
    }

    fun toGeminiFunctionDeclarationsJson(): JSONArray {
        val arr = JSONArray()
        buildSchemas().forEach { schema ->
            arr.put(
                JSONObject()
                    .put("name", schema.name)
                    .put("description", schema.description)
                    .put("parameters", schema.parameters)
            )
        }
        return arr
    }

    fun toAnthropicToolsJson(): JSONArray {
        val arr = JSONArray()
        buildSchemas().forEach { schema ->
            arr.put(
                JSONObject()
                    .put("name", schema.name)
                    .put("description", schema.description)
                    .put("input_schema", schema.parameters)
            )
        }
        return arr
    }

    suspend fun executeTool(
        name: String,
        argumentsJson: String,
        webSearchClient: WebSearchClient,
        contacts: List<QuickContact> = emptyList(),
        customApps: List<CustomAppEntry> = emptyList()
    ): ToolExecutionOutcome {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        return when (name.trim().lowercase()) {
            "calculator" -> runCalculator(args)
            "get_current_time" -> runCurrentTime(args)
            "timezone_convert" -> runTimezoneConvert(args)
            "fetch_url" -> runFetchUrl(args, webSearchClient)
            "generate_chart" -> runGenerateChart(args)
            "execute_device_action", "device_action" -> runDeviceAction(args, contacts, customApps)
            else -> ToolExecutionOutcome(
                toolName = name,
                resultJson = JSONObject().put("error", "Unknown client tool: $name").toString(),
                displaySummary = "TOOL // Unknown tool `$name`"
            )
        }
    }

    private fun runDeviceAction(
        args: JSONObject,
        contacts: List<QuickContact>,
        customApps: List<CustomAppEntry>
    ): ToolExecutionOutcome {
        val type = args.optString("action_type").trim().lowercase(Locale.US)
        val label = args.optString("label").trim()
        val contactName = args.optString("contact_name").trim().ifBlank { label }
        val rawPhone = args.optString("phone_number").trim()

        val resolvedContact = if (rawPhone.isBlank() && contactName.isNotBlank()) {
            DeviceActionEngine.resolveContact(contactName, contacts)
        } else null

        val action: DeviceAction? = when (type) {
            "make_call", "dial_phone", "call" -> {
                val number = when {
                    rawPhone.isNotBlank() -> DeviceActionEngine.normalizePhoneNumber(rawPhone)
                    resolvedContact != null -> DeviceActionEngine.normalizePhoneNumber(resolvedContact.phoneNumber)
                    else -> ""
                }
                DeviceAction.DialPhone(
                    phoneNumber = number,
                    contactLabel = resolvedContact?.name ?: contactName.takeIf { it.isNotBlank() }
                )
            }
            "open_youtube", "youtube", "play_youtube" -> {
                val query = args.optString("youtube_query").trim()
                    .ifBlank { args.optString("query").trim() }
                    .ifBlank { label }
                if (query.isNotBlank()) {
                    DeviceAction.OpenYouTube(
                        queryOrUrl = query,
                        videoId = DeviceActionEngine.extractYouTubeVideoId(query)
                    )
                } else {
                    DeviceActionEngine.resolveAppEntry("YouTube", customApps)
                }
            }
            "send_whatsapp", "whatsapp" -> {
                val digits = when {
                    rawPhone.isNotBlank() -> DeviceActionEngine.normalizeWhatsAppDigits(rawPhone)
                    resolvedContact != null -> DeviceActionEngine.normalizeWhatsAppDigits(resolvedContact.phoneNumber)
                    else -> ""
                }
                val msg = args.optString("message").trim()
                DeviceAction.SendWhatsApp(
                    phoneNumber = digits,
                    message = msg,
                    contactLabel = resolvedContact?.name ?: contactName.takeIf { it.isNotBlank() }
                )
            }
            "set_alarm" -> DeviceAction.SetAlarm(
                hour = args.optInt("hour", 7),
                minute = args.optInt("minute", 0),
                label = label.takeIf { it.isNotBlank() }
            )
            "set_timer" -> DeviceAction.SetTimer(
                seconds = args.optInt("duration_seconds", 60).coerceAtLeast(1),
                label = label.takeIf { it.isNotBlank() }
            )
            "open_app" -> {
                val appQuery = args.optString("app_name").trim().ifBlank { label }
                DeviceActionEngine.resolveAppEntry(appQuery, customApps)
                    ?: DeviceAction.OpenApp(appName = appQuery.ifBlank { "App" }, packageName = appQuery.takeIf { it.contains(".") })
            }
            else -> null
        }

        val summary = action?.let { "${it.badge} // ${it.title}" } ?: "ACTION // Prepared device intent"
        return ToolExecutionOutcome(
            toolName = "execute_device_action",
            resultJson = JSONObject()
                .put("status", "action_card_ready")
                .put("action_type", type)
                .put("title", action?.title.orEmpty())
                .put("resolved_phone", resolvedContact?.phoneNumber ?: rawPhone)
                .toString(),
            displaySummary = summary,
            triggeredDeviceAction = action
        )
    }

    private fun runCalculator(args: JSONObject): ToolExecutionOutcome {
        val expr = args.optString("expression").trim()
        if (expr.isEmpty()) {
            return ToolExecutionOutcome(
                toolName = "calculator",
                resultJson = JSONObject().put("error", "Missing expression").toString(),
                displaySummary = "CALC // Missing expression"
            )
        }
        return runCatching {
            val value = MathExpressionEvaluator.evaluate(expr)
            val formatted = formatMathResult(value)
            ToolExecutionOutcome(
                toolName = "calculator",
                resultJson = JSONObject()
                    .put("expression", expr)
                    .put("result", formatted)
                    .put("numeric", value)
                    .toString(),
                displaySummary = "CALC // $expr = $formatted"
            )
        }.getOrElse { err ->
            ToolExecutionOutcome(
                toolName = "calculator",
                resultJson = JSONObject().put("expression", expr).put("error", err.message ?: "Invalid expression").toString(),
                displaySummary = "CALC // Error evaluating `$expr`"
            )
        }
    }

    private fun runCurrentTime(args: JSONObject): ToolExecutionOutcome {
        val tzRaw = args.optString("timezone").trim()
        val zone = if (tzRaw.isBlank()) {
            ZoneId.systemDefault()
        } else {
            runCatching { resolveZone(tzRaw) }.getOrDefault(ZoneId.systemDefault())
        }
        val now = ZonedDateTime.now(zone)
        val formatted = now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm:ss z (XXX)", Locale.US))
        val res = JSONObject()
            .put("timezone", zone.id)
            .put("iso_8601", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("formatted", formatted)
            .put("day_of_week", now.dayOfWeek.name)
            .put("unix_epoch_seconds", now.toEpochSecond())
        return ToolExecutionOutcome(
            toolName = "get_current_time",
            resultJson = res.toString(),
            displaySummary = "TIME // $formatted"
        )
    }

    private fun runTimezoneConvert(args: JSONObject): ToolExecutionOutcome {
        val dtRaw = args.optString("datetime").trim()
        val fromTz = args.optString("from_timezone").trim().ifBlank { ZoneId.systemDefault().id }
        val toTz = args.optString("to_timezone").trim().ifBlank { "UTC" }
        return runCatching {
            val srcZone = resolveZone(fromTz)
            val dstZone = resolveZone(toTz)
            val srcZdt = parseFlexibleDateTime(dtRaw, srcZone)
            val converted = srcZdt.withZoneSameInstant(dstZone)
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z (XXX)", Locale.US)
            val out = JSONObject()
                .put("source", srcZdt.format(fmt))
                .put("converted", converted.format(fmt))
                .put("iso_8601", converted.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .put("from_timezone", srcZone.id)
                .put("to_timezone", dstZone.id)
            ToolExecutionOutcome(
                toolName = "timezone_convert",
                resultJson = out.toString(),
                displaySummary = "TZ // ${srcZdt.format(DateTimeFormatter.ofPattern("HH:mm z"))} -> ${converted.format(DateTimeFormatter.ofPattern("HH:mm z"))}"
            )
        }.getOrElse { err ->
            ToolExecutionOutcome(
                toolName = "timezone_convert",
                resultJson = JSONObject().put("error", err.message ?: "Timezone conversion failed").toString(),
                displaySummary = "TZ // Conversion failed"
            )
        }
    }

    private suspend fun runFetchUrl(
        args: JSONObject,
        webSearchClient: WebSearchClient
    ): ToolExecutionOutcome {
        val url = args.optString("url").trim()
        if (url.isEmpty()) {
            return ToolExecutionOutcome(
                toolName = "fetch_url",
                resultJson = JSONObject().put("error", "Missing url").toString(),
                displaySummary = "FETCH // Missing URL"
            )
        }
        return runCatching {
            val scraped = webSearchClient.fetchUrlAsMarkdown(url)
            val trimmed = scraped.markdown.take(5500)
            ToolExecutionOutcome(
                toolName = "fetch_url",
                resultJson = JSONObject()
                    .put("url", scraped.url)
                    .put("title", scraped.title)
                    .put("markdown", trimmed)
                    .toString(),
                displaySummary = "FETCH // Read ${scraped.title.take(36)}"
            )
        }.getOrElse { err ->
            ToolExecutionOutcome(
                toolName = "fetch_url",
                resultJson = JSONObject().put("url", url).put("error", err.message ?: "Failed to fetch URL").toString(),
                displaySummary = "FETCH // Failed $url"
            )
        }
    }

    private fun runGenerateChart(args: JSONObject): ToolExecutionOutcome {
        val type = args.optString("chart_type", "bar").trim().lowercase()
        val title = args.optString("title", "Data Visualization").trim()
        return if (type == "mermaid") {
            val code = args.optString("mermaid_code").trim()
                .ifBlank { "graph TD\n  A[$title] --> B[Ready]" }
            val md = "```mermaid\n$code\n```"
            ToolExecutionOutcome(
                toolName = "generate_chart",
                resultJson = JSONObject().put("status", "rendered").put("type", "mermaid").put("title", title).toString(),
                displaySummary = "DIAGRAM // Mermaid: $title",
                inlineArtifactMarkdown = md
            )
        } else {
            val labels = args.optJSONArray("labels") ?: JSONArray()
            val datasets = args.optJSONArray("datasets") ?: JSONArray()
            val spec = JSONObject()
                .put("type", if (type in listOf("bar", "line", "pie", "doughnut", "radar")) type else "bar")
                .put("title", title)
                .put(
                    "data",
                    JSONObject()
                        .put("labels", labels)
                        .put("datasets", datasets)
                )
            val md = "```chartjs\n${spec.toString(2)}\n```"
            ToolExecutionOutcome(
                toolName = "generate_chart",
                resultJson = JSONObject().put("status", "rendered").put("type", type).put("title", title).toString(),
                displaySummary = "CHART // ${type.uppercase()}: $title",
                inlineArtifactMarkdown = md
            )
        }
    }

    private fun resolveZone(raw: String): ZoneId {
        val aliases = mapOf(
            "IST" to "Asia/Kolkata",
            "PST" to "America/Los_Angeles",
            "PDT" to "America/Los_Angeles",
            "EST" to "America/New_York",
            "EDT" to "America/New_York",
            "CST" to "America/Chicago",
            "MST" to "America/Denver",
            "GMT" to "UTC",
            "BST" to "Europe/London",
            "CET" to "Europe/Paris",
            "JST" to "Asia/Tokyo",
            "AEST" to "Australia/Sydney",
            "SGT" to "Asia/Singapore"
        )
        val mapped = aliases[raw.uppercase(Locale.US)] ?: raw
        return ZoneId.of(mapped)
    }

    private fun parseFlexibleDateTime(raw: String, zone: ZoneId): ZonedDateTime {
        if (raw.isBlank() || raw.equals("now", ignoreCase = true)) {
            return ZonedDateTime.now(zone)
        }
        runCatching { return ZonedDateTime.parse(raw) }
        runCatching { return Instant.parse(raw).atZone(zone) }
        val normalized = raw.replace(' ', 'T')
        runCatching { return LocalDateTime.parse(normalized).atZone(zone) }
        runCatching {
            val time = LocalTime.parse(raw)
            return LocalDateTime.of(LocalDate.now(zone), time).atZone(zone)
        }
        val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "HH:mm", "h:mm a")
        for (p in patterns) {
            runCatching {
                val fmt = DateTimeFormatter.ofPattern(p, Locale.US)
                if (p.startsWith("yyyy")) {
                    return LocalDateTime.parse(raw, fmt).atZone(zone)
                } else {
                    return LocalDateTime.of(LocalDate.now(zone), LocalTime.parse(raw, fmt)).atZone(zone)
                }
            }
        }
        return ZonedDateTime.now(zone)
    }

    private fun formatMathResult(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        val roundedLong = value.toLong()
        if (abs(value - roundedLong.toDouble()) < 1e-12 && abs(value) < 1e15) {
            return roundedLong.toString()
        }
        return runCatching {
            BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }.getOrDefault(value.toString())
    }
}

/**
 * Self-contained recursive-descent scientific math evaluator (zero dependencies).
 */
internal object MathExpressionEvaluator {
    fun evaluate(input: String): Double {
        val parser = Parser(input.replace(",", "").replace("×", "*").replace("÷", "/").replace("−", "-"))
        val value = parser.parseExpression()
        parser.skipWhitespace()
        if (parser.pos < parser.src.length) {
            throw IllegalArgumentException("Unexpected character '${parser.src[parser.pos]}' at index ${parser.pos}")
        }
        return value
    }

    private class Parser(val src: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun match(c: Char): Boolean {
            skipWhitespace()
            if (pos < src.length && src[pos] == c) {
                pos++
                return true
            }
            return false
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    match('+') -> x + parseTerm()
                    match('-') -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                x = when {
                    match('*') -> x * parsePower()
                    match('/') -> {
                        val d = parsePower()
                        if (d == 0.0) throw ArithmeticException("Division by zero")
                        x / d
                    }
                    match('%') -> x % parsePower()
                    else -> return x
                }
            }
        }

        private fun parsePower(): Double {
            var x = parseUnary()
            if (match('^')) {
                x = x.pow(parsePower())
            }
            return x
        }

        private fun parseUnary(): Double {
            if (match('+')) return parseUnary()
            if (match('-')) return -parseUnary()
            var x = parsePrimary()
            while (match('!')) {
                val n = x.toInt()
                if (n < 0 || n > 170) throw IllegalArgumentException("Factorial out of range: $x")
                var acc = 1.0
                for (i in 2..n) acc *= i
                x = acc
            }
            return x
        }

        private fun parsePrimary(): Double {
            skipWhitespace()
            if (match('(')) {
                val x = parseExpression()
                if (!match(')')) throw IllegalArgumentException("Missing closing ')'")
                return x
            }
            val start = pos
            if (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) {
                while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
                if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                    pos++
                    if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                    while (pos < src.length && src[pos].isDigit()) pos++
                }
                return src.substring(start, pos).toDouble()
            }
            if (pos < src.length && src[pos].isLetter()) {
                while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
                val ident = src.substring(start, pos).lowercase(Locale.US)
                if (ident == "pi") return PI
                if (ident == "e") return kotlin.math.E
                if (!match('(')) throw IllegalArgumentException("Unknown identifier '$ident'")
                val args = mutableListOf<Double>()
                if (!match(')')) {
                    do {
                        args += parseExpression()
                    } while (match(','))
                    if (!match(')')) throw IllegalArgumentException("Missing ')' after $ident(...)")
                }
                return applyFunction(ident, args)
            }
            throw IllegalArgumentException("Expected number or expression at index $pos")
        }

        private fun applyFunction(name: String, args: List<Double>): Double {
            fun a0() = args.getOrElse(0) { throw IllegalArgumentException("$name requires an argument") }
            fun a1() = args.getOrElse(1) { throw IllegalArgumentException("$name requires 2 arguments") }
            return when (name) {
                "sqrt" -> sqrt(a0())
                "abs" -> abs(a0())
                "sin" -> sin(a0())
                "cos" -> cos(a0())
                "tan" -> tan(a0())
                "asin" -> asin(a0())
                "acos" -> acos(a0())
                "atan" -> atan(a0())
                "ln" -> ln(a0())
                "log", "log10" -> log10(a0())
                "exp" -> exp(a0())
                "ceil" -> ceil(a0())
                "floor" -> floor(a0())
                "round" -> round(a0())
                "pow" -> a0().pow(a1())
                "min" -> args.minOrNull() ?: 0.0
                "max" -> args.maxOrNull() ?: 0.0
                else -> throw IllegalArgumentException("Unknown function '$name'")
            }
        }
    }
}
