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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdkManagerScreen(navController: androidx.navigation.NavController) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val toolsDir = File(ctx.filesDir, "tools")
    data class Tool(val name: String, val file: String, val url: String, val desc: String)
    val tools = listOf(
        Tool("ecj.jar","ecj.jar","https://github.com/nicowillis/ecj-android/releases/download/v3.14.0/ecj-3.14.0.jar","Eclipse Compiler for Java — compiles .java files"),
        Tool("android.jar","android.jar","https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v2.7.0-beta.1/android.jar","Android SDK stubs — required for compilation"),
        Tool("kotlin-stdlib","kotlin-stdlib.jar","https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar","Kotlin Standard Library"),
    )
    var statusMap by remember { mutableStateOf(tools.associate { it.file to File(toolsDir, it.file).exists() }) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var progress    by remember { mutableFloatStateOf(0f) }
    fun download(tool: Tool) {
        downloading = tool.name
        scope.launch(Dispatchers.IO) {
            try {
                toolsDir.mkdirs()
                val dest = File(toolsDir, tool.file)
                val conn = URL(tool.url).openConnection().also { it.connect() }
                val total = conn.contentLength.toLong()
                conn.getInputStream().use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(8192); var read = 0L
                        var n: Int
                        while (input.read(buf).also { n = it } >= 0) {
                            output.write(buf, 0, n); read += n
                            if (total > 0) withContext(Dispatchers.Main) { progress = read.toFloat() / total }
                        }
                    }
                }
                withContext(Dispatchers.Main) { statusMap = statusMap + (tool.file to true); downloading = null; progress = 0f }
            } catch (e: Exception) { withContext(Dispatchers.Main) { downloading = null; progress = 0f } }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("SDK Manager") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Build Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Download tools for the Simple build mode. For Gradle builds, install Termux and run: pkg install aapt2 ecj d8 android-sdk", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                val termuxAvailable = File("/data/data/com.termux/files/usr/bin/sh").exists()
                Card(colors = CardDefaults.cardColors(if (termuxAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (termuxAvailable) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                            tint = if (termuxAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Termux", fontWeight = FontWeight.Bold)
                            Text(if (termuxAvailable) "Detected — full Gradle + JVM builds available" else "Not installed — install from F-Droid for full Gradle support", fontSize = 12.sp)
                        }
                    }
                }
            }
            items(tools) { tool ->
                val installed = statusMap[tool.file] == true
                val isDownloading = downloading == tool.name
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (installed) Icons.Default.CheckCircle else Icons.Default.Download, null,
                                tint = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tool.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(tool.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!installed && !isDownloading)
                                TextButton(onClick = { download(tool) }) { Text("Download") }
                            if (installed) Text("✓ Ready", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (isDownloading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("Downloading… ${(progress * 100).toInt()}%", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}