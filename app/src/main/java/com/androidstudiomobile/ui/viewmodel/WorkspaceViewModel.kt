package com.androidstudiomobile.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.MainApplication
import com.androidstudiomobile.build.BuildEngine
import com.androidstudiomobile.build.BuildMode
import com.androidstudiomobile.build.BuildVariant
import com.androidstudiomobile.build.GradleBuildEngine
import com.androidstudiomobile.data.model.*
import com.androidstudiomobile.data.repository.ProjectRepository
import com.androidstudiomobile.plugins.PluginLoader
import com.androidstudiomobile.profiler.BuildProfiler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

enum class WorkspacePanel { EDITOR, TERMINAL, FILES, LOGCAT, RESOURCES, FIND, LAYOUT_DESIGNER }

data class FileTreeNode(
    val name: String, val path: String, val isDirectory: Boolean,
    val depth: Int = 0, val children: List<FileTreeNode> = emptyList()
)

data class WorkspaceUiState(
    val projectName: String = "", val projectPath: String = "",
    val openFiles: List<OpenFile> = emptyList(), val activeFileIndex: Int = 0,
    val fileTree: List<FileTreeNode> = emptyList(), val buildLogs: List<BuildLog> = emptyList(),
    val isBuilding: Boolean = false, val buildResult: BuildResult? = null,
    val activePanel: WorkspacePanel = WorkspacePanel.EDITOR,
    val buildVariant: BuildVariant = BuildVariant.DEBUG, val buildMode: BuildMode = BuildMode.SIMPLE,
    val availableGradleTasks: List<String> = emptyList(), val isSaving: Boolean = false
)

class WorkspaceViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ProjectRepository(app)
    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var buildEngine: BuildEngine? = null
    private var gradleEngine: GradleBuildEngine? = null
    private var projectId: Long = 0L

    // ── Plugin notification channel ───────────────────────────────────────────
    private val _pluginNotification = MutableStateFlow<String?>(null)
    val pluginNotification: StateFlow<String?> = _pluginNotification.asStateFlow()
    fun dismissPluginNotification() { _pluginNotification.value = null }

    // ── PluginLoader ──────────────────────────────────────────────────────────
    val pluginLoader: PluginLoader by lazy {
        PluginLoader(
            ctx = getApplication(),
            api = object : PluginLoader.EditorApi {
                override fun openFile(path: String)  = this@WorkspaceViewModel.openFile(path)
                override fun insertText(text: String) {}
                override fun currentFilePath(): String =
                    _uiState.value.openFiles.getOrNull(_uiState.value.activeFileIndex)?.path ?: ""
                override fun readFile(path: String): String =
                    runCatching { File(path).readText() }.getOrDefault("")
                override fun writeFile(path: String, content: String) {
                    viewModelScope.launch(Dispatchers.IO) { runCatching { File(path).writeText(content) } }
                }
                override fun listFiles(dir: String): List<String> =
                    File(dir).listFiles()?.map { it.absolutePath } ?: emptyList()
                override fun runBuild(variant: String) = buildProject()
                override fun showNotification(msg: String) { _pluginNotification.value = msg }
                override fun log(msg: String) { Log.d("PluginLoader", msg) }
            }
        ).also { it.refresh() }
    }

    val plugins       get() = pluginLoader.plugins
    val pluginResults get() = pluginLoader.results
    fun runPlugin(plugin: PluginLoader.Plugin) =
        viewModelScope.launch(Dispatchers.IO) { pluginLoader.run(plugin) }

    // ── BuildProfiler — Application singleton ────────────────────────────────
    val profiler: BuildProfiler get() = MainApplication.profiler

    // ─────────────────────────────────────────────────────────────────────────

    fun loadProject(id: Long) {
        projectId = id
        viewModelScope.launch {
            val project = repo.getProject(id) ?: return@launch
            buildEngine  = BuildEngine(getApplication())
            gradleEngine = GradleBuildEngine(getApplication())
            val mode  = gradleEngine!!.detectBuildMode(project.path)
            val tasks = if (mode == BuildMode.GRADLE) gradleEngine!!.getGradleTasks(project.path) else emptyList()
            _uiState.update {
                it.copy(
                    projectName = project.name, projectPath = project.path,
                    fileTree = buildFileTree(File(project.path)),
                    buildMode = mode, availableGradleTasks = tasks
                )
            }
            repo.updateLastOpened(id)
            MainApplication.lsp.start(project.path)
        }
    }

    fun openFile(path: String) {
        val file = File(path); if (!file.exists() || !file.isFile) return
        val existing = _uiState.value.openFiles.indexOfFirst { it.path == path }
        if (existing >= 0) { _uiState.update { it.copy(activeFileIndex = existing) }; return }
        val content = try { file.readText() } catch (_: Exception) { "" }
        val lang = when (file.extension.lowercase()) {
            "kt","kts" -> "kotlin"; "java" -> "java"; "xml" -> "xml"; "json" -> "json"
            "gradle"   -> "groovy"; "md"   -> "markdown"; "sh"   -> "shell"
            "pro"      -> "proguard"; "toml" -> "toml"; else -> "plaintext"
        }
        val of = OpenFile(path, file.name, content, lang)
        _uiState.update { s ->
            val files = s.openFiles + of
            s.copy(openFiles = files, activeFileIndex = files.lastIndex, activePanel = WorkspacePanel.EDITOR)
        }
    }

    fun closeFile(index: Int) {
        _uiState.update { s ->
            val files = s.openFiles.toMutableList().also { it.removeAt(index) }
            s.copy(openFiles = files, activeFileIndex = when {
                files.isEmpty()       -> 0
                index >= files.size   -> files.lastIndex
                else                  -> index
            })
        }
    }

    fun saveCurrentFile(content: String) {
        val s = _uiState.value; val file = s.openFiles.getOrNull(s.activeFileIndex) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { File(file.path).writeText(content) } catch (_: Exception) {}
            _uiState.update { st ->
                val files = st.openFiles.toMutableList()
                files[s.activeFileIndex] = file.copy(content = content, isModified = false)
                st.copy(openFiles = files)
            }
        }
    }

    fun markDirty(index: Int) = _uiState.update { s ->
        val files = s.openFiles.toMutableList()
        if (index < files.size) files[index] = files[index].copy(isModified = true)
        s.copy(openFiles = files)
    }

    fun setActiveFile(index: Int)         = _uiState.update { it.copy(activeFileIndex = index) }
    fun setActivePanel(p: WorkspacePanel) = _uiState.update { it.copy(activePanel = p) }
    fun setBuildVariant(v: BuildVariant)  = _uiState.update { it.copy(buildVariant = v) }

    fun buildProject(gradleTask: String? = null) {
        val path    = _uiState.value.projectPath
        val mode    = _uiState.value.buildMode
        val variant = _uiState.value.buildVariant
        if (path.isBlank()) return
        _uiState.update {
            it.copy(isBuilding = true, buildLogs = emptyList(), buildResult = null,
                activePanel = WorkspacePanel.TERMINAL)
        }
        viewModelScope.launch {
            val profile = profiler.begin()

            val result = if (mode == BuildMode.GRADLE) {
                val engine = gradleEngine ?: run {
                    profiler.finish(profile)
                    _uiState.update { it.copy(isBuilding = false) }
                    return@launch
                }
                val logJob = launch { engine.logs.collect { logs -> _uiState.update { s -> s.copy(buildLogs = logs) } } }
                profiler.phase(profile, "Gradle Build") {
                    val r = engine.buildWithGradle(path, variant, gradleTask)
                    logJob.cancel()
                    Pair(r.success, r.logs.lastOrNull()?.message ?: "")
                }
                engine.buildWithGradle(path, variant, gradleTask).also { logJob.cancel() }
            } else {
                val engine = buildEngine ?: run {
                    profiler.finish(profile)
                    _uiState.update { it.copy(isBuilding = false) }
                    return@launch
                }
                val logJob = launch { engine.logs.collect { logs -> _uiState.update { s -> s.copy(buildLogs = logs) } } }
                profiler.phase(profile, "Simple Build") {
                    val r = engine.buildProject(path)
                    logJob.cancel()
                    Pair(r.success, r.logs.lastOrNull()?.message ?: "")
                }
                engine.buildProject(path).also { logJob.cancel() }
            }

            profiler.finish(profile, result.apkPath)
            repo.updateBuildStatus(projectId, if (result.success) "SUCCESS" else "FAILED")
            _uiState.update { it.copy(isBuilding = false, buildResult = result, buildLogs = result.logs) }
        }
    }

    fun installApk(apkPath: String) {
        val ctx = getApplication<Application>(); val file = File(apkPath)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun deleteFile(path: String)  { File(path).delete(); refreshFileTree() }
    fun renameFile(path: String, newName: String) {
        File(path).renameTo(File(File(path).parent, newName)); refreshFileTree()
    }
    fun createNewFile(parentPath: String, name: String) {
        File(parentPath, name).createNewFile(); refreshFileTree()
        openFile(File(parentPath, name).absolutePath)
    }
    fun createNewFolder(parentPath: String, name: String) {
        File(parentPath, name).mkdirs(); refreshFileTree()
    }

    private fun refreshFileTree() {
        val path = _uiState.value.projectPath
        if (path.isNotBlank()) _uiState.update { it.copy(fileTree = buildFileTree(File(path))) }
    }

    private fun buildFileTree(dir: File, depth: Int = 0): List<FileTreeNode> {
        if (!dir.exists() || depth > 8) return emptyList()
        return dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?.filterNot { it.name.startsWith(".") && depth == 0 }
            ?.map {
                FileTreeNode(it.name, it.absolutePath, it.isDirectory, depth,
                    if (it.isDirectory) buildFileTree(it, depth + 1) else emptyList())
            } ?: emptyList()
    }
}
