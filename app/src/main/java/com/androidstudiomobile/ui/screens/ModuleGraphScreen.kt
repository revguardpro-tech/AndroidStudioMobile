package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.modules.ModuleGraphParser
import com.androidstudiomobile.ui.viewmodel.ModuleGraphViewModel

/**
 * Multi-Module Graph Screen — visualizador de dependências entre módulos.
 *
 * Solução:
 * - Parseia settings.gradle.kts e build.gradle.kts de todos os módulos.
 * - Exibe o grafo de dependências em um layout visual de lista com badges.
 * - Detecta dependências circulares e as sinaliza em vermelho.
 * - Mostra o tipo de cada módulo (app, library, feature, core) com cores.
 * - Permite sincronizar apenas a estrutura, sem baixar deps externas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleGraphScreen(
    navController: NavController,
    projectPath: String,
    vm: ModuleGraphViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(projectPath) { vm.loadProject(projectPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Module Graph") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadProject(projectPath) }) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Sync, "Sincronizar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Stats
            if (!state.isLoading && state.modules.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatBadge("Módulos", state.modules.size.toString())
                        StatBadge("Arestas", state.edges.size.toString())
                        StatBadge("Circulares", state.circularDeps.size.toString(),
                            if (state.circularDeps.isNotEmpty()) Color(0xFFFF5722) else null)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Módulos") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Dependências") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Análise") })
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Analisando projeto...")
                    }
                }
                state.modules.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountTree, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Nenhum módulo detectado")
                        Text("Abra um projeto Android multi-módulo", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> when (selectedTab) {
                    0 -> ModulesTab(state)
                    1 -> DependenciesTab(state)
                    2 -> AnalysisTab(state)
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, tint: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            color = tint ?: MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ModulesTab(state: com.androidstudiomobile.ui.viewmodel.ModuleGraphState) {
    var selectedModule by remember { mutableStateOf<ModuleGraphParser.GradleModule?>(null) }

    if (selectedModule != null) {
        ModuleDetailCard(selectedModule!!) { selectedModule = null }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        // Agrupar por tipo
        val grouped = state.modules.groupBy { it.type }
        ModuleGraphParser.ModuleType.entries.forEach { type ->
            val mods = grouped[type] ?: return@forEach
            item {
                Text(
                    type.name, Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = moduleTypeColor(type)
                )
            }
            items(mods) { module ->
                ModuleCard(module) { selectedModule = module }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleGraphParser.GradleModule, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Type indicator
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                    .background(moduleTypeColor(module.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    moduleTypeIcon(module.type), null,
                    Modifier.size(24.dp), tint = moduleTypeColor(module.type)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(module.name, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace)
                    ModuleTypeBadge(module.type)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (module.hasKotlin) FeatureBadge("Kotlin", Color(0xFF7F52FF))
                    if (module.hasCompose) FeatureBadge("Compose", Color(0xFF4CAF50))
                    if (module.dependencies.isNotEmpty())
                        FeatureBadge("${module.dependencies.size} deps", MaterialTheme.colorScheme.primary)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModuleDetailCard(module: ModuleGraphParser.GradleModule, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
            Text(" Voltar")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(module.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModuleTypeBadge(module.type)
                            if (module.hasKotlin) FeatureBadge("Kotlin", Color(0xFF7F52FF))
                            if (module.hasCompose) FeatureBadge("Compose", Color(0xFF4CAF50))
                        }
                        Text(module.path, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Text("Plugins:", fontWeight = FontWeight.Bold)
                        module.plugins.forEach { Text("  • $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }

                        if (module.dependencies.isNotEmpty()) {
                            Text("Dependências de módulos:", fontWeight = FontWeight.Bold)
                            module.dependencies.forEach { dep ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("  • ${dep.targetModule}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .padding(4.dp, 2.dp)) {
                                        Text(dep.configuration, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        if (module.externalDeps.isNotEmpty()) {
                            Text("Dependências externas (${module.externalDeps.size}):", fontWeight = FontWeight.Bold)
                            module.externalDeps.take(10).forEach { dep ->
                                Text("  • $dep", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DependenciesTab(state: com.androidstudiomobile.ui.viewmodel.ModuleGraphState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        item {
            Text("Grafo de Dependências", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(state.edges) { (from, to, config) ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(from, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                    Text(to, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(4.dp, 2.dp)) {
                        Text(config, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisTab(state: com.androidstudiomobile.ui.viewmodel.ModuleGraphState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        item {
            Text("Análise do Projeto", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Dependências circulares
        if (state.circularDeps.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5722).copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5722))
                            Spacer(Modifier.width(8.dp))
                            Text("Dependências Circulares Detectadas!", fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5722))
                        }
                        state.circularDeps.forEach { cycle ->
                            Text("  ${cycle.joinToString(" → ")}", fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace, color = Color(0xFFFF5722))
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("Nenhuma dependência circular detectada", color = Color(0xFF4CAF50))
                    }
                }
            }
        }

        // Módulo mais dependido
        item {
            val mostDepended = state.edges.groupBy { it.second }.maxByOrNull { it.value.size }
            if (mostDepended != null) {
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Módulo mais usado:", fontWeight = FontWeight.Bold)
                        Text(mostDepended.key, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Depende deste: ${mostDepended.value.size} módulos", fontSize = 12.sp)
                    }
                }
            }
        }

        // Módulos sem dependências
        item {
            val isolated = state.modules.filter { m ->
                state.edges.none { it.first == m.name || it.second == m.name }
            }
            if (isolated.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Módulos isolados (${isolated.size}):", fontWeight = FontWeight.Bold)
                        isolated.forEach { m ->
                            Text("  • ${m.name}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleTypeBadge(type: ModuleGraphParser.ModuleType) {
    Box(
        Modifier.background(moduleTypeColor(type).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, moduleTypeColor(type).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(type.name, fontSize = 10.sp, color = moduleTypeColor(type), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeatureBadge(label: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(4.dp, 2.dp)) {
        Text(label, fontSize = 10.sp, color = color)
    }
}

private fun moduleTypeColor(type: ModuleGraphParser.ModuleType): Color = when (type) {
    ModuleGraphParser.ModuleType.APP     -> Color(0xFF4CAF50)
    ModuleGraphParser.ModuleType.LIBRARY -> Color(0xFF2196F3)
    ModuleGraphParser.ModuleType.FEATURE -> Color(0xFFFF9800)
    ModuleGraphParser.ModuleType.CORE    -> Color(0xFF9C27B0)
    ModuleGraphParser.ModuleType.TEST    -> Color(0xFF607D8B)
    ModuleGraphParser.ModuleType.UNKNOWN -> Color(0xFF9E9E9E)
}

private fun moduleTypeIcon(type: ModuleGraphParser.ModuleType) = when (type) {
    ModuleGraphParser.ModuleType.APP     -> Icons.Default.Android
    ModuleGraphParser.ModuleType.LIBRARY -> Icons.Default.LibraryBooks
    ModuleGraphParser.ModuleType.FEATURE -> Icons.Default.Extension
    ModuleGraphParser.ModuleType.CORE    -> Icons.Default.Hub
    ModuleGraphParser.ModuleType.TEST    -> Icons.Default.Science
    ModuleGraphParser.ModuleType.UNKNOWN -> Icons.Default.Help
}
