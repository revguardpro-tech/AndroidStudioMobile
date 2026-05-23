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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.androidstudiomobile.lsp.GoToDefinitionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefactorScreen(navController: NavController, projectPath: String) {
    val scope = rememberCoroutineScope()
    var symbol     by remember { mutableStateOf("") }
    var newName    by remember { mutableStateOf("") }
    var results    by remember { mutableStateOf<List<String>>(emptyList()) }
    var isBusy     by remember { mutableStateOf(false) }
    var doneCount  by remember { mutableIntStateOf(0) }
    fun rename() {
        if (symbol.isBlank() || newName.isBlank()) return
        isBusy = true; results = emptyList()
        scope.launch(Dispatchers.IO) {
            val usages = GoToDefinitionProvider.findUsages(symbol, projectPath)
            val files  = usages.map { it.filePath }.distinct()
            var count  = 0
            files.forEach { path ->
                val f = File(path); val c = f.readText()
                val updated = c.replace(Regex("(?<![a-zA-Z0-9_])${Regex.escape(symbol)}(?![a-zA-Z0-9_])"), newName)
                if (updated != c) { f.writeText(updated); count++ }
            }
            doneCount = count
            results = files.take(50)
            isBusy = false
        }
    }
    fun optimizeImports() {
        isBusy = true
        scope.launch(Dispatchers.IO) {
            var count = 0
            File(projectPath).walkTopDown().filter { it.extension == "kt" }.take(100).forEach { file ->
                val lines = file.readLines()
                val imports = lines.filter { it.trimStart().startsWith("import ") }
                val used    = imports.filter { imp -> val cls = imp.substringAfterLast('.').trimEnd(';'); lines.any { l -> !l.startsWith("import") && l.contains(cls) } }
                if (used.size != imports.size) {
                    val newLines = lines.filter { !it.trimStart().startsWith("import ") || used.contains(it) }
                    file.writeText(newLines.joinToString("
")); count++
                }
            }
            doneCount = count; results = listOf("Optimized imports in $count file(s)"); isBusy = false
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Refactor") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rename Symbol", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = symbol, onValueChange = { symbol = it }, label = { Text("Symbol to rename") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::rename, enabled = !isBusy && symbol.isNotBlank() && newName.isNotBlank()) {
                    if (isBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Text("Rename")
                }
                OutlinedButton(onClick = ::optimizeImports, enabled = !isBusy) { Text("Optimize Imports") }
            }
            if (doneCount > 0) Text("✅ Modified $doneCount file(s)", color = MaterialTheme.colorScheme.primary)
            if (results.isNotEmpty()) {
                Text("Affected files:", style = MaterialTheme.typography.labelLarge)
                LazyColumn {
                    items(results) { path ->
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(path.substringAfterLast('/'), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("⚠ Rename is text-based (not semantic). Review changes before committing.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}