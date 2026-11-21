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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayCircleOutline
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
    }

    val ctaLabel = when (action) {
        is DeviceAction.DialPhone -> "OPEN DIALER"
        is DeviceAction.OpenYouTube -> "OPEN YOUTUBE"
        is DeviceAction.SendWhatsApp -> "OPEN WHATSAPP"
        is DeviceAction.SetAlarm -> "SET ALARM"
        is DeviceAction.SetTimer -> "START TIMER"
        is DeviceAction.OpenApp -> "OPEN APP"
    }

    val needsPhoneNumber = when (action) {
        is DeviceAction.DialPhone -> action.phoneNumber.isBlank()
        is DeviceAction.SendWhatsApp -> action.phoneNumber.isBlank()
        else -> false
    }

    var inlinePhoneDraft by remember(action) { mutableStateOf("") }

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
                        if (needsPhoneNumber && inlinePhoneDraft.isNotBlank() && onUpdateAction != null) {
                            onUpdateAction(applyPhoneNumber(inlinePhoneDraft))
                        } else {
                            onExecute()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("execute_device_action")
            ) {
                Text(
                    text = if (needsPhoneNumber && inlinePhoneDraft.isNotBlank()) "APPLY NUMBER" else ctaLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = SwissWhite
                )
            }
        }

        // Inline Quick Contact / Number Picker when a contact name wasn't found in Quick Contacts
        if (needsPhoneNumber && onUpdateAction != null) {
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
                    else -> null
                }
                Text(
                    text = if (!targetName.isNullOrBlank()) {
                        "NUMBER NEEDED FOR \"$targetName\" // PICK OR ENTER BELOW"
                    } else {
                        "QUICK CONTACT // PICK SAVED OR ENTER PHONE NUMBER"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )

                if (quickContacts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickContacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .background(SwissPaper, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable {
                                        onUpdateAction(applyPhoneNumber(contact.phoneNumber, contact.name))
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
                                    text = contact.phoneNumber,
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
                        value = inlinePhoneDraft,
                        onValueChange = { inlinePhoneDraft = it },
                        placeholder = {
                            Text(
                                "+91 9876543210",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        shape = RectangleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("device_action_inline_phone")
                    )
                    if (!targetName.isNullOrBlank() && onSaveQuickContact != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (inlinePhoneDraft.isNotBlank()) SwissInk else SwissMuted.copy(alpha = 0.4f),
                                    RectangleShape
                                )
                                .clickable(enabled = inlinePhoneDraft.isNotBlank()) {
                                    val normalized = DeviceActionEngine.normalizePhoneNumber(inlinePhoneDraft)
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
                                    if (inlinePhoneDraft.isNotBlank()) SwissInk else SwissMuted.copy(alpha = 0.4f),
                                    RectangleShape
                                )
                                .clickable(enabled = inlinePhoneDraft.isNotBlank()) {
                                    onUpdateAction(applyPhoneNumber(inlinePhoneDraft))
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
