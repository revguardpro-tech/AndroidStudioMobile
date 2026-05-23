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
import androidx.navigation.NavController
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var fontSize by remember { mutableIntStateOf(14) }
    var wordWrap by remember { mutableStateOf(false) }
    var minimap  by remember { mutableStateOf(true) }
    var autosave by remember { mutableStateOf(true) }
    var lintEnabled by remember { mutableStateOf(true) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(headlineContent = { Text("Editor") }, leadingContent = { Icon(Icons.Default.Code, null) })
            HorizontalDivider()
            ListItem(headlineContent = { Text("Font Size") }, supportingContent = { Text("$fontSize sp") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (fontSize > 10) fontSize-- }) { Icon(Icons.Default.Remove, null) }
                        Text("$fontSize")
                        IconButton(onClick = { if (fontSize < 24) fontSize++ }) { Icon(Icons.Default.Add, null) }
                    }
                })
            ListItem(headlineContent = { Text("Word Wrap") }, supportingContent = { Text("Wrap long lines") },
                trailingContent = { Switch(checked = wordWrap, onCheckedChange = { wordWrap = it }) })
            ListItem(headlineContent = { Text("Minimap") }, supportingContent = { Text("Show code minimap") },
                trailingContent = { Switch(checked = minimap, onCheckedChange = { minimap = it }) })
            ListItem(headlineContent = { Text("Auto Save") }, supportingContent = { Text("Save on focus loss") },
                trailingContent = { Switch(checked = autosave, onCheckedChange = { autosave = it }) })
            HorizontalDivider()
            ListItem(headlineContent = { Text("Analysis") }, leadingContent = { Icon(Icons.Default.BugReport, null) })
            HorizontalDivider()
            ListItem(headlineContent = { Text("Real-time Lint") }, supportingContent = { Text("Highlight errors as you type") },
                trailingContent = { Switch(checked = lintEnabled, onCheckedChange = { lintEnabled = it }) })
            ListItem(headlineContent = { Text("Kotlin Language Server") }, supportingContent = { Text("Install via Termux: pkg install kotlin-language-server") },
                leadingContent = { Icon(Icons.Default.Terminal, null) })
            HorizontalDivider()
            ListItem(headlineContent = { Text("About") }, leadingContent = { Icon(Icons.Default.Info, null) })
            HorizontalDivider()
            ListItem(headlineContent = { Text("Android Studio Mobile") }, supportingContent = { Text("v2.0 — Monaco Editor + Kotlin LSP + Gradle builds") })
            ListItem(headlineContent = { Text("Feature Parity") }, supportingContent = { Text("~75-80% of daily-use Android Studio features.
Emulator, USB Debugger, and Profiler require desktop ADB — not possible on Android.") })
        }
    }
}