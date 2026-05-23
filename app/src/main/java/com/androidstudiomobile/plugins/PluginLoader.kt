package com.androidstudiomobile.plugins

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// PluginLoader.kt
//
// Carrega scripts .kts do diretório filesDir/plugins/.
// Execução:
//  1. Se `kotlinc` disponível (Termux) → ProcessBuilder -script
//  2. Caso contrário → interpretador DSL embutido
//
// API exposta aos plugins:
//   editor.openFile(path)         — abre arquivo no editor
//   editor.insertText(text)       — insere texto na posição do cursor
//   editor.currentFilePath()      — caminho do arquivo aberto
//   editor.readFile(path)         — lê arquivo como String
//   editor.writeFile(path, text)  — escreve arquivo
//   editor.listFiles(dir)         — lista arquivos de um diretório
//   editor.runBuild(variant)      — dispara build (debug|release)
//   editor.showNotification(msg)  — exibe Snackbar/Toast
//   editor.log(msg)               — Log.d no Logcat (tag: PLUGIN)
// ─────────────────────────────────────────────────────────────────────────────

class PluginLoader(private val ctx: Context, private val api: EditorApi) {

    // ── API interface ─────────────────────────────────────────────────────────

    interface EditorApi {
        fun openFile(path: String)
        fun insertText(text: String)
        fun currentFilePath(): String
        fun readFile(path: String): String
        fun writeFile(path: String, content: String)
        fun listFiles(dir: String): List<String>
        fun runBuild(variant: String = "debug")
        fun showNotification(msg: String)
        fun log(msg: String)
    }

    // ── modelo ────────────────────────────────────────────────────────────────

    data class Plugin(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val path: String,
        val enabled: Boolean = true
    )

    sealed class RunResult {
        object Running                              : RunResult()
        data class Success(val output: String)      : RunResult()
        data class Error(val msg: String, val line: Int = -1) : RunResult()
    }

    // ── state ─────────────────────────────────────────────────────────────────

    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins: StateFlow<List<Plugin>> = _plugins

    private val _results = MutableStateFlow<Map<String, RunResult>>(emptyMap())
    val results: StateFlow<Map<String, RunResult>> = _results

    private val pluginDir = File(ctx.filesDir, "plugins").also { it.mkdirs() }
    private val scope     = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── discovery ─────────────────────────────────────────────────────────────

    fun refresh() {
        _plugins.value = pluginDir.listFiles { f -> f.extension == "kts" }
            ?.map { f -> pluginFromFile(f) } ?: emptyList()
    }

    fun install(src: File): Plugin? {
        if (!src.exists() || src.extension != "kts") return null
        src.copyTo(File(pluginDir, src.name), overwrite = true)
        refresh()
        return _plugins.value.find { it.id == src.nameWithoutExtension }
    }

    /** Instala um plugin de exemplo pelo nome ("header", "stats", "dedup"). */
    fun installSample(name: String) {
        val content = when (name) {
            "header" -> SAMPLE_HEADER
            "stats"  -> SAMPLE_STATS
            "dedup"  -> SAMPLE_DEDUP
            else     -> return
        }
        File(pluginDir, "$name.kts").writeText(content)
        refresh()
    }

    // ── execution ─────────────────────────────────────────────────────────────

    fun run(plugin: Plugin) {
        mark(plugin.id, RunResult.Running)
        scope.launch {
            val result = if (kotlincAvailable()) runWithKotlinc(plugin) else interpret(plugin)
            mark(plugin.id, result)
        }
    }

    private fun mark(id: String, r: RunResult) {
        _results.value = _results.value + (id to r)
    }

    // ── kotlinc via ProcessBuilder ────────────────────────────────────────────

    private fun kotlincAvailable() = runCatching {
        Runtime.getRuntime().exec(arrayOf("which","kotlinc")).waitFor() == 0
    }.getOrDefault(false)

    private suspend fun runWithKotlinc(plugin: Plugin): RunResult =
        withTimeoutOrNull(30_000L) {
            runCatching {
                val proc = ProcessBuilder("kotlinc", "-script", plugin.path)
                    .redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                if (proc.waitFor() == 0) RunResult.Success(out.take(2000))
                else RunResult.Error(out.take(800))
            }.getOrElse { RunResult.Error(it.message ?: "Erro ao executar kotlinc") }
        } ?: RunResult.Error("Timeout: plugin demorou mais de 30s")

