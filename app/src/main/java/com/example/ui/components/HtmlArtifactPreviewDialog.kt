package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSubtleFill

object ArtifactDetector {
    fun isPreviewable(language: String, code: String): Boolean {
        val lang = language.trim().lowercase()
        if (lang in setOf("html", "htm", "svg", "jsx", "tsx", "react", "canvas")) return true
        val head = code.trimStart().lowercase()
        if (head.startsWith("<!doctype html") || head.startsWith("<html") || head.startsWith("<svg")) {
            return true
        }
        if (lang in setOf("js", "javascript") &&
            (code.contains("getContext(") || code.contains("document.body") || code.contains("<canvas"))
        ) {
            return true
        }
        return false
    }

    fun wrapForSandbox(language: String, code: String): String {
        val trimmed = code.trim()
        val lower = trimmed.lowercase()
        val lang = language.trim().lowercase()
        val isSvg = lang == "svg" || lower.startsWith("<svg")
        if (isSvg) {
            return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
                "<style>html,body{margin:0;padding:14px;min-height:100vh;display:flex;align-items:center;justify-content:center;" +
                "background:#F4F4F2;box-sizing:border-box;}svg{max-width:100%;height:auto;}</style></head>" +
                "<body>$trimmed</body></html>"
        }
        if (lower.startsWith("<!doctype") || lower.startsWith("<html")) {
            return trimmed
        }
        if (lang in setOf("jsx", "tsx", "react")) {
            val cleanedReact = trimmed
                .replace(Regex("""^\s*import\s+.*?;\s*$""", RegexOption.MULTILINE), "")
                .replace(Regex("""export\s+default\s+"""), "window.__AllChatRootComponent = ")
            return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <style>
                    html,body{margin:0;padding:12px;font-family:system-ui,-apple-system,sans-serif;background:#FFFFFF;color:#111111;box-sizing:border-box;}
                    #err{color:#E60000;font-family:monospace;font-size:12px;white-space:pre-wrap;}
                  </style>
                  <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
                  <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
                  <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
                </head>
                <body>
                  <div id="root"></div>
                  <div id="err"></div>
                  <script type="text/babel">
                    try {
                      const { useState, useEffect, useMemo, useRef, useCallback } = React;
                      $cleanedReact
                      const Comp = window.__AllChatRootComponent || (typeof App !== 'undefined' ? App : null);
                      if (Comp) {
                        ReactDOM.createRoot(document.getElementById('root')).render(React.createElement(Comp));
                      } else {
                        document.getElementById('err').textContent = 'Define `export default function App()` or `function App()` to render preview.';
                      }
                    } catch (e) {
                      document.getElementById('err').textContent = 'Preview Error: ' + e.message;
                    }
                  </script>
                </body>
                </html>
            """.trimIndent()
        }
        if (lang == "canvas" || (lang in setOf("js", "javascript") && code.contains("getContext("))) {
            return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <style>html,body{margin:0;padding:8px;background:#F4F4F2;display:flex;justify-content:center;align-items:center;min-height:220px;}canvas{background:#FFFFFF;border:1px solid #111;max-width:100%;}</style>
                </head>
                <body>
                  <canvas id="canvas" width="340" height="220"></canvas>
                  <script>
                    try {
                      const canvas = document.getElementById('canvas');
                      const ctx = canvas.getContext('2d');
                      $trimmed
                    } catch (e) {
                      document.body.innerHTML = '<pre style="color:#E60000;font-size:12px;">' + e.message + '</pre>';
                    }
                  </script>
                </body>
                </html>
            """.trimIndent()
        }
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
            "<style>body{margin:14px;font-family:system-ui,-apple-system,sans-serif;background:#FFFFFF;color:#111111;}</style></head>" +
            "<body>$trimmed</body></html>"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InlineSandboxPreviewPanel(
    language: String,
    code: String,
    onExpandFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var reloadNonce by remember { mutableIntStateOf(0) }
    val htmlDocument = remember(language, code) { ArtifactDetector.wrapForSandbox(language, code) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .border(1.dp, SwissInk, RectangleShape)
            .testTag("inline_code_preview_panel")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissCanvas)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SANDBOX PREVIEW // ${language.ifBlank { "HTML" }.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = SwissInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "[ RELOAD ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissInk,
                    modifier = Modifier.clickable { reloadNonce++ }
                )
                Text(
                    text = "[ FULLSCREEN ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.example.ui.theme.SwissSignalRed,
                    modifier = Modifier.clickable(onClick = onExpandFullscreen)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(SwissPaper)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase()
                                return scheme != "about" && scheme != "data" && scheme != "javascript"
                            }
                        }
                        loadDataWithBaseURL(
                            "https://sandbox.local/",
                            htmlDocument,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { webView ->
                    val tagKey = "inline:$reloadNonce:${htmlDocument.hashCode()}"
                    if (webView.tag != tagKey) {
                        webView.tag = tagKey
                        webView.loadDataWithBaseURL(
                            "https://sandbox.local/",
                            htmlDocument,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlArtifactPreviewDialog(
    language: String,
    code: String,
    onDismiss: () -> Unit
) {
    var showSource by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    val htmlDocument = remember(language, code) { ArtifactDetector.wrapForSandbox(language, code) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .background(SwissPaper, RectangleShape)
                .border(1.dp, SwissInk, RectangleShape)
        ) {
            SwissPanelHeader(
                leftLabel = "SANDBOX // ${language.ifBlank { "HTML" }.uppercase()} ARTIFACT",
                rightLabel = "ISOLATED",
                rightAccent = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissCanvas)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwissToggleCell(
                    label = "PREVIEW",
                    selected = !showSource,
                    onClick = { showSource = false },
                    useSignalRedWhenSelected = true,
                    modifier = Modifier.testTag("artifact_preview_tab")
                )
                SwissToggleCell(
                    label = "SOURCE",
                    selected = showSource,
                    onClick = { showSource = true },
                    modifier = Modifier.testTag("artifact_source_tab")
                )
                Box(modifier = Modifier.weight(1f))
                if (!showSource) {
                    SwissIconCell(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Reload preview",
                        onClick = { reloadNonce++ },
                        cellSize = 40.dp,
                        modifier = Modifier.testTag("artifact_reload_button")
                    )
                }
                SwissIconCell(
                    icon = Icons.Default.Close,
                    contentDescription = "Close preview",
                    onClick = onDismiss,
                    inverted = true,
                    cellSize = 40.dp,
                    modifier = Modifier.testTag("artifact_close_button")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, SwissInk, RectangleShape)
            ) {
                if (showSource) {
                    Text(
                        text = code,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = SwissInk,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SwissSubtleFill)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                } else {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artifact_webview"),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val scheme = request?.url?.scheme?.lowercase()
                                        return scheme != "about" && scheme != "data" && scheme != "javascript"
                                    }
                                }
                                loadDataWithBaseURL(
                                    "https://sandbox.local/",
                                    htmlDocument,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        update = { webView ->
                            val tagKey = "$reloadNonce:${htmlDocument.hashCode()}"
                            if (webView.tag != tagKey) {
                                webView.tag = tagKey
                                webView.loadDataWithBaseURL(
                                    "https://sandbox.local/",
                                    htmlDocument,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
