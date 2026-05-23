package com.androidstudiomobile.lsp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64

// ─────────────────────────────────────────────────────────────────────────────
// LanguageServerManager.kt
//
// Gerencia kotlin-language-server (KLS) via ProcessBuilder (stdio).
// Se KLS não estiver disponível (sem Termux), inicia servidor LSP embutido
// com índice de 60+ símbolos Android/Compose.
// Expõe WebSocket local (porta 7700) para o Monaco Editor.
// Script JS pronto para injeção em MonacoWebView incluído.
// ─────────────────────────────────────────────────────────────────────────────

class LanguageServerManager(private val ctx: Context) {

    companion object {
        private const val TAG    = "LSP"
        const val WS_PORT        = 7700
        private const val KLS    = "kotlin-language-server"
    }

    sealed class State {
        object Stopped  : State()
        object Starting : State()
        data class Running(val mode: String) : State()
        data class Error(val msg: String)    : State()
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var klsProc: Process? = null
    private var wsServer: LspWsServer? = null

    // ── lifecycle ─────────────────────────────────────────────────────────────

    fun start(projectRoot: String) {
        if (_state.value is State.Running) return
        _state.value = State.Starting
        scope.launch {
            if (klsAvailable()) launchKls(projectRoot) else launchEmbedded()
        }
    }

    fun stop() {
        klsProc?.destroy(); klsProc = null
        wsServer?.stop();   wsServer = null
        _state.value = State.Stopped
    }

    // ── KLS via ProcessBuilder ────────────────────────────────────────────────

    private fun klsAvailable(): Boolean = runCatching {
        Runtime.getRuntime().exec(arrayOf("which", KLS)).waitFor() == 0
    }.getOrDefault(false)

    private fun launchKls(root: String) {
        runCatching {
            val proc = ProcessBuilder(KLS, "--stdio")
                .directory(File(root))
                .redirectErrorStream(false)
                .start()
            klsProc = proc

            // Envia initialize via stdio
            sendLspRequest(proc.outputStream, buildInitialize())

            // Ponte stdio → WebSocket
            wsServer = LspWsServer(WS_PORT,
                klsIn  = proc.inputStream,
                klsOut = proc.outputStream,
                embedded = null
            ).also { it.start() }

            _state.value = State.Running("kotlin-language-server (Termux)")
            Log.i(TAG, "KLS started on port $WS_PORT")
        }.onFailure { e ->
            Log.e(TAG, "KLS failed: ${e.message}, falling back to embedded")
            launchEmbedded()
        }
    }

    // ── Servidor embutido ─────────────────────────────────────────────────────

    private fun launchEmbedded() {
        val handler = EmbeddedLspHandler(ctx)
        wsServer = LspWsServer(WS_PORT, null, null, handler).also { it.start() }
        _state.value = State.Running("Embedded LSP (${handler.completionCount} symbols)")
        Log.i(TAG, "Embedded LSP started on port $WS_PORT")
    }

    // ── Monaco injection script ───────────────────────────────────────────────

    fun monacoScript(): String = """
        (function(){
          if(window.__lspOk) return; window.__lspOk=true;
          const ws=new WebSocket('ws://localhost:$WS_PORT');
          let id=1; const cb={};
          ws.onopen=()=>{ send({jsonrpc:'2.0',id:id++,method:'initialize',
            params:{processId:null,rootUri:null,capabilities:{
              textDocument:{completion:{completionItem:{snippetSupport:true}},
              hover:{},publishDiagnostics:{}}}}}) };
          ws.onmessage=e=>{
            const m=JSON.parse(e.data);
            if(m.method==='textDocument/publishDiagnostics'){
              const marks=(m.params.diagnostics||[]).map(d=>({
                severity: d.severity===1?monaco.MarkerSeverity.Error:monaco.MarkerSeverity.Warning,
                message:d.message,
                startLineNumber:d.range.start.line+1, startColumn:d.range.start.character+1,
                endLineNumber:d.range.end.line+1,     endColumn:d.range.end.character+1
              }));
              const model=monaco.editor.getModels()[0];
              if(model) monaco.editor.setModelMarkers(model,'lsp',marks);
            }
            if(m.id&&cb[m.id]){cb[m.id](m.result);delete cb[m.id];}
          };
          function send(o){if(ws.readyState===1)ws.send(JSON.stringify(o));}
          function req(method,params){return new Promise(r=>{const i=id++;cb[i]=r;send({jsonrpc:'2.0',id:i,method,params});});}
          if(typeof monaco!=='undefined'){
            monaco.languages.registerCompletionItemProvider('kotlin',{
              triggerCharacters:['.','('],
              provideCompletionItems:async(model,pos)=>{
                const r=await req('textDocument/completion',{
                  textDocument:{uri:model.uri.toString()},
                  position:{line:pos.lineNumber-1,character:pos.column-1}});
                if(!r||!r.items)return{suggestions:[]};
                return{suggestions:r.items.map(i=>({label:i.label,kind:i.kind||1,
                  insertText:i.insertText||i.label,documentation:i.documentation}))};
              }
            });
            monaco.languages.registerHoverProvider('kotlin',{
              provideHover:async(model,pos)=>{
                const r=await req('textDocument/hover',{
                  textDocument:{uri:model.uri.toString()},
                  position:{line:pos.lineNumber-1,character:pos.column-1}});
                if(!r||!r.contents)return null;
                return{contents:[{value:typeof r.contents==='string'?r.contents:r.contents.value||''}]};
              }
            });
          }
        })();
    """.trimIndent()

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildInitialize(): JSONObject = JSONObject().apply {
        put("jsonrpc", "2.0"); put("id", 1); put("method", "initialize")
        put("params", JSONObject().apply {
            put("processId", JSONObject.NULL); put("rootUri", JSONObject.NULL)
            put("capabilities", JSONObject().apply {
                put("textDocument", JSONObject().apply {
                    put("completion", JSONObject().apply {
                        put("completionItem", JSONObject().apply { put("snippetSupport", true) })
                    })
                })
            })
        })
    }

