package com.androidstudiomobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ThemeEntry(val name: String, val parent: String, val attributes: Map<String, String>)
data class ThemeEditorState(
    val isLoading: Boolean = false,
    val colors: List<Pair<String, String>> = emptyList(),
    val themes: List<ThemeEntry> = emptyList(),
    val isDirty: Boolean = false,
    val savedMessage: String = "",
    val projectPath: String = "",
    val colorsFilePath: String = ""
)

class ThemeEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(ThemeEditorState())
    val state: StateFlow<ThemeEditorState> = _state.asStateFlow()

    fun loadTheme(projectPath: String) {
        _state.update { it.copy(isLoading = true, projectPath = projectPath, savedMessage = "") }
        viewModelScope.launch {
            val colors = withContext(Dispatchers.IO) { loadColors(projectPath) }
            val themes = withContext(Dispatchers.IO) { loadThemes(projectPath) }
            val colorsPath = findColorsFile(projectPath)?.absolutePath ?: ""
            _state.update { it.copy(isLoading = false, colors = colors, themes = themes, colorsFilePath = colorsPath, isDirty = false) }
        }
    }

    fun updateColor(name: String, hex: String) {
        _state.update { s ->
            val updated = s.colors.map { if (it.first == name) Pair(name, hex) else it }
            s.copy(colors = updated, isDirty = true)
        }
    }

    fun saveTheme() {
        val s = _state.value
        if (s.colorsFilePath.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val sb = StringBuilder()
                    sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                    sb.appendLine("<resources>")
                    s.colors.forEach { (name, value) ->
                        sb.appendLine("    <color name=\"$name\">$value</color>")
                    }
                    sb.appendLine("</resources>")
                    File(s.colorsFilePath).writeText(sb.toString())
                } catch (_: Exception) {}
            }
            _state.update { it.copy(isDirty = false, savedMessage = "Tema salvo em ${File(s.colorsFilePath).name}") }
        }
    }

    private fun findColorsFile(projectPath: String): File? =
        File(projectPath).walkTopDown()
            .filter { it.name == "colors.xml" && it.path.contains("res") }
            .firstOrNull()

    private fun loadColors(projectPath: String): List<Pair<String, String>> {
        val file = findColorsFile(projectPath) ?: return getDefaultColors()
        return try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val doc = factory.newDocumentBuilder().parse(file)
            val colors = mutableListOf<Pair<String, String>>()
            val nodes = doc.getElementsByTagName("color")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as org.w3c.dom.Element
                val name = el.getAttribute("name")
                val value = el.textContent.trim()
                if (name.isNotBlank() && value.isNotBlank()) colors += Pair(name, value)
            }
            colors.ifEmpty { getDefaultColors() }
        } catch (_: Exception) { getDefaultColors() }
    }

    private fun loadThemes(projectPath: String): List<ThemeEntry> {
        val file = File(projectPath).walkTopDown()
            .filter { it.name == "themes.xml" && it.path.contains("res") }
            .firstOrNull() ?: return emptyList()
        return try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val doc = factory.newDocumentBuilder().parse(file)
            val themes = mutableListOf<ThemeEntry>()
            val styles = doc.getElementsByTagName("style")
            for (i in 0 until styles.length) {
                val el = styles.item(i) as org.w3c.dom.Element
                val name = el.getAttribute("name")
                val parent = el.getAttribute("parent")
                val attrs = mutableMapOf<String, String>()
                val items = el.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val item = items.item(j) as org.w3c.dom.Element
                    attrs[item.getAttribute("name")] = item.textContent.trim()
                }
                themes += ThemeEntry(name, parent, attrs)
            }
            themes
        } catch (_: Exception) { emptyList() }
    }

    private fun getDefaultColors() = listOf(
        Pair("colorPrimary", "#6200EE"),
        Pair("colorPrimaryDark", "#3700B3"),
        Pair("colorAccent", "#03DAC5"),
        Pair("colorSecondary", "#03DAC5"),
        Pair("colorBackground", "#FFFFFF"),
        Pair("colorSurface", "#FFFFFF"),
        Pair("colorError", "#B00020"),
        Pair("colorOnPrimary", "#FFFFFF"),
        Pair("colorOnSurface", "#000000")
    )
}
