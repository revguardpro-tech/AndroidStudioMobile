package com.androidstudiomobile.testrunner

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

enum class TestStatus { PENDING, RUNNING, PASSED, FAILED, SKIPPED, ERROR }

data class TestCase(
    val id: String = java.util.UUID.randomUUID().toString(),
    val className: String,
    val methodName: String,
    val status: TestStatus = TestStatus.PENDING,
    val durationMs: Long = 0,
    val failureMessage: String? = null
)

data class TestRunResult(
    val cases: List<TestCase>,
    val passed: Int, val failed: Int, val skipped: Int, val errors: Int,
    val durationMs: Long, val rawOutput: String
) {
    val total get() = cases.size
}

class InstrumentedTestRunner {

    companion object { private const val TAG = "InstrumentedTestRunner" }

    private val shizukuAvailable: Boolean by lazy { checkShizuku() }

    private fun checkShizuku(): Boolean = try {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        cls.getMethod("pingBinder").invoke(null) as? Boolean ?: false
    } catch (_: Exception) { false }

    fun runTests(pkg: String, runner: String = "androidx.test.runner.AndroidJUnitRunner",
                 cls: String? = null, method: String? = null): Flow<TestCase> = flow {
        val raw = exec(buildCmd(pkg, runner, cls, method))
        parseOutput(raw).cases.forEach { emit(it) }
    }

    suspend fun runAndWait(pkg: String, runner: String = "androidx.test.runner.AndroidJUnitRunner",
                           cls: String? = null, method: String? = null): TestRunResult =
        withContext(Dispatchers.IO) { parseOutput(exec(buildCmd(pkg, runner, cls, method))) }

    private fun buildCmd(pkg: String, runner: String, cls: String?, method: String?): String {
        val sb = StringBuilder("am instrument -w -r")
        if (cls != null) sb.append(" -e class ${if (method != null) "$cls#$method" else cls}")
        sb.append(" $pkg/$runner")
        return sb.toString()
    }

    private fun exec(cmd: String): String = try {
        if (shizukuAvailable) execShizuku(cmd) else execRuntime(cmd)
    } catch (e: Exception) { "INSTRUMENTATION_CODE: -1\n${e.message}" }

    private fun execRuntime(cmd: String): String {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    private fun execShizuku(cmd: String): String = try {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        val proc = cls.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .invoke(null, arrayOf("sh", "-c", cmd), null, null)
        (proc.javaClass.getMethod("getInputStream").invoke(proc) as java.io.InputStream).bufferedReader().readText()
    } catch (e: Exception) { execRuntime(cmd) }

    private fun parseOutput(raw: String): TestRunResult {
        val cases = mutableListOf<TestCase>()
        var passed = 0; var failed = 0; var skipped = 0; var errors = 0
        val current = mutableMapOf<String, String>()
        for (line in raw.lines()) {
            val t = line.trim()
            when {
                t.startsWith("INSTRUMENTATION_STATUS:") -> {
                    val kv = t.removePrefix("INSTRUMENTATION_STATUS:").split("=", limit = 2)
                    current[kv.getOrElse(0) { "" }.trim()] = kv.getOrElse(1) { "" }.trim()
                }
                t.startsWith("INSTRUMENTATION_STATUS_CODE:") -> {
                    val code = t.removePrefix("INSTRUMENTATION_STATUS_CODE:").trim().toIntOrNull() ?: 0
                    if (code != 1) {
                        val status = when (code) { 0 -> { passed++; TestStatus.PASSED }; -2 -> { failed++; TestStatus.FAILED }; -3 -> { skipped++; TestStatus.SKIPPED }; else -> { errors++; TestStatus.ERROR } }
                        cases.add(TestCase(className = current["class"] ?: "Unknown", methodName = current["test"] ?: "unknown",
                            status = status, failureMessage = current["stack"]?.take(600)))
                        current.clear()
                    }
                }
            }
        }
        // Try XML fallback
        val xmlIdx = raw.indexOf("<?xml")
        if (xmlIdx >= 0) runCatching { return parseXml(raw.substring(xmlIdx), raw) }
        return TestRunResult(cases, passed, failed, skipped, errors, 0L, raw)
    }

    private fun parseXml(xml: String, raw: String): TestRunResult {
        val cases = mutableListOf<TestCase>(); var passed = 0; var failed = 0; var skipped = 0; var errors = 0
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val nodes = doc.getElementsByTagName("testcase")
        for (i in 0 until nodes.length) {
            val tc = nodes.item(i) as Element
            val fail = tc.getElementsByTagName("failure").item(0) as? Element
            val err  = tc.getElementsByTagName("error").item(0) as? Element
            val skip = tc.getElementsByTagName("skipped").item(0)
            val status = when { fail != null -> { failed++; TestStatus.FAILED }; err != null -> { errors++; TestStatus.ERROR }; skip != null -> { skipped++; TestStatus.SKIPPED }; else -> { passed++; TestStatus.PASSED } }
            cases.add(TestCase(className = tc.getAttribute("classname"), methodName = tc.getAttribute("name"),
                status = status, durationMs = (tc.getAttribute("time").toDoubleOrNull()?.times(1000))?.toLong() ?: 0,
                failureMessage = (fail ?: err)?.textContent?.take(600)))
        }
        return TestRunResult(cases, passed, failed, skipped, errors, 0L, raw)
    }

    val isShizukuAvailable get() = shizukuAvailable
}
