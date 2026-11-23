package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.example.widget.AllChatWidgetProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.R
import com.example.data.remote.ConnectionTestResult
import com.example.domain.model.AutoRouterConfig
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderCatalog
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import com.example.domain.model.SelectableModel
import com.example.domain.model.WebSearchProvider
import com.example.ui.components.CustomAppsSettingsPanel
import com.example.ui.components.DailyBudgetGuardPanel
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.ModelPricingDialog
import com.example.ui.components.PasswordPromptDialog
import com.example.ui.components.PasteCurlImporterBox
import com.example.ui.components.ProviderCatalogSheet
import com.example.ui.components.QuickContactsSettingsPanel
import com.example.ui.components.RoutinesSettingsPanel
import com.example.ui.components.SmartTriggersSettingsPanel
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissRuleDivider
import com.example.ui.components.SwissToggleCell
import com.example.ui.components.SwissUtilityHeader
import com.example.ui.components.VaultSecurityAndBackupPanel
import com.example.ui.components.VisionFallbackSettingsPanel
import com.example.ui.components.WebhookRegistryPanel
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
    onTestAssistantOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showExportPwdDialog by remember { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingImportRawJson by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            val payload = pendingExportJson
            pendingExportJson = null
            if (uri != null && payload != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open output stream.")
                }.onFailure { err ->
                    viewModel.reportVaultError(err.message ?: "Failed to save vault file.")
                }
            }
        }
    )

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read selected file.")
                }.onSuccess { raw ->
                    pendingImportRawJson = raw
                }.onFailure { err ->
                    viewModel.reportVaultError(err.message ?: "Failed to read backup file.")
                }
            }
        }
    )

    val importHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read conversations.json.")
                }.onSuccess { raw ->
                    viewModel.importExternalConversations(raw)
                }.onFailure { err ->
                    viewModel.reportVaultError(err.message ?: "Failed to read selected JSON file.")
                }
            }
        }
    )

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

                // Vault Security (PIN Lock + Auto-Lock), Currency & Encrypted .allchat Backup/Restore
                item {
                    VaultSecurityAndBackupPanel(
                        displayCurrency = state.displayCurrency,
                        isPinEnabled = state.isPinEnabled,
                        autoLockMinutes = state.autoLockMinutes,
                        lockOnAppSwitch = state.lockOnAppSwitch,
                        statusMessage = state.vaultStatusMessage,
                        errorMessage = state.vaultErrorMessage,
                        onSelectCurrency = viewModel::setDisplayCurrency,
                        onRequestSetPin = { showPinSetupDialog = true },
                        onDisablePin = viewModel::disablePin,
                        onLockNow = viewModel::lockVaultNow,
                        onSetAutoLockMinutes = viewModel::setAutoLockMinutes,
                        onSetLockOnAppSwitch = viewModel::setLockOnAppSwitch,
                        onRequestExportBackup = { showExportPwdDialog = true },
                        onRequestImportBackup = {
                            openBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        onDismissNotices = viewModel::clearVaultNotices
                    )
                }

                // Daily Budget Cap & 80% Auto-Downgrade Guard
                item {
                    DailyBudgetGuardPanel(
                        config = state.dailyBudgetConfig,
                        todaySpendUsd = state.todaySpendUsd,
                        availableModels = state.textChatModels,
                        onSaveConfig = viewModel::saveDailyBudgetConfig
                    )
                }

                // Web Search Grounding (Tavily / Brave / Exa / Native Gemini)
                item {
                    WebSearchGroundingPanel(
                        selectedProvider = state.searchProvider,
                        hasApiKey = state.hasSearchApiKey,
                        onSelectProvider = viewModel::setSearchProvider,
                        onSaveApiKey = viewModel::saveSearchApiKey,
                        onOpenWebsite = { url -> openProviderWebsite(context, url) }
                    )
                }

                // Smart Auto-Router Specialist Model Assignment
                if (state.textChatModels.isNotEmpty()) {
                    item {
                        AutoRouterSettingsPanel(
                            config = state.autoRouterConfig,
                            availableModels = state.textChatModels,
                            onUpdateConfig = viewModel::updateAutoRouterConfig
                        )
                    }
                }

                // Live Lens 429 Auto-Failover Vision Fallback Queue
                if (state.visionModels.isNotEmpty()) {
                    item {
                        VisionFallbackSettingsPanel(
                            visionModels = state.visionModels,
                            fallbackQueueIds = state.visionFallbackModelIds,
                            onUpdateQueue = viewModel::updateVisionFallbackQueue
                        )
                    }
                }

                // 1-Tap Assistant Routines & Macros Builder
                item {
                    RoutinesSettingsPanel(
                        routines = state.routines,
                        onSaveRoutine = viewModel::saveRoutine,
                        onDeleteRoutine = viewModel::deleteRoutine,
                        onAddStarterPreset = viewModel::addStarterRoutine
                    )
                }

                // Smart Triggers (Time Profiles, GPS Geofence Zones, Eco Mode, Whisper dB, 7d Auto-Prune)
                item {
                    SmartTriggersSettingsPanel(
                        timeProfilesEnabled = state.timeProfilesEnabled,
                        autoEcoMode = state.autoEcoMode,
                        autoDeleteJunkChats = state.autoDeleteJunkChats,
                        wakePhraseEnabled = state.wakePhraseEnabled,
                        ambientWhisperEnabled = state.ambientWhisperEnabled,
                        smartZones = state.smartZones,
                        onToggleTimeProfiles = viewModel::setTimeProfilesEnabled,
                        onToggleAutoEco = viewModel::setAutoEcoMode,
                        onToggleAutoDeleteJunk = viewModel::setAutoDeleteJunkChats,
                        onPruneJunkNow = viewModel::pruneJunkChatsNow,
                        onToggleWakePhrase = viewModel::setWakePhraseEnabled,
                        onToggleAmbientWhisper = viewModel::setAmbientWhisperEnabled,
                        onSaveSmartZone = viewModel::saveSmartZone,
                        onDeleteSmartZone = viewModel::deleteSmartZone
                    )
                }

                // Smart Home / Webhook / Tasker Bridge
                item {
                    WebhookRegistryPanel(
                        webhooks = state.webhooks,
                        testStatus = state.webhookTestStatus,
                        onSaveWebhook = viewModel::saveWebhook,
                        onDeleteWebhook = viewModel::deleteWebhook,
                        onTestWebhook = viewModel::testWebhook
                    )
                }

                // Quick Contacts Manager (Zero-Permission Phone Contact Picker + Email & UPI IDs)
                item {
                    QuickContactsSettingsPanel(
                        contacts = state.quickContacts,
                        onSaveContact = { name, phone, email, upi ->
                            viewModel.saveQuickContact(name, phone, email, upi)
                        },
                        onDeleteContact = viewModel::deleteQuickContact,
                        onReportError = viewModel::reportVaultError
                    )
                }

                // Custom App Packages Manager (for "Open <App>" Device Actions)
                item {
                    CustomAppsSettingsPanel(
                        customApps = state.customApps,
                        onSaveCustomApp = viewModel::saveCustomApp,
                        onDeleteCustomApp = viewModel::deleteCustomApp
                    )
                }

                // ChatGPT / Claude Conversation History Importer
                item {
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "MIGRATE // CHATGPT & CLAUDE IMPORTER",
                                rightLabel = "CONVERSATIONS.JSON"
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Import your exported conversations.json from ChatGPT or Claude directly into AllChat's local encrypted database without sending data to any server.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                                SwissButton(
                                    text = if (state.isImportingHistory) "Importing…" else "Import conversations.json",
                                    leadingIcon = Icons.Outlined.UploadFile,
                                    variant = SwissButtonVariant.INK,
                                    enabled = !state.isImportingHistory,
                                    minHeight = 42.dp,
                                    onClick = {
                                        importHistoryLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("import_external_history_button")
                                )
                            }
                        }
                    }
                }

                // Native Android Home Screen Widget & App Shortcuts Launcher
                item {
                    var widgetPinStatus by remember { mutableStateOf<String?>(null) }
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "ANDROID // HOME WIDGET & SHORTCUTS",
                                rightLabel = "QUICK LAUNCH"
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Pin the AllChat Quick Ask & Camera Search widget to your Android Home Screen, or long-press the AllChat launcher icon for instant New Chat, Voice Prompt, Summarize Clipboard, and Image Studio shortcuts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                                SwissButton(
                                    text = "Pin Quick Ask Widget to Home Screen",
                                    variant = SwissButtonVariant.PRIMARY,
                                    minHeight = 42.dp,
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            val appWidgetManager = AppWidgetManager.getInstance(context)
                                            val provider = ComponentName(context, AllChatWidgetProvider::class.java)
                                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                                appWidgetManager.requestPinAppWidget(provider, null, null)
                                                widgetPinStatus = "WIDGET // Pin prompt sent to Android Launcher."
                                            } else {
                                                widgetPinStatus = "INFO // Long-press your Home Screen -> Widgets -> AllChat Quick Ask."
                                            }
                                        } else {
                                            widgetPinStatus = "INFO // Long-press your Home Screen -> Widgets -> AllChat Quick Ask."
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pin_home_widget_button")
                                )
                                widgetPinStatus?.let { notice ->
                                    Text(
                                        text = notice,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
                            }
                        }
                    }
                }

                // Assistant Overlay & Volume Up + Volume Down System-Wide Trigger Guide
                item {
                    var triggerNotice by remember { mutableStateOf<String?>(null) }
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "SYS // ASSISTANT OVERLAY & VOLUME TRIGGER",
                                rightLabel = "VOL UP + DOWN",
                                rightAccent = true
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "In-App Hardware Chord: Press Volume Up + Volume Down simultaneously (within 300ms) anywhere inside AllChat to pop open the Voice & Lens Assistant Overlay.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissInk
                                )
                                Text(
                                    text = "System-Wide (Lock Screen / Any App): Bind Volume Up + Down via Android Accessibility → Volume key shortcut, Key Mapper, or Default Digital Assistant to launch:\n• Deep Link: allchat://assistant\n• Activity: com.example.ui.assistant.AssistantOverlayActivity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SwissButton(
                                        text = "Test Overlay",
                                        variant = SwissButtonVariant.PRIMARY,
                                        minHeight = 40.dp,
                                        onClick = onTestAssistantOverlay,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_test_assistant_overlay")
                                    )
                                    SwissButton(
                                        text = "Copy Deep Link",
                                        variant = SwissButtonVariant.OUTLINE,
                                        minHeight = 40.dp,
                                        onClick = {
                                            clipboard.setText(AnnotatedString("allchat://assistant"))
                                            triggerNotice = "COPIED // allchat://assistant copied for Key Mapper / Shortcuts."
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_copy_assistant_uri")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SwissButton(
                                        text = "Accessibility Settings",
                                        variant = SwissButtonVariant.INK,
                                        minHeight = 40.dp,
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            }.onFailure {
                                                triggerNotice = "ERR // Open Android Settings → Accessibility manually."
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_open_accessibility")
                                    )
                                    SwissButton(
                                        text = "Default Assistant",
                                        variant = SwissButtonVariant.OUTLINE,
                                        minHeight = 40.dp,
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            }.onFailure {
                                                triggerNotice = "ERR // Open Android Settings → Default Apps → Digital Assistant."
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_open_default_assistant")
                                    )
                                }
                                triggerNotice?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
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
                            onToggleEnabled = { enabled -> viewModel.toggleProviderEnabled(provider.id, enabled) },
                            onTest = { viewModel.testConnection(provider) },
                            onFetchModels = { viewModel.fetchModels(provider) },
                            onAddManualModel = { modelId -> viewModel.addManualModel(provider.id, modelId) },
                            onToggleCapability = viewModel::toggleCapability,
                            onEditPricing = viewModel::openModelPricing,
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
            onImportCurl = viewModel::importProviderFromCurl,
            onUpdateFields = { n, t, b, p, k, modelsText ->
                viewModel.updateEditorFields(
                    name = n,
                    type = t,
                    baseUrl = b,
                    proxyUrl = p,
                    apiKey = k,
                    customModelsText = modelsText
                )
            },
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditorProvider
        )
    }

    if (showPinSetupDialog) {
        PasswordPromptDialog(
            eyebrow = "SEC // VAULT LOCK",
            title = if (state.isPinEnabled) "Update Vault PIN" else "Enable Vault PIN Lock",
            description = "Set a 4+ character PIN or master passphrase. All API keys will be encrypted with PBKDF2-HMAC-SHA256 + AES-256-GCM and decrypted only in memory after unlocking.",
            confirmLabel = "Enable Lock",
            requireConfirmation = true,
            onDismiss = { showPinSetupDialog = false },
            onConfirm = { pin ->
                showPinSetupDialog = false
                viewModel.enableOrUpdatePin(pin)
            }
        )
    }

    if (showExportPwdDialog) {
        PasswordPromptDialog(
            eyebrow = "BACKUP // ENCRYPT .ALLCHAT",
            title = "Encrypt Vault Backup",
            description = "Choose a backup password (min 4 chars) to encrypt all providers, API keys, models, pricing, personas, and chat branches into a portable .allchat JSON file.",
            confirmLabel = "Export File",
            requireConfirmation = true,
            onDismiss = { showExportPwdDialog = false },
            onConfirm = { password ->
                showExportPwdDialog = false
                scope.launch {
                    viewModel.exportEncryptedVault(password).onSuccess { encryptedJson ->
                        pendingExportJson = encryptedJson
                        createBackupLauncher.launch("allchat_vault_${System.currentTimeMillis()}.allchat.json")
                    }
                }
            }
        )
    }

    pendingImportRawJson?.let { rawJson ->
        PasswordPromptDialog(
            eyebrow = "RESTORE // DECRYPT .ALLCHAT",
            title = "Decrypt & Restore Vault",
            description = "Enter the backup password used to encrypt this .allchat vault archive. Restoring replaces local providers, personas, and chats.",
            confirmLabel = "Restore Now",
            requireConfirmation = false,
            onDismiss = { pendingImportRawJson = null },
            onConfirm = { password ->
                pendingImportRawJson = null
                viewModel.importEncryptedVault(rawJson, password)
            }
        )
    }

    state.pricingTargetModel?.let { targetModel ->
        ModelPricingDialog(
            model = targetModel,
            onDismiss = viewModel::dismissModelPricing,
            onSave = { inputPrice, outputPrice ->
                viewModel.saveModelPricing(targetModel.id, inputPrice, outputPrice)
            }
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
    onToggleEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onFetchModels: () -> Unit,
    onAddManualModel: (String) -> Unit,
    onToggleCapability: (ModelInfo, ModelCapability) -> Unit,
    onEditPricing: (ModelInfo) -> Unit,
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                    SwissToggleCell(
                        label = if (provider.isEnabled) "ON" else "PAUSED",
                        selected = provider.isEnabled,
                        useSignalRedWhenSelected = false,
                        onClick = { onToggleEnabled(!provider.isEnabled) },
                        modifier = Modifier.testTag("toggle_provider_enabled_${provider.id}")
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
                val context = LocalContext.current
                val apiKeyUrl = remember(provider.name, provider.baseUrl) {
                    ProviderCatalog.resolveWebsiteUrl(provider.name, provider.baseUrl)
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = SwissInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (apiKeyUrl != null) {
                        GetApiKeyLinkRow(
                            url = apiKeyUrl,
                            onClick = { openProviderWebsite(context, apiKeyUrl) },
                            modifier = Modifier.testTag("provider_get_api_key_${provider.id}")
                        )
                    }
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
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val priceLabel = if (model.hasPricing) {
                                                String.format(
                                                    Locale.US,
                                                    "$%.2f/$%.2f",
                                                    model.inputPricePerMillion,
                                                    model.outputPricePerMillion
                                                )
                                            } else {
                                                "$ / 1M"
                                            }
                                            SwissToggleCell(
                                                label = priceLabel,
                                                selected = model.hasPricing,
                                                useSignalRedWhenSelected = false,
                                                onClick = { onEditPricing(model) },
                                                modifier = Modifier.testTag("model_pricing_${model.id}")
                                            )
                                            SwissIconCell(
                                                icon = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Remove model",
                                                onClick = { onDeleteModel(model.id) },
                                                bordered = false,
                                                cellSize = 32.dp
                                            )
                                        }
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
    onImportCurl: (String) -> Boolean,
    onUpdateFields: (
        name: String?,
        type: ProviderType?,
        baseUrl: String?,
        proxyUrl: String?,
        apiKey: String?,
        customModelsText: String?
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
                    PasteCurlImporterBox(onImportCurl = onImportCurl)
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

                val context = LocalContext.current
                val apiKeyUrl = remember(editor.name, editor.baseUrl) {
                    ProviderCatalog.resolveWebsiteUrl(editor.name, editor.baseUrl)
                }

                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onUpdateFields(it, null, null, null, null, null) },
                    label = { Text("Provider Name") },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_provider_name")
                )

                if (apiKeyUrl != null) {
                    GetApiKeyLinkRow(
                        url = apiKeyUrl,
                        onClick = { openProviderWebsite(context, apiKeyUrl) },
                        showHostHint = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_get_api_key_link")
                    )
                }

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
                            onClick = { onUpdateFields(null, type, null, null, null, null) }
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.baseUrl,
                    onValueChange = { onUpdateFields(null, null, it, null, null, null) },
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
                    onValueChange = { onUpdateFields(null, null, null, it, null, null) },
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
                    onValueChange = { onUpdateFields(null, null, null, null, it, null) },
                    label = {
                        Text(
                            if (editor.requiresApiKey) {
                                "API Key(s) — Comma-separated for 429 rotation"
                            } else {
                                "API Key (Optional for local server)"
                            }
                        )
                    },
                    supportingText = {
                        Text(
                            "Add multiple comma-separated keys (key1, key2) to auto-rotate on HTTP 429 rate limits.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
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

                OutlinedTextField(
                    value = editor.customModelsText,
                    onValueChange = { onUpdateFields(null, null, null, null, null, it) },
                    label = { Text("Model List (Optional, comma-separated)") },
                    placeholder = { Text("gpt-4o, deepseek-r1, llama-3.3-70b") },
                    supportingText = {
                        Text(
                            "Pre-seed custom model IDs directly into Room, or leave blank and tap Fetch Models after saving.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
                        )
                    },
                    maxLines = 3,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_custom_models")
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

@Composable
private fun GetApiKeyLinkRow(
    url: String,
    onClick: () -> Unit,
    showHostHint: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hostLabel = remember(url) {
        runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
    }
    Row(
        modifier = modifier
            .background(SwissSignalRedTint, RectangleShape)
            .border(1.dp, SwissSignalRed, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Key,
            contentDescription = null,
            tint = SwissSignalRed,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = "Get an API key",
            style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
            color = SwissSignalRed
        )
        if (showHostHint && hostLabel.isNotBlank()) {
            Text(
                text = "// $hostLabel",
                style = MaterialTheme.typography.labelSmall,
                color = SwissMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = "Open provider website",
            tint = SwissSignalRed,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun WebSearchGroundingPanel(
    selectedProvider: WebSearchProvider,
    hasApiKey: Boolean,
    onSelectProvider: (WebSearchProvider) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onOpenWebsite: (String) -> Unit
) {
    var apiKeyDraft by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    SwissPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            SwissPanelHeader(
                leftLabel = "GROUNDING // BYOK WEB SEARCH",
                rightLabel = if (selectedProvider == WebSearchProvider.GEMINI_NATIVE) "NATIVE" else if (hasApiKey) "KEY SAVED" else "NO KEY",
                rightAccent = selectedProvider != WebSearchProvider.GEMINI_NATIVE && !hasApiKey
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Toggle the WEB icon in Chat to inject top 5 live search snippets with clickable [1] [2] citation pills.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WebSearchProvider.entries.forEach { provider ->
                        SwissToggleCell(
                            label = provider.label,
                            selected = selectedProvider == provider,
                            useSignalRedWhenSelected = true,
                            onClick = { onSelectProvider(provider) },
                            modifier = Modifier.testTag("search_provider_${provider.name.lowercase()}")
                        )
                    }
                }

                if (selectedProvider != WebSearchProvider.GEMINI_NATIVE) {
                    val websiteUrl = when (selectedProvider) {
                        WebSearchProvider.TAVILY -> "https://app.tavily.com/home"
                        WebSearchProvider.BRAVE -> "https://api.search.brave.com/app/keys"
                        WebSearchProvider.EXA -> "https://dashboard.exa.ai/api-keys"
                        WebSearchProvider.GEMINI_NATIVE -> ""
                    }
                    if (websiteUrl.isNotBlank()) {
                        GetApiKeyLinkRow(
                            url = websiteUrl,
                            onClick = { onOpenWebsite(websiteUrl) },
                            showHostHint = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            label = {
                                Text(
                                    if (hasApiKey) "${selectedProvider.label} Key (Saved — enter new to replace)"
                                    else "${selectedProvider.label} API Key"
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
                                        contentDescription = "Toggle visibility",
                                        tint = SwissInk
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_api_key_input")
                        )
                        SwissButton(
                            text = "Save",
                            variant = SwissButtonVariant.PRIMARY,
                            minHeight = 52.dp,
                            onClick = {
                                onSaveApiKey(apiKeyDraft)
                                apiKeyDraft = ""
                            },
                            modifier = Modifier.testTag("save_search_api_key_button")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoRouterSettingsPanel(
    config: AutoRouterConfig,
    availableModels: List<SelectableModel>,
    onUpdateConfig: (AutoRouterConfig) -> Unit
) {
    fun resolve(id: Long?) = id?.let { target -> availableModels.firstOrNull { it.model.id == target } }

    SwissPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            SwissPanelHeader(
                leftLabel = "ROUTER // SMART AUTO-ROUTING",
                rightLabel = "AUTO MODE"
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Assign specialist models for each task type, then select AUTO in Chat's model picker to route prompts automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                AutoRouteSlotRow(
                    label = "FAST CHAT",
                    selected = resolve(config.fastChatModelId),
                    models = availableModels,
                    onSelect = { onUpdateConfig(config.copy(fastChatModelId = it.model.id)) }
                )
                AutoRouteSlotRow(
                    label = "HEAVY CODING",
                    selected = resolve(config.heavyCodingModelId),
                    models = availableModels,
                    onSelect = { onUpdateConfig(config.copy(heavyCodingModelId = it.model.id)) }
                )
                AutoRouteSlotRow(
                    label = "VISION (IMAGES)",
                    selected = resolve(config.visionModelId),
                    models = availableModels,
                    onSelect = { onUpdateConfig(config.copy(visionModelId = it.model.id)) }
                )
                AutoRouteSlotRow(
                    label = "LONG CONTEXT (>10K TOK)",
                    selected = resolve(config.longContextModelId),
                    models = availableModels,
                    onSelect = { onUpdateConfig(config.copy(longContextModelId = it.model.id)) }
                )
            }
        }
    }
}

@Composable
private fun AutoRouteSlotRow(
    label: String,
    selected: SelectableModel?,
    models: List<SelectableModel>,
    onSelect: (SelectableModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "SPECIALIST // $label",
            style = MaterialTheme.typography.labelSmall,
            color = SwissInk
        )
        ModelPickerButton(
            selectedModel = selected,
            availableModels = models,
            requiredCapability = ModelCapability.TEXT_CHAT,
            onSelectModel = onSelect,
            onOpenSettings = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun openProviderWebsite(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

