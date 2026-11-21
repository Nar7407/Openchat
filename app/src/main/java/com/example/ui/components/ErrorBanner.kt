package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

@Composable
fun ErrorBanner(
    message: String,
    suggestProxySetting: Boolean = false,
    onOpenProxySettings: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Retry",
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SwissPaper,
        shape = RectangleShape,
        border = BorderStroke(1.dp, SwissInk),
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Stark Signal Red HUD header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSignalRed)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ERR // FAULT DETECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissWhite
                )
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss error",
                            tint = SwissWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissInk)
                    .padding(top = 1.dp)
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwissInk
                )
                if (suggestProxySetting) {
                    Text(
                        text = "NOTE // Endpoint may be blocked by origin/network rules. Configure a Proxy / Gateway URL in Provider Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                }
                if (onRetry != null || (suggestProxySetting && onOpenProxySettings != null)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onRetry != null) {
                            SwissButton(
                                text = retryLabel,
                                onClick = onRetry,
                                leadingIcon = Icons.Outlined.Refresh,
                                variant = SwissButtonVariant.PRIMARY,
                                minHeight = 40.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("error_retry_button")
                            )
                        }
                        if (suggestProxySetting && onOpenProxySettings != null) {
                            SwissButton(
                                text = "Configure Proxy / Base URL",
                                onClick = onOpenProxySettings,
                                leadingIcon = Icons.Outlined.Settings,
                                variant = SwissButtonVariant.OUTLINE,
                                minHeight = 40.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("error_open_proxy_settings")
                            )
                        }
                    }
                }
            }
        }
    }
}
