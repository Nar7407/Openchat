package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headphones
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.QuickContact
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.DeviceActionEngine

@Composable
fun QuickContactsSettingsPanel(
    contacts: List<QuickContact>,
    onSaveContact: (name: String, phone: String, email: String, upiId: String, nicknames: String) -> Unit,
    onImportContactsBulk: (List<QuickContact>) -> Unit,
    onDeleteContact: (id: String) -> Unit,
    onReportError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nameDraft by remember { mutableStateOf("") }
    var phoneDraft by remember { mutableStateOf("") }
    var nicknamesDraft by remember { mutableStateOf("") }
    var emailDraft by remember { mutableStateOf("") }
    var upiDraft by remember { mutableStateOf("") }

    // Zero-permission Android Phone Contact Picker (Google Play Policy compliant — NO READ_CONTACTS required)
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
            runCatching {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val pickedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val pickedNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                        if (pickedName.isNotBlank() && pickedNum.isNotBlank()) {
                            onSaveContact(pickedName, DeviceActionEngine.normalizePhoneNumber(pickedNum), "", "", "")
                        } else {
                            onReportError("Selected contact did not contain a valid phone number.")
                        }
                    }
                }
            }.onFailure { err ->
                onReportError(err.message ?: "Could not import contact from phone picker.")
            }
        }
    }

    // Zero-permission Bulk vCard (.vcf) File Importer via Android Storage Access Framework
    val vcfImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val rawVcf = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = DeviceActionEngine.parseVcfContacts(rawVcf)
            if (parsed.isEmpty()) {
                onReportError("No valid contacts found inside the selected .vcf file.")
            } else {
                onImportContactsBulk(parsed)
            }
        }.onFailure { err ->
            onReportError(err.message ?: "Could not read the selected .vcf file.")
        }
    }

    SwissPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            SwissPanelHeader(
                leftLabel = "ACTIONS // QUICK CONTACTS (CALL, WHATSAPP, SMS, EMAIL & UPI)",
                rightLabel = "${contacts.size} SAVED"
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Import contacts from your phone or bulk .vcf export, or add manually with nicknames (e.g. \"Mom, Mummy\", \"Bhai\"). Saved contacts are resolved automatically across Chat, Live Assistant & Widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SwissButton(
                        text = "Pick Phone Contact",
                        leadingIcon = Icons.Outlined.Contacts,
                        variant = SwissButtonVariant.PRIMARY,
                        minHeight = 40.dp,
                        onClick = {
                            runCatching {
                                val pickIntent = Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                                contactPickerLauncher.launch(pickIntent)
                            }.onFailure {
                                onReportError("No Contacts app available on this device. Add manually or import .vcf below.")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_import_phone_contact")
                    )

                    SwissButton(
                        text = "Import .VCF (Bulk)",
                        variant = SwissButtonVariant.OUTLINE,
                        minHeight = 40.dp,
                        onClick = {
                            runCatching {
                                vcfImportLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*"))
                            }.onFailure {
                                onReportError("Unable to open file picker for .vcf import.")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_import_vcf_contacts")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        placeholder = { Text("Name (e.g. Sunita)", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_contact_name_input")
                    )
                    OutlinedTextField(
                        value = phoneDraft,
                        onValueChange = { phoneDraft = it },
                        placeholder = { Text("+91 9876543210", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RectangleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_contact_phone_input")
                    )
                }

                OutlinedTextField(
                    value = nicknamesDraft,
                    onValueChange = { nicknamesDraft = it },
                    placeholder = { Text("Voice Aliases / Nicknames (e.g. Mom, Mummy, Bhai, Vimla)", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_contact_nicknames_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = emailDraft,
                        onValueChange = { emailDraft = it },
                        placeholder = { Text("Email (optional)", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RectangleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_contact_email_input")
                    )
                    OutlinedTextField(
                        value = upiDraft,
                        onValueChange = { upiDraft = it },
                        placeholder = { Text("UPI ID (name@bank)", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_contact_upi_input")
                    )
                }

                SwissButton(
                    text = "Add Quick Contact",
                    leadingIcon = Icons.Filled.Add,
                    variant = SwissButtonVariant.INK,
                    enabled = nameDraft.isNotBlank() && (phoneDraft.isNotBlank() || emailDraft.isNotBlank() || upiDraft.isNotBlank()),
                    minHeight = 40.dp,
                    onClick = {
                        onSaveContact(
                            nameDraft.trim(),
                            if (phoneDraft.isNotBlank()) DeviceActionEngine.normalizePhoneNumber(phoneDraft) else "",
                            emailDraft.trim(),
                            upiDraft.trim(),
                            nicknamesDraft.trim()
                        )
                        nameDraft = ""
                        phoneDraft = ""
                        nicknamesDraft = ""
                        emailDraft = ""
                        upiDraft = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_add_contact_button")
                )

                if (contacts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        contacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissCanvas, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = contact.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = SwissInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (contact.nicknames.isNotBlank()) {
                                            Text(
                                                text = "AKA ${contact.nicknames.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissSignalRed,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    val detailLine = listOfNotNull(
                                        contact.phoneNumber.takeIf { it.isNotBlank() },
                                        contact.email.takeIf { it.isNotBlank() },
                                        contact.upiId.takeIf { it.isNotBlank() }?.let { "UPI: $it" }
                                    ).joinToString(" · ")
                                    Text(
                                        text = detailLine,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit contact & voice aliases",
                                        tint = SwissInk,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                nameDraft = contact.name
                                                phoneDraft = contact.phoneNumber
                                                nicknamesDraft = contact.nicknames
                                                emailDraft = contact.email
                                                upiDraft = contact.upiId
                                            }
                                            .testTag("settings_edit_contact_${contact.id}")
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete contact",
                                    tint = SwissSignalRed,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onDeleteContact(contact.id) }
                                )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomAppsSettingsPanel(
    customApps: List<CustomAppEntry>,
    onSaveCustomApp: (name: String, packageName: String) -> Unit,
    onDeleteCustomApp: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var appNameDraft by remember { mutableStateOf("") }
    var packageDraft by remember { mutableStateOf("") }

    SwissPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            SwissPanelHeader(
                leftLabel = "ACTIONS // CUSTOM APP PACKAGES",
                rightLabel = "${DeviceActionEngine.curatedApps.size} BUILT-IN + ${customApps.size}"
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Built-in support includes WhatsApp, YouTube, PhonePe, GPay, Paytm, Swiggy, Zomato, Spotify, Maps, Instagram, and Camera. Add any custom Android package ID below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = appNameDraft,
                        onValueChange = { appNameDraft = it },
                        placeholder = { Text("App Name (e.g. Notion)", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_custom_app_name")
                    )
                    OutlinedTextField(
                        value = packageDraft,
                        onValueChange = { packageDraft = it },
                        placeholder = { Text("notion.id", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_custom_app_pkg")
                    )
                }

                SwissButton(
                    text = "Add Custom App Package",
                    leadingIcon = Icons.Filled.Add,
                    variant = SwissButtonVariant.OUTLINE,
                    enabled = appNameDraft.isNotBlank() && packageDraft.contains("."),
                    minHeight = 40.dp,
                    onClick = {
                        onSaveCustomApp(appNameDraft.trim(), packageDraft.trim())
                        appNameDraft = ""
                        packageDraft = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_add_custom_app_button")
                )

                if (customApps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        customApps.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissCanvas, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SwissInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete custom app",
                                    tint = SwissSignalRed,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onDeleteCustomApp(app.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisionFallbackSettingsPanel(
    visionModels: List<SelectableModel>,
    fallbackQueueIds: List<Long>,
    onUpdateQueue: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val queuedModels = remember(visionModels, fallbackQueueIds) {
        fallbackQueueIds.mapNotNull { id -> visionModels.firstOrNull { it.model.id == id } }
    }
    val unqueuedModels = remember(visionModels, fallbackQueueIds) {
        visionModels.filter { m -> m.model.id !in fallbackQueueIds }
    }

    SwissPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            SwissPanelHeader(
                leftLabel = "LIVE LENS // 429 VISION FALLBACK QUEUE",
                rightLabel = if (queuedModels.isEmpty()) "AUTO ORDER" else "${queuedModels.size} QUEUED",
                rightAccent = queuedModels.isNotEmpty()
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "If Live Lens / Auto-Explainer hits HTTP 429 or quota exhaustion, it cools down that model for 60s and automatically switches to the next vision model in this priority order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                if (queuedModels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        queuedModels.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissInk, RectangleShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SwissSignalRed
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.model.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.provider.name} · ${item.model.modelId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissConcrete,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (idx > 0) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowUpward,
                                        contentDescription = "Move up",
                                        tint = SwissWhite,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                val next = fallbackQueueIds.toMutableList()
                                                val moved = next.removeAt(idx)
                                                next.add(idx - 1, moved)
                                                onUpdateQueue(next)
                                            }
                                    )
                                }
                                if (idx < queuedModels.lastIndex) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDownward,
                                        contentDescription = "Move down",
                                        tint = SwissWhite,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                val next = fallbackQueueIds.toMutableList()
                                                val moved = next.removeAt(idx)
                                                next.add(idx + 1, moved)
                                                onUpdateQueue(next)
                                            }
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove from queue",
                                    tint = SwissSignalRed,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            onUpdateQueue(fallbackQueueIds.filterNot { it == item.model.id })
                                        }
                                )
                            }
                        }
                    }
                }

                if (unqueuedModels.isNotEmpty()) {
                    Text(
                        text = "AVAILABLE VISION MODELS (TAP TO APPEND)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        unqueuedModels.take(8).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable {
                                        onUpdateQueue(fallbackQueueIds + item.model.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.model.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.provider.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissMuted
                                    )
                                }
                                Text(
                                    text = "+ QUEUE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EarbudLaunchSetupPanel(
    onTestLiveMic: () -> Unit,
    onCopiedNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val deepLinks = remember {
        listOf(
            "ANDROID DEEP LINK" to "allchat://live",
            "PROTOCOL LINK" to "web+allchat://live",
            "WEB / PWA ROUTE" to "https://allchat.app/?mode=live"
        )
    }

    SwissPanel(modifier = modifier.fillMaxWidth().testTag("earbud_launch_setup_panel")) {
        Column {
            SwissPanelHeader(
                leftLabel = "HANDS-FREE // EARBUD LAUNCH SETUP",
                rightLabel = "BLUETOOTH"
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Control Live Assistant & Call/WhatsApp confirmations directly from your Bluetooth earbuds: 1x Tap = Confirm / Interrupt / Talk, 2x Tap or Next Track = Cancel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                deepLinks.forEachIndexed { idx, (label, uri) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissCanvas, RectangleShape)
                            .border(1.dp, SwissInk, RectangleShape)
                            .clickable {
                                clipboard.setText(AnnotatedString(uri))
                                onCopiedNotice("Copied $uri")
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("earbud_deeplink_copy_$idx"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                            Text(
                                text = uri,
                                style = MaterialTheme.typography.titleSmall,
                                color = SwissInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(SwissInk, RectangleShape)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy $uri",
                                tint = SwissWhite,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "COPY",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper, RectangleShape)
                        .border(1.dp, SwissConcrete, RectangleShape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "BACKGROUND EARBUD LAUNCH (KEY MAPPER / MACRODROID)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed
                    )
                    Text(
                        text = "1. Install Key Mapper or MacroDroid from Google Play.\n" +
                            "2. Set Trigger → Bluetooth Headset Button (Long Press / Double Press).\n" +
                            "3. Set Action → Open URL / Deep Link → paste allchat://live (or web+allchat://live).\n" +
                            "4. Pressing your earbud even when AllChat is closed will jump straight into Live Mic listening with an audio chime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissInk
                    )
                }

                SwissButton(
                    text = "Test Live Mic & Earbud Mode",
                    leadingIcon = Icons.Outlined.Headphones,
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 42.dp,
                    onClick = onTestLiveMic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_test_earbud_live_mic")
                )
            }
        }
    }
}

