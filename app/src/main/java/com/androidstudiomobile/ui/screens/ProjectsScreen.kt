package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.androidstudiomobile.data.model.Project
import com.androidstudiomobile.ui.viewmodel.ProjectsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navController: NavController, viewModel: ProjectsViewModel) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Projects") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("new_project") }) {
                Icon(Icons.Default.Add, "New Project")
            }
        }
    ) { padding ->
        if (state.projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No projects yet. Create one!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(state.projects) { project ->
                    ProjectCard(project, 
                        onClick = { navController.navigate("workspace/${project.id}") },
                        onDelete = { viewModel.deleteProject(project) })
                }
            }
        }
    }
}

@Composable
fun ProjectCard(project: Project, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(Modifier.fillMaxWidth().padding(8.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
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
            text = { Text("Delete \"${project.name}\"? This removes it from the list but does not delete files.") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } })
    }
}
