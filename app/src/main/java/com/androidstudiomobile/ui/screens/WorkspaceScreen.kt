package com.androidstudiomobile.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.build.BuildVariant
import com.androidstudiomobile.data.model.BuildLog
import com.androidstudiomobile.data.model.LogLevel
import com.androidstudiomobile.editor.MonacoEditorBridge
import com.androidstudiomobile.lint.LintIssue
import com.androidstudiomobile.lsp.LspManager
import com.androidstudiomobile.lsp.LspStatusBar
import com.androidstudiomobile.lsp.LspViewModel
import com.androidstudiomobile.ui.navigation.Screen
import com.androidstudiomobile.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(navController: NavController, projectId: Long) {
    val vm: WorkspaceViewModel = viewModel()
    val lspVm: LspViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val lspStatus by lspVm.lspStatus.collectAsStateWithLifecycle()
    val lintIssues by lspVm.analyzerIssues.collectAsStateWithLifecycle()
    val isAnalyzing by lspVm.isAnalyzing.collectAsStateWithLifecycle()
    val goToResult by lspVm.goToResult.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var editorRef by remember { mutableStateOf<MonacoEditorBridge?>(null) }
    var cursorLine by remember { mutableIntStateOf(1) }
    var cursorCol by remember { mutableIntStateOf(1) }
    var showFileTree by remember { mutableStateOf(false) }
    var showBuildMenu by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { vm.loadProject(projectId) }

    LaunchedEffect(state.projectPath) {
        if (state.projectPath.isNotBlank() && LspManager.isAvailable())
            lspVm.startLsp(ctx, state.projectPath)
    }

    LaunchedEffect(goToResult) {
        goToResult?.results?.singleOrNull()?.let { def ->
            vm.openFile(def.filePath)
            editorRef?.gotoLine(def.line)
            lspVm.dismissGoTo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showFileTree = !showFileTree }) {
                            Icon(Icons.Default.FolderOpen, null)
                        }
                        Text(
                            state.projectName, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Build menu
                    Box {
                        IconButton(onClick = { showBuildMenu = true }) {
                            if (state.isBuilding) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.PlayArrow, "Build", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showBuildMenu, onDismissRequest = { showBuildMenu = false }) {
                            DropdownMenuItem(text = { Text("▶ Run / assembleDebug") },
                                onClick = { vm.buildProject(); showBuildMenu = false })
                            DropdownMenuItem(text = { Text("⚙ assembleRelease") },
                                onClick = { vm.buildProject("assembleRelease"); showBuildMenu = false })
                            DropdownMenuItem(text = { Text("🧹 clean") },
                                onClick = { vm.buildProject("clean"); showBuildMenu = false })
                            DropdownMenuItem(text = { Text("🔍 lintDebug") },
                                onClick = { vm.buildProject("lintDebug"); showBuildMenu = false })
                            DropdownMenuItem(text = { Text("📦 bundleRelease") },
                                onClick = { vm.buildProject("bundleRelease"); showBuildMenu = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Switch to Debug") },
                                onClick = { vm.setBuildVariant(BuildVariant.DEBUG); showBuildMenu = false })
                            DropdownMenuItem(text = { Text("Switch to Release") },
                                onClick = { vm.setBuildVariant(BuildVariant.RELEASE); showBuildMenu = false })
                        }
                    }

                    // Refactor
                    IconButton(onClick = { navController.navigate(Screen.Refactor.withPath(state.projectPath)) }) {
                        Icon(Icons.Default.AutoFixHigh, "Refactor")
                    }

                    // Git
                    IconButton(onClick = { navController.navigate(Screen.Git.withPath(state.projectPath)) }) {
                        Icon(Icons.Default.Source, "Git")
                    }

                    // Preview do arquivo atual (Compose / XML)
                    val currentFile = state.openFiles.getOrNull(state.activeFileIndex)
                    if (currentFile != null && (currentFile.path.endsWith(".kt") || currentFile.path.endsWith(".xml"))) {
                        IconButton(onClick = {
                            navController.navigate(Screen.ComposePreview.withPath(currentFile.path))
                        }) {
                            Icon(Icons.Default.Preview, "Preview")
                        }
                    }

                    // More menu expandido com todas as ferramentas
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            // Ferramentas de análise de código
                            DropdownMenuItem(
                                text = { Text("Find in Project") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                onClick = { navController.navigate(Screen.FindInProject.withPath(state.projectPath)); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Resource Manager") },
                                leadingIcon = { Icon(Icons.Default.Image, null) },
                                onClick = { navController.navigate(Screen.ResourceManager.withPath(state.projectPath)); menuExpanded = false }
                            )
                            HorizontalDivider()

                            // Ferramentas de Build e Deploy
                            DropdownMenuItem(
                                text = { Text("Signed APK Wizard") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                onClick = { navController.navigate(Screen.SignedApkWizard.withPath(state.projectPath)); menuExpanded = false }
                            )
                            state.buildResult?.apkPath?.let { apk ->
                                DropdownMenuItem(
                                    text = { Text("Analyze APK") },
                                    leadingIcon = { Icon(Icons.Default.Analytics, null) },
                                    onClick = { navController.navigate(Screen.ApkAnalyzer.withPath(apk)); menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Install APK") },
                                    leadingIcon = { Icon(Icons.Default.InstallMobile, null) },
                                    onClick = { vm.installApk(apk); menuExpanded = false }
                                )
                            }
                            HorizontalDivider()

                            // Ferramentas de Layout e UI
                            currentFile?.path?.let { fp ->
                                DropdownMenuItem(
                                    text = { Text("Compose Preview") },
                                    leadingIcon = { Icon(Icons.Default.Preview, null) },
                                    onClick = { navController.navigate(Screen.ComposePreview.withPath(fp)); menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hierarchy Viewer") },
                                    leadingIcon = { Icon(Icons.Default.AccountTree, null) },
                                    onClick = { navController.navigate(Screen.HierarchyViewer.withPath(fp)); menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Device Simulator") },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                                    onClick = { navController.navigate(Screen.DeviceSimulator.withPath(fp)); menuExpanded = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Theme Editor") },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = { navController.navigate(Screen.ThemeEditor.withPath(state.projectPath)); menuExpanded = false }
                            )
                            HorizontalDivider()

                            // Ferramentas de Análise de Projeto
                            DropdownMenuItem(
                                text = { Text("Module Graph") },
                                leadingIcon = { Icon(Icons.Default.Hub, null) },
                                onClick = { navController.navigate(Screen.ModuleGraph.withPath(state.projectPath)); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Database Inspector") },
                                leadingIcon = { Icon(Icons.Default.Storage, null) },
                                onClick = { navController.navigate(Screen.DatabaseInspector.empty); menuExpanded = false }
                            )
                            currentFile?.path?.let { fp ->
                                DropdownMenuItem(
                                    text = { Text("Debug Inspector") },
                                    leadingIcon = { Icon(Icons.Default.BugReport, null) },
                                    onClick = { navController.navigate(Screen.DebugInspector.withPaths(fp, state.projectPath)); menuExpanded = false }
                                )
                            }
                            HorizontalDivider()

                            HorizontalDivider()

                            // Ferramentas avançadas
                            DropdownMenuItem(
                                text = { Text("Nav Graph Editor") },
                                leadingIcon = { Icon(Icons.Default.AccountTree, null) },
                                onClick = {
                                    val fp = state.openFiles.getOrNull(state.activeFileIndex)?.path ?: state.projectPath
                                    navController.navigate(Screen.NavGraphEditor.withPath(fp))
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Layout Editor") },
                                leadingIcon = { Icon(Icons.Default.Dashboard, null) },
                                onClick = {
                                    navController.navigate(Screen.LayoutEditor.empty)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Build Profiler") },
                                leadingIcon = { Icon(Icons.Default.Speed, null) },
                                onClick = {
                                    navController.navigate(Screen.Profiler.route)
                                    menuExpanded = false
                                }
                            )
                                                        // Configurações
                            DropdownMenuItem(
                                text = { Text("SDK Manager") },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { navController.navigate(Screen.SdkManager.route); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { navController.navigate(Screen.Settings.route); menuExpanded = false }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            LspStatusBar(
                lspStatus = lspStatus, issues = lintIssues, isAnalyzing = isAnalyzing,
                cursorLine = cursorLine, cursorCol = cursorCol,
                language = state.openFiles.getOrNull(state.activeFileIndex)?.language ?: "kotlin",
                buildVariant = state.buildVariant.name,
                onErrorClick = { vm.setActivePanel(WorkspacePanel.TERMINAL) },
                onLspClick = {}
            )
        }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            // File tree drawer
            if (showFileTree) {
                Surface(
                    Modifier.width(220.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp
                ) {
                    FileTreePanel(
                        state.fileTree,
                        onFileClick = { path -> vm.openFile(path); showFileTree = false },
                        onNewFile = { parent -> vm.createNewFile(parent, "NewFile.kt") },
                        onNewFolder = { parent -> vm.createNewFolder(parent, "newfolder") },
                        onDelete = { path -> vm.deleteFile(path) }
                    )
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Tab bar dos arquivos abertos
                if (state.openFiles.isNotEmpty()) {
                    LazyRow(Modifier.fillMaxWidth().height(36.dp).background(MaterialTheme.colorScheme.surface)) {
                        itemsIndexed(state.openFiles) { idx, file ->
                            Row(
                                modifier = Modifier
                                    .clickable { vm.setActiveFile(idx); vm.setActivePanel(WorkspacePanel.EDITOR) }
                                    .background(
                                        if (idx == state.activeFileIndex && state.activePanel == WorkspacePanel.EDITOR)
                                            MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (file.isModified) Text("● ", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                Text(file.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Close, null, Modifier.size(12.dp).clickable { vm.closeFile(idx) })
                            }
                        }
                    }
                }

                // Panel selector — Editor, Build, Logcat
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    PanelTab("Editor", Icons.Default.Code, state.activePanel == WorkspacePanel.EDITOR) {
                        vm.setActivePanel(WorkspacePanel.EDITOR)
                    }
                    PanelTab("Build", Icons.Default.Terminal, state.activePanel == WorkspacePanel.TERMINAL) {
                        vm.setActivePanel(WorkspacePanel.TERMINAL)
                    }
                    PanelTab("Logcat", Icons.Default.BugReport, state.activePanel == WorkspacePanel.LOGCAT) {
                        navController.navigate(Screen.Logcat.route)
                    }
                }
                HorizontalDivider()

                // Main content area
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.activePanel) {
                        WorkspacePanel.EDITOR -> {
                            val activeFile = state.openFiles.getOrNull(state.activeFileIndex)
                            if (activeFile != null) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        MonacoEditorBridge(ctx).also { bridge ->
                                            bridge.layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            bridge.projectPath = state.projectPath
                                            bridge.currentFile = activeFile.path
                                            bridge.listener = object : MonacoEditorBridge.EditorListener {
                                                override fun onContentChanged(content: String) { vm.markDirty(state.activeFileIndex) }
                                                override fun onSaveRequested(content: String) { vm.saveCurrentFile(content) }
                                                override fun onLintIssues(issues: List<LintIssue>) {}
                                                override fun onGoToDefinition(symbol: String) {
                                                    lspVm.goToDefinition(symbol, state.projectPath, activeFile.path)
                                                }
                                                override fun onFindUsages(symbol: String) {
                                                    lspVm.findUsages(symbol, state.projectPath)
                                                }
                                                override fun onNavigateToFile(filePath: String, line: Int) {
                                                    vm.openFile(filePath); bridge.gotoLine(line)
                                                }
                                                override fun onCursorMoved(line: Int, col: Int) {
                                                    cursorLine = line; cursorCol = col
                                                }
                                                override fun onEditorReady() {
                                                    bridge.setContent(activeFile.content, activeFile.language)
                                                }
                                                override fun onHoverRequest(symbol: String): String? = null
                                                override fun onGetDoc(symbol: String) {
                                                    lspVm.getDocumentation(symbol, state.projectPath)
                                                }
                                            }
                                            editorRef = bridge
                                        }
                                    },
                                    update = { bridge ->
                                        bridge.projectPath = state.projectPath
                                        bridge.currentFile = activeFile.path
                                    }
                                )
                            } else {
                                EmptyEditorPlaceholder { showFileTree = true }
                            }
                        }
                        WorkspacePanel.TERMINAL -> {
                            BuildOutputPanel(state.buildLogs, state.isBuilding, state.buildResult?.apkPath) { apk ->
                                vm.installApk(apk)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditorPlaceholder(onOpenTree: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Code, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Abra um arquivo da árvore de projeto", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onOpenTree) { Text("Abrir Árvore de Arquivos") }
        }
    }
}

@Composable private fun PanelTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun FileTreePanel(
    nodes: List<FileTreeNode>,
    onFileClick: (String) -> Unit,
    onNewFile: (String) -> Unit,
    onNewFolder: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        fun renderNodes(list: List<FileTreeNode>) {
            list.forEach { node ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (!node.isDirectory) onFileClick(node.path) }
                            .padding(start = (node.depth * 12 + 8).dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            null, Modifier.size(16.dp),
                            tint = if (node.isDirectory) Color(0xFFFFC66D) else MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(node.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (node.isDirectory) {
                            IconButton(onClick = { onNewFile(node.path) }, Modifier.size(20.dp)) {
                                Icon(Icons.Default.Add, null, Modifier.size(12.dp))
                            }
                        } else {
                            IconButton(onClick = { onDelete(node.path) }, Modifier.size(20.dp)) {
                                Icon(Icons.Default.Delete, null, Modifier.size(12.dp))
                            }
                        }
                    }
                }
                if (node.isDirectory) renderNodes(node.children)
            }
        }
        renderNodes(nodes)
    }
}

@Composable private fun BuildOutputPanel(
    logs: List<BuildLog>,
    isBuilding: Boolean,
    apkPath: String?,
    onInstall: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (isBuilding) LinearProgressIndicator(Modifier.fillMaxWidth())
        apkPath?.let {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1B5E20)).padding(8.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Text("✅ Build successful — APK ready", color = Color.White, fontSize = 12.sp)
                TextButton(onClick = { onInstall(it) }) { Text("Install", color = Color.White) }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(logs) { log ->
                Text(log.message, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = when (log.level) {
                    LogLevel.ERROR -> Color(0xFFFF6B6B); LogLevel.SUCCESS -> Color(0xFF4CAF50)
                    LogLevel.WARNING -> Color(0xFFFFC107); else -> Color(0xFFD4D4D4)
                })
            }
        }
    }
}
