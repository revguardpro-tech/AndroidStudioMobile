package com.androidstudiomobile.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.androidstudiomobile.ui.viewmodel.ResourceManagerViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceManagerScreen(navController: NavController, projectPath: String, vm: ResourceManagerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(projectPath) { vm.loadProject(projectPath) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Resource Manager") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton(onClick = vm::saveStrings) { Icon(Icons.Default.Save, "Save") } })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab==0, onClick = { tab=0 }, text = { Text("Strings") })
                Tab(selected = tab==1, onClick = { tab=1 }, text = { Text("Colors") })
                Tab(selected = tab==2, onClick = { tab=2 }, text = { Text("Drawables") })
            }
            when (tab) {
                0 -> {
                    var newName by remember { mutableStateOf("") }
                    var newValue by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("name") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = newValue, onValueChange = { newValue = it }, label = { Text("value") }, modifier = Modifier.weight(1f), singleLine = true)
                            IconButton(onClick = { if (newName.isNotBlank()) { vm.addString(newName, newValue); newName = ""; newValue = "" } }) { Icon(Icons.Default.Add, null) }
                        }
                        LazyColumn(contentPadding = PaddingValues(8.dp)) {
                            itemsIndexed(state.strings) { idx, s ->
                                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.name, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                                    OutlinedTextField(value = s.value, onValueChange = { vm.updateString(idx, it) },
                                        modifier = Modifier.weight(1f), singleLine = true)
                                }
                            }
                        }
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                    itemsIndexed(state.colors) { idx, c ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, Modifier.weight(1f))
                            Text(c.value, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                2 -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                    itemsIndexed(state.drawables) { _, name ->
                        ListItem(headlineContent = { Text(name) }, leadingContent = { Icon(Icons.Default.Image, null) })
                    }
                    if (state.drawables.isEmpty()) item { Text("No drawables found in res/", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}