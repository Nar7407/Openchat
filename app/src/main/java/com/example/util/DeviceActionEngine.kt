package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.QuickContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DialExecutionResult(
    val message: String,
    val usedAutoDialBridge: Boolean = false
)

sealed interface DeviceAction {
    val badge: String
    val title: String
    val subtitle: String

    data class DialPhone(
        val phoneNumber: String,
        val contactLabel: String? = null,
        val ambiguousMatches: List<QuickContact> = emptyList()
    ) : DeviceAction {
        override val badge: String = "PHONE // CALL"
        val displayBanner: String
            get() = when {
                !contactLabel.isNullOrBlank() && phoneNumber.isNotBlank() ->
                    "ACTION // Calling $contactLabel ($phoneNumber)"
                !contactLabel.isNullOrBlank() ->
                    "ACTION // Calling $contactLabel"
                phoneNumber.isNotBlank() ->
                    "ACTION // Calling $phoneNumber"
                else -> "ACTION // Open Phone Dialer"
            }
        override val title: String = when {
            ambiguousMatches.size >= 2 && phoneNumber.isBlank() -> "Choose Contact: ${contactLabel ?: "Call"}"
            !contactLabel.isNullOrBlank() && phoneNumber.isNotBlank() -> "ACTION // Calling $contactLabel ($phoneNumber)"
            !contactLabel.isNullOrBlank() -> "ACTION // Calling $contactLabel"
            phoneNumber.isNotBlank() -> "ACTION // Calling $phoneNumber"
            else -> "Open Phone Dialer"
        }
        override val subtitle: String = when {
            ambiguousMatches.size >= 2 && phoneNumber.isBlank() ->
                "${ambiguousMatches.size} saved contacts match — say first or second, or tap one below"
            phoneNumber.isNotBlank() -> "Direct Dialer · $phoneNumber"
            else -> "Pick a Quick Contact or type a number"
        }
    }

