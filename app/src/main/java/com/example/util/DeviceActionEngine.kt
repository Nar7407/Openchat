package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.QuickContact
import java.net.URLEncoder
import java.util.Locale

sealed interface DeviceAction {
    val badge: String
    val title: String
    val subtitle: String

    data class DialPhone(
        val phoneNumber: String,
        val contactLabel: String? = null
    ) : DeviceAction {
        override val badge: String = "PHONE // CALL"
        override val title: String = when {
            !contactLabel.isNullOrBlank() && phoneNumber.isNotBlank() -> "Call $contactLabel ($phoneNumber)"
            !contactLabel.isNullOrBlank() -> "Call $contactLabel"
            phoneNumber.isNotBlank() -> "Dial $phoneNumber"
            else -> "Open Phone Dialer"
        }
        override val subtitle: String = if (phoneNumber.isNotBlank()) "tel:$phoneNumber" else "Pick a Quick Contact or type a number"
    }

    data class OpenYouTube(
        val queryOrUrl: String,
        val videoId: String? = null
    ) : DeviceAction {
        override val badge: String = "YOUTUBE // PLAY"
        override val title: String = if (!videoId.isNullOrBlank()) {
            "Play YouTube Video ($videoId)"
        } else {
            "Play on YouTube: ${queryOrUrl.take(42)}"
        }
        override val subtitle: String = if (!videoId.isNullOrBlank()) {
            "https://youtu.be/$videoId"
        } else {
            "Search & play in Android YouTube app"
        }
    }

    data class SendWhatsApp(
        val phoneNumber: String,
        val message: String,
        val contactLabel: String? = null
    ) : DeviceAction {
        override val badge: String = "WHATSAPP // MESSAGE"
        override val title: String = when {
            !contactLabel.isNullOrBlank() -> "WhatsApp $contactLabel"
            phoneNumber.isNotBlank() -> "WhatsApp $phoneNumber"
            else -> "Send WhatsApp Message"
        }
        override val subtitle: String = buildString {
            if (phoneNumber.isNotBlank()) append("+$phoneNumber · ")
            else append("Pick contact or enter number · ")
            append(message.ifBlank { "Open chat" })
        }
    }

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String? = null
    ) : DeviceAction {
        override val badge: String = "CLOCK // ALARM"
        override val title: String = String.format(Locale.US, "Set Alarm for %02d:%02d", hour, minute)
        override val subtitle: String = label?.takeIf { it.isNotBlank() } ?: "Android Clock Alarm"
    }

    data class SetTimer(
        val seconds: Int,
        val label: String? = null
    ) : DeviceAction {
        override val badge: String = "CLOCK // TIMER"
        override val title: String = "Start ${formatDuration(seconds)} Timer"
        override val subtitle: String = label?.takeIf { it.isNotBlank() } ?: "Android Clock Countdown"
    }

    data class OpenApp(
        val appName: String,
        val packageName: String? = null,
        val fallbackUri: String? = null
    ) : DeviceAction {
        override val badge: String = "APP // LAUNCH"
        override val title: String = "Launch $appName"
        override val subtitle: String = packageName ?: fallbackUri ?: "Android App Intent"
    }

    companion object {
        fun formatDuration(totalSeconds: Int): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return buildList {
                if (h > 0) add("${h}h")
                if (m > 0) add("${m}m")
                if (s > 0 || (h == 0 && m == 0)) add("${s}s")
            }.joinToString(" ")
        }
    }
}

data class CuratedAppEntry(
    val id: String,
    val name: String,
    val category: String,
    val packageName: String?,
    val fallbackUri: String?,
    val aliases: List<String> = emptyList()
)

object DeviceActionEngine {

