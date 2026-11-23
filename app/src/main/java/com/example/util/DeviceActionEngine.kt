package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.QuickContact
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

    data class OpenMaps(
        val query: String,
        val isNavigation: Boolean = false
    ) : DeviceAction {
        override val badge: String = if (isNavigation) "MAPS // NAVIGATE" else "MAPS // SEARCH"
        override val title: String = if (isNavigation) {
            "Navigate to ${query.take(40)}"
        } else {
            "Search Maps: ${query.take(40)}"
        }
        override val subtitle: String = if (isNavigation) {
            "google.navigation:q=$query"
        } else {
            "geo:0,0?q=$query"
        }
    }

    data class CreateCalendarEvent(
        val eventTitle: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val location: String? = null,
        val description: String? = null
    ) : DeviceAction {
        override val badge: String = "CALENDAR // EVENT"
        override val title: String = "Schedule: ${eventTitle.take(42)}"
        override val subtitle: String = buildString {
            val fmt = SimpleDateFormat("EEE, dd MMM · HH:mm", Locale.US)
            append(fmt.format(Date(startTimeMillis)))
            if (!location.isNullOrBlank()) append(" · $location")
        }
    }

    data class SendSms(
        val phoneNumber: String,
        val message: String,
        val contactLabel: String? = null
    ) : DeviceAction {
        override val badge: String = "SMS // MESSAGE"
        override val title: String = when {
            !contactLabel.isNullOrBlank() -> "SMS $contactLabel"
            phoneNumber.isNotBlank() -> "SMS $phoneNumber"
            else -> "Send SMS Message"
        }
        override val subtitle: String = buildString {
            if (phoneNumber.isNotBlank()) append("$phoneNumber · ")
            else append("Pick contact or enter number · ")
            append(message.ifBlank { "Compose SMS" })
        }
    }

    data class SendEmail(
        val emailAddress: String,
        val subject: String,
        val body: String,
        val contactLabel: String? = null
    ) : DeviceAction {
        override val badge: String = "EMAIL // COMPOSE"
        override val title: String = when {
            !contactLabel.isNullOrBlank() -> "Email $contactLabel"
            emailAddress.isNotBlank() -> "Email $emailAddress"
            else -> "Compose Email"
        }
        override val subtitle: String = buildString {
            if (emailAddress.isNotBlank()) append("$emailAddress · ")
            else append("Enter recipient email · ")
            append(subject.ifBlank { body.take(40).ifBlank { "Open Mail Composer" } })
        }
    }

    data class UpiPay(
        val upiId: String,
        val payeeName: String,
        val amount: String,
        val note: String = ""
    ) : DeviceAction {
        override val badge: String = "UPI // CONFIRM PAY"
        override val title: String = buildString {
            append("Pay ")
            if (amount.isNotBlank()) append("₹$amount ") else append("UPI ")
            append("to ${payeeName.ifBlank { upiId.ifBlank { "Recipient" } }}")
        }
        override val subtitle: String = buildString {
            if (upiId.isNotBlank()) append("upi://pay?pa=$upiId")
            else append("Pick saved UPI contact or enter UPI ID (name@bank)")
            if (note.isNotBlank()) append(" · $note")
        }
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

        // 5.5 UPI Payment Confirmation Card:
        //     "Pay ₹500 to Rahul via UPI", "UPI pay 250 to mom@okaxis for groceries", "Send 100 rs to Mom on UPI"
        parseUpiPrompt(text, contacts)?.let { return it }

        // 5.6 Google Maps Directions & Nearby Search:
        //     "Navigate to Mumbai Airport", "Directions to Pune Station", "Find coffee shops near me", "Open maps for Taj Mahal"
        parseMapsPrompt(text)?.let { return it }

        // 5.7 Calendar Event Insert:
        //     "Schedule Team Standup tomorrow at 10:30 am", "Add calendar event Dentist on 2026-11-25 at 4pm"
        parseCalendarPrompt(text)?.let { return it }

        // 5.8 Send SMS:
        //     "Send SMS to Mom saying reached safely", "Text +91 9876543210: Call me back"
        parseSmsPrompt(text, contacts)?.let { return it }

        // 5.9 Send Email:
        //     "Send email to rahul@example.com subject Project Update body Attached the report"
        parseEmailPrompt(text, contacts)?.let { return it }

        // 6. Open / Launch Any App (curated + custom user packages + raw package IDs)
        val openMatch = Regex("""^(?:open|launch|start|run)\s+(.+)$""", RegexOption.IGNORE_CASE).find(text)
        if (openMatch != null) {
            val targetRaw = openMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            val resolved = resolveAppEntry(targetRaw, customApps)
            if (resolved != null) return resolved
        }

        return null
    }

    private fun parseMapsPrompt(text: String): DeviceAction.OpenMaps? {
        val navMatch = Regex(
            """^(?:navigate|directions|get\s+directions|route|drive|take\s+me)\s+(?:to\s+)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (navMatch != null) {
            val dest = navMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            if (dest.length >= 2) return DeviceAction.OpenMaps(query = dest, isNavigation = true)
        }

        val mapsSearchMatch = Regex(
            """^(?:open\s+(?:google\s+)?maps\s+(?:for|to|search)\s+|search\s+(?:google\s+)?maps\s+(?:for\s+)?|show\s+)(.+?)(?:\s+on\s+(?:google\s+)?maps)?$""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (mapsSearchMatch != null && text.contains("map", ignoreCase = true)) {
            val q = mapsSearchMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            if (q.length >= 2 && !q.equals("maps", ignoreCase = true)) {
                return DeviceAction.OpenMaps(query = q, isNavigation = false)
            }
        }

        val nearbyMatch = Regex(
            """^(?:find|search|locate|show)\s+(.+?)\s+(?:near\s+me|nearby)\s*$""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (nearbyMatch != null) {
            val q = nearbyMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            if (q.length >= 2) return DeviceAction.OpenMaps(query = "$q near me", isNavigation = false)
        }
        return null
    }

    private fun parseCalendarPrompt(text: String): DeviceAction.CreateCalendarEvent? {
        val lower = text.lowercase(Locale.US)
        val looksCalendar = lower.startsWith("schedule ") ||
            lower.startsWith("add calendar") ||
            lower.startsWith("create calendar") ||
            lower.startsWith("add event") ||
            lower.startsWith("create event") ||
            lower.startsWith("book meeting") ||
            lower.startsWith("create meeting") ||
            lower.contains("on my calendar") ||
            lower.contains("to my calendar")
        if (!looksCalendar) return null

        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        var dateMatched = false
        val isoDate = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(lower)
        if (isoDate != null) {
            val y = isoDate.groupValues[1].toIntOrNull()
            val m = isoDate.groupValues[2].toIntOrNull()
            val d = isoDate.groupValues[3].toIntOrNull()
            if (y != null && m != null && d != null && m in 1..12 && d in 1..31) {
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m - 1)
                cal.set(Calendar.DAY_OF_MONTH, d)
                dateMatched = true
            }
        } else if (lower.contains("day after tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 2)
            dateMatched = true
        } else if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            dateMatched = true
        } else if (lower.contains("today")) {
            dateMatched = true
        }

        val hm = parseAlarmHourMinute(lower)
        if (hm != null) {
            cal.set(Calendar.HOUR_OF_DAY, hm.first)
            cal.set(Calendar.MINUTE, hm.second)
            if (!dateMatched && cal.timeInMillis < System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
        }

        val title = text
            .replace(Regex("""^(?:schedule|add\s+calendar\s+event|create\s+calendar\s+event|add\s+event|create\s+event|book\s+meeting|create\s+meeting)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:on|for)\s+\d{4}-\d{1,2}-\d{1,2}""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:today|tomorrow|day\s+after\s+tomorrow)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:at|from)\s+\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:on|to)\s+my\s+calendar\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd('.', '!', '?')
            .ifBlank { "New Event" }

        val startMs = cal.timeInMillis
        val endMs = startMs + 60L * 60L * 1000L
        return DeviceAction.CreateCalendarEvent(
            eventTitle = title,
            startTimeMillis = startMs,
            endTimeMillis = endMs
        )
    }

    private fun parseSmsPrompt(text: String, contacts: List<QuickContact>): DeviceAction.SendSms? {
        val lower = text.lowercase(Locale.US)
        if (lower.contains("whatsapp")) return null

        val pA = Regex(
            """^(?:send\s+)?(?:an?\s+)?(?:sms|text\s+message|text)\s+(?:to\s+)?([^:]+?)(?:\s+(?:saying|that|with\s+message|message)\s+|:\s*)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)
        val pB = Regex(
            """^(?:send\s+)?(?:an?\s+)?sms\s+(?:to\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        val (rawTarget, rawMsg) = when {
            pA != null -> pA.groupValues[1].trim() to pA.groupValues[2].trim()
            pB != null -> pB.groupValues[1].trim() to ""
            else -> return null
        }

        val cleanTarget = rawTarget.trim().trimEnd(':', ',', '.', '!')
        val phoneMatch = Regex("""(\+?\d[\d\s\-()]{5,18}\d)""").find(cleanTarget)
        if (phoneMatch != null) {
            return DeviceAction.SendSms(
                phoneNumber = normalizePhoneNumber(phoneMatch.value),
                message = rawMsg.trim()
            )
        }
        val matched = resolveContact(cleanTarget, contacts)
        return if (matched != null) {
            DeviceAction.SendSms(
                phoneNumber = normalizePhoneNumber(matched.phoneNumber),
                message = rawMsg.trim(),
                contactLabel = matched.name
            )
        } else {
            DeviceAction.SendSms(
                phoneNumber = "",
                message = rawMsg.trim(),
                contactLabel = cleanTarget.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun parseEmailPrompt(text: String, contacts: List<QuickContact>): DeviceAction.SendEmail? {
        val lower = text.lowercase(Locale.US)
        if (!lower.startsWith("send email") && !lower.startsWith("send an email") &&
            !lower.startsWith("email ") && !lower.startsWith("mail ") && !lower.startsWith("compose email")
        ) {
            return null
        }
        if (Regex("""^(?:open|launch)\s+(?:gmail|email|mail)\s*$""", RegexOption.IGNORE_CASE).matches(text)) {
            return null
        }

        val directEmail = Regex("""([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""").find(text)?.value.orEmpty()
        val subjectMatch = Regex("""subject\s*[:\-]?\s*(.+?)(?:\s+(?:body|saying|message)\s*[:\-]?\s*|$)""", RegexOption.IGNORE_CASE).find(text)
        val bodyMatch = Regex("""(?:body|saying|message)\s*[:\-]?\s*(.+)$""", RegexOption.IGNORE_CASE).find(text)
        val aboutMatch = if (subjectMatch == null) {
            Regex("""(?:about|regarding)\s+(.+)$""", RegexOption.IGNORE_CASE).find(text)
        } else null

        val targetRaw = text
            .replace(Regex("""^(?:send\s+(?:an?\s+)?email|compose\s+email|email|mail)\s+(?:to\s+)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:subject|about|regarding|body|saying|message)\b.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd(':', ',', '.', '!')

        val matched = if (directEmail.isBlank() && targetRaw.isNotBlank()) resolveContact(targetRaw, contacts) else null
        val resolvedEmail = directEmail.ifBlank { matched?.email.orEmpty() }
        val subject = subjectMatch?.groupValues?.get(1)?.trim()
            ?: aboutMatch?.groupValues?.get(1)?.trim()
            ?: ""
        val body = bodyMatch?.groupValues?.get(1)?.trim().orEmpty()

        return DeviceAction.SendEmail(
            emailAddress = resolvedEmail,
            subject = subject,
            body = body,
            contactLabel = matched?.name ?: targetRaw.takeIf { it.isNotBlank() && !it.contains("@") }
        )
    }

    private fun parseUpiPrompt(text: String, contacts: List<QuickContact>): DeviceAction.UpiPay? {
        val lower = text.lowercase(Locale.US)
        val looksUpi = lower.contains("upi") ||
            Regex("""^(?:pay|send)\s+(?:₹|rs\.?|inr\s*)?\d+(?:\.\d{1,2})?\s*(?:rs\.?|rupees?|inr)?\s+to\s+""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (!looksUpi) return null

        val upiRegex = Regex("""([a-zA-Z0-9._-]{2,}@[a-zA-Z0-9]{2,})""")
        val directUpi = upiRegex.find(text)?.value.orEmpty()
        val amountMatch = Regex("""(?:₹|rs\.?|inr\s*)?(\d+(?:\.\d{1,2})?)\s*(?:rs\.?|rupees?|inr)?""", RegexOption.IGNORE_CASE).find(text)
        val amount = amountMatch?.groupValues?.get(1).orEmpty()
        val noteMatch = Regex("""\s+(?:for|note|memo)\s+(.+)$""", RegexOption.IGNORE_CASE).find(text)
        val note = noteMatch?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', '?').orEmpty()

        val payeeMatch = Regex(
            """\bto\s+([A-Za-z0-9._@\s-]{2,36}?)(?:\s+(?:via|on|using|for|note|memo)\b|$)""",
            RegexOption.IGNORE_CASE
        ).find(text)
        val rawPayee = payeeMatch?.groupValues?.get(1)?.trim()?.trimEnd(',', '.', '!').orEmpty()

        val matchedContact = if (rawPayee.isNotBlank() && !rawPayee.contains("@")) {
            resolveContact(rawPayee, contacts)
        } else null

        val resolvedUpi = directUpi.ifBlank { matchedContact?.upiId.orEmpty() }
        val resolvedName = matchedContact?.name ?: rawPayee.takeIf { it.isNotBlank() && !it.contains("@") }
            ?: resolvedUpi.substringBefore("@").ifBlank { "UPI Payee" }

        return DeviceAction.UpiPay(
            upiId = resolvedUpi,
            payeeName = resolvedName,
            amount = amount,
            note = note
        )
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

                is DeviceAction.OpenMaps -> {
                    val encoded = URLEncoder.encode(action.query.trim(), "UTF-8")
                    val primaryUri = if (action.isNavigation) {
                        Uri.parse("google.navigation:q=$encoded")
                    } else {
                        Uri.parse("geo:0,0?q=$encoded")
                    }
                    val mapsIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val genericGeoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val webFallback = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(mapsIntent) }
                        .recoverCatching { context.startActivity(genericGeoIntent) }
                        .onFailure { context.startActivity(webFallback) }
                    if (action.isNavigation) "Started navigation to ${action.query}" else "Opened Maps for ${action.query}"
                }

                is DeviceAction.CreateCalendarEvent -> {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, action.eventTitle)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.startTimeMillis)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.endTimeMillis)
                        if (!action.location.isNullOrBlank()) {
                            putExtra(CalendarContract.Events.EVENT_LOCATION, action.location)
                        }
                        if (!action.description.isNullOrBlank()) {
                            putExtra(CalendarContract.Events.DESCRIPTION, action.description)
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Opened Calendar to add \"${action.eventTitle}\""
                }

                is DeviceAction.SendSms -> {
                    val clean = action.phoneNumber.replace(Regex("""[^\d+*#]"""), "")
                    if (clean.isBlank()) {
                        throw IllegalArgumentException("Enter or select a phone number first to send SMS.")
                    }
                    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).apply {
                        putExtra("sms_body", action.message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val fallbackIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("sms:$clean?body=${URLEncoder.encode(action.message, "UTF-8")}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(smsIntent) }
                        .onFailure { context.startActivity(fallbackIntent) }
                    "Opened SMS composer for ${action.contactLabel ?: clean}"
                }

                is DeviceAction.SendEmail -> {
                    val recipient = action.emailAddress.trim()
                    if (recipient.isBlank()) {
                        throw IllegalArgumentException("Enter a recipient email address first.")
                    }
                    val queryParts = buildList {
                        if (action.subject.isNotBlank()) add("subject=${URLEncoder.encode(action.subject, "UTF-8")}")
                        if (action.body.isNotBlank()) add("body=${URLEncoder.encode(action.body, "UTF-8")}")
                    }
                    val mailUri = Uri.parse(
                        "mailto:$recipient" + if (queryParts.isNotEmpty()) "?${queryParts.joinToString("&")}" else ""
                    )
                    val emailIntent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                        if (action.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, action.subject)
                        if (action.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, action.body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(emailIntent)
                    "Opened Email composer for ${action.contactLabel ?: recipient}"
                }

                is DeviceAction.UpiPay -> {
                    val vpa = action.upiId.trim()
                    if (vpa.isBlank() || !vpa.contains("@")) {
                        throw IllegalArgumentException("Enter a valid UPI ID (e.g. name@okaxis) before paying.")
                    }
                    val upiUri = Uri.Builder()
                        .scheme("upi")
                        .authority("pay")
                        .appendQueryParameter("pa", vpa)
                        .appendQueryParameter("pn", action.payeeName.ifBlank { vpa.substringBefore("@") })
                        .apply {
                            if (action.amount.isNotBlank()) appendQueryParameter("am", action.amount.trim())
                            if (action.note.isNotBlank()) appendQueryParameter("tn", action.note.trim())
                        }
                        .appendQueryParameter("cu", "INR")
                        .build()
                    val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
                    val chooser = Intent.createChooser(upiIntent, "Pay via UPI").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    "Opened UPI payment sheet for ${action.payeeName.ifBlank { vpa }}"
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
