package com.androidstudiomobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.debug.DebugInspectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DebugInspectorState(
    val markers: List<DebugInspectorEngine.DebugMarker> = emptyList(),
    val isInjecting: Boolean = false,
    val injectionResult: DebugInspectorEngine.InspectionResult? = null,
    val capturedValues: List<DebugInspectorEngine.DebugLogEntry> = emptyList(),
    val error: String? = null
)

class DebugInspectorViewModel : ViewModel() {
    private val _state = MutableStateFlow(DebugInspectorState())
    val state: StateFlow<DebugInspectorState> = _state.asStateFlow()

    fun loadFile(filePath: String) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                try { File(filePath).readText() } catch (_: Exception) { "" }
            }
            val markers = DebugInspectorEngine.scanForDebugMarkers(content)
            _state.update { it.copy(markers = markers) }
        }
    }

    fun injectLogs(projectPath: String) {
        _state.update { it.copy(isInjecting = true) }
        viewModelScope.launch {
            val tmpDir = withContext(Dispatchers.IO) {
                val dir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "asm_debug_${System.currentTimeMillis()}")
                dir.absolutePath
            }
            val result = DebugInspectorEngine.injectDebugLogs(projectPath, tmpDir)
            _state.update { it.copy(isInjecting = false, injectionResult = result) }
        }
    }

    fun removeLogs(filePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DebugInspectorEngine.removeDebugLogs(filePath)
            }
            loadFile(filePath)
        }
    }

    fun loadLogcatOutput(logcatText: String) {
        val entries = DebugInspectorEngine.parseDebugLogsFromLogcat(logcatText)
        _state.update { it.copy(capturedValues = entries) }
    }
}
