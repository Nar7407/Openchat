package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import com.example.domain.model.QuickContact
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import com.example.util.DeviceAction

/**
 * Renders a sequential queue of stacked Device Action Cards:
 * - Step 1 is rendered live & interactive via [DeviceActionConfirmCard].
 * - Steps 2..N are stacked cleanly below as queued preview cards that automatically
 *   promote to Step 1 once the active step completes or is confirmed via earbud tap.
 */
@Composable
fun DeviceActionQueueStack(
    actions: List<DeviceAction>,
    onExecuteActive: (DeviceAction) -> Unit,
    onDismissActive: () -> Unit,
    onUpdateActive: ((DeviceAction) -> Unit)? = null,
    onRemoveQueuedAt: ((Int) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    quickContacts: List<QuickContact> = emptyList(),
    onSaveQuickContact: ((name: String, phone: String) -> Unit)? = null,
    autoLaunchDialer: Boolean = true,
    autoLaunchWhatsApp: Boolean = true,
    showEarbudHints: Boolean = true,
    autoDialWebhookUrl: String = "",
    onDialLaunched: ((DeviceAction.DialPhone, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val active = actions.firstOrNull() ?: return
    val queuedCount = (actions.size - 1).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_action_queue_stack"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (actions.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSignalRed, RectangleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("action_chain_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTION CHAIN // STEP 1 OF ${actions.size} ACTIVE · $queuedCount NEXT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SwissWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onClearAll != null) {
                    Text(
                        text = "[CLEAR ALL]",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissWhite,
                        modifier = Modifier
                            .clickable { onClearAll() }
                            .padding(start = 8.dp)
                            .testTag("action_chain_clear_all")
                    )
                }
            }
        }

        DeviceActionConfirmCard(
            action = active,
            onExecute = { onExecuteActive(active) },
            onDismiss = onDismissActive,
            quickContacts = quickContacts,
            onUpdateAction = onUpdateActive,
            onSaveQuickContact = onSaveQuickContact,
            // When multiple actions are chained, avoid auto-launching dialer silently before Step 1 review
            autoLaunchDialer = autoLaunchDialer && actions.size == 1,
            autoLaunchWhatsApp = autoLaunchWhatsApp,
            showEarbudHints = showEarbudHints,
            autoDialWebhookUrl = autoDialWebhookUrl,
            onDialLaunched = onDialLaunched
        )

        if (actions.size > 1) {
            actions.drop(1).forEachIndexed { offsetIdx, nextAction ->
                val stepNum = offsetIdx + 2
                val queueIndex = offsetIdx + 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill, RectangleShape)
                        .border(1.dp, SwissInk.copy(alpha = 0.65f), RectangleShape)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("queued_action_card_$stepNum"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEP $stepNum",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissWhite,
                        modifier = Modifier
                            .background(SwissInk, RectangleShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = nextAction.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = nextAction.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = SwissInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (nextAction.subtitle.isNotBlank()) {
                            Text(
                                text = nextAction.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onRemoveQueuedAt != null) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Skip queued step $stepNum",
                            tint = SwissInk,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onRemoveQueuedAt(queueIndex) }
                                .testTag("remove_queued_action_$stepNum")
                        )
                    }
                }
            }
        }
    }
}
