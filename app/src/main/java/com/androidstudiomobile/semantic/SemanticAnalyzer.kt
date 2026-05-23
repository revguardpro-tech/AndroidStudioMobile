package com.androidstudiomobile.semantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ClassInfo(val name: String, val packageName: String, val superClass: String?,
    val interfaces: List<String>, val methods: List<MethodInfo>, val properties: List<PropInfo>,
    val imports: List<String>, val startLine: Int, val endLine: Int)

data class MethodInfo(val name: String, val returnType: String, val parameters: List<ParamInfo>,
    val modifiers: List<String>, val body: String, val startLine: Int, val endLine: Int)

data class PropInfo(val name: String, val type: String, val isVal: Boolean,
    val modifiers: List<String>, val initializer: String?, val line: Int)

data class ParamInfo(val name: String, val type: String, val default: String? = null)

data class RefactorPreview(val description: String, val originalCode: String,
    val refactoredCode: String, val affectedFiles: List<String>, val safe: Boolean = true)

class SemanticAnalyzer {

    suspend fun analyze(content: String, fileName: String): ClassInfo? =
        withContext(Dispatchers.Default) { runCatching { parse(content) }.getOrNull() }

    private fun parse(src: String): ClassInfo? {
        val lines = src.lines()
        var pkg = ""; val imports = mutableListOf<String>(); var className = ""; var superClass: String? = null
        val interfaces = mutableListOf<String>(); val methods = mutableListOf<MethodInfo>()
        val props = mutableListOf<PropInfo>(); var classStart = 0

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("package ") -> pkg = line.removePrefix("package ").trim()
                line.startsWith("import ")  -> imports.add(line.removePrefix("import ").trim())
                className.isEmpty() && Regex("(class|object|interface)\\s+\\w+").containsMatchIn(line) -> {
                    classStart = i; className = Regex("(?:class|object|interface)\\s+(\\w+)").find(line)?.groupValues?.get(1) ?: ""
                    val colonPart = line.substringAfter(":", "").trim()
                    val types = colonPart.split(",").map { it.trim().substringBefore("(").substringBefore("<") }.filter { it.isNotBlank() }
                    if (types.isNotEmpty()) { superClass = types[0]; interfaces.addAll(types.drop(1)) }
                }
                className.isNotEmpty() && Regex("(?:override|private|protected|internal|open|suspend|inline|\\s)*fun\\s+\\w+").containsMatchIn(line) -> {
                    parseMethod(lines, i)?.let { methods.add(it); i = it.endLine }
                }
                className.isNotEmpty() && Regex("(?:val|var)\\s+\\w+").containsMatchIn(line) -> {
                    parseProp(line, i)?.let { props.add(it) }
                }
            }
            i++
        }
        if (className.isEmpty()) return null
        return ClassInfo(className, pkg, superClass, interfaces, methods, props, imports, classStart, lines.size - 1)
    }

    private fun parseMethod(lines: List<String>, start: Int): MethodInfo? {
        val line = lines[start].trim()
        val rx = Regex("""fun\s+(\w+)\s*\(([^)]*)\)(?:\s*:\s*(\S+))?""")
        val m = rx.find(line) ?: return null
        val name = m.groupValues[1]; val paramsStr = m.groupValues[2]; val ret = m.groupValues[3].ifEmpty { "Unit" }
        val mods = listOf("private","protected","internal","open","override","abstract","suspend","inline","operator").filter { line.contains("\\b$it\\b".toRegex()) }
        var braces = 0; var end = start
        for (j in start until lines.size) { braces += lines[j].count { it == '{' } - lines[j].count { it == '}' }; if (j > start && braces <= 0) { end = j; break } }
        val body = lines.subList(start, minOf(end + 1, lines.size)).joinToString("\n")
        val params = if (paramsStr.isBlank()) emptyList() else paramsStr.split(",").mapNotNull { p ->
            val parts = p.trim().split(":"); if (parts.size < 2) return@mapNotNull null
            val nd = parts[1].split("="); ParamInfo(parts[0].trim().removePrefix("val ").removePrefix("var "), nd[0].trim(), nd.getOrNull(1)?.trim())
        }
        return MethodInfo(name, ret, params, mods, body, start, end)
    }

    private fun parseProp(line: String, lineNum: Int): PropInfo? {
        val rx = Regex("""(?:(?:private|protected|internal|override|open|lateinit)\s+)*(val|var)\s+(\w+)(?:\s*:\s*([^=]+?))?(?:\s*=\s*(.+))?$""")
        val m = rx.find(line.trim()) ?: return null
        return PropInfo(m.groupValues[2], m.groupValues[3].trim().ifEmpty { "Any" }, m.groupValues[1] == "val",
            listOf("private","protected","internal","open","override","lateinit").filter { line.contains("\\b$it\\b".toRegex()) },
            m.groupValues[4].trim().ifEmpty { null }, lineNum)
    }

    // ─── Refactoring operations ────────────────────────────────────────────────

    fun inlineMethod(src: String, method: MethodInfo): RefactorPreview {
        val body = method.body.lines().drop(1).dropLast(1).joinToString("\n") { it.trimIndent() }
        val refactored = src.replace(Regex("""${Regex.escape(method.name)}\s*\([^)]*\)"""), "/* inlined */ $body")
        return RefactorPreview("Inline '${method.name}' — replace all call sites with body", method.body, refactored, listOf("current file"))
    }

    fun changeSignature(src: String, method: MethodInfo, newParams: List<ParamInfo>, newReturn: String? = null): RefactorPreview {
        val oldSig = buildSig(method.name, method.parameters, method.returnType)
        val newSig  = buildSig(method.name, newParams, newReturn ?: method.returnType)
        return RefactorPreview("Change signature of '${method.name}'", oldSig, newSig, listOf("current file", "all callers"))
    }

    fun extractInterface(ci: ClassInfo, methodNames: List<String>): RefactorPreview {
        val sel = ci.methods.filter { it.name in methodNames }
        val iface = "I${ci.name}"
        val code = buildString {
            appendLine("interface $iface {")
            sel.forEach { appendLine("    fun ${it.name}(${it.parameters.joinToString(", ") { p -> "${p.name}: ${p.type}" }}): ${it.returnType}") }
            appendLine("}")
        }
        return RefactorPreview("Extract interface '$iface' with ${sel.size} methods", "class ${ci.name} { ... }", code, listOf("${iface}.kt (new)", "${ci.name}.kt"))
    }

    fun safeDelete(src: String, name: String): RefactorPreview {
        val usages = src.lines().mapIndexedNotNull { i, l -> if (l.contains("$name(") || l.contains(".$name")) i + 1 else null }
        return if (usages.isEmpty()) {
            val cleaned = src.lines().filterNot { it.trim().matches(Regex("(fun|val|var|class)\\s+$name.*")) }.joinToString("\n")
            RefactorPreview("Safe delete '$name' — 0 usages found", "// $name declaration", cleaned, listOf("current file"))
        } else {
            RefactorPreview("Cannot delete '$name' — ${usages.size} usages at lines: ${usages.take(5).joinToString()}", "", src, emptyList(), safe = false)
        }
    }

    fun moveClass(src: String, ci: ClassInfo, newPkg: String): RefactorPreview {
        val refactored = src.replace("package ${ci.packageName}", "package $newPkg")
        return RefactorPreview("Move '${ci.name}' from ${ci.packageName} → $newPkg", "package ${ci.packageName}", "package $newPkg",
            listOf("${ci.name}.kt (moved)", "all importers"))
    }

    private fun buildSig(name: String, params: List<ParamInfo>, ret: String) =
        "fun $name(${params.joinToString(", ") { "${it.name}: ${it.type}${if (it.default != null) " = ${it.default}" else ""}" }}): $ret"
}
