package com.androidstudiomobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.androidstudiomobile.preview.ComposePreviewer
import com.androidstudiomobile.ui.viewmodel.HierarchyViewerViewModel
import java.io.File

/**
 * Hierarchy Viewer — alternativa offline ao Layout Inspector ao vivo.
 *
 * Solução:
 * - Parseia o XML de layout e constrói a árvore de views com todos os atributos.
 * - Exibe a hierarquia em um tree-view interativo com expand/collapse.
 * - Cada nó permite editar atributos inline (salva de volta no XML).
 * - Mostra estatísticas de profundidade, número de views, etc.
 * - Funciona offline, sem precisar de um dispositivo conectado.
 *
 * Para layouts Compose: analisa o código e gera uma árvore baseada
 * nos composables encontrados (análise estática do código-fonte).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HierarchyViewerScreen(
    navController: NavController,
    filePath: String,
    vm: HierarchyViewerViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(filePath) { vm.loadFile(filePath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hierarchy Viewer", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(File(filePath).name, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (state.isDirty) {
                        IconButton(onClick = { vm.saveFile(filePath) }) {
                            Icon(Icons.Default.Save, "Salvar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Stats bar
            state.root?.let { root ->
                val depth = countDepth(root)
                val total = countTotal(root)
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatChip("Views", total.toString(), Icons.Default.ViewModule)
                        StatChip("Profundidade", depth.toString(), Icons.Default.AccountTree)
                        StatChip("Tipo", if (filePath.endsWith(".xml")) "XML" else "Compose",
                            Icons.Default.Code)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Hierarquia") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Atributos") })
            }

            when (selectedTab) {
                0 -> HierarchyTab(vm, state)
                1 -> AttributesTab(vm, state)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text("$label: $value", fontSize = 11.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun HierarchyTab(vm: HierarchyViewerViewModel, state: com.androidstudiomobile.ui.viewmodel.HierarchyViewerState) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.root == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountTree, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Arquivo não reconhecido como layout")
                Text("Abra um arquivo .xml de layout Android", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        item {
            HierarchyNode(
                node = state.root,
                expandedNodes = state.expandedNodes,
                selectedNodeId = state.selectedNodeId,
                depth = 0,
                onToggle = vm::toggleNode,
                onSelect = vm::selectNode
            )
        }
    }
}

@Composable
private fun HierarchyNode(
    node: ComposePreviewer.XmlViewNode,
    expandedNodes: Set<String>,
    selectedNodeId: String?,
    depth: Int,
    onToggle: (String) -> Unit,
    onSelect: (ComposePreviewer.XmlViewNode) -> Unit
) {
    val nodeId = "${depth}_${node.tag}_${node.id}"
    val isExpanded = expandedNodes.contains(nodeId)
    val isSelected = selectedNodeId == nodeId
    val hasChildren = node.children.isNotEmpty()

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    RoundedCornerShape(4.dp)
                )
                .border(
                    if (isSelected) 1.dp else 0.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(4.dp)
                )
                .clickable { onSelect(node); if (hasChildren) onToggle(nodeId) }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse toggle
            if (hasChildren) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.width(16.dp))
            }

            // Node color indicator
            Box(Modifier.size(8.dp).background(viewTypeColor(node.tag), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))

            // Tag name
            Text(
                node.tag,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = viewTypeColor(node.tag)
            )

            // ID
            if (node.id.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "@+id/${node.id}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Text content
            if (node.text.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "\"${node.text.take(20)}\"",
                    fontSize = 10.sp,
                    color = Color(0xFF6A8759),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                "${node.widthSpec.take(4)} × ${node.heightSpec.take(4)}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = isExpanded || depth == 0) {
            if (hasChildren && (isExpanded || depth == 0)) {
                Column {
                    node.children.forEach { child ->
                        HierarchyNode(child, expandedNodes, selectedNodeId, depth + 1, onToggle, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributesTab(vm: HierarchyViewerViewModel, state: com.androidstudiomobile.ui.viewmodel.HierarchyViewerState) {
    val selected = state.selectedNode
    if (selected == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.TouchApp, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Selecione uma view na hierarquia")
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Cabeçalho
        Card(
            Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(selected.tag, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = viewTypeColor(selected.tag))
                if (selected.id.isNotBlank()) Text("@+id/${selected.id}", fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Atributos editáveis principais
        Text("Atributos:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))

        val mainAttrs = buildList {
            add(Triple("android:id", "@+id/${selected.id}", "id"))
            add(Triple("android:layout_width", selected.widthSpec, "layout_width"))
            add(Triple("android:layout_height", selected.heightSpec, "layout_height"))
            if (selected.text.isNotBlank()) add(Triple("android:text", selected.text, "text"))
            if (selected.background.isNotBlank()) add(Triple("android:background", selected.background, "background"))
            if (selected.textColor.isNotBlank()) add(Triple("android:textColor", selected.textColor, "textColor"))
            if (selected.textSize > 0) add(Triple("android:textSize", "${selected.textSize}sp", "textSize"))
            if (selected.paddingDp > 0) add(Triple("android:padding", "${selected.paddingDp}dp", "padding"))
            if (selected.gravity.isNotBlank()) add(Triple("android:gravity", selected.gravity, "gravity"))
        }

        mainAttrs.forEach { (key, value, _) ->
            AttributeRow(key = key, value = value, onValueChange = {})
        }

        // Todos os atributos extras
        if (selected.attributes.size > mainAttrs.size) {
            Text("Todos os atributos:", fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp))
            selected.attributes.entries.sortedBy { it.key }.forEach { (k, v) ->
                AttributeRow(key = k, value = v, onValueChange = { vm.updateAttribute(k, it) })
            }
        }
    }
}

@Composable
private fun AttributeRow(key: String, value: String, onValueChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf(value) }

    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(key.removePrefix("android:"), fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (editing) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                } else {
                    Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (editing) {
                IconButton(onClick = { onValueChange(textValue); editing = false }) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { textValue = value; editing = false }) {
                    Icon(Icons.Default.Close, null)
                }
            } else {
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun viewTypeColor(tag: String): Color = when {
    tag.endsWith("Layout") || tag == "ConstraintLayout" -> Color(0xFF6200EE)
    tag == "TextView" -> Color(0xFF2196F3)
    tag == "Button" || tag == "MaterialButton" -> Color(0xFF4CAF50)
    tag == "ImageView" || tag == "ShapeableImageView" -> Color(0xFFFF9800)
    tag == "RecyclerView" -> Color(0xFF009688)
    tag == "EditText" || tag == "TextInputLayout" -> Color(0xFFF44336)
    tag == "CardView" || tag == "MaterialCardView" -> Color(0xFF795548)
    tag == "BottomNavigationView" || tag == "NavigationView" -> Color(0xFF607D8B)
    else -> Color(0xFF9C27B0)
}

private fun countDepth(node: ComposePreviewer.XmlViewNode, d: Int = 0): Int =
    if (node.children.isEmpty()) d else node.children.maxOf { countDepth(it, d + 1) }

private fun countTotal(node: ComposePreviewer.XmlViewNode): Int =
    1 + node.children.sumOf { countTotal(it) }
