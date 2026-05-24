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

/**
 * BuildEngine — concrete class (not interface) so WorkspaceViewModel can instantiate it directly.
 * Handles "simple" builds (non-Gradle projects) and delegates to shell commands where available.
 */
class BuildEngine(private val context: Context) {

    private val _logs = MutableStateFlow<List<BuildLog>>(emptyList())
    val logs: StateFlow<List<BuildLog>> = _logs.asStateFlow()

    suspend fun buildProject(projectPath: String): BuildResult = withContext(Dispatchers.IO) {
        val accumLogs = mutableListOf<BuildLog>()
        fun log(msg: String, level: LogLevel = LogLevel.INFO) {
            accumLogs.add(BuildLog(msg, level))
            _logs.value = accumLogs.toList()
        }

        log("Starting build for: $projectPath")
        val projectDir = File(projectPath)

        if (!projectDir.exists()) {
            log("Project directory not found: $projectPath", LogLevel.ERROR)
            return@withContext BuildResult(false, logs = accumLogs)
        }

        val gradleWrapper = File(projectDir, "gradlew")
        if (gradleWrapper.exists()) {
            log("Found gradlew — delegating to GradleBuildEngine is recommended.", LogLevel.WARNING)
            log("Running ./gradlew assembleDebug as fallback...", LogLevel.INFO)
            return@withContext try {
                gradleWrapper.setExecutable(true)
                val startMs = System.currentTimeMillis()
                val proc = ProcessBuilder(gradleWrapper.absolutePath, "assembleDebug")
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().forEachLine { line ->
                    val lvl = when {
                        line.contains("ERROR", true) || line.contains("FAILED") -> LogLevel.ERROR
                        line.contains("WARN", true)  -> LogLevel.WARNING
                        line.contains("BUILD SUCCESSFUL") -> LogLevel.SUCCESS
                        else -> LogLevel.VERBOSE
                    }
                    log(line, lvl)
                }
                val exit = proc.waitFor()
                val dur  = System.currentTimeMillis() - startMs
                if (exit == 0) {
                    log("BUILD SUCCESSFUL in ${dur}ms", LogLevel.SUCCESS)
                    val apk = projectDir.walkTopDown().filter { it.extension == "apk" }.firstOrNull()
                    BuildResult(true, apk?.absolutePath, accumLogs, dur)
                } else {
                    log("BUILD FAILED (exit $exit) in ${dur}ms", LogLevel.ERROR)
                    BuildResult(false, null, accumLogs, dur)
                }
            } catch (e: Exception) {
                log("Build exception: ${e.message}", LogLevel.ERROR)
                BuildResult(false, null, accumLogs)
            }
        }

        log("No gradlew found — this project cannot be built without Gradle.", LogLevel.WARNING)
        log("Install Gradle via Termux: pkg install gradle", LogLevel.INFO)
        BuildResult(false, logs = accumLogs)
    }
}
