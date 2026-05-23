package com.androidstudiomobile.lsp

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.ServerSocket

/**
 * kotlin-language-server (KLS) lifecycle manager.
 * Install via Termux: pkg install kotlin-language-server
 * Architecture: Monaco ←TCP relay← KLS stdio
 */
object LspManager {
    const val LSP_PORT = 19827
    private const val PREFIX = "/data/data/com.termux/files/usr"

    enum class LspStatus { STOPPED, STARTING, RUNNING, ERROR }
    private val _status = MutableStateFlow(LspStatus.STOPPED)
    val status: StateFlow<LspStatus> = _status

    private var serverJob: Job?  = null
    private var lspProcess: Process? = null

    fun isAvailable(): Boolean = listOf(
        "$PREFIX/bin/kotlin-language-server",
        "$PREFIX/share/kotlin-language-server/server.jar"
    ).any { File(it).exists() }

    private fun getCmd(): List<String>? {
        val bin = File("$PREFIX/bin/kotlin-language-server")
        if (bin.exists()) return listOf(bin.absolutePath)
        val jar = File("$PREFIX/share/kotlin-language-server/server.jar")
        if (jar.exists()) return listOf("$PREFIX/bin/java", "-jar", jar.absolutePath)
        return null
    }

    fun start(context: Context, projectPath: String, scope: CoroutineScope) {
        if (_status.value == LspStatus.RUNNING) return
        val cmd = getCmd() ?: run { _status.value = LspStatus.ERROR; return }
        _status.value = LspStatus.STARTING
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(cmd).directory(File(projectPath))
                pb.environment().apply {
                    put("PATH", "$PREFIX/bin:${System.getenv("PATH") ?: ""}")
                    put("LD_LIBRARY_PATH", "$PREFIX/lib")
                }
                lspProcess = pb.start()
                _status.value = LspStatus.RUNNING
                launch(Dispatchers.IO) { startRelay(lspProcess!!, this) }
                lspProcess!!.waitFor()
                _status.value = LspStatus.STOPPED
            } catch (_: Exception) { _status.value = LspStatus.ERROR }
        }
    }

    private fun startRelay(klsProcess: Process, scope: CoroutineScope) {
        try {
            ServerSocket(LSP_PORT).use { server ->
                val client = server.accept()
                scope.launch(Dispatchers.IO) {
                    try { client.getInputStream().copyTo(klsProcess.outputStream) } catch (_: Exception) {}
                }
                scope.launch(Dispatchers.IO) {
                    try { klsProcess.inputStream.copyTo(client.getOutputStream()) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        serverJob?.cancel()
        lspProcess?.destroy()
        lspProcess = null
        _status.value = LspStatus.STOPPED
    }
}
