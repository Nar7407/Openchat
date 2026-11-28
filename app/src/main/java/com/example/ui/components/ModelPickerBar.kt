package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerButton(
    selectedModel: SelectableModel?,
    availableModels: List<SelectableModel>,
    requiredCapability: ModelCapability,
    onSelectModel: (SelectableModel) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
    openTrigger: Int = 0,
    supportsAutoRoute: Boolean = false,
    isAutoRouteEnabled: Boolean = false,
    onSelectAutoRoute: (() -> Unit)? = null,
    pinnedModelIds: List<Long> = emptyList(),
    modelLatencyMs: Map<Long, Long> = emptyMap(),
    onTogglePinModel: ((Long) -> Unit)? = null,
    onSwipePinnedModel: ((forward: Boolean) -> Unit)? = null
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(openTrigger) {
        if (openTrigger > 0) {
            searchQuery = ""
            showSheet = true
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bg = if (isPressed) SwissInk else SwissPaper
    val fg = if (isPressed) SwissCanvas else SwissInk
    val subFg = if (isPressed) SwissConcrete else SwissMuted

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .background(bg, RectangleShape)
            .then(if (bordered) Modifier.border(1.dp, SwissInk, RectangleShape) else Modifier)
            .then(
                if (onSwipePinnedModel != null) {
                    Modifier.pointerInput(onSwipePinnedModel) {
                        var accumulatedDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                accumulatedDrag += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    accumulatedDrag < -56f -> onSwipePinnedModel(true)
                                    accumulatedDrag > 56f -> onSwipePinnedModel(false)
                                }
                                accumulatedDrag = 0f
                            },
                            onDragCancel = { accumulatedDrag = 0f }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    searchQuery = ""
                    showSheet = true
                }
            )
            .testTag("model_picker_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isAutoRouteEnabled || selectedModel != null) SwissSignalRed else SwissMuted)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isAutoRouteEnabled && supportsAutoRoute -> "AUTO // SMART ROUTER"
                        selectedModel != null -> "MODEL // ${selectedModel.provider.name.uppercase()}"
                        else -> "NO ${requiredCapability.label.uppercase()} MODEL"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAutoRouteEnabled && supportsAutoRoute && !isPressed) SwissSignalRed else subFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isAutoRouteEnabled && supportsAutoRoute -> "Auto-Route Specialist"
                        selectedModel != null -> selectedModel.model.displayName
                        else -> "Select ${requiredCapability.label} model"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selectedModel?.model?.supportsImageInput == true &&
                requiredCapability == ModelCapability.TEXT_CHAT
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Vision enabled",
                    tint = if (isPressed) SwissWhite else SwissSignalRed,
                    modifier = Modifier.size(15.dp)
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Choose model",
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filteredModels = remember(availableModels, searchQuery) {
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                availableModels
            } else {
                availableModels.filter { item ->
                    item.model.displayName.contains(q, ignoreCase = true) ||
                        item.model.modelId.contains(q, ignoreCase = true) ||
                        item.provider.name.contains(q, ignoreCase = true)
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = SwissCanvas,
            shape = RectangleShape,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .border(1.dp, SwissInk, RectangleShape)
            ) {
                // Header bar with explicit Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "SELECTOR // ${requiredCapability.label.uppercase()} (${filteredModels.size}/${availableModels.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
                        )
                        Text(
                            text = "${requiredCapability.label} Models",
                            style = MaterialTheme.typography.titleLarge,
                            color = SwissInk
                        )
                    }
                    SwissIconCell(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close model selector",
                        onClick = { showSheet = false },
                        bordered = false,
                        cellSize = 52.dp,
                        modifier = Modifier.border(1.dp, SwissInk, RectangleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwissInk)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (availableModels.isEmpty()) {
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
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "STATUS // UNCONFIGURED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = "No ${requiredCapability.label} models available",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SwissInk
                                )
                                Text(
                                    text = "Add a provider in Settings, fetch or add models, and toggle ${requiredCapability.label} on.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted
                                )
                                SwissButton(
                                    text = "Configure Providers",
                                    onClick = {
                                        showSheet = false
                                        onOpenSettings()
                                    },
                                    leadingIcon = Icons.Outlined.Settings,
                                    variant = SwissButtonVariant.PRIMARY,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("picker_go_to_settings")
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Filter ${availableModels.size} models (e.g. llama, deepseek, qwen, gemini)…",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = SwissInk)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Clear filter",
                                            tint = SwissInk,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RectangleShape,
                            colors = swissTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model_picker_search")
                        )

                        if (filteredModels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "NO MODELS MATCH \"$searchQuery\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SwissMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(1.dp, SwissInk, RectangleShape)
                            ) {
                                if (supportsAutoRoute && onSelectAutoRoute != null && searchQuery.isBlank()) {
                                    item(key = "auto_router_option") {
                                        val autoBg = if (isAutoRouteEnabled) SwissInk else SwissPaper
                                        val autoFg = if (isAutoRouteEnabled) SwissWhite else SwissInk
                                        val autoSub = if (isAutoRouteEnabled) SwissConcrete else SwissMuted
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(autoBg)
                                                .clickable {
                                                    onSelectAutoRoute()
                                                    showSheet = false
                                                }
                                                .testTag("model_option_auto_router")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "AUTO // Smart Task Router",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = autoFg
                                                    )
                                                    Text(
                                                        text = "Auto-picks Fast Chat, Heavy Coding, Vision, or Long Context",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = autoSub,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                SwissBadge(
                                                    text = if (isAutoRouteEnabled) "ACTIVE" else "AUTO",
                                                    containerColor = SwissSignalRed,
                                                    contentColor = SwissWhite,
                                                    borderColor = SwissSignalRed
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(SwissInk)
                                            )
                                        }
                                    }
                                }
                                val pinnedModelsOrdered = pinnedModelIds.mapNotNull { pid ->
                                    filteredModels.firstOrNull { it.model.id == pid }
                                }
                                val pinnedIdSet = pinnedModelsOrdered.map { it.model.id }.toSet()
                                val unpinnedModels = filteredModels.filter { it.model.id !in pinnedIdSet }

                                if (pinnedModelsOrdered.isNotEmpty()) {
                                    item(key = "pinned_models_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(SwissInk)
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "PINNED FAVORITES (${pinnedModelIds.size}/4) // LOCKED AT TOP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissSignalRed
                                            )
                                            Text(
                                                text = "LIVE LATENCY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissConcrete
                                            )
                                        }
                                    }
                                    items(pinnedModelsOrdered, key = { "pinned_${it.model.id}" }) { item ->
                                        ModelPickerRowItem(
                                            item = item,
                                            isSelected = !isAutoRouteEnabled && item.model.id == selectedModel?.model?.id,
                                            isPinned = true,
                                            latencyMs = modelLatencyMs[item.model.id],
                                            onSelect = {
                                                onSelectModel(item)
                                                showSheet = false
                                            },
                                            onTogglePin = onTogglePinModel?.let { cb -> { cb(item.model.id) } }
                                        )
                                    }
                                    if (unpinnedModels.isNotEmpty()) {
                                        item(key = "all_models_header") {
                                            Text(
                                                text = "ALL ${requiredCapability.label.uppercase()} MODELS (${unpinnedModels.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissMuted,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(SwissCanvas)
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                items(unpinnedModels, key = { it.model.id }) { item ->
                                    ModelPickerRowItem(
                                        item = item,
                                        isSelected = !isAutoRouteEnabled && item.model.id == selectedModel?.model?.id,
                                        isPinned = false,
                                        latencyMs = modelLatencyMs[item.model.id],
                                        onSelect = {
                                            onSelectModel(item)
                                            showSheet = false
                                        },
                                        onTogglePin = onTogglePinModel?.let { cb -> { cb(item.model.id) } }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelPickerRowItem(
    item: SelectableModel,
    isSelected: Boolean,
    isPinned: Boolean,
    latencyMs: Long?,
    onSelect: () -> Unit,
    onTogglePin: (() -> Unit)?
) {
    val rowBg = if (isSelected) SwissInk else SwissPaper
    val rowFg = if (isSelected) SwissWhite else SwissInk
    val rowSub = if (isSelected) SwissConcrete else SwissMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onSelect)
            .testTag("model_option_${item.model.modelId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = rowFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.provider.name.uppercase()} / ${item.model.modelId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = rowSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (latencyMs != null && latencyMs > 0L) {
                    SwissBadge(
                        text = "${latencyMs}ms",
                        containerColor = if (isSelected) SwissPaper else SwissCanvas,
                        contentColor = if (latencyMs <= 900L) SwissSignalRed else SwissInk
                    )
                }
                if (item.model.supportsImageInput) {
                    SwissBadge(
                        text = "VIS",
                        containerColor = if (isSelected) SwissPaper else SwissConcrete,
                        contentColor = SwissInk
                    )
                }
                if (isSelected) {
                    SwissBadge(
                        text = "ACTIVE",
                        containerColor = SwissSignalRed,
                        contentColor = SwissWhite,
                        borderColor = SwissSignalRed
                    )
                }
                if (onTogglePin != null) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("pin_model_${item.model.modelId}")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin favorite model" else "Pin favorite model",
                            tint = if (isPinned) SwissSignalRed else rowSub,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SwissInk)
        )
    }
}
