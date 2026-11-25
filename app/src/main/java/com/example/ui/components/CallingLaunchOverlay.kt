package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.DeviceAction
import kotlinx.coroutines.delay

/**
 * Full-screen high-contrast Calling Overlay shown inside AllChat while a confirmed Call action
 * is launching (via direct Android Dialer `intent://` or local Auto-Dial Webhook bridge).
 * Keeps the confirmed contact name (including emojis, e.g. "Manisha ❤️✨") and phone number
 * prominently visible in both the top ACTION // banner and the center calling card.
 */
@Composable
fun CallingLaunchOverlay(
    action: DeviceAction.DialPhone,
    usedAutoDialBridge: Boolean = false,
    onLaunchDialerNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayName = action.contactLabel?.takeIf { it.isNotBlank() }
        ?: action.phoneNumber.ifBlank { "Phone Dialer" }
    val displayNumber = action.phoneNumber.trim()
    val bannerText = action.displayBanner

    val pulseTransition = rememberInfiniteTransition(label = "calling_overlay_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Auto-dismiss gracefully after returning from the Phone app or if left open
    LaunchedEffect(action, usedAutoDialBridge) {
        delay(10_000L)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SwissInk)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("calling_launch_overlay"),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top ACTION // Banner with confirmed contact name & number
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSignalRed)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = SwissWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calling_overlay_top_banner")
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onDismiss)
                        .testTag("calling_overlay_close_top"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close calling overlay",
                        tint = SwissWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Center Full-Screen Calling Identity Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (usedAutoDialBridge) {
                        "HANDS-FREE // AUTO-DIAL WEBHOOK ACTIVE"
                    } else {
                        "PHONE // LAUNCHING ANDROID DIALER"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .scale(pulseScale)
                        .background(SwissSignalRed, RectangleShape)
                        .border(2.dp, SwissWhite, RectangleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Call,
                        contentDescription = "Calling $displayName",
                        tint = SwissWhite,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = SwissWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("calling_overlay_contact_name")
                    )
                    if (displayNumber.isNotBlank() && displayNumber != displayName) {
                        Text(
                            text = displayNumber,
                            style = MaterialTheme.typography.titleMedium,
                            color = SwissWhite.copy(alpha = 0.82f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("calling_overlay_phone_number")
                        )
                    }
                }

                Text(
                    text = if (usedAutoDialBridge) {
                        "Direct call command dispatched to your MacroDroid / Tasker Auto-Dial Webhook."
                    } else {
                        "Handing off directly to Android Phone Dialer (bypassing browser tel: preview)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissWhite.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Action Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(SwissSignalRed, RectangleShape)
                        .clickable(onClick = onLaunchDialerNow)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("calling_overlay_open_dialer_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = SwissWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "CALL ${displayName.uppercase()}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = SwissWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(SwissPaper, RectangleShape)
                        .border(1.dp, SwissWhite, RectangleShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("calling_overlay_dismiss_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DISMISS & RETURN TO ALLCHAT",
                        style = MaterialTheme.typography.labelLarge,
                        color = SwissInk
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "[🎧 1x TAP EARBUD: RE-DIAL]   [⏭ 2x TAP: DISMISS]",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
