package com.example.ui.components

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

object CodeSandboxDetector {
    fun isRunnableCode(language: String): Boolean {
        val lang = language.trim().lowercase()
        return lang in setOf("python", "py", "javascript", "js")
    }

    fun isChartOrDiagram(language: String): Boolean {
        val lang = language.trim().lowercase()
        return lang in setOf("chartjs", "chart", "mermaid")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeInterpreterDialog(
    language: String,
    code: String,
    onDismiss: () -> Unit
) {
    val isPython = language.trim().lowercase() in setOf("python", "py")
    var runKey by remember { mutableIntStateOf(0) }
    val htmlDocument = remember(language, code, runKey) {
        if (isPython) buildPyodideRunnerHtml(code) else buildJsRunnerHtml(code)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SwissPaper,
            shape = RectangleShape,
            border = BorderStroke(1.dp, SwissInk),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPython) "SANDBOX // PYODIDE WASM PYTHON" else "SANDBOX // JS INTERPRETER",
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissWhite
                        )
                        Text(
                            text = if (isPython) "Executes locally in WebAssembly · Supports stdout & matplotlib PNG plots"
                            else "Executes locally in isolated WebView · Captures console & return value",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissCanvas
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .background(SwissSignalRed)
                                .clickable { runKey++ }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("rerun_code_sandbox")
                        ) {
                            Text("[ RE-RUN ]", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                        }
                        Box(
                            modifier = Modifier
                                .border(1.dp, SwissWhite, RectangleShape)
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("close_code_sandbox")
                        ) {
                            Text("[ CLOSE ]", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            loadDataWithBaseURL("https://allchat.sandbox/", htmlDocument, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        if (webView.tag != runKey) {
                            webView.tag = runKey
                            webView.loadDataWithBaseURL("https://allchat.sandbox/", htmlDocument, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChartDiagramDialog(
    language: String,
    code: String,
    onDismiss: () -> Unit
) {
    val isMermaid = language.trim().lowercase() == "mermaid"
    val htmlDocument = remember(language, code) {
        if (isMermaid) buildMermaidHtml(code) else buildChartJsHtml(code)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SwissPaper,
            shape = RectangleShape,
            border = BorderStroke(1.dp, SwissInk),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMermaid) "DIAGRAM // MERMAID.JS VIEWER" else "VISUAL // CHART.JS INTERACTIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissWhite
                        )
                        Text(
                            text = "Interactive client-side render · Pinch to zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissCanvas
                        )
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, SwissWhite, RectangleShape)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("close_chart_viewer")
                    ) {
                        Text("[ CLOSE ]", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            loadDataWithBaseURL("https://allchat.charts/", htmlDocument, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        }
    }
}

private fun encodeUtf8Base64(text: String): String =
    Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

private fun buildJsRunnerHtml(code: String): String {
    val codeB64 = encodeUtf8Base64(code)
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<style>
  body { margin: 0; padding: 14px; background: #EFEFEF; color: #111111; font-family: monospace; font-size: 13px; }
  .badge { display: inline-block; background: #111111; color: #FFFFFF; padding: 4px 8px; font-weight: bold; letter-spacing: 0.08em; margin-bottom: 10px; }
  .err-badge { background: #FF3B30; }
  pre { background: #FFFFFF; border: 1px solid #111111; padding: 12px; white-space: pre-wrap; word-break: break-word; margin: 0 0 12px 0; }
  .err { border-color: #FF3B30; color: #B91C1C; background: #FFF5F5; }
</style>
</head>
<body>
  <div id="status" class="badge">EXEC // RUNNING JAVASCRIPT…</div>
  <pre id="out"></pre>
  <pre id="err" class="err" style="display:none;"></pre>
<script>
  const outEl = document.getElementById('out');
  const errEl = document.getElementById('err');
  const statusEl = document.getElementById('status');
  const logs = [];
  const fmt = (v) => {
    if (typeof v === 'string') return v;
    try { return JSON.stringify(v, null, 2); } catch (e) { return String(v); }
  };
  console.log = (...args) => { logs.push(args.map(fmt).join(' ')); outEl.textContent = logs.join('\n'); };
  console.warn = (...args) => { logs.push('[WARN] ' + args.map(fmt).join(' ')); outEl.textContent = logs.join('\n'); };
  console.error = (...args) => { errEl.style.display = 'block'; errEl.textContent += args.map(fmt).join(' ') + '\n'; };
  (async () => {
    try {
      const bytes = Uint8Array.from(atob("$codeB64"), c => c.charCodeAt(0));
      const src = new TextDecoder('utf-8').decode(bytes);
      const t0 = performance.now();
      const fn = new Function('return (async () => {\n' + src + '\n})();');
      const result = await fn();
      const elapsed = Math.round(performance.now() - t0);
      if (result !== undefined) {
        logs.push('=> ' + fmt(result));
      }
      outEl.textContent = logs.length ? logs.join('\n') : '(Completed with no console output)';
      statusEl.textContent = 'DONE // EXECUTED IN ' + elapsed + 'MS';
    } catch (e) {
      statusEl.textContent = 'ERR // EXECUTION FAILED';
      statusEl.classList.add('err-badge');
      errEl.style.display = 'block';
      errEl.textContent = (e && e.stack) ? e.stack : String(e);
    }
  })();
</script>
</body>
</html>
    """.trimIndent()
}

private fun buildPyodideRunnerHtml(code: String): String {
    val codeB64 = encodeUtf8Base64(code)
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<style>
  body { margin: 0; padding: 14px; background: #EFEFEF; color: #111111; font-family: monospace; font-size: 13px; }
  .badge { display: inline-block; background: #111111; color: #FFFFFF; padding: 4px 8px; font-weight: bold; letter-spacing: 0.08em; margin-bottom: 10px; }
  .err-badge { background: #FF3B30; }
  pre { background: #FFFFFF; border: 1px solid #111111; padding: 12px; white-space: pre-wrap; word-break: break-word; margin: 0 0 12px 0; }
  .err { border-color: #FF3B30; color: #B91C1C; background: #FFF5F5; }
  img.plot { width: 100%; max-width: 100%; border: 1px solid #111111; background: #FFFFFF; margin-top: 8px; }
</style>
<script src="https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js"></script>
</head>
<body>
  <div id="status" class="badge">BOOT // LOADING PYODIDE WASM RUNTIME…</div>
  <pre id="out">Initializing WebAssembly Python…</pre>
  <pre id="err" class="err" style="display:none;"></pre>
  <div id="plots"></div>
<script>
  const outEl = document.getElementById('out');
  const errEl = document.getElementById('err');
  const statusEl = document.getElementById('status');
  const plotsEl = document.getElementById('plots');
  (async () => {
    try {
      const bytes = Uint8Array.from(atob("$codeB64"), c => c.charCodeAt(0));
      const userCode = new TextDecoder('utf-8').decode(bytes);
      const pyodide = await loadPyodide();
      statusEl.textContent = 'PKG // LOADING IMPORTS…';
      await pyodide.loadPackagesFromImports(userCode);
      if (userCode.includes('matplotlib')) {
        await pyodide.loadPackage('matplotlib');
      }
      statusEl.textContent = 'EXEC // RUNNING PYTHON…';
      const t0 = performance.now();
      let stdoutBuf = '';
      pyodide.setStdout({ batched: (s) => { stdoutBuf += s + '\n'; outEl.textContent = stdoutBuf; } });
      pyodide.setStderr({ batched: (s) => { errEl.style.display = 'block'; errEl.textContent += s + '\n'; } });
      const preamble = `
import sys
__allchat_plots = []
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import io, base64
    def __allchat_show(*args, **kwargs):
        buf = io.BytesIO()
        plt.savefig(buf, format='png', dpi=150, bbox_inches='tight')
        buf.seek(0)
        __allchat_plots.append(base64.b64encode(buf.read()).decode('ascii'))
        plt.close('all')
    plt.show = __allchat_show
except Exception:
    pass
`;
      await pyodide.runPythonAsync(preamble);
      const res = await pyodide.runPythonAsync(userCode);
      const elapsed = Math.round(performance.now() - t0);
      if (res !== undefined && res !== null) {
        stdoutBuf += '=> ' + String(res) + '\n';
      }
      outEl.textContent = stdoutBuf.trim() || '(Completed with no console output)';
      const plots = pyodide.globals.get('__allchat_plots').toJs();
      if (plots && plots.length) {
        plots.forEach((b64) => {
          const img = document.createElement('img');
          img.className = 'plot';
          img.src = 'data:image/png;base64,' + b64;
          plotsEl.appendChild(img);
        });
      }
      statusEl.textContent = 'DONE // PYTHON EXECUTED IN ' + elapsed + 'MS';
    } catch (e) {
      statusEl.textContent = 'ERR // PYTHON EXCEPTION';
      statusEl.classList.add('err-badge');
      errEl.style.display = 'block';
      errEl.textContent = String(e);
    }
  })();
</script>
</body>
</html>
    """.trimIndent()
}

private fun buildChartJsHtml(jsonCode: String): String {
    val b64 = encodeUtf8Base64(jsonCode)
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js"></script>
<style>
  body { margin: 0; padding: 14px; background: #EFEFEF; color: #111111; font-family: monospace; }
  .card { background: #FFFFFF; border: 1px solid #111111; padding: 14px; }
  h3 { margin: 0 0 12px 0; font-size: 14px; text-transform: uppercase; letter-spacing: 0.06em; }
  canvas { width: 100% !important; max-height: 440px; }
  pre { color: #FF3B30; white-space: pre-wrap; font-size: 12px; }
</style>
</head>
<body>
  <div class="card">
    <h3 id="title">CHART</h3>
    <canvas id="chartCanvas"></canvas>
    <pre id="err"></pre>
  </div>
<script>
  const palette = ['#FF3B30', '#111111', '#0057FF', '#555555', '#D97706', '#059669'];
  try {
    const bytes = Uint8Array.from(atob("$b64"), c => c.charCodeAt(0));
    const spec = JSON.parse(new TextDecoder('utf-8').decode(bytes));
    if (spec.title) document.getElementById('title').textContent = spec.title;
    const data = spec.data || { labels: spec.labels || [], datasets: spec.datasets || [] };
    (data.datasets || []).forEach((ds, idx) => {
      const col = palette[idx % palette.length];
      if (!ds.backgroundColor) ds.backgroundColor = (spec.type === 'pie' || spec.type === 'doughnut') ? palette : col;
      if (!ds.borderColor) ds.borderColor = '#111111';
      if (ds.borderWidth === undefined) ds.borderWidth = 1.5;
    });
    new Chart(document.getElementById('chartCanvas').getContext('2d'), {
      type: spec.type || 'bar',
      data: data,
      options: Object.assign({
        responsive: true,
        plugins: { legend: { position: 'bottom' } }
      }, spec.options || {})
    });
  } catch (e) {
    document.getElementById('err').textContent = 'Chart parse error: ' + e;
  }
</script>
</body>
</html>
    """.trimIndent()
}

private fun buildMermaidHtml(mermaidCode: String): String {
    val b64 = encodeUtf8Base64(mermaidCode)
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<style>
  body { margin: 0; padding: 14px; background: #EFEFEF; color: #111111; font-family: monospace; }
  .stage { background: #FFFFFF; border: 1px solid #111111; padding: 16px; overflow: auto; }
  pre { color: #FF3B30; white-space: pre-wrap; font-size: 12px; }
</style>
</head>
<body>
  <div class="stage">
    <div id="diagram" class="mermaid"></div>
    <pre id="err"></pre>
  </div>
<script type="module">
  import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
  try {
    const bytes = Uint8Array.from(atob("$b64"), c => c.charCodeAt(0));
    const code = new TextDecoder('utf-8').decode(bytes);
    mermaid.initialize({ startOnLoad: false, theme: 'neutral', securityLevel: 'strict' });
    const { svg } = await mermaid.render('mmdSvg', code);
    document.getElementById('diagram').innerHTML = svg;
  } catch (e) {
    document.getElementById('err').textContent = 'Mermaid render error: ' + e;
  }
</script>
</body>
</html>
    """.trimIndent()
}
