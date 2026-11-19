package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.ModelInfo
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint

@Composable
fun VaultSecurityAndBackupPanel(
    displayCurrency: DisplayCurrency,
    isPinEnabled: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onSelectCurrency: (DisplayCurrency) -> Unit,
    onRequestSetPin: () -> Unit,
    onDisablePin: () -> Unit,
    onLockNow: () -> Unit,
    onRequestExportBackup: () -> Unit,
    onRequestImportBackup: () -> Unit,
    onDismissNotices: () -> Unit,
    modifier: Modifier = Modifier
) {
    SwissPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            SwissPanelHeader(
                leftLabel = "00 // VAULT SECURITY, CURRENCY & BACKUP",
                rightLabel = if (isPinEnabled) "PIN LOCKED" else "KEYSTORE ONLY",
                rightAccent = isPinEnabled
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Currency Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COST DISPLAY CURRENCY",
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissInk
                        )
                        Text(
                            text = "Shown beside token counts when model pricing is configured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DisplayCurrency.entries.forEach { cur ->
                            SwissToggleCell(
                                label = "${cur.code} ${cur.symbol}",
                                selected = displayCurrency == cur,
                                useSignalRedWhenSelected = true,
                                onClick = { onSelectCurrency(cur) },
                                modifier = Modifier.testTag("currency_${cur.code}")
                            )
                        }
                    }
                }

                SwissRuleDivider(symbol = "*")

                // PIN / Passphrase Encryption Lock
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PIN / MASTER PASSPHRASE VAULT LOCK",
                        style = MaterialTheme.typography.labelLarge,
                        color = SwissInk
                    )
                    Text(
                        text = if (isPinEnabled) {
                            "Active — API keys are encrypted at rest with PBKDF2-SHA256 + AES-256-GCM and decrypted only in memory after unlocking."
                        } else {
                            "Optional 4+ char PIN or passphrase on launch to encrypt API keys with a second PBKDF2 + AES-256-GCM layer."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissButton(
                            text = if (isPinEnabled) "Change PIN" else "Enable PIN Lock",
                            leadingIcon = Icons.Outlined.Key,
                            variant = if (isPinEnabled) SwissButtonVariant.OUTLINE else SwissButtonVariant.INK,
                            minHeight = 40.dp,
                            onClick = onRequestSetPin,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vault_set_pin_button")
                        )
                        if (isPinEnabled) {
                            SwissButton(
                                text = "Lock",
                                leadingIcon = Icons.Filled.Lock,
                                variant = SwissButtonVariant.PRIMARY,
                                minHeight = 40.dp,
                                onClick = onLockNow,
                                modifier = Modifier.testTag("vault_lock_now_button")
                            )
                            SwissButton(
                                text = "Disable",
                                variant = SwissButtonVariant.OUTLINE,
                                minHeight = 40.dp,
                                onClick = onDisablePin,
                                modifier = Modifier.testTag("vault_disable_pin_button")
                            )
                        }
                    }
                }

                SwissRuleDivider(symbol = "/")

                // Encrypted .allchat JSON Backup & Restore
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ENCRYPTED VAULT ARCHIVE (.ALLCHAT)",
                        style = MaterialTheme.typography.labelLarge,
                        color = SwissInk
                    )
                    Text(
                        text = "Export or restore all providers, API keys, models, pricing, personas, and branching chats in a password-protected AES-256-GCM JSON file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissButton(
                            text = "Backup Vault",
                            leadingIcon = Icons.Outlined.Upload,
                            variant = SwissButtonVariant.INK,
                            minHeight = 42.dp,
                            onClick = onRequestExportBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vault_backup_button")
                        )
                        SwissButton(
                            text = "Restore Vault",
                            leadingIcon = Icons.Outlined.Download,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 42.dp,
                            onClick = onRequestImportBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vault_restore_button")
                        )
                    }
                }

                AnimatedVisibility(visible = !statusMessage.isNullOrBlank()) {
                    statusMessage?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissVerifiedTint)
                                .border(1.dp, SwissVerifiedInk, RectangleShape)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissVerifiedInk
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                    errorMessage?.let { err ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSignalRedTint)
                                .border(1.dp, SwissSignalRed, RectangleShape)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordPromptDialog(
    eyebrow: String,
    title: String,
    description: String,
    confirmLabel: String,
    requireConfirmation: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    val mismatch = requireConfirmation && confirmPwd.isNotEmpty() && pwd != confirmPwd
    val canSubmit = pwd.trim().length >= 4 && (!requireConfirmation || pwd == confirmPwd)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        title = {
            Column {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                Text(title, style = MaterialTheme.typography.titleLarge, color = SwissInk)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = SwissMuted)
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("PIN / Passphrase (min 4 chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RectangleShape,
                    colors = swissTextFieldColors(SwissPaper),
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmPwd,
                        onValueChange = { confirmPwd = it },
                        label = { Text("Confirm PIN / Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RectangleShape,
                        colors = swissTextFieldColors(SwissPaper),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (mismatch) {
                    Text(
                        text = "ERR // PASSPHRASES DO NOT MATCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed
                    )
                }
            }
        },
        confirmButton = {
            SwissButton(
                text = confirmLabel,
                variant = SwissButtonVariant.PRIMARY,
                enabled = canSubmit,
                minHeight = 42.dp,
                onClick = { onConfirm(pwd.trim()) }
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
fun ModelPricingDialog(
    model: ModelInfo,
    onDismiss: () -> Unit,
    onSave: (inputPricePerMillion: Double, outputPricePerMillion: Double) -> Unit
) {
    var inputText by remember(model.id) {
        mutableStateOf(if (model.inputPricePerMillion > 0.0) model.inputPricePerMillion.toString() else "")
    }
    var outputText by remember(model.id) {
        mutableStateOf(if (model.outputPricePerMillion > 0.0) model.outputPricePerMillion.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        title = {
            Column {
                Text("COST // TOKEN PRICING", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                Text(model.displayName, style = MaterialTheme.typography.titleLarge, color = SwissInk)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter optional USD price per 1,000,000 tokens. AllChat will calculate exact per-message and session costs in USD or INR.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Input Price ($ USD / 1M tokens)") },
                    placeholder = { Text("e.g. 0.15") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RectangleShape,
                    colors = swissTextFieldColors(SwissPaper),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = outputText,
                    onValueChange = { outputText = it },
                    label = { Text("Output Price ($ USD / 1M tokens)") },
                    placeholder = { Text("e.g. 0.60") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RectangleShape,
                    colors = swissTextFieldColors(SwissPaper),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            SwissButton(
                text = "Save Pricing",
                variant = SwissButtonVariant.PRIMARY,
                minHeight = 42.dp,
                onClick = {
                    val inPrice = inputText.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val outPrice = outputText.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    onSave(inPrice, outPrice)
                }
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
