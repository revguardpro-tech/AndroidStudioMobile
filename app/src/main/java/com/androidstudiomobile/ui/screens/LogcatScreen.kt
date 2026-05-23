package com.androidstudiomobile.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.viewmodel.LogcatViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(navController: NavController, vm: LogcatViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val filtered = state.entries.filter {
        (state.filter.isBlank() || it.message.contains(state.filter, ignoreCase = true) || it.tag.contains(state.filter, ignoreCase = true)) &&
        (state.levelFilter == "V" || it.level == state.levelFilter)
    }
    LaunchedEffect(filtered.size) { if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.lastIndex) }
    LaunchedEffect(Unit) { vm.start() }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Logcat") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (state.isRunning) IconButton(onClick = vm::stop) { Icon(Icons.Default.Stop, "Stop") }
                    else IconButton(onClick = vm::start) { Icon(Icons.Default.PlayArrow, "Start") }
                    IconButton(onClick = vm::clear) { Icon(Icons.Default.Delete, "Clear") }
                })
        },
        bottomBar = {
            Column {
                OutlinedTextField(value = state.filter, onValueChange = vm::setFilter,
                    placeholder = { Text("Filter…") }, modifier = Modifier.fillMaxWidth().padding(8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("V","D","I","W","E","F").forEach { lvl ->
                        FilterChip(selected = state.levelFilter == lvl, onClick = { vm.setLevelFilter(lvl) }, label = { Text(lvl) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState) {
            items(filtered) { entry ->
                val color = when (entry.level) { "E","F" -> Color(0xFFFF6B6B); "W" -> Color(0xFFFFC107); "I" -> Color(0xFF4CAF50); "D" -> Color(0xFF75BEFF); else -> Color(0xFFAAAAAA) }
                Row(Modifier.padding(horizontal = 8.dp, vertical = 1.dp)) {
                    Text(entry.level, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(entry.tag.take(20).padEnd(20), color = Color(0xFF9876AA), fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(140.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(entry.message, color = color.copy(alpha = 0.9f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}