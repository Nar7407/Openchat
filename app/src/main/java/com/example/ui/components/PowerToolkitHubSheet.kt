package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

enum class PowerToolAction {
    QR_GENERATOR,
    VIDEO_FRAME,
    COLOR_PALETTE,
    PRIVACY_REDACTOR,
    CSV_EXPLORER,
    GRAPH_PLOTTER,
    DECISION_MATRIX,
    INTERACTIVE_CANVAS,
    EXPENSE_TRACKER,
    WATCHLIST,
    BLE_INSPECTOR,
    FACE_INTERPRETER,
    COMMUTE_HUD,
    SPOTLIGHT_SEARCH,
    CLIPBOARD_VAULT,
    LOCAL_WORKSPACE,
    VOICE_FORM_FILLER,
    CLI_COMMAND_BUILDER
}

private data class PowerToolTile(
    val action: PowerToolAction,
    val badge: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val highlight: Boolean = false
)

@Composable
fun PowerToolkitHubDialog(
    onDismiss: () -> Unit,
    onSelectTool: (PowerToolAction) -> Unit
) {
    val tiles = listOf(
        PowerToolTile(PowerToolAction.CLI_COMMAND_BUILDER, "/CMD", "CLI Command Builder", "FFmpeg/ADB/Git Flag Tooltips", Icons.Default.Terminal, true),
        PowerToolTile(PowerToolAction.VOICE_FORM_FILLER, "/FORM", "Voice Form Filler", "Dictate → Structured Table", Icons.Default.TableChart, true),
        PowerToolTile(PowerToolAction.INTERACTIVE_CANVAS, "EDITOR", "Canvas Studio", "Highlight-to-Edit & v1..v3", Icons.Default.EditNote, true),
        PowerToolTile(PowerToolAction.SPOTLIGHT_SEARCH, "SEARCH", "Spotlight & Map", "Search Chats, Notes, Docs", Icons.Default.Search, true),
        PowerToolTile(PowerToolAction.EXPENSE_TRACKER, "FINANCE", "Expense Tracker", "Receipt OCR + .xlsx Export", Icons.Default.AccountBalanceWallet),
        PowerToolTile(PowerToolAction.WATCHLIST, "RADAR", "Topic Watchlist", "Track URL & News Diffs", Icons.Default.Visibility),
        PowerToolTile(PowerToolAction.GRAPH_PLOTTER, "MATH", "Graph Plotter", "2D f(x) + a,b,c Sliders", Icons.Default.ShowChart),
        PowerToolTile(PowerToolAction.DECISION_MATRIX, "COMPARE", "Decision Matrix", "Weighted Pros/Cons Sliders", Icons.Default.Scale),
        PowerToolTile(PowerToolAction.CSV_EXPLORER, "DATA", "CSV Chart Studio", "Bar, Line, Scatter, Pie", Icons.Default.BarChart),
        PowerToolTile(PowerToolAction.LOCAL_WORKSPACE, "SAF", "Local Folder", "@file Context + Write-Back", Icons.Default.FolderOpen),
        PowerToolTile(PowerToolAction.FACE_INTERPRETER, "DUPLEX", "Live Interpreter", "180° Face-to-Face Split", Icons.Default.Translate, true),
        PowerToolTile(PowerToolAction.COMMUTE_HUD, "HANDS-FREE", "Commute HUD", "4-Button + Earbuds + Wake", Icons.Default.DirectionsCar),
        PowerToolTile(PowerToolAction.BLE_INSPECTOR, "HARDWARE", "BLE Inspector", "ESP32 / Arduino GATT Live", Icons.Default.BluetoothSearching),
        PowerToolTile(PowerToolAction.CLIPBOARD_VAULT, "VAULT", "Clipboard Merger", "Multi-Snippet Composer", Icons.Default.ContentPaste),
        PowerToolTile(PowerToolAction.QR_GENERATOR, "BEAM", "QR Drop Zone", "Animated Multi-QR Stream", Icons.Default.QrCode2),
        PowerToolTile(PowerToolAction.VIDEO_FRAME, "MEDIA", "Video Scrubber", "Extract Exact Keyframe", Icons.Default.VideoFile),
        PowerToolTile(PowerToolAction.COLOR_PALETTE, "DESIGN", "Palette & WCAG", "6 Swatches + Tailwind/CSS", Icons.Default.Palette),
        PowerToolTile(PowerToolAction.PRIVACY_REDACTOR, "PRIVACY", "PII Redactor", "Mask Phone/PAN/UPI Local", Icons.Default.Shield)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("power_toolkit_hub_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ALLCHAT // POWER TOOLKIT",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SwissWhite
                        )
                        Text(
                            text = "18 LOCAL & AI STUDIOS — CLI BUILDER, VOICE FORMS, QR STREAM, DATA & HARDWARE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissConcrete,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SwissIconCell(
                        icon = Icons.Default.Close,
                        contentDescription = "Close toolkit hub",
                        onClick = onDismiss,
                        inverted = true,
                        bordered = false,
                        cellSize = 48.dp,
                        modifier = Modifier.testTag("toolkit_hub_close_btn")
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tiles.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { tile ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 84.dp)
                                        .background(SwissPaper, RectangleShape)
                                        .border(
                                            width = if (tile.highlight) 1.5.dp else 1.dp,
                                            color = if (tile.highlight) SwissSignalRed else SwissInk,
                                            shape = RectangleShape
                                        )
                                        .clickable {
                                            onDismiss()
                                            onSelectTool(tile.action)
                                        }
                                        .padding(10.dp)
                                        .testTag("toolkit_tile_${tile.action.name.lowercase()}"),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tile.badge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (tile.highlight) SwissSignalRed else SwissMuted
                                        )
                                        Icon(
                                            imageVector = tile.icon,
                                            contentDescription = tile.title,
                                            tint = if (tile.highlight) SwissSignalRed else SwissInk,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = tile.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SwissInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tile.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
