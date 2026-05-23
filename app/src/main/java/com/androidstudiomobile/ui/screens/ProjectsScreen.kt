package com.androidstudiomobile.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.data.model.Project
import com.androidstudiomobile.ui.navigation.Screen
import com.androidstudiomobile.ui.viewmodel.ProjectsViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navController: NavController, vm: ProjectsViewModel = viewModel()) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Android Studio Mobile", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { navController.navigate(Screen.SdkManager.route) }) { Icon(Icons.Default.Build, "SDK Manager") }
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) { Icon(Icons.Default.Settings, "Settings") }
            }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { navController.navigate(Screen.NewProject.route) },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("New Project") })
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("No projects yet", style = MaterialTheme.typography.titleLarge)
                    Text("Create a new project or clone a repository", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects) { project ->
                    ProjectCard(project,
                        onClick = { navController.navigate(Screen.Workspace.withId(project.id)) },
                        onDelete = { vm.deleteProject(project) })
                }
            }
        }
    }
}
@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Android, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(project.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(project.path.takeLast(40), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(project.language, fontSize = 10.sp) })
                    project.buildStatus.takeIf { it != "UNKNOWN" }?.let {
                        AssistChip(onClick = {}, label = { Text(it, fontSize = 10.sp) })
                    }
                }
            }
            IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Project") },
            text = { Text("Delete "${project.name}"? This removes it from the list but does not delete files.") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } })
    }
}