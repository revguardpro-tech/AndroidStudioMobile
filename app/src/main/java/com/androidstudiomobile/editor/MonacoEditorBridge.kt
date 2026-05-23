package com.androidstudiomobile.editor

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.*
import com.androidstudiomobile.lint.LintEngine
import com.androidstudiomobile.lint.LintIssue
import com.androidstudiomobile.lint.LintSeverity
import com.androidstudiomobile.lsp.GoToDefinitionProvider
import com.androidstudiomobile.lsp.DefinitionResult
import com.androidstudiomobile.lsp.LspManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
class MonacoEditorBridge @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs) {

    interface EditorListener {
        fun onContentChanged(content: String)
        fun onSaveRequested(content: String)
        fun onLintIssues(issues: List<LintIssue>)
        fun onGoToDefinition(symbol: String)
        fun onFindUsages(symbol: String)
        fun onNavigateToFile(filePath: String, line: Int)
        fun onCursorMoved(line: Int, col: Int)
        fun onEditorReady()
        fun onHoverRequest(symbol: String): String?
        fun onGetDoc(symbol: String)
    }

    var listener: EditorListener? = null
    var currentLanguage: String = "kotlin"
    var projectPath: String = ""
    var currentFile: String = ""

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isReady = false
    private val pending = mutableListOf<String>()

    init {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
        }
        addJavascriptInterface(AndroidBridge(), "Android")
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                android.util.Log.d("Monaco[${m.messageLevel()}]", "${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                android.util.Log.e("Monaco", "Error ${err.description} on ${req.url}")
            }
        }
        loadUrl("file:///android_asset/monaco/index.html")
    }

    private fun exec(js: String) {
        if (isReady) evaluateJavascript("Editor.$js", null)
        else pending.add(js)
    }

    fun setContent(code: String, language: String = currentLanguage) {
        currentLanguage = language
        val safe = code.replace("\\", "\\\\").replace("`", "\\`").replace("\$", "\\\$")
        exec("setValue(`$safe`, `$language`)")
    }

    fun getContent(cb: (String) -> Unit) {
        evaluateJavascript("Editor.getValue()") { raw ->
            cb(raw?.removeSurrounding("\"")
                ?.replace("\\n", "\n")?.replace("\\t", "\t")
                ?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: "")
        }
    }

    fun setLanguage(lang: String) { currentLanguage = lang; exec("setLanguage(`$lang`)") }
    fun gotoLine(line: Int, col: Int = 1) = exec("gotoLine($line, $col)")
    fun format()     = exec("format()")
    fun undo()       = exec("undo()")
    fun redo()       = exec("redo()")
    fun foldAll()    = exec("foldAll()")
    fun unfoldAll()  = exec("unfoldAll()")
    fun setFontSize(sp: Int)       = exec("setFontSize($sp)")
    fun setWordWrap(on: Boolean)   = exec("setWordWrap($on)")
    fun setMinimap(on: Boolean)    = exec("setMinimap($on)")
    fun setTheme(theme: String)    = exec("setTheme(`$theme`)")
    fun connectKls(port: Int = LspManager.LSP_PORT) = exec("connectKls($port)")

    fun applyLintMarkers(issues: List<LintIssue>) {
        val arr = JSONArray().also { a ->
            issues.forEach { i -> a.put(JSONObject().apply {
                put("line", i.line); put("column", i.column)
                put("message", i.message); put("rule", i.rule)
                put("severity", i.severity.name)
            })}
        }
        val safe = arr.toString().replace("`", "\\`")
        evaluateJavascript("Editor.setMarkers(`$safe`)", null)
    }

    fun showDefinitions(results: List<DefinitionResult>) {
        val arr = JSONArray().also { a ->
            results.forEach { r -> a.put(JSONObject().apply {
                put("filePath", r.filePath); put("fileName", r.fileName)
                put("line", r.line); put("column", r.column)
                put("snippet", r.snippet); put("kind", r.kind.name)
            })}
        }
        val safe = arr.toString().replace("`", "\\`")
        evaluateJavascript("Editor.showDefinitions(`$safe`)", null)
    }

    private inner class AndroidBridge {
        @JavascriptInterface fun onEditorReady() {
            isReady = true
            post {
                pending.forEach { evaluateJavascript("Editor.$it", null) }
                pending.clear()
                listener?.onEditorReady()
                if (LspManager.isAvailable()) connectKls()
            }
        }

        @JavascriptInterface fun onContentChanged(content: String) =
            post { listener?.onContentChanged(content) }

        @JavascriptInterface fun onSave(content: String) =
            post { listener?.onSaveRequested(content) }

        @JavascriptInterface fun onLintRequest(content: String, lang: String) {
            scope.launch(Dispatchers.IO) {
                val issues = LintEngine.lint(content, lang)
                withContext(Dispatchers.Main) {
                    listener?.onLintIssues(issues)
                    applyLintMarkers(issues)
                }
            }
        }

        @JavascriptInterface fun onGoToDefinition(symbol: String) {
            scope.launch(Dispatchers.IO) {
                val results = GoToDefinitionProvider.findDefinition(symbol, projectPath, currentFile)
                withContext(Dispatchers.Main) {
                    if (results.isEmpty()) listener?.onGoToDefinition(symbol)
                    else showDefinitions(results)
                }
            }
        }

        @JavascriptInterface fun onFindUsages(symbol: String) =
            post { listener?.onFindUsages(symbol) }

        @JavascriptInterface fun onNavigateToFile(filePath: String, line: Int) =
            post { listener?.onNavigateToFile(filePath, line) }

        @JavascriptInterface fun onCursorMoved(line: Int, col: Int) =
            post { listener?.onCursorMoved(line, col) }

        @JavascriptInterface fun onHoverRequest(symbol: String): String =
            listener?.onHoverRequest(symbol) ?: ""

        @JavascriptInterface fun onGetDoc(symbol: String) =
            post { listener?.onGetDoc(symbol) }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); scope.cancel() }
}
