package com.androidstudiomobile.ui.viewmodel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class TerminalState(val output: List<String> = emptyList(), val cwd: String = "/", val isRunning: Boolean = false)
class TerminalViewModel : ViewModel() {
    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()
    private var shellProcess: Process? = null
    private val TERM_SHELL = if (java.io.File("/data/data/com.termux/files/usr/bin/bash").exists())
        "/data/data/com.termux/files/usr/bin/bash" else "/system/bin/sh"
    fun startShell(context: Context) {
        if (shellProcess != null) return
        _state.update { it.copy(output = emptyList(), isRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(TERM_SHELL, "-i").redirectErrorStream(true)
                pb.environment()["TERM"] = "xterm-256color"
                pb.environment()["HOME"] = context.filesDir.absolutePath
                shellProcess = pb.start()
                appendLine("$ Shell started (${TERM_SHELL})")
                shellProcess!!.inputStream.bufferedReader().forEachLine { line -> appendLine(line) }
            } catch (e: Exception) { appendLine("Error: ${e.message}") }
            _state.update { it.copy(isRunning = false) }
        }
    }
    fun runCommand(cmd: String) {
        appendLine("$ $cmd")
        viewModelScope.launch(Dispatchers.IO) {
            try { 
                shellProcess?.outputStream?.apply { 
                    write("$cmd\n".toByteArray())
                    flush() 
                } ?: run { 
                    val out = Runtime.getRuntime().exec(cmd).inputStream.bufferedReader().readText()
                    appendLine(out) 
                }
            } catch (e: Exception) { appendLine("Error: ${e.message}") }
        }
    }
    fun clear() = _state.update { it.copy(output = emptyList()) }
    private fun appendLine(line: String) = _state.update { s -> s.copy(output = (s.output + line).takeLast(3000)) }
    override fun onCleared() { super.onCleared(); shellProcess?.destroy() }
}
