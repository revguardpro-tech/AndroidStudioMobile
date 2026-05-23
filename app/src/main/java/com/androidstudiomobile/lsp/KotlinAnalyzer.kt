package com.androidstudiomobile.lsp

import android.content.Context
import com.androidstudiomobile.lint.LintEngine
import com.androidstudiomobile.lint.LintIssue
import com.androidstudiomobile.lint.LintSeverity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Real-compiler error analyzer.
 * Priority: kotlinc (Termux) > ecj.jar > LintEngine (regex fallback)
 */
class KotlinAnalyzer(private val context: Context) {

    private val _issues = MutableStateFlow<List<LintIssue>>(emptyList())
    val issues: StateFlow<List<LintIssue>> = _issues

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val toolsDir   get() = File(context.filesDir, "tools")
    private val ecjJar     get() = File(toolsDir, "ecj.jar")
    private val androidJar get() = File(toolsDir, "android.jar")
    private val PREFIX = "/data/data/com.termux/files/usr"
    private val termuxKotlinc = File("$PREFIX/bin/kotlinc")
    private val termuxJava    = File("$PREFIX/bin/java")

    suspend fun analyze(filePath: String, content: String, projectPath: String) =
        withContext(Dispatchers.IO) {
            _isAnalyzing.value = true
            try {
                val file   = File(filePath)
                val tmpDir = File(context.cacheDir, "lsp_tmp").also { it.mkdirs() }
                val tmp    = File(tmpDir, file.name).also { it.writeText(content) }
                _issues.value = when {
                    file.extension == "kt"   && termuxKotlinc.exists() -> analyzeKotlinc(tmp, projectPath)
                    file.extension == "java" && ecjJar.exists()        -> analyzeEcj(tmp, projectPath)
                    file.extension == "kt"                             -> LintEngine.lintKotlin(content)
                    else -> LintEngine.lint(content, file.extension.ifBlank { "text" })
                }
            } catch (_: Exception) {
                _issues.value = emptyList()
            } finally {
                _isAnalyzing.value = false
            }
        }

    private fun analyzeKotlinc(file: File, projectPath: String): List<LintIssue> {
        val cp  = buildClasspath(projectPath)
        val env = mapOf("PATH" to "$PREFIX/bin:${System.getenv("PATH")}", "LD_LIBRARY_PATH" to "$PREFIX/lib")
        return try {
            val pb = ProcessBuilder(listOf(termuxKotlinc.absolutePath, "-nowarn", "-classpath", cp, file.absolutePath))
                .redirectErrorStream(true)
            pb.environment().putAll(env)
            parseKotlincOutput(pb.start().also { it.waitFor() }.inputStream.bufferedReader().readText())
        } catch (_: Exception) { LintEngine.lintKotlin(file.readText()) }
    }

    private fun analyzeEcj(file: File, projectPath: String): List<LintIssue> {
        val java = if (termuxJava.exists()) termuxJava.absolutePath else "java"
        val cp   = buildClasspath(projectPath)
        return try {
            val pb = ProcessBuilder(listOf(java, "-jar", ecjJar.absolutePath, "-1.8", "-warn:none", "-classpath", cp, file.absolutePath))
                .redirectErrorStream(true)
            parseEcjOutput(pb.start().also { it.waitFor() }.inputStream.bufferedReader().readText())
        } catch (_: Exception) { emptyList() }
    }

    private fun buildClasspath(projectPath: String): String {
        val parts = mutableListOf<String>()
        if (androidJar.exists()) parts += androidJar.absolutePath
        File(projectPath).walkTopDown().filter { it.extension == "jar" }.take(8).forEach { parts += it.absolutePath }
        return parts.joinToString(":")
    }

    private fun parseKotlincOutput(out: String): List<LintIssue> {
        val re = Regex("""(\S+\.kt):(\d+):(\d+): (error|warning): (.+)""")
        return out.lines().mapNotNull { line -> re.find(line)?.let { m ->
            val (_, ln, col, sev, msg) = m.destructured
            LintIssue(ln.toIntOrNull() ?: 1, col.toIntOrNull() ?: 1, msg.trim(),
                if (sev == "error") LintSeverity.ERROR else LintSeverity.WARNING, "kotlinc")
        }}
    }

    private fun parseEcjOutput(out: String): List<LintIssue> {
        val re = Regex("""(\d+)\. (ERROR|WARNING) in \S+ \(at line (\d+)\)[\s\S]*?\n\t(.+?)(?=\n-)""")
        return re.findAll(out).map { m ->
            val (_, sev, ln, msg) = m.destructured
            LintIssue(ln.toIntOrNull() ?: 1, 1, msg.trim(),
                if (sev == "ERROR") LintSeverity.ERROR else LintSeverity.WARNING, "ecj")
        }.toList()
    }

    fun getCapabilities() = AnalyzerCapabilities(
        hasRealCompiler = ecjJar.exists() || termuxKotlinc.exists(),
        hasKotlinc = termuxKotlinc.exists(),
        hasEcj = ecjJar.exists(),
        hasLsp = LspManager.isAvailable()
    )
}

data class AnalyzerCapabilities(
    val hasRealCompiler: Boolean,
    val hasKotlinc: Boolean,
    val hasEcj: Boolean,
    val hasLsp: Boolean
)
