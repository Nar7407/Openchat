package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissWhite

/**
 * Dual-Lane Cloud Model Assignment settings panel.
 * Lets the user designate a dedicated "Fast Utility Model" (e.g. Groq gpt-oss-20b / Gemini Flash)
 * alongside their primary reasoning model for lightweight background tasks (session auto-titling,
 * Memory Bank extraction, Live Lens tagging, and quick clipboard summaries) with an automatic
 * 2-message / 800-char (~250 token) payload trimmer.
 */
@Composable
fun FastUtilityModelSettingsPanel(
    availableModels: List<SelectableModel>,
    fastUtilityModelId: Long?,
    onSelectFastUtilityModel: (Long?) -> Unit,
    onOpenProvidersHint: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedFastModel = availableModels.firstOrNull { it.model.id == fastUtilityModelId }

    SwissPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_fast_utility_model_panel")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwissPanelHeader(
                leftLabel = "LANE 02 // FAST UTILITY MODEL ROUTING",
                rightLabel = if (selectedFastModel != null) "DUAL-LANE ACTIVE" else "USING PRIMARY",
                rightAccent = selectedFastModel != null,
                backgroundColor = SwissSubtleFill
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Route lightweight background tasks—auto-titling, Memory extraction, Live Lens tagging, and quick clipboard summaries—to a fast low-cost cloud endpoint so primary reasoning quotas stay untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .border(1.dp, SwissInk, RectangleShape)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PAYLOAD TRIMMER // LATEST 2 MSGS · MAX 800 CHARS (~250 TOKENS)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissVerifiedInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                ModelPickerButton(
                    selectedModel = selectedFastModel,
                    availableModels = availableModels,
                    requiredCapability = ModelCapability.TEXT_CHAT,
                    onSelectModel = { picked -> onSelectFastUtilityModel(picked.model.id) },
                    onOpenSettings = onOpenProvidersHint,
                    bordered = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fast_utility_model_picker")
                )

                if (selectedFastModel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ASSIGNED: ${selectedFastModel.model.displayName} (${selectedFastModel.provider.name})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "[ RESET TO PRIMARY ]",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SwissSignalRed,
                            modifier = Modifier
                                .clickable { onSelectFastUtilityModel(null) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("reset_fast_utility_model_btn")
                        )
                    }
                } else {
                    Text(
                        text = "Tip: Pick a high-speed model like Groq llama-3.1-8b-instant, openai/gpt-oss-20b, or Gemini Flash for <500ms background tasks.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                }
            }
        }
    }
}