    // ── DSL interpreter ───────────────────────────────────────────────────────

    private fun interpret(plugin: Plugin): RunResult {
        val out = StringBuilder()
        return runCatching {
            File(plugin.path).readLines().forEachIndexed { lineIdx, rawLine ->
                val t = rawLine.trim()
                when {
                    t.startsWith("//") || t.startsWith("/*") || t.isBlank() -> Unit
                    t.startsWith("editor.openFile(") -> {
                        val p = strArg(t); api.openFile(p); out.appendLine("openFile($p)")
                    }
                    t.startsWith("editor.insertText(") -> {
                        val s = strArg(t); api.insertText(s); out.appendLine("insertText(…)")
                    }
                    t.startsWith("editor.writeFile(") -> {
                        val (p, c) = twoStrArgs(t); api.writeFile(p, c); out.appendLine("writeFile($p)")
                    }
                    t.startsWith("editor.showNotification(") -> {
                        val m = strArg(t); api.showNotification(m); out.appendLine("notify: $m")
                    }
                    t.startsWith("editor.log(") -> {
                        val m = strArg(t); api.log(m); out.appendLine("[LOG] $m")
                    }
                    t.startsWith("editor.runBuild(") -> {
                        val v = strArg(t).ifEmpty { "debug" }; api.runBuild(v); out.appendLine("runBuild($v)")
                    }
                    t.startsWith("println(") -> {
                        out.appendLine(strArg(t))
                    }
                    // val/var declarations — skip in interpreter
                    t.startsWith("val ") || t.startsWith("var ") -> Unit
                }
            }
            RunResult.Success(out.toString().ifEmpty { "Plugin executado (sem output)" })
        }.getOrElse { RunResult.Error(it.message ?: "Erro no interpretador", -1) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun pluginFromFile(f: File): Plugin {
        val meta = mutableMapOf<String,String>()
        f.bufferedReader().useLines { seq ->
            seq.take(8).forEach { line ->
                val t = line.trim()
                if (t.startsWith("// @")) {
                    val kv = t.removePrefix("// @").split(":", limit = 2)
                    if (kv.size == 2) meta[kv[0].trim()] = kv[1].trim()
                }
            }
        }
        return Plugin(
            id          = f.nameWithoutExtension,
            name        = meta["name"]        ?: f.nameWithoutExtension,
            description = meta["description"] ?: "",
            version     = meta["version"]     ?: "1.0",
            path        = f.absolutePath
        )
    }

    private fun strArg(call: String)   = Regex(""""([^"]*)"""").find(call)?.groupValues?.get(1) ?: ""
    private fun twoStrArgs(call: String): Pair<String,String> {
        val m = Regex(""""([^"]*)"""").findAll(call).map { it.groupValues[1] }.toList()
        return (m.getOrElse(0){""}) to (m.getOrElse(1){""})
    }

    // ── sample plugins ────────────────────────────────────────────────────────

    private val SAMPLE_HEADER = """
        // @name: Header Inserter
        // @description: Insere cabeçalho de copyright no arquivo atual
        // @version: 1.0
        val header = "// Copyright 2025 — Android Studio Mobile\n\n"
        val path = editor.currentFilePath()
        val content = editor.readFile(path)
        editor.writeFile(path, header + content)
        editor.showNotification("Header inserido!")
    """.trimIndent()

    private val SAMPLE_STATS = """
        // @name: File Stats
        // @description: Exibe linhas, palavras e caracteres do arquivo atual
        // @version: 1.0
        val path = editor.currentFilePath()
        val text = editor.readFile(path)
        val lines = text.split("\n").size
        val words = text.split(Regex("\\s+")).size
        editor.showNotification("${'$'}lines linhas · ${'$'}words palavras · ${'$'}{text.length} chars")
        editor.log("Stats: ${'$'}lines/${'$'}words/${'$'}{text.length}")
    """.trimIndent()

    private val SAMPLE_DEDUP = """
        // @name: Dedup Imports
        // @description: Remove imports duplicados no arquivo atual
        // @version: 1.0
        val path = editor.currentFilePath()
        val lines = editor.readFile(path).split("\n")
        val seen = linkedSetOf<String>()
        val result = lines.filter { line ->
            if (line.startsWith("import ")) seen.add(line)
            else true
        } + seen.toList()
        editor.writeFile(path, result.joinToString("\n"))
        editor.showNotification("Imports deduplicados!")
    """.trimIndent()
}
