package com.androidstudiomobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.modules.ModuleGraphParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModuleGraphState(
    val isLoading: Boolean = false,
    val modules: List<ModuleGraphParser.GradleModule> = emptyList(),
    val edges: List<Triple<String, String, String>> = emptyList(),
    val circularDeps: List<List<String>> = emptyList(),
    val projectPath: String = ""
)

class ModuleGraphViewModel : ViewModel() {
    private val _state = MutableStateFlow(ModuleGraphState())
    val state: StateFlow<ModuleGraphState> = _state.asStateFlow()

    fun loadProject(projectPath: String) {
        _state.update { it.copy(isLoading = true, projectPath = projectPath) }
        viewModelScope.launch {
            val graph = ModuleGraphParser.parseProject(projectPath)
            val circular = ModuleGraphParser.detectCircularDependencies(graph)
            _state.update { it.copy(
                isLoading = false,
                modules = graph.modules,
                edges = graph.edges,
                circularDeps = circular
            ) }
        }
    }
}