    val curatedApps: List<CuratedAppEntry> = listOf(
        CuratedAppEntry("whatsapp", "WhatsApp", "Messaging", "com.whatsapp", "whatsapp://send", listOf("wa", "whats app")),
        CuratedAppEntry("youtube", "YouTube", "Media", "com.google.android.youtube", "vnd.youtube://", listOf("yt", "you tube")),
        CuratedAppEntry("phone", "Phone Dialer", "System", null, "tel:", listOf("dialer", "call", "phone")),
        CuratedAppEntry("camera", "Camera", "System", null, "action:camera", listOf("cam", "photo camera")),
        CuratedAppEntry("clock", "Clock / Alarms", "System", "com.google.android.deskclock", null, listOf("alarm", "timer", "clock")),
        CuratedAppEntry("chrome", "Chrome", "Web", "com.android.chrome", "https://www.google.com", listOf("browser", "google chrome")),
        CuratedAppEntry("gmail", "Gmail", "Productivity", "com.google.android.gm", "mailto:", listOf("email", "mail")),
        CuratedAppEntry("maps", "Google Maps", "Navigation", "com.google.android.apps.maps", "geo:0,0", listOf("maps", "navigation", "directions")),
        CuratedAppEntry("telegram", "Telegram", "Messaging", "org.telegram.messenger", "tg://", listOf("tg")),
        CuratedAppEntry("instagram", "Instagram", "Social", "com.instagram.android", "https://instagram.com", listOf("ig", "insta")),
        CuratedAppEntry("spotify", "Spotify", "Media", "com.spotify.music", "spotify:", listOf("music")),
        CuratedAppEntry("phonepe", "PhonePe", "Payments", "com.phonepe.app", "phonepe://", listOf("phone pe", "upi")),
        CuratedAppEntry("gpay", "Google Pay (GPay)", "Payments", "com.google.android.apps.nbu.paisa.user", "tez://upi/", listOf("google pay", "gpay", "g pay", "tez")),
        CuratedAppEntry("paytm", "Paytm", "Payments", "net.one97.paytm", "paytmmp://", listOf("pay tm")),
        CuratedAppEntry("swiggy", "Swiggy", "Food", "in.swiggy.android", "https://www.swiggy.com", listOf("food")),
        CuratedAppEntry("zomato", "Zomato", "Food", "com.application.zomato", "https://www.zomato.com", emptyList()),
        CuratedAppEntry("calendar", "Calendar", "Productivity", "com.google.android.calendar", "content://com.android.calendar/time/", listOf("schedule")),
        CuratedAppEntry("calculator", "Calculator", "Utility", "com.google.android.calculator", null, listOf("calc")),
        CuratedAppEntry("settings", "Android Settings", "System", "com.android.settings", null, listOf("system settings")),
        CuratedAppEntry("playstore", "Play Store", "System", "com.android.vending", "market://details?id=com.android.vending", listOf("play store", "store")),
        CuratedAppEntry("linkedin", "LinkedIn", "Social", "com.linkedin.android", "https://www.linkedin.com", listOf("linked in")),
        CuratedAppEntry("x", "X (Twitter)", "Social", "com.twitter.android", "https://x.com", listOf("twitter")),
        CuratedAppEntry("slack", "Slack", "Work", "com.Slack", "slack://open", emptyList()),
        CuratedAppEntry("discord", "Discord", "Social", "com.discord", "https://discord.com/app", emptyList()),
        CuratedAppEntry("github", "GitHub", "Dev", "com.github.android", "https://github.com", listOf("git hub"))
    )

