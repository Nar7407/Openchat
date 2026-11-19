package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.remote.ConnectionTestResult
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderCatalog
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import com.example.ui.components.ProviderCatalogSheet
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissRuleDivider
import com.example.ui.components.SwissToggleCell
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
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWarnInk
import com.example.ui.theme.SwissWarnTint
import com.example.ui.theme.SwissWhite
import com.example.ui.viewmodel.ProviderEditorState
import com.example.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SwissCanvas)
    ) {
        SwissUtilityHeader(
            breadcrumb = "05 // SYSTEM / CONFIGURATION",
            title = "providers & settings",
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
            leadingContentDescription = "Back",
            onLeadingClick = onNavigateBack,
            leadingTestTag = "settings_back_button",
            trailingContent = {
                SwissIconCell(
                    icon = Icons.Outlined.Info,
                    contentDescription = "Setup & Limitations Guide",
                    onClick = { viewModel.setShowLimitationsSheet(true) },
                    bordered = false,
                    cellSize = 52.dp,
                    modifier = Modifier.testTag("open_limitations_guide")
                )
            }
        )

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
                // Device-Only Security Notice Panel
                item {
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "SEC // LOCAL KEYSTORE VAULT",
                                rightLabel = "DEVICE-ONLY",
                                rightAccent = true,
                                backgroundColor = SwissSignalRedTint
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.security_banner_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SwissInk
                                )
                                Text(
                                    text = stringResource(R.string.security_banner_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                            }
                        }
                    }
                }

                // Quick Add Presets + Full 116 A-Z Catalog Bento Module
                item {
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "01 // PROVIDER DIRECTORY",
                                rightLabel = "${ProviderCatalog.all.size} PRESETS A–Z"
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Popular Providers",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SwissInk
                                        )
                                        Text(
                                            text = "Quick-add or search the full A–Z OpenCode index.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissMuted
                                        )
                                    }
                                    SwissButton(
                                        text = "Browse A–Z",
                                        leadingIcon = Icons.Filled.Add,
                                        variant = SwissButtonVariant.PRIMARY,
                                        minHeight = 42.dp,
                                        onClick = viewModel::openCatalog,
                                        modifier = Modifier.testTag("add_provider_button")
                                    )
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ProviderCatalog.popular.forEach { preset ->
                                        SwissToggleCell(
                                            label = preset.title,
                                            selected = false,
                                            onClick = { viewModel.openAddProvider(preset) },
                                            modifier = Modifier.testTag("preset_${preset.id}")
                                        )
                                    }
                                    SwissToggleCell(
                                        label = "ALL ${ProviderCatalog.all.size} A–Z →",
                                        selected = true,
                                        useSignalRedWhenSelected = false,
                                        onClick = viewModel::openCatalog,
                                        modifier = Modifier.testTag("browse_all_providers_chip")
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.providers.isEmpty()) {
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "PROVIDERS // 00 ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = "No AI providers added yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SwissInk
                                )
                                Text(
                                    text = "Tap a popular preset above or Browse A–Z to connect any of ${ProviderCatalog.all.size} AI providers, gateways, coding plans, or local servers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                            }
                        }
                    }
                } else {
                    items(state.providers, key = { it.id }) { provider ->
                        ProviderPanel(
                            provider = provider,
                            isTesting = provider.id in state.testingProviderIds,
                            isFetching = provider.id in state.fetchingProviderIds,
                            testResult = state.connectionResults[provider.id],
                            fetchMessage = state.fetchMessages[provider.id],
                            onEdit = { viewModel.openEditProvider(provider) },
                            onDelete = { viewModel.deleteProvider(provider.id) },
                            onTest = { viewModel.testConnection(provider) },
                            onFetchModels = { viewModel.fetchModels(provider) },
                            onAddManualModel = { modelId -> viewModel.addManualModel(provider.id, modelId) },
                            onToggleCapability = viewModel::toggleCapability,
                            onDeleteModel = viewModel::deleteModel
                        )
                    }
                }
            }
        }
    }

    if (state.showCatalogSheet) {
        ProviderCatalogSheet(
            searchQuery = state.catalogSearchQuery,
            selectedCategory = state.catalogCategory,
            onSearchChange = viewModel::updateCatalogQuery,
            onCategoryChange = viewModel::selectCatalogCategory,
            onSelectPreset = viewModel::selectPresetFromCatalog,
            onCreateCustomFromSearch = viewModel::openCustomFromCatalogSearch,
            onDismiss = viewModel::dismissCatalog
        )
    }

    if (state.editor.isOpen && !state.showCatalogSheet) {
        ProviderEditorDialog(
            editor = state.editor,
            onBrowseCatalog = viewModel::openCatalog,
            onUpdateFields = viewModel::updateEditorFields,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditorProvider
        )
    }

    if (state.showLimitationsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowLimitationsSheet(false) },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SwissInk, RectangleShape)
            ) {
                SwissPanelHeader(
                    leftLabel = "DOC // SETUP & KNOWN LIMITATIONS",
                    rightLabel = "REV 1.0"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GuideSection(
                        index = "01",
                        title = "Local Encrypted Key Storage",
                        body = "API keys are encrypted using Android Keystore AES-GCM and stored strictly on this device. No developer server ever receives your keys or chat history."
                    )
                    GuideSection(
                        index = "02",
                        title = "Proxy / Custom Gateway URL",
                        body = "If an endpoint is blocked by a firewall, regional restriction, or web gateway, fill in the optional Proxy / Gateway URL field inside a provider's editor."
                    )
                    GuideSection(
                        index = "03",
                        title = "Local Models (Ollama & LMStudio)",
                        body = "On the Android Emulator, use http://10.0.2.2:11434/v1 for Ollama or http://10.0.2.2:1234/v1 for LMStudio. On a physical phone, enter your computer's LAN IP."
                    )
                    GuideSection(
                        index = "04",
                        title = "Capability Filtering",
                        body = "Manually added models default to Text Chat only. Toggle Image Input, Image Gen, Video Gen, or Summarize on each model so it appears in that studio's picker."
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideSection(index: String, title: String, body: String) {
    SwissPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("$index // ${title.uppercase()}", style = MaterialTheme.typography.labelLarge, color = SwissSignalRed)
            Text(body, style = MaterialTheme.typography.bodySmall, color = SwissInk)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderPanel(
    provider: ProviderConfig,
    isTesting: Boolean,
    isFetching: Boolean,
    testResult: ConnectionTestResult?,
    fetchMessage: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onFetchModels: () -> Unit,
    onAddManualModel: (String) -> Unit,
    onToggleCapability: (ModelInfo, ModelCapability) -> Unit,
    onDeleteModel: (Long) -> Unit
) {
    var manualModelInput by remember { mutableStateOf("") }
    var modelsExpanded by remember(provider.id) { mutableStateOf(true) }

    SwissPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Provider HUD Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSubtleFill)
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissBadge(
                        text = provider.type.label,
                        containerColor = if (provider.type == ProviderType.GEMINI) SwissSignalRed else SwissInk,
                        contentColor = SwissWhite,
                        borderColor = if (provider.type == ProviderType.GEMINI) SwissSignalRed else SwissInk
                    )
                    SwissBadge(
                        text = if (provider.hasApiKey) "KEY ENCRYPTED" else "NO KEY",
                        containerColor = if (provider.hasApiKey) SwissVerifiedTint else SwissConcrete,
                        contentColor = if (provider.hasApiKey) SwissVerifiedInk else SwissInk
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwissIconCell(
                        icon = Icons.Outlined.Edit,
                        contentDescription = "Edit provider",
                        onClick = onEdit,
                        cellSize = 36.dp,
                        modifier = Modifier.testTag("edit_provider_${provider.id}")
                    )
                    SwissIconCell(
                        icon = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete provider",
                        onClick = onDelete,
                        cellSize = 36.dp,
                        modifier = Modifier.testTag("delete_provider_${provider.id}")
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SwissInk)
            )

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = SwissInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.effectiveBaseUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!provider.proxyUrl.isNullOrBlank()) {
                        Text(
                            text = "PROXY OVERRIDE // ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                    }
                }

                // Action Row: Test & Fetch Models
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissButton(
                        text = if (isTesting) "Testing…" else "Test",
                        leadingIcon = if (isTesting) null else Icons.Outlined.NetworkCheck,
                        variant = SwissButtonVariant.OUTLINE,
                        enabled = !isTesting,
                        minHeight = 42.dp,
                        onClick = onTest,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_connection_${provider.id}")
                    )
                    SwissButton(
                        text = if (isFetching) "Fetching…" else "Fetch Models",
                        leadingIcon = if (isFetching) null else Icons.Outlined.CloudDownload,
                        variant = SwissButtonVariant.INK,
                        enabled = !isFetching,
                        minHeight = 42.dp,
                        onClick = onFetchModels,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fetch_models_${provider.id}")
                    )
                }

                AnimatedVisibility(visible = testResult != null) {
                    testResult?.let { res ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (res.success) SwissVerifiedTint else SwissSignalRedTint)
                                .border(1.dp, if (res.success) SwissVerifiedInk else SwissSignalRed, RectangleShape)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = res.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (res.success) SwissVerifiedInk else SwissInk
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !fetchMessage.isNullOrBlank()) {
                    fetchMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (msg.startsWith("Fetch failed")) SwissSignalRed else SwissInk
                        )
                    }
                }

                // Manual Model Add
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualModelInput,
                        onValueChange = { manualModelInput = it },
                        placeholder = { Text("Add model ID (e.g. gemini-2.5-flash)") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_model_input_${provider.id}")
                    )
                    SwissButton(
                        text = "Add",
                        variant = SwissButtonVariant.PRIMARY,
                        enabled = manualModelInput.isNotBlank(),
                        minHeight = 52.dp,
                        onClick = {
                            onAddManualModel(manualModelInput)
                            manualModelInput = ""
                        },
                        modifier = Modifier.testTag("add_manual_model_${provider.id}")
                    )
                }

                // Models & Capabilities
                if (provider.models.isNotEmpty()) {
                    SwissRuleDivider(symbol = "*")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MODELS // ${provider.models.size} CONFIGURED",
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissInk
                        )
                        SwissToggleCell(
                            label = if (modelsExpanded) "HIDE" else "SHOW",
                            selected = false,
                            onClick = { modelsExpanded = !modelsExpanded }
                        )
                    }

                    AnimatedVisibility(visible = modelsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SwissInk, RectangleShape)
                        ) {
                            provider.models.forEachIndexed { idx, model ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissSubtleFill)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = model.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = SwissInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (model.displayName != model.modelId) {
                                                Text(
                                                    text = model.modelId,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SwissMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        SwissIconCell(
                                            icon = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove model",
                                            onClick = { onDeleteModel(model.id) },
                                            bordered = false,
                                            cellSize = 32.dp
                                        )
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ModelCapability.entries.forEach { cap ->
                                            val enabled = model.supports(cap)
                                            SwissToggleCell(
                                                label = if (enabled) "✓ ${cap.label}" else cap.label,
                                                selected = enabled,
                                                useSignalRedWhenSelected = false,
                                                onClick = { onToggleCapability(model, cap) }
                                            )
                                        }
                                    }
                                }
                                if (idx < provider.models.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(SwissInk)
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
private fun ProviderEditorDialog(
    editor: ProviderEditorState,
    onBrowseCatalog: () -> Unit,
    onUpdateFields: (
        name: String?,
        type: ProviderType?,
        baseUrl: String?,
        proxyUrl: String?,
        apiKey: String?
    ) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        title = {
            Column {
                Text(
                    text = if (editor.editingProviderId == null) "PROVIDER // NEW ENTRY" else "PROVIDER // EDIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
                Text(
                    text = if (editor.editingProviderId == null) "Add Provider" else "Edit Provider",
                    style = MaterialTheme.typography.titleLarge,
                    color = SwissInk
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editor.editingProviderId == null) {
                    SwissButton(
                        text = "Pick from All ${ProviderCatalog.all.size} Providers (A–Z)",
                        variant = SwissButtonVariant.INK,
                        minHeight = 42.dp,
                        onClick = onBrowseCatalog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_browse_catalog_button")
                    )
                }

                if (editor.helperHint.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .border(1.dp, SwissInk, RectangleShape)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "SPEC // ${editor.helperHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissInk
                        )
                    }
                }

                if (editor.hasUrlPlaceholders) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissWarnTint)
                            .border(1.dp, SwissSignalRed, RectangleShape)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "TEMPLATE URL // Replace <...> placeholders in Base URL with your resource/account values before testing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissWarnInk
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onUpdateFields(it, null, null, null, null) },
                    label = { Text("Provider Name") },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_provider_name")
                )

                Text(
                    text = "PROTOCOL // TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissMuted
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderType.entries.forEach { type ->
                        SwissToggleCell(
                            label = type.label,
                            selected = editor.type == type,
                            useSignalRedWhenSelected = type == ProviderType.GEMINI,
                            onClick = { onUpdateFields(null, type, null, null, null) }
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.baseUrl,
                    onValueChange = { onUpdateFields(null, null, it, null, null) },
                    label = { Text("Base URL") },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_base_url")
                )

                OutlinedTextField(
                    value = editor.proxyUrl,
                    onValueChange = { onUpdateFields(null, null, null, it, null) },
                    label = { Text("Proxy / Gateway URL (Optional)") },
                    placeholder = { Text("https://my-proxy.example.com/v1") },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_proxy_url")
                )

                OutlinedTextField(
                    value = editor.apiKey,
                    onValueChange = { onUpdateFields(null, null, null, null, it) },
                    label = {
                        Text(
                            if (editor.requiresApiKey) {
                                "API Key (Stored on device only)"
                            } else {
                                "API Key (Optional for local server)"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key",
                                tint = SwissInk
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_api_key")
                )
            }
        },
        confirmButton = {
            SwissButton(
                text = "Save",
                variant = SwissButtonVariant.PRIMARY,
                enabled = editor.name.isNotBlank(),
                minHeight = 42.dp,
                onClick = onSave,
                modifier = Modifier.testTag("editor_save_provider")
            )
        },
        dismissButton = {
            SwissButton(
                text = "Cancel",
                variant = SwissButtonVariant.OUTLINE,
                minHeight = 42.dp,
                onClick = onDismiss
            )
        }
    )
}
