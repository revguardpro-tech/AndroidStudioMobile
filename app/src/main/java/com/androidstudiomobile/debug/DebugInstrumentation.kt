package com.androidstudiomobile.debug

import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// DebugInstrumentation.kt
// Percorre código-fonte .kt/.java do projeto e injeta Log.d em toda linha
// marcada com //debug. Suporta rollback limpo via uninstrument().
// ─────────────────────────────────────────────────────────────────────────────

object DebugInstrumentation {

    private const val MARKER   = "//debug"
    private const val TAG      = "ASM_DEBUG"
    private val IMPORT_LOG     = "import android.util.Log"

    data class InjectionReport(
        val filePath: String,
        val linesInjected: Int,
        val snippets: List<String>
    )

    /** Instrumenta todos .kt e .java dentro de [srcDir] e grava em [outDir]. */
    fun instrument(srcDir: File, outDir: File): List<InjectionReport> =
        srcDir.walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "java") }
            .map { src ->
                val rel  = src.relativeTo(srcDir)
                val dest = File(outDir, rel.path).also { it.parentFile?.mkdirs() }
                instrumentFile(src, dest)
            }
            .filter { it.linesInjected > 0 }
            .toList()

    /** Instrumenta um único arquivo e devolve relatório. */
    fun instrumentFile(src: File, dest: File = src): InjectionReport {
        val original = src.readLines().toMutableList()
        val snippets = mutableListOf<String>()
        var offset   = 0

        original.toList().forEachIndexed { i, line ->
            if (line.trimEnd().endsWith(MARKER)) {
                val indent   = line.indexOfFirst { !it.isWhitespace() }
                    .let { if (it < 0) "" else line.substring(0, it) }
                val varName  = extractVariable(line)
                val logLine  = buildLogLine(indent, varName)
                original.add(i + 1 + offset, logLine)
                snippets += logLine.trim()
                offset++
            }
        }

        // Insere import se necessário
        if (snippets.isNotEmpty() && original.none { it.trim() == IMPORT_LOG }) {
            val pkgLine = original.indexOfFirst { it.startsWith("package ") }
            original.add(if (pkgLine >= 0) pkgLine + 1 else 0, IMPORT_LOG)
        }

        dest.writeText(original.joinToString("\n"))
        return InjectionReport(src.path, snippets.size, snippets)
    }

    /** Remove TODOS os Log.d injetados por esta ferramenta (rollback seguro). */
    fun uninstrument(dir: File) {
        dir.walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "java") }
            .forEach { f ->
                val lines   = f.readLines()
                val cleaned = lines.filterNot { it.trimStart().startsWith("""Log.d("$TAG"""") }
                if (cleaned.size != lines.size) f.writeText(cleaned.joinToString("\n"))
            }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Extrai o nome da variável mais próxima na linha.
     * Ordem: val/var declaration → assignment → fallback literal.
     */
    private fun extractVariable(line: String): String {
        val clean = line.removeSuffix(MARKER).trimEnd()
        Regex("""(?:val|var)\s+(\w+)""").find(clean)?.let { return it.groupValues[1] }
        Regex("""(\w+)\s*=""").find(clean)?.let { return it.groupValues[1] }
        Regex("""\b([a-z]\w+)\b""").findAll(clean).lastOrNull()
            ?.let { return it.groupValues[1] }
        return """"(sem variável detectada)""""
    }

    private fun buildLogLine(indent: String, varName: String): String =
        if (varName.startsWith('"'))
            """${indent}Log.d("$TAG", $varName)"""
        else
            """${indent}Log.d("$TAG", "$varName = \$$varName")"""
}
