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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.SwissWhite
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine

@Composable
fun DeviceActionConfirmCard(
    action: DeviceAction,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
    quickContacts: List<QuickContact> = emptyList(),
    onUpdateAction: ((DeviceAction) -> Unit)? = null,
    onSaveQuickContact: ((name: String, phone: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val icon = when (action) {
        is DeviceAction.DialPhone -> Icons.Outlined.Call
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

    val ctaLabel = when (action) {
        is DeviceAction.DialPhone -> "OPEN DIALER"
        is DeviceAction.OpenYouTube -> "OPEN YOUTUBE"
        is DeviceAction.SendWhatsApp -> "OPEN WHATSAPP"
        is DeviceAction.SetAlarm -> "SET ALARM"
        is DeviceAction.SetTimer -> "START TIMER"
        is DeviceAction.OpenApp -> "OPEN APP"
        is DeviceAction.OpenMaps -> if (action.isNavigation) "NAVIGATE" else "OPEN MAPS"
        is DeviceAction.CreateCalendarEvent -> "ADD EVENT"
        is DeviceAction.SendSms -> "OPEN SMS"
        is DeviceAction.SendEmail -> "COMPOSE MAIL"
        is DeviceAction.UpiPay -> "PAY VIA UPI"
    }

    val needsPhoneNumber = when (action) {
        is DeviceAction.DialPhone -> action.phoneNumber.isBlank()
        is DeviceAction.SendWhatsApp -> action.phoneNumber.isBlank()
        is DeviceAction.SendSms -> action.phoneNumber.isBlank()
        else -> false
    }
    val needsEmail = action is DeviceAction.SendEmail && action.emailAddress.isBlank()
    val needsUpi = action is DeviceAction.UpiPay && (action.upiId.isBlank() || !action.upiId.contains("@"))

    var inlineDraft by remember(action) { mutableStateOf("") }

    fun applyPhoneNumber(rawPhone: String, labelOverride: String? = null): DeviceAction {
        val clean = rawPhone.trim()
        return when (action) {
            is DeviceAction.DialPhone -> action.copy(
                phoneNumber = DeviceActionEngine.normalizePhoneNumber(clean),
                contactLabel = labelOverride ?: action.contactLabel
            )
            is DeviceAction.SendWhatsApp -> action.copy(
                phoneNumber = DeviceActionEngine.normalizeWhatsAppDigits(clean),
                contactLabel = labelOverride ?: action.contactLabel
            )
            is DeviceAction.SendSms -> action.copy(
                phoneNumber = DeviceActionEngine.normalizePhoneNumber(clean),
                contactLabel = labelOverride ?: action.contactLabel
            )
            else -> action
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper, RectangleShape)
            .border(1.dp, SwissInk, RectangleShape)
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
                    .size(18.dp)
                    .clickable(onClick = onDismiss)
                    .testTag("dismiss_device_action")
            )
        }

        // Main Summary + Action CTA Row
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
                    .testTag("execute_device_action")
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

        // Inline Quick Contact / Number / Email / UPI ID Picker when recipient info is missing
        if ((needsPhoneNumber || needsEmail || needsUpi) && onUpdateAction != null) {
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
                    is DeviceAction.SendWhatsApp -> action.contactLabel
                    is DeviceAction.SendSms -> action.contactLabel
                    is DeviceAction.SendEmail -> action.contactLabel
                    is DeviceAction.UpiPay -> action.payeeName
                    else -> null
                }
                val promptLabel = when {
                    needsUpi -> "UPI ID NEEDED${targetName?.let { " FOR \"$it\"" }.orEmpty()} // PICK SAVED OR ENTER VPA"
                    needsEmail -> "EMAIL NEEDED${targetName?.let { " FOR \"$it\"" }.orEmpty()} // PICK SAVED OR ENTER BELOW"
                    else -> "NUMBER NEEDED${targetName?.let { " FOR \"$it\"" }.orEmpty()} // PICK SAVED OR ENTER BELOW"
                }
                Text(
                    text = promptLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )

                val matchingContacts = remember(quickContacts, needsPhoneNumber, needsEmail, needsUpi) {
                    when {
                        needsUpi -> quickContacts.filter { it.upiId.isNotBlank() }
                        needsEmail -> quickContacts.filter { it.email.isNotBlank() }
                        else -> quickContacts.filter { it.phoneNumber.isNotBlank() }
                    }
                }

                if (matchingContacts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchingContacts.forEach { contact ->
                            val chipValue = when {
                                needsUpi -> contact.upiId
                                needsEmail -> contact.email
                                else -> contact.phoneNumber
                            }
                            Row(
                                modifier = Modifier
                                    .background(SwissPaper, RectangleShape)
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
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissInk
                                )
                                Text(
                                    text = chipValue,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissMuted
                                )
                            }
                        }
                    }
                }

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
