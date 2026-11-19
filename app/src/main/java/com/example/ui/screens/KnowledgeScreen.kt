package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.KnowledgeDocInfo
import com.example.ui.components.ErrorBanner
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissRuleDivider
import com.example.ui.components.SwissUtilityHeader
import com.example.ui.components.swissGraphPaper
import com.example.ui.components.swissRegistrationMarks
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.ui.viewmodel.KnowledgeViewModel
import java.util.Locale

@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showNewNoteDialog by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }

    var editingDoc by remember { mutableStateOf<KnowledgeDocInfo?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editSlug by remember { mutableStateOf("") }

    var previewDoc by remember { mutableStateOf<KnowledgeDocInfo?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) viewModel.importFile(uri)
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SwissCanvas)
    ) {
        SwissUtilityHeader(
            breadcrumb = "06 // KNOWLEDGE / LOCAL RAG",
            title = "knowledge base",
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
            leadingContentDescription = "Back",
            onLeadingClick = onNavigateBack,
            leadingTestTag = "knowledge_back_button"
        )

        if (state.isImporting) {
            LinearProgressIndicator(
                color = SwissSignalRed,
                trackColor = SwissConcrete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AnimatedVisibility(visible = state.statusNotice != null) {
                        state.statusNotice?.let { notice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissVerifiedTint, RectangleShape)
                                    .border(1.dp, SwissVerifiedInk, RectangleShape)
                                    .clickable { viewModel.clearNotices() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissVerifiedInk,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = SwissVerifiedInk,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(visible = state.errorMessage != null) {
                        state.errorMessage?.let { err ->
                            ErrorBanner(
                                message = err,
                                onDismiss = viewModel::clearNotices
                            )
                        }
                    }
                }

                // Upload & Quick Note Module
                item {
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "RAG // CLIENT-SIDE TF-IDF INDEX",
                                rightLabel = "${state.docs.size} DOCS",
                                rightAccent = true
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Upload PDFs, study notes, Markdown manuals, or datasheets strictly on this device. Reference any document in Chat using #doc-name (or #all) to automatically attach top TF-IDF cosine-matched excerpts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SwissButton(
                                        text = if (state.isImporting) "Indexing…" else "Upload PDF / TXT / MD",
                                        leadingIcon = Icons.Outlined.UploadFile,
                                        variant = SwissButtonVariant.PRIMARY,
                                        enabled = !state.isImporting,
                                        minHeight = 44.dp,
                                        onClick = {
                                            filePicker.launch(
                                                arrayOf(
                                                    "application/pdf",
                                                    "text/plain",
                                                    "text/markdown",
                                                    "text/*",
                                                    "*/*"
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("knowledge_upload_file_button")
                                    )
                                    SwissButton(
                                        text = "Add Note",
                                        leadingIcon = Icons.Filled.Add,
                                        variant = SwissButtonVariant.INK,
                                        minHeight = 44.dp,
                                        onClick = {
                                            noteTitle = ""
                                            noteContent = ""
                                            showNewNoteDialog = true
                                        },
                                        modifier = Modifier.testTag("knowledge_add_note_button")
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Retrieval Tester
                if (state.docs.isNotEmpty()) {
                    item {
                        SwissPanel(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                SwissPanelHeader(
                                    leftLabel = "TEST // COSINE SIMILARITY RETRIEVAL",
                                    rightLabel = "TOP 4 CHUNKS"
                                )
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = state.testQuery,
                                        onValueChange = viewModel::updateTestQuery,
                                        placeholder = { Text("Test a query against your indexed docs…") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Search, contentDescription = null, tint = SwissInk)
                                        },
                                        singleLine = true,
                                        shape = RectangleShape,
                                        colors = swissTextFieldColors(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("knowledge_test_query_input")
                                    )

                                    if (state.testQuery.isNotBlank()) {
                                        if (state.testResults.isEmpty()) {
                                            Text(
                                                text = "NO MATCHING EXCERPTS FOUND FOR QUERY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissMuted
                                            )
                                        } else {
                                            state.testResults.forEachIndexed { idx, ex ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(SwissSubtleFill)
                                                        .border(1.dp, SwissInk, RectangleShape)
                                                        .padding(10.dp)
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "0${idx + 1} // #${ex.docSlug} · CHUNK ${ex.chunkIndex + 1}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = SwissSignalRed
                                                            )
                                                            Text(
                                                                text = String.format(Locale.US, "SCORE %.2f", ex.score),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = SwissInk
                                                            )
                                                        }
                                                        Text(
                                                            text = ex.text,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = SwissInk,
                                                            maxLines = 4,
                                                            overflow = TextOverflow.Ellipsis
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
                }

                // Indexed Documents List
                if (state.docs.isEmpty()) {
                    item {
                        Surface(
                            color = SwissPaper,
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, SwissInk),
                            modifier = Modifier
                                .fillMaxWidth()
                                .swissGraphPaper()
                                .swissRegistrationMarks()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "INDEX // 00 DOCUMENTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = "No local knowledge documents yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SwissInk
                                )
                                Text(
                                    text = "Upload .pdf, .md, or .txt files above. Then type #doc-name in Chat to ground any model with your local files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                            }
                        }
                    }
                } else {
                    items(state.docs, key = { it.id }) { doc ->
                        SwissPanel(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissSubtleFill)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SwissBadge(
                                        text = "#${doc.slug}",
                                        containerColor = SwissInk,
                                        contentColor = SwissWhite,
                                        borderColor = SwissInk
                                    )
                                    Text(
                                        text = "${doc.chunkCount} CHUNKS · ${doc.charCount} CHARS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissMuted
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(SwissInk)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { previewDoc = doc }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(SwissConcrete)
                                            .border(1.dp, SwissInk, RectangleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = SwissInk,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = doc.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = SwissInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = doc.content.take(120).replace("\n", " "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        SwissIconCell(
                                            icon = Icons.Outlined.Edit,
                                            contentDescription = "Rename document",
                                            onClick = {
                                                editingDoc = doc
                                                editTitle = doc.title
                                                editSlug = doc.slug
                                            },
                                            cellSize = 36.dp
                                        )
                                        SwissIconCell(
                                            icon = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete document",
                                            onClick = { viewModel.deleteDoc(doc.id) },
                                            cellSize = 36.dp
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

    if (showNewNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Column {
                    Text("RAG // MANUAL ENTRY", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text("Add Knowledge Note", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title (becomes #slug)") },
                        placeholder = { Text("e.g. API Spec or Biology Notes") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Document Content / Markdown") },
                        minLines = 6,
                        maxLines = 10,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                SwissButton(
                    text = "Index Note",
                    variant = SwissButtonVariant.PRIMARY,
                    enabled = noteContent.isNotBlank(),
                    minHeight = 42.dp,
                    onClick = {
                        viewModel.addManualNote(noteTitle, noteContent)
                        showNewNoteDialog = false
                    }
                )
            },
            dismissButton = {
                SwissButton(
                    text = "Cancel",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 42.dp,
                    onClick = { showNewNoteDialog = false }
                )
            }
        )
    }

    editingDoc?.let { target ->
        AlertDialog(
            onDismissRequest = { editingDoc = null },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Column {
                    Text("RAG // RENAME HANDLE", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text("Edit Document Tag", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Display Title") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSlug,
                        onValueChange = { editSlug = it },
                        label = { Text("Chat Tag (without #)") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                SwissButton(
                    text = "Save",
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 42.dp,
                    onClick = {
                        viewModel.renameDoc(target.id, editTitle, editSlug)
                        editingDoc = null
                    }
                )
            },
            dismissButton = {
                SwissButton(
                    text = "Cancel",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 42.dp,
                    onClick = { editingDoc = null }
                )
            }
        )
    }

    previewDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { previewDoc = null },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Column {
                    Text("#${doc.slug} // ${doc.chunkCount} CHUNKS", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text(doc.title, style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(SwissPaper)
                        .border(1.dp, SwissInk, RectangleShape)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = doc.content.take(12_000),
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissInk
                    )
                }
            },
            confirmButton = {
                SwissButton(
                    text = "Close",
                    variant = SwissButtonVariant.INK,
                    minHeight = 40.dp,
                    onClick = { previewDoc = null }
                )
            }
        )
    }
}
