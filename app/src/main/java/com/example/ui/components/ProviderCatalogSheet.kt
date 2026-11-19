package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ProviderCatalog
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderPreset
import com.example.domain.model.ProviderType
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderCatalogSheet(
    searchQuery: String,
    selectedCategory: ProviderCategory,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (ProviderCategory) -> Unit,
    onSelectPreset: (ProviderPreset) -> Unit,
    onCreateCustomFromSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filtered = remember(searchQuery, selectedCategory) {
        ProviderCatalog.filter(searchQuery, selectedCategory)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .border(1.dp, SwissInk, RectangleShape)
        ) {
            // Top segmented header bar
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
                        text = "INDEX // OPENCODE A–Z DIRECTORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    Text(
                        text = "provider catalog (${filtered.size}/${ProviderCatalog.all.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = SwissInk
                    )
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close catalog",
                    onClick = onDismiss,
                    bordered = false,
                    cellSize = 52.dp,
                    modifier = Modifier
                        .border(1.dp, SwissInk, RectangleShape)
                        .testTag("catalog_close_button")
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            "Search ${ProviderCatalog.all.size} providers (Bedrock, xAI, Alibaba…)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = SwissInk)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear search",
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
                        .testTag("catalog_search_input")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderCategory.entries.forEach { category ->
                        SwissToggleCell(
                            label = category.label,
                            selected = selectedCategory == category,
                            onClick = { onCategoryChange(category) },
                            useSignalRedWhenSelected = category == ProviderCategory.POPULAR,
                            modifier = Modifier.testTag("catalog_category_${category.name.lowercase()}")
                        )
                    }
                }

                if (filtered.isEmpty()) {
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
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "QUERY // ZERO MATCHES",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed
                            )
                            Text(
                                text = "No matching provider found",
                                style = MaterialTheme.typography.titleMedium,
                                color = SwissInk
                            )
                            Text(
                                text = "Add \"$searchQuery\" as a custom OpenAI-compatible, Anthropic, or Gemini endpoint.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissMuted
                            )
                            SwissButton(
                                text = "Add Custom Provider",
                                onClick = onCreateCustomFromSearch,
                                leadingIcon = Icons.Filled.Add,
                                variant = SwissButtonVariant.PRIMARY,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("catalog_add_custom_fallback")
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, SwissInk, RectangleShape),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filtered, key = { it.id }) { preset ->
                            ProviderCatalogRow(
                                preset = preset,
                                onClick = { onSelectPreset(preset) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCatalogRow(
    preset: ProviderPreset,
    onClick: () -> Unit
) {
    val (badgeBg, badgeFg, badgeLabel) = when (preset.type) {
        ProviderType.GEMINI -> Triple(SwissSignalRed, SwissWhite, "GEMINI")
        ProviderType.ANTHROPIC -> Triple(SwissVerifiedTint, SwissVerifiedInk, "ANTHROPIC")
        ProviderType.OPENAI_COMPATIBLE -> Triple(SwissConcrete, SwissInk, "OPENAI")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .clickable(onClick = onClick)
            .testTag("catalog_item_${preset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = SwissInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    SwissBadge(
                        text = badgeLabel,
                        containerColor = badgeBg,
                        contentColor = badgeFg,
                        borderColor = if (preset.type == ProviderType.GEMINI) SwissSignalRed else SwissInk
                    )
                }
                Text(
                    text = preset.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preset.hasUrlPlaceholders) SwissSignalRed else SwissMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Configure ${preset.title}",
                tint = SwissInk,
                modifier = Modifier.size(18.dp)
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
