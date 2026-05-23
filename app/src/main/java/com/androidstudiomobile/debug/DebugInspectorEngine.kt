package com.androidstudiomobile.debug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug Variable Inspector — alternativa ao debugger com breakpoints.
 *
 * Solução adotada:
 * - O usuário insere comentários especiais no formato `// debug: varName` no código Kotlin.
 * - Este engine escaneia o código, detecta esses marcadores e injeta chamadas
 *   android.util.Log.d() antes de compilar o APK.
 * - Os valores aparecem no Logcat integrado com a tag "ASM_DEBUG".
 * - O APK resultante tem os logs de debug injetados automaticamente,
 *   permitindo inspecionar variáveis sem um debugger real.
 *
 * Formato suportado:
 *   val x = 42 // debug: x
 *   val name = getName() // debug: name, userId, status
 */
object DebugInspectorEngine {

    private const val DEBUG_TAG = "ASM_DEBUG"
    private const val DEBUG_MARKER = "// debug:"

    data class DebugInjection(
        val filePath: String,
        val lineNumber: Int,
        val variables: List<String>,
        val originalLine: String,
        val injectedLine: String
    )

    data class InspectionResult(
        val modifiedFiles: List<String>,
        val injections: List<DebugInjection>,
        val totalLogs: Int
    )

    /**
     * Escaneia todos os arquivos .kt no projeto e injeta logs de debug.
     * Cria cópias dos arquivos modificados em um diretório temporário.
     */
    suspend fun injectDebugLogs(
        projectPath: String,
        outputDir: String
    ): InspectionResult = withContext(Dispatchers.IO) {
        val injections = mutableListOf<DebugInjection>()
        val modifiedFiles = mutableListOf<String>()

        val sourceDir = File(projectPath)
        val outDir = File(outputDir).also { it.mkdirs() }

        sourceDir.walkTopDown()
            .filter { it.extension == "kt" && !it.path.contains("build/") }
            .forEach { sourceFile ->
                val lines = sourceFile.readLines()
                var modified = false
                val newLines = mutableListOf<String>()

                lines.forEachIndexed { idx, line ->
                    newLines += line
                    val debugIdx = line.indexOf(DEBUG_MARKER)
                    if (debugIdx >= 0) {
                        val varsStr = line.substring(debugIdx + DEBUG_MARKER.length).trim()
                        val vars = varsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

                        if (vars.isNotEmpty()) {
                            val indent = "    ".repeat(line.length - line.trimStart().length).let {
                                if (it.isEmpty()) "    " else it
                            }
                            // Injeta um Log.d para cada variável declarada
                            vars.forEach { varName ->
                                val logLine = "${indent}android.util.Log.d(\"$DEBUG_TAG\", " +
                                    "\"[Line ${idx + 1}] $varName = \$$varName\")"
                                newLines += logLine
                            }
                            injections += DebugInjection(
                                filePath = sourceFile.absolutePath,
                                lineNumber = idx + 1,
                                variables = vars,
                                originalLine = line,
                                injectedLine = vars.joinToString(", ") { v ->
                                    "${indent}Log.d(\"$DEBUG_TAG\", \"$v = \$$v\")"
                                }
                            )
                            modified = true
                        }
                    }
                }

                if (modified) {
                    // Escreve o arquivo modificado mantendo a estrutura de diretórios
                    val relativePath = sourceFile.relativeTo(sourceDir).path
                    val outFile = File(outDir, relativePath).also { it.parentFile?.mkdirs() }
                    outFile.writeText(newLines.joinToString("\n"))
                    modifiedFiles += outFile.absolutePath
                }
            }

        InspectionResult(modifiedFiles, injections, injections.sumOf { it.variables.size })
    }

    /**
     * Remove todas as injeções de debug de um arquivo (limpa os logs injetados).
     */
    suspend fun removeDebugLogs(filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val lines = file.readLines()
        val cleaned = lines.filter { line ->
            !line.trim().startsWith("android.util.Log.d(\"$DEBUG_TAG\"")
        }
        val result = cleaned.joinToString("\n")
        file.writeText(result)
        result
    }

    /**
     * Analisa um arquivo e retorna todas as marcações de debug encontradas.
     */
    fun scanForDebugMarkers(content: String): List<DebugMarker> {
        val markers = mutableListOf<DebugMarker>()
        content.lines().forEachIndexed { idx, line ->
            val debugIdx = line.indexOf(DEBUG_MARKER)
            if (debugIdx >= 0) {
                val vars = line.substring(debugIdx + DEBUG_MARKER.length)
                    .trim().split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (vars.isNotEmpty()) {
                    markers += DebugMarker(idx + 1, vars, line.trim())
                }
            }
        }
        return markers
    }

    data class DebugMarker(
        val line: Int,
        val variables: List<String>,
        val sourceLine: String
    )

    /**
     * Gera um relatório de inspeção de variáveis baseado nos logs do Logcat.
     */
    fun parseDebugLogsFromLogcat(logcatOutput: String): List<DebugLogEntry> {
        val entries = mutableListOf<DebugLogEntry>()
        val regex = Regex("""D\s+$DEBUG_TAG\s*:\s*\[Line (\d+)\] (\w+) = (.+)""")
        logcatOutput.lines().forEach { line ->
            regex.find(line)?.let { match ->
                val (lineNum, varName, value) = match.destructured
                entries += DebugLogEntry(
                    lineNumber = lineNum.toIntOrNull() ?: 0,
                    variableName = varName,
                    value = value.trim(),
                    timestamp = extractTimestamp(line)
                )
            }
        }
        return entries
    }

    private fun extractTimestamp(logLine: String): String {
        return Regex("""(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)""")
            .find(logLine)?.groupValues?.get(1) ?: ""
    }

    data class DebugLogEntry(
        val lineNumber: Int,
        val variableName: String,
        val value: String,
        val timestamp: String
    )
}
