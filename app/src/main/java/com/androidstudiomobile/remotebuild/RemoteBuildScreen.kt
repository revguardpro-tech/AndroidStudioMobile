package com.androidstudiomobile.remotebuild

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RemoteBuildViewModel : ViewModel() {
    private val svc = RemoteBuildService()
    private val _logs = MutableStateFlow<List<BuildLog>>(emptyList())
    val logs = _logs.asStateFlow()
    private val _status = MutableStateFlow(BuildStatus.UNKNOWN)
    val status = _status.asStateFlow()

    var config     by mutableStateOf(RemoteBuildConfig())
    var projectDir by mutableStateOf("")
    var downloadTo by mutableStateOf("/sdcard/Download/app-debug.zip")
    var artifactUrl by mutableStateOf<String?>(null)
    var isBuilding  by mutableStateOf(false)
    var tab        by mutableStateOf(0)

    fun start() = viewModelScope.launch {
        if (projectDir.isBlank()) return@launch
        isBuilding = true; artifactUrl = null; _logs.value = emptyList(); _status.value = BuildStatus.QUEUED
        svc.triggerAndMonitor(config, File(projectDir)).collect { (s, logs) ->
            _status.value = s; _logs.value = logs
            logs.lastOrNull { it.message.startsWith("Artifact:") }?.let { artifactUrl = it.message.substringAfter("Artifact: ") }
            if (s == BuildStatus.SUCCESS || s == BuildStatus.FAILURE || s == BuildStatus.CANCELLED) isBuilding = false
        }
    }

    fun download() = viewModelScope.launch {
        val url = artifactUrl ?: return@launch
        val token = if (config.type == EndpointType.GITHUB_ACTIONS) config.githubToken else config.customKey
        _logs.value = _logs.value + BuildLog("Downloading artifact…")
        svc.downloadArtifact(url, File(downloadTo), token)
        _logs.value = _logs.value + BuildLog("Saved to $downloadTo", "SUCCESS")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBuildScreen(navController: NavController, projectPath: String) {
    val vm: RemoteBuildViewModel = viewModel()
    LaunchedEffect(projectPath) { if (projectPath.isNotBlank()) vm.projectDir = projectPath }
    val logs by vm.logs.collectAsState()
    val status by vm.status.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Surface(color = Color(0xFF2D2D2D)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text("Remote Build", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f)); BuildStatusChip(status)
                }
                if (vm.isBuilding) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF007ACC), trackColor = Color(0xFF3C3C3C))
            }
        }

        TabRow(vm.tab, containerColor = Color(0xFF252526), contentColor = Color(0xFF007ACC)) {
            listOf("Config", "Log").forEachIndexed { i, t -> Tab(vm.tab == i, { vm.tab = i }, text = { Text(t, fontSize = 11.sp, color = if (vm.tab == i) Color(0xFF007ACC) else Color.Gray) }) }
        }

        when (vm.tab) {
            0 -> LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Project", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            BuildField("Project directory", vm.projectDir) { vm.projectDir = it }
                            Spacer(Modifier.height(6.dp))
                            BuildField("Gradle task (e.g. assembleDebug)", vm.config.gradleTask) { vm.config = vm.config.copy(gradleTask = it) }
                        }
                    }
                }
                item {
                    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Build Server", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EndpointType.values().forEach { t ->
                                    FilterChip(vm.config.type == t, { vm.config = vm.config.copy(type = t) }, label = { Text(t.name.replace('_',' '), fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF007ACC), selectedLabelColor = Color.White, containerColor = Color(0xFF3C3C3C), labelColor = Color.Gray))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (vm.config.type == EndpointType.GITHUB_ACTIONS) {
                                BuildField("GitHub Token", vm.config.githubToken) { vm.config = vm.config.copy(githubToken = it) }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(vm.config.githubOwner, { vm.config = vm.config.copy(githubOwner = it) }, Modifier.weight(1f), label = { Text("Owner", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = buildColors())
                                    OutlinedTextField(vm.config.githubRepo, { vm.config = vm.config.copy(githubRepo = it) }, Modifier.weight(1f), label = { Text("Repo", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = buildColors())
                                }
                                Spacer(Modifier.height(6.dp))
                                BuildField("Workflow (e.g. build.yml)", vm.config.githubWorkflow) { vm.config = vm.config.copy(githubWorkflow = it) }
                            } else {
                                BuildField("Server URL", vm.config.customUrl) { vm.config = vm.config.copy(customUrl = it) }
                                Spacer(Modifier.height(6.dp))
                                BuildField("API Key", vm.config.customKey) { vm.config = vm.config.copy(customKey = it) }
                            }
                        }
                    }
                }
                item {
                    Button({ vm.start(); vm.tab = 1 }, Modifier.fillMaxWidth(), enabled = !vm.isBuilding && vm.projectDir.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) {
                        Icon(Icons.Default.Build, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (vm.isBuilding) "Building…" else "Start Remote Build")
                    }
                }
            }
            1 -> Column(Modifier.fillMaxSize()) {
                vm.artifactUrl?.let { _ ->
                    Surface(color = Color(0xFF1A3A1A), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓ Build succeeded", color = Color(0xFF4CAF50), fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Button({ vm.download() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text("Download APK", fontSize = 11.sp) }
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (logs.isEmpty()) item { Text("No build started.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                    items(logs) { log ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("›", color = Color(0xFF007ACC), fontFamily = FontFamily.Monospace, fontSize = 11.sp); Spacer(Modifier.width(5.dp))
                            Text(log.message, color = when (log.level) { "ERROR" -> Color(0xFFFF6B6B); "WARN" -> Color(0xFFFF9800); "SUCCESS" -> Color(0xFF4CAF50); else -> Color(0xFFCCCCCC) }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun BuildStatusChip(s: BuildStatus) {
    val (text, color) = when (s) { BuildStatus.SUCCESS -> "Success" to Color(0xFF4CAF50); BuildStatus.FAILURE -> "Failed" to Color(0xFFFF5252); BuildStatus.IN_PROGRESS -> "Building…" to Color(0xFF007ACC); BuildStatus.QUEUED -> "Queued" to Color(0xFFFF9800); else -> "Idle" to Color.Gray }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BuildField(label: String, value: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label, color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = buildColors())

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun buildColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray)