    fun normalizePhoneNumber(raw: String, defaultCountryCode: String = "91"): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.replace(Regex("""[^\d]"""), "")
        if (digits.isEmpty()) return ""
        return when {
            hasPlus -> "+$digits"
            digits.length == 10 && defaultCountryCode.isNotBlank() -> "+$defaultCountryCode$digits"
            digits.startsWith("0") && digits.length == 11 && defaultCountryCode.isNotBlank() ->
                "+$defaultCountryCode${digits.drop(1)}"
            else -> "+$digits"
        }
    }

    fun normalizeWhatsAppDigits(raw: String, defaultCountryCode: String = "91"): String {
        val digits = raw.replace(Regex("""[^\d]"""), "")
        if (digits.isEmpty()) return ""
        return when {
            digits.length == 10 && defaultCountryCode.isNotBlank() -> "$defaultCountryCode$digits"
            digits.startsWith("0") && digits.length == 11 && defaultCountryCode.isNotBlank() ->
                "$defaultCountryCode${digits.drop(1)}"
            else -> digits
        }
    }

    fun resolveContact(query: String, contacts: List<QuickContact>): QuickContact? {
        val clean = query.trim().lowercase(Locale.US)
        if (clean.isEmpty()) return null
        return contacts.firstOrNull { it.name.trim().lowercase(Locale.US) == clean }
            ?: contacts.firstOrNull { it.name.trim().lowercase(Locale.US).startsWith(clean) }
            ?: contacts.firstOrNull { it.name.trim().lowercase(Locale.US).contains(clean) }
            ?: contacts.firstOrNull { clean.contains(it.name.trim().lowercase(Locale.US)) && it.name.trim().length >= 2 }
    }

    fun extractYouTubeVideoId(text: String): String? {
        val patterns = listOf(
            Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?v=|shorts/|embed/|live/))([A-Za-z0-9_-]{11})""")
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    fun resolveAppEntry(targetRaw: String, customApps: List<CustomAppEntry> = emptyList()): DeviceAction.OpenApp? {
        val cleanTarget = targetRaw.trim().trimEnd('.', '!', '?')
        if (cleanTarget.isBlank()) return null
        val targetLower = cleanTarget.lowercase(Locale.US)

        // 1. User custom apps first
        val customMatch = customApps.firstOrNull { app ->
            app.name.equals(cleanTarget, ignoreCase = true) ||
                app.packageName.equals(cleanTarget, ignoreCase = true) ||
                targetLower.contains(app.name.lowercase(Locale.US))
        }
        if (customMatch != null) {
            return DeviceAction.OpenApp(
                appName = customMatch.name,
                packageName = customMatch.packageName
            )
        }

        // 2. Built-in curated dictionary
        val matchedApp = curatedApps.firstOrNull { app ->
            app.name.equals(cleanTarget, ignoreCase = true) ||
                app.id.equals(targetLower, ignoreCase = true) ||
                app.aliases.any { it.equals(targetLower, ignoreCase = true) } ||
                targetLower.contains(app.id)
        }
        if (matchedApp != null) {
            return DeviceAction.OpenApp(
                appName = matchedApp.name,
                packageName = matchedApp.packageName,
                fallbackUri = matchedApp.fallbackUri
            )
        }

        // 3. Direct Android package ID like "com.spotify.music"
        if (cleanTarget.contains(".") && !cleanTarget.contains(" ")) {
            return DeviceAction.OpenApp(
                appName = cleanTarget.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                packageName = cleanTarget,
                fallbackUri = null
            )
        }
        return null
    }

    fun parseFromPrompt(
        rawInput: String,
        contacts: List<QuickContact> = emptyList(),
        customApps: List<CustomAppEntry> = emptyList()
    ): DeviceAction? {
        val text = rawInput.trim()
        if (text.isEmpty() || text.startsWith("/")) return null
        val lower = text.lowercase(Locale.US)

        // 1. Timer: "set a timer for 5 minutes", "timer 90 seconds"
        if (lower.contains("timer") || lower.contains("countdown")) {
            val seconds = parseTimerSeconds(lower)
            if (seconds != null && seconds > 0) {
                val label = extractLabelAfterKeywords(text, listOf("for ", "called ", "named ", "labeled "))
                    ?.takeIf { !it.matches(Regex("^\\d+.*")) }
                return DeviceAction.SetTimer(seconds = seconds, label = label)
            }
        }

        // 2. Alarm: "set alarm for 6:30 am", "wake me up at 7 am"
        if (lower.contains("alarm") || lower.contains("wake me")) {
            val hm = parseAlarmHourMinute(lower)
            if (hm != null) {
                val label = extractLabelAfterKeywords(text, listOf("labeled ", "called ", "named ", "message "))
                return DeviceAction.SetAlarm(hour = hm.first, minute = hm.second, label = label)
            }
        }

        // 3. Send WhatsApp Message:
        //    "Send WhatsApp to Rahul saying I'll be late"
        //    "Message Mom on WhatsApp: reaching in 10 mins"
        //    "WhatsApp +91 9876543210 hello there"
        if (lower.contains("whatsapp") || lower.startsWith("wa ")) {
            val parsedWa = parseWhatsAppPrompt(text, contacts)
            if (parsedWa != null) return parsedWa
        }

        // 4. Play / Open YouTube Video or Search:
        //    "Play Arijit Singh songs on YouTube", "Open MKBHD latest video on YouTube", "Open https://youtu.be/..."
        val directYtId = extractYouTubeVideoId(text)
        if (directYtId != null && (lower.startsWith("open ") || lower.startsWith("play ") || lower.startsWith("watch ") || text.startsWith("http"))) {
            return DeviceAction.OpenYouTube(queryOrUrl = text, videoId = directYtId)
        }
        val ytPlayMatch = Regex(
            """^(?:play|watch|search|find|open)\s+(.+?)\s+(?:on|in)\s+youtube\s*$""",
            RegexOption.IGNORE_CASE
        ).find(text)
            ?: Regex(
                """^(?:youtube|yt)\s+(?:play|search|watch)?\s*(.+)$""",
                RegexOption.IGNORE_CASE
            ).find(text)
        if (ytPlayMatch != null) {
            val query = ytPlayMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            if (query.isNotBlank() && !query.equals("youtube", ignoreCase = true)) {
                return DeviceAction.OpenYouTube(queryOrUrl = query, videoId = extractYouTubeVideoId(query))
            }
        }

        // 5. Phone Call / Dial (supports raw phone number OR Quick Contact name like "Call Mom")
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") ||
            lower.startsWith("make a call to ") || lower.contains("call number ") || lower.contains("dial number ")
        ) {
            val phoneMatch = Regex("""(\+?\d[\d\s\-()]{5,18}\d)""").find(text)
            if (phoneMatch != null) {
                val cleanNum = normalizePhoneNumber(phoneMatch.value)
                val beforeNum = text.substring(0, phoneMatch.range.first)
                    .replace(Regex("^(?:make\\s+a\\s+call\\s+to|call\\s+number|dial\\s+number|call|dial|phone)\\s+", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .takeIf { it.isNotBlank() && it.length <= 24 }
                return DeviceAction.DialPhone(phoneNumber = cleanNum, contactLabel = beforeNum)
            }
            val targetName = text
                .replace(Regex("^(?:make\\s+a\\s+call\\s+to|call|dial|phone)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
                .trimEnd('.', '!', '?')
            if (targetName.isNotBlank() && targetName.length <= 32 && !targetName.contains("?")) {
                val matchedContact = resolveContact(targetName, contacts)
                if (matchedContact != null) {
                    return DeviceAction.DialPhone(
                        phoneNumber = normalizePhoneNumber(matchedContact.phoneNumber),
                        contactLabel = matchedContact.name
                    )
                }
                // Unsaved contact name: still open confirmation card with inline Quick Contact / Number Picker!
                val words = targetName.split(Regex("\\s+"))
                if (words.size <= 3) {
                    return DeviceAction.DialPhone(
                        phoneNumber = "",
                        contactLabel = targetName
                    )
                }
            }
        }

        // 6. Open / Launch Any App (curated + custom user packages + raw package IDs)
        val openMatch = Regex("""^(?:open|launch|start|run)\s+(.+)$""", RegexOption.IGNORE_CASE).find(text)
        if (openMatch != null) {
            val targetRaw = openMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            val resolved = resolveAppEntry(targetRaw, customApps)
            if (resolved != null) return resolved
        }

        return null
    }

    private fun parseWhatsAppPrompt(text: String, contacts: List<QuickContact>): DeviceAction.SendWhatsApp? {
        val lower = text.lowercase(Locale.US)
        // Don't hijack "open whatsapp" — let OpenApp handle plain launch
        if (Regex("""^(?:open|launch|start)\s+whatsapp\s*$""", RegexOption.IGNORE_CASE).matches(text)) {
            return null
        }

        // Pattern A: "send whatsapp (message) to <target> saying/: <msg>"
        val patternA = Regex(
            """(?:send\s+)?(?:a\s+)?whatsapp(?:\s+message)?\s+(?:to\s+)?([^:]+?)(?:\s+(?:saying|that|with message|message)\s+|:\s*)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        // Pattern B: "message/text/send <target> on whatsapp (saying/:) <msg>"
        val patternB = Regex(
            """(?:send|message|text|msg)\s+(.+?)\s+on\s+whatsapp(?:\s+(?:saying|that)\s+|:\s*|\s+)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        // Pattern C: "whatsapp <phone_number> <msg>"
        val patternC = Regex(
            """^(?:whatsapp|wa)\s+(\+?\d[\d\s\-()]{6,18}\d)(?:\s+(.+))?$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        // Pattern D: "send whatsapp to <target>" (no message yet)
        val patternD = Regex(
            """^(?:send\s+)?(?:a\s+)?whatsapp(?:\s+message)?\s+(?:to\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        val (rawTarget, rawMsg) = when {
            patternA != null -> patternA.groupValues[1].trim() to patternA.groupValues[2].trim()
            patternB != null -> patternB.groupValues[1].trim() to patternB.groupValues[2].trim()
            patternC != null -> patternC.groupValues[1].trim() to patternC.groupValues[2].trim()
            patternD != null && !lower.contains("what is") && !lower.contains("how to") ->
                patternD.groupValues[1].trim() to ""
            else -> return null
        }

        val cleanTarget = rawTarget.trim().trimEnd(':', ',', '.', '!')
        val phoneInside = Regex("""(\+?\d[\d\s\-()]{6,18}\d)""").find(cleanTarget)
        if (phoneInside != null) {
            return DeviceAction.SendWhatsApp(
                phoneNumber = normalizeWhatsAppDigits(phoneInside.value),
                message = rawMsg.trim(),
                contactLabel = null
            )
        }

        val matchedContact = resolveContact(cleanTarget, contacts)
        return if (matchedContact != null) {
            DeviceAction.SendWhatsApp(
                phoneNumber = normalizeWhatsAppDigits(matchedContact.phoneNumber),
                message = rawMsg.trim(),
                contactLabel = matchedContact.name
            )
        } else {
            DeviceAction.SendWhatsApp(
                phoneNumber = "",
                message = rawMsg.trim(),
                contactLabel = cleanTarget.takeIf { it.isNotBlank() }
            )
        }
    }

    fun execute(context: Context, action: DeviceAction): Result<String> {
        return runCatching {
            when (action) {
                is DeviceAction.DialPhone -> {
                    val clean = action.phoneNumber.replace(Regex("""[^\d+*#]"""), "")
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    if (clean.isNotBlank()) "Opened Phone Dialer for $clean" else "Opened Phone Dialer"
                }

                is DeviceAction.OpenYouTube -> {
                    val vid = action.videoId ?: extractYouTubeVideoId(action.queryOrUrl)
                    if (!vid.isNullOrBlank()) {
                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$vid")).apply {
                            setPackage("com.google.android.youtube")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$vid")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(appIntent) }
                            .onFailure { context.startActivity(fallbackIntent) }
                        "Playing YouTube video ($vid)"
                    } else {
                        val encoded = URLEncoder.encode(action.queryOrUrl.trim(), "UTF-8")
                        val ytSearchUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                        val appIntent = Intent(Intent.ACTION_VIEW, ytSearchUri).apply {
                            setPackage("com.google.android.youtube")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/results?search_query=$encoded")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(appIntent) }
                            .onFailure { context.startActivity(webIntent) }
                        "Opened YouTube for \"${action.queryOrUrl.take(36)}\""
                    }
                }

                is DeviceAction.SendWhatsApp -> {
                    val digits = normalizeWhatsAppDigits(action.phoneNumber)
                    if (digits.isBlank()) {
                        throw IllegalArgumentException("Enter or select a phone number first to open WhatsApp.")
                    }
                    val encodedMsg = URLEncoder.encode(action.message, "UTF-8")
                    val waUri = Uri.parse("https://wa.me/$digits${if (action.message.isNotBlank()) "?text=$encodedMsg" else ""}")
                    val appIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(appIntent) }
                        .onFailure { context.startActivity(fallbackIntent) }
                    "Opened WhatsApp chat for ${action.contactLabel ?: "+$digits"}"
                }

                is DeviceAction.SetAlarm -> {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, action.hour.coerceIn(0, 23))
                        putExtra(AlarmClock.EXTRA_MINUTES, action.minute.coerceIn(0, 59))
                        if (!action.label.isNullOrBlank()) {
                            putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        }
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Sent alarm (${String.format(Locale.US, "%02d:%02d", action.hour, action.minute)}) to Clock"
                }

                is DeviceAction.SetTimer -> {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, action.seconds.coerceAtLeast(1))
                        if (!action.label.isNullOrBlank()) {
                            putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        }
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Started ${DeviceAction.formatDuration(action.seconds)} timer in Clock"
                }

                is DeviceAction.OpenApp -> {
                    if (action.fallbackUri == "action:camera") {
                        val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(camIntent)
                        return@runCatching "Opened Camera"
                    }
                    val pm = context.packageManager
                    val launchIntent = action.packageName?.let { pkg ->
                        pm.getLaunchIntentForPackage(pkg)
                    }?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        "Launched ${action.appName}"
                    } else if (action.packageName == "com.android.settings") {
                        val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                        "Opened Android Settings"
                    } else if (!action.fallbackUri.isNullOrBlank()) {
                        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse(action.fallbackUri)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(uriIntent)
                        "Opened ${action.appName}"
                    } else {
                        throw IllegalStateException("${action.appName} is not installed or cannot be launched directly.")
                    }
                }
            }
        }
    }

    private fun parseTimerSeconds(lower: String): Int? {
        var total = 0
        var matchedAny = false

        Regex("""(\d+)\s*(?:hours?|hrs?|h)\b""").findAll(lower).forEach { m ->
            total += (m.groupValues[1].toIntOrNull() ?: 0) * 3600
            matchedAny = true
        }
        Regex("""(\d+)\s*(?:minutes?|mins?|m)\b""").findAll(lower).forEach { m ->
            total += (m.groupValues[1].toIntOrNull() ?: 0) * 60
            matchedAny = true
        }
        Regex("""(\d+)\s*(?:seconds?|secs?|s)\b""").findAll(lower).forEach { m ->
            total += (m.groupValues[1].toIntOrNull() ?: 0)
            matchedAny = true
        }
        if (matchedAny && total > 0) return total

        val bare = Regex("""timer\s+(?:for\s+)?(\d+)\b""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        return if (bare != null && bare > 0) bare * 60 else null
    }

    private fun parseAlarmHourMinute(lower: String): Pair<Int, Int>? {
        val m = Regex("""(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?""").findAll(lower)
            .firstOrNull { match ->
                val rawH = match.groupValues[1].toIntOrNull() ?: return@firstOrNull false
                val hasColon = match.groupValues[2].isNotEmpty()
                val hasAmPm = match.groupValues[3].isNotEmpty()
                (hasColon || hasAmPm) && rawH in 0..23
            } ?: return null

        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val ampm = m.groupValues[3].replace(".", "").lowercase(Locale.US)
        if (ampm == "pm" && hour in 1..11) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
    }

    private fun extractLabelAfterKeywords(text: String, keywords: List<String>): String? {
        val lower = text.lowercase(Locale.US)
        for (kw in keywords) {
            val idx = lower.lastIndexOf(kw)
            if (idx >= 0) {
                val cand = text.substring(idx + kw.length).trim().trimEnd('.', '!', '?')
                if (cand.length in 2..40) return cand
            }
        }
        return null
    }
}
