package com.androidstudiomobile.lint
import java.io.File

enum class LintSeverity { INFO, WARNING, ERROR }
data class LintIssue(val line: Int, val column: Int, val message: String, val severity: LintSeverity, val id: String)

class LintEngine {
    fun lintKotlin(content: String): List<LintIssue> {
        val issues = mutableListOf<LintIssue>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val ln = idx + 1; val t = line.trim()
            if (t.startsWith("import") && t.contains(".*"))
                issues += LintIssue(ln, 1, "Wildcard import", LintSeverity.WARNING, "wildcard-import")
            if (Regex("""System\.out\.print""").containsMatchIn(t))
                issues += LintIssue(ln, 1, "Use android.util.Log instead of System.out", LintSeverity.WARNING, "no-sysout")
            if (t.contains("GlobalScope"))
                issues += LintIssue(ln, line.indexOf("GlobalScope")+1, "Avoid GlobalScope — use viewModelScope or lifecycleScope", LintSeverity.WARNING, "global-scope")
            if (t.contains("Thread.sleep("))
                issues += LintIssue(ln, 1, "Thread.sleep() on main thread will block UI", LintSeverity.WARNING, "thread-sleep")
            if (t.contains("TODO()") || t.contains("TODO(\"\"\"\""))
                issues += LintIssue(ln, line.indexOf("TODO")+1, "Incomplete implementation: TODO", LintSeverity.INFO, "todo")
        }
        return issues
    }

    fun lintJava(content: String): List<LintIssue> {
        val issues = mutableListOf<LintIssue>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val ln = idx + 1; val t = line.trim()
            if (Regex("""catch\s*\(\s*Exception\s+""").containsMatchIn(t))
                issues += LintIssue(ln, 1, "Catching generic Exception — be more specific", LintSeverity.WARNING, "broad-catch")
            if (t.contains("System.out.print"))
                issues += LintIssue(ln, 1, "Use android.util.Log instead of System.out", LintSeverity.WARNING, "no-sysout")
            if (t.startsWith("import") && t.contains(".*"))
                issues += LintIssue(ln, 1, "Wildcard import", LintSeverity.WARNING, "wildcard-import")
        }
        return issues
    }

    fun lintXml(content: String): List<LintIssue> {
        val issues = mutableListOf<LintIssue>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val ln = idx + 1; val t = line.trim()
            if (t.contains("android:text=\"\"") && !t.contains("@string/") && !t.contains("android:text=\"@") && t.length > 20)
                issues += LintIssue(ln, 1, "Hardcoded text — use string resource", LintSeverity.WARNING, "hardcoded-text")
            if (t.contains("android:textColor=\"#") || t.contains("android:background=\"#"))
                issues += LintIssue(ln, 1, "Hardcoded color — use color resource", LintSeverity.WARNING, "hardcoded-color")
        }
        // Check well-formedness
        val openCount  = Regex("<[^/!][^>]*[^/]>").findAll(content).count()
        val closeCount = Regex("</[^>]+>").findAll(content).count()
        val selfClose  = Regex("<[^>]+/>").findAll(content).count()
        if (openCount > closeCount + selfClose + 5)
            issues += LintIssue(1, 1, "XML may have unclosed tags", LintSeverity.WARNING, "xml-structure")
        return issues
    }

    fun lintJson(content: String): List<LintIssue> {
        return try { org.json.JSONObject(content); emptyList() }
        catch (e: Exception) { listOf(LintIssue(1, 1, "Invalid JSON: ${e.message?.take(80)}", LintSeverity.ERROR, "json-parse")) }
    }
}
