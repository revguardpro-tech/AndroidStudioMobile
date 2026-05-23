package com.androidstudiomobile.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.viewmodel.GitViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(navController: NavController, projectPath: String, vm: GitViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var commitMsg   by remember { mutableStateOf("") }
    var remoteUrl   by remember { mutableStateOf("") }
    var username    by remember { mutableStateOf("") }
    var token       by remember { mutableStateOf("") }
    var cloneUrl    by remember { mutableStateOf("") }
    var activeTab   by remember { mutableIntStateOf(0) }
    LaunchedEffect(projectPath) { if (projectPath.isNotBlank()) vm.openRepo(projectPath) }
    state.error?.let { err ->
        LaunchedEffect(err) { kotlinx.coroutines.delay(3000); vm.dismissError() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Git — ${state.branch}") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.error?.let { Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer) }}
            state.success?.let { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer) }}
            Card { Column(Modifier.padding(12.dp)) {
                Text("Repository Status", style = MaterialTheme.typography.titleSmall)
                Text(state.status.ifBlank { "No changes" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Branch: ${state.branch}", color = MaterialTheme.colorScheme.primary)
            }}
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Commit") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Push/Pull") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Clone") })
                Tab(selected = activeTab == 3, onClick = { activeTab = 3 }, text = { Text("Log") })
            }
            when (activeTab) {
                0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = commitMsg, onValueChange = { commitMsg = it }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.commit(commitMsg); commitMsg = "" }, modifier = Modifier.fillMaxWidth(), enabled = commitMsg.isNotBlank() && !state.isBusy) {
                        if (state.isBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Commit All Changes")
                    }
                }
                1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("GitHub Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Personal Access Token") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.push(username = username, token = token) }, Modifier.weight(1f), enabled = !state.isBusy) { Text("Push") }
                    }
                }
                2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cloneUrl, onValueChange = { cloneUrl = it }, label = { Text("Repository URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token (optional)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.cloneRepo(cloneUrl, projectPath, username, token) }, Modifier.fillMaxWidth(), enabled = cloneUrl.isNotBlank() && !state.isBusy) { Text("Clone") }
                }
                3 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.log) { entry ->
                        Text(entry, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.log.isEmpty()) item { Text("No commits found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}