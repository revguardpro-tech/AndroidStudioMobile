package com.androidstudiomobile.debug

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

// ── ViewModel ────────────────────────────────────────────────────────────────

class DebugLogViewerViewModel : ViewModel() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var logcatJob: Job? = null

    data class LogEntry(
        val timestamp: String,
        val tag: String,
        val message: String,
        val level: Char = 'D'
    )

    /**
     * Inicia leitura de logcat filtrando apenas a tag ASM_DEBUG.
     * Usa `logcat -d` (dump) pois processos do próprio app têm acesso.
     * Para streaming contínuo usa `logcat` sem -d num Job cancelável.
     */
    fun startCapture() {
        if (_isRunning.value) return
        _isRunning.value = true
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Limpa buffer antes de começar
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()

                val proc = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "-s", "ASM_DEBUG:D")
                )
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                while (isActive) {
                    line = reader.readLine() ?: break
                    parseLogcatLine(line)?.let { entry ->
                        _logs.value = (_logs.value + entry).takeLast(500)
                    }
                }
                proc.destroy()
            } catch (_: Exception) { }
            _isRunning.value = false
        }
    }

    fun stopCapture() {
        logcatJob?.cancel()
        _isRunning.value = false
    }

    fun clearLogs() { _logs.value = emptyList() }

    /** Formato logcat -v time:  MM-DD HH:MM:SS.mmm D/TAG(PID): message */
    private fun parseLogcatLine(raw: String): LogEntry? {
        if (raw.isBlank() || raw.startsWith("-----")) return null
        return try {
            val ts = raw.substring(0, 18).trim()
            val rest = raw.substring(19)
            val level = rest.firstOrNull() ?: 'D'
            val tagEnd = rest.indexOf('(').takeIf { it > 0 } ?: return null
            val tag = rest.substring(2, tagEnd).trim()
            val msgStart = rest.indexOf(':').takeIf { it > 0 }?.plus(2) ?: return null
            val msg = rest.substring(msgStart)
            LogEntry(ts, tag, msg, level)
        } catch (_: Exception) { null }
    }
}

// ── UI ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogViewerScreen(viewModel: DebugLogViewerViewModel = remember { DebugLogViewerViewModel() }) {
    val logs by viewModel.logs.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val listState = rememberLazyListState()
    var filterText by remember { mutableStateOf("") }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Log Viewer", fontFamily = FontFamily.Monospace) },
                actions = {
                    IconButton(onClick = { if (isRunning) viewModel.stopCapture() else viewModel.startCapture() }) {
                        Icon(
                            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isRunning) Color(0xFFE53935) else Color(0xFF43A047)
                        )
                    }
                    IconButton(onClick = viewModel::clearLogs) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text("Filtrar mensagem") },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                singleLine = true
            )

            val filtered = if (filterText.isBlank()) logs
            else logs.filter { it.message.contains(filterText, ignoreCase = true) }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isRunning) "Aguardando logs ASM_DEBUG…" else "Pressione ▶ para capturar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filtered) { entry ->
                        LogRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: DebugLogViewerViewModel.LogEntry) {
    val bg = when (entry.level) {
        'E' -> Color(0x22E53935)
        'W' -> Color(0x22FFA000)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = entry.timestamp,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF888888),
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFF00E676)
        )
    }
    HorizontalDivider(thickness = 0.3.dp, color = Color(0x33FFFFFF))
}
