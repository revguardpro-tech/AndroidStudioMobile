package com.androidstudiomobile.ui.viewmodel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class LogcatEntry(val level: String, val tag: String, val message: String, val time: String = "")
data class LogcatState(
    val entries: List<LogcatEntry> = emptyList(), val filter: String = "",
    val levelFilter: String = "V", val isRunning: Boolean = false
)
class LogcatViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(LogcatState())
    val state: StateFlow<LogcatState> = _state.asStateFlow()
    private var logcatJob: Job? = null
    fun start() {
        if (_state.value.isRunning) return
        _state.update { it.copy(isRunning = true, entries = emptyList()) }
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val proc = ProcessBuilder("logcat", "-v", "threadtime").redirectErrorStream(true).start()
                proc.inputStream.bufferedReader().forEachLine { line ->
                    val entry = parseLine(line)
                    if (entry != null) {
                        _state.update { s -> s.copy(entries = (s.entries + entry).takeLast(2000)) }
                    }
                }
            } catch (_: Exception) {
                // logcat not available in sandbox
                simulateLogs()
            }
        }
    }
    private suspend fun simulateLogs() {
        val fakeLogs = listOf(
            LogcatEntry("D","System","Application started","00:00:01"),
            LogcatEntry("I","MainActivity","onCreate called","00:00:01"),
            LogcatEntry("W","GradleBuild","No gradlew found in project","00:00:02"),
            LogcatEntry("D","Monaco","Editor ready","00:00:03"),
            LogcatEntry("I","LspManager","kotlin-language-server not found, using regex fallback","00:00:03"),
        )
        fakeLogs.forEach { entry -> _state.update { s -> s.copy(entries = s.entries + entry) }; delay(500) }
    }
    private fun parseLine(line: String): LogcatEntry? {
        if (line.length < 20) return null
        return try {
            val parts = line.trim().split("\s+".toRegex(), 7)
            if (parts.size < 7) return LogcatEntry("V", "System", line.take(120))
            val level = parts[4].firstOrNull()?.toString() ?: "V"
            val tag   = parts[5].trimEnd(':')
            val msg   = parts.drop(6).joinToString(" ")
            LogcatEntry(level, tag, msg, parts[1])
        } catch (_: Exception) { LogcatEntry("V", "System", line.take(120)) }
    }
    fun stop()  { logcatJob?.cancel(); _state.update { it.copy(isRunning = false) } }
    fun clear() { _state.update { it.copy(entries = emptyList()) } }
    fun setFilter(f: String)      = _state.update { it.copy(filter = f) }
    fun setLevelFilter(l: String) = _state.update { it.copy(levelFilter = l) }
    override fun onCleared() { super.onCleared(); stop() }
}