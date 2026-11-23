package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.ClipboardSnippetInfo
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.SpotlightResult
import com.example.domain.model.TopicCluster
import com.example.domain.model.WatchlistItemInfo
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.util.XlsxExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. Private Receipt / Bill / Monthly Expense Tracker + Native .xlsx Export
@Composable
fun ExpenseTrackerDialog(
    expenses: List<ExpenseRecord>,
    onSaveExpense: (ExpenseRecord) -> Unit,
    onDeleteExpense: (Long) -> Unit,
    onScanReceiptBitmap: suspend (Bitmap) -> Result<ExpenseRecord>,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var isScanning by remember { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isScanning = true
                val bmp = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bmp != null) {
                    onScanReceiptBitmap(bmp)
                        .onSuccess { rec -> onNotify("RECEIPT // Logged ${rec.merchant} (${rec.currency} ${rec.amount}).") }
                        .onFailure { err -> onNotify("RECEIPT // ${err.message ?: "Scan failed"}") }
                } else {
                    onNotify("Could not decode receipt image.")
                }
                isScanning = false
            }
        }
    }

    val xlsxSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = XlsxExporter.buildExpensesXlsx(expenses)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.onSuccess {
                onNotify("EXPENSES // Exported ${expenses.size} entries to .xlsx spreadsheet.")
            }.onFailure {
                onNotify("Export failed: ${it.message}")
            }
        }
    }

    val totalAmount = expenses.sumOf { it.amount }
    val byCategory = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("expense_tracker_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "EXPENSE TRACKER // RECEIPT OCR + .XLSX",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close expense tracker", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SwissInk).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL LOGGED (${expenses.size})", style = MaterialTheme.typography.labelSmall, color = SwissCanvas)
                            Text(
                                text = String.format(Locale.US, "₹ %.2f", totalAmount),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = SwissWhite
                            )
                        }
                        SwissButton(
                            text = "EXPORT .XLSX",
                            onClick = { xlsxSaver.launch("AllChat_Expenses_$today.xlsx") },
                            enabled = expenses.isNotEmpty(),
                            leadingIcon = Icons.Default.Download,
                            variant = SwissButtonVariant.PRIMARY
                        )
                    }

                    if (byCategory.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            byCategory.entries.take(4).forEach { (cat, sum) ->
                                Column(modifier = Modifier.weight(1f).background(SwissPaper).border(1.dp, SwissInk).padding(6.dp)) {
                                    Text(cat.uppercase(), style = MaterialTheme.typography.labelSmall, color = SwissMuted, maxLines = 1)
                                    Text(String.format(Locale.US, "%.0f", sum), style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissInk)
                                }
                            }
                        }
                    }

                    SwissButton(
                        text = if (isScanning) "EXTRACTING RECEIPT WITH VISION AI…" else "SCAN RECEIPT / UPI SCREENSHOT",
                        onClick = {
                            receiptPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !isScanning,
                        leadingIcon = Icons.Default.CameraAlt,
                        variant = SwissButtonVariant.INK,
                        modifier = Modifier.fillMaxWidth().testTag("expense_scan_receipt_btn")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = merchant,
                            onValueChange = { merchant = it },
                            label = { Text("Merchant") },
                            singleLine = true,
                            colors = swissTextFieldColors(),
                            shape = RectangleShape,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount") },
                            singleLine = true,
                            colors = swissTextFieldColors(),
                            shape = RectangleShape,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Food", "Travel", "Bills", "Shopping", "Study").forEach { cat ->
                            SwissToggleCell(label = cat, selected = category == cat, onClick = { category = cat }, modifier = Modifier.weight(1f))
                        }
                    }

                    SwissButton(
                        text = "ADD MANUAL ENTRY",
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: return@SwissButton
                            onSaveExpense(ExpenseRecord(merchant = merchant.ifBlank { "Cash Expense" }, dateIso = today, category = category, amount = amt))
                            merchant = ""
                            amountText = ""
                        },
                        enabled = amountText.toDoubleOrNull() != null,
                        leadingIcon = Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth()
                    )

                    expenses.forEach { rec ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${rec.merchant} · ${rec.currency} ${String.format(Locale.US, "%.2f", rec.amount)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                                Text("${rec.dateIso} · ${rec.category}${if (rec.notes.isNotBlank()) " · ${rec.notes}" else ""}", style = MaterialTheme.typography.bodySmall, color = SwissMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SwissIconCell(icon = Icons.Default.Delete, contentDescription = "Delete expense", onClick = { onDeleteExpense(rec.id) }, bordered = false, cellSize = 48.dp)
                        }
                    }
                }
            }
        }
    }
}

