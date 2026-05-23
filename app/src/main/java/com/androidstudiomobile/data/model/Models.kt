package com.androidstudiomobile.data.model
import java.io.File
data class BuildLog(val message: String, val level: LogLevel, val timestamp: Long = System.currentTimeMillis())
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR, VERBOSE, DEBUG }
data class BuildResult(val success: Boolean, val apkPath: String? = null, val logs: List<BuildLog> = emptyList(), val durationMs: Long = 0)
data class LintIssue(val line: Int, val column: Int, val message: String, val severity: LintSeverity, val rule: String = "lint", val length: Int = 0)
enum class LintSeverity { ERROR, WARNING, INFO }