    data class WhatsAppCall(
        val phoneNumber: String,
        val contactLabel: String? = null,
        val isVideoCall: Boolean = false,
        val ambiguousMatches: List<QuickContact> = emptyList()
    ) : DeviceAction {
        override val badge: String = if (isVideoCall) "WHATSAPP // VIDEO CALL" else "WHATSAPP // VOICE CALL"
        val displayBanner: String
            get() {
                val mode = if (isVideoCall) "WhatsApp Video Call" else "WhatsApp Voice Call"
                return when {
                    !contactLabel.isNullOrBlank() && phoneNumber.isNotBlank() ->
                        "ACTION // $mode: $contactLabel (+$phoneNumber)"
                    !contactLabel.isNullOrBlank() ->
                        "ACTION // $mode: $contactLabel"
                    phoneNumber.isNotBlank() ->
                        "ACTION // $mode: +$phoneNumber"
                    else -> "ACTION // Open WhatsApp Call"
                }
            }
        override val title: String = when {
            ambiguousMatches.size >= 2 && phoneNumber.isBlank() ->
                "Choose Contact: ${contactLabel ?: "WhatsApp Call"}"
            !contactLabel.isNullOrBlank() ->
                "${if (isVideoCall) "WhatsApp Video Call" else "WhatsApp Call"} $contactLabel"
            phoneNumber.isNotBlank() ->
                "${if (isVideoCall) "WhatsApp Video Call" else "WhatsApp Call"} +$phoneNumber"
            else -> if (isVideoCall) "WhatsApp Video Call" else "WhatsApp Voice Call"
        }
        override val subtitle: String = when {
            ambiguousMatches.size >= 2 && phoneNumber.isBlank() ->
                "${ambiguousMatches.size} matches found — say first or second, or tap one below"
            phoneNumber.isNotBlank() ->
                "+$phoneNumber · Opens contact's WhatsApp ${if (isVideoCall) "video" else "voice"} screen"
            else -> "Pick a Quick Contact or enter a number for WhatsApp"
        }
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
        val contactLabel: String? = null,
        val ambiguousMatches: List<QuickContact> = emptyList()
    ) : DeviceAction {
        override val badge: String = "WHATSAPP // MESSAGE"
        override val title: String = when {
            ambiguousMatches.size >= 2 && phoneNumber.isBlank() -> "Choose Contact: ${contactLabel ?: "WhatsApp"}"
            !contactLabel.isNullOrBlank() -> "WhatsApp $contactLabel"
            phoneNumber.isNotBlank() -> "WhatsApp $phoneNumber"
            else -> "Send WhatsApp Message"
        }
        override val subtitle: String = buildString {
            when {
                ambiguousMatches.size >= 2 && phoneNumber.isBlank() ->
                    append("${ambiguousMatches.size} matches found — say first or second, or tap one · ")
                phoneNumber.isNotBlank() -> append("+$phoneNumber · ")
                else -> append("Pick contact or enter number · ")
            }
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
        val contactLabel: String? = null,
        val ambiguousMatches: List<QuickContact> = emptyList()
    ) : DeviceAction {
        override val badge: String = "SMS // MESSAGE"
        override val title: String = when {
            ambiguousMatches.size >= 2 && phoneNumber.isBlank() -> "Choose Contact: ${contactLabel ?: "SMS"}"
            !contactLabel.isNullOrBlank() -> "SMS $contactLabel"
            phoneNumber.isNotBlank() -> "SMS $phoneNumber"
            else -> "Send SMS Message"
        }
        override val subtitle: String = buildString {
            when {
                ambiguousMatches.size >= 2 && phoneNumber.isBlank() ->
                    append("${ambiguousMatches.size} matches found — say first or second, or tap one · ")
                phoneNumber.isNotBlank() -> append("$phoneNumber · ")
                else -> append("Pick contact or enter number · ")
            }
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

    /**
     * Resolves ALL matching contacts by primary name OR nicknames/aliases.
     * Priority tiers:
     * 1) Exact match on name or any alias
     * 2) Prefix match on name or any alias
     * 3) Substring contains match (or query contains a >=2 char name/alias)
     */
    private fun compactAlphaNum(input: String): String =
        input.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    /**
     * Phonetic key tailored for English + Indian STT variations:
     * folds V/W/B -> b, PH -> f, aspirated consonants (bh/dh/th/kh/gh),
     * C/Q -> k, Z -> j, SH -> s, EE/EA -> i, OO -> u, and duplicate letters.
     */
    private fun phoneticKey(input: String): String {
        var s = input.lowercase(Locale.US).replace(Regex("[^a-z]"), "")
        if (s.isEmpty()) return ""
        s = s
            .replace("ph", "f")
            .replace("bh", "b")
            .replace("dh", "d")
            .replace("th", "t")
            .replace("kh", "k")
            .replace("gh", "g")
            .replace("sh", "s")
            .replace("ee", "i")
            .replace("ea", "i")
            .replace("oo", "u")
            .replace("v", "b")
            .replace("w", "b")
            .replace("q", "k")
            .replace("c", "k")
            .replace("z", "j")
        val collapsed = StringBuilder()
        var prev = '\u0000'
        for (ch in s) {
            if (ch != prev) {
                collapsed.append(ch)
                prev = ch
            }
        }
        return collapsed.toString()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            System.arraycopy(curr, 0, prev, 0, b.length + 1)
        }
        return prev[b.length]
    }

    fun resolveMatchingContacts(
        query: String,
        contacts: List<QuickContact>,
        requirePhone: Boolean = false
    ): List<QuickContact> {
        val clean = query.trim().lowercase(Locale.US)
            .removePrefix("my ")
            .trim()
        if (clean.isEmpty()) return emptyList()
        val pool = if (requirePhone) contacts.filter { it.phoneNumber.isNotBlank() } else contacts
        if (pool.isEmpty()) return emptyList()

        fun candidateLabels(c: QuickContact): List<String> = buildList {
            val full = c.name.trim().lowercase(Locale.US)
            if (full.isNotEmpty()) {
                add(full)
                // Also include individual name tokens (e.g. first name) for voice queries
                full.split(Regex("\\s+")).filter { it.length >= 2 }.forEach { token ->
                    if (token != full) add(token)
                }
            }
            c.aliasList.forEach { alias ->
                alias.lowercase(Locale.US).takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }

        // 1. Exact label match
        val exact = pool.filter { c -> candidateLabels(c).any { it == clean } }
        if (exact.isNotEmpty()) return exact

        // 2. Space-insensitive exact match (e.g. "vimladevi" == "vimla devi")
        val cleanCompact = compactAlphaNum(clean)
        if (cleanCompact.length >= 2) {
            val compactExact = pool.filter { c ->
                candidateLabels(c).any { compactAlphaNum(it) == cleanCompact }
            }
            if (compactExact.isNotEmpty()) return compactExact
        }

        // 3. Prefix match
        val prefix = pool.filter { c ->
            candidateLabels(c).any { it.startsWith(clean) || (cleanCompact.length >= 3 && compactAlphaNum(it).startsWith(cleanCompact)) }
        }
        if (prefix.isNotEmpty()) return prefix

        // 4. Substring contains match
        val contains = pool.filter { c ->
            candidateLabels(c).any { label ->
                label.contains(clean) || (label.length >= 2 && clean.contains(label))
            }
        }
        if (contains.isNotEmpty()) return contains

        // 5. Phonetic match (handles V/B/W, PH/F, EE/I, OO/U, missing spaces)
        val cleanPhonetic = phoneticKey(clean)
        if (cleanPhonetic.length >= 3) {
            val phoneticHits = pool.filter { c ->
                candidateLabels(c).any { label ->
                    val lp = phoneticKey(label)
                    lp.isNotEmpty() && (lp == cleanPhonetic || (lp.length >= 4 && cleanPhonetic.length >= 4 && (lp.startsWith(cleanPhonetic) || cleanPhonetic.startsWith(lp))))
                }
            }
            if (phoneticHits.isNotEmpty()) return phoneticHits
        }

        // 6. Fuzzy Levenshtein edit-distance match on both raw compact & phonetic keys
        if (cleanCompact.length >= 3) {
            val maxDist = when {
                cleanCompact.length <= 4 -> 1
                cleanCompact.length <= 7 -> 2
                else -> 2
            }
            val scored = pool.mapNotNull { c ->
                val bestDist = candidateLabels(c).minOfOrNull { label ->
                    val lc = compactAlphaNum(label)
                    val rawD = if (lc.isNotEmpty()) levenshteinDistance(cleanCompact, lc) else 99
                    val lp = phoneticKey(label)
                    val phonD = if (lp.isNotEmpty() && cleanPhonetic.isNotEmpty()) levenshteinDistance(cleanPhonetic, lp) else 99
                    minOf(rawD, phonD)
                } ?: 99
                if (bestDist <= maxDist) c to bestDist else null
            }
            if (scored.isNotEmpty()) {
                val minBest = scored.minOf { it.second }
                return scored.filter { it.second == minBest }.map { it.first }
            }
        }

        return emptyList()
    }

    fun resolveContact(query: String, contacts: List<QuickContact>): QuickContact? =
        resolveMatchingContacts(query, contacts).firstOrNull()

    fun resolveContactByPhone(rawPhone: String, contacts: List<QuickContact>): QuickContact? {
        val targetNorm = normalizePhoneNumber(rawPhone)
        val targetDigits = targetNorm.filter { it.isDigit() }
        if (targetDigits.length < 6) return null
        val targetLast10 = if (targetDigits.length >= 10) targetDigits.takeLast(10) else targetDigits
        return contacts.firstOrNull { c ->
            val cNorm = normalizePhoneNumber(c.phoneNumber)
            val cDigits = cNorm.filter { it.isDigit() }
            if (cDigits.length < 6) return@firstOrNull false
            cNorm == targetNorm ||
                cDigits == targetDigits ||
                (cDigits.length >= 10 && targetDigits.length >= 10 && cDigits.takeLast(10) == targetLast10)
        }
    }

    fun formatContactsForSystemPrompt(contacts: List<QuickContact>): String {
        if (contacts.isEmpty()) return ""
        return buildString {
            appendLine("### Saved User Quick Contacts Directory (Name, Nicknames & Phone)")
            appendLine("When the user asks to call, WhatsApp, SMS, email, or pay any person below (by name or nickname), resolve their exact number/details from this list immediately and invoke `execute_device_action`. NEVER ask the user for digits that already exist below, and NEVER claim 'tap confirmation' without calling `execute_device_action`.")
            contacts.take(60).forEach { c ->
                val aliasPart = if (c.aliasList.isNotEmpty()) " (nicknames: ${c.aliasList.joinToString(", ")})" else ""
                val details = buildList {
                    if (c.phoneNumber.isNotBlank()) add("Phone: ${c.phoneNumber}")
                    if (c.email.isNotBlank()) add("Email: ${c.email}")
                    if (c.upiId.isNotBlank()) add("UPI: ${c.upiId}")
                }.joinToString(" | ")
                if (details.isNotBlank()) {
                    appendLine("- ${c.name}$aliasPart: $details")
                }
            }
        }.trim()
    }

    /**
     * Parses vCard (.vcf 2.1 / 3.0 / 4.0) text into QuickContact records for zero-permission bulk import.
     */
    fun parseVcfContacts(rawVcf: String): List<QuickContact> {
        if (rawVcf.isBlank()) return emptyList()
        // Unfold RFC 6350 folded lines (CRLF/LF followed by single space or tab)
        val unfolded = rawVcf
            .replace("\r\n ", "")
            .replace("\r\n\t", "")
            .replace("\n ", "")
            .replace("\n\t", "")

        val results = mutableListOf<QuickContact>()
        val cards = unfolded.split(Regex("BEGIN:VCARD", RegexOption.IGNORE_CASE))
        for (card in cards) {
            if (!card.contains("END:VCARD", ignoreCase = true)) continue
            val body = card.substringBefore("END:VCARD")
            var fullName = ""
            var structuredName = ""
            val nicknames = mutableListOf<String>()
            val phones = mutableListOf<String>()
            var email = ""

            body.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val colonIdx = line.indexOf(':')
                if (colonIdx <= 0) return@forEach
                val keyPart = line.substring(0, colonIdx).uppercase(Locale.US)
                val valueRaw = line.substring(colonIdx + 1).trim()
                val value = if (keyPart.contains("QUOTED-PRINTABLE")) {
                    decodeQuotedPrintable(valueRaw)
                } else {
                    valueRaw.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ")
                }.trim()
                val baseProp = keyPart.substringBefore(';').substringAfterLast('.')
                when (baseProp) {
                    "FN" -> if (value.isNotBlank()) fullName = value
                    "N" -> {
                        val parts = value.split(';').map { it.trim() }.filter { it.isNotEmpty() }
                        if (parts.isNotEmpty()) {
                            structuredName = if (parts.size >= 2) "${parts[1]} ${parts[0]}".trim() else parts[0]
                        }
                    }
                    "NICKNAME", "X-NICKNAME" -> {
                        value.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { nicknames += it }
                    }
                    "TEL" -> {
                        val normalized = normalizePhoneNumber(value)
                        if (normalized.length >= 6 && normalized !in phones) {
                            phones += normalized
                        }
                    }
                    "EMAIL" -> if (email.isBlank() && value.contains("@")) email = value
                }
            }

            val resolvedName = fullName.ifBlank { structuredName }.trim()
            if (resolvedName.isBlank()) continue
            val nickStr = nicknames.distinctBy { it.lowercase(Locale.US) }.joinToString(", ")
            if (phones.isEmpty()) {
                if (email.isNotBlank()) {
                    results += QuickContact(name = resolvedName, phoneNumber = "", email = email, nicknames = nickStr)
                }
            } else {
                phones.forEachIndexed { idx, phone ->
                    val label = if (phones.size > 1) "$resolvedName (${idx + 1})" else resolvedName
                    val extraNick = if (phones.size > 1) {
                        (listOf(resolvedName) + nicknames).distinctBy { it.lowercase(Locale.US) }.joinToString(", ")
                    } else nickStr
                    results += QuickContact(
                        name = label,
                        phoneNumber = phone,
                        email = email,
                        nicknames = extraNick
                    )
                }
            }
        }
        return results
    }

    private fun decodeQuotedPrintable(input: String): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < input.length) {
            if (input[i] == '=' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3)
                val b = hex.toIntOrNull(16)
                if (b != null) {
                    out.write(b)
                    i += 3
                    continue
                }
            }
            out.write(input[i].code)
            i++
        }
        return runCatching { out.toString("UTF-8") }.getOrDefault(input)
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

