package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import com.androidstudiomobile.ui.viewmodel.DatabaseInspectorViewModel
import java.io.File

/**
 * Database Inspector — visualizador SQLite integrado.
 *
 * Solução:
 * - Usa android.database.sqlite.SQLiteDatabase nativo para abrir e ler arquivos .db.
 * - O dev copia o banco de dados para um local acessível (ex: filesDir do app target via adb pull,
 *   ou se o app estiver em modo debug com FLAG_DEBUGGABLE).
 * - Permite executar queries SQL personalizadas.
 * - Lista todas as tabelas e mostra os dados em um grid rolável.
 * - Exibe o schema de cada tabela (CREATE TABLE statement).
 *
 * Limitação documentada: requer que o dev exporte o .db para um caminho acessível.
 * Para Room, basta fazer: context.getDatabasePath("nome.db").copyTo(File(downloads, "nome.db"))
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseInspectorScreen(
    navController: NavController,
    dbPath: String = "",
    vm: DatabaseInspectorViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var customQuery by remember { mutableStateOf("SELECT * FROM sqlite_master WHERE type='table'") }

    LaunchedEffect(dbPath) { if (dbPath.isNotBlank()) vm.openDatabase(dbPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Database Inspector") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, null) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Header com caminho do DB
            Card(
                Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.dbPath.isNotBlank()) File(state.dbPath).name else "Nenhum banco aberto",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        if (state.dbPath.isNotBlank()) {
                            Text(state.dbPath, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.tables.size} tabelas  •  SQLite ${state.sqliteVersion}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (state.dbPath.isBlank()) {
                // Instruções para abrir um banco
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.Storage, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Database Inspector", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Para inspecionar um banco Room/SQLite, exporte o arquivo .db para um local acessível:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Card {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Como exportar o banco:", fontWeight = FontWeight.Bold)
                                Text("1. Em debug: use adb pull", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text("2. No código Kotlin:", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text("   val db = getDatabasePath(\"app.db\")", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary)
                                Text("   db.copyTo(File(getExternalFilesDir(null), \"app.db\"))",
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        var pathInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = pathInput,
                            onValueChange = { pathInput = it },
                            label = { Text("Caminho do arquivo .db") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
                        )
                        Button(
                            onClick = { vm.openDatabase(pathInput) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pathInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Abrir Banco de Dados")
                        }
                    }
                }
                return@Scaffold
            }

            // Query bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("SELECT * FROM tabela", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Code, null) }
                )
                IconButton(
                    onClick = { vm.executeQuery(customQuery) },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                ) { Icon(Icons.Default.PlayArrow, "Executar", tint = MaterialTheme.colorScheme.onPrimary) }
            }

            state.errorMessage?.let {
                Card(
                    Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                }
            }

            // Tabs: tabelas + resultado da query
            var selectedTab by remember { mutableIntStateOf(0) }
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tabelas (${state.tables.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Resultado${if (state.queryResults.isNotEmpty()) " (${state.queryResults.size})" else ""}") })
            }

            when (selectedTab) {
                0 -> TablesView(vm, state, customQuery = { q -> customQuery = q; selectedTab = 1; vm.executeQuery(q) })
                1 -> QueryResultView(state)
            }
        }
    }
}

@Composable
private fun TablesView(
    vm: DatabaseInspectorViewModel,
    state: com.androidstudiomobile.ui.viewmodel.DbInspectorState,
    customQuery: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        items(state.tables) { table ->
            Card(
                onClick = { customQuery("SELECT * FROM ${table.name} LIMIT 100") },
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TableChart, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(table.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${table.rowCount} registros  •  ${table.columns.size} colunas",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    // Colunas
                    table.columns.forEach { col ->
                        Row(Modifier.padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(col.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(120.dp))
                            Text(col.type, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace)
                            if (col.isPrimaryKey) {
                                Box(Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)).padding(2.dp, 1.dp)) {
                                    Text("PK", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (!col.isNullable) {
                                Box(Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(2.dp, 1.dp)) {
                                    Text("NOT NULL", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                    // Schema
                    if (table.createStatement.isNotBlank()) {
                        var showSchema by remember { mutableStateOf(false) }
                        TextButton(onClick = { showSchema = !showSchema }) {
                            Text(if (showSchema) "Ocultar schema" else "Ver CREATE TABLE", fontSize = 11.sp)
                        }
                        AnimatedVisibility(visible = showSchema) {
                            Text(table.createStatement, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.horizontalScroll(rememberScrollState()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryResultView(state: com.androidstudiomobile.ui.viewmodel.DbInspectorState) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.queryResults.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum resultado. Execute uma query acima.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val headers = state.queryResults.first().keys.toList()
    val rows = state.queryResults

    Column(Modifier.fillMaxSize()) {
        Text("${rows.size} registros retornados", Modifier.padding(8.dp), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column {
                // Header
                Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    headers.forEach { col ->
                        Text(col, Modifier.width(120.dp).padding(8.dp, 6.dp),
                            fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                HorizontalDivider()
                LazyColumn {
                    itemsIndexed(rows) { idx, row ->
                        Row(Modifier.background(if (idx % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)) {
                            headers.forEach { col ->
                                val value = row[col]?.toString() ?: "NULL"
                                Text(value.take(20), Modifier.width(120.dp).padding(8.dp, 4.dp),
                                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                    color = if (value == "NULL") MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedVisibility(visible: Boolean, content: @Composable () -> Unit) {
    if (visible) content()
}
