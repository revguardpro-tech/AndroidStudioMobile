package com.androidstudiomobile.refactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// RefactorActions.kt
//
// Três funções principais:
//  1. renameSymbol  — busca e substitui com word-boundary em todo o projeto
//  2. extractMethod — recorta bloco selecionado e gera função Kotlin
//  3. optimizeImports — remove imports não utilizados e reordena por categoria
//
// Bônus:
//  4. moveFile      — move .kt e atualiza package + imports
//  5. convertToData — adiciona `data` modifier a classes simples
// ─────────────────────────────────────────────────────────────────────────────

object RefactorActions {

    // ── 1. Rename symbol ──────────────────────────────────────────────────────

    data class RenameResult(
        val filesChanged: Int,
        val occurrences: Int,
        val preview: String
    )

    /** Substitui [oldName] por [newName] em todos os arquivos de [projectDir]. */
    suspend fun renameSymbol(
        projectDir: String,
        oldName: String,
        newName: String,
        extensions: List<String> = listOf("kt","java","xml","kts")
    ): RenameResult = withContext(Dispatchers.IO) {
        val dir    = File(projectDir)
        val pattern = Regex("""\b${Regex.escape(oldName)}\b""")
        var files  = 0; var total = 0
        val preview = StringBuilder()

        dir.walkTopDown()
            .filter { it.isFile && it.extension in extensions }
            .forEach { f ->
                val src = f.readText()
                val n   = pattern.findAll(src).count()
                if (n > 0) {
                    f.writeText(pattern.replace(src, newName))
                    files++; total += n
                    preview.appendLine("${f.relativeTo(dir)}: $n ocorrência(s)")
                }
            }
        RenameResult(files, total, preview.toString().take(1000))
    }

    // ── 2. Extract method ─────────────────────────────────────────────────────

    data class ExtractResult(
        val updatedSource: String,
        val extractedFn: String,
        val callSite: String,
        val params: List<String>
    )

    /**
     * Recorta [source] de [startLine]..[endLine] (1-indexed) e gera:
     *  • função privada com os parâmetros detectados
     *  • código original substituído pelo call site
     */
    fun extractMethod(
        source: String,
        startLine: Int,
        endLine: Int,
        fnName: String,
        receiver: String = ""
    ): ExtractResult {
        val lines      = source.lines().toMutableList()
        val selected   = lines.subList(startLine - 1, endLine).joinToString("\n")
        val indent     = "    "

        // Detecta variáveis usadas mas não declaradas no bloco (= candidatos a parâmetros)
        val declared   = Regex("""(?:val|var)\s+(\w+)""").findAll(selected)
            .map { it.groupValues[1] }.toSet()
        val used       = Regex("""\b([a-z][a-zA-Z0-9_]*)\b""").findAll(selected)
            .map { it.groupValues[1] }.toSet()
        val params     = (used - declared - KOTLIN_KEYWORDS).take(4).toList()

        val paramList  = params.joinToString(", ") { "$it: Any" }
        val callArgs   = params.joinToString(", ")
        val rcv        = if (receiver.isNotEmpty()) "$receiver." else ""
        val callSite   = "$indent$rcv$fnName($callArgs)"

        val fn = buildString {
            appendLine()
            appendLine("${indent}private fun $rcv$fnName($paramList) {")
            selected.lines().forEach { appendLine("$indent    $it") }
            append("$indent}")
        }

        // Substitui o bloco selecionado pelo call site
        val before = lines.subList(0, startLine - 1).joinToString("\n")
        val after  = lines.subList(endLine, lines.size).joinToString("\n")
        val updated = "$before\n$callSite\n$after\n$fn"

        return ExtractResult(updated, fn.trim(), callSite.trim(), params)
    }

    // ── 3. Optimize imports ───────────────────────────────────────────────────

    data class OptimizeResult(
        val updatedSource: String,
        val removed: List<String>,
        val kept: Int
    )

    /** Remove imports não utilizados e reordena por categoria (android → androidx → kotlinx → kotlin → outros). */
    fun optimizeImports(fileContent: String): OptimizeResult {
        val lines     = fileContent.lines()
        val pkgLine   = lines.firstOrNull { it.startsWith("package ") } ?: ""
        val imports   = lines.filter { it.trimStart().startsWith("import ") }
        val codeLines = lines.filter { !it.startsWith("import ") && !it.startsWith("package ") }
        val code      = codeLines.joinToString("\n")

        val removed = mutableListOf<String>()
        val kept    = mutableListOf<String>()

        imports.forEach { imp ->
            val sym = imp.trim().removePrefix("import ").split(".").last()
            val used = sym == "*" || Regex("""\b${Regex.escape(sym)}\b""").containsMatchIn(code)
            if (used) kept.add(imp) else removed.add(imp)
        }

        // Ordena: android → androidx → kotlinx → kotlin → outros
        val sorted = kept.sortedWith(compareBy(
            {
                val p = it.trim().removePrefix("import ")
                when { p.startsWith("android.") -> 0; p.startsWith("androidx.") -> 1
                    p.startsWith("kotlinx.") -> 2;  p.startsWith("kotlin.") -> 3; else -> 4 }
            }, { it }
        ))

        val result = buildString {
            if (pkgLine.isNotEmpty()) { appendLine(pkgLine); appendLine() }
            sorted.forEach { appendLine(it) }
            if (sorted.isNotEmpty()) appendLine()
            append(codeLines.dropWhile { it.isBlank() }.joinToString("\n"))
        }

        return OptimizeResult(result, removed, sorted.size)
    }

    // ── 4. Move file ──────────────────────────────────────────────────────────

    suspend fun moveFile(
        file: File,
        newPackage: String,
        srcDir: File
    ): RenameResult = withContext(Dispatchers.IO) {
        val className  = file.nameWithoutExtension
        val oldPkg     = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
            .find(file.readText())?.groupValues?.get(1)
            ?: return@withContext RenameResult(0, 0, "Package não encontrado")

        // Move o arquivo
        val newDir = File(srcDir, newPackage.replace('.', '/')).also { it.mkdirs() }
        val newFile = File(newDir, file.name)
        newFile.writeText(file.readText().replace("package $oldPkg", "package $newPackage"))
        file.delete()

        // Atualiza imports em todos os outros arquivos
        var total = 0; var files = 0
        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" && it != newFile }
            .forEach { f ->
                val src = f.readText()
                val upd = src.replace("import $oldPkg.$className", "import $newPackage.$className")
                if (upd != src) { f.writeText(upd); files++; total++ }
            }
        RenameResult(files + 1, total, "Movido: $oldPkg.$className → $newPackage.$className")
    }

    // ── 5. Convert to data class ──────────────────────────────────────────────

    fun convertToData(source: String): String =
        source.replace(Regex("""(\n\s*)((?:open\s+|abstract\s+)?)class\s+(\w+)""")) { mr ->
            if (mr.groupValues[2].isNotEmpty()) mr.value  // já tem modifier especial
            else "${mr.groupValues[1]}data class ${mr.groupValues[3]}"
        }

    // ── constants ─────────────────────────────────────────────────────────────

    private val KOTLIN_KEYWORDS = setOf(
        "val","var","fun","class","object","interface","if","else","when","while","for",
        "return","true","false","null","this","super","override","private","public",
        "protected","internal","suspend","inline","reified","companion","data","sealed",
        "abstract","open","import","package","in","is","as","by","do","try","catch",
        "finally","throw","break","continue","it","field","get","set","lazy","init"
    )
}
