package com.androidstudiomobile.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.zip.ZipFile
data class ApkInfo(val size: Long = 0, val files: List<ApkEntry> = emptyList(), val manifest: String = "", val permissions: List<String> = emptyList(), val dexInfo: String = "")
data class ApkEntry(val name: String, val size: Long, val compressedSize: Long)
data class ApkState(val info: ApkInfo? = null, val isLoading: Boolean = false, val error: String? = null)
class ApkAnalyzerViewModel : ViewModel() {
    private val _state = MutableStateFlow(ApkState())
    val state: StateFlow<ApkState> = _state.asStateFlow()
    fun analyzeApk(path: String) {
        _state.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                val zip  = ZipFile(file)
                val entries = zip.entries().toList().map { ApkEntry(it.name, it.size, it.compressedSize) }.sortedByDescending { it.size }
                val manifest = zip.entries().toList().firstOrNull { it.name == "AndroidManifest.xml" }
                    ?.let { "Binary AndroidManifest.xml (${it.size} bytes)" } ?: "Not found"
                val permissions = entries.filter { it.name.contains("permission", true) }.map { it.name }
                val dexCount = entries.count { it.name.endsWith(".dex") }
                val dexInfo  = "$dexCount DEX file(s) — ${entries.filter { it.name.endsWith(".dex") }.sumOf { it.size } / 1024} KB total"
                zip.close()
                _state.update { it.copy(isLoading = false, info = ApkInfo(file.length(), entries.take(100), manifest, permissions, dexInfo)) }
            } catch (e: Exception) { _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}