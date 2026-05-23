package com.androidstudiomobile.lsp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DefinitionResult(
    val filePath: String, val fileName: String,
    val line: Int, val column: Int,
    val snippet: String, val kind: DefinitionKind
)

enum class DefinitionKind { CLASS, DATA_CLASS, INTERFACE, OBJECT, ENUM, FUN, VARIABLE, UNKNOWN }

object GoToDefinitionProvider {

    suspend fun findDefinition(symbol: String, projectPath: String, currentFile: String = ""): List<DefinitionResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DefinitionResult>()
            val patterns = mapOf(
                DefinitionKind.DATA_CLASS to Regex("""data\s+class\s+${Regex.escape(symbol)}\b"""),
                DefinitionKind.CLASS      to Regex("""(?:sealed\s+|abstract\s+|open\s+)?class\s+${Regex.escape(symbol)}\b"""),
                DefinitionKind.INTERFACE  to Regex("""interface\s+${Regex.escape(symbol)}\b"""),
                DefinitionKind.OBJECT     to Regex("""object\s+${Regex.escape(symbol)}\b"""),
                DefinitionKind.ENUM       to Regex("""enum\s+class\s+${Regex.escape(symbol)}\b"""),
                DefinitionKind.FUN        to Regex("""(?:suspend\s+)?fun\s+${Regex.escape(symbol)}\s*[(<]"""),
                DefinitionKind.VARIABLE   to Regex("""(?:val|var)\s+${Regex.escape(symbol)}\s*[=:,]"""),
            )
            scan(projectPath) { file, lines ->
                lines.forEachIndexed { idx, line ->
                    patterns.forEach { (kind, pat) ->
                        if (pat.containsMatchIn(line)) results += DefinitionResult(
                            filePath = file.absolutePath, fileName = file.name,
                            line = idx + 1, column = (pat.find(line)?.range?.first ?: 0) + 1,
                            snippet = line.trim().take(100), kind = kind
                        )
                    }
                }
            }
            results.sortWith(compareByDescending<DefinitionResult> { it.kind != DefinitionKind.UNKNOWN }
                .thenBy { it.filePath == currentFile })
            results.distinctBy { "${it.filePath}:${it.line}" }.take(20)
        }

    suspend fun findUsages(symbol: String, projectPath: String): List<DefinitionResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DefinitionResult>()
            val re = Regex("""(?<![a-zA-Z0-9_])${Regex.escape(symbol)}(?![a-zA-Z0-9_])""")
            scan(projectPath) { file, lines ->
                lines.forEachIndexed { idx, line ->
                    if (re.containsMatchIn(line) && !line.trim().startsWith("//"))
                        results += DefinitionResult(file.absolutePath, file.name, idx + 1,
                            (re.find(line)?.range?.first ?: 0) + 1, line.trim().take(100), DefinitionKind.UNKNOWN)
                }
            }
            results.sortBy { it.filePath }
            results.take(200)
        }

    suspend fun getKDoc(symbol: String, projectPath: String): String? =
        withContext(Dispatchers.IO) {
            val re = Regex("""(/\*\*[\s\S]*?\*/)[\s\n\t]*(?:data\s+)?(?:class|fun|val|var|interface|object|enum)\s+${Regex.escape(symbol)}\b""")
            var doc: String? = null
            scan(projectPath) { file, _ ->
                if (doc != null) return@scan
                re.find(try { file.readText() } catch (_: Exception) { return@scan })?.let {
                    doc = it.groupValues[1].removePrefix("/**").removeSuffix("*/")
                        .lines().joinToString("\n") { l -> l.trim().removePrefix("*").trim() }.trim()
                }
            }
            doc
        }

    private fun scan(projectPath: String, action: (File, List<String>) -> Unit) {
        File(projectPath).walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt","java","xml") }
            .filter { !it.absolutePath.contains("/build/") && !it.absolutePath.contains("/.git/") }
            .take(300)
            .forEach { file -> try { action(file, file.readLines()) } catch (_: Exception) {} }
    }
}
