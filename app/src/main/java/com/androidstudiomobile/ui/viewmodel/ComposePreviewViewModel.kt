package com.androidstudiomobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.preview.ComposePreviewer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ComposePreviewState(
    val isLoading: Boolean = false,
    val previews: List<ComposePreviewer.PreviewAnnotation> = emptyList(),
    val xmlRoot: ComposePreviewer.XmlViewNode? = null,
    val previewType: ComposePreviewer.PreviewType = ComposePreviewer.PreviewType.NONE,
    val functionBodies: Map<String, String> = emptyMap(),
    val error: String? = null
)

class ComposePreviewViewModel : ViewModel() {
    private val _state = MutableStateFlow(ComposePreviewState())
    val state: StateFlow<ComposePreviewState> = _state.asStateFlow()

    fun loadFile(filePath: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val file = File(filePath)
                val type = ComposePreviewer.detectPreviewType(filePath)
                when (type) {
                    ComposePreviewer.PreviewType.COMPOSE_PREVIEW,
                    ComposePreviewer.PreviewType.COMPOSE_NO_PREVIEW -> {
                        val content = file.readText()
                        val previews = ComposePreviewer.parseKotlinContent(content)
                        val bodies = extractFunctionBodies(content, previews.map { it.functionName })
                        _state.update { it.copy(
                            isLoading = false,
                            previewType = type,
                            previews = previews,
                            functionBodies = bodies
                        ) }
                    }
                    ComposePreviewer.PreviewType.XML_LAYOUT -> {
                        val content = file.readText()
                        val root = ComposePreviewer.parseXmlContent(content)
                        _state.update { it.copy(isLoading = false, previewType = type, xmlRoot = root) }
                    }
                    ComposePreviewer.PreviewType.NONE -> {
                        _state.update { it.copy(isLoading = false, previewType = type) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun extractFunctionBodies(content: String, functionNames: List<String>): Map<String, String> {
        val bodies = mutableMapOf<String, String>()
        functionNames.forEach { name ->
            val startIdx = content.indexOf("fun $name(")
            if (startIdx < 0) return@forEach
            var braceCount = 0
            var started = false
            var endIdx = startIdx
            for (i in startIdx until content.length) {
                when (content[i]) {
                    '{' -> { braceCount++; started = true }
                    '}' -> {
                        braceCount--
                        if (started && braceCount == 0) { endIdx = i + 1; break }
                    }
                }
            }
            if (endIdx > startIdx) {
                bodies[name] = content.substring(startIdx, minOf(endIdx, startIdx + 2000))
            }
        }
        return bodies
    }
}
