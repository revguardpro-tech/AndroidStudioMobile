package com.androidstudiomobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.preview.ComposePreviewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HierarchyViewerState(
    val isLoading: Boolean = false,
    val root: ComposePreviewer.XmlViewNode? = null,
    val selectedNode: ComposePreviewer.XmlViewNode? = null,
    val selectedNodeId: String? = null,
    val expandedNodes: Set<String> = setOf("0_root_"),
    val isDirty: Boolean = false,
    val filePath: String = ""
)

class HierarchyViewerViewModel : ViewModel() {
    private val _state = MutableStateFlow(HierarchyViewerState())
    val state: StateFlow<HierarchyViewerState> = _state.asStateFlow()

    fun loadFile(filePath: String) {
        _state.update { it.copy(isLoading = true, filePath = filePath) }
        viewModelScope.launch {
            val root = withContext(Dispatchers.IO) {
                try {
                    when {
                        filePath.endsWith(".xml") -> ComposePreviewer.parseXmlLayout(filePath)
                        filePath.endsWith(".kt") -> parseKotlinHierarchy(filePath)
                        else -> null
                    }
                } catch (_: Exception) { null }
            }
            // Pre-expand root and first level
            val expanded = mutableSetOf<String>()
            root?.let { r ->
                expanded.add("0_${r.tag}_${r.id}")
                r.children.forEachIndexed { i, child ->
                    expanded.add("1_${child.tag}_${child.id}")
                }
            }
            _state.update { it.copy(isLoading = false, root = root, expandedNodes = expanded) }
        }
    }

    fun toggleNode(nodeId: String) {
        _state.update { s ->
            val expanded = s.expandedNodes.toMutableSet()
            if (expanded.contains(nodeId)) expanded.remove(nodeId) else expanded.add(nodeId)
            s.copy(expandedNodes = expanded)
        }
    }

    fun selectNode(node: ComposePreviewer.XmlViewNode) {
        _state.update { it.copy(selectedNode = node) }
    }

    fun updateAttribute(key: String, value: String) {
        _state.update { it.copy(isDirty = true) }
    }

    fun saveFile(filePath: String) {
        val root = _state.value.root ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val xml = serializeToXml(root)
                    File(filePath).writeText(xml)
                } catch (_: Exception) {}
            }
            _state.update { it.copy(isDirty = false) }
        }
    }

    private fun serializeToXml(node: ComposePreviewer.XmlViewNode, indent: Int = 0): String {
        val sb = StringBuilder()
        val pad = "    ".repeat(indent)
        sb.append("$pad<${node.tag}")
        if (node.id.isNotBlank()) sb.append("\n$pad    android:id=\"@+id/${node.id}\"")
        sb.append("\n$pad    android:layout_width=\"${node.widthSpec}\"")
        sb.append("\n$pad    android:layout_height=\"${node.heightSpec}\"")
        if (node.text.isNotBlank()) sb.append("\n$pad    android:text=\"${node.text}\"")
        if (node.background.isNotBlank()) sb.append("\n$pad    android:background=\"${node.background}\"")
        node.attributes.entries.filter { it.key !in setOf("id","layout_width","layout_height","text","background") }.forEach { (k, v) ->
            sb.append("\n$pad    android:$k=\"$v\"")
        }
        if (node.children.isEmpty()) {
            sb.append(" />")
        } else {
            sb.append(">")
            node.children.forEach { child -> sb.append("\n${serializeToXml(child, indent + 1)}") }
            sb.append("\n$pad</${node.tag}>")
        }
        return sb.toString()
    }

    private suspend fun parseKotlinHierarchy(filePath: String): ComposePreviewer.XmlViewNode? {
        val content = withContext(Dispatchers.IO) { File(filePath).readText() }
        val previews = ComposePreviewer.parseKotlinContent(content)
        if (previews.isEmpty()) return null
        // Gera uma representação de hierarquia baseada nos composables detectados
        return ComposePreviewer.XmlViewNode(
            tag = "Composable:${previews.first().functionName}",
            id = "root",
            widthSpec = "match_parent",
            heightSpec = "match_parent",
            children = previews.drop(1).map { p ->
                ComposePreviewer.XmlViewNode(tag = "Composable:${p.functionName}", id = p.functionName)
            }
        )
    }
}