    private val bridgeHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val callMishearingWords = setOf(
        "all", "coal", "cool", "carl", "paul", "hall", "tall", "wall", "fall", "gall",
        "koll", "kol", "cal", "kaul", "corl", "cawl", "kohl", "crawl"
    )
    private val unambiguousCallTypos = setOf("coal", "koll", "kol", "kaul", "corl", "cawl", "cal")

    /**
     * Pre-processes voice-to-text transcripts to repair common STT mishearings at the start
     * of Call and WhatsApp/Message commands (e.g., "all Manisha" / "coal Manisha" -> "call Manisha",
     * "what's up Manisha hi" -> "whatsapp Manisha hi", "massage Manisha hi" -> "message Manisha hi").
     */
    fun preprocessVoiceCommand(
        rawInput: String,
        contacts: List<QuickContact> = emptyList()
    ): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("/")) return trimmed

        // Preserve any politeness prefix while inspecting the command head
        val prefixRegex = Regex(
            """^((?:hey\s+allchat[\s,]+|ok\s+allchat[\s,]+|please\s+|can\s+you\s+(?:please\s+)?|could\s+you\s+(?:please\s+)?)+)(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val prefixMatch = prefixRegex.find(trimmed)
        val prefix = prefixMatch?.groupValues?.get(1).orEmpty()
        val body = (prefixMatch?.groupValues?.get(2) ?: trimmed).trim()
        if (body.isEmpty()) return trimmed

        // 1. Repair "call to <target>" / "make call <target>" / "dial to <target>"
        val callToMatch = Regex("""^(?:call\s+to|make\s+call(?:\s+to)?|dial\s+to)\s+(.+)$""", RegexOption.IGNORE_CASE).find(body)
        if (callToMatch != null) {
            return "${prefix}call ${callToMatch.groupValues[1].trim()}".trim()
        }

        // 2. Repair WhatsApp STT mishearings ("what's up Manisha hi", "whats app Manisha hi", "watsapp Manisha hi")
        val waTypoMatch = Regex(
            """^(?:what'?s\s+up|whatsup|whats\s+app|watsapp|watts\s+app|wattsapp)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (waTypoMatch != null) {
            val rest = waTypoMatch.groupValues[1].trim()
            val headWord = body.substringBefore(" ").lowercase(Locale.US)
            val isUnambiguousWaTypo = headWord in setOf("watsapp", "wattsapp") ||
                body.lowercase(Locale.US).startsWith("whats app ") ||
                body.lowercase(Locale.US).startsWith("watts app ")
            val hasPhone = Regex("""\+?\d[\d\s\-()]{6,18}\d""").containsMatchIn(rest)
            val matchesContact = hasLeadingContactMatch(rest, contacts)
            if (isUnambiguousWaTypo || hasPhone || matchesContact) {
                return "${prefix}whatsapp $rest".trim()
            }
        }

        // 3. Repair "massage <contact> <msg>" -> "message <contact> <msg>"
        val massageMatch = Regex("""^massage\s+(.+)$""", RegexOption.IGNORE_CASE).find(body)
        if (massageMatch != null) {
            val rest = massageMatch.groupValues[1].trim()
            if (hasLeadingContactMatch(rest, contacts) || Regex("""\+?\d[\d\s\-()]{6,18}\d""").containsMatchIn(rest)) {
                return "${prefix}message $rest".trim()
            }
        }

        // 4. Repair Call STT mishearings at the start ("all Manisha", "coal Manisha", "cal Mom")
        val firstSpace = body.indexOf(' ')
        if (firstSpace > 0) {
            val firstWord = body.substring(0, firstSpace).lowercase(Locale.US).trimEnd(',', '.')
            val rest = body.substring(firstSpace + 1).trim()
            if (firstWord in callMishearingWords && rest.isNotBlank()) {
                val cleanedTarget = rest.replace(Regex("""^(?:to|my)\s+""", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .trimEnd('.', '!', '?')
                val hasPhone = Regex("""^\+?\d[\d\s\-()]{5,18}\d$""").matches(cleanedTarget)
                val matchesSavedContact = cleanedTarget.isNotBlank() &&
                    resolveMatchingContacts(cleanedTarget, contacts, requirePhone = true).isNotEmpty()
                val words = cleanedTarget.split(Regex("\\s+")).filter { it.isNotBlank() }
                val looksLikeSingleProperName = firstWord in unambiguousCallTypos &&
                    words.size in 1..2 &&
                    words.firstOrNull()?.firstOrNull()?.isUpperCase() == true
                if (hasPhone || matchesSavedContact || looksLikeSingleProperName) {
                    return "${prefix}call $rest".trim()
                }
            }
        }

        return trimmed
    }

    private fun hasLeadingContactMatch(remainder: String, contacts: List<QuickContact>): Boolean {
        if (contacts.isEmpty()) return false
        val stripped = remainder.replace(Regex("""^(?:to|my)\s+""", RegexOption.IGNORE_CASE), "").trim()
        if (stripped.isEmpty()) return false
        val tokens = stripped.split(Regex("\\s+")).filter { it.isNotBlank() }
        val maxPrefix = minOf(3, tokens.size)
        for (len in maxPrefix downTo 1) {
            val candidate = tokens.take(len).joinToString(" ").trimEnd(':', ',', '.', '!')
            if (candidate.length >= 2 && resolveMatchingContacts(candidate, contacts, requirePhone = true).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * Splits "<contact name> <message>" by matching the longest prefix against saved contacts.
     */
    private fun splitLeadingContactAndMessage(
        remainder: String,
        contacts: List<QuickContact>
    ): Pair<String, String>? {
        val stripped = remainder.replace(Regex("""^(?:to\s+)?(?:my\s+)?""", RegexOption.IGNORE_CASE), "").trim()
        if (stripped.isEmpty()) return null
        val tokens = stripped.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        // Check explicit separator keywords first ("Manisha saying hi", "Manisha: hi")
        val sepMatch = Regex(
            """^([^:]+?)(?:\s+(?:saying|telling\s+(?:him|her|them)|that|with\s+message|message)\s+|:\s*)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(stripped)
        if (sepMatch != null) {
            val cand = sepMatch.groupValues[1].trim().trimEnd(':', ',', '.', '!')
            val msg = sepMatch.groupValues[2].trim()
            if (cand.isNotBlank()) return cand to msg
        }

        // Try longest matching saved contact prefix (3 words -> 2 words -> 1 word)
        val maxPrefix = minOf(3, tokens.size)
        for (len in maxPrefix downTo 1) {
            val cand = tokens.take(len).joinToString(" ").trimEnd(':', ',', '.', '!')
            if (cand.length >= 2 && resolveMatchingContacts(cand, contacts, requirePhone = true).isNotEmpty()) {
                val msg = tokens.drop(len).joinToString(" ").trim()
                return cand to msg
            }
        }
        return null
    }

    /**
     * Sequential Multi-Action Chain Parser:
     * Splits compound voice/text prompts (e.g. "Message Rahul I'm leaving and set an alarm for 8:30 AM")
     * into an ordered queue of DeviceActions ONLY when every resulting segment independently resolves
     * into a valid DeviceAction. Regular messages containing "and" (e.g. "Message Rahul bring bread and milk")
     * stay intact as a single action.
     */
    fun parseActionChainFromPrompt(
        rawInput: String,
        contacts: List<QuickContact> = emptyList(),
        customApps: List<CustomAppEntry> = emptyList()
    ): List<DeviceAction> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("/")) return emptyList()

        val chained = splitCompoundActions(trimmed, contacts, customApps, depth = 0)
        if (chained.size >= 2) return chained

        return listOfNotNull(parseFromPrompt(trimmed, contacts, customApps))
    }

    private fun splitCompoundActions(
        clause: String,
        contacts: List<QuickContact>,
        customApps: List<CustomAppEntry>,
        depth: Int
    ): List<DeviceAction> {
        val clean = clause.trim().trimEnd('.', ';')
        if (clean.isEmpty() || depth >= 4) {
            return listOfNotNull(parseFromPrompt(clean, contacts, customApps))
        }

        // First try explicit clause separators (semicolon / multi-sentence)
        val explicitSep = Regex("""\s*;\s*|\.\s+""")
        val explicitParts = clean.split(explicitSep).map { it.trim() }.filter { it.isNotEmpty() }
        if (explicitParts.size >= 2) {
            val allParsed = explicitParts.flatMap { part ->
                val sub = splitCompoundActions(part, contacts, customApps, depth + 1)
                if (sub.isEmpty()) return emptyList()
                sub
            }
            if (allParsed.size >= 2) return allParsed
        }

        // Scan chain connectors ("and then", "then", ", and", "and") and split ONLY when both sides parse cleanly
        val connectorRegex = Regex(
            """(?i)(?:,\s*and\s+then\s+|,\s*then\s+|\s+and\s+then\s+|\s+then\s+|,\s*and\s+|\s+and\s+|,\s+)"""
        )
        val matches = connectorRegex.findAll(clean).toList()
        for (m in matches) {
            val leftStr = clean.substring(0, m.range.first).trim()
            val rightStr = clean.substring(m.range.last + 1).trim()
            if (leftStr.length < 3 || rightStr.length < 3) continue
            val leftAction = parseFromPrompt(leftStr, contacts, customApps) ?: continue
            val rightActions = splitCompoundActions(rightStr, contacts, customApps, depth + 1)
            if (rightActions.isNotEmpty()) {
                return listOf(leftAction) + rightActions
            }
        }
        return emptyList()
    }

    fun ambiguousMatchesOf(action: DeviceAction): List<QuickContact> = when (action) {
        is DeviceAction.DialPhone -> action.ambiguousMatches
        is DeviceAction.WhatsAppCall -> action.ambiguousMatches
        is DeviceAction.SendWhatsApp -> action.ambiguousMatches
        is DeviceAction.SendSms -> action.ambiguousMatches
        else -> emptyList()
    }

    fun contactTargetNameOf(action: DeviceAction): String? = when (action) {
        is DeviceAction.DialPhone -> action.contactLabel
        is DeviceAction.WhatsAppCall -> action.contactLabel
        is DeviceAction.SendWhatsApp -> action.contactLabel
        is DeviceAction.SendSms -> action.contactLabel
        is DeviceAction.SendEmail -> action.contactLabel
        is DeviceAction.UpiPay -> action.payeeName
        else -> null
    }

    /**
     * Builds the spoken voice disambiguation prompt when multiple imported contacts match a spoken name.
     */
    fun buildDisambiguationSpeech(action: DeviceAction): String? {
        val matches = ambiguousMatchesOf(action)
        if (matches.size < 2) return null
        val target = contactTargetNameOf(action)?.takeIf { it.isNotBlank() } ?: "that contact"
        val spokenNames = matches.take(4).map { it.name }
        val namesJoined = when (spokenNames.size) {
            2 -> "${spokenNames[0]} and ${spokenNames[1]}"
            else -> spokenNames.dropLast(1).joinToString(", ") + ", and " + spokenNames.last()
        }
        val ordinalHint = when (matches.size) {
            2 -> "Say first or second, or tap one."
            3 -> "Say first, second, or third, or tap one."
            else -> "Say first, second, third, or fourth, or tap one."
        }
        return "Found ${matches.size} contacts named $target: $namesJoined. $ordinalHint"
    }

    /**
     * Resolves a spoken or typed disambiguation reply ("first", "second", "2", "Rahul Verma")
     * against an active action with duplicate matching contacts.
     */
    fun resolveDisambiguationVoiceReply(
        rawTranscript: String,
        pendingAction: DeviceAction
    ): DeviceAction? {
        val matches = ambiguousMatchesOf(pendingAction)
        if (matches.size < 2) return null

        val clean = rawTranscript.trim().lowercase(Locale.US)
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (clean.isEmpty()) return null

        val tokens = clean.split(" ")
        var chosenIndex: Int? = null

        if (tokens.size <= 5) {
            val ordinalPatterns: List<Pair<Int, Regex>> = listOf(
                0 to Regex("""\b(first|1st|one|1|top)\b"""),
                1 to Regex("""\b(second|2nd|two|2)\b"""),
                2 to Regex("""\b(third|3rd|three|3)\b"""),
                3 to Regex("""\b(fourth|4th|four|4)\b"""),
                4 to Regex("""\b(fifth|5th|five|5)\b""")
            )
            for ((idx, rx) in ordinalPatterns) {
                if (idx < matches.size && rx.containsMatchIn(clean)) {
                    chosenIndex = idx
                    break
                }
            }
        }

        val chosenContact: QuickContact? = if (chosenIndex != null) {
            matches.getOrNull(chosenIndex)
        } else {
            // Try matching spoken surname or full name within the ambiguous candidates
            val directMatch = resolveMatchingContacts(clean, matches, requirePhone = false)
            if (directMatch.size == 1) directMatch.first() else null
        }

        if (chosenContact == null) return null

        return when (pendingAction) {
            is DeviceAction.DialPhone -> pendingAction.copy(
                phoneNumber = normalizePhoneNumber(chosenContact.phoneNumber),
                contactLabel = chosenContact.name,
                ambiguousMatches = emptyList()
            )
            is DeviceAction.WhatsAppCall -> pendingAction.copy(
                phoneNumber = normalizeWhatsAppDigits(chosenContact.phoneNumber),
                contactLabel = chosenContact.name,
                ambiguousMatches = emptyList()
            )
            is DeviceAction.SendWhatsApp -> pendingAction.copy(
                phoneNumber = normalizeWhatsAppDigits(chosenContact.phoneNumber),
                contactLabel = chosenContact.name,
                ambiguousMatches = emptyList()
            )
            is DeviceAction.SendSms -> pendingAction.copy(
                phoneNumber = normalizePhoneNumber(chosenContact.phoneNumber),
                contactLabel = chosenContact.name,
                ambiguousMatches = emptyList()
            )
            else -> null
        }
    }

    fun buildSpokenConfirmationPrompt(
        action: DeviceAction,
        stepIndex: Int = 0,
        totalSteps: Int = 1
    ): String {
        val disambig = buildDisambiguationSpeech(action)
        val stepPrefix = when {
            totalSteps <= 1 -> ""
            stepIndex == 0 -> "Queued $totalSteps actions. Step 1: "
            else -> "Step ${stepIndex + 1} of $totalSteps: "
        }
        if (disambig != null) return stepPrefix + disambig
        val core = when (action) {
            is DeviceAction.DialPhone -> if (action.phoneNumber.isNotBlank()) {
                "Ready to call ${action.contactLabel ?: action.phoneNumber}. Press earbud once to confirm, or double-press to cancel."
            } else {
                "Pick a saved contact or enter a number below to call ${action.contactLabel ?: ""}."
            }
            is DeviceAction.WhatsAppCall -> if (action.phoneNumber.isNotBlank()) {
                val mode = if (action.isVideoCall) "WhatsApp video call" else "WhatsApp call"
                "Ready for $mode with ${action.contactLabel ?: action.phoneNumber}. Press earbud once to confirm, or double-press to cancel."
            } else {
                "Select a contact or enter a phone number below for WhatsApp call."
            }
            is DeviceAction.SendWhatsApp -> if (action.phoneNumber.isNotBlank()) {
                "Ready to send WhatsApp to ${action.contactLabel ?: action.phoneNumber}. Press earbud once to confirm, or double-press to cancel."
            } else {
                "Select a contact or enter a phone number below to send on WhatsApp."
            }
            else -> "Ready to ${action.title}. Press earbud once to confirm, or tap below."
        }
        return stepPrefix + core
    }

    fun parseFromPrompt(
        rawInput: String,
        contacts: List<QuickContact> = emptyList(),
        customApps: List<CustomAppEntry> = emptyList()
    ): DeviceAction? {
        val text = preprocessVoiceCommand(rawInput, contacts).trim()
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

        // 2.5 WhatsApp Voice / Video Call Routing (BEFORE generic Call & WhatsApp text messaging):
        //     "WhatsApp call Rahul", "Video call Manisha", "WhatsApp video call Mom", "Call Rahul on WhatsApp"
        parseWhatsAppCallPrompt(text, contacts)?.let { return it }

        // 3. Send WhatsApp Message:
        //    "Send WhatsApp to Rahul saying I'll be late"
        //    "Message Mom on WhatsApp: reaching in 10 mins"
        //    "WhatsApp +91 9876543210 hello there"
        if (lower.contains("whatsapp") || lower.startsWith("wa ")) {
            val parsedWa = parseWhatsAppPrompt(text, contacts)
            if (parsedWa != null) return parsedWa
        }

        // 3.5 Natural Messaging ("message Manisha hi", "msg Mom reached safely", "send message to Rahul hi") -> WhatsApp
        parseGenericMessagePrompt(text, contacts)?.let { return it }

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

        // 5. Phone Call / Dial (supports raw phone number OR Quick Contact name/nickname like "Call Mom" or "Please call Bhai")
        val normalizedCallInput = text.replace(
            Regex("""^(?:hey\s+allchat\s+|please\s+|can\s+you\s+(?:please\s+)?|could\s+you\s+(?:please\s+)?)+""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        val callLower = normalizedCallInput.lowercase(Locale.US)
        if (callLower.startsWith("call ") || callLower.startsWith("dial ") || callLower.startsWith("phone ") ||
            callLower.startsWith("make a call to ") || callLower.startsWith("ring ") ||
            callLower.contains("call number ") || callLower.contains("dial number ")
        ) {
            val phoneMatch = Regex("""(\+?\d[\d\s\-()]{5,18}\d)""").find(normalizedCallInput)
            if (phoneMatch != null) {
                val cleanNum = normalizePhoneNumber(phoneMatch.value)
                val beforeNum = normalizedCallInput.substring(0, phoneMatch.range.first)
                    .replace(Regex("^(?:make\\s+a\\s+call\\s+to|call\\s+number|dial\\s+number|call|dial|phone|ring)\\s+", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .takeIf { it.isNotBlank() && it.length <= 24 }
                val resolvedName = beforeNum ?: resolveContactByPhone(cleanNum, contacts)?.name
                return DeviceAction.DialPhone(phoneNumber = cleanNum, contactLabel = resolvedName)
            }
            val targetName = normalizedCallInput
                .replace(Regex("^(?:make\\s+a\\s+call\\s+to|call|dial|phone|ring)\\s+(?:my\\s+)?", RegexOption.IGNORE_CASE), "")
                .trim()
                .trimEnd('.', '!', '?')
            if (targetName.isNotBlank() && targetName.length <= 36 && !targetName.contains("?")) {
                val matches = resolveMatchingContacts(targetName, contacts, requirePhone = true)
                val distinctPhones = matches.distinctBy { normalizePhoneNumber(it.phoneNumber) }
                if (distinctPhones.size >= 2) {
                    return DeviceAction.DialPhone(
                        phoneNumber = "",
                        contactLabel = targetName,
                        ambiguousMatches = distinctPhones
                    )
                }
                val matchedContact = distinctPhones.firstOrNull()
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

    /**
     * Routes explicit WhatsApp voice/video call prompts ("WhatsApp call Rahul", "Video call Manisha",
     * "WhatsApp video call Mom", "Call Rahul on WhatsApp") directly to DeviceAction.WhatsAppCall
     * instead of the regular phone dialer or WhatsApp text message composer.
     */
    private fun parseWhatsAppCallPrompt(rawText: String, contacts: List<QuickContact>): DeviceAction.WhatsAppCall? {
        val text = rawText.replace(
            Regex("""^(?:hey\s+allchat[\s,]+|please\s+|can\s+you\s+(?:please\s+)?|could\s+you\s+(?:please\s+)?)+""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        val lower = text.lowercase(Locale.US)
        val hasVideoKeyword = lower.contains("video call") || lower.contains("videocall") || lower.contains("facetime")
        val hasWaKeyword = lower.contains("whatsapp") || lower.startsWith("wa ") || lower.endsWith(" on wa")

        if (!hasVideoKeyword && !hasWaKeyword) return null
        if (!hasVideoKeyword && !lower.contains("call") && !lower.contains("ring")) return null

        val patterns: List<Pair<Boolean, Regex>> = listOf(
            // Video call patterns
            true to Regex("""^(?:make\s+a\s+|start\s+a\s+)?(?:whatsapp|wa)\s+video\s*call\s+(?:to\s+)?(?:my\s+)?(.+)$""", RegexOption.IGNORE_CASE),
            true to Regex("""^(?:make\s+a\s+|start\s+a\s+)?video\s*call\s+(?:to\s+)?(?:my\s+)?(.+?)(?:\s+on\s+(?:whatsapp|wa))?$""", RegexOption.IGNORE_CASE),
            true to Regex("""^(?:call|ring)\s+(?:my\s+)?(.+?)\s+on\s+(?:whatsapp|wa)\s+video$""", RegexOption.IGNORE_CASE),
            // Voice call on WhatsApp patterns
            false to Regex("""^(?:make\s+a\s+|start\s+a\s+)?(?:whatsapp|wa)\s+(?:voice\s+)?call\s+(?:to\s+)?(?:my\s+)?(.+)$""", RegexOption.IGNORE_CASE),
            false to Regex("""^(?:voice\s+call|call|ring|dial)\s+(?:to\s+)?(?:my\s+)?(.+?)\s+(?:on|via|using|in)\s+(?:whatsapp|wa)$""", RegexOption.IGNORE_CASE)
        )

        var isVideo = hasVideoKeyword
        var rawTarget: String? = null
        for ((videoFlag, rx) in patterns) {
            val m = rx.find(text)
            if (m != null) {
                isVideo = videoFlag || hasVideoKeyword
                rawTarget = m.groupValues[1].trim()
                break
            }
        }
        val cleanTarget = rawTarget
            ?.replace(Regex("""\s+(?:on|via)\s+(?:whatsapp|wa)$""", RegexOption.IGNORE_CASE), "")
            ?.trim()
            ?.trimEnd(':', ',', '.', '!', '?')
            .orEmpty()
        if (cleanTarget.isBlank() || cleanTarget.length > 40) return null

        val phoneInside = Regex("""(\+?\d[\d\s\-()]{6,18}\d)""").find(cleanTarget)
        if (phoneInside != null) {
            val digits = normalizeWhatsAppDigits(phoneInside.value)
            val byPhone = resolveContactByPhone(digits, contacts)
            return DeviceAction.WhatsAppCall(
                phoneNumber = digits,
                contactLabel = byPhone?.name,
                isVideoCall = isVideo
            )
        }

        val matches = resolveMatchingContacts(cleanTarget, contacts, requirePhone = true)
        val distinctPhones = matches.distinctBy { normalizeWhatsAppDigits(it.phoneNumber) }
        return when {
            distinctPhones.size >= 2 -> DeviceAction.WhatsAppCall(
                phoneNumber = "",
                contactLabel = cleanTarget,
                isVideoCall = isVideo,
                ambiguousMatches = distinctPhones
            )
            distinctPhones.size == 1 -> {
                val c = distinctPhones.first()
                DeviceAction.WhatsAppCall(
                    phoneNumber = normalizeWhatsAppDigits(c.phoneNumber),
                    contactLabel = c.name,
                    isVideoCall = isVideo
                )
            }
            else -> DeviceAction.WhatsAppCall(
                phoneNumber = "",
                contactLabel = cleanTarget,
                isVideoCall = isVideo
            )
        }
    }

    private fun parseWhatsAppPrompt(rawText: String, contacts: List<QuickContact>): DeviceAction.SendWhatsApp? {
        val text = rawText.replace(
            Regex("""^(?:hey\s+allchat\s+|please\s+|can\s+you\s+(?:please\s+)?|could\s+you\s+(?:please\s+)?)+""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        val lower = text.lowercase(Locale.US)
        // Don't hijack "open whatsapp" — let OpenApp handle plain launch
        if (Regex("""^(?:open|launch|start)\s+whatsapp\s*$""", RegexOption.IGNORE_CASE).matches(text)) {
            return null
        }

        // Pattern A: "send whatsapp (message) to <target> saying/that/: <msg>"
        val patternA = Regex(
            """(?:send\s+)?(?:a\s+)?whatsapp(?:\s+message)?\s+(?:to\s+)?(?:my\s+)?([^:]+?)(?:\s+(?:saying|telling\s+(?:him|her|them)|that|with\s+message|message)\s+|:\s*)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        // Pattern B: "message/text/send <target> on whatsapp (saying/:) <msg>"
        val patternB = Regex(
            """(?:send(?:\s+a\s+message)?|message|text|msg)\s+(?:to\s+)?(?:my\s+)?(.+?)\s+on\s+whatsapp(?:\s+(?:saying|telling\s+(?:him|her|them)|that)\s+|:\s*|\s+)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        // Pattern B2: "send a message on whatsapp to <target> saying/: <msg>"
        val patternB2 = Regex(
            """(?:send\s+)?(?:a\s+)?(?:message|msg|text)\s+on\s+whatsapp\s+(?:to\s+)?(?:my\s+)?([^:]+?)(?:\s+(?:saying|that|with\s+message)\s+|:\s*|\s+)(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        // Pattern C: "whatsapp <phone_number> <msg>"
        val patternC = Regex(
            """^(?:whatsapp|wa)\s+(\+?\d[\d\s\-()]{6,18}\d)(?:\s+(.+))?$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        // Pattern C2: "whatsapp <saved contact name (1-3 words)> <msg>"
        val waRemainder = text.replace(Regex("""^(?:whatsapp|wa)\s+""", RegexOption.IGNORE_CASE), "").trim()
        val contactSplitC2 = if ((lower.startsWith("whatsapp ") || lower.startsWith("wa ")) &&
            !lower.startsWith("whatsapp is") && !lower.startsWith("whatsapp web")
        ) {
            splitLeadingContactAndMessage(waRemainder, contacts)
        } else null

        // Pattern D: "send whatsapp to <target>" (no message yet)
        val patternD = Regex(
            """^(?:send\s+)?(?:a\s+)?whatsapp(?:\s+message)?\s+(?:to\s+)?(?:my\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text)

        val (rawTarget, rawMsg) = when {
            patternA != null -> patternA.groupValues[1].trim() to patternA.groupValues[2].trim()
            patternB != null -> patternB.groupValues[1].trim() to patternB.groupValues[2].trim()
            patternB2 != null -> patternB2.groupValues[1].trim() to patternB2.groupValues[2].trim()
            patternC != null -> patternC.groupValues[1].trim() to patternC.groupValues[2].trim()
            contactSplitC2 != null -> contactSplitC2
            patternD != null && !lower.contains("what is") && !lower.contains("how to") ->
                patternD.groupValues[1].trim() to ""
            else -> return null
        }

        val cleanTarget = rawTarget.trim().trimEnd(':', ',', '.', '!')
        val phoneInside = Regex("""(\+?\d[\d\s\-()]{6,18}\d)""").find(cleanTarget)
        if (phoneInside != null) {
            val digits = normalizeWhatsAppDigits(phoneInside.value)
            val byPhone = resolveContactByPhone(digits, contacts)
            return DeviceAction.SendWhatsApp(
                phoneNumber = digits,
                message = rawMsg.trim(),
                contactLabel = byPhone?.name
            )
        }

        val matches = resolveMatchingContacts(cleanTarget, contacts, requirePhone = true)
        val distinctPhones = matches.distinctBy { normalizeWhatsAppDigits(it.phoneNumber) }
        if (distinctPhones.size >= 2) {
            return DeviceAction.SendWhatsApp(
                phoneNumber = "",
                message = rawMsg.trim(),
                contactLabel = cleanTarget,
                ambiguousMatches = distinctPhones
            )
        }
        val matchedContact = distinctPhones.firstOrNull()
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

    /**
     * Handles natural messaging commands without an explicit "WhatsApp" keyword
     * (e.g., "message Manisha hi", "msg Mom I reached safely", "send a message to Rahul hello").
     */
    private fun parseGenericMessagePrompt(
        rawText: String,
        contacts: List<QuickContact>
    ): DeviceAction.SendWhatsApp? {
        val text = rawText.replace(
            Regex("""^(?:hey\s+allchat[\s,]+|please\s+|can\s+you\s+(?:please\s+)?|could\s+you\s+(?:please\s+)?)+""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        val lower = text.lowercase(Locale.US)
        if (lower.contains("sms") || lower.contains("email") || lower.contains("mail") ||
            lower.contains("slack") || lower.contains("telegram") || lower.contains("instagram")
        ) {
            return null
        }

        val headMatch = Regex(
            """^(?:send\s+(?:a\s+)?(?:message|msg)\s+to|message|msg)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return null

        val remainder = headMatch.groupValues[1].trim()
        if (remainder.isEmpty()) return null

        // Direct phone number: "message +91 9876543210 hi"
        val phoneFirst = Regex("""^(\+?\d[\d\s\-()]{6,18}\d)(?:\s+(.+))?$""").find(remainder)
        if (phoneFirst != null) {
            val digits = normalizeWhatsAppDigits(phoneFirst.groupValues[1])
            val byPhone = resolveContactByPhone(digits, contacts)
            return DeviceAction.SendWhatsApp(
                phoneNumber = digits,
                message = phoneFirst.groupValues[2].trim(),
                contactLabel = byPhone?.name
            )
        }

        val split = splitLeadingContactAndMessage(remainder, contacts)
        val (rawTarget, rawMsg) = if (split != null) {
            split
        } else {
            val tokens = remainder.replace(Regex("""^(?:to\s+)?(?:my\s+)?""", RegexOption.IGNORE_CASE), "")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) return null
            // If no saved contact matched, only interpret short direct commands like "message Manisha hi"
            if (lower.startsWith("message ") || lower.startsWith("msg ") || lower.startsWith("send a message to ") || lower.startsWith("send message to ")) {
                val targetWord = tokens.first().trimEnd(':', ',', '.', '!')
                val msgBody = tokens.drop(1).joinToString(" ").trim()
                targetWord to msgBody
            } else {
                return null
            }
        }

        val cleanTarget = rawTarget.trim().trimEnd(':', ',', '.', '!')
        if (cleanTarget.isBlank()) return null

        val matches = resolveMatchingContacts(cleanTarget, contacts, requirePhone = true)
        val distinctPhones = matches.distinctBy { normalizeWhatsAppDigits(it.phoneNumber) }
        return when {
            distinctPhones.size >= 2 -> DeviceAction.SendWhatsApp(
                phoneNumber = "",
                message = rawMsg.trim(),
                contactLabel = cleanTarget,
                ambiguousMatches = distinctPhones
            )
            distinctPhones.size == 1 -> {
                val c = distinctPhones.first()
                DeviceAction.SendWhatsApp(
                    phoneNumber = normalizeWhatsAppDigits(c.phoneNumber),
                    message = rawMsg.trim(),
                    contactLabel = c.name
                )
            }
            else -> DeviceAction.SendWhatsApp(
                phoneNumber = "",
                message = rawMsg.trim(),
                contactLabel = cleanTarget
            )
        }
    }

    /**
     * Executes a Call action using an optional user-configured local Auto-Dial Webhook URL
     * (MacroDroid / Tasker ACTION_CALL bridge) for completely hands-free calling on 1-tap earbud
     * confirmation, automatically falling back to the direct Android Dialer (`intent://` -> `tel:`).
     */
    suspend fun executeDialWithBridge(
        context: Context,
        action: DeviceAction.DialPhone,
        autoDialWebhookUrl: String
    ): Result<DialExecutionResult> {
        val clean = action.phoneNumber.replace(Regex("""[^\d+*#]"""), "")
        val resolvedName = action.contactLabel?.takeIf { it.isNotBlank() }
        val targetLabel = when {
            resolvedName != null && clean.isNotBlank() -> "$resolvedName ($clean)"
            resolvedName != null -> resolvedName
            clean.isNotBlank() -> clean
            else -> "Phone Dialer"
        }

        val trimmedWebhook = autoDialWebhookUrl.trim()
        if (clean.isNotBlank() && (trimmedWebhook.startsWith("http://") || trimmedWebhook.startsWith("https://"))) {
            val bridgeOk = withContext(Dispatchers.IO) {
                runCatching {
                    val encodedNumber = URLEncoder.encode(clean, "UTF-8")
                    val digitsOnly = clean.filter { it.isDigit() }
                    val encodedName = URLEncoder.encode(resolvedName.orEmpty(), "UTF-8")
                    val finalUrl = if (trimmedWebhook.contains("{number}") ||
                        trimmedWebhook.contains("{digits}") ||
                        trimmedWebhook.contains("{name}")
                    ) {
                        trimmedWebhook
                            .replace("{number}", encodedNumber)
                            .replace("{digits}", digitsOnly)
                            .replace("{name}", encodedName)
                    } else {
                        val sep = if (trimmedWebhook.contains("?")) "&" else "?"
                        "${trimmedWebhook}${sep}number=$encodedNumber&name=$encodedName"
                    }
                    val req = Request.Builder().url(finalUrl).get().build()
                    bridgeHttpClient.newCall(req).execute().use { resp -> resp.isSuccessful }
                }.getOrDefault(false)
            }
            if (bridgeOk) {
                return Result.success(
                    DialExecutionResult(
                        message = "Calling $targetLabel via Auto-Dial Webhook",
                        usedAutoDialBridge = true
                    )
                )
            }
        }

        return execute(context, action).map { msg ->
            DialExecutionResult(message = msg, usedAutoDialBridge = false)
        }
    }

    fun execute(context: Context, action: DeviceAction): Result<String> {
        return runCatching {
            when (action) {
                is DeviceAction.DialPhone -> {
                    val clean = action.phoneNumber.replace(Regex("""[^\d+*#]"""), "")
                    val dialUri = if (clean.isNotBlank()) {
                        Uri.fromParts("tel", clean, null)
                    } else {
                        Uri.parse("tel:")
                    }
                    // Launch system Phone Dialer directly via Android intent:// scheme with automatic tel: fallback
                    val intentUriString = "intent://$clean#Intent;scheme=tel;action=android.intent.action.DIAL;end"
                    val directDialerIntent = runCatching {
                        Intent.parseUri(intentUriString, Intent.URI_INTENT_SCHEME).apply {
                            this.action = Intent.ACTION_DIAL
                            data = dialUri
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val resolvedPkg = context.packageManager.resolveActivity(this, 0)?.activityInfo?.packageName
                            if (!resolvedPkg.isNullOrBlank() && resolvedPkg != "android") {
                                setPackage(resolvedPkg)
                            }
                        }
                    }.getOrNull()

                    val fallbackTelIntent = Intent(Intent.ACTION_DIAL, dialUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    if (directDialerIntent != null) {
                        runCatching { context.startActivity(directDialerIntent) }
                            .onFailure { context.startActivity(fallbackTelIntent) }
                    } else {
                        context.startActivity(fallbackTelIntent)
                    }

                    val resolvedName = action.contactLabel?.takeIf { it.isNotBlank() }
                    when {
                        resolvedName != null && clean.isNotBlank() -> "Calling $resolvedName ($clean)"
                        resolvedName != null -> "Calling $resolvedName"
                        clean.isNotBlank() -> "Calling $clean"
                        else -> "Opened Phone Dialer"
                    }
                }

                is DeviceAction.WhatsAppCall -> {
                    val digits = normalizeWhatsAppDigits(action.phoneNumber)
                    if (digits.isBlank()) {
                        throw IllegalArgumentException("Enter or select a phone number first to open WhatsApp call.")
                    }
                    // Open the contact's WhatsApp screen directly via intent:// with https://wa.me/ fallback
                    val directIntentUri =
                        "intent://send?phone=$digits#Intent;package=com.whatsapp;scheme=whatsapp;end"
                    val directIntent = runCatching {
                        Intent.parseUri(directIntentUri, Intent.URI_INTENT_SCHEME).apply {
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }.getOrNull()
                    val fallbackWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (directIntent != null) {
                        runCatching { context.startActivity(directIntent) }
                            .onFailure { context.startActivity(fallbackWebIntent) }
                    } else {
                        context.startActivity(fallbackWebIntent)
                    }
                    val modeLabel = if (action.isVideoCall) "WhatsApp video call" else "WhatsApp voice call"
                    "Opened $modeLabel screen for ${action.contactLabel ?: "+$digits"}"
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
                    val encodedMsg = URLEncoder.encode(action.message, "UTF-8").replace("+", "%20")
                    val querySuffix = if (action.message.isNotBlank()) "&text=$encodedMsg" else ""
                    val directIntentUri =
                        "intent://send?phone=$digits$querySuffix#Intent;package=com.whatsapp;scheme=whatsapp;end"
                    val directIntent = runCatching {
                        Intent.parseUri(directIntentUri, Intent.URI_INTENT_SCHEME).apply {
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }.getOrNull()

                    val waWebUri = Uri.parse(
                        "https://wa.me/$digits${if (action.message.isNotBlank()) "?text=$encodedMsg" else ""}"
                    )
                    val fallbackWebIntent = Intent(Intent.ACTION_VIEW, waWebUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    if (directIntent != null) {
                        runCatching { context.startActivity(directIntent) }
                            .onFailure { context.startActivity(fallbackWebIntent) }
                    } else {
                        context.startActivity(fallbackWebIntent)
                    }
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

    /**
     * Fallback Streamed Action Interceptor:
     * Even if an LLM (like Qwen3 or a non-tool-calling model) replies with plain confirmation text
     * ("Ready to send WhatsApp to Vimla...", "Tap confirmation to execute", "Calling Vimla...")
     * instead of invoking `execute_device_action`, strip `<think>` blocks and extract the interactive
     * WhatsApp / Call DeviceAction so the confirmation card still renders automatically.
     */
    fun parseFromAssistantReply(
        userPrompt: String,
        assistantReply: String,
        contacts: List<QuickContact> = emptyList(),
        customApps: List<CustomAppEntry> = emptyList()
    ): DeviceAction? {
        val visibleReply = assistantReply
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<reasoning>[\s\S]*?</reasoning>""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (visibleReply.isBlank()) return null

        val lowerReply = visibleReply.lowercase(Locale.US)
        val lowerPrompt = userPrompt.lowercase(Locale.US)

        // 1. Direct wa.me URL in assistant reply
        val waUrlMatch = Regex("""https?://wa\.me/(\d{7,15})(?:\?text=([^\s)\]]+))?""", RegexOption.IGNORE_CASE)
            .find(visibleReply)
        if (waUrlMatch != null) {
            val digits = normalizeWhatsAppDigits(waUrlMatch.groupValues[1])
            val decodedMsg = runCatching {
                java.net.URLDecoder.decode(waUrlMatch.groupValues.getOrNull(2).orEmpty(), "UTF-8")
            }.getOrDefault("")
            val matchedContact = resolveContactByPhone(digits, contacts)
            return DeviceAction.SendWhatsApp(
                phoneNumber = digits,
                message = decodedMsg,
                contactLabel = matchedContact?.name
            )
        }

        val soundsLikeConfirmation = lowerReply.contains("ready to send") ||
            lowerReply.contains("tap confirmation") ||
            lowerReply.contains("confirm to send") ||
            lowerReply.contains("confirm to execute") ||
            lowerReply.contains("open whatsapp") ||
            lowerReply.contains("sending whatsapp") ||
            lowerReply.contains("whatsapp message to") ||
            lowerReply.contains("opening phone dialer") ||
            lowerReply.contains("ready to call") ||
            lowerReply.contains("calling ") ||
            lowerReply.contains("dialing ") ||
            lowerReply.contains("wants to contact") ||
            lowerReply.contains("wants to call") ||
            lowerReply.contains("wants to message")

        // 2. If user prompt itself parses cleanly AND the assistant confirmed an action, return it
        if (soundsLikeConfirmation) {
            parseFromPrompt(userPrompt, contacts, customApps)?.let { return it }
        }

        // 2.5 If model replied "User wants to contact <Name>" without invoking tool, resolve directly to DialPhone
        val wantsContactMatch = Regex(
            """(?:wants\s+to\s+(?:contact|call|ring|reach)|contacting|calling)\s+\*?\*?([A-Za-z][A-Za-z0-9 ._-]{1,28})""",
            RegexOption.IGNORE_CASE
        ).find(visibleReply)
        if (wantsContactMatch != null) {
            val rawName = wantsContactMatch.groupValues[1]
                .trim()
                .trimEnd('.', ',', ':', ';', '*', '"', '\'')
            if (rawName.isNotBlank()) {
                val matches = resolveMatchingContacts(rawName, contacts, requirePhone = true)
                    .distinctBy { normalizePhoneNumber(it.phoneNumber) }
                if (matches.size >= 2) {
                    return DeviceAction.DialPhone(phoneNumber = "", contactLabel = rawName, ambiguousMatches = matches)
                }
                matches.firstOrNull()?.let { c ->
                    return DeviceAction.DialPhone(
                        phoneNumber = normalizePhoneNumber(c.phoneNumber),
                        contactLabel = c.name
                    )
                }
            }
        }

        // 3. Extract WhatsApp intent from plain confirmation text
        val mentionsWhatsApp = lowerReply.contains("whatsapp") || lowerPrompt.contains("whatsapp")
        if (mentionsWhatsApp && soundsLikeConfirmation) {
            val phoneMatch = Regex("""(\+?\d[\d\s\-()]{7,18}\d)""").find(visibleReply)
                ?: Regex("""(\+?\d[\d\s\-()]{7,18}\d)""").find(userPrompt)
            val recipientMatch = Regex(
                """(?:whatsapp(?:\s+message)?\s+to|message\s+to|send\s+to|recipient[:\s]+)\s*\*?\*?([A-Za-z][A-Za-z0-9 ._-]{1,28})""",
                RegexOption.IGNORE_CASE
            ).find(visibleReply)
            val rawRecipient = recipientMatch?.groupValues?.get(1)
                ?.trim()
                ?.trimEnd('.', ',', ':', ';', '*', '"', '\'')
                ?.substringBefore(" (")
                ?.trim()
                .orEmpty()

            val quotedMsg = Regex("""["“']([^"”'\n]{1,240})["”']""").find(visibleReply)?.groupValues?.get(1)
                ?: Regex("""(?:message|text)\s*:\s*(.+)""", RegexOption.IGNORE_CASE).find(visibleReply)?.groupValues?.get(1)?.trim()
                ?: ""

            if (phoneMatch != null) {
                val digits = normalizeWhatsAppDigits(phoneMatch.value)
                val byPhone = resolveContactByPhone(digits, contacts)
                val label = byPhone?.name ?: rawRecipient.takeIf { it.isNotBlank() }
                return DeviceAction.SendWhatsApp(
                    phoneNumber = digits,
                    message = quotedMsg,
                    contactLabel = label
                )
            }
            if (rawRecipient.isNotBlank()) {
                val matches = resolveMatchingContacts(rawRecipient, contacts, requirePhone = true)
                    .distinctBy { normalizeWhatsAppDigits(it.phoneNumber) }
                return when {
                    matches.size >= 2 -> DeviceAction.SendWhatsApp(
                        phoneNumber = "",
                        message = quotedMsg,
                        contactLabel = rawRecipient,
                        ambiguousMatches = matches
                    )
                    matches.size == 1 -> DeviceAction.SendWhatsApp(
                        phoneNumber = normalizeWhatsAppDigits(matches.first().phoneNumber),
                        message = quotedMsg,
                        contactLabel = matches.first().name
                    )
                    else -> DeviceAction.SendWhatsApp(
                        phoneNumber = "",
                        message = quotedMsg,
                        contactLabel = rawRecipient
                    )
                }
            }
        }

        // 4. Extract Call intent from plain confirmation text
        val mentionsCall = (lowerReply.contains("call") || lowerReply.contains("dial")) &&
            (lowerReply.contains("ready to call") || lowerReply.contains("calling ") || lowerReply.contains("dialing ") || lowerReply.contains("open dialer") || lowerReply.contains("phone dialer"))
        if (mentionsCall) {
            val phoneMatch = Regex("""(\+?\d[\d\s\-()]{7,18}\d)""").find(visibleReply)
                ?: Regex("""(\+?\d[\d\s\-()]{7,18}\d)""").find(userPrompt)
            val nameMatch = Regex(
                """(?:calling|dialing|ready\s+to\s+call|call)\s+\*?\*?([A-Za-z][A-Za-z0-9 ._-]{1,28})""",
                RegexOption.IGNORE_CASE
            ).find(visibleReply)
            val rawName = nameMatch?.groupValues?.get(1)
                ?.trim()
                ?.trimEnd('.', ',', ':', ';', '*', '"', '\'')
                ?.substringBefore(" (")
                ?.trim()
                .orEmpty()

            if (phoneMatch != null) {
                val cleanNum = normalizePhoneNumber(phoneMatch.value)
                val byPhone = resolveContactByPhone(cleanNum, contacts)
                return DeviceAction.DialPhone(
                    phoneNumber = cleanNum,
                    contactLabel = byPhone?.name ?: rawName.takeIf { it.isNotBlank() }
                )
            }
            if (rawName.isNotBlank()) {
                val matches = resolveMatchingContacts(rawName, contacts, requirePhone = true)
                    .distinctBy { normalizePhoneNumber(it.phoneNumber) }
                if (matches.size >= 2) {
                    return DeviceAction.DialPhone(phoneNumber = "", contactLabel = rawName, ambiguousMatches = matches)
                }
                matches.firstOrNull()?.let { c ->
                    return DeviceAction.DialPhone(
                        phoneNumber = normalizePhoneNumber(c.phoneNumber),
                        contactLabel = c.name
                    )
                }
            }
        }

        return null
    }
}
