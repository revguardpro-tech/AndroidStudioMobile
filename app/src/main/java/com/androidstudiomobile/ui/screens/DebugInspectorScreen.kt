package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.debug.DebugInspectorEngine
import com.androidstudiomobile.ui.viewmodel.DebugInspectorViewModel
import java.io.File

/**
 * Debug Inspector Screen — alternativa ao debugger com breakpoints.
 *
 * Solução:
 * - O usuário marca variáveis com `// debug: varName` no código Kotlin.
 * - Esta tela mostra todos os marcadores detectados no arquivo aberto.
 * - Ao clicar em "Injetar Logs", o engine processa o projeto e insere
 *   chamadas android.util.Log.d() antes de cada marcador.
 * - Os logs aparecem no Logcat com a tag "ASM_DEBUG".
 * - Também mostra os valores capturados do Logcat em tempo real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugInspectorScreen(
    navController: NavController,
    filePath: String,
    projectPath: String,
    vm: DebugInspectorViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(filePath) { vm.loadFile(filePath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Debug Inspector", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(File(filePath).name, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Instrução de uso
            Card(
                Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Como usar o Debug Inspector", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Adicione `// debug: varName` após qualquer linha de código Kotlin.\n" +
                        "Exemplo:\n  val userId = 42 // debug: userId\n  val name = getName() // debug: name, status",
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Marcadores detectados
            Text(
                "Marcadores detectados (${state.markers.size}):",
                Modifier.padding(8.dp),
                fontWeight = FontWeight.Bold
            )

            if (state.markers.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BugReport, null, Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Nenhum marcador `// debug:` encontrado",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Abra o arquivo no editor e adicione marcadores",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(0.4f), contentPadding = PaddingValues(8.dp)) {
                    items(state.markers) { marker ->
                        DebugMarkerCard(marker)
                    }
                }
            }

            HorizontalDivider()

            // Ações
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.injectLogs(projectPath) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isInjecting && state.markers.isNotEmpty()
                ) {
                    if (state.isInjecting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Injetar Logs e Compilar")
                }
                OutlinedButton(
                    onClick = { vm.removeLogs(filePath) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isInjecting
                ) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remover Logs")
                }
            }

            if (state.injectionResult != null) {
                Card(
                    Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Resultado da injeção:", fontWeight = FontWeight.Bold)
                        Text("Arquivos modificados: ${state.injectionResult!!.modifiedFiles.size}")
                        Text("Logs injetados: ${state.injectionResult!!.totalLogs}")
                        Text("Compile o projeto e veja os valores no Logcat com a tag 'ASM_DEBUG'",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            // Valores capturados do Logcat
            Text(
                "Valores capturados (Logcat ASM_DEBUG):",
                Modifier.padding(8.dp),
                fontWeight = FontWeight.Bold
            )

            if (state.capturedValues.isEmpty()) {
                Box(Modifier.weight(0.4f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhum valor capturado ainda.\nCompile com logs injetados e execute o app.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    Modifier.weight(0.4f).background(Color(0xFF1E1E1E)).fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(state.capturedValues) { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ":${entry.lineNumber}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF808080),
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                entry.variableName,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF9876AA),
                                modifier = Modifier.width(100.dp)
                            )
                            Text("=", color = Color(0xFF808080), fontSize = 11.sp)
                            SelectionContainer {
                                Text(
                                    entry.value,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF6A8759)
                                )
                            }
                            if (entry.timestamp.isNotBlank()) {
                                Spacer(Modifier.weight(1f))
                                Text(entry.timestamp, fontSize = 9.sp, color = Color(0xFF606060))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugMarkerCard(marker: DebugInspectorEngine.DebugMarker) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(36.dp).background(
                    MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("L${marker.line}", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(marker.sourceLine.trim(), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    marker.variables.forEach { varName ->
                        Box(
                            Modifier.background(Color(0xFF9876AA).copy(0.15f),
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(4.dp, 2.dp)
                        ) {
                            Text(varName, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = Color(0xFF9876AA))
                        }
                    }
                }
            }
            Icon(Icons.Default.BugReport, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}
