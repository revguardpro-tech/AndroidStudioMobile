package com.androidstudiomobile.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.navigation.Screen
import com.androidstudiomobile.ui.viewmodel.NewProjectViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(navController: NavController, vm: NewProjectViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdProjectId) {
        state.createdProjectId?.let { navController.navigate(Screen.Workspace.withId(it)) { popUpTo(Screen.Projects.route) } }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("New Project") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        }) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Project Configuration", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = state.name, onValueChange = vm::updateName,
                label = { Text("Project Name") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = state.packageName, onValueChange = vm::updatePackage,
                label = { Text("Package Name") }, leadingIcon = { Icon(Icons.Default.Inventory, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                supportingText = { Text("e.g. com.example.myapp") })
            Text("Min SDK", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 26, 28, 30, 33).forEach { sdk ->
                    FilterChip(selected = state.minSdk == sdk, onClick = { vm.updateMinSdk(sdk) }, label = { Text("API $sdk") })
                }
            }
            Text("Language", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Kotlin", "Java").forEach { lang ->
                    FilterChip(selected = state.language == lang, onClick = { vm.updateLanguage(lang) }, label = { Text(lang) })
                }
            }
            Text("Template", style = MaterialTheme.typography.labelLarge)
            listOf("EmptyActivity","BasicActivity","NavigationDrawer","BottomNavigation").forEach { t ->
                FilterChip(selected = state.template == t, onClick = { vm.updateTemplate(t) }, label = { Text(t) }, modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp)); Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }}
            Button(onClick = vm::createProject, modifier = Modifier.fillMaxWidth(), enabled = !state.isCreating) {
                if (state.isCreating) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (state.isCreating) "Creating…" else "Create Project")
            }
        }
    }
}