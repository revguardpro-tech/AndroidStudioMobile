package com.androidstudiomobile.lsp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GoToResult(val results: List<DefinitionResult>, val symbol: String, val type: GoToType)
enum class GoToType { DEFINITION, USAGES }

class LspViewModel(app: Application) : AndroidViewModel(app) {
    private val analyzer = KotlinAnalyzer(app)
    val analyzerIssues = analyzer.issues
    val isAnalyzing    = analyzer.isAnalyzing
    val lspStatus      = LspManager.status

    private val _goToResult     = MutableStateFlow<GoToResult?>(null)
    val goToResult: StateFlow<GoToResult?> = _goToResult.asStateFlow()

    private val _documentation  = MutableStateFlow<String?>(null)
    val documentation: StateFlow<String?> = _documentation.asStateFlow()

    fun analyzeFile(filePath: String, content: String, projectPath: String) {
        viewModelScope.launch { analyzer.analyze(filePath, content, projectPath) }
    }

    fun goToDefinition(symbol: String, projectPath: String, currentFile: String = "") {
        viewModelScope.launch {
            val results = GoToDefinitionProvider.findDefinition(symbol, projectPath, currentFile)
            _goToResult.value = GoToResult(results, symbol, GoToType.DEFINITION)
        }
    }

    fun findUsages(symbol: String, projectPath: String) {
        viewModelScope.launch {
            val results = GoToDefinitionProvider.findUsages(symbol, projectPath)
            _goToResult.value = GoToResult(results, symbol, GoToType.USAGES)
        }
    }

    fun getDocumentation(symbol: String, projectPath: String) {
        viewModelScope.launch {
            _documentation.value = GoToDefinitionProvider.getKDoc(symbol, projectPath)
                ?: "No KDoc found for `$symbol`"
        }
    }

    fun startLsp(context: android.content.Context, projectPath: String) =
        LspManager.start(context, projectPath, viewModelScope)

    fun stopLsp() = LspManager.stop()
    fun dismissGoTo() { _goToResult.value = null }
    fun dismissDoc()  { _documentation.value = null }
    fun getCapabilities() = analyzer.getCapabilities()
}