// 2. Web Page / Topic Change Watcher ("Watchlist")
@Composable
fun WatchlistDialog(
    items: List<WatchlistItemInfo>,
    onAddItem: (title: String, queryOrUrl: String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onCheckItem: suspend (WatchlistItemInfo) -> Result<WatchlistItemInfo>,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var queryOrUrl by remember { mutableStateOf("") }
    var checkingId by remember { mutableStateOf<Long?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("watchlist_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WATCHLIST // WEB & TOPIC CHANGE DIFF",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close watchlist", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Watch label (e.g. Exam Results / GPU Price)") },
                        singleLine = true,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = queryOrUrl,
                        onValueChange = { queryOrUrl = it },
                        label = { Text("URL (https://...) or Live Search Topic") },
                        singleLine = true,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth().testTag("watchlist_url_input")
                    )
                    SwissButton(
                        text = "ADD TO WATCHLIST",
                        onClick = {
                            onAddItem(title.ifBlank { queryOrUrl.take(32) }, queryOrUrl)
                            title = ""
                            queryOrUrl = ""
                        },
                        enabled = queryOrUrl.isNotBlank(),
                        leadingIcon = Icons.Default.Add,
                        variant = SwissButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )

                    items.forEach { item ->
                        Column(
                            modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                                    Text(item.queryOrUrl, style = MaterialTheme.typography.bodySmall, color = SwissMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Row {
                                    SwissIconCell(
                                        icon = Icons.Default.Refresh,
                                        contentDescription = "Check updates",
                                        onClick = {
                                            if (checkingId != null) return@SwissIconCell
                                            scope.launch {
                                                checkingId = item.id
                                                onCheckItem(item)
                                                    .onSuccess { onNotify("WATCHLIST // Checked \"${item.title}\".") }
                                                    .onFailure { onNotify("WATCHLIST // ${it.message ?: "Check failed"}") }
                                                checkingId = null
                                            }
                                        },
                                        accent = true,
                                        cellSize = 48.dp
                                    )
                                    SwissIconCell(icon = Icons.Default.Delete, contentDescription = "Delete watch item", onClick = { onDeleteItem(item.id) }, bordered = false, cellSize = 48.dp)
                                }
                            }
                            if (checkingId == item.id) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = SwissSignalRed, modifier = Modifier.padding(end = 8.dp))
                                    Text("Fetching & diffing changes…", style = MaterialTheme.typography.bodySmall, color = SwissMuted)
                                }
                            } else if (item.lastDiffMarkdown.isNotBlank()) {
                                MarkdownText(markdown = item.lastDiffMarkdown.take(900))
                            }
                        }
                    }
                }
            }
        }
    }
}

