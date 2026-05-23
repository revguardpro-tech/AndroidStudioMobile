package com.androidstudiomobile.nativedebug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

data class StackFrame(val index: Int, val address: String, val function: String, val file: String, val line: Int)
data class DebugVariable(val name: String, val type: String, val value: String)
data class Breakpoint(val id: Int, val file: String, val line: Int, val enabled: Boolean = true)

sealed class DebugEvent {
    data class Stopped(val reason: String, val frame: StackFrame) : DebugEvent()
    object Running : DebugEvent()
    object Terminated : DebugEvent()
}

class NativeDebugEngine(private val context: Context) {

    companion object {
        private const val TAG = "NativeDebugEngine"
        private const val LLDB_SERVER_ASSET = "lldb-server"
    }

    private var lldbProcess: Process? = null
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var nextBpId = 1

    private val _events = MutableStateFlow<DebugEvent>(DebugEvent.Terminated)
    val events: Flow<DebugEvent> = _events.asStateFlow()

    private val _breakpoints = MutableStateFlow<List<Breakpoint>>(emptyList())
    val breakpoints = _breakpoints.asStateFlow()

    private val _stackFrames = MutableStateFlow<List<StackFrame>>(emptyList())
    val stackFrames = _stackFrames.asStateFlow()

    private val _variables = MutableStateFlow<List<DebugVariable>>(emptyList())
    val variables = _variables.asStateFlow()

    private fun extractLldbServer(): File {
        val f = File(context.filesDir, LLDB_SERVER_ASSET)
        if (!f.exists()) {
            try {
                context.assets.open(LLDB_SERVER_ASSET).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                f.setExecutable(true)
            } catch (e: Exception) {
                Log.w(TAG, "lldb-server asset not found — place it in app/src/main/assets/lldb-server")
            }
        }
        return f
    }

    suspend fun startServer(targetPackage: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bin = extractLldbServer()
            val cmd = arrayOf("run-as", targetPackage, bin.absolutePath,
                "platform", "--listen", "unix-abstract://lldb_socket", "--server")
            lldbProcess = Runtime.getRuntime().exec(cmd)
            Thread.sleep(600)
            connectSocket()
        } catch (e: Exception) {
            Log.e(TAG, "startServer failed", e)
            false
        }
    }

    private fun connectSocket(): Boolean = try {
        socket = Socket("127.0.0.1", 9000)
        writer = PrintWriter(socket!!.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
        cmd("version")
        true
    } catch (e: Exception) { false }

    private fun cmd(c: String): String {
        writer?.println(c)
        val sb = StringBuilder()
        var line: String?
        while (reader?.readLine().also { line = it } != null) {
            sb.appendLine(line)
            if (line == "(lldb)" || line?.startsWith("error:") == true) break
        }
        return sb.toString()
    }

    suspend fun addBreakpoint(file: String, line: Int): Breakpoint = withContext(Dispatchers.IO) {
        cmd("breakpoint set --file $file --line $line")
        val bp = Breakpoint(nextBpId++, file, line)
        _breakpoints.value = _breakpoints.value + bp
        bp
    }

    suspend fun removeBreakpoint(id: Int) = withContext(Dispatchers.IO) {
        cmd("breakpoint delete $id")
        _breakpoints.value = _breakpoints.value.filter { it.id != id }
    }

    suspend fun continueExec() = withContext(Dispatchers.IO) {
        _events.value = DebugEvent.Running; cmd("continue"); refreshState()
    }

    suspend fun stepOver() = withContext(Dispatchers.IO) { cmd("thread step-over"); refreshState() }
    suspend fun stepInto() = withContext(Dispatchers.IO) { cmd("thread step-in"); refreshState() }
    suspend fun stepOut()  = withContext(Dispatchers.IO) { cmd("thread step-out"); refreshState() }

    suspend fun evaluate(expr: String): String = withContext(Dispatchers.IO) { cmd("expression -- $expr").trim() }

    private fun refreshState() {
        val bt = cmd("bt")
        val frames = parseBacktrace(bt)
        _stackFrames.value = frames
        if (frames.isNotEmpty()) {
            cmd("frame select 0")
            _variables.value = parseVariables(cmd("frame variable"))
            _events.value = DebugEvent.Stopped("step", frames[0])
        }
    }

    private fun parseBacktrace(out: String): List<StackFrame> {
        val rx = Regex("""frame #(\d+): (0x[0-9a-f]+) (.+?) at (.+?):(\d+)""")
        return rx.findAll(out).map {
            StackFrame(it.groupValues[1].toInt(), it.groupValues[2],
                it.groupValues[3], it.groupValues[4], it.groupValues[5].toIntOrNull() ?: 0)
        }.toList()
    }

    private fun parseVariables(out: String): List<DebugVariable> {
        val rx = Regex("""\((.+?)\) (\w+) = (.+)""")
        return out.lines().mapNotNull { rx.find(it.trim())?.let { m ->
            DebugVariable(m.groupValues[2], m.groupValues[1], m.groupValues[3])
        }}
    }

    fun stop() {
        runCatching { writer?.println("quit"); socket?.close(); lldbProcess?.destroy() }
    }
}