    private fun sendLspRequest(out: OutputStream, obj: JSONObject) {
        val body    = obj.toString().toByteArray(Charsets.UTF_8)
        val header  = "Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
        out.write(header); out.write(body); out.flush()
    }
}

// ── WebSocket bridge ──────────────────────────────────────────────────────────

class LspWsServer(
    private val port: Int,
    private val klsIn: InputStream?,
    private val klsOut: OutputStream?,
    private val embedded: EmbeddedLspHandler?
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var srv: ServerSocket? = null

    fun start() { scope.launch {
        srv = ServerSocket(port)
        while (true) {
            val client = srv?.accept() ?: break
            launch { handleClient(client) }
        }
    }}

    fun stop() { srv?.close(); scope.cancel() }

    private suspend fun handleClient(sock: Socket) {
        val inp = sock.getInputStream()
        val out = sock.getOutputStream()
        // WebSocket handshake
        val hdrs = mutableMapOf<String,String>()
        BufferedReader(InputStreamReader(inp)).let { r ->
            var line = r.readLine()
            while (!line.isNullOrBlank()) {
                val p = line.split(":",limit=2)
                if (p.size==2) hdrs[p[0].trim()] = p[1].trim()
                line = r.readLine()
            }
        }
        val key    = hdrs["Sec-WebSocket-Key"] ?: return
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest("${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11".toByteArray()))
        PrintWriter(OutputStreamWriter(out)).run {
            print("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n")
            print("Connection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n")
            flush()
        }
        // After upgrade: handle frames
        scope.launch {
            val buf = ByteArray(65536)
            while (sock.isConnected) {
                val n = runCatching { inp.read(buf) }.getOrDefault(-1)
                if (n < 0) break
                val text = decodeFrame(buf, n) ?: continue
                val resp = embedded?.handle(text) ?: continue
                out.write(encodeFrame(resp)); out.flush()
            }
        }
    }

    private fun decodeFrame(buf: ByteArray, len: Int): String? {
        if (len < 6) return null
        return runCatching {
            val payLen = (buf[1].toInt() and 0x7F)
            val mask   = buf.slice(2..5).map { it }
            val data   = ByteArray(payLen) { i -> (buf[6+i].toInt() xor mask[i%4].toInt()).toByte() }
            String(data, Charsets.UTF_8)
        }.getOrNull()
    }

    private fun encodeFrame(text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x81.toByte(), payload.size.toByte()) + payload
    }
}

// ── Embedded LSP handler ──────────────────────────────────────────────────────

class EmbeddedLspHandler(ctx: Context) {

    private val symbols = buildSymbols()
    val completionCount get() = symbols.size

    fun handle(json: String): String? = runCatching {
        val req    = JSONObject(json)
        val method = req.optString("method")
        val id     = req.optInt("id", -1)
        when (method) {
            "initialize"          -> reply(id, capabilities())
            "textDocument/completion" -> reply(id, completions(req))
            "textDocument/hover"  -> reply(id, hover())
            "initialized", "textDocument/didOpen",
            "textDocument/didChange", "textDocument/didSave" -> null
            else -> if (id >= 0) reply(id, JSONObject()) else null
        }
    }.getOrNull()

    private fun reply(id: Int, result: JSONObject) =
        JSONObject().apply { put("jsonrpc","2.0"); put("id",id); put("result",result) }.toString()

    private fun capabilities() = JSONObject().apply {
        put("capabilities", JSONObject().apply {
            put("completionProvider", JSONObject().apply {
                put("triggerCharacters", JSONArray().apply { put("."); put("(") })
            })
            put("hoverProvider", true)
            put("textDocumentSync", 1)
        })
    }