// 3. Global Spotlight Search & Visual Topic Map
@Composable
fun SpotlightSearchDialog(
    onSearch: suspend (query: String, filter: String) -> List<SpotlightResult>,
    onLoadTopicClusters: suspend () -> List<TopicCluster>,
    onOpenConversation: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var showTopicMap by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SpotlightResult>>(emptyList()) }
    var clusters by remember { mutableStateOf<List<TopicCluster>>(emptyList()) }

    LaunchedEffect(query, filter, showTopicMap) {
        if (showTopicMap) {
            clusters = onLoadTopicClusters()
        } else {
            results = onSearch(query, filter)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("spotlight_search_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SPOTLIGHT // SEARCH & TOPIC MAP",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close spotlight", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissToggleCell(label = "SPOTLIGHT SEARCH", selected = !showTopicMap, onClick = { showTopicMap = false }, useSignalRedWhenSelected = true, modifier = Modifier.weight(1f))
                        SwissToggleCell(label = "VISUAL TOPIC MAP", selected = showTopicMap, onClick = { showTopicMap = true }, useSignalRedWhenSelected = true, modifier = Modifier.weight(1f))
                    }

                    if (!showTopicMap) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Search messages, notes, docs, expenses…") },
                            singleLine = true,
                            colors = swissTextFieldColors(),
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth().testTag("spotlight_query_input")
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("ALL", "CODE", "IMAGES", "NOTES", "EXPENSES", "WEEK").forEach { f ->
                                SwissToggleCell(label = f, selected = filter == f, onClick = { filter = f }, modifier = Modifier.weight(1f))
                            }
                        }

                        if (results.isEmpty()) {
                            Text("No matching entries found.", style = MaterialTheme.typography.bodySmall, color = SwissMuted)
                        } else {
                            results.take(30).forEach { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissPaper)
                                        .border(1.dp, SwissInk)
                                        .clickable(enabled = r.conversationId != null) {
                                            r.conversationId?.let {
                                                onOpenConversation(it)
                                                onDismiss()
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(r.kind, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissSignalRed)
                                        if (r.hasCode) Text("CODE", style = MaterialTheme.typography.labelSmall, color = SwissVerifiedInk)
                                    }
                                    Text(r.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.snippet, style = MaterialTheme.typography.bodySmall, color = SwissMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        if (clusters.isEmpty()) {
                            Text("Start a few conversations to see your automatic topic map.", style = MaterialTheme.typography.bodySmall, color = SwissMuted)
                        } else {
                            clusters.forEach { cluster ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.5.dp, SwissInk).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cluster.topic.uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissSignalRed)
                                        Text("${cluster.conversationIds.size} CHATS", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissInk)
                                    }
                                    cluster.sampleTitles.forEachIndexed { idx, sample ->
                                        val cid = cluster.conversationIds.getOrNull(idx)
                                        Text(
                                            text = "→ $sample",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissInk,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(SwissCanvas)
                                                .clickable(enabled = cid != null) {
                                                    cid?.let {
                                                        onOpenConversation(it)
                                                        onDismiss()
                                                    }
                                                }
                                                .padding(8.dp)
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

// 4. Smart Clipboard History Vault & Multi-Snippet Merger
@Composable
fun ClipboardVaultDialog(
    snippets: List<ClipboardSnippetInfo>,
    onMergeAndSend: (String) -> Unit,
    onDeleteSnippet: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = remember { mutableStateListOf<Long>() }
    var mergeInstruction by remember { mutableStateOf("Combine, organize, and analyze these snippets cleanly:") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("clipboard_vault_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CLIPBOARD VAULT // MULTI-SNIPPET MERGER",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close clipboard vault", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mergeInstruction,
                        onValueChange = { mergeInstruction = it },
                        label = { Text("Instruction for merged snippets") },
                        singleLine = true,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissButton(
                            text = "MERGE (${selectedIds.size}) → CHAT",
                            onClick = {
                                val chosen = snippets.filter { it.id in selectedIds }
                                if (chosen.isNotEmpty()) {
                                    val combined = buildString {
                                        appendLine(mergeInstruction)
                                        chosen.forEachIndexed { i, s ->
                                            appendLine("\n--- Snippet ${i + 1} ---")
                                            appendLine(s.content)
                                        }
                                    }
                                    onMergeAndSend(combined)
                                    onDismiss()
                                }
                            },
                            enabled = selectedIds.isNotEmpty(),
                            leadingIcon = Icons.Default.MergeType,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1.4f)
                        )
                        SwissButton(
                            text = "CLEAR VAULT",
                            onClick = onClearAll,
                            enabled = snippets.isNotEmpty(),
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    if (snippets.isEmpty()) {
                        Text(
                            text = "No snippets saved yet. Copy text in any app and return to AllChat — it automatically logs into your local vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissMuted
                        )
                    } else {
                        snippets.forEach { sn ->
                            val isSelected = sn.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) SwissSignalRedTint else SwissPaper)
                                    .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) SwissSignalRed else SwissInk)
                                    .clickable {
                                        if (isSelected) selectedIds.remove(sn.id) else selectedIds.add(sn.id)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isSelected) "[SELECTED] ${if (sn.isUrl) "URL" else "SNIPPET"}" else if (sn.isUrl) "URL" else "SNIPPET",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                        color = if (isSelected) SwissSignalRed else SwissMuted
                                    )
                                    Text(sn.preview, style = MaterialTheme.typography.bodySmall, color = SwissInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                SwissIconCell(icon = Icons.Default.Delete, contentDescription = "Delete snippet", onClick = { onDeleteSnippet(sn.id) }, bordered = false, cellSize = 48.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
