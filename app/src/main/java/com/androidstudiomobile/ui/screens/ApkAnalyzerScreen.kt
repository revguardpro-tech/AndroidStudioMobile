package com.androidstudiomobile.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.viewmodel.ApkAnalyzerViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkAnalyzerScreen(navController: NavController, apkPath: String, vm: ApkAnalyzerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(apkPath) { if (apkPath.isNotBlank()) vm.analyzeApk(apkPath) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("APK Analyzer") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            state.info?.let { info ->
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab==0, onClick = { tab=0 }, text = { Text("Files") })
                    Tab(selected = tab==1, onClick = { tab=1 }, text = { Text("Manifest") })
                    Tab(selected = tab==2, onClick = { tab=2 }, text = { Text("Permissions") })
                    Tab(selected = tab==3, onClick = { tab=3 }, text = { Text("DEX") })
                }
                when (tab) {
                    0 -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        item { Text("APK Size: ${info.size/1024} KB", Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall) }
                        items(info.files) { entry ->
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(entry.name, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                Text("${entry.compressedSize/1024}K", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    1 -> Text(info.manifest, Modifier.padding(16.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    2 -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        if (info.permissions.isEmpty()) item { Text("No permissions declared", Modifier.padding(8.dp)) }
                        items(info.permissions) { Text(it, Modifier.padding(horizontal=8.dp, vertical=2.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    }
                    3 -> Text(info.dexInfo, Modifier.padding(16.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}