    private fun completions(req: JSONObject): JSONObject {
        val uri    = req.optJSONObject("params")?.optJSONObject("textDocument")?.optString("uri","") ?: ""
        val isXml  = uri.endsWith(".xml")
        val items  = JSONArray()
        symbols.filter { if (isXml) it.second == "xml" else it.second != "xml" }
            .forEach { (name, _, doc) ->
                items.put(JSONObject().apply {
                    put("label", name); put("kind", 6)
                    put("insertText", name)
                    put("documentation", JSONObject().apply { put("kind","markdown"); put("value","**$name** — $doc") })
                })
            }
        return JSONObject().apply { put("items", items); put("isIncomplete", false) }
    }

    private fun hover() = JSONObject().apply {
        put("contents", JSONObject().apply { put("kind","markdown"); put("value","**Kotlin/Android SDK**") })
    }

    private fun buildSymbols(): List<Triple<String,String,String>> = listOf(
        // Compose
        Triple("Column","kt","Vertical layout"), Triple("Row","kt","Horizontal layout"),
        Triple("Box","kt","Overlay layout"), Triple("LazyColumn","kt","Scrollable vertical list"),
        Triple("LazyRow","kt","Scrollable horizontal list"), Triple("Scaffold","kt","Material scaffold"),
        Triple("Text","kt","Text composable"), Triple("Button","kt","Button composable"),
        Triple("OutlinedButton","kt","Outlined button"), Triple("TextButton","kt","Text button"),
        Triple("FloatingActionButton","kt","FAB"), Triple("IconButton","kt","Icon button"),
        Triple("TopAppBar","kt","Top app bar"), Triple("BottomAppBar","kt","Bottom app bar"),
        Triple("NavigationBar","kt","Nav bar"), Triple("Surface","kt","Material surface"),
        Triple("Card","kt","Material card"), Triple("ElevatedCard","kt","Elevated card"),
        Triple("OutlinedTextField","kt","Outlined text field"), Triple("TextField","kt","Text field"),
        Triple("Checkbox","kt","Checkbox"), Triple("Switch","kt","Toggle switch"),
        Triple("Slider","kt","Slider"), Triple("RadioButton","kt","Radio button"),
        Triple("CircularProgressIndicator","kt","Loading indicator"),
        Triple("LinearProgressIndicator","kt","Progress bar"),
        Triple("HorizontalDivider","kt","Divider"), Triple("Spacer","kt","Empty space"),
        Triple("Image","kt","Image composable"), Triple("Icon","kt","Icon composable"),
        Triple("AlertDialog","kt","Alert dialog"), Triple("ModalBottomSheet","kt","Bottom sheet"),
        Triple("DropdownMenu","kt","Dropdown"), Triple("Chip","kt","Chip composable"),
        Triple("FilterChip","kt","Filter chip"), Triple("AssistChip","kt","Assist chip"),
        // Modifier
        Triple("Modifier","kt","Compose modifier"), Triple("padding","kt","Padding modifier"),
        Triple("fillMaxSize","kt","Fill max size"), Triple("fillMaxWidth","kt","Fill max width"),
        Triple("fillMaxHeight","kt","Fill max height"), Triple("size","kt","Fixed size"),
        Triple("weight","kt","Flex weight"), Triple("background","kt","Background color"),
        Triple("clickable","kt","Click handler"), Triple("clip","kt","Clip shape"),
        Triple("border","kt","Border stroke"), Triple("offset","kt","Offset position"),
        Triple("alpha","kt","Opacity"), Triple("rotate","kt","Rotation"),
        Triple("scale","kt","Scale"), Triple("shadow","kt","Drop shadow"),
        // State / coroutines
        Triple("remember","kt","State holder"), Triple("mutableStateOf","kt","Mutable state"),
        Triple("LaunchedEffect","kt","Side effect"), Triple("collectAsState","kt","Flow collector"),
        Triple("rememberCoroutineScope","kt","Coroutine scope"),
        Triple("derivedStateOf","kt","Derived state"),
        // Android
        Triple("Log","kt","android.util.Log"), Triple("Toast","kt","Short message"),
        Triple("Intent","kt","Activity intent"), Triple("Bundle","kt","Key-value store"),
        Triple("ViewModel","kt","MVVM ViewModel"), Triple("LiveData","kt","Observable data"),
        Triple("StateFlow","kt","Coroutine flow"), Triple("Room","kt","SQLite ORM"),
        // XML attrs
        Triple("android:layout_width","xml","Layout width"), Triple("android:layout_height","xml","Layout height"),
        Triple("android:id","xml","View ID"), Triple("android:text","xml","Text content"),
        Triple("android:textSize","xml","Text size"), Triple("android:textColor","xml","Text color"),
        Triple("android:background","xml","Background"), Triple("android:padding","xml","Padding"),
        Triple("android:gravity","xml","Gravity"), Triple("android:visibility","xml","Visibility"),
        Triple("android:src","xml","Image source"), Triple("android:hint","xml","Hint text"),
        Triple("app:layout_constraintTop_toTopOf","xml","Constraint top"),
        Triple("app:layout_constraintBottom_toBottomOf","xml","Constraint bottom"),
        Triple("app:layout_constraintStart_toStartOf","xml","Constraint start"),
        Triple("app:layout_constraintEnd_toEndOf","xml","Constraint end")
    )
}
