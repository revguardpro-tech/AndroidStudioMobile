package com.androidstudiomobile.ui.viewmodel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.data.model.Project
import com.androidstudiomobile.data.repository.ProjectRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProjectRepository(app)
    val projects: StateFlow<List<Project>> = repo.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun createProject(name: String, path: String, packageName: String, language: String = "Kotlin") {
        viewModelScope.launch {
            File(path).mkdirs()
            repo.insertProject(Project(name = name, path = path, packageName = packageName, language = language))
        }
    }
    fun deleteProject(project: Project) { viewModelScope.launch { repo.deleteProject(project) } }
    fun importProject(path: String) {
        viewModelScope.launch {
            val dir = File(path)
            if (dir.exists()) repo.insertProject(Project(name = dir.name, path = path, packageName = "com.example.${dir.name.lowercase()}"))
        }
    }
}