package com.androidstudiomobile.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
data class StringResource(val name: String, var value: String)
data class ColorResource(val name: String, var value: String)
data class ResourceState(
    val strings: List<StringResource> = emptyList(), val colors: List<ColorResource> = emptyList(),
    val drawables: List<String> = emptyList(), val projectPath: String = ""
)
class ResourceManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow(ResourceState())
    val state: StateFlow<ResourceState> = _state.asStateFlow()
    fun loadProject(path: String) {
        _state.update { it.copy(projectPath = path) }
        viewModelScope.launch(Dispatchers.IO) {
            val resDir = File(path, "app/src/main/res")
            val strings   = parseStrings(File(resDir, "values/strings.xml"))
            val colors    = parseColors(File(resDir, "values/colors.xml"))
            val drawables = File(resDir).walkTopDown().filter { it.isFile && it.extension in listOf("png","jpg","webp","xml","svg") }
                .map { it.name }.toList()
            _state.update { it.copy(strings = strings, colors = colors, drawables = drawables) }
        }
    }
    private fun parseStrings(f: File): List<StringResource> {
        if (!f.exists()) return emptyList()
        return Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(f.readText())
            .map { StringResource(it.groupValues[1], it.groupValues[2]) }.toList()
    }
    private fun parseColors(f: File): List<ColorResource> {
        if (!f.exists()) return emptyList()
        return Regex("""<color name="([^"]+)">(#[A-Fa-f0-9]+)</color>""").findAll(f.readText())
            .map { ColorResource(it.groupValues[1], it.groupValues[2]) }.toList()
    }
    fun saveStrings() {
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(_state.value.projectPath, "app/src/main/res/values/strings.xml")
            val xml = "<?xml version="1.0" encoding="utf-8"?>
<resources>
" +
                _state.value.strings.joinToString("
") { "    <string name="${it.name}">${it.value}</string>" } + "
</resources>"
            f.parentFile?.mkdirs(); f.writeText(xml)
        }
    }
    fun addString(name: String, value: String) = _state.update { it.copy(strings = it.strings + StringResource(name, value)) }
    fun updateString(index: Int, value: String) = _state.update { s ->
        s.copy(strings = s.strings.toMutableList().also { it[index] = it[index].copy(value = value) })
    }
}