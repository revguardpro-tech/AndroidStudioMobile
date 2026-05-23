package com.androidstudiomobile.ui.screens
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.viewmodel.FindInProjectViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindInProjectScreen(navController: NavController, projectPath: String, vm: FindInProjectViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Find in Project") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.query, onValueChange = vm::updateQuery,
                label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (state.query.isNotBlank()) IconButton(onClick = { vm.search(projectPath) }) { Icon(Icons.Default.Search, "Search") } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.caseSensitive, onClick = vm::toggleCase, label = { Text("Aa", fontSize = 12.sp) })
                FilterChip(selected = state.useRegex, onClick = vm::toggleRegex, label = { Text(".*", fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
                OutlinedTextField(value = state.fileExtFilter, onValueChange = vm::setExtFilter,
                    label = { Text("ext") }, modifier = Modifier.width(80.dp), singleLine = true)
            }
            OutlinedTextField(value = state.replaceQuery, onValueChange = vm::updateReplace,
                label = { Text("Replace with (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.search(projectPath) }, enabled = !state.isSearching && state.query.isNotBlank()) {
                    if (state.isSearching) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text("Search")
                }
                if (state.replaceQuery.isNotBlank() && state.results.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.replaceAll(projectPath) }) { Text("Replace All (${state.results.size})") }
                }
            }
            if (state.replacedCount > 0) Text("✅ Replaced in ${state.replacedCount} file(s)", color = MaterialTheme.colorScheme.primary)
            Text("${state.results.size} result(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.results) { result ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(result.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(":${result.line}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(result.snippet, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}