package com.androidstudiomobile.build

import android.content.Context
import com.androidstudiomobile.data.model.BuildLog
import com.androidstudiomobile.data.model.BuildResult
import com.androidstudiomobile.data.model.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class GradleBuildEngine(private val context: Context) {

    private val _logs = MutableStateFlow<List<BuildLog>>(emptyList())
    val logs: StateFlow<List<BuildLog>> = _logs.asStateFlow()

    fun detectBuildMode(projectPath: String): BuildMode {
        val dir = File(projectPath)
        return if (File(dir, "gradlew").exists() || File(dir, "build.gradle").exists() ||
            File(dir, "build.gradle.kts").exists()) {
            BuildMode.GRADLE
        } else {
            BuildMode.SIMPLE
        }
    }

    suspend fun getGradleTasks(projectPath: String): List<String> = withContext(Dispatchers.IO) {
        val gradlew = File(projectPath, "gradlew")
        if (!gradlew.exists()) return@withContext emptyList()
        return@withContext try {
            gradlew.setExecutable(true)
            val proc = ProcessBuilder(gradlew.absolutePath, "tasks", "--all", "-q")
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            output.lines()
                .filter { it.matches(Regex("[a-zA-Z][a-zA-Z0-9]+.*-.*")) }
                .mapNotNull { it.split(" - ").firstOrNull()?.trim() }
                .filter { it.isNotBlank() && !it.startsWith("-") }
                .take(50)
        } catch (_: Exception) {
            listOf("assembleDebug", "assembleRelease", "clean", "build", "test")
        }
    }

    suspend fun buildWithGradle(
        projectPath: String,
        variant: BuildVariant = BuildVariant.DEBUG,
        gradleTask: String? = null
    ): BuildResult = withContext(Dispatchers.IO) {
        val accumLogs = mutableListOf<BuildLog>()
        fun log(msg: String, level: LogLevel = LogLevel.INFO) {
            accumLogs.add(BuildLog(msg, level))
            _logs.value = accumLogs.toList()
        }

        val gradlew = File(projectPath, "gradlew")
        if (!gradlew.exists()) {
            log("gradlew not found in $projectPath", LogLevel.ERROR)
            return@withContext BuildResult(false, logs = accumLogs)
        }

        val task = gradleTask ?: when (variant) {
            BuildVariant.DEBUG   -> "assembleDebug"
            BuildVariant.RELEASE -> "assembleRelease"
        }

        log("Running: ./gradlew $task", LogLevel.INFO)
        log("Project: $projectPath", LogLevel.VERBOSE)

        return@withContext try {
            gradlew.setExecutable(true)
            val startMs = System.currentTimeMillis()
            val proc = ProcessBuilder(gradlew.absolutePath, task, "--stacktrace")
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()

            proc.inputStream.bufferedReader().forEachLine { line ->
                val level = when {
                    line.contains("ERROR", true) || line.contains("FAILED", true) -> LogLevel.ERROR
                    line.contains("WARN", true)  -> LogLevel.WARNING
                    line.contains("BUILD SUCCESSFUL") -> LogLevel.SUCCESS
                    line.startsWith("> Task") -> LogLevel.DEBUG
                    else -> LogLevel.VERBOSE
                }
                log(line, level)
            }

            val exitCode = proc.waitFor()
            val durationMs = System.currentTimeMillis() - startMs
            val success = exitCode == 0

            if (success) {
                log("BUILD SUCCESSFUL in ${durationMs}ms", LogLevel.SUCCESS)
                val apk = findApk(projectPath, variant)
                BuildResult(true, apk?.absolutePath, accumLogs, durationMs)
            } else {
                log("BUILD FAILED (exit $exitCode) after ${durationMs}ms", LogLevel.ERROR)
                BuildResult(false, null, accumLogs, durationMs)
            }
        } catch (e: Exception) {
            log("Build exception: ${e.message}", LogLevel.ERROR)
            BuildResult(false, null, accumLogs)
        }
    }

    private fun findApk(projectPath: String, variant: BuildVariant): File? {
        val variantDir = if (variant == BuildVariant.DEBUG) "debug" else "release"
        return File(projectPath).walkTopDown()
            .filter { it.extension == "apk" && it.path.contains(variantDir) }
            .firstOrNull()
    }
}
