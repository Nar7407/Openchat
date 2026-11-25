package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.QuickContact
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine
import kotlinx.coroutines.delay

@Composable
fun DeviceActionConfirmCard(
    action: DeviceAction,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
    quickContacts: List<QuickContact> = emptyList(),
    onUpdateAction: ((DeviceAction) -> Unit)? = null,
    onSaveQuickContact: ((name: String, phone: String) -> Unit)? = null,
    autoLaunchDialer: Boolean = true,
    autoLaunchWhatsApp: Boolean = true,
    showEarbudHints: Boolean = true,
    autoDialWebhookUrl: String = "",
    onDialLaunched: ((DeviceAction.DialPhone, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val icon = when (action) {
        is DeviceAction.DialPhone -> Icons.Outlined.Call
        is DeviceAction.WhatsAppCall -> if (action.isVideoCall) Icons.Outlined.Videocam else Icons.Outlined.Call
        is DeviceAction.OpenYouTube -> Icons.Outlined.PlayCircleOutline
        is DeviceAction.SendWhatsApp -> Icons.Outlined.Chat
        is DeviceAction.SetAlarm -> Icons.Outlined.Alarm
        is DeviceAction.SetTimer -> Icons.Outlined.Timer
        is DeviceAction.OpenApp -> Icons.Outlined.Launch
        is DeviceAction.OpenMaps -> Icons.Outlined.Map
        is DeviceAction.CreateCalendarEvent -> Icons.Outlined.Event
        is DeviceAction.SendSms -> Icons.Outlined.Sms
        is DeviceAction.SendEmail -> Icons.Outlined.Email
        is DeviceAction.UpiPay -> Icons.Outlined.AccountBalanceWallet
    }

    val effectiveAction: DeviceAction = remember(action, quickContacts) {
        when (action) {
            is DeviceAction.DialPhone -> {
                if (action.contactLabel.isNullOrBlank() && action.phoneNumber.isNotBlank()) {
                    val matched = DeviceActionEngine.resolveContactByPhone(action.phoneNumber, quickContacts)
                    if (matched != null) action.copy(contactLabel = matched.name) else action
                } else {
                    action
                }
            }
            is DeviceAction.WhatsAppCall -> {
                if (action.contactLabel.isNullOrBlank() && action.phoneNumber.isNotBlank()) {
                    val matched = DeviceActionEngine.resolveContactByPhone(action.phoneNumber, quickContacts)
                    if (matched != null) action.copy(contactLabel = matched.name) else action
                } else {
                    action
                }
            }
            is DeviceAction.SendWhatsApp -> {
                if (action.contactLabel.isNullOrBlank() && action.phoneNumber.isNotBlank()) {
                    val matched = DeviceActionEngine.resolveContactByPhone(action.phoneNumber, quickContacts)
                    if (matched != null) action.copy(contactLabel = matched.name) else action
                } else {
                    action
                }
            }
            else -> action
        }
    }

    val ctaLabel = when (effectiveAction) {
        is DeviceAction.DialPhone -> {
            val target = effectiveAction.contactLabel?.takeIf { it.isNotBlank() }
            if (target != null) "OPEN DIALER / CALL ${target.uppercase()}" else "OPEN DIALER"
        }
        is DeviceAction.WhatsAppCall -> {
            val target = effectiveAction.contactLabel?.takeIf { it.isNotBlank() }?.uppercase()
            val mode = if (effectiveAction.isVideoCall) "WHATSAPP VIDEO" else "WHATSAPP CALL"
            if (target != null) "OPEN $mode · $target" else "OPEN $mode"
        }
        is DeviceAction.OpenYouTube -> "OPEN YOUTUBE"
        is DeviceAction.SendWhatsApp -> "SEND ON WHATSAPP"
        is DeviceAction.SetAlarm -> "SET ALARM"
        is DeviceAction.SetTimer -> "START TIMER"
        is DeviceAction.OpenApp -> "OPEN APP"
        is DeviceAction.OpenMaps -> if (effectiveAction.isNavigation) "NAVIGATE" else "OPEN MAPS"
        is DeviceAction.CreateCalendarEvent -> "ADD EVENT"
        is DeviceAction.SendSms -> "OPEN SMS"
        is DeviceAction.SendEmail -> "COMPOSE MAIL"
        is DeviceAction.UpiPay -> "PAY VIA UPI"
    }

    val ambiguousMatches: List<QuickContact> = when (action) {
        is DeviceAction.DialPhone -> action.ambiguousMatches
        is DeviceAction.WhatsAppCall -> action.ambiguousMatches
        is DeviceAction.SendWhatsApp -> action.ambiguousMatches
        is DeviceAction.SendSms -> action.ambiguousMatches
        else -> emptyList()
    }

    val needsPhoneNumber = when (action) {
        is DeviceAction.DialPhone -> action.phoneNumber.isBlank()
        is DeviceAction.WhatsAppCall -> action.phoneNumber.isBlank()
        is DeviceAction.SendWhatsApp -> action.phoneNumber.isBlank()
        is DeviceAction.SendSms -> action.phoneNumber.isBlank()
        else -> false
    }
    val needsEmail = action is DeviceAction.SendEmail && action.emailAddress.isBlank()
    val needsUpi = action is DeviceAction.UpiPay && (action.upiId.isBlank() || !action.upiId.contains("@"))

    var inlineDraft by remember(action) { mutableStateOf("") }
    var dialerAutoTriggered by remember(action) { mutableStateOf(false) }

    // 1. Streamlined Phone Calling: immediately launch direct Android dialer (or Auto-Dial Webhook bridge)
    // when a clean resolved number arrives, while keeping the full-screen Calling Overlay & action card visible.
    LaunchedEffect(effectiveAction, autoLaunchDialer, autoDialWebhookUrl) {
        if (autoLaunchDialer && effectiveAction is DeviceAction.DialPhone && effectiveAction.phoneNumber.isNotBlank() && effectiveAction.ambiguousMatches.isEmpty()) {
            if (!dialerAutoTriggered) {
                dialerAutoTriggered = true
                onDialLaunched?.invoke(effectiveAction, autoDialWebhookUrl.trim().startsWith("http"))
                DeviceActionEngine.executeDialWithBridge(context, effectiveAction, autoDialWebhookUrl)
                    .onSuccess { res ->
                        onDialLaunched?.invoke(effectiveAction, res.usedAutoDialBridge)
                    }
            }
        }
    }

    // 2. Hands-Free WhatsApp Auto-Launch: 3-second countdown with instant Cancel when a valid single number is ready.
    val canAutoLaunchWhatsApp = autoLaunchWhatsApp &&
        action is DeviceAction.SendWhatsApp &&
        action.phoneNumber.isNotBlank() &&
        action.ambiguousMatches.isEmpty()
    var waCountdownSec by remember(action) { mutableIntStateOf(if (canAutoLaunchWhatsApp) 3 else 0) }
    var waAutoCancelled by remember(action) { mutableStateOf(false) }

    LaunchedEffect(action, canAutoLaunchWhatsApp, waAutoCancelled) {
        if (canAutoLaunchWhatsApp && !waAutoCancelled) {
            for (sec in 3 downTo 1) {
                waCountdownSec = sec
                delay(1000L)
                if (waAutoCancelled) return@LaunchedEffect
            }
            waCountdownSec = 0
            if (!waAutoCancelled) {
                onExecute()
            }
        }
    }

    fun applyPhoneNumber(rawPhone: String, labelOverride: String? = null): DeviceAction {
        val clean = rawPhone.trim()
        return when (action) {
            is DeviceAction.DialPhone -> action.copy(
                phoneNumber = DeviceActionEngine.normalizePhoneNumber(clean),
                contactLabel = labelOverride ?: action.contactLabel,
                ambiguousMatches = emptyList()
            )
            is DeviceAction.WhatsAppCall -> action.copy(
                phoneNumber = DeviceActionEngine.normalizeWhatsAppDigits(clean),
                contactLabel = labelOverride ?: action.contactLabel,
                ambiguousMatches = emptyList()
            )
            is DeviceAction.SendWhatsApp -> action.copy(
                phoneNumber = DeviceActionEngine.normalizeWhatsAppDigits(clean),
                contactLabel = labelOverride ?: action.contactLabel,
                ambiguousMatches = emptyList()
            )
            is DeviceAction.SendSms -> action.copy(
                phoneNumber = DeviceActionEngine.normalizePhoneNumber(clean),
                contactLabel = labelOverride ?: action.contactLabel,
                ambiguousMatches = emptyList()
            )
            else -> action
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper, RectangleShape)
            .border(1.5.dp, SwissInk, RectangleShape)
            .testTag("device_action_confirm_card")
    ) {
        // Stark Swiss Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissInk)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SwissSignalRed,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = action.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissWhite
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss action",
                tint = SwissWhite,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        waAutoCancelled = true
                        onDismiss()
                    }
                    .testTag("dismiss_device_action")
            )
        }

        // Dedicated High-Contrast WhatsApp Confirmation Panel (Recipient Name, Number, Preview, 3s Auto-Launch + Large CTA)
        if (effectiveAction is DeviceAction.SendWhatsApp) {
            val waAction = effectiveAction
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = waAction.contactLabel?.takeIf { it.isNotBlank() } ?: "WhatsApp Recipient",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = SwissInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (waAction.phoneNumber.isNotBlank()) "+${waAction.phoneNumber}" else "Select a saved contact or enter phone number below",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (waAction.phoneNumber.isNotBlank()) SwissVerifiedInk else SwissSignalRed
                        )
                    }
                    if (canAutoLaunchWhatsApp && !waAutoCancelled && waCountdownSec > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OPENING IN ${waCountdownSec}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                modifier = Modifier
                                    .background(SwissInk, RectangleShape)
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                    .testTag("whatsapp_countdown_badge")
                            )
                            Text(
                                text = "CANCEL",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                modifier = Modifier
                                    .background(SwissPaper, RectangleShape)
                                    .border(1.dp, SwissSignalRed, RectangleShape)
                                    .clickable { waAutoCancelled = true }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                    .testTag("whatsapp_cancel_countdown")
                            )
                        }
                    } else if (waAutoCancelled && waAction.phoneNumber.isNotBlank()) {
                        Text(
                            text = "AUTO-OPEN PAUSED",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
                        )
                    }
                }

                // Message Preview Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissCanvas, RectangleShape)
                        .border(1.dp, SwissInk, RectangleShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "MESSAGE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    Text(
                        text = waAction.message.ifBlank { "(Open WhatsApp chat directly — no pre-filled message)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwissInk,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Large High-Contrast Primary CTA Button
                if (!needsPhoneNumber || inlineDraft.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(SwissSignalRed, RectangleShape)
                            .clickable {
                                waAutoCancelled = true
                                if (needsPhoneNumber && inlineDraft.isNotBlank() && onUpdateAction != null) {
                                    onUpdateAction(applyPhoneNumber(inlineDraft))
                                } else {
                                    onExecute()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("execute_device_action"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Chat,
                                contentDescription = null,
                                tint = SwissWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (needsPhoneNumber && inlineDraft.isNotBlank()) "APPLY NUMBER" else "SEND ON WHATSAPP",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = SwissWhite
                            )
                        }
                    }
                    if (showEarbudHints && !needsPhoneNumber && waAction.ambiguousMatches.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("earbud_action_status_pill"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "[🎧 1x TAP EARBUD: CONFIRM]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SwissInk, RectangleShape)
                                    .clickable {
                                        waAutoCancelled = true
                                        onExecute()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                            Text(
                                text = "[⏭ 2x TAP: CANCEL]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(SwissCanvas, RectangleShape)
                                    .border(1.dp, SwissSignalRed, RectangleShape)
                                    .clickable {
                                        waAutoCancelled = true
                                        onDismiss()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        } else if (effectiveAction is DeviceAction.WhatsAppCall) {
            // Dedicated WhatsApp Voice / Video Call Confirmation Card (opens contact's WhatsApp screen directly)
            val waCall = effectiveAction
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = waCall.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = waCall.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (waCall.phoneNumber.isNotBlank()) SwissVerifiedInk else SwissSignalRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (waCall.phoneNumber.isNotBlank() && waCall.ambiguousMatches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(SwissSignalRed, RectangleShape)
                            .clickable { onExecute() }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("execute_device_action"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (waCall.isVideoCall) Icons.Outlined.Videocam else Icons.Outlined.Call,
                                contentDescription = null,
                                tint = SwissWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = ctaLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = SwissWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (showEarbudHints) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("earbud_action_status_pill"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "[🎧 1x TAP EARBUD: CONFIRM]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SwissInk, RectangleShape)
                                    .clickable { onExecute() }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                            Text(
                                text = "[⏭ 2x TAP: CANCEL]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(SwissCanvas, RectangleShape)
                                    .border(1.dp, SwissSignalRed, RectangleShape)
                                    .clickable { onDismiss() }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        } else if (effectiveAction is DeviceAction.DialPhone) {
            // Streamlined Direct Dialer Card (auto-launched intent:// dialer + prominent re-tap button + Android security note)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = effectiveAction.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = effectiveAction.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (effectiveAction.phoneNumber.isNotBlank() && effectiveAction.ambiguousMatches.isEmpty()) {
                    Text(
                        text = if (dialerAutoTriggered) {
                            "DIALER OPENED // Tap the green call button in Android's Phone app to place the call (Android security requires final confirmation)."
                        } else {
                            "Android security requires tapping the green call button in the system dialer."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissVerifiedInk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissVerifiedTint, RectangleShape)
                            .border(1.dp, SwissInk, RectangleShape)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(SwissSignalRed, RectangleShape)
                            .clickable { onExecute() }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("execute_device_action"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Call,
                                contentDescription = null,
                                tint = SwissWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = ctaLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = SwissWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (showEarbudHints) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("earbud_action_status_pill"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "[🎧 1x TAP EARBUD: CONFIRM]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SwissInk, RectangleShape)
                                    .clickable { onExecute() }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                            Text(
                                text = "[⏭ 2x TAP: CANCEL]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(SwissCanvas, RectangleShape)
                                    .border(1.dp, SwissSignalRed, RectangleShape)
                                    .clickable { onDismiss() }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Standard Summary + Action CTA Row for Alarms, Timers, YouTube, Maps, Calendar, SMS, Email, UPI, OpenApp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = SwissInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = action.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .background(SwissSignalRed, RectangleShape)
                        .clickable {
                            when {
                                needsPhoneNumber && inlineDraft.isNotBlank() && onUpdateAction != null -> {
                                    onUpdateAction(applyPhoneNumber(inlineDraft))
                                }
                                needsEmail && inlineDraft.isNotBlank() && onUpdateAction != null && action is DeviceAction.SendEmail -> {
                                    onUpdateAction(action.copy(emailAddress = inlineDraft.trim()))
                                }
                                needsUpi && inlineDraft.isNotBlank() && onUpdateAction != null && action is DeviceAction.UpiPay -> {
                                    onUpdateAction(action.copy(upiId = inlineDraft.trim()))
                                }
                                else -> onExecute()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("execute_device_action"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            (needsPhoneNumber || needsEmail || needsUpi) && inlineDraft.isNotBlank() -> "APPLY"
                            else -> ctaLabel
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SwissWhite
                    )
                }
            }
        }

        // Quick-Select Multi-Match Contact Chips OR Missing Contact/Number/Email/UPI Picker
        if ((ambiguousMatches.size >= 2 || needsPhoneNumber || needsEmail || needsUpi) && onUpdateAction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SwissInk.copy(alpha = 0.25f))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSubtleFill)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val targetName = when (action) {
                    is DeviceAction.DialPhone -> action.contactLabel
                    is DeviceAction.WhatsAppCall -> action.contactLabel
                    is DeviceAction.SendWhatsApp -> action.contactLabel
                    is DeviceAction.SendSms -> action.contactLabel
                    is DeviceAction.SendEmail -> action.contactLabel
                    is DeviceAction.UpiPay -> action.payeeName
                    else -> null
                }
                val promptLabel = when {
                    ambiguousMatches.size >= 2 ->
                        "FOUND ${ambiguousMatches.size} CONTACTS NAMED \"${targetName.orEmpty()}\" // SAY \"FIRST\" OR \"SECOND\", OR TAP ONE"
                    needsUpi -> "UPI ID NEEDED${targetName?.let { " FOR \"$it\"" }.orEmpty()} // PICK SAVED OR ENTER VPA"
                    needsEmail -> "EMAIL NEEDED${targetName?.let { " FOR \"$it\"" }.orEmpty()} // PICK SAVED OR ENTER BELOW"
                    else -> "NUMBER NEEDED${targetName?.let { " FOR \"$it\"" }.orEmpty()} // PICK SAVED OR ENTER BELOW"
                }
                Text(
                    text = promptLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )

                val chipContacts = remember(ambiguousMatches, quickContacts, needsPhoneNumber, needsEmail, needsUpi) {
                    if (ambiguousMatches.size >= 2) {
                        ambiguousMatches
                    } else {
                        when {
                            needsUpi -> quickContacts.filter { it.upiId.isNotBlank() }
                            needsEmail -> quickContacts.filter { it.email.isNotBlank() }
                            else -> quickContacts.filter { it.phoneNumber.isNotBlank() }
                        }
                    }
                }

                if (chipContacts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chipContacts.forEachIndexed { idx, contact ->
                            val chipValue = when {
                                needsUpi -> contact.upiId
                                needsEmail -> contact.email
                                else -> contact.phoneNumber
                            }
                            val isDisambig = ambiguousMatches.size >= 2
                            Row(
                                modifier = Modifier
                                    .heightIn(min = if (isDisambig) 44.dp else 36.dp)
                                    .background(if (isDisambig) SwissInk else SwissPaper, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable {
                                        when {
                                            needsUpi && action is DeviceAction.UpiPay ->
                                                onUpdateAction(action.copy(upiId = contact.upiId, payeeName = contact.name))
                                            needsEmail && action is DeviceAction.SendEmail ->
                                                onUpdateAction(action.copy(emailAddress = contact.email, contactLabel = contact.name))
                                            else ->
                                                onUpdateAction(applyPhoneNumber(contact.phoneNumber, contact.name))
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 7.dp)
                                    .testTag(if (isDisambig) "contact_disambig_pill_$idx" else "contact_quick_chip_$idx"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isDisambig) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SwissWhite,
                                        modifier = Modifier
                                            .background(SwissSignalRed, RectangleShape)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isDisambig) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isDisambig) SwissWhite else SwissInk
                                )
                                Text(
                                    text = chipValue,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDisambig) SwissSignalRed else SwissMuted
                                )
                            }
                        }
                    }
                }

                if (needsPhoneNumber || needsEmail || needsUpi) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inlineDraft,
                            onValueChange = { inlineDraft = it },
                            placeholder = {
                                Text(
                                    when {
                                        needsUpi -> "name@okaxis / 9876543210@ybl"
                                        needsEmail -> "recipient@example.com"
                                        else -> "+91 9876543210"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = RectangleShape,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when {
                                    needsPhoneNumber -> KeyboardType.Phone
                                    needsEmail -> KeyboardType.Email
                                    else -> KeyboardType.Text
                                }
                            ),
                            colors = swissTextFieldColors(containerColor = SwissCanvas),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("device_action_inline_phone")
                        )
                        if (needsPhoneNumber && !targetName.isNullOrBlank() && onSaveQuickContact != null) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (inlineDraft.isNotBlank()) SwissInk else SwissMuted.copy(alpha = 0.4f),
                                        RectangleShape
                                    )
                                    .clickable(enabled = inlineDraft.isNotBlank()) {
                                        val normalized = DeviceActionEngine.normalizePhoneNumber(inlineDraft)
                                        onSaveQuickContact(targetName, normalized)
                                        onUpdateAction(applyPhoneNumber(normalized, targetName))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 14.dp)
                                    .testTag("device_action_save_contact")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PersonAdd,
                                        contentDescription = "Save to Quick Contacts",
                                        tint = SwissWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "SAVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissWhite
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (inlineDraft.isNotBlank()) SwissInk else SwissMuted.copy(alpha = 0.4f),
                                        RectangleShape
                                    )
                                    .clickable(enabled = inlineDraft.isNotBlank()) {
                                        when {
                                            needsUpi && action is DeviceAction.UpiPay ->
                                                onUpdateAction(action.copy(upiId = inlineDraft.trim()))
                                            needsEmail && action is DeviceAction.SendEmail ->
                                                onUpdateAction(action.copy(emailAddress = inlineDraft.trim()))
                                            else ->
                                                onUpdateAction(applyPhoneNumber(inlineDraft))
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = "USE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
