package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import kotlinx.coroutines.launch

@Composable
fun PinLockOverlay(
    onUnlockAttempt: suspend (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var unlocking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (pinInput.trim().length < 4 || unlocking) return
        scope.launch {
            unlocking = true
            errorMessage = null
            val ok = onUnlockAttempt(pinInput)
            unlocking = false
            if (!ok) {
                errorMessage = "INVALID PIN OR PASSPHRASE // ACCESS DENIED"
                pinInput = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwissCanvas)
            .swissGraphPaper()
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        SwissPanel(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .swissRegistrationMarks()
        ) {
            Column {
                SwissPanelHeader(
                    leftLabel = "00 // KEY VAULT LOCKED",
                    rightLabel = "AES-256-GCM",
                    rightAccent = true
                )
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(SwissInk)
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Vault locked",
                            tint = SwissWhite
                        )
                    }
                    Text(
                        text = "allchat vault",
                        style = MaterialTheme.typography.displayMedium,
                        color = SwissInk
                    )
                    Text(
                        text = "API keys are encrypted at rest with PBKDF2-HMAC-SHA256 + AES-256-GCM. Enter your PIN or master passphrase to decrypt keys into memory for this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwissMuted
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            errorMessage = null
                        },
                        label = {
                            Text(
                                "PIN / MASTER PASSPHRASE",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RectangleShape,
                        colors = swissTextFieldColors(SwissPaper),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_pin_input")
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSignalRed)
                                .border(1.dp, SwissInk, RectangleShape)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    SwissButton(
                        text = if (unlocking) "DECRYPTING..." else "UNLOCK VAULT",
                        onClick = { submit() },
                        variant = SwissButtonVariant.PRIMARY,
                        enabled = pinInput.trim().length >= 4 && !unlocking,
                        leadingIcon = Icons.Default.LockOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_unlock_button")
                    )
                }
            }
        }
    }
}
