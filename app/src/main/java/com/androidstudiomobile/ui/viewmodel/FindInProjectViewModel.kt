package com.androidstudiomobile.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
data class SearchResult(val filePath: String, val fileName: String, val line: Int, val snippet: String, val matchRange: IntRange = 0..0)
data class FindState(val query: String = "", val replaceQuery: String = "", val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false, val caseSensitive: Boolean = false, val useRegex: Boolean = false,
    val fileExtFilter: String = "", val replacedCount: Int = 0)
class FindInProjectViewModel : ViewModel() {
    private val _state = MutableStateFlow(FindState())
    val state: StateFlow<FindState> = _state.asStateFlow()
    private var searchJob: Job? = null
    fun updateQuery(q: String) = _state.update { it.copy(query = q) }
    fun updateReplace(r: String) = _state.update { it.copy(replaceQuery = r) }
    fun toggleCase()   = _state.update { it.copy(caseSensitive = !it.caseSensitive) }
    fun toggleRegex()  = _state.update { it.copy(useRegex = !it.useRegex) }
    fun setExtFilter(e: String) = _state.update { it.copy(fileExtFilter = e) }
    fun search(projectPath: String) {
        val q = _state.value.query; if (q.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(isSearching = true, results = emptyList()) }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<SearchResult>()
            val ext = _state.value.fileExtFilter
            val cs  = _state.value.caseSensitive
            val re  = _state.value.useRegex
            try {
                val pattern = if (re) Regex(q, if (!cs) setOf(RegexOption.IGNORE_CASE) else emptySet())
                              else Regex(Regex.escape(q), if (!cs) setOf(RegexOption.IGNORE_CASE) else emptySet())
                File(projectPath).walkTopDown()
                    .filter { it.isFile && (ext.isBlank() || it.extension == ext) }
                    .filter { !it.absolutePath.contains("/build/") }
                    .take(300)
                    .forEach { file ->
                        file.readLines().forEachIndexed { idx, line ->
                            pattern.find(line)?.let { match ->
                                results.add(SearchResult(file.absolutePath, file.name, idx+1, line.trim().take(100), match.range))
                            }
                        }
                    }
            } catch (_: Exception) {}
            _state.update { it.copy(results = results, isSearching = false) }
        }
    }
    fun replaceAll(projectPath: String) {
        val s = _state.value; if (s.query.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            s.results.map { it.filePath }.distinct().forEach { path ->
                val f = File(path)
                val content = f.readText()
                val newContent = if (s.useRegex) content.replace(Regex(s.query), s.replaceQuery)
                                 else if (s.caseSensitive) content.replace(s.query, s.replaceQuery)
                                 else content.replace(s.query, s.replaceQuery, ignoreCase = true)
                if (newContent != content) { f.writeText(newContent); count++ }
            }
            _state.update { it.copy(replacedCount = count) }
        }
    }
}