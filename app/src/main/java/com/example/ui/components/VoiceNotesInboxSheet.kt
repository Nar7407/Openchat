package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.domain.model.VoiceNoteInfo
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissGridLine
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import com.example.util.VoiceInputController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceNotesInboxSheet(
    notes: List<VoiceNoteInfo>,
    isSavingNote: Boolean,
    onSaveNewMemo: (String) -> Unit,
    onUpdateNote: (VoiceNoteInfo) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var draftMemo by remember { mutableStateOf("") }
    var isDictating by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    var editingNote by remember { mutableStateOf<VoiceNoteInfo?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editMarkdown by remember { mutableStateOf("") }

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onPartialText = { partial -> draftMemo = partial },
            onFinalText = { finalText ->
                draftMemo = if (draftMemo.isBlank()) finalText else "$draftMemo $finalText".trim()
            },
            onListeningChanged = { listening -> isDictating = listening },
            onFailure = { err -> feedbackBanner = err }
        )
    }
    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.start()
        } else {
            feedbackBanner = "Microphone permission denied."
        }
    }

    val filteredNotes = remember(notes, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) notes
        else notes.filter { n ->
            n.title.contains(q, ignoreCase = true) ||
                n.markdownContent.contains(q, ignoreCase = true) ||
                n.tags.any { it.contains(q, ignoreCase = true) }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d · HH:mm", Locale.US) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .border(2.dp, SwissInk, RectangleShape),
            shape = RectangleShape,
            color = SwissWhite
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "VOICE QUICK-NOTES // INBOX",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = SwissSignalRed
                        )
                        Text(
                            text = "Structured Markdown Notes (${notes.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SwissWhite
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_voice_notes_inbox")
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close Voice Notes Inbox", tint = SwissWhite)
                    }
                }

                // Quick Dictate & Save composer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draftMemo,
                        onValueChange = { draftMemo = it },
                        placeholder = { Text("Dictate or type a quick memo… AI cleans it into structured Markdown") },
                        maxLines = 3,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissPaper),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_note_draft_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .background(if (isDictating) SwissSignalRed else SwissWhite, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable {
                                    if (isDictating) {
                                        voiceController.stop()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) voiceController.start()
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("voice_note_mic_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isDictating) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                contentDescription = if (isDictating) "Stop dictation" else "Dictate voice note",
                                tint = if (isDictating) SwissWhite else SwissInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isDictating) "LISTENING…" else "DICTATE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isDictating) SwissWhite else SwissInk
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (draftMemo.isNotBlank() && !isSavingNote) SwissInk else SwissGridLine,
                                    RectangleShape
                                )
                                .clickable(enabled = draftMemo.isNotBlank() && !isSavingNote) {
                                    val payload = draftMemo.trim()
                                    draftMemo = ""
                                    onSaveNewMemo(payload)
                                    feedbackBanner = "Cleaned & saving note to inbox…"
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("voice_note_save_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSavingNote) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = SwissWhite
                                    )
                                    Text(
                                        text = "CLEANING NOTE…",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = SwissWhite
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.NoteAdd,
                                        contentDescription = null,
                                        tint = SwissWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "CLEAN & SAVE NOTE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SwissWhite
                                    )
                                }
                            }
                        }
                    }

                    if (feedbackBanner != null) {
                        Text(
                            text = feedbackBanner.orEmpty(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = SwissSignalRed
                        )
                    }
                }

                HorizontalDivider(color = SwissGridLine)

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notes by title, content, or #tag…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("voice_note_search_input")
                )

                // Notes List
                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (notes.isEmpty()) {
                                "No saved voice notes yet.\nDictate above, tap \"Save Note\" in Assistant, or type /note <memo>."
                            } else {
                                "No notes match \"$searchQuery\"."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwissMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            val isEditing = editingNote?.id == note.id
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissWhite, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = SwissInk
                                        )
                                        Text(
                                            text = dateFormatter.format(Date(note.updatedAt)),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = SwissMuted
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText(note.title, "# ${note.title}\n\n${note.markdownContent}"))
                                                feedbackBanner = "Copied \"${note.title}\" Markdown."
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy note Markdown", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, note.title)
                                                    putExtra(Intent.EXTRA_TEXT, "# ${note.title}\n\n${note.markdownContent}")
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Note"))
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Outlined.Share, contentDescription = "Share note", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (isEditing) {
                                                    editingNote = null
                                                } else {
                                                    editingNote = note
                                                    editTitle = note.title
                                                    editMarkdown = note.markdownContent
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit note", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { onDeleteNote(note.id) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("delete_voice_note_${note.id}")
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete note", tint = SwissSignalRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                if (note.tags.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        note.tags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .background(SwissSubtleFill, RectangleShape)
                                                    .border(1.dp, SwissGridLine, RectangleShape)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "#$tag",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                                    color = SwissInk
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isEditing) {
                                    OutlinedTextField(
                                        value = editTitle,
                                        onValueChange = { editTitle = it },
                                        label = { Text("Title") },
                                        singleLine = true,
                                        shape = RectangleShape,
                                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = editMarkdown,
                                        onValueChange = { editMarkdown = it },
                                        label = { Text("Markdown") },
                                        minLines = 4,
                                        shape = RectangleShape,
                                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SwissButton(
                                            text = "Cancel",
                                            variant = SwissButtonVariant.OUTLINE,
                                            minHeight = 40.dp,
                                            onClick = { editingNote = null },
                                            modifier = Modifier.weight(1f)
                                        )
                                        SwissButton(
                                            text = "Save Changes",
                                            variant = SwissButtonVariant.PRIMARY,
                                            minHeight = 40.dp,
                                            onClick = {
                                                onUpdateNote(
                                                    note.copy(
                                                        title = editTitle.trim().ifBlank { note.title },
                                                        markdownContent = editMarkdown.trim()
                                                    )
                                                )
                                                editingNote = null
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    MarkdownText(markdown = note.markdownContent)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